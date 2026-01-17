package com.logichh.townycapture;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/**
 * Handles inventory interactions for statistics GUI menus.
 */
public class StatisticsGUIListener implements Listener {
    
    private final TownyCapture plugin;
    private final StatisticsGUI gui;
    
    public StatisticsGUIListener(TownyCapture plugin, StatisticsGUI gui) {
        this.plugin = plugin;
        this.gui = gui;
    }
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        
        String title = event.getView().getTitle();
        
        // Check if it's a statistics menu
        if (!title.contains("Statistics") && !title.contains("Server Records")) {
            return;
        }
        
        // Cancel all clicks in statistics menus
        event.setCancelled(true);
        
        Player player = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();
        
        // Only handle clicks in the top inventory
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) {
            return;
        }
        
        // Handle the click
        gui.handleClick(player, slot, event.getClickedInventory());
    }
    
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        
        String title = event.getView().getTitle();
        
        // Check if it's a statistics menu
        if (!title.contains("Statistics") && !title.contains("Server Records")) {
            return;
        }
        
        Player player = (Player) event.getPlayer();
        
        // Delay removal to allow navigation between menus
        // If player opens a new stats menu immediately, don't clear the session
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            // Only clear if player doesn't have a stats menu open
            String currentTitle = player.getOpenInventory().getTitle();
            if (!currentTitle.contains("Statistics") && !currentTitle.contains("Server Records")) {
                gui.closeMenu(player);
            }
        }, 1L);
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        String title = event.getView().getTitle();
        if (!title.contains("Statistics") && !title.contains("Server Records")) {
            return;
        }

        event.setCancelled(true);
    }
}
