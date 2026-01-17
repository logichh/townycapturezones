package com.logichh.townycapture;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Manages all statistics GUI menus with beautiful design and pagination.
 */
public class StatisticsGUI {
    
    private final TownyCapture plugin;
    private final StatisticsManager statsManager;
    
    // Track active menu sessions
    private final Map<UUID, MenuSession> activeSessions = new HashMap<>();
    
    // Command usage cooldown (5 minutes)
    private final Map<UUID, Long> commandCooldowns = new HashMap<>();
    private static final long COOLDOWN_MS = 5 * 60 * 1000; // 5 minutes
    private static final Material MAIN_BORDER_MATERIAL = Material.LIGHT_BLUE_STAINED_GLASS_PANE;
    private static final Material SUB_BORDER_MATERIAL = Material.GRAY_STAINED_GLASS_PANE;
    private static final Material FILL_MATERIAL = Material.BLACK_STAINED_GLASS_PANE;
    private static final int BACK_SLOT = 49;
    private static final int INFO_SLOT = 49;
    private static final int HEADER_LEFT_SLOT = 2;
    private static final int HEADER_RIGHT_SLOT = 6;
    private static final int HEADER_CENTER_SLOT = 4;
    private static final int[] LEFT_GRID_SLOTS = {
        10, 11, 12,
        19, 20, 21,
        28, 29, 30,
        37, 38, 39
    };
    private static final int[] RIGHT_GRID_SLOTS = {
        14, 15, 16,
        23, 24, 25,
        32, 33, 34,
        41, 42, 43
    };
    
    public StatisticsGUI(TownyCapture plugin, StatisticsManager statsManager) {
        this.plugin = plugin;
        this.statsManager = statsManager;
    }
    
    /**
     * Check if player can use stats command (cooldown check)
     */
    public boolean canUseStatsCommand(Player player) {
        if (player.hasPermission("capturepoints.admin.stats.nocooldown")) {
            return true;
        }
        
        Long lastUse = commandCooldowns.get(player.getUniqueId());
        if (lastUse == null) {
            return true;
        }
        
        long elapsed = System.currentTimeMillis() - lastUse;
        return elapsed >= COOLDOWN_MS;
    }
    
    /**
     * Get remaining cooldown time in seconds
     */
    public long getCooldownSeconds(Player player) {
        Long lastUse = commandCooldowns.get(player.getUniqueId());
        if (lastUse == null) {
            return 0;
        }
        
        long elapsed = System.currentTimeMillis() - lastUse;
        long remaining = COOLDOWN_MS - elapsed;
        return remaining > 0 ? TimeUnit.MILLISECONDS.toSeconds(remaining) : 0;
    }
    
    /**
     * Set cooldown for player
     */
    public void setCooldown(Player player) {
        if (!player.hasPermission("capturepoints.admin.stats.nocooldown")) {
            commandCooldowns.put(player.getUniqueId(), System.currentTimeMillis());
        }
    }
    
