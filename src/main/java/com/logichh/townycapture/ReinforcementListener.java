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
import org.bukkit.metadata.MetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class ReinforcementListener implements Listener {
    private static final String META_REINFORCEMENT = "capture_reinforcement";
    private static final String META_SOURCE = "reinforcement_source";
    private static final String META_MYTHIC_TYPE = "reinforcement_mythic_type";

    private final TownyCapture plugin;
    private final Map<String, List<UUID>> activeReinforcements = new HashMap<>();
    private final Map<String, BukkitTask> phaseTasks = new HashMap<>();
    private final Map<String, BukkitTask> trackingTasks = new HashMap<>();
    private final Map<String, Integer> currentPhase = new HashMap<>();
    private final Deque<SpawnRequest> spawnQueue = new ArrayDeque<>();
    private final Map<String, Integer> pendingSpawns = new HashMap<>();
    private final Random random = new Random();
    private BukkitTask spawnProcessorTask;
    private MobSpawner mobSpawner;

    public ReinforcementListener(TownyCapture plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Set the mob spawner implementation.
     * This is called by TownyCapture during initialization.
     */
    public void setMobSpawner(MobSpawner mobSpawner) {
        this.mobSpawner = mobSpawner;
        plugin.getLogger().info("Reinforcement mob spawner set to: " + mobSpawner.getName());
    }

    @EventHandler
    public void onEntityTarget(EntityTargetEvent event) {
        // Make reinforcement mobs only target capturing players
        if (!event.getEntity().hasMetadata(META_REINFORCEMENT)) {
            return;
        }

        if (!(event.getTarget() instanceof Player)) {
            event.setCancelled(true);
            return;
        }

        String pointId = event.getEntity().getMetadata(META_REINFORCEMENT).get(0).asString();
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
        if (!event.getEntity().hasMetadata(META_REINFORCEMENT)) {
            return;
        }

        // Clear all loot drops but keep XP
        event.getDrops().clear();

        String pointId = event.getEntity().getMetadata(META_REINFORCEMENT).get(0).asString();
        CaptureSession session = plugin.getActiveSession(pointId);

        // Only reward if there's an active capture
        if (session != null && session.isActive() && !session.isInPreparationPhase()) {
            int secondsReduced = getTimerReductionSeconds(pointId, event.getEntity());
            if (secondsReduced > 0) {
                int currentTime = session.getRemainingCaptureTime();
                int newTime = Math.max(0, currentTime - secondsReduced); // Don't go below 0
                session.setRemainingCaptureTime(newTime);

                // Notify the killer if it's a player
                Player killer = event.getEntity().getKiller();
                if (killer != null) {
                    plugin.sendNotification(killer, Messages.get("messages.reinforcement.timer-reduced", Map.of(
                        "seconds", String.valueOf(secondsReduced)
                    )));
                }
                
                // Check if we need to spawn a phase due to timer change
                checkAndSpawnPhase(pointId);
            }
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
        int intervalSeconds = getWaveIntervalSeconds(pointId);
        int expectedPhase = (elapsed / intervalSeconds) + 1;
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

    private int getWaveIntervalSeconds(String pointId) {
        int interval = plugin.getZoneConfigManager().getInt(pointId, "reinforcements.wave-interval", 30);
        return Math.max(1, interval);
    }

    private void spawnReinforcementWave(String pointId, CapturePoint point, int phase) {
        // Use zone-specific config
        if (!(boolean) plugin.getZoneConfigManager().getZoneSetting(pointId, "reinforcements.enabled", true)) {
            return;
        }

        // Get active session to check time remaining
        CaptureSession session = plugin.getActiveSession(pointId);
        if (session == null || !session.isActive() || session.isInPreparationPhase()) {
            return;
        }

        int timeLeft = session.getRemainingCaptureTime();
        
        // Stop spawning in last minute (60 seconds)
        if (timeLeft <= 60) {
            return;
        }

        // Calculate mobs based on phase (harder each phase)
        int baseMobs = ((Number) plugin.getZoneConfigManager().getZoneSetting(pointId, "reinforcements.mobs-per-wave", 1)).intValue();
        int mobsThisPhase = baseMobs + phase;
        
        // Cap at 12 mobs per wave (after phase 11+)
        int actualMobs = Math.min(mobsThisPhase, 12);
        int maxMobs = ((Number) plugin.getZoneConfigManager().getZoneSetting(pointId, "reinforcements.max-mobs-per-point", 50)).intValue();

        // Get capturing players to target
        String capturingTown = point.getCapturingTown();
        if (capturingTown == null || capturingTown.isEmpty()) {
            return;
        }
        com.palmergames.bukkit.towny.TownyAPI townyAPI = com.palmergames.bukkit.towny.TownyAPI.getInstance();
        
        // Enqueue spawns respecting the global cap; queue processor will rate-limit
        List<UUID> reinforcements = activeReinforcements.computeIfAbsent(pointId, k -> new ArrayList<>());
        int pending = pendingSpawns.getOrDefault(pointId, 0);
        int availableSlots = Math.max(0, maxMobs - (reinforcements.size() + pending));
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
                plugin.sendNotification(player, phaseMessage);
            }
        }
    }

    private void enqueueSpawn(String pointId) {
        spawnQueue.add(new SpawnRequest(pointId));
        pendingSpawns.merge(pointId, 1, Integer::sum);
        ensureSpawnProcessor();
    }

    private void ensureSpawnProcessor() {
        if (spawnProcessorTask != null && !spawnProcessorTask.isCancelled()) {
            return;
        }
        spawnProcessorTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::processSpawnQueue, 1L, 1L);
    }

    private void processSpawnQueue() {
        int maxIterations = spawnQueue.size();
        int attempts = 0;
        Map<String, Integer> perZoneProcessed = new HashMap<>();
        while (attempts < maxIterations) {
            SpawnRequest request = spawnQueue.poll();
            if (request == null) {
                break;
            }
            attempts++;
            decrementPending(request.pointId);

            int zoneLimit = getZoneSpawnLimit(request.pointId);
            int zoneProcessed = perZoneProcessed.getOrDefault(request.pointId, 0);
            if (zoneProcessed >= zoneLimit) {
                spawnQueue.add(request);
                incrementPending(request.pointId);
                continue;
            }

            CaptureSession session = plugin.getActiveSession(request.pointId);
            CapturePoint point = plugin.getCapturePoint(request.pointId);
            if (!isSpawnAllowed(session, point)) {
                removeQueuedSpawns(request.pointId);
                continue;
            }

            String capturingTown = point.getCapturingTown();

            List<UUID> reinforcements = activeReinforcements.computeIfAbsent(request.pointId, k -> new ArrayList<>());
            // Use zone-specific max-mobs-per-point
            int maxMobs = ((Number) plugin.getZoneConfigManager().getZoneSetting(request.pointId, "reinforcements.max-mobs-per-point", 50)).intValue();
            if (reinforcements.size() >= maxMobs) {
                continue;
            }

            if (spawnReinforcementMob(point, session, capturingTown, reinforcements)) {
                perZoneProcessed.put(request.pointId, zoneProcessed + 1);
            }
        }

        if (spawnQueue.isEmpty() && spawnProcessorTask != null) {
            spawnProcessorTask.cancel();
            spawnProcessorTask = null;
        }
    }

    private void decrementPending(String pointId) {
        Integer current = pendingSpawns.get(pointId);
        if (current == null) {
            return;
        }
        int next = current - 1;
        if (next <= 0) {
            pendingSpawns.remove(pointId);
        } else {
            pendingSpawns.put(pointId, next);
        }
    }

    private void incrementPending(String pointId) {
        pendingSpawns.merge(pointId, 1, Integer::sum);
    }

    private int getZoneSpawnLimit(String pointId) {
        int value = 3;
        if (plugin.getZoneConfigManager() != null && pointId != null) {
            value = plugin.getZoneConfigManager().getInt(pointId, "reinforcements.spawn-rate.max-per-tick", 3);
        } else if (plugin.getZoneConfigManager() != null) {
            value = plugin.getZoneConfigManager().getDefaultInt("reinforcements.spawn-rate.max-per-tick", 3);
        } else {
            value = plugin.getConfig().getInt("reinforcements.spawn-rate.max-per-tick", 3);
        }
        return Math.max(1, value);
    }

    private int getTimerReductionSeconds(String pointId, LivingEntity entity) {
        if (plugin.getZoneConfigManager() == null) {
            return getRandomReduction(1, 3);
        }

        String source = getMetadataString(entity, META_SOURCE);
        boolean isMythic = source != null && "MYTHIC".equalsIgnoreCase(source);
        if (isMythic) {
            int defaultMin = plugin.getZoneConfigManager().getInt(pointId, "reinforcements.timer-reduction.mythic.default.min", 1);
            int defaultMax = plugin.getZoneConfigManager().getInt(pointId, "reinforcements.timer-reduction.mythic.default.max", 3);
            String mythicType = getMetadataString(entity, META_MYTHIC_TYPE);
            if (mythicType != null && !mythicType.isEmpty()) {
                String basePath = "reinforcements.timer-reduction.mythic.per-mob." + mythicType;
                int min = plugin.getZoneConfigManager().getInt(pointId, basePath + ".min", defaultMin);
                int max = plugin.getZoneConfigManager().getInt(pointId, basePath + ".max", defaultMax);
                return getRandomReduction(min, max);
            }
            return getRandomReduction(defaultMin, defaultMax);
        }

        int vanillaMin = plugin.getZoneConfigManager().getInt(pointId, "reinforcements.timer-reduction.vanilla.min", 1);
        int vanillaMax = plugin.getZoneConfigManager().getInt(pointId, "reinforcements.timer-reduction.vanilla.max", 3);
        return getRandomReduction(vanillaMin, vanillaMax);
    }

    private int getRandomReduction(int min, int max) {
        int safeMin = Math.max(0, min);
        int safeMax = Math.max(safeMin, max);
        if (safeMax <= safeMin) {
            return safeMin;
        }
        return safeMin + random.nextInt(safeMax - safeMin + 1);
    }

    private String getMetadataString(Entity entity, String key) {
        if (entity == null || !entity.hasMetadata(key)) {
            return null;
        }
        List<MetadataValue> values = entity.getMetadata(key);
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.get(0).asString();
    }

    private boolean isSpawnAllowed(CaptureSession session, CapturePoint point) {
        if (session == null || point == null) {
            return false;
        }
        if (!session.isActive() || session.isInPreparationPhase()) {
            return false;
        }
        if (session.getTownName() == null || session.getTownName().isEmpty()) {
            return false;
        }
        String capturingTown = point.getCapturingTown();
        if (capturingTown == null || capturingTown.isEmpty()) {
            return false;
        }
        return capturingTown.equals(session.getTownName());
    }

    private void removeQueuedSpawns(String pointId) {
        if (spawnQueue.isEmpty()) {
            pendingSpawns.remove(pointId);
            return;
        }

        int removed = 0;
        Iterator<SpawnRequest> iterator = spawnQueue.iterator();
        while (iterator.hasNext()) {
            SpawnRequest request = iterator.next();
            if (request.pointId.equals(pointId)) {
                iterator.remove();
                removed++;
            }
        }

        if (removed > 0) {
            Integer current = pendingSpawns.get(pointId);
            if (current != null) {
                int next = current - removed;
                if (next <= 0) {
                    pendingSpawns.remove(pointId);
                } else {
                    pendingSpawns.put(pointId, next);
                }
            }
        }
    }

    private boolean spawnReinforcementMob(CapturePoint point, CaptureSession session, String capturingTown, List<UUID> reinforcements) {
        // Check if mob spawner is initialized
        if (mobSpawner == null) {
            plugin.getLogger().warning("MobSpawner not initialized! Cannot spawn reinforcements.");
            return false;
        }
        
        if (!mobSpawner.isAvailable()) {
            plugin.getLogger().warning("MobSpawner is not available! Cannot spawn reinforcements.");
            return false;
        }

        if (session == null || !session.isActive() || session.isInPreparationPhase()) {
            return false;
        }
        
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

        double angle = random.nextDouble() * 2 * Math.PI;
        double radius = random.nextDouble() * 8 + 3; // 3-11 blocks away
        Location spawnLoc = origin.clone().add(Math.cos(angle) * radius, 0.5, Math.sin(angle) * radius);

        // Ensure chunk is loaded and find safe Y
        spawnLoc.getChunk().load();
        int surfaceY = spawnLoc.getWorld().getHighestBlockYAt(spawnLoc.getBlockX(), spawnLoc.getBlockZ());
        spawnLoc.setY(surfaceY + 1.0);

        // Use the mob spawner abstraction to spawn the mob
        LivingEntity mob = mobSpawner.spawnMob(point.getId(), spawnLoc, originPlayer);
        
        if (mob == null) {
            plugin.getLogger().warning("Failed to spawn reinforcement mob at " + spawnLoc);
            return false;
        }

        // Set metadata to mark as reinforcement
        mob.setMetadata(META_REINFORCEMENT, new FixedMetadataValue(plugin, point.getId()));
        mob.setMetadata("capture_point_id", new FixedMetadataValue(plugin, point.getId()));
        mob.setMetadata("capturing_town", new FixedMetadataValue(plugin, capturingTown));

        reinforcements.add(mob.getUniqueId());

        // Make mob persistent (won't despawn naturally)
        mob.setRemoveWhenFarAway(false);
        mob.setPersistent(true);

        // Add fire resistance to prevent burning
        mob.addPotionEffect(new PotionEffect(
            PotionEffectType.FIRE_RESISTANCE, 
            999999, 
            1, 
            true, 
            false
        ));
        
        // Handle special mob types
        if (mob instanceof Slime) {
            ((Slime) mob).setSize(2); // Medium size
        }
        if (mob instanceof MagmaCube) {
            ((MagmaCube) mob).setSize(2); // Medium size
        }

        // Initial target setup (will be continuously updated by tracking task)
        updateMobTarget(mob, point, capturingTown);
        return true;
    }
    
    /**
     * Updates a mob's target to the nearest capturing player.
     */
    private void updateMobTarget(Entity mobEntity, CapturePoint point, String capturingTown) {
        updateMobTarget(mobEntity, point, getEligibleTargets(point, capturingTown));
    }

    private void updateMobTarget(Entity mobEntity, CapturePoint point, List<Player> candidates) {
        if (!(mobEntity instanceof Creature)) {
            return;
        }
        
        if (mobEntity.isDead() || !mobEntity.isValid()) {
            return;
        }

        if (point == null || candidates.isEmpty()) {
            return;
        }

        if (mobEntity.getWorld() != point.getLocation().getWorld()) {
            return;
        }
        
        Creature creature = (Creature) mobEntity;
        Player nearestTarget = null;
        double closestDist = Double.MAX_VALUE;

        for (Player player : candidates) {
            if (!player.isOnline() || player.isDead()) {
                continue;
            }
            double dist = player.getLocation().distance(mobEntity.getLocation());
            if (dist < closestDist) {
                closestDist = dist;
                nearestTarget = player;
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

    private List<Player> getEligibleTargets(CapturePoint point, String capturingTown) {
        if (point == null || capturingTown == null || capturingTown.isEmpty()) {
            return Collections.emptyList();
        }

        Location center = point.getLocation();
        if (center == null || center.getWorld() == null) {
            return Collections.emptyList();
        }

        int blockRadius = point.getChunkRadius() * 16;
        double maxDistance = blockRadius + 50;
        com.palmergames.bukkit.towny.TownyAPI townyAPI = com.palmergames.bukkit.towny.TownyAPI.getInstance();
        List<Player> candidates = new ArrayList<>();

        for (Player player : center.getWorld().getPlayers()) {
            if (!player.isOnline() || player.isDead()) {
                continue;
            }

            com.palmergames.bukkit.towny.object.Town playerTown = townyAPI.getTown(player);
            if (playerTown == null || !playerTown.getName().equals(capturingTown)) {
                continue;
            }

            double blockDist = plugin.getChunkDistance(player.getLocation(), center) * 16;
            if (blockDist <= maxDistance) {
                candidates.add(player);
            }
        }

        return candidates;
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

            CapturePoint point = plugin.getCapturePoint(pointId);
            if (point == null) {
                stopMobTracking(pointId);
                return;
            }

            List<Player> candidates = getEligibleTargets(point, capturingTown);
            if (candidates.isEmpty()) {
                return;
            }
            
            // Update target for each mob
            for (UUID mobUUID : new ArrayList<>(reinforcements)) {
                Entity mob = plugin.getServer().getEntity(mobUUID);
                if (mob != null && mob.isValid() && !mob.isDead()) {
                    updateMobTarget(mob, point, candidates);
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
        removeQueuedSpawns(pointId);
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
        pendingSpawns.clear();
        
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
        // Use zone-specific config
        if (!(boolean) plugin.getZoneConfigManager().getZoneSetting(pointId, "reinforcements.enabled", true)) {
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

        // Check every second for timer mark based on wave interval
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
            
            int intervalSeconds = getWaveIntervalSeconds(pointId);
            boolean isAtMark = (timeLeft % intervalSeconds == 0);
            
            if (isAtMark) {
                // Calculate expected phase based on timer position
                int initialTime = session.getInitialCaptureTime();
                int elapsed = initialTime - timeLeft;
                int expectedPhase = (elapsed / intervalSeconds) + 1;
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
