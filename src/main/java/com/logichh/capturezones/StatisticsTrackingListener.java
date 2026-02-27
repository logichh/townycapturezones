package com.logichh.capturezones;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

/**
 * Listener for tracking combat statistics within capture zones.
 */
public class StatisticsTrackingListener implements Listener {
    
    private final CaptureZones plugin;
    private final StatisticsManager statsManager;
    
    public StatisticsTrackingListener(CaptureZones plugin) {
        this.plugin = plugin;
        this.statsManager = plugin.getStatisticsManager();
    }
    
    /**
     * Track kills and deaths within capture zones
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (statsManager == null) return;
        
        LivingEntity victim = event.getEntity();
        Entity killerEntity = victim.getKiller();
        
        // Check if death occurred in a capture zone
        String zoneId = findZoneAtLocation(victim.getLocation());
        if (zoneId == null) return;
        
        // Player killed another player
        if (victim instanceof Player && killerEntity instanceof Player) {
            Player killerPlayer = (Player) killerEntity;
            Player victimPlayer = (Player) victim;
            
            statsManager.onPlayerKillInZone(
                killerPlayer.getUniqueId(), 
                victimPlayer.getUniqueId(), 
                zoneId
            );
        }
        // Player killed a mob
        else if (!(victim instanceof Player) && killerEntity instanceof Player) {
            Player killerPlayer = (Player) killerEntity;
            statsManager.onMobKillInZone(killerPlayer.getUniqueId(), zoneId);
        }
    }
    
    /**
     * Helper method to find if a location is within any capture zone
     */
    private String findZoneAtLocation(org.bukkit.Location location) {
        for (CapturePoint point : plugin.getCandidateCapturePoints(location, 0)) {
            if (plugin.isWithinZone(point, location)) {
                return point.getId();
            }
        }
        return null;
    }
}

