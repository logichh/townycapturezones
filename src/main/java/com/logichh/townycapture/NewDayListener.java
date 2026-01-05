package com.logichh.townycapture;

import com.logichh.townycapture.TownyCapture;
import com.palmergames.bukkit.towny.event.NewDayEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class NewDayListener
implements Listener {
    private final TownyCapture plugin;

    public NewDayListener(TownyCapture plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onNewDay(NewDayEvent event) {
        this.plugin.handleNewDay();
    }
}
