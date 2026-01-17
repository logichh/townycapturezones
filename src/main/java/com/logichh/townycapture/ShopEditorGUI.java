package com.logichh.townycapture;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * Admin shop configuration interface
 */
public class ShopEditorGUI {
    
    private final TownyCapture plugin;
    private final ShopData shop;
    private final String zoneId;
    private final Player admin;
    private Inventory currentInventory;
    private int infoSlot = 49;
    private int backSlot = 53;
    
    // Editor state
    private EditorMode mode = EditorMode.MAIN_MENU;
    private ShopItemConfig editingItem = null;
    
    public enum EditorMode {
        MAIN_MENU,
        SETTINGS,
        ITEM_PLACEMENT,
        ITEM_CONFIG,
        STATISTICS
    }
    
    public ShopEditorGUI(TownyCapture plugin, String zoneId, ShopData shop, Player admin) {
        this.plugin = plugin;
        this.shop = shop;
        this.zoneId = zoneId;
        this.admin = admin;
    }
    
    /**
     * Open main editor menu
     */
    public void openMainMenu() {
        this.mode = EditorMode.MAIN_MENU;
        
        Inventory inv = Bukkit.createInventory(null, 27, Messages.get("gui.editor.title", Map.of("zone", zoneId)));
        
        // Enable/Disable toggle
        ItemStack toggle = new ItemStack(shop.isEnabled() ? Material.LIME_DYE : Material.GRAY_DYE);
        ItemMeta toggleMeta = toggle.getItemMeta();
        if (toggleMeta != null) {
            toggleMeta.setDisplayName(shop.isEnabled() ? ChatColor.GREEN + "Shop Enabled" : ChatColor.GRAY + "Shop Disabled");
            toggleMeta.setLore(Arrays.asList(ChatColor.GRAY + "Click to toggle"));
            toggle.setItemMeta(toggleMeta);
        }
        inv.setItem(10, toggle);
        
        // Settings
        ItemStack settings = new ItemStack(Material.COMPARATOR);
        ItemMeta settingsMeta = settings.getItemMeta();
        if (settingsMeta != null) {
            settingsMeta.setDisplayName(ChatColor.YELLOW + "Shop Settings");
            settingsMeta.setLore(Arrays.asList(
                ChatColor.GRAY + "Access: " + ChatColor.WHITE + shop.getAccessMode().name(),
                ChatColor.GRAY + "Stock: " + ChatColor.WHITE + shop.getStockSystem().name(),
                ChatColor.GRAY + "Layout: " + ChatColor.WHITE + shop.getLayoutMode().name(),
                ChatColor.GRAY + "Pricing: " + ChatColor.WHITE + shop.getPricingMode().name(),
                ChatColor.GRAY + "Restock: " + ChatColor.WHITE + shop.getRestockSchedule().name(),
                "",
                ChatColor.GRAY + "Click to configure"
            ));
            settings.setItemMeta(settingsMeta);
        }
        inv.setItem(11, settings);
        
        // Add/Edit Items
        ItemStack items = new ItemStack(Material.CHEST);
        ItemMeta itemsMeta = items.getItemMeta();
        if (itemsMeta != null) {
            itemsMeta.setDisplayName(ChatColor.YELLOW + "Manage Items");
            itemsMeta.setLore(Arrays.asList(
                ChatColor.GRAY + "Current items: " + ChatColor.WHITE + shop.getItems().size(),
                "",
                ChatColor.GRAY + "Click to edit items"
            ));
            items.setItemMeta(itemsMeta);
        }
        inv.setItem(12, items);
        
        // Statistics
        ItemStack stats = new ItemStack(Material.BOOK);
        ItemMeta statsMeta = stats.getItemMeta();
        if (statsMeta != null) {
            statsMeta.setDisplayName(ChatColor.YELLOW + "Statistics");
            statsMeta.setLore(Arrays.asList(
                ChatColor.GRAY + "Total Buys: " + ChatColor.WHITE + shop.getTotalBuys(),
                ChatColor.GRAY + "Total Sells: " + ChatColor.WHITE + shop.getTotalSells(),
                ChatColor.GRAY + "Revenue: " + ChatColor.GOLD + String.format("%.2f", shop.getTotalRevenue()),
                "",
                ChatColor.GRAY + "Click to view details"
            ));
            stats.setItemMeta(statsMeta);
        }
        inv.setItem(13, stats);
        
        // Manual Restock
        ItemStack restock = new ItemStack(Material.HOPPER);
        ItemMeta restockMeta = restock.getItemMeta();
        if (restockMeta != null) {
            restockMeta.setDisplayName(ChatColor.YELLOW + "Manual Restock");
            restockMeta.setLore(Arrays.asList(ChatColor.GRAY + "Click to restock all items"));
            restock.setItemMeta(restockMeta);
        }
        inv.setItem(14, restock);
        
        // Save & Close
        ItemStack save = new ItemStack(Material.EMERALD);
        ItemMeta saveMeta = save.getItemMeta();
        if (saveMeta != null) {
            saveMeta.setDisplayName(ChatColor.GREEN + "Save & Close");
            save.setItemMeta(saveMeta);
        }
        inv.setItem(22, save);

        currentInventory = inv;
        admin.openInventory(inv);
    }
    
