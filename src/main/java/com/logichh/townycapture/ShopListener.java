package com.logichh.townycapture;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles all shop GUI interactions
 */
public class ShopListener implements Listener {
    
    private final TownyCapture plugin;
    private final Map<Player, ShopGUI> activeShops;
    private final Map<Player, ShopEditorGUI> activeEditors;
    private final Map<UUID, PendingEdit> pendingEdits;
    
    public ShopListener(TownyCapture plugin) {
        this.plugin = plugin;
        this.activeShops = new HashMap<>();
        this.activeEditors = new HashMap<>();
        this.pendingEdits = new ConcurrentHashMap<>();
    }
    
    /**
     * Open shop for player
     */
    public void openShop(Player player, String zoneId) {
        ShopManager manager = plugin.getShopManager();
        ShopData shop = manager.getShop(zoneId);
        
        if (!shop.isEnabled()) {
            player.sendMessage(Messages.get("errors.shop.disabled"));
            return;
        }

        if (manager.isShopBlockedByCapture(zoneId)) {
            CapturePoint zone = plugin.getCapturePoint(zoneId);
            String zoneName = zone != null ? zone.getName() : zoneId;
            player.sendMessage(Messages.get("errors.shop.capture-active", Map.of("zone", zoneName)));
            return;
        }
        
        if (!manager.canAccessShop(player, zoneId)) {
            player.sendMessage(Messages.get("errors.shop.no-access"));
            return;
        }
        
        ShopGUI gui = new ShopGUI(plugin, zoneId, shop, player);
        activeShops.put(player, gui);
        gui.open();
    }
    
