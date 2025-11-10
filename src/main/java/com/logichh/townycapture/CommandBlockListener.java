/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.entity.Player
 *  org.bukkit.event.EventHandler
 *  org.bukkit.event.Listener
 *  org.bukkit.event.player.PlayerCommandPreprocessEvent
 */
package com.logichh.townycapture;

import com.logichh.townycapture.CapturePoint;
import com.logichh.townycapture.TownyCapture;
import java.util.List;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

public class CommandBlockListener
implements Listener {
    private final TownyCapture plugin;

    public CommandBlockListener(TownyCapture plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage().toLowerCase();
        if (player.isOp()) {
            return;
        }
        for (CapturePoint point : this.plugin.getCapturePoints().values()) {
            boolean isInCaptureZone = this.plugin.isWithinChunkRadius(point.getLocation(), player.getLocation(), point.getChunkRadius());
            boolean isInBufferZone = this.plugin.isWithinChunkRadius(point.getLocation(), player.getLocation(), point.getChunkRadius() + 1);
            if (!isInCaptureZone && !isInBufferZone) continue;
            List<String> blockedCommands = this.plugin.getConfig().getStringList("blocked-commands");
            for (String blockedCommand : blockedCommands) {
                if (!message.startsWith(blockedCommand.toLowerCase())) continue;
                event.setCancelled(true);
                String zone = isInCaptureZone ? "capture zone" : "buffer zone";
                player.sendMessage(this.plugin.colorize("&c\u26a0 That command is blocked in the " + zone + "!"));
                return;
            }
            if (!isInBufferZone || !message.startsWith("/t new") && !message.startsWith("/t claim") && !message.startsWith("/town new") && !message.startsWith("/town claim")) continue;
            event.setCancelled(true);
            player.sendMessage(this.plugin.colorize("&c\u26a0 You cannot claim land or create towns near a capture zone!"));
            return;
        }
    }
}
