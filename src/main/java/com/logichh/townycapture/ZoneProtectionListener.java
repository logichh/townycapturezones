package com.logichh.townycapture;

import com.logichh.townycapture.CapturePoint;
import com.logichh.townycapture.TownyCapture;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public class ZoneProtectionListener implements Listener {
    private final TownyCapture plugin;

    public ZoneProtectionListener(TownyCapture plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (event == null || event.getPlayer() == null || event.getBlock() == null) {
            return;
        }
        if (event.getPlayer().isOp()) {
            return;
        }
        String zoneId = getZoneIdAtLocation(event.getBlock().getLocation());
        if (zoneId != null) {
            boolean preventBreak = (boolean) plugin.getZoneConfigManager().getZoneSetting(zoneId, "protection.prevent-block-break", true);
            if (preventBreak) {
                event.setCancelled(true);
                plugin.sendNotification(event.getPlayer(), Messages.get("protection.block-break-blocked"));
            }
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (event == null || event.getPlayer() == null || event.getBlock() == null) {
            return;
        }
        if (event.getPlayer().isOp()) {
            return;
        }
        String zoneId = getZoneIdAtLocation(event.getBlock().getLocation());
        if (zoneId != null) {
            boolean preventPlace = (boolean) plugin.getZoneConfigManager().getZoneSetting(zoneId, "protection.prevent-block-place", true);
            if (preventPlace) {
                event.setCancelled(true);
                plugin.sendNotification(event.getPlayer(), Messages.get("protection.block-place-blocked"));
            }
        }
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        if (event == null || event.getLocation() == null) {
            return;
        }
        String zoneId = getZoneIdAtLocation(event.getLocation());
        if (zoneId != null) {
            boolean preventExplosions = (boolean) plugin.getZoneConfigManager().getZoneSetting(zoneId, "protection.prevent-explosions", true);
            if (preventExplosions) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event == null || event.getEntity() == null || event.getDamager() == null) {
            return;
        }
        if (!(event.getEntity() instanceof Player) || !(event.getDamager() instanceof Player)) {
            return;
        }
        String zoneId = getZoneIdAtLocation(event.getEntity().getLocation());
        if (zoneId != null) {
            boolean preventPvp = (boolean) plugin.getZoneConfigManager().getZoneSetting(zoneId, "protection.prevent-pvp", false);
            if (preventPvp) {
                event.setCancelled(true);
                plugin.sendNotification((Player) event.getDamager(), Messages.get("protection.pvp-blocked"));
            }
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event == null || event.getPlayer() == null) {
            return;
        }
        if (event.getPlayer().isOp()) {
            return;
        }
        String zoneId = getZoneIdAtLocation(event.getPlayer().getLocation());
        if (zoneId != null) {
            boolean preventItemUse = (boolean) plugin.getZoneConfigManager().getZoneSetting(zoneId, "protection.prevent-item-use", true);
            if (preventItemUse) {
                Material itemType = event.getItem() != null ? event.getItem().getType() : null;
                if (itemType == Material.ENDER_PEARL) {
                    boolean allowEnderPearls = (boolean) plugin.getZoneConfigManager().getZoneSetting(zoneId, "protection.allow-ender-pearls", false);
                    if (allowEnderPearls) {
                        return;
                    }
                } else if (itemType == Material.CHORUS_FRUIT) {
                    boolean allowChorusFruit = (boolean) plugin.getZoneConfigManager().getZoneSetting(zoneId, "protection.allow-chorus-fruit", false);
                    if (allowChorusFruit) {
                        return;
                    }
                }
                event.setCancelled(true);
                plugin.sendNotification(event.getPlayer(), Messages.get("protection.item-use-blocked"));
            }
        }
    }

    @EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (event == null || event.getPlayer() == null || event.getTo() == null) {
            return;
        }
        if (event.getPlayer().isOp()) {
            return;
        }
        String zoneId = getZoneIdAtLocation(event.getTo());
        if (zoneId != null) {
            boolean preventTeleporting = (boolean) plugin.getZoneConfigManager().getZoneSetting(zoneId, "protection.prevent-teleporting", true);
            if (preventTeleporting) {
                PlayerTeleportEvent.TeleportCause cause = event.getCause();
                if (cause == PlayerTeleportEvent.TeleportCause.ENDER_PEARL) {
                    boolean allowEnderPearls = (boolean) plugin.getZoneConfigManager().getZoneSetting(zoneId, "protection.allow-ender-pearls", false);
                    if (allowEnderPearls) {
                        return;
                    }
                } else if (cause == PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT) {
                    boolean allowChorusFruit = (boolean) plugin.getZoneConfigManager().getZoneSetting(zoneId, "protection.allow-chorus-fruit", false);
                    if (allowChorusFruit) {
                        return;
                    }
                }
                event.setCancelled(true);
                plugin.sendNotification(event.getPlayer(), Messages.get("protection.teleport-blocked"));
            }
        }
    }

    @EventHandler
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (event == null || event.getPlayer() == null || event.getMessage() == null) {
            return;
        }
        if (event.getPlayer().isOp()) {
            return;
        }
        String zoneId = getZoneIdAtLocation(event.getPlayer().getLocation());
        if (zoneId != null) {
            String command = event.getMessage().toLowerCase();
            
            boolean preventHomes = (boolean) plugin.getZoneConfigManager().getZoneSetting(zoneId, "protection.prevent-homes", true);
            if (preventHomes && (command.startsWith("/home") || command.startsWith("/homes") || command.startsWith("/sethome") || command.startsWith("/delhome"))) {
                event.setCancelled(true);
                plugin.sendNotification(event.getPlayer(), Messages.get("protection.home-blocked"));
                return;
            }
            
            boolean preventClaiming = (boolean) plugin.getZoneConfigManager().getZoneSetting(zoneId, "protection.prevent-claiming", true);
            if (preventClaiming && (command.startsWith("/t claim") || command.startsWith("/town claim") || command.startsWith("/n claim") || command.startsWith("/nation claim"))) {
                event.setCancelled(true);
                plugin.sendNotification(event.getPlayer(), Messages.get("protection.claiming-blocked"));
            }
        }
    }

    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        // Only prevent natural spawning in capture zones
        // Allow all plugin/spawner/egg/custom spawns
        if (event == null || event.getLocation() == null || event.getEntity() == null) {
            return;
        }
        
        CreatureSpawnEvent.SpawnReason reason = event.getSpawnReason();
        
        // Only block natural world spawning (NATURAL, DEFAULT)
        // Allow: CUSTOM (plugin spawns like our reinforcements), SPAWNER (mob spawners), 
        //        EGG (spawn eggs), BUILD (builds), etc.
        if (reason == CreatureSpawnEvent.SpawnReason.NATURAL || 
            reason == CreatureSpawnEvent.SpawnReason.DEFAULT) {
            
            // Prevent natural mob spawning in capture zones
            if (this.isInProtectedZone(event.getLocation())) {
                event.setCancelled(true);
            }
        }
        // All other spawn reasons (including CUSTOM for reinforcement mobs) are allowed
    }

    /**
     * Get the zone ID for a location, or null if not in a zone
     */
    private String getZoneIdAtLocation(Location location) {
        for (CapturePoint point : this.plugin.getCapturePoints().values()) {
            // Get buffer size from zone-specific config
            boolean bufferEnabled = (boolean) plugin.getZoneConfigManager().getZoneSetting(point.getId(), "protection.buffer-zone.enabled", true);
            int bufferSize = bufferEnabled ? ((Number) plugin.getZoneConfigManager().getZoneSetting(point.getId(), "protection.buffer-zone.size", 1)).intValue() : 0;
            
            // Calculate actual block distance for circular zones
            double blockDistance = this.plugin.getChunkDistance(point.getLocation(), location) * 16;
            int blockRadius = (point.getChunkRadius() + bufferSize) * 16;
            
            if (blockDistance <= blockRadius) {
                return point.getId();
            }
        }
        return null;
    }
    
    /**
     * @deprecated Use getZoneIdAtLocation instead for per-zone config support
     */
    @Deprecated
    private boolean isInProtectedZone(Location location) {
        return getZoneIdAtLocation(location) != null;
    }
}
