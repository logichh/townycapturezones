package com.logichh.townycapture;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.Material;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.object.Resident;

import java.io.File;
import java.util.*;

/**
 * Manages all zone shops, transactions, and persistence
 */
public class ShopManager {
    
    private final TownyCapture plugin;
    private final Map<String, ShopData> shops;
    private final Map<String, DynamicPricing> pricingEngines;
    private final File shopsFolder;
    private org.bukkit.scheduler.BukkitTask restockTask;
    private org.bukkit.scheduler.BukkitTask pricingTask;
    
    public ShopManager(TownyCapture plugin) {
        this.plugin = plugin;
        this.shops = new HashMap<>();
        this.pricingEngines = new HashMap<>();
        this.shopsFolder = new File(plugin.getDataFolder(), "shops");
        
        if (!shopsFolder.exists()) {
            shopsFolder.mkdirs();
        }
        
        loadAllShops();
        startPeriodicTasks();
    }
    
    /**
     * Load all shop configurations
     */
    public void loadAllShops() {
        shops.clear();
        pricingEngines.clear();
        
        File[] files = shopsFolder.listFiles((dir, name) -> name.endsWith("_shop.yml"));
        if (files != null) {
            for (File file : files) {
                String zoneId = file.getName().replace("_shop.yml", "");
                ShopData shop = ShopData.load(zoneId, file);
                shops.put(zoneId, shop);
                pricingEngines.put(zoneId, new DynamicPricing(shop));
                
                plugin.getLogger().info("Loaded shop for zone: " + zoneId);
            }
        }
    }
    
    /**
     * Get or create shop for a zone
     */
    public ShopData getShop(String zoneId) {
        return shops.computeIfAbsent(zoneId, id -> {
            ShopData shop = new ShopData(id);
            pricingEngines.put(id, new DynamicPricing(shop));
            return shop;
        });
    }
    
    /**
     * Save a shop configuration
     */
    public void saveShop(String zoneId) {
        ShopData shop = shops.get(zoneId);
        if (shop != null) {
            File file = new File(shopsFolder, zoneId + "_shop.yml");
            shop.save(file);
        }
    }
    
    /**
     * Save all shops
     */
    public void saveAllShops() {
        for (String zoneId : shops.keySet()) {
            saveShop(zoneId);
        }
    }
    
    /**
     * Delete a shop
     */
    public void deleteShop(String zoneId) {
        shops.remove(zoneId);
        pricingEngines.remove(zoneId);
        File file = new File(shopsFolder, zoneId + "_shop.yml");
        if (file.exists()) {
            file.delete();
        }
    }
    
    /**
     * Check if player can access shop
     */
    public boolean canAccessShop(Player player, String zoneId) {
        ShopData shop = shops.get(zoneId);
        if (shop == null || !shop.isEnabled()) {
            return false;
        }
        
        CapturePoint zone = plugin.getCapturePoint(zoneId);
        if (zone == null) {
            return false;
        }
        
        // Check if player is in the zone
        if (!plugin.isWithinChunkRadius(player.getLocation(), zone.getLocation(), zone.getChunkRadius())) {
            return false;
        }
        
        // Check access mode
        ShopData.AccessMode accessMode = shop.getAccessMode();

        if (accessMode == ShopData.AccessMode.CONTROLLED_ONLY && plugin.isPointActive(zoneId)) {
            return false;
        }
        
        if (accessMode == ShopData.AccessMode.ALWAYS) {
            return true;
        }
        
        // Get player's town
        Town playerTown = TownyAPI.getInstance().getTown(player);
        if (playerTown == null) {
            return false;
        }
        
        String controllingTown = zone.getControllingTown();
        if (controllingTown == null || controllingTown.isEmpty()) {
            return accessMode == ShopData.AccessMode.ALWAYS;
        }
        
        if (accessMode == ShopData.AccessMode.CONTROLLED_ONLY) {
            // Any player from a town can use if their town controls it
            return playerTown.getName().equals(controllingTown);
        }
        
        if (accessMode == ShopData.AccessMode.OWNER_ONLY) {
            // Only the controlling town can use it
            return playerTown.getName().equals(controllingTown);
        }
        
        return false;
    }

