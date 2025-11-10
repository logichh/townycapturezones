/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.palmergames.bukkit.towny.TownyAPI
 *  com.palmergames.bukkit.towny.object.Resident
 *  org.bukkit.Bukkit
 *  org.bukkit.Location
 *  org.bukkit.command.CommandSender
 *  org.bukkit.entity.Player
 *  org.bukkit.event.EventHandler
 *  org.bukkit.event.Listener
 *  org.bukkit.event.player.PlayerMoveEvent
 *  org.bukkit.event.player.PlayerQuitEvent
 *  org.bukkit.metadata.MetadataValue
 *  org.bukkit.plugin.Plugin
 */
package com.logichh.townycapture;

import com.logichh.townycapture.CapturePoint;
import com.logichh.townycapture.CaptureSession;
import com.logichh.townycapture.TownyCapture;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Resident;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.Plugin;

public class CaptureEvents
implements Listener {
    private TownyCapture plugin;
    private Map<UUID, CapturePoint> playerZones = new HashMap<UUID, CapturePoint>();
    private Map<UUID, Integer> actionBarTasks = new HashMap<UUID, Integer>();

    public CaptureEvents(TownyCapture plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location to = event.getTo();
        Location from = event.getFrom();
        CapturePoint enteredZone = null;
        CapturePoint leftZone = null;
        boolean enteredBuffer = false;

        // Skip if player hasn't moved to a different block
        if (from != null && to != null && 
            from.getBlockX() == to.getBlockX() && 
            from.getBlockY() == to.getBlockY() && 
            from.getBlockZ() == to.getBlockZ()) {
            return;
        }

        for (CapturePoint point : this.plugin.getCapturePoints().values()) {
            // Calculate actual block distances (circular zones)
            double currentBlockDistance = this.plugin.getChunkDistance(point.getLocation(), to);
            double previousBlockDistance = from != null ? this.plugin.getChunkDistance(point.getLocation(), from) : Double.MAX_VALUE;
            
            // Convert chunk radius to block distance for circular detection
            int blockRadius = point.getChunkRadius() * 16;
            
            // Check if player is in the capture zone (circular)
            boolean wasInZone = previousBlockDistance * 16 <= blockRadius;
            boolean isInZone = currentBlockDistance * 16 <= blockRadius;
            
            // Check if player is in the buffer zone (circular, 1 chunk outside)
            boolean wasInBuffer = previousBlockDistance * 16 <= blockRadius + 16;
            boolean isInBuffer = currentBlockDistance * 16 <= blockRadius + 16;

            // Buffer zone messages
            if (!wasInBuffer && isInBuffer) {
                enteredBuffer = true;
                String enterMessage = "&e\u26a0 You are approaching capture zone: &6%zone%";
                enterMessage = enterMessage.replace("%zone%", point.getName());
                player.sendMessage(this.plugin.colorize(enterMessage));
            }

            // Capture zone messages
            if (!wasInZone && isInZone) {
                enteredZone = point;
                String enterZoneMessage = "&c\u2694 &lYOU HAVE ENTERED CAPTURE ZONE: &6%zone% &c\u2694";
                enterZoneMessage = enterZoneMessage.replace("%zone%", point.getName());
                player.sendMessage(this.plugin.colorize(enterZoneMessage));
                this.startContinuousActionBar(player, point);
                continue;
            }

            // Left zone message
            if (wasInZone && !isInZone) {
                leftZone = point;
                String leaveZoneMessage = "&c\u2694 &lYOU HAVE LEFT CAPTURE ZONE: &6%zone% &c\u2694";
                leaveZoneMessage = leaveZoneMessage.replace("%zone%", point.getName());
                player.sendMessage(this.plugin.colorize(leaveZoneMessage));
                this.stopContinuousActionBar(player);
            }
        }

        // Check for capture session distance
        CaptureSession session = this.plugin.getActiveSessions().values().stream()
                .filter(s -> s.getPlayerName().equals(player.getName()))
                .findFirst()
                .orElse(null);

        if (session != null) {
            // Calculate actual block distance for circular detection
            double currentBlockDistance = this.plugin.getChunkDistance(session.getPoint().getLocation(), to);
            int blockRadius = session.getPoint().getChunkRadius() * 16;

            if (currentBlockDistance * 16 > blockRadius) {
                this.plugin.cancelCapture(session.getPointId(), "Player moved too far from capture zone");
                String movedTooFar = this.plugin.getConfig().getString("messages.errors.moved-too-far", "&cCapture cancelled! You moved too far from the capture point.");
                player.sendMessage(this.plugin.colorize(movedTooFar));
                return;
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        this.stopContinuousActionBar(player);
        this.playerZones.remove(player.getUniqueId());
        
        // Check if this player has any active capture sessions
        for (Map.Entry<String, CaptureSession> entry : this.plugin.getActiveSessions().entrySet()) {
            CaptureSession session = entry.getValue();
            String pointId = entry.getKey();
            
            // If this player is the initiator of the capture
            if (session.getPlayerName().equals(player.getName())) {
                // Remove player from session
                session.getPlayers().remove(player);
                
                // Check if there are any other players in the session
                boolean hasOnlinePlayers = false;
                for (Player sessionPlayer : session.getPlayers()) {
                    if (sessionPlayer != null && sessionPlayer.isOnline()) {
                        hasOnlinePlayers = true;
                        break;
                    }
                }
                
                // If no other players are online, cancel the capture
                if (!hasOnlinePlayers) {
                    this.plugin.cancelCapture(pointId, "All participants disconnected");
                }
            }
        }
        
        // Clean up any boundary visualization tasks for this player
        for (Map.Entry<String, org.bukkit.scheduler.BukkitTask> entry : this.plugin.boundaryTasks.entrySet()) {
            if (entry.getKey().startsWith(player.getUniqueId().toString())) {
                if (entry.getValue() != null) {
                    entry.getValue().cancel();
                }
            }
        }
        // Remove the entries
        this.plugin.boundaryTasks.entrySet().removeIf(entry -> entry.getKey().startsWith(player.getUniqueId().toString()));
    }

    private void startContinuousActionBar(Player player, CapturePoint zone) {
        this.playerZones.put(player.getUniqueId(), zone);
        this.stopContinuousActionBar(player);
        int taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask((Plugin)this.plugin, () -> {
            String message;
            if (!player.isOnline()) {
                this.stopContinuousActionBar(player);
                return;
            }
            if (!this.plugin.isWithinChunkRadius(zone.getLocation(), player.getLocation(), zone.getChunkRadius())) {
                this.stopContinuousActionBar(player);
                return;
            }
            CaptureSession session = this.plugin.getActiveSessions().get(zone.getId());
            if (session != null) {
                message = this.plugin.getConfig().getString("messages.zone.continuous-capturing", "&c\u2694 &c&l%town% IS CAPTURING %zone% &c\u2694");
                message = message.replace("%town%", session.getTownName()).replace("%zone%", zone.getName());
            } else {
                message = this.plugin.getConfig().getString("messages.zone.continuous", "&c\u26a0 &c&lYOU ARE IN CAPTURE ZONE %zone% &c\u26a0");
                message = message.replace("%zone%", zone.getName());
            }
            this.sendActionBarMessage(player, message);
        }, 0L, 20L);
        this.actionBarTasks.put(player.getUniqueId(), taskId);
    }

    private void stopContinuousActionBar(Player player) {
        Integer taskId = this.actionBarTasks.remove(player.getUniqueId());
        if (taskId != null) {
            Bukkit.getScheduler().cancelTask(taskId.intValue());
        }
    }

    private void sendActionBarMessage(Player player, String message) {
        String playerName = player.getName();
        String command = "cmi actionbarmsg " + playerName + " " + this.plugin.colorize(message);
        Bukkit.getServer().dispatchCommand((CommandSender)Bukkit.getConsoleSender(), command);
    }

    private boolean isInvulnerable(Player player) {
        try {
            TownyAPI api = TownyAPI.getInstance();
            Resident resident = api.getResident(player.getUniqueId());
            if (resident == null) {
                return false;
            }
            long registeredTime = resident.getRegistered();
            long timeElapsed = System.currentTimeMillis() - registeredTime;
            long days7 = 604800000L;
            if (player.hasMetadata("invulnerable")) {
                return ((MetadataValue)player.getMetadata("invulnerable").get(0)).asBoolean();
            }
            return timeElapsed < days7;
        }
        catch (Exception e) {
            this.plugin.getLogger().warning("Error getting towny registration time: " + e.getMessage());
            return false;
        }
    }
}