    /**
     * Open editor for admin
     */
    public void openEditor(Player admin, String zoneId) {
        if (!admin.hasPermission("capturepoints.admin.shop")) {
            admin.sendMessage(Messages.get("errors.no-permission"));
            return;
        }
        
        ShopManager manager = plugin.getShopManager();
        ShopData shop = manager.getShop(zoneId);
        
        // Remove from shop GUI if they were browsing
        activeShops.remove(admin);
        
        ShopEditorGUI editor = new ShopEditorGUI(plugin, zoneId, shop, admin);
        activeEditors.put(admin, editor);
        editor.openMainMenu();
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        // Check if player has an active editor GUI first (admins take priority)
        if (activeEditors.containsKey(player)) {
            ShopEditorGUI editor = activeEditors.get(player);
            if (!isEditorView(editor, event.getView())) {
                return; // Not an editor GUI, ignore
            }
            
            // In ITEM_PLACEMENT mode, allow limited interactions
            if (editor.getMode() == ShopEditorGUI.EditorMode.ITEM_PLACEMENT) {
                int rawSlot = event.getRawSlot();
                int topSize = event.getView().getTopInventory().getSize();
                int infoSlot = editor.getInfoSlot();
                int backSlot = editor.getBackSlot();
                
                // Always cancel to prevent items being taken out
                event.setCancelled(true);
                
                // Info button
                if (infoSlot >= 0 && rawSlot == infoSlot) {
                    return;
                }
                
                // Back button - go back
                if (backSlot >= 0 && rawSlot == backSlot) {
                    editor.openMainMenu();
                    return;
                }
                
                // Clicking in player's inventory with shift - try to place in shop
                if (rawSlot >= topSize && event.isShiftClick()) {
                    ItemStack clicked = event.getCurrentItem();
                    if (clicked != null && clicked.getType() != Material.AIR) {
                        int slot = findFirstEmptySlot(event.getView().getTopInventory(), infoSlot, backSlot);
                        if (slot >= 0) {
                            addItemToShop(editor, clicked, slot);
                            if (event.getClickedInventory() != null) {
                                event.getClickedInventory().setItem(event.getSlot(), null);
                            } else {
                                clicked.setAmount(0);
                            }
                            editor.openItemPlacement();
                        }
                    }
                    return;
                }
                
                // Shift-click in top inventory - remove item
                if (rawSlot < topSize && event.isShiftClick()) {
                    ItemStack clicked = event.getCurrentItem();
                    if (clicked != null && clicked.getType() != Material.AIR) {
                        editor.getShop().removeItem(rawSlot);
                        plugin.getShopManager().saveShop(editor.getZoneId());
                        editor.openItemPlacement();
                    }
                    return;
                }
                
                // Regular click in top inventory
                if (rawSlot < topSize && !event.isShiftClick()) {
                    ItemStack clicked = event.getCurrentItem();
                    ItemStack cursor = event.getCursor();
                    boolean clickedEmpty = clicked == null || clicked.getType() == Material.AIR;
                    if (clickedEmpty && cursor != null && cursor.getType() != Material.AIR) {
                        if ((infoSlot < 0 || rawSlot != infoSlot) && (backSlot < 0 || rawSlot != backSlot)) {
                            addItemToShop(editor, cursor, rawSlot);
                            event.setCursor(null);
                            editor.openItemPlacement();
                        }
                        return;
                    }
                    if (clicked != null && clicked.getType() != Material.AIR) {
                        ShopItemConfig item = editor.getShop().getItem(rawSlot);
                        if (item != null) {
                            editor.openItemConfig(item);
                        }
                    }
                }
                
                return;
            }
            
            // For all other modes, cancel and handle normally
            event.setCancelled(true);
            handleEditorClick(player, event);
            return;
        }
        
        // Check if player has an active shop GUI
        if (activeShops.containsKey(player)) {
            ShopGUI gui = activeShops.get(player);

            // Verify this is actually a shop inventory
            if (!isShopView(gui, event.getView())) {
                return; // Not a shop GUI, ignore
            }
            
            event.setCancelled(true);
            handleShopClick(player, event);
            return;
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        // Check if player has an active editor GUI first (admins take priority)
        ShopEditorGUI editor = activeEditors.get(player);
        if (editor != null && isEditorView(editor, event.getView())) {
            event.setCancelled(true);
            return;
        }

        ShopGUI gui = activeShops.get(player);
        if (gui != null && isShopView(gui, event.getView())) {
            event.setCancelled(true);
        }
    }
    
    /**
     * Handle player shop GUI clicks
     */
    private void handleShopClick(Player player, InventoryClickEvent event) {
        ShopGUI gui = activeShops.get(player);
        if (gui == null) return;
        
        // Only process clicks in the top inventory (the shop GUI)
        int rawSlot = event.getRawSlot();
        int topSize = event.getView().getTopInventory().getSize();
        if (rawSlot < 0 || rawSlot >= topSize) {
            // Click is in player's inventory - event already cancelled, just return
            return;
        }
        
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;
        
        int slot = event.getSlot();
        
        // Handle based on current GUI state
        switch (gui.getCurrentState()) {
            case BROWSING:
                handleBrowsing(player, gui, clicked, slot, event);
                break;
            case QUANTITY_SELECT:
                handleQuantitySelect(player, gui, clicked, slot);
                break;
        }
    }
    
    private void handleBrowsing(Player player, ShopGUI gui, ItemStack clicked, int slot, InventoryClickEvent event) {
        // Navigation arrows (slots 48, 50 in bottom row)
        if (gui.isPaginated() && (slot == 48 || slot == 50) && clicked.getType() == Material.ARROW) {
            if (!clicked.hasItemMeta()) return;
            String name = clicked.getItemMeta().getDisplayName();
            if (name.contains(Messages.get("gui.shop.previous-page"))) {
                gui.openPage(gui.getCurrentPage() - 1);
            } else if (name.contains(Messages.get("gui.shop.next-page"))) {
                gui.openPage(gui.getCurrentPage() + 1);
            }
            return;
        }
        
        // Only process shop items in first 45 slots when paginated
        if (gui.isPaginated() && slot >= 45) return;
        
        // Shop item - look up using the slot map
        ShopItemConfig item = gui.getItemAtSlot(slot);
        if (item != null) {
            // Left click = buy, Right click = sell, Shift doesn't matter
            ClickType clickType = event.getClick();
            boolean buyMode = clickType == ClickType.LEFT || clickType == ClickType.SHIFT_LEFT;
            
            if (buyMode && !item.isBuyable()) {
                player.sendMessage(Messages.get("errors.shop.not-buyable"));
                return;
            }
            if (!buyMode && !item.isSellable()) {
                player.sendMessage(Messages.get("errors.shop.not-sellable"));
                return;
            }
            
            gui.openQuantitySelector(item, buyMode);
        }
    }
    
    private void handleQuantitySelect(Player player, ShopGUI gui, ItemStack clicked, int slot) {
        // Null check for clicked item
        if (clicked == null || clicked.getType() == Material.AIR) {
            return;
        }
        
        // Slot 22 is the cancel button
        if (slot == 22 && clicked.getType() == Material.BARRIER) {
            gui.open();
            return;
        }
        
        // Only slots 10-16 are valid quantity options
        if (slot < 10 || slot > 16) {
            return; // Ignore all other slots
        }
        
        // Must be a glass pane
        Material type = clicked.getType();
        if (type != Material.GREEN_STAINED_GLASS_PANE && 
            type != Material.RED_STAINED_GLASS_PANE && 
            type != Material.GRAY_STAINED_GLASS_PANE) {
            return;
        }
        
        // Gray panes are disabled (out of stock)
        if (type == Material.GRAY_STAINED_GLASS_PANE) {
            player.sendMessage(Messages.get("errors.shop.out-of-stock"));
            return;
        }

        Integer quantity = gui.getQuantityForSlot(slot);
        if (quantity == null) {
            return;
        }

        ShopManager manager = plugin.getShopManager();
        boolean success;
        
        if (gui.isBuyMode()) {
            success = manager.processBuy(player, gui.getZoneId(), 
                gui.getSelectedItem().getSlot(), quantity);
        } else {
            success = manager.processSell(player, gui.getZoneId(), 
                gui.getSelectedItem().getSlot(), quantity);
        }
        
        if (success) {
            player.closeInventory();
        } else {
            gui.open();
        }
    }
    
    /**
     * Handle admin editor GUI clicks
     */
    private void handleEditorClick(Player admin, InventoryClickEvent event) {
        ShopEditorGUI editor = activeEditors.get(admin);
        if (editor == null) return;
        
        // Only process clicks in the top inventory (the editor GUI)
        // Exception: ITEM_PLACEMENT mode is handled separately with rawSlot checks
        int rawSlot = event.getRawSlot();
        int topSize = event.getView().getTopInventory().getSize();
        if (rawSlot < 0 || rawSlot >= topSize) {
            return; // Click is in player's inventory, ignore it
        }
        
        ItemStack clicked = event.getCurrentItem();
        int slot = event.getSlot();
        
        ShopEditorGUI.EditorMode mode = editor.getMode();
        
        switch (mode) {
            case MAIN_MENU:
                handleMainMenuClick(admin, editor, clicked, slot);
                break;
            case SETTINGS:
                handleSettingsClick(admin, editor, clicked, slot);
                break;
            case ITEM_PLACEMENT:
                handleItemPlacementClick(admin, editor, event);
                break;
            case ITEM_CONFIG:
                handleItemConfigClick(admin, editor, clicked, slot);
                break;
            case STATISTICS:
                handleStatisticsClick(admin, editor, clicked, slot);
                break;
        }
    }
    
    private void handleMainMenuClick(Player admin, ShopEditorGUI editor, ItemStack clicked, int slot) {
        if (clicked == null) return;
        
        switch (slot) {
            case 10: // Enable/Disable toggle
                editor.getShop().setEnabled(!editor.getShop().isEnabled());
                editor.openMainMenu();
                break;
            case 11: // Settings
                editor.openSettings();
                break;
            case 12: // Manage Items
                editor.openItemPlacement();
                break;
            case 13: // Statistics
                editor.openStatistics();
                break;
            case 14: // Manual Restock
                plugin.getShopManager().restockShop(editor.getZoneId());
                admin.sendMessage(Messages.get("messages.shop.restocked"));
                editor.openMainMenu();
                break;
            case 22: // Save & Close
                plugin.getShopManager().saveShop(editor.getZoneId());
                admin.sendMessage(Messages.get("messages.shop.saved"));
                admin.closeInventory();
                break;
        }
    }
    
    private void handleSettingsClick(Player admin, ShopEditorGUI editor, ItemStack clicked, int slot) {
        if (clicked == null) return;
        
        ShopData shop = editor.getShop();
        
        switch (slot) {
            case 10: // Access Mode
                cycleAccessMode(shop);
                break;
            case 11: // Stock System
                cycleStockSystem(shop);
                break;
            case 12: // Layout Mode
                cycleLayoutMode(shop);
                break;
            case 13: // Pricing Mode
                cyclePricingMode(shop);
                break;
            case 14: // Restock Schedule
                cycleRestockSchedule(shop);
                break;
            case 22: // Back
                editor.openMainMenu();
                return;
        }
        
        editor.openSettings(); // Refresh
    }
    
    private void handleItemPlacementClick(Player admin, ShopEditorGUI editor, InventoryClickEvent event) {
        int slot = event.getSlot();
        
        // Back button
        if (slot == 53) {
            editor.openMainMenu();
            return;
        }
        
        // Info button
        if (slot == 49) {
            return;
        }
        
        ItemStack clicked = event.getCurrentItem();
        
        // Shift-click to remove item
        if (event.isShiftClick() && clicked != null && clicked.getType() != Material.AIR) {
            editor.getShop().removeItem(slot);
            editor.openItemPlacement();
            return;
        }
        
        // Click to configure existing item
        if (clicked != null && clicked.getType() != Material.AIR) {
            ShopItemConfig item = editor.getShop().getItem(slot);
            if (item != null) {
                editor.openItemConfig(item);
            }
        }
        
        // Drop item to add new (handled by allowing the event in specific cases)
    }
    
    private void handleItemConfigClick(Player admin, ShopEditorGUI editor, ItemStack clicked, int slot) {
        ShopItemConfig item = editor.getEditingItem();
        if (item == null) return;
        
        // Slot 4 is the display item - do nothing (already cancelled)
        if (slot == 4) return;
        
        switch (slot) {
            case 10: // Buyable toggle
                item.setBuyable(!item.isBuyable());
                editor.openItemConfig(item);
                break;
            case 11: // Sellable toggle
                item.setSellable(!item.isSellable());
                editor.openItemConfig(item);
                break;
            case 12: // Stock settings
                pendingEdits.put(admin.getUniqueId(), new PendingEdit(PendingEditType.STOCK, editor, item));
                admin.sendMessage(Messages.get("messages.shop.set-stock-prompt"));
                admin.closeInventory();
                break;
            case 13: // Category
                pendingEdits.put(admin.getUniqueId(), new PendingEdit(PendingEditType.CATEGORY, editor, item));
                admin.sendMessage(Messages.get("messages.shop.set-category-prompt"));
                admin.closeInventory();
                break;
            case 14: // Slot
                pendingEdits.put(admin.getUniqueId(), new PendingEdit(PendingEditType.SLOT, editor, item));
                admin.sendMessage(Messages.get("messages.shop.set-slot-prompt"));
                admin.closeInventory();
                break;
            case 15: // Buy price
                pendingEdits.put(admin.getUniqueId(), new PendingEdit(PendingEditType.BUY_PRICE, editor, item));
                admin.sendMessage(Messages.get("messages.shop.set-buy-price-prompt"));
                admin.closeInventory();
                break;
            case 16: // Sell price
                pendingEdits.put(admin.getUniqueId(), new PendingEdit(PendingEditType.SELL_PRICE, editor, item));
                admin.sendMessage(Messages.get("messages.shop.set-sell-price-prompt"));
                admin.closeInventory();
                break;
            case 22: // Save
                editor.getShop().addItem(item);
                editor.openItemPlacement();
                break;
        }
    }
    
    private void handleStatisticsClick(Player admin, ShopEditorGUI editor, ItemStack clicked, int slot) {
        if (slot == 22) { // Back button
            editor.openMainMenu();
        }
    }
    
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Player player = (Player) event.getPlayer();
        
        // Remove from active shop GUIs after verifying the player isn't switching between shop menus
        ShopGUI closingGui = activeShops.get(player);
        if (closingGui != null) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                ShopGUI currentGui = activeShops.get(player);
                if (currentGui != closingGui) {
                    return;
                }
                if (!player.isOnline()) {
                    activeShops.remove(player);
                    return;
                }
                if (!isShopView(currentGui, player.getOpenInventory())) {
                    activeShops.remove(player);
                }
            }, 1L);
        }
        
        // Save editor changes and sync items from inventory
        if (activeEditors.containsKey(player)) {
            ShopEditorGUI editor = activeEditors.get(player);
            
            // If closing a 54-slot ITEM_PLACEMENT inventory, sync the contents
            if (editor.getMode() == ShopEditorGUI.EditorMode.ITEM_PLACEMENT && 
                event.getInventory().getSize() == 54 &&
                event.getInventory().equals(editor.getCurrentInventory())) {
                syncEditorInventory(editor, event.getInventory());
                plugin.getShopManager().saveShop(editor.getZoneId());
            }
            
            // Delay check if player is still in editor
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                ShopEditorGUI currentEditor = activeEditors.get(player);
                if (currentEditor == null) {
                    return;
                }
                if (pendingEdits.containsKey(player.getUniqueId())) {
                    return;
                }
                // If player doesn't have an editor inventory open anymore, remove them
                if (!isEditorView(currentEditor, player.getOpenInventory())) {
                    ShopEditorGUI removed = activeEditors.remove(player);
                    if (removed != null) {
                        plugin.getShopManager().saveShop(removed.getZoneId());
                    }
                }
            }, 1L);
        }
    }
    
    /**
     * Sync items from editor inventory to shop config
     */
    private void syncEditorInventory(ShopEditorGUI editor, Inventory inv) {
        ShopData shop = editor.getShop();
        
        // Clear items not in inventory
        Set<Integer> slotsToRemove = new HashSet<>(shop.getItems().keySet());
        
        int infoSlot = editor.getInfoSlot();
        int backSlot = editor.getBackSlot();

        for (int slot = 0; slot < 54; slot++) {
            // Skip control slots
            if (slot == infoSlot || slot == backSlot) continue;
            
            ItemStack item = inv.getItem(slot);
            
            if (item != null && item.getType() != Material.AIR) {
                slotsToRemove.remove(slot);
                
                // Check if this slot already has a config
                if (!shop.getItems().containsKey(slot)) {
                    // Create new config for this item
                    ShopItemConfig config = new ShopItemConfig(item.getType(), slot);
                    config.setBuyable(true);
                    config.setSellable(true);
                    config.setBuyPrice(100.0);
                    config.setSellPrice(50.0);
                    config.setStock(64);
                    config.setMaxStock(64);
                    config.setDisplayItem(item.clone());
                    shop.getItems().put(slot, config);
                }
            }
        }
        
        // Remove items that are no longer in the inventory
        for (Integer slot : slotsToRemove) {
            shop.removeItem(slot);
        }
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        
        // Cleanup any active GUI references to prevent memory leaks
        activeShops.remove(player);
        pendingEdits.remove(player.getUniqueId());
        
        // Save editor changes before cleanup
        if (activeEditors.containsKey(player)) {
            ShopEditorGUI editor = activeEditors.remove(player);
            plugin.getShopManager().saveShop(editor.getZoneId());
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        PendingEdit pending = pendingEdits.remove(event.getPlayer().getUniqueId());
        if (pending == null) return;

        event.setCancelled(true);
        String message = event.getMessage().trim();

        plugin.getServer().getScheduler().runTask(plugin, () -> applyPendingEdit(event.getPlayer(), pending, message));
    }
    
    // Helper methods for cycling enum values
    private void cycleAccessMode(ShopData shop) {
        ShopData.AccessMode[] modes = ShopData.AccessMode.values();
        int current = shop.getAccessMode().ordinal();
        shop.setAccessMode(modes[(current + 1) % modes.length]);
    }
    
    private void cycleStockSystem(ShopData shop) {
        ShopData.StockSystem[] systems = ShopData.StockSystem.values();
        int current = shop.getStockSystem().ordinal();
        shop.setStockSystem(systems[(current + 1) % systems.length]);
    }
    
    private void cycleLayoutMode(ShopData shop) {
        ShopData.LayoutMode[] modes = ShopData.LayoutMode.values();
        int current = shop.getLayoutMode().ordinal();
        shop.setLayoutMode(modes[(current + 1) % modes.length]);
    }
    
    private void cyclePricingMode(ShopData shop) {
        ShopData.PricingMode[] modes = ShopData.PricingMode.values();
        int current = shop.getPricingMode().ordinal();
        shop.setPricingMode(modes[(current + 1) % modes.length]);
    }
    
    private void cycleRestockSchedule(ShopData shop) {
        ShopData.RestockSchedule[] schedules = ShopData.RestockSchedule.values();
        int current = shop.getRestockSchedule().ordinal();
        shop.setRestockSchedule(schedules[(current + 1) % schedules.length]);
    }

    private boolean isShopView(ShopGUI gui, InventoryView view) {
        if (gui == null || view == null) return false;
        Inventory current = gui.getCurrentInventory();
        if (current != null) {
            return current.equals(view.getTopInventory());
        }
        String title = view.getTitle();
        return titleMatches(title, Messages.get("gui.shop.title", Map.of("zone", ""))) ||
            titleMatches(title, Messages.get("gui.shop.quantity-title", Map.of("item", ""))) ||
            titleMatches(title, Messages.get("gui.shop.page-title", Map.of("page", ""))) ||
            titleMatches(title, Messages.get("gui.shop.category-page-title", Map.of(
                "category", "",
                "page", ""
            ))) ||
            titleMatches(title, Messages.get("gui.shop.categories-title"));
    }

    private int findFirstEmptySlot(Inventory inv, int infoSlot, int backSlot) {
        int size = inv.getSize();
        for (int slot = 0; slot < size; slot++) {
            if (slot == infoSlot || slot == backSlot) {
                continue;
            }
            ItemStack existing = inv.getItem(slot);
            if (existing == null || existing.getType() == Material.AIR) {
                return slot;
            }
        }
        return -1;
    }

    private void addItemToShop(ShopEditorGUI editor, ItemStack source, int slot) {
        ShopData shop = editor.getShop();
        if (shop.getItem(slot) != null) {
            return;
        }

        ShopItemConfig config = new ShopItemConfig(source.getType(), slot);
        config.setBuyable(true);
        config.setSellable(true);
        config.setBuyPrice(100.0);
        config.setSellPrice(50.0);
        config.setStock(64);
        config.setMaxStock(64);
        config.setDisplayItem(source.clone());
        shop.addItem(config);
        plugin.getShopManager().saveShop(editor.getZoneId());
    }

    private boolean isEditorView(ShopEditorGUI editor, InventoryView view) {
        if (editor == null || view == null) return false;
        Inventory current = editor.getCurrentInventory();
        if (current != null) {
            return current.equals(view.getTopInventory());
        }
        String title = view.getTitle();
        return titleMatches(title, Messages.get("gui.editor.title", Map.of("zone", ""))) ||
            titleMatches(title, Messages.get("gui.editor.settings-title")) ||
            titleMatches(title, Messages.get("gui.editor.items-title")) ||
            titleMatches(title, Messages.get("gui.editor.config-title", Map.of("item", ""))) ||
            titleMatches(title, Messages.get("gui.editor.stats-title"));
    }

    private boolean titleMatches(String title, String template) {
        if (title == null || template == null) {
            return false;
        }
        String plainTitle = ChatColor.stripColor(title);
        String plainTemplate = ChatColor.stripColor(template);
        return plainTitle.contains(plainTemplate);
    }

    private void applyPendingEdit(Player player, PendingEdit pending, String message) {
        if (pending.editor == null || pending.item == null) {
            return;
        }

        if (!activeEditors.containsKey(player)) {
            activeEditors.put(player, pending.editor);
        }

        switch (pending.type) {
            case STOCK:
                handleStockInput(player, pending.editor, pending.item, message);
                break;
            case CATEGORY:
                handleCategoryInput(player, pending.editor, pending.item, message);
                break;
            case SLOT:
                handleSlotInput(player, pending.editor, pending.item, message);
                break;
            case BUY_PRICE:
                handleBuyPriceInput(player, pending.editor, pending.item, message);
                break;
            case SELL_PRICE:
                handleSellPriceInput(player, pending.editor, pending.item, message);
                break;
            default:
                break;
        }
    }

    private void handleStockInput(Player player, ShopEditorGUI editor, ShopItemConfig item, String message) {
        int maxStock;
        try {
            maxStock = Integer.parseInt(message);
        } catch (NumberFormatException e) {
            player.sendMessage(Messages.get("errors.shop.invalid-stock"));
            editor.openItemConfig(item);
            return;
        }

        if (maxStock < -1) {
            player.sendMessage(Messages.get("errors.shop.invalid-stock"));
            editor.openItemConfig(item);
            return;
        }

        item.setMaxStock(maxStock);
        if (maxStock >= 0 && item.getStock() > maxStock) {
            item.setStock(maxStock);
        }

        editor.openItemConfig(item);
    }

    private void handleCategoryInput(Player player, ShopEditorGUI editor, ShopItemConfig item, String message) {
        String category = message.trim();
        if (category.isEmpty()) {
            player.sendMessage(Messages.get("errors.shop.invalid-category"));
            editor.openItemConfig(item);
            return;
        }

        ShopData shop = editor.getShop();
        shop.removeItem(item.getSlot());
        item.setCategory(category);
        shop.addItem(item);

        editor.openItemConfig(item);
    }

    private void handleSlotInput(Player player, ShopEditorGUI editor, ShopItemConfig item, String message) {
        int slot;
        try {
            slot = Integer.parseInt(message.trim());
        } catch (NumberFormatException e) {
            player.sendMessage(Messages.get("errors.shop.invalid-slot"));
            editor.openItemConfig(item);
            return;
        }

        if (slot < 0 || slot > 53) {
            player.sendMessage(Messages.get("errors.shop.invalid-slot"));
            editor.openItemConfig(item);
            return;
        }

        ShopData shop = editor.getShop();
        ShopItemConfig existing = shop.getItem(slot);
        if (existing != null && existing != item) {
            player.sendMessage(Messages.get("errors.shop.slot-occupied"));
            editor.openItemConfig(item);
            return;
        }

        shop.removeItem(item.getSlot());
        item.setSlot(slot);
        shop.addItem(item);

        editor.openItemConfig(item);
    }

    private void handleBuyPriceInput(Player player, ShopEditorGUI editor, ShopItemConfig item, String message) {
        double price;
        try {
            price = Double.parseDouble(message.trim());
        } catch (NumberFormatException e) {
            player.sendMessage(Messages.get("errors.shop.invalid-price"));
            editor.openItemConfig(item);
            return;
        }

        if (price < 0) {
            player.sendMessage(Messages.get("errors.shop.invalid-price"));
            editor.openItemConfig(item);
            return;
        }

        item.setBuyPrice(price);
        editor.openItemConfig(item);
    }

    private void handleSellPriceInput(Player player, ShopEditorGUI editor, ShopItemConfig item, String message) {
        double price;
        try {
            price = Double.parseDouble(message.trim());
        } catch (NumberFormatException e) {
            player.sendMessage(Messages.get("errors.shop.invalid-price"));
            editor.openItemConfig(item);
            return;
        }

        if (price < 0) {
            player.sendMessage(Messages.get("errors.shop.invalid-price"));
            editor.openItemConfig(item);
            return;
        }

        item.setSellPrice(price);
        editor.openItemConfig(item);
    }

    private enum PendingEditType {
        STOCK,
        CATEGORY,
        SLOT,
        BUY_PRICE,
        SELL_PRICE
    }

    private static class PendingEdit {
        private final PendingEditType type;
        private final ShopEditorGUI editor;
        private final ShopItemConfig item;

        private PendingEdit(PendingEditType type, ShopEditorGUI editor, ShopItemConfig item) {
            this.type = type;
            this.editor = editor;
            this.item = item;
        }
    }
}
