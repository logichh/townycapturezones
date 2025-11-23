/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.entity.Player
 *  org.bukkit.event.EventHandler
 *  org.bukkit.event.Listener
 *  org.bukkit.event.entity.PlayerDeathEvent
 */
package com.logichh.townycapture;

import com.logichh.townycapture.CapturePoint;
import com.logichh.townycapture.CaptureSession;
import com.logichh.townycapture.TownyCapture;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

public class CaptureDeathListener
implements Listener {
    private final TownyCapture plugin;

    public CaptureDeathListener(TownyCapture plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        CaptureSession session = plugin.getActiveSessions().values().stream()
                .filter(s -> s.getPlayerName().equals(player.getName()))
                .findFirst()
                .orElse(null);

        if (session != null) {
            plugin.cancelCaptureByDeath(session.getPointId(), player.getName(), "Death");
        }
    }
}