    /**
     * Open settings configuration
     */
    public void openSettings() {
        this.mode = EditorMode.SETTINGS;
        
        Inventory inv = Bukkit.createInventory(null, 27, Messages.get("gui.editor.settings-title"));
        
        // Access Mode
        ItemStack access = new ItemStack(Material.OAK_DOOR);
        ItemMeta accessMeta = access.getItemMeta();
        if (accessMeta != null) {
            accessMeta.setDisplayName(ChatColor.YELLOW + "Access Mode");
            accessMeta.setLore(Arrays.asList(
                ChatColor.GRAY + "Current: " + ChatColor.WHITE + shop.getAccessMode().name(),
                "",
                ChatColor.WHITE + "ALWAYS: " + ChatColor.GRAY + "Anyone can use",
                ChatColor.WHITE + "CONTROLLED_ONLY: " + ChatColor.GRAY + "Controlling town only",
                ChatColor.WHITE + "OWNER_ONLY: " + ChatColor.GRAY + "Owner town only",
                "",
                ChatColor.GRAY + "Click to cycle"
            ));
            access.setItemMeta(accessMeta);
        }
        inv.setItem(10, access);
        
        // Stock System
        ItemStack stock = new ItemStack(Material.CHEST);
        ItemMeta stockMeta = stock.getItemMeta();
        if (stockMeta != null) {
            stockMeta.setDisplayName(ChatColor.YELLOW + "Stock System");
            stockMeta.setLore(Arrays.asList(
                ChatColor.GRAY + "Current: " + ChatColor.WHITE + shop.getStockSystem().name(),
                "",
                ChatColor.WHITE + "INFINITE: " + ChatColor.GRAY + "Unlimited stock",
                ChatColor.WHITE + "LIMITED: " + ChatColor.GRAY + "Stock restocks",
                ChatColor.WHITE + "FINITE: " + ChatColor.GRAY + "Stock never restocks",
                "",
                ChatColor.GRAY + "Click to cycle"
            ));
            stock.setItemMeta(stockMeta);
        }
        inv.setItem(11, stock);
        
        // Layout Mode
        ItemStack layout = new ItemStack(Material.PAINTING);
        ItemMeta layoutMeta = layout.getItemMeta();
        if (layoutMeta != null) {
            layoutMeta.setDisplayName(ChatColor.YELLOW + "Layout Mode");
            layoutMeta.setLore(Arrays.asList(
                ChatColor.GRAY + "Current: " + ChatColor.WHITE + shop.getLayoutMode().name(),
                "",
                ChatColor.WHITE + "SINGLE_PAGE: " + ChatColor.GRAY + "One inventory",
                ChatColor.WHITE + "CATEGORIES: " + ChatColor.GRAY + "Organized by category",
                ChatColor.WHITE + "PAGINATED: " + ChatColor.GRAY + "Multiple pages",
                "",
                ChatColor.GRAY + "Click to cycle"
            ));
            layout.setItemMeta(layoutMeta);
        }
        inv.setItem(12, layout);
        
        // Pricing Mode
        ItemStack pricing = new ItemStack(Material.GOLD_INGOT);
        ItemMeta pricingMeta = pricing.getItemMeta();
        if (pricingMeta != null) {
            pricingMeta.setDisplayName(ChatColor.YELLOW + "Pricing Mode");
            pricingMeta.setLore(Arrays.asList(
                ChatColor.GRAY + "Current: " + ChatColor.WHITE + shop.getPricingMode().name(),
                "",
                ChatColor.WHITE + "FIXED: " + ChatColor.GRAY + "Prices never change",
                ChatColor.WHITE + "DYNAMIC: " + ChatColor.GRAY + "Prices adjust with supply/demand",
                "",
                ChatColor.GRAY + "Click to cycle"
            ));
            pricing.setItemMeta(pricingMeta);
        }
        inv.setItem(13, pricing);
        
        // Restock Schedule
        ItemStack restock = new ItemStack(Material.CLOCK);
        ItemMeta restockMeta = restock.getItemMeta();
        if (restockMeta != null) {
            restockMeta.setDisplayName(ChatColor.YELLOW + "Restock Schedule");
            restockMeta.setLore(Arrays.asList(
                ChatColor.GRAY + "Current: " + ChatColor.WHITE + shop.getRestockSchedule().name(),
                "",
                ChatColor.WHITE + "HOURLY: " + ChatColor.GRAY + "Every hour",
                ChatColor.WHITE + "DAILY: " + ChatColor.GRAY + "Every 24 hours",
                ChatColor.WHITE + "WEEKLY: " + ChatColor.GRAY + "Every 7 days",
                ChatColor.WHITE + "MANUAL: " + ChatColor.GRAY + "Admin only",
                "",
                ChatColor.GRAY + "Click to cycle"
            ));
            restock.setItemMeta(restockMeta);
        }
        inv.setItem(14, restock);
        
        // Back button
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta backMeta = back.getItemMeta();
        if (backMeta != null) {
            backMeta.setDisplayName(ChatColor.GRAY + "Back");
            back.setItemMeta(backMeta);
        }
        inv.setItem(22, back);

        currentInventory = inv;
        admin.openInventory(inv);
    }
    
