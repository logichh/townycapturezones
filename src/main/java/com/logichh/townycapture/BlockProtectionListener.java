/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.World
 *  org.bukkit.entity.Player
 *  org.bukkit.event.EventHandler
 *  org.bukkit.event.Listener
 *  org.bukkit.event.block.BlockBreakEvent
 *  org.bukkit.event.block.BlockPlaceEvent
 */
package com.logichh.townycapture;

import com.logichh.townycapture.CapturePoint;
import com.logichh.townycapture.TownyCapture;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

public class BlockProtectionListener
implements Listener {
    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (event == null || event.getPlayer() == null || event.getBlock() == null) {
            return;
        }
        Player player = event.getPlayer();
        TownyCapture plugin = (TownyCapture)TownyCapture.getPlugin(TownyCapture.class);
        if (player.isOp()) {
            return;
        }
        for (CapturePoint point : plugin.getCapturePoints().values()) {
            double chunkDistance;
            World blockWorld = event.getBlock().getWorld();
            if (blockWorld == null || !blockWorld.getUID().equals(point.getWorldUUID()) || !((chunkDistance = plugin.getChunkDistance(point.getLocation(), event.getBlock().getLocation())) <= (double)(point.getChunkRadius() + 1))) continue;
            event.setCancelled(true);
            if (chunkDistance <= (double)point.getChunkRadius()) {
                player.sendMessage(plugin.colorize(plugin.getConfig().getString("messages.protection.break-in-zone", "&cYou cannot break blocks in capture zones!")));
            } else {
                player.sendMessage(plugin.colorize(plugin.getConfig().getString("messages.protection.break-in-buffer", "&cYou cannot break blocks in the buffer zone around capture zones!")));
            }
            return;
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (event == null || event.getPlayer() == null || event.getBlock() == null) {
            return;
        }
        Player player = event.getPlayer();
        TownyCapture plugin = (TownyCapture)TownyCapture.getPlugin(TownyCapture.class);
        if (player.isOp()) {
            return;
        }
        for (CapturePoint point : plugin.getCapturePoints().values()) {
            double chunkDistance;
            World blockWorld = event.getBlock().getWorld();
            if (blockWorld == null || !blockWorld.getUID().equals(point.getWorldUUID()) || !((chunkDistance = plugin.getChunkDistance(point.getLocation(), event.getBlock().getLocation())) <= (double)(point.getChunkRadius() + 1))) continue;
            event.setCancelled(true);
            if (chunkDistance <= (double)point.getChunkRadius()) {
                player.sendMessage(plugin.colorize(plugin.getConfig().getString("messages.protection.place-in-zone", "&cYou cannot place blocks in capture zones!")));
            } else {
                player.sendMessage(plugin.colorize(plugin.getConfig().getString("messages.protection.place-in-buffer", "&cYou cannot place blocks in the buffer zone around capture zones!")));
            }
            return;
        }
    }
}
