package com.logichh.townycapture;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * Checks for plugin updates via Modrinth API
 */
public class UpdateChecker {
    
    private final TownyCapture plugin;
    private final Logger logger;
    private final String projectId = "IdhAg76d";
    private final String currentVersion;
    
    private String latestVersion = null;
    private String downloadUrl = null;
    private String changelog = null;
    private long lastCheck = 0;
    private static final long CACHE_DURATION = 6 * 60 * 60 * 1000; // 6 hours
    
    public UpdateChecker(TownyCapture plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.currentVersion = plugin.getDescription().getVersion();
    }
    
    /**
     * Check for updates asynchronously
     */
    public CompletableFuture<Boolean> checkForUpdates() {
        // Return cached result if still valid
        if (System.currentTimeMillis() - lastCheck < CACHE_DURATION && latestVersion != null) {
            return CompletableFuture.completedFuture(isUpdateAvailable());
        }
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                String apiUrl = "https://api.modrinth.com/v2/project/" + projectId + "/version";
                URL url = new URL(apiUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("User-Agent", "TownyCapture/" + currentVersion);
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                
                int responseCode = connection.getResponseCode();
                if (responseCode != 200) {
                    logger.warning("Failed to check for updates: HTTP " + responseCode);
                    return false;
                }
                
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                
                // Parse JSON response
                JsonArray versions = JsonParser.parseString(response.toString()).getAsJsonArray();
                if (versions.size() == 0) {
                    logger.warning("No versions found on Modrinth");
                    return false;
                }
                
                // Get the latest version (first in array)
                JsonObject latestVersionObj = versions.get(0).getAsJsonObject();
                latestVersion = latestVersionObj.get("version_number").getAsString();
                
                // Get download URL
                JsonArray files = latestVersionObj.getAsJsonArray("files");
                if (files.size() > 0) {
                    JsonObject primaryFile = files.get(0).getAsJsonObject();
                    downloadUrl = primaryFile.get("url").getAsString();
                }
                
                // Get changelog (truncate if too long)
                if (latestVersionObj.has("changelog")) {
                    String fullChangelog = latestVersionObj.get("changelog").getAsString();
                    changelog = fullChangelog.length() > 200 ? fullChangelog.substring(0, 200) + "..." : fullChangelog;
                }
                
                lastCheck = System.currentTimeMillis();
                
                if (isUpdateAvailable()) {
                    logger.info("Update available! Current: " + currentVersion + " | Latest: " + latestVersion);
                    return true;
                }
                
                return false;
                
            } catch (Exception e) {
                logger.warning("Failed to check for updates: " + e.getMessage());
                if (plugin.getConfig().getBoolean("settings.debug-mode", false)) {
                    e.printStackTrace();
                }
                return false;
            }
        });
    }
    
    /**
     * Check if an update is available
     */
    public boolean isUpdateAvailable() {
        if (latestVersion == null) return false;
        return compareVersions(currentVersion, latestVersion) < 0;
    }
    
    /**
     * Compare two version strings (semantic versioning)
     * Returns: -1 if v1 < v2, 0 if equal, 1 if v1 > v2
     */
    private int compareVersions(String v1, String v2) {
        // Remove 'v' prefix if present
        v1 = v1.replaceFirst("^v", "");
        v2 = v2.replaceFirst("^v", "");
        
        String[] parts1 = v1.split("\\.");
        String[] parts2 = v2.split("\\.");
        
        int length = Math.max(parts1.length, parts2.length);
        for (int i = 0; i < length; i++) {
            int p1 = i < parts1.length ? parseVersionPart(parts1[i]) : 0;
            int p2 = i < parts2.length ? parseVersionPart(parts2[i]) : 0;
            
            if (p1 < p2) return -1;
            if (p1 > p2) return 1;
        }
        
        return 0;
    }
    
    /**
     * Parse version part (handles "1.0.9" and "1.0.9-SNAPSHOT")
     */
    private int parseVersionPart(String part) {
        try {
            // Remove any suffix like -SNAPSHOT, -beta, etc
            String numericPart = part.split("-")[0];
            return Integer.parseInt(numericPart);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
    
    /**
     * Show update notification to player
     */
    public void showUpdateNotification(Player player) {
        if (!isUpdateAvailable()) return;
        if (plugin.isNotificationsDisabled(player)) return;
        
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (plugin.isNotificationsDisabled(player)) {
                return;
            }
            // Send title/subtitle
            player.sendTitle(
                ChatColor.GOLD + "⚡ " + ChatColor.YELLOW + ChatColor.BOLD + "Update Available" + ChatColor.GOLD + " ⚡",
                ChatColor.GRAY + "Current: " + ChatColor.WHITE + currentVersion + ChatColor.GRAY + " → Latest: " + ChatColor.GREEN + latestVersion,
                10, 70, 20
            );
            
            // Send fancy chat notification
            player.sendMessage("");
            player.sendMessage(ChatColor.GOLD + "╔═══════════════════════════════════════════════╗");
            player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + ChatColor.BOLD + "TownyCapture Update Available!" + ChatColor.GOLD + "          ║");
            player.sendMessage(ChatColor.GOLD + "║" + ChatColor.GRAY + "                                               " + ChatColor.GOLD + "║");
            player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.WHITE + "Current Version: " + ChatColor.RED + currentVersion + ChatColor.GRAY + "                       " + ChatColor.GOLD + "║");
            player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.WHITE + "Latest Version:  " + ChatColor.GREEN + ChatColor.BOLD + latestVersion + ChatColor.GRAY + "                       " + ChatColor.GOLD + "║");
            player.sendMessage(ChatColor.GOLD + "║" + ChatColor.GRAY + "                                               " + ChatColor.GOLD + "║");
            
            // Clickable download link
            if (downloadUrl != null) {
                String downloadMsg = ChatColor.GOLD + "║ " + ChatColor.AQUA + ChatColor.UNDERLINE + "Click here to download" + 
                                    ChatColor.GRAY + "                       " + ChatColor.GOLD + "║";
                
                net.md_5.bungee.api.chat.TextComponent downloadComponent = new net.md_5.bungee.api.chat.TextComponent(downloadMsg);
                downloadComponent.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(
                    net.md_5.bungee.api.chat.ClickEvent.Action.OPEN_URL,
                    "https://modrinth.com/plugin/towny-capture-points"
                ));
                downloadComponent.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(
                    net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
                    new net.md_5.bungee.api.chat.ComponentBuilder("Open Modrinth page").create()
                ));
                player.spigot().sendMessage(downloadComponent);
            }
            
            player.sendMessage(ChatColor.GOLD + "╚═══════════════════════════════════════════════╝");
            player.sendMessage("");
            
        }, 60L); // Show after 3 seconds
    }
    
    public String getCurrentVersion() {
        return currentVersion;
    }
    
    public String getLatestVersion() {
        return latestVersion;
    }
    
    public String getDownloadUrl() {
        return downloadUrl;
    }
}
