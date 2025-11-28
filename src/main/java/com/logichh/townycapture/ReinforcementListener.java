package com.logichh.townycapture;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class ReinforcementListener implements Listener {
    private final TownyCapture plugin;
    private final Map<String, List<UUID>> activeReinforcements = new HashMap<>();
    private final Map<String, BukkitTask> phaseTasks = new HashMap<>();
    private final Map<String, BukkitTask> trackingTasks = new HashMap<>();
    private final Map<String, Integer> currentPhase = new HashMap<>();
    private final Deque<SpawnRequest> spawnQueue = new ArrayDeque<>();
    private BukkitTask spawnProcessorTask;

    public ReinforcementListener(TownyCapture plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onEntityTarget(EntityTargetEvent event) {
        // Make reinforcement mobs only target capturing players
        if (!event.getEntity().hasMetadata("capture_reinforcement")) {
            return;
        }

        if (!(event.getTarget() instanceof Player)) {
            event.setCancelled(true);
            return;
        }

        String pointId = event.getEntity().getMetadata("capture_reinforcement").get(0).asString();
        CapturePoint point = plugin.getCapturePoint(pointId);

        if (point == null) {
            event.setCancelled(true);
            return;
        }

        // Only target capturing players
        String capturingTown = point.getCapturingTown();
        if (capturingTown == null || capturingTown.isEmpty()) {
            event.setCancelled(true);
            return;
        }

        Player target = (Player) event.getTarget();
        com.palmergames.bukkit.towny.object.Town targetTown = com.palmergames.bukkit.towny.TownyAPI.getInstance().getTown(target);
        
        // Only attack players from the capturing town
        if (targetTown == null || !targetTown.getName().equals(capturingTown)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        // Check if this is a reinforcement mob
        if (!event.getEntity().hasMetadata("capture_reinforcement")) {
            return;
        }

        // Clear all loot drops but keep XP
        event.getDrops().clear();

        String pointId = event.getEntity().getMetadata("capture_reinforcement").get(0).asString();
        CaptureSession session = plugin.getActiveSession(pointId);

        // Only reward if there's an active capture
        if (session != null && session.isActive()) {
            // Reduce capture time by random 1-3 seconds when reinforcement is killed
            Random random = new Random();
            int secondsReduced = random.nextInt(3) + 1; // 1 to 3 seconds
            int currentTime = session.getRemainingCaptureTime();
            int newTime = Math.max(0, currentTime - secondsReduced); // Don't go below 0
            session.setRemainingCaptureTime(newTime);

            // Notify the killer if it's a player
            Player killer = event.getEntity().getKiller();
            if (killer != null) {
                killer.sendMessage(Messages.get("messages.reinforcement.timer-reduced", Map.of(
                    "seconds", String.valueOf(secondsReduced)
                )));
            }
            
            // Check if we need to spawn a phase due to timer change
            checkAndSpawnPhase(pointId);
        }

        // Remove from active reinforcements list
        List<UUID> reinforcements = activeReinforcements.get(pointId);
        if (reinforcements != null) {
            reinforcements.remove(event.getEntity().getUniqueId());
        }
    }
    
    private void checkAndSpawnPhase(String pointId) {
        CaptureSession session = plugin.getActiveSession(pointId);
        if (session == null || !session.isActive()) {
            return;
        }
        
        int timeLeft = session.getRemainingCaptureTime();
        
        // Stop spawning in last minute
        if (timeLeft <= 60) {
            return;
        }
        
        // Calculate elapsed time and expected phase
        int initialTime = session.getInitialCaptureTime();
        int elapsed = initialTime - timeLeft;
        int expectedPhase = (elapsed / 30) + 1;
        int currentP = currentPhase.getOrDefault(pointId, 1);
        
        // If we've skipped phases due to kill rewards, spawn them now
        if (expectedPhase > currentP) {
            CapturePoint point = plugin.getCapturePoint(pointId);
            if (point != null) {
                for (int phase = currentP + 1; phase <= expectedPhase; phase++) {
                    currentPhase.put(pointId, phase);
                    spawnReinforcementWave(pointId, point, phase);
                }
            }
        }
    }

    private void spawnReinforcementWave(String pointId, CapturePoint point, int phase) {
        if (!plugin.getConfig().getBoolean("reinforcements.enabled", true)) {
            return;
        }

        // Get active session to check time remaining
        CaptureSession session = plugin.getActiveSession(pointId);
        if (session == null) {
            return;
        }

        int timeLeft = session.getRemainingCaptureTime();
        
        // Stop spawning in last minute (60 seconds)
        if (timeLeft <= 60) {
            return;
        }

        // Calculate mobs based on phase (harder each phase)
        int baseMobs = plugin.getConfig().getInt("reinforcements.mobs-per-wave", 1);
        int mobsThisPhase = baseMobs + phase;
        
        // Cap at 12 mobs per wave (after phase 11+)
        int actualMobs = Math.min(mobsThisPhase, 12);
        int maxMobs = plugin.getConfig().getInt("reinforcements.max-mobs-per-point", 50);

        // Get capturing players to target
        String capturingTown = point.getCapturingTown();
        com.palmergames.bukkit.towny.TownyAPI townyAPI = com.palmergames.bukkit.towny.TownyAPI.getInstance();
        
        // Enqueue spawns respecting the global cap; queue processor will rate-limit
        List<UUID> reinforcements = activeReinforcements.computeIfAbsent(pointId, k -> new ArrayList<>());
        int availableSlots = Math.max(0, maxMobs - reinforcements.size());
        int toQueue = Math.min(actualMobs, availableSlots);
        for (int i = 0; i < toQueue; i++) {
            enqueueSpawn(pointId);
        }

        // Send message to capturing players only
        String phaseMessage = Messages.get("messages.reinforcement.phase", Map.of(
            "phase", String.valueOf(phase),
            "count", String.valueOf(actualMobs)
        ));
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            com.palmergames.bukkit.towny.object.Town playerTown = townyAPI.getTown(player);
            if (playerTown != null && playerTown.getName().equals(capturingTown) && 
                plugin.isWithinChunkRadius(player.getLocation(), point.getLocation(), point.getChunkRadius())) {
                player.sendMessage(phaseMessage);
            }
        }
    }

    private void enqueueSpawn(String pointId) {
        spawnQueue.add(new SpawnRequest(pointId));
        ensureSpawnProcessor();
    }

    private void ensureSpawnProcessor() {
        if (spawnProcessorTask != null && !spawnProcessorTask.isCancelled()) {
            return;
        }
        spawnProcessorTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::processSpawnQueue, 1L, 1L);
    }

    private void processSpawnQueue() {
        int maxPerTick = Math.max(1, plugin.getConfig().getInt("reinforcements.spawn-rate.max-per-tick", 3));
        int processed = 0;
        while (processed < maxPerTick) {
            SpawnRequest request = spawnQueue.poll();
            if (request == null) {
                break;
            }

            CaptureSession session = plugin.getActiveSession(request.pointId);
            CapturePoint point = plugin.getCapturePoint(request.pointId);
            if (session == null || point == null) {
                continue;
            }

            String capturingTown = session.getTownName();
            if (capturingTown == null || capturingTown.isEmpty()) {
                continue;
            }

            List<UUID> reinforcements = activeReinforcements.computeIfAbsent(request.pointId, k -> new ArrayList<>());
            int maxMobs = plugin.getConfig().getInt("reinforcements.max-mobs-per-point", 50);
            if (reinforcements.size() >= maxMobs) {
                continue;
            }

            if (spawnReinforcementMob(point, session, capturingTown, reinforcements)) {
                processed++;
            }
        }

        if (spawnQueue.isEmpty() && spawnProcessorTask != null) {
            spawnProcessorTask.cancel();
            spawnProcessorTask = null;
        }
    }

    private boolean spawnReinforcementMob(CapturePoint point, CaptureSession session, String capturingTown, List<UUID> reinforcements) {
        Player originPlayer = null;
        if (session.getInitiatorUUID() != null) {
            originPlayer = plugin.getServer().getPlayer(session.getInitiatorUUID());
        }
        if (originPlayer == null) {
            originPlayer = session.getPlayers().stream().filter(Player::isOnline).findFirst().orElse(null);
        }

        Location origin = originPlayer != null ? originPlayer.getLocation() : point.getLocation();
        if (origin.getWorld() == null) {
            return false;
        }

        Random random = new Random();
        double angle = random.nextDouble() * 2 * Math.PI;
        double radius = random.nextDouble() * 8 + 3; // 3-11 blocks away
        Location spawnLoc = origin.clone().add(Math.cos(angle) * radius, 0.5, Math.sin(angle) * radius);

        // Ensure chunk is loaded and find safe Y
        spawnLoc.getChunk().load();
        int surfaceY = spawnLoc.getWorld().getHighestBlockYAt(spawnLoc.getBlockX(), spawnLoc.getBlockZ());
        spawnLoc.setY(surfaceY + 1.0);

        // Choose random mob type
        List<String> mobTypes = plugin.getConfig().getStringList("reinforcements.mob-types");
        if (mobTypes.isEmpty()) {
            mobTypes = Arrays.asList("ZOMBIE", "SKELETON");
        }

        String mobTypeStr = mobTypes.get(random.nextInt(mobTypes.size()));
        EntityType mobType;
        try {
            mobType = EntityType.valueOf(mobTypeStr);
        } catch (IllegalArgumentException e) {
            mobType = EntityType.ZOMBIE;
        }

        Mob mob = (Mob) spawnLoc.getWorld().spawnEntity(spawnLoc, mobType);

        // Set metadata to mark as reinforcement
        mob.setMetadata("capture_reinforcement", new FixedMetadataValue(plugin, point.getId()));
        mob.setMetadata("capture_point_id", new FixedMetadataValue(plugin, point.getId()));
        mob.setMetadata("capturing_town", new FixedMetadataValue(plugin, capturingTown));

        reinforcements.add(mob.getUniqueId());

        // Make mob persistent (won't despawn naturally)
        mob.setRemoveWhenFarAway(false);
        mob.setPersistent(true);

        // Prevent daylight burning - multiple layers of protection
        EntityEquipment equipment = mob.getEquipment();
        if (equipment != null) {
            List<String> hatItems = plugin.getConfig().getStringList("reinforcements.hat-items");
            if (!hatItems.isEmpty()) {
                String hatMaterial = hatItems.get(random.nextInt(hatItems.size()));
                try {
                    Material hat = Material.valueOf(hatMaterial);
                    equipment.setHelmet(new org.bukkit.inventory.ItemStack(hat));
                    equipment.setHelmetDropChance(0f);
                } catch (IllegalArgumentException e) {
                    equipment.setHelmet(new org.bukkit.inventory.ItemStack(Material.LEATHER_HELMET));
                    equipment.setHelmetDropChance(0f);
                }
            }
        }
        
        if (mob instanceof LivingEntity) {
            ((LivingEntity) mob).addPotionEffect(new PotionEffect(
                PotionEffectType.FIRE_RESISTANCE, 
                999999, 
                1, 
                true, 
                false
            ));
        }
        
        if (mob instanceof Slime) {
            ((Slime) mob).setSize(2); // Medium size
        }
        if (mob instanceof MagmaCube) {
            ((MagmaCube) mob).setSize(2); // Medium size
        }

        // Initial target setup (will be continuously updated by tracking task)
        updateMobTarget(mob, point.getId(), capturingTown);
        return true;
    }
    
    /**
     * Updates a mob's target to the nearest capturing player (no distance limit)
     */
    private void updateMobTarget(Entity mobEntity, String pointId, String capturingTown) {
        if (!(mobEntity instanceof Creature)) {
            return;
        }
        
        if (mobEntity.isDead() || !mobEntity.isValid()) {
            return;
        }
        
        CapturePoint point = plugin.getCapturePoint(pointId);
        if (point == null) {
            return;
        }
        
        Creature creature = (Creature) mobEntity;
        Player nearestTarget = null;
        double closestDist = Double.MAX_VALUE;
        
        com.palmergames.bukkit.towny.TownyAPI townyAPI = com.palmergames.bukkit.towny.TownyAPI.getInstance();
        
        // Search for players in the same world - NO DISTANCE LIMIT
        for (Player player : mobEntity.getWorld().getPlayers()) {
            if (!player.isOnline() || player.isDead()) {
                continue;
            }
            
            com.palmergames.bukkit.towny.object.Town playerTown = townyAPI.getTown(player);
            
            // Only target players from the capturing town
            if (playerTown != null && playerTown.getName().equals(capturingTown)) {
                // Check if player is in or near the capture zone (allow some leeway)
                double blockDist = plugin.getChunkDistance(player.getLocation(), point.getLocation()) * 16;
                int blockRadius = point.getChunkRadius() * 16;
                
                // Allow mobs to follow up to 50 blocks outside the zone
                if (blockDist <= (blockRadius + 50)) {
                    double dist = player.getLocation().distance(mobEntity.getLocation());
                    if (dist < closestDist) {
                        closestDist = dist;
                        nearestTarget = player;
                    }
                }
            }
        }
        
        // Update target if we found one
        if (nearestTarget != null) {
            try {
                creature.setTarget(nearestTarget);
            } catch (Exception e) {
                // Silently handle any targeting exceptions
            }
        } else if (creature.getTarget() != null) {
            // If no valid target found but mob has a target, keep current target
            // This prevents mobs from losing track of players
        }
    }
    
    /**
     * Starts a continuous tracking task for mobs in a capture zone
     */
    private void startMobTracking(String pointId, String capturingTown) {
        // Don't start multiple tracking tasks for the same point
        if (trackingTasks.containsKey(pointId)) {
            return;
        }
        
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            // Check if capture is still active
            if (!plugin.getActiveSessions().containsKey(pointId)) {
                stopMobTracking(pointId);
                return;
            }
            
            List<UUID> reinforcements = activeReinforcements.get(pointId);
            if (reinforcements == null || reinforcements.isEmpty()) {
                return;
            }
            
            // Update target for each mob
            for (UUID mobUUID : new ArrayList<>(reinforcements)) {
                Entity mob = plugin.getServer().getEntity(mobUUID);
                if (mob != null && mob.isValid() && !mob.isDead()) {
                    updateMobTarget(mob, pointId, capturingTown);
                }
            }
        }, 20L, 20L); // Every second (20 ticks)
        
        trackingTasks.put(pointId, task);
    }
    
    /**
     * Stops the mob tracking task for a capture zone
     */
    private void stopMobTracking(String pointId) {
        BukkitTask task = trackingTasks.remove(pointId);
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
        spawnQueue.removeIf(req -> req.pointId.equals(pointId));
    }

    public void clearReinforcements(String pointId) {
        List<UUID> reinforcements = activeReinforcements.remove(pointId);
        if (reinforcements != null) {
            for (UUID uuid : reinforcements) {
                org.bukkit.entity.Entity entity = plugin.getServer().getEntity(uuid);
                if (entity != null) {
                    entity.remove();
                }
            }
        }

        // Cancel phase task
        BukkitTask task = phaseTasks.remove(pointId);
        if (task != null) {
            task.cancel();
        }
        
        // Stop tracking task
        stopMobTracking(pointId);
        
        currentPhase.remove(pointId);
    }

    public void shutdown() {
        // Cancel global spawn processor
        if (spawnProcessorTask != null && !spawnProcessorTask.isCancelled()) {
            spawnProcessorTask.cancel();
        }
        spawnQueue.clear();
        
        // Cancel per-point tasks and clean mobs
        for (String pointId : new ArrayList<>(activeReinforcements.keySet())) {
            clearReinforcements(pointId);
        }
        for (BukkitTask task : phaseTasks.values()) {
            if (task != null && !task.isCancelled()) {
                task.cancel();
            }
        }
        phaseTasks.clear();
    }

    public void startReinforcementWaves(String pointId, CapturePoint point) {
        if (!plugin.getConfig().getBoolean("reinforcements.enabled", true)) {
            return;
        }

        // Start at phase 1
        currentPhase.put(pointId, 1);
        
        // Initial wave
        spawnReinforcementWave(pointId, point, 1);
        
        // Start continuous mob tracking for smart AI
        String capturingTown = point.getCapturingTown();
        if (capturingTown != null && !capturingTown.isEmpty()) {
            startMobTracking(pointId, capturingTown);
        }

        // Check every second for timer mark (:00 or :30)
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!plugin.getActiveSessions().containsKey(pointId)) {
                clearReinforcements(pointId);
                return;
            }

            CaptureSession session = plugin.getActiveSession(pointId);
            if (session == null) {
                return;
            }

            int timeLeft = session.getRemainingCaptureTime();
            
            // Stop spawning in last minute
            if (timeLeft <= 60) {
                return;
            }
            
            // Check if we're at a :00 or :30 mark on the timer
            int seconds = timeLeft % 60;
            boolean isAtMark = (seconds == 0 || seconds == 30);
            
            if (isAtMark) {
                // Calculate expected phase based on timer position
                int initialTime = session.getInitialCaptureTime();
                int elapsed = initialTime - timeLeft;
                int expectedPhase = (elapsed / 30) + 1;
                int currentP = currentPhase.getOrDefault(pointId, 1);
                
                // Spawn any phases we've skipped
                if (expectedPhase > currentP) {
                    for (int phase = currentP + 1; phase <= expectedPhase; phase++) {
                        currentPhase.put(pointId, phase);
                        spawnReinforcementWave(pointId, point, phase);
                    }
                }
            }
        }, 20L, 20L); // Every second (20 ticks)

        phaseTasks.put(pointId, task);
    }

    private static class SpawnRequest {
        private final String pointId;

        private SpawnRequest(String pointId) {
            this.pointId = pointId;
        }
    }
}