    /**
     * Open item placement editor
     */
    public void openItemPlacement() {
        this.mode = EditorMode.ITEM_PLACEMENT;
        
        Inventory inv = Bukkit.createInventory(null, 54, Messages.get("gui.editor.items-title"));
        
        // Add existing shop items
        for (ShopItemConfig item : shop.getItems().values()) {
            if (item.getSlot() >= 0 && item.getSlot() < 54) {
                inv.setItem(item.getSlot(), createEditorItemDisplay(item));
            }
        }
        
        // Instructions item
        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta infoMeta = info.getItemMeta();
        if (infoMeta != null) {
            infoMeta.setDisplayName(ChatColor.YELLOW + "How to Edit");
            infoMeta.setLore(Arrays.asList(
                ChatColor.GRAY + "- Click empty slot to place",
                ChatColor.GRAY + "- Shift-click to auto-place",
                ChatColor.GRAY + "- Click items to configure",
                ChatColor.GRAY + "- Shift-click to remove"
            ));
            info.setItemMeta(infoMeta);
        }
        
        // Back button
        ItemStack back = new ItemStack(Material.BARRIER);
        ItemMeta backMeta = back.getItemMeta();
        if (backMeta != null) {
            backMeta.setDisplayName(ChatColor.RED + "Back");
            back.setItemMeta(backMeta);
        }

        int[] infoCandidates = {49, 45, 46, 47, 48, 50, 51, 52, 53};
        int[] backCandidates = {53, 52, 51, 50, 48, 47, 46, 45, 49};
        infoSlot = findControlSlot(inv, infoCandidates, -1);
        backSlot = findControlSlot(inv, backCandidates, infoSlot);

        if (infoSlot >= 0) {
            inv.setItem(infoSlot, info);
        }
        if (backSlot >= 0) {
            inv.setItem(backSlot, back);
        }
        
        currentInventory = inv;
        admin.openInventory(inv);
    }
    