    /**
     * Open main category selection menu
     */
    public void openMainMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, ChatColor.DARK_AQUA + "" + ChatColor.BOLD + "Statistics Hub");
        applyFrame(inv, MAIN_BORDER_MATERIAL, FILL_MATERIAL);

        StatisticsData.ServerRecords records = statsManager.getData().getServerRecords();
        inv.setItem(HEADER_CENTER_SLOT, createStatItem(Material.NETHER_STAR,
            ChatColor.AQUA + "" + ChatColor.BOLD + "Server Snapshot",
            ChatColor.GRAY + "Captures: " + ChatColor.WHITE + records.totalServerCaptures,
            ChatColor.GRAY + "Deaths: " + ChatColor.WHITE + records.totalServerDeaths,
            ChatColor.GRAY + "Economy: " + ChatColor.WHITE + String.format("$%.2f", records.totalServerEconomy)
        ));

        inv.setItem(20, createCategoryItem(Material.BEACON, "Captures",
            ChatColor.GRAY + "Top towns, players, zones",
            ChatColor.DARK_GRAY + "Click to view"
        ));
        
        inv.setItem(22, createCategoryItem(Material.DIAMOND_SWORD, "Combat",
            ChatColor.GRAY + "Kills, deaths, K/D ratios",
            ChatColor.DARK_GRAY + "Click to view"
        ));
        
        inv.setItem(24, createCategoryItem(Material.SHIELD, "Control",
            ChatColor.GRAY + "Hold times, streaks",
            ChatColor.DARK_GRAY + "Click to view"
        ));
        
        inv.setItem(29, createCategoryItem(Material.EMERALD, "Economy",
            ChatColor.GRAY + "Rewards, earnings",
            ChatColor.DARK_GRAY + "Click to view"
        ));
        
        inv.setItem(31, createCategoryItem(Material.CLOCK, "Activity",
            ChatColor.GRAY + "Mob kills, participation",
            ChatColor.DARK_GRAY + "Click to view"
        ));
        
        inv.setItem(33, createCategoryItem(Material.BOOK, "Records",
            ChatColor.GRAY + "All-time bests",
            ChatColor.DARK_GRAY + "Click to view"
        ));
        
        inv.setItem(INFO_SLOT, createInfoItem());
        
        activeSessions.put(player.getUniqueId(), new MenuSession(MenuType.MAIN, 0));
        player.openInventory(inv);
    }
    
    /**
     * Open captures category menu
     */
    public void openCapturesMenu(Player player, int page) {
        Inventory inv = Bukkit.createInventory(null, 54, ChatColor.DARK_AQUA + "" + ChatColor.BOLD + "Statistics - Captures");
        applyFrame(inv, SUB_BORDER_MATERIAL, FILL_MATERIAL);
        
        inv.setItem(BACK_SLOT, createBackButton());
        inv.setItem(HEADER_CENTER_SLOT, createHeaderItem(Material.BEACON, "Captures"));
        inv.setItem(HEADER_LEFT_SLOT, createHeaderItem(Material.WHITE_BANNER, "Top Towns"));
        inv.setItem(HEADER_RIGHT_SLOT, createHeaderItem(Material.PLAYER_HEAD, "Top Players"));
        
        List<Map.Entry<String, Integer>> topTowns = statsManager.getTopTownsByCaptures(10);
        for (int i = 0; i < topTowns.size() && i < LEFT_GRID_SLOTS.length; i++) {
            Map.Entry<String, Integer> entry = topTowns.get(i);
            inv.setItem(LEFT_GRID_SLOTS[i], createTownStatItem(
                i + 1,
                entry.getKey(),
                ChatColor.GOLD + "Captures: " + ChatColor.WHITE + entry.getValue()
            ));
        }
        
        List<Map.Entry<UUID, StatisticsData.PlayerStats>> topPlayers = 
            statsManager.getData().getAllPlayerStats().entrySet().stream()
                .sorted((e1, e2) -> Integer.compare(e2.getValue().totalCaptures, e1.getValue().totalCaptures))
                .limit(10)
                .toList();
        
        for (int i = 0; i < topPlayers.size() && i < RIGHT_GRID_SLOTS.length; i++) {
            Map.Entry<UUID, StatisticsData.PlayerStats> entry = topPlayers.get(i);
            inv.setItem(RIGHT_GRID_SLOTS[i], createPlayerStatItem(
                i + 1,
                entry.getKey(),
                ChatColor.GOLD + "Captures: " + ChatColor.WHITE + entry.getValue().totalCaptures,
                ChatColor.GRAY + "Failed: " + ChatColor.WHITE + entry.getValue().failedCaptures,
                ChatColor.GRAY + "Success Rate: " + ChatColor.WHITE + 
                    String.format("%.1f%%", entry.getValue().getSuccessRate())
            ));
        }
        
        activeSessions.put(player.getUniqueId(), new MenuSession(MenuType.CAPTURES, page));
        player.openInventory(inv);
    }
    
    /**
     * Open combat category menu
     */
    public void openCombatMenu(Player player, int page) {
        Inventory inv = Bukkit.createInventory(null, 54, ChatColor.DARK_AQUA + "" + ChatColor.BOLD + "Statistics - Combat");
        applyFrame(inv, SUB_BORDER_MATERIAL, FILL_MATERIAL);
        
        inv.setItem(BACK_SLOT, createBackButton());
        inv.setItem(HEADER_CENTER_SLOT, createHeaderItem(Material.DIAMOND_SWORD, "Combat"));
        inv.setItem(HEADER_LEFT_SLOT, createHeaderItem(Material.IRON_SWORD, "Most Kills"));
        inv.setItem(HEADER_RIGHT_SLOT, createHeaderItem(Material.DIAMOND_SWORD, "Best K/D"));
        
        List<Map.Entry<UUID, Integer>> topKillers = statsManager.getTopPlayersByKills(10);
        for (int i = 0; i < topKillers.size() && i < LEFT_GRID_SLOTS.length; i++) {
            Map.Entry<UUID, Integer> entry = topKillers.get(i);
            StatisticsData.PlayerStats stats = statsManager.getData().getPlayerStats(entry.getKey());
            inv.setItem(LEFT_GRID_SLOTS[i], createPlayerStatItem(
                i + 1,
                entry.getKey(),
                ChatColor.GOLD + "Kills: " + ChatColor.WHITE + entry.getValue(),
                ChatColor.GRAY + "Deaths: " + ChatColor.WHITE + stats.deathsInZones,
                ChatColor.GRAY + "K/D: " + ChatColor.WHITE + String.format("%.2f", stats.getKDRatio())
            ));
        }
        
        List<Map.Entry<UUID, Double>> topKD = statsManager.getTopPlayersByKDRatio(10);
        for (int i = 0; i < topKD.size() && i < RIGHT_GRID_SLOTS.length; i++) {
            Map.Entry<UUID, Double> entry = topKD.get(i);
            StatisticsData.PlayerStats stats = statsManager.getData().getPlayerStats(entry.getKey());
            inv.setItem(RIGHT_GRID_SLOTS[i], createPlayerStatItem(
                i + 1,
                entry.getKey(),
                ChatColor.GOLD + "K/D: " + ChatColor.WHITE + String.format("%.2f", entry.getValue()),
                ChatColor.GRAY + "Kills: " + ChatColor.WHITE + stats.killsInZones,
                ChatColor.GRAY + "Deaths: " + ChatColor.WHITE + stats.deathsInZones
            ));
        }
        
        activeSessions.put(player.getUniqueId(), new MenuSession(MenuType.COMBAT, page));
        player.openInventory(inv);
    }
    
    /**
     * Open control category menu
     */
    public void openControlMenu(Player player, int page) {
        Inventory inv = Bukkit.createInventory(null, 54, ChatColor.DARK_AQUA + "" + ChatColor.BOLD + "Statistics - Control");
        applyFrame(inv, SUB_BORDER_MATERIAL, FILL_MATERIAL);
        
        inv.setItem(BACK_SLOT, createBackButton());
        inv.setItem(HEADER_CENTER_SLOT, createHeaderItem(Material.SHIELD, "Top Hold Times"));
        
        List<Map.Entry<String, Long>> topTowns = statsManager.getTopTownsByHoldTime(10);
        for (int i = 0; i < topTowns.size() && i < LEFT_GRID_SLOTS.length; i++) {
            Map.Entry<String, Long> entry = topTowns.get(i);
            StatisticsData.TownStats stats = statsManager.getData().getTownStats(entry.getKey());
            inv.setItem(LEFT_GRID_SLOTS[i], createTownStatItem(
                i + 1,
                entry.getKey(),
                ChatColor.GOLD + "Hold Time: " + ChatColor.WHITE + formatDuration(entry.getValue()),
                ChatColor.GRAY + "Current Zones: " + ChatColor.WHITE + stats.currentControlledZones,
                ChatColor.GRAY + "Max Zones: " + ChatColor.WHITE + stats.maxSimultaneousZones
            ));
        }
        
        activeSessions.put(player.getUniqueId(), new MenuSession(MenuType.CONTROL, page));
        player.openInventory(inv);
    }
    
    /**
     * Open economy category menu
     */
    public void openEconomyMenu(Player player, int page) {
        Inventory inv = Bukkit.createInventory(null, 54, ChatColor.DARK_AQUA + "" + ChatColor.BOLD + "Statistics - Economy");
        applyFrame(inv, SUB_BORDER_MATERIAL, FILL_MATERIAL);
        
        inv.setItem(BACK_SLOT, createBackButton());
        
        double totalEconomy = statsManager.getData().getServerRecords().totalServerEconomy;
        inv.setItem(HEADER_CENTER_SLOT, createStatItem(Material.EMERALD_BLOCK, 
            ChatColor.GOLD + "" + ChatColor.BOLD + "Total Economy",
            ChatColor.WHITE + String.format("$%.2f", totalEconomy),
            ChatColor.GRAY + "Distributed to all towns"
        ));
        
        List<Map.Entry<String, Double>> topTowns = statsManager.getTopTownsByRewards(10);
        for (int i = 0; i < topTowns.size() && i < LEFT_GRID_SLOTS.length; i++) {
            Map.Entry<String, Double> entry = topTowns.get(i);
            inv.setItem(LEFT_GRID_SLOTS[i], createTownStatItem(
                i + 1,
                entry.getKey(),
                ChatColor.GOLD + "Rewards: " + ChatColor.WHITE + String.format("$%.2f", entry.getValue())
            ));
        }
        
        activeSessions.put(player.getUniqueId(), new MenuSession(MenuType.ECONOMY, page));
        player.openInventory(inv);
    }
    
    /**
     * Open activity category menu
     */
    public void openActivityMenu(Player player, int page) {
        Inventory inv = Bukkit.createInventory(null, 54, ChatColor.DARK_AQUA + "" + ChatColor.BOLD + "Statistics - Activity");
        applyFrame(inv, SUB_BORDER_MATERIAL, FILL_MATERIAL);
        
        inv.setItem(BACK_SLOT, createBackButton());
        inv.setItem(HEADER_CENTER_SLOT, createHeaderItem(Material.CLOCK, "Top Towns by Mob Kills"));
        
        List<Map.Entry<String, Integer>> topTowns = statsManager.getTopTownsByMobKills(10);
        for (int i = 0; i < topTowns.size() && i < LEFT_GRID_SLOTS.length; i++) {
            Map.Entry<String, Integer> entry = topTowns.get(i);
            inv.setItem(LEFT_GRID_SLOTS[i], createTownStatItem(
                i + 1,
                entry.getKey(),
                ChatColor.GOLD + "Mob Kills: " + ChatColor.WHITE + entry.getValue()
            ));
        }
        
        activeSessions.put(player.getUniqueId(), new MenuSession(MenuType.ACTIVITY, page));
        player.openInventory(inv);
    }
    
    /**
     * Open records category menu
     */
    public void openRecordsMenu(Player player, int page) {
        Inventory inv = Bukkit.createInventory(null, 54, ChatColor.DARK_AQUA + "" + ChatColor.BOLD + "Statistics - Records");
        applyFrame(inv, SUB_BORDER_MATERIAL, FILL_MATERIAL);
        
        inv.setItem(BACK_SLOT, createBackButton());
        inv.setItem(HEADER_CENTER_SLOT, createHeaderItem(Material.BOOK, "Server Records"));
        
        StatisticsData.ServerRecords records = statsManager.getData().getServerRecords();
        
        // Fastest capture
        inv.setItem(10, createStatItem(Material.FEATHER,
            ChatColor.GOLD + "Fastest Capture",
            ChatColor.WHITE + formatDuration(records.fastestCaptureTime),
            ChatColor.GRAY + "Zone: " + ChatColor.WHITE + records.fastestCaptureZone
        ));
        
        // Longest capture
        inv.setItem(12, createStatItem(Material.TURTLE_HELMET,
            ChatColor.GOLD + "Longest Capture",
            ChatColor.WHITE + formatDuration(records.longestCaptureTime),
            ChatColor.GRAY + "Zone: " + ChatColor.WHITE + records.longestCaptureZone
        ));
        
        // Most captured zone
        inv.setItem(14, createStatItem(Material.TARGET,
            ChatColor.GOLD + "Most Captured Zone",
            ChatColor.WHITE + records.mostCapturedZone,
            ChatColor.GRAY + "Captures: " + ChatColor.WHITE + records.mostCapturesCount
        ));
        
        // Most profitable zone
        inv.setItem(16, createStatItem(Material.GOLD_BLOCK,
            ChatColor.GOLD + "Most Profitable Zone",
            ChatColor.WHITE + records.mostProfitableZone,
            ChatColor.GRAY + "Total: " + ChatColor.WHITE + String.format("$%.2f", records.mostProfitableReward)
        ));
        
        // Dominant town
        inv.setItem(20, createStatItem(Material.WHITE_BANNER,
            ChatColor.GOLD + "Dominant Town",
            ChatColor.WHITE + records.dominantTown,
            ChatColor.GRAY + "Captures: " + ChatColor.WHITE + records.dominantTownCaptures
        ));
        
        // First capture
        inv.setItem(22, createStatItem(Material.NETHER_STAR,
            ChatColor.GOLD + "First Capture",
            ChatColor.WHITE + (records.firstCaptureTime > 0 ? 
                new Date(records.firstCaptureTime).toString() : "None"),
            ChatColor.GRAY + "Town: " + ChatColor.WHITE + records.firstCapturingTown,
            ChatColor.GRAY + "Player: " + ChatColor.WHITE + records.firstCapturingPlayer
        ));
        
        // Most deadly zone
        inv.setItem(24, createStatItem(Material.SKELETON_SKULL,
            ChatColor.GOLD + "Most Deadly Zone",
            ChatColor.WHITE + records.mostDeadlyZone,
            ChatColor.GRAY + "Deaths: " + ChatColor.WHITE + records.mostDeaths
        ));
        
        // Server totals
        inv.setItem(31, createStatItem(Material.DIAMOND,
            ChatColor.GOLD + "" + ChatColor.BOLD + "Server Totals",
            ChatColor.WHITE + "Captures: " + ChatColor.YELLOW + records.totalServerCaptures,
            ChatColor.WHITE + "Deaths: " + ChatColor.YELLOW + records.totalServerDeaths,
            ChatColor.WHITE + "Mob Kills: " + ChatColor.YELLOW + records.totalServerMobKills,
            ChatColor.WHITE + "Economy: " + ChatColor.YELLOW + String.format("$%.2f", records.totalServerEconomy)
        ));
        
        activeSessions.put(player.getUniqueId(), new MenuSession(MenuType.RECORDS, page));
        player.openInventory(inv);
    }
    
    /**
     * Handle menu click
     */
    public void handleClick(Player player, int slot, Inventory inv) {
        MenuSession session = activeSessions.get(player.getUniqueId());
        if (session == null) return;
        
        switch (session.type) {
            case MAIN:
                handleMainMenuClick(player, slot);
                break;
            case CAPTURES:
            case COMBAT:
            case CONTROL:
            case ECONOMY:
            case ACTIVITY:
            case RECORDS:
                if (slot == BACK_SLOT) {
                    player.closeInventory();
                    player.performCommand("cap stats");
                }
                break;
        }
    }
    
    private void handleMainMenuClick(Player player, int slot) {
        switch (slot) {
            case 20: openCapturesMenu(player, 0); break;
            case 22: openCombatMenu(player, 0); break;
            case 24: openControlMenu(player, 0); break;
            case 29: openEconomyMenu(player, 0); break;
            case 31: openActivityMenu(player, 0); break;
            case 33: openRecordsMenu(player, 0); break;
        }
    }
    
    /**
     * Close menu for player
     */
    public void closeMenu(Player player) {
        activeSessions.remove(player.getUniqueId());
    }
    
    // ==================== HELPER METHODS ====================
    
    private void applyFrame(Inventory inv, Material borderMaterial, Material fillMaterial) {
        ItemStack border = createFiller(borderMaterial);
        ItemStack fill = createFiller(fillMaterial);
        int size = inv.getSize();
        int rows = size / 9;

        for (int slot = 0; slot < size; slot++) {
            int row = slot / 9;
            int col = slot % 9;
            if (row == 0 || row == rows - 1 || col == 0 || col == 8) {
                setIfEmpty(inv, slot, border);
            }
        }

        for (int slot = 0; slot < size; slot++) {
            if (inv.getItem(slot) == null) {
                inv.setItem(slot, fill);
            }
        }
    }

    private void setIfEmpty(Inventory inv, int slot, ItemStack item) {
        if (inv.getItem(slot) == null) {
            inv.setItem(slot, item);
        }
    }

    private ItemStack createFiller(Material material) {
        ItemStack filler = new ItemStack(material);
        ItemMeta meta = filler.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            filler.setItemMeta(meta);
        }
        return filler;
    }
    
    private ItemStack createCategoryItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.AQUA + "" + ChatColor.BOLD + name);
            meta.setLore(Arrays.asList(lore));
            item.setItemMeta(meta);
        }
        return item;
    }
    
    private ItemStack createHeaderItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.YELLOW + "" + ChatColor.BOLD + name);
            item.setItemMeta(meta);
        }
        return item;
    }
    
    private ItemStack createTownStatItem(int rank, String townName, String... stats) {
        ItemStack item = new ItemStack(Material.WHITE_BANNER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + "#" + rank + " " + ChatColor.WHITE + townName);
            meta.setLore(Arrays.asList(stats));
            item.setItemMeta(meta);
        }
        return item;
    }
    
    private ItemStack createPlayerStatItem(int rank, UUID playerId, String... stats) {
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerId);
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(offlinePlayer);
            String playerName = offlinePlayer.getName() != null ? offlinePlayer.getName() : "Unknown";
            meta.setDisplayName(ChatColor.AQUA + "#" + rank + " " + ChatColor.WHITE + playerName);
            meta.setLore(Arrays.asList(stats));
            item.setItemMeta(meta);
        }
        return item;
    }
    
    private ItemStack createStatItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(Arrays.asList(lore));
            item.setItemMeta(meta);
        }
        return item;
    }
    
    private ItemStack createBackButton() {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.RED + "" + ChatColor.BOLD + "< Back");
            meta.setLore(Collections.singletonList(ChatColor.GRAY + "Return to categories"));
            item.setItemMeta(meta);
        }
        return item;
    }
    
    private ItemStack createInfoItem() {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.AQUA + "" + ChatColor.BOLD + "How It Works");
            meta.setLore(Arrays.asList(
                ChatColor.GRAY + "Stats update as captures happen",
                ChatColor.GRAY + "and refresh each time you open.",
                "",
                ChatColor.GRAY + "All stats are public",
                ChatColor.GRAY + "and visible to everyone."
            ));
            item.setItemMeta(meta);
        }
        return item;
    }
    
    private String formatDuration(long millis) {
        if (millis == 0) return "N/A";
        
        long days = TimeUnit.MILLISECONDS.toDays(millis);
        long hours = TimeUnit.MILLISECONDS.toHours(millis) % 24;
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60;
        
        if (days > 0) {
            return String.format("%dd %dh %dm", days, hours, minutes);
        } else if (hours > 0) {
            return String.format("%dh %dm", hours, minutes);
        } else {
            return String.format("%dm", minutes);
        }
    }
    
    // ==================== INNER CLASSES ====================
    
    private static class MenuSession {
        final MenuType type;
        final int page;
        
        MenuSession(MenuType type, int page) {
            this.type = type;
            this.page = page;
        }
    }
    
    private enum MenuType {
        MAIN, CAPTURES, COMBAT, CONTROL, ECONOMY, ACTIVITY, RECORDS
    }
}