    public boolean isShopBlockedByCapture(String zoneId) {
        ShopData shop = shops.get(zoneId);
        if (shop == null) {
            return false;
        }
        return shop.getAccessMode() == ShopData.AccessMode.CONTROLLED_ONLY && plugin.isPointActive(zoneId);
    }
    
    /**
     * Process a buy transaction
     */
    public boolean processBuy(Player player, String zoneId, int slot, int quantity) {
        ShopData shop = shops.get(zoneId);
        if (shop == null) return false;
        
        ShopItemConfig item = shop.getItem(slot);
        if (item == null || !item.isBuyable()) {
            player.sendMessage(Messages.get("errors.shop.not-buyable"));
            return false;
        }
        
        // Check stock (skip for infinite stock shops)
        if (shop.getStockSystem() != ShopData.StockSystem.INFINITE && !item.hasStock(quantity)) {
            player.sendMessage(Messages.get("errors.shop.out-of-stock"));
            return false;
        }
        
        // Calculate cost
        double totalCost = item.getEffectiveBuyPrice() * quantity;
        ItemStack itemStack = createPurchaseItem(item, quantity);
        
        // Check inventory space before taking money or stock
        if (!canFit(player.getInventory(), itemStack, quantity)) {
            player.sendMessage(Messages.get("errors.shop.inventory-full"));
            return false;
        }
        
        // Check player balance
        try {
            Resident resident = TownyAPI.getInstance().getResident(player.getUniqueId());
            if (resident == null || resident.getAccount() == null) {
                player.sendMessage(Messages.get("errors.shop.no-account"));
                return false;
            }
            
            if (!resident.getAccount().canPayFromHoldings(totalCost)) {
                player.sendMessage(Messages.get("errors.shop.insufficient-funds", Map.of(
                    "cost", String.format("%.2f", totalCost)
                )));
                return false;
            }
            
            // Process payment
            resident.getAccount().withdraw(totalCost, "Shop purchase");
            
            // Give items
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(itemStack);
            if (!leftovers.isEmpty()) {
                // Refund if inventory couldn't accept the items
                resident.getAccount().deposit(totalCost, "Shop purchase refund");
                player.sendMessage(Messages.get("errors.shop.inventory-full"));
                return false;
            }
            
            // Update stock
            if (shop.getStockSystem() != ShopData.StockSystem.INFINITE) {
                item.removeStock(quantity);
            }
            
            // Update pricing
            DynamicPricing pricing = pricingEngines.get(zoneId);
            if (pricing != null) {
                pricing.onBuyTransaction(item, quantity);
            }
            
            // Record transaction
            shop.recordBuy(quantity, totalCost);
            
            // Save
            saveShop(zoneId);
            
            player.sendMessage(Messages.get("messages.shop.buy-success", Map.of(
                "quantity", String.valueOf(quantity),
                "item", item.getMaterial().name(),
                "cost", String.format("%.2f", totalCost)
            )));
            
            return true;
            
        } catch (Exception e) {
            plugin.getLogger().warning("Shop buy transaction failed: " + e.getMessage());
            player.sendMessage(Messages.get("errors.shop.transaction-failed"));
            return false;
        }
    }
    