    /**
     * Open item configuration for a specific item
     */
    public void openItemConfig(ShopItemConfig item) {
        this.mode = EditorMode.ITEM_CONFIG;
        this.editingItem = item;
        
        String itemName = item.getDisplayName() != null ? item.getDisplayName() : item.getMaterial().name();
        Inventory inv = Bukkit.createInventory(null, 27, Messages.get("gui.editor.config-title", 
            Map.of("item", itemName)));
        
        // Item display
        ItemStack displayItem = item.getDisplayItem() != null ? item.getDisplayItem().clone() : new ItemStack(item.getMaterial());
        inv.setItem(4, displayItem);
        
        // Buyable toggle
        ItemStack buyable = new ItemStack(item.isBuyable() ? Material.LIME_DYE : Material.GRAY_DYE);
        ItemMeta buyableMeta = buyable.getItemMeta();
        if (buyableMeta != null) {
            buyableMeta.setDisplayName(item.isBuyable() ? ChatColor.GREEN + "Buyable" : ChatColor.GRAY + "Not Buyable");
            buyableMeta.setLore(Arrays.asList(
                ChatColor.GRAY + "Buy Price: " + ChatColor.YELLOW + item.getBuyPrice(),
                "",
                ChatColor.GRAY + "Click to toggle"
            ));
            buyable.setItemMeta(buyableMeta);
        }
        inv.setItem(10, buyable);
        
        // Sellable toggle
        ItemStack sellable = new ItemStack(item.isSellable() ? Material.RED_DYE : Material.GRAY_DYE);
        ItemMeta sellableMeta = sellable.getItemMeta();
        if (sellableMeta != null) {
            sellableMeta.setDisplayName(item.isSellable() ? ChatColor.RED + "Sellable" : ChatColor.GRAY + "Not Sellable");
            sellableMeta.setLore(Arrays.asList(
                ChatColor.GRAY + "Sell Price: " + ChatColor.YELLOW + item.getSellPrice(),
                "",
                ChatColor.GRAY + "Click to toggle"
            ));
            sellable.setItemMeta(sellableMeta);
        }
        inv.setItem(11, sellable);
        
        // Stock settings
        ItemStack stockItem = new ItemStack(Material.CHEST);
        ItemMeta stockMeta = stockItem.getItemMeta();
        if (stockMeta != null) {
            String currentStock = isUnlimitedStock(item) ? "Unlimited" : String.valueOf(item.getStock());
            String maxStock = isUnlimitedStock(item) ? "Unlimited" : String.valueOf(item.getMaxStock());
            stockMeta.setDisplayName(ChatColor.YELLOW + "Stock");
            stockMeta.setLore(Arrays.asList(
                ChatColor.GRAY + "Current: " + ChatColor.WHITE + currentStock,
                ChatColor.GRAY + "Max: " + ChatColor.WHITE + maxStock,
                "",
                ChatColor.GRAY + "Click to set max stock in chat",
                ChatColor.GRAY + "Use -1 for unlimited"
            ));
            stockItem.setItemMeta(stockMeta);
        }
        inv.setItem(12, stockItem);
        
        // Category
        ItemStack category = new ItemStack(Material.NAME_TAG);
        ItemMeta categoryMeta = category.getItemMeta();
        if (categoryMeta != null) {
            categoryMeta.setDisplayName(ChatColor.YELLOW + "Category");
            String cat = item.getCategory() != null ? item.getCategory() : "None";
            categoryMeta.setLore(Arrays.asList(
                ChatColor.GRAY + "Current: " + ChatColor.WHITE + cat,
                "",
                ChatColor.GRAY + "Type in chat to set"
            ));
            category.setItemMeta(categoryMeta);
        }
        inv.setItem(13, category);

        // Slot
        ItemStack slotItem = new ItemStack(Material.COMPASS);
        ItemMeta slotMeta = slotItem.getItemMeta();
        if (slotMeta != null) {
            slotMeta.setDisplayName(ChatColor.YELLOW + "Display Slot");
            slotMeta.setLore(Arrays.asList(
                ChatColor.GRAY + "Current: " + ChatColor.WHITE + item.getSlot(),
                "",
                ChatColor.GRAY + "Click to set slot in chat"
            ));
            slotItem.setItemMeta(slotMeta);
        }
        inv.setItem(14, slotItem);

        // Buy price
        ItemStack buyPrice = new ItemStack(Material.EMERALD);
        ItemMeta buyPriceMeta = buyPrice.getItemMeta();
        if (buyPriceMeta != null) {
            buyPriceMeta.setDisplayName(ChatColor.YELLOW + "Buy Price");
            buyPriceMeta.setLore(Arrays.asList(
                ChatColor.GRAY + "Current: " + ChatColor.WHITE + String.format("%.2f", item.getBuyPrice()),
                "",
                ChatColor.GRAY + "Click to set in chat"
            ));
            buyPrice.setItemMeta(buyPriceMeta);
        }
        inv.setItem(15, buyPrice);

        // Sell price
        ItemStack sellPrice = new ItemStack(Material.GOLD_INGOT);
        ItemMeta sellPriceMeta = sellPrice.getItemMeta();
        if (sellPriceMeta != null) {
            sellPriceMeta.setDisplayName(ChatColor.YELLOW + "Sell Price");
            sellPriceMeta.setLore(Arrays.asList(
                ChatColor.GRAY + "Current: " + ChatColor.WHITE + String.format("%.2f", item.getSellPrice()),
                "",
                ChatColor.GRAY + "Click to set in chat"
            ));
            sellPrice.setItemMeta(sellPriceMeta);
        }
        inv.setItem(16, sellPrice);
        
        // Save button
        ItemStack save = new ItemStack(Material.EMERALD);
        ItemMeta saveMeta = save.getItemMeta();
        if (saveMeta != null) {
            saveMeta.setDisplayName(ChatColor.GREEN + "Save");
            save.setItemMeta(saveMeta);
        }
        inv.setItem(22, save);

        currentInventory = inv;
        admin.openInventory(inv);
    }
    
