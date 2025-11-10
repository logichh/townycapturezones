/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Location
 *  org.bukkit.entity.Player
 *  org.bukkit.event.EventHandler
 *  org.bukkit.event.Listener
 *  org.bukkit.event.block.BlockBreakEvent
 *  org.bukkit.event.block.BlockPlaceEvent
 *  org.bukkit.event.entity.EntityDamageByEntityEvent
 *  org.bukkit.event.entity.EntityExplodeEvent
 *  org.bukkit.event.player.PlayerCommandPreprocessEvent
 *  org.bukkit.event.player.PlayerInteractEvent
 *  org.bukkit.event.player.PlayerTeleportEvent
 */
package com.logichh.townycapture;

import com.logichh.townycapture.CapturePoint;
import com.logichh.townycapture.TownyCapture;
import org.bukkit.Location;
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

public class ZoneProtectionListener
implements Listener {
    private final TownyCapture plugin;

    public ZoneProtectionListener(TownyCapture plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (!this.plugin.getConfig().getBoolean("protection.prevent-block-break", true)) {
            return;
        }
        if (event == null || event.getPlayer() == null || event.getBlock() == null) {
            return;
        }
        if (event.getPlayer().isOp()) {
            return;
        }
        if (this.isInProtectedZone(event.getBlock().getLocation())) {
            event.setCancelled(true);
            String message = this.plugin.getConfig().getString("protection.messages.block-break-blocked", "&cYou cannot break blocks in protected zones!");
            event.getPlayer().sendMessage(this.plugin.colorize(message));
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!this.plugin.getConfig().getBoolean("protection.prevent-block-place", true)) {
            return;
        }
        if (event == null || event.getPlayer() == null || event.getBlock() == null) {
            return;
        }
        if (event.getPlayer().isOp()) {
            return;
        }
        if (this.isInProtectedZone(event.getBlock().getLocation())) {
            event.setCancelled(true);
            String message = this.plugin.getConfig().getString("protection.messages.block-place-blocked", "&cYou cannot place blocks in protected zones!");
            event.getPlayer().sendMessage(this.plugin.colorize(message));
        }
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        if (!this.plugin.getConfig().getBoolean("protection.prevent-explosions", true)) {
            return;
        }
        if (event == null || event.getLocation() == null) {
            return;
        }
        if (this.isInProtectedZone(event.getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!this.plugin.getConfig().getBoolean("protection.prevent-pvp", false)) {
            return;
        }
        if (event == null || event.getEntity() == null || event.getDamager() == null) {
            return;
        }
        if (!(event.getEntity() instanceof Player) || !(event.getDamager() instanceof Player)) {
            return;
        }
        if (this.isInProtectedZone(event.getEntity().getLocation())) {
            event.setCancelled(true);
            String message = this.plugin.getConfig().getString("protection.messages.pvp-blocked", "&cPvP is blocked in protected zones!");
            ((Player)event.getDamager()).sendMessage(this.plugin.colorize(message));
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!this.plugin.getConfig().getBoolean("protection.prevent-item-use", true)) {
            return;
        }
        if (event == null || event.getPlayer() == null) {
            return;
        }
        if (event.getPlayer().isOp()) {
            return;
        }
        if (this.isInProtectedZone(event.getPlayer().getLocation())) {
            event.setCancelled(true);
            String message = this.plugin.getConfig().getString("protection.messages.item-use-blocked", "&cYou cannot use items in protected zones!");
            event.getPlayer().sendMessage(this.plugin.colorize(message));
        }
    }

    @EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (!this.plugin.getConfig().getBoolean("protection.prevent-teleporting", true)) {
            return;
        }
        if (event == null || event.getPlayer() == null || event.getTo() == null) {
            return;
        }
        if (event.getPlayer().isOp()) {
            return;
        }
        if (this.isInProtectedZone(event.getTo())) {
            event.setCancelled(true);
            String message = this.plugin.getConfig().getString("protection.messages.teleport-blocked", "&cYou cannot teleport into protected zones!");
            event.getPlayer().sendMessage(this.plugin.colorize(message));
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
        String command = event.getMessage().toLowerCase();
        if (this.plugin.getConfig().getBoolean("protection.prevent-homes", true) && (command.startsWith("/home") || command.startsWith("/homes") || command.startsWith("/sethome") || command.startsWith("/delhome")) && this.isInProtectedZone(event.getPlayer().getLocation())) {
            event.setCancelled(true);
            String message = this.plugin.getConfig().getString("protection.messages.home-blocked", "&cHome commands are blocked in protected zones!");
            event.getPlayer().sendMessage(this.plugin.colorize(message));
        }
        if (this.plugin.getConfig().getBoolean("protection.prevent-claiming", true) && (command.startsWith("/t claim") || command.startsWith("/town claim") || command.startsWith("/n claim") || command.startsWith("/nation claim")) && this.isInProtectedZone(event.getPlayer().getLocation())) {
            event.setCancelled(true);
            String message = this.plugin.getConfig().getString("protection.messages.claiming-blocked", "&cClaiming is blocked in protected zones!");
            event.getPlayer().sendMessage(this.plugin.colorize(message));
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

    private boolean isInProtectedZone(Location location) {
        int bufferSize = this.plugin.getConfig().getBoolean("protection.buffer-zone.enabled", true) ? this.plugin.getConfig().getInt("protection.buffer-zone.size", 1) : 0;
        for (CapturePoint point : this.plugin.getCapturePoints().values()) {
            // Calculate actual block distance for circular zones
            double blockDistance = this.plugin.getChunkDistance(point.getLocation(), location) * 16;
            int blockRadius = (point.getChunkRadius() + bufferSize) * 16;
            
            if (blockDistance <= blockRadius) {
                return true;
            }
        }
        return false;
    }
}