    /**
     * Process a sell transaction
     */
    public boolean processSell(Player player, String zoneId, int slot, int quantity) {
        ShopData shop = shops.get(zoneId);
        if (shop == null) return false;
        
        ShopItemConfig item = shop.getItem(slot);
        if (item == null || !item.isSellable()) {
            player.sendMessage(Messages.get("errors.shop.not-sellable"));
            return false;
        }
        
        // Check player has items
        ItemStack checkStack = new ItemStack(item.getMaterial());
        int playerAmount = 0;
        for (ItemStack invItem : player.getInventory().getStorageContents()) {
            if (invItem != null && invItem.getType() == item.getMaterial()) {
                playerAmount += invItem.getAmount();
            }
        }
        
        if (playerAmount < quantity) {
            player.sendMessage(Messages.get("errors.shop.insufficient-items"));
            return false;
        }
        
        // Calculate earnings
        double totalEarnings = item.getEffectiveSellPrice() * quantity;
        
        try {
            Resident resident = TownyAPI.getInstance().getResident(player.getUniqueId());
            if (resident == null || resident.getAccount() == null) {
                player.sendMessage(Messages.get("errors.shop.no-account"));
                return false;
            }
            
            // Remove items from player
            int remaining = quantity;
            ItemStack[] storage = player.getInventory().getStorageContents();
            for (int i = 0; i < storage.length && remaining > 0; i++) {
                ItemStack invItem = storage[i];
                if (invItem != null && invItem.getType() == item.getMaterial()) {
                    int removeAmount = Math.min(remaining, invItem.getAmount());
                    int newAmount = invItem.getAmount() - removeAmount;
                    if (newAmount <= 0) {
                        player.getInventory().setItem(i, null);
                    } else {
                        invItem.setAmount(newAmount);
                    }
                    remaining -= removeAmount;
                }
            }
            
            // Give money
            resident.getAccount().deposit(totalEarnings, "Shop sale");
            
            // Update stock
            if (shop.getStockSystem() != ShopData.StockSystem.INFINITE) {
                item.addStock(quantity);
            }
            
            // Update pricing
            DynamicPricing pricing = pricingEngines.get(zoneId);
            if (pricing != null) {
                pricing.onSellTransaction(item, quantity);
            }
            
            // Record transaction
            shop.recordSell(quantity, totalEarnings);
            
            // Save
            saveShop(zoneId);
            
            player.sendMessage(Messages.get("messages.shop.sell-success", Map.of(
                "quantity", String.valueOf(quantity),
                "item", item.getMaterial().name(),
                "earnings", String.format("%.2f", totalEarnings)
            )));
            
            return true;
            
        } catch (Exception e) {
            plugin.getLogger().warning("Shop sell transaction failed: " + e.getMessage());
            player.sendMessage(Messages.get("errors.shop.transaction-failed"));
            return false;
        }
    }
    
    /**
     * Manual restock for a shop
     */
    public void restockShop(String zoneId) {
        ShopData shop = shops.get(zoneId);
        if (shop != null) {
            shop.performRestock();
            saveShop(zoneId);
        }
    }
    
    /**
     * Start periodic maintenance tasks
     */
    private void startPeriodicTasks() {
        // Auto-restock task (every 5 minutes)
        restockTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Map.Entry<String, ShopData> entry : shops.entrySet()) {
                ShopData shop = entry.getValue();
                if (shop.isEnabled() && shop.needsRestock()) {
                    shop.performRestock();
                    saveShop(entry.getKey());
                    plugin.getLogger().info("Auto-restocked shop: " + entry.getKey());
                }
            }
        }, 6000L, 6000L); // Every 5 minutes
        
        // Dynamic pricing update task (every hour)
        pricingTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Map.Entry<String, DynamicPricing> entry : pricingEngines.entrySet()) {
                entry.getValue().updateAllPrices();
            }
        }, 72000L, 72000L); // Every hour
    }
    
    /**
     * Shutdown and cleanup tasks
     */
    public void shutdown() {
        if (restockTask != null && !restockTask.isCancelled()) {
            restockTask.cancel();
        }
        if (pricingTask != null && !pricingTask.isCancelled()) {
            pricingTask.cancel();
        }
        saveAllShops();
    }
    
    public Map<String, ShopData> getShops() {
        return shops;
    }
    
    public DynamicPricing getPricingEngine(String zoneId) {
        return pricingEngines.get(zoneId);
    }

    private boolean canFit(Inventory inventory, ItemStack stack, int quantity) {
        int maxStack = Math.max(1, stack.getMaxStackSize());
        int remaining = quantity;

        for (ItemStack invItem : inventory.getStorageContents()) {
            if (remaining <= 0) {
                return true;
            }
            if (invItem == null || invItem.getType() == Material.AIR) {
                remaining -= maxStack;
                continue;
            }
            if (invItem.isSimilar(stack)) {
                remaining -= Math.max(0, maxStack - invItem.getAmount());
            }
        }

        return remaining <= 0;
    }

    private ItemStack createPurchaseItem(ShopItemConfig item, int quantity) {
        ItemStack base = item.getDisplayItem() != null ? item.getDisplayItem().clone() : new ItemStack(item.getMaterial());
        if (item.getDisplayItem() == null && item.getDisplayName() != null && !item.getDisplayName().isEmpty()) {
            ItemMeta meta = base.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(item.getDisplayName());
                base.setItemMeta(meta);
            }
        }
        base.setAmount(quantity);
        return base;
    }
}