    /**
     * Open statistics view
     */
    public void openStatistics() {
        this.mode = EditorMode.STATISTICS;
        
        Inventory inv = Bukkit.createInventory(null, 27, Messages.get("gui.editor.stats-title"));
        
        // Total buys
        ItemStack buys = new ItemStack(Material.GREEN_WOOL);
        ItemMeta buysMeta = buys.getItemMeta();
        if (buysMeta != null) {
            buysMeta.setDisplayName(ChatColor.GREEN + "Total Purchases");
            buysMeta.setLore(Arrays.asList(ChatColor.WHITE + String.valueOf(shop.getTotalBuys())));
            buys.setItemMeta(buysMeta);
        }
        inv.setItem(11, buys);
        
        // Total sells
        ItemStack sells = new ItemStack(Material.RED_WOOL);
        ItemMeta sellsMeta = sells.getItemMeta();
        if (sellsMeta != null) {
            sellsMeta.setDisplayName(ChatColor.RED + "Total Sales");
            sellsMeta.setLore(Arrays.asList(ChatColor.WHITE + String.valueOf(shop.getTotalSells())));
            sells.setItemMeta(sellsMeta);
        }
        inv.setItem(13, sells);
        
        // Revenue
        ItemStack revenue = new ItemStack(Material.GOLD_INGOT);
        ItemMeta revenueMeta = revenue.getItemMeta();
        if (revenueMeta != null) {
            revenueMeta.setDisplayName(ChatColor.YELLOW + "Total Revenue");
            revenueMeta.setLore(Arrays.asList(ChatColor.WHITE + String.format("%.2f", shop.getTotalRevenue())));
            revenue.setItemMeta(revenueMeta);
        }
        inv.setItem(15, revenue);
        
        // Back button
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta backMeta = back.getItemMeta();
        if (backMeta != null) {
            backMeta.setDisplayName(ChatColor.GRAY + "Back");
            back.setItemMeta(backMeta);
        }
        inv.setItem(22, back);

        currentInventory = inv;
        admin.openInventory(inv);
    }
    
    /**
     * Create item display for editor
     */
    private ItemStack createEditorItemDisplay(ShopItemConfig item) {
        // Handle null displayItem by creating a new ItemStack from material
        ItemStack display = item.getDisplayItem() != null ? 
            item.getDisplayItem().clone() : 
            new ItemStack(item.getMaterial());
        ItemMeta meta = display.getItemMeta();
        
        if (meta != null) {
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Slot: " + ChatColor.WHITE + item.getSlot());
            if (item.isBuyable()) lore.add(ChatColor.GREEN + "Buy: " + ChatColor.YELLOW + item.getBuyPrice());
            if (item.isSellable()) lore.add(ChatColor.RED + "Sell: " + ChatColor.YELLOW + item.getSellPrice());
            lore.add(ChatColor.GRAY + "Stock: " + ChatColor.WHITE + (isUnlimitedStock(item) ? "Unlimited" : String.valueOf(item.getStock())));
            if (item.getCategory() != null) lore.add(ChatColor.GRAY + "Category: " + ChatColor.WHITE + item.getCategory());
            lore.add("");
            lore.add(ChatColor.GRAY + "Click to configure");
            lore.add(ChatColor.GRAY + "Shift-click to remove");
            
            meta.setLore(lore);
            display.setItemMeta(meta);
        }
        
        return display;
    }

    private boolean isUnlimitedStock(ShopItemConfig item) {
        return shop.getStockSystem() == ShopData.StockSystem.INFINITE || item.getMaxStock() < 0;
    }
    
    // Getters
    public EditorMode getMode() { return mode; }
    public ShopItemConfig getEditingItem() { return editingItem; }
    public String getZoneId() { return zoneId; }
    public ShopData getShop() { return shop; }
    public Inventory getCurrentInventory() { return currentInventory; }
    public int getInfoSlot() { return infoSlot; }
    public int getBackSlot() { return backSlot; }

    private int findControlSlot(Inventory inv, int[] candidates, int reservedSlot) {
        for (int slot : candidates) {
            if (slot == reservedSlot) {
                continue;
            }
            ItemStack existing = inv.getItem(slot);
            if (existing == null || existing.getType() == Material.AIR) {
                return slot;
            }
        }
        return -1;
    }
}
