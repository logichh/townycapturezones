package com.logichh.townycapture;

import org.bukkit.configuration.ConfigurationSection;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.awt.Color;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Discord Webhook Manager with rich embeds and per-zone rate limiting
 */
public class DiscordWebhook {
    
    private final TownyCapture plugin;
    private final Logger logger;
    private String webhookUrl;
    private boolean enabled;
    private boolean useEmbeds;
    private String mentionRole;
    private String mentionRoleId;
    private boolean showCoordinates;
    private boolean showRewardsAmount;
    private String embedAuthorName;
    private String embedAuthorIcon;
    private String embedFooterText;
    private String embedFooterIcon;
    private String embedThumbnailUrl;
    private boolean embedTimestamp;
    
    // Alert toggles
    private final Map<String, Boolean> alertToggles = new HashMap<>();
    
    // Per-zone rate limiting: zoneId -> last message timestamp
    private final Map<String, Long> lastMessageTime = new ConcurrentHashMap<>();
    private long rateLimitMs = 1000; // 1 second per zone by default
    
    public DiscordWebhook(TownyCapture plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        loadConfig();
    }
    
    /**
     * Load Discord configuration from config.yml
     */
    public void loadConfig() {
        ConfigurationSection discord = plugin.getConfig().getConfigurationSection("discord");
        if (discord == null) {
            this.enabled = false;
            logger.info("Discord webhook not configured.");
            return;
        }
        
        this.enabled = discord.getBoolean("enabled", false);
        this.webhookUrl = discord.getString("webhook-url", "");
        this.useEmbeds = discord.getBoolean("use-embeds", true);
        parseMentionRole(discord.getString("mention-role", ""));
        this.showCoordinates = discord.getBoolean("show-coordinates", true);
        this.showRewardsAmount = discord.getBoolean("show-rewards-amount", true);
        this.rateLimitMs = discord.getLong("rate-limit-ms", 1000);

        ConfigurationSection embed = discord.getConfigurationSection("embed");
        String serverName = plugin.getServer() != null ? plugin.getServer().getName() : "Server";
        String authorNameRaw = embed != null ? embed.getString("author-name", "TownyCapture") : "TownyCapture";
        this.embedAuthorName = resolveEmbedText(authorNameRaw, serverName);
        this.embedAuthorIcon = embed != null ? embed.getString("author-icon", "") : "";
        String footerTextRaw = embed != null ? embed.getString("footer-text", "TownyCapture") : "TownyCapture";
        this.embedFooterText = resolveEmbedText(footerTextRaw, serverName);
        this.embedFooterIcon = embed != null ? embed.getString("footer-icon", "") : "";
        this.embedThumbnailUrl = embed != null ? embed.getString("thumbnail-url", "") : "";
        this.embedTimestamp = embed == null || embed.getBoolean("show-timestamp", true);
        
        // Load alert toggles
        ConfigurationSection alerts = discord.getConfigurationSection("alerts");
        if (alerts != null) {
            alertToggles.put("capture-started", alerts.getBoolean("capture-started", true));
            alertToggles.put("capture-completed", alerts.getBoolean("capture-completed", true));
            alertToggles.put("capture-failed", alerts.getBoolean("capture-failed", true));
            alertToggles.put("capture-cancelled", alerts.getBoolean("capture-cancelled", true));
            alertToggles.put("rewards-distributed", alerts.getBoolean("rewards-distributed", true));
            alertToggles.put("reinforcement-phases", alerts.getBoolean("reinforcement-phases", false));
            alertToggles.put("zone-created", alerts.getBoolean("zone-created", true));
            alertToggles.put("zone-deleted", alerts.getBoolean("zone-deleted", true));
            alertToggles.put("weekly-reset", alerts.getBoolean("weekly-reset", true));
            alertToggles.put("first-capture-bonus", alerts.getBoolean("first-capture-bonus", true));
            alertToggles.put("player-death", alerts.getBoolean("player-death", true));
            alertToggles.put("new-records", alerts.getBoolean("new-records", false));
            alertToggles.put("milestones", alerts.getBoolean("milestones", false));
        }
        
        if (enabled && !webhookUrl.isEmpty()) {
            logger.info("Discord webhook enabled! Alerts will be sent to Discord.");
        } else if (enabled) {
            logger.warning("Discord webhook enabled but URL not configured!");
            this.enabled = false;
        }
    }

    private void parseMentionRole(String rawValue) {
        mentionRole = "";
        mentionRoleId = null;
        if (rawValue == null) {
            return;
        }
        String trimmed = rawValue.trim();
        if (trimmed.isEmpty()) {
            return;
        }
        if (trimmed.matches("^<@&(\\d+)>$")) {
            mentionRole = trimmed;
            mentionRoleId = trimmed.substring(3, trimmed.length() - 1);
            return;
        }
        if (trimmed.matches("^\\d+$")) {
            mentionRoleId = trimmed;
            mentionRole = "<@&" + trimmed + ">";
            return;
        }
        mentionRole = trimmed;
        if (enabled) {
            logger.warning("Discord mention-role should be a role ID or <@&ROLE_ID> to ensure role pings work.");
        }
    }

    private String resolveEmbedText(String value, String serverName) {
        if (value == null) {
            return "";
        }
        String resolved = value.replace("{server}", serverName);
        resolved = resolved.replace("{plugin}", plugin.getName());
        resolved = resolved.replace("{version}", plugin.getDescription().getVersion());
        return resolved;
    }
    
    /**
     * Check if an alert type is enabled
     */
    private boolean isAlertEnabled(String alertType) {
        return enabled && alertToggles.getOrDefault(alertType, false);
    }
    
    /**
     * Check rate limit for a zone
     */
    private boolean isRateLimited(String zoneId) {
        long now = System.currentTimeMillis();
        Long lastTime = lastMessageTime.get(zoneId);
        
        // Check rate limit
        if (lastTime != null && (now - lastTime) < rateLimitMs) {
            return true;
        }
        
        // Atomically update if not rate limited
        // Use putIfAbsent for the initial case, compute for updates
        lastMessageTime.compute(zoneId, (key, oldValue) -> {
            if (oldValue == null || (now - oldValue) >= rateLimitMs) {
                return now;
            }
            return oldValue;
        });
        
        return false;
    }
    
    /**
     * Send capture started alert
     */
    public void sendCaptureStarted(String zoneId, String zoneName, String townName, String location) {
        if (!isAlertEnabled("capture-started") || isRateLimited(zoneId)) return;
        
        if (useEmbeds) {
            sendEmbed("⚔️ Capture Started!", 
                townName + " has started capturing " + zoneName + "!",
                Color.YELLOW,
                createField("Zone", zoneName, true),
                createField("Town", townName, true),
                showCoordinates ? createField("Location", location, true) : null
            );
        } else {
            sendPlainText("⚔️ **" + townName + "** has started capturing **" + zoneName + "**!");
        }
    }
    
    /**
     * Send capture completed alert
     */
    public void sendCaptureCompleted(String zoneId, String zoneName, String townName, String captureTime) {
        if (!isAlertEnabled("capture-completed") || isRateLimited(zoneId)) return;
        
        if (useEmbeds) {
            sendEmbed("✅ Capture Complete!", 
                townName + " has successfully captured " + zoneName + "!",
                Color.GREEN,
                createField("Zone", zoneName, true),
                createField("Controlling Town", townName, true),
                createField("Capture Time", captureTime, true)
            );
        } else {
            sendPlainText("✅ **" + townName + "** has captured **" + zoneName + "**!");
        }
    }
    
    /**
     * Send capture failed alert
     */
    public void sendCaptureFailed(String zoneId, String zoneName, String townName, String reason) {
        if (!isAlertEnabled("capture-failed") || isRateLimited(zoneId)) return;
        
        if (useEmbeds) {
            sendEmbed("❌ Capture Failed!", 
                townName + "'s capture of " + zoneName + " has failed.",
                Color.RED,
                createField("Zone", zoneName, true),
                createField("Town", townName, true),
                createField("Reason", reason, false)
            );
        } else {
            sendPlainText("❌ **" + townName + "** failed to capture **" + zoneName + "**. Reason: " + reason);
        }
    }
    
    /**
     * Send capture cancelled alert
     */
    public void sendCaptureCancelled(String zoneId, String zoneName, String townName, String reason) {
        if (!isAlertEnabled("capture-cancelled") || isRateLimited(zoneId)) return;
        
        if (useEmbeds) {
            sendEmbed("⚠️ Capture Cancelled", 
                townName + "'s capture of " + zoneName + " was cancelled.",
                Color.ORANGE,
                createField("Zone", zoneName, true),
                createField("Town", townName, true),
                createField("Reason", reason, false)
            );
        } else {
            sendPlainText("⚠️ **" + townName + "**'s capture of **" + zoneName + "** was cancelled. Reason: " + reason);
        }
    }
    
    /**
     * Send rewards distributed alert
     */
    public void sendRewardsDistributed(String zoneId, String zoneName, String townName, double amount, String rewardType) {
        if (!isAlertEnabled("rewards-distributed")) return;
        
        String amountStr = showRewardsAmount ? String.format("$%.2f", amount) : "[Hidden]";
        
        if (useEmbeds) {
            sendEmbed("💰 Rewards Distributed", 
                townName + " received " + rewardType + " rewards for controlling " + zoneName,
                new Color(255, 215, 0), // Gold
                createField("Zone", zoneName, true),
                createField("Town", townName, true),
                showRewardsAmount ? createField("Amount", amountStr, true) : null,
                createField("Type", rewardType, true)
            );
        } else {
            sendPlainText("💰 **" + townName + "** received " + amountStr + " (" + rewardType + ") for **" + zoneName + "**!");
        }
    }
    
    /**
     * Send reinforcement phase alert
     */
    public void sendReinforcementPhase(String zoneId, String zoneName, int phase, int mobCount) {
        if (!isAlertEnabled("reinforcement-phases") || isRateLimited(zoneId)) return;
        
        if (useEmbeds) {
            sendEmbed("🛡️ Reinforcements Arrived", 
                "Phase " + phase + " defenders have spawned at " + zoneName,
                Color.RED,
                createField("Zone", zoneName, true),
                createField("Phase", String.valueOf(phase), true),
                createField("Defenders", mobCount + " mobs", true)
            );
        } else {
            sendPlainText("🛡️ **Phase " + phase + "** reinforcements arrived at **" + zoneName + "** (" + mobCount + " defenders)");
        }
    }
    
    /**
     * Send zone created alert
     */
    public void sendZoneCreated(String zoneId, String zoneName, String creator, String type, int radius, double reward) {
        if (!isAlertEnabled("zone-created")) return;
        
        if (useEmbeds) {
            sendEmbed("🆕 New Capture Zone Created", 
                "A new capture zone has been created!",
                Color.CYAN,
                createField("Zone Name", zoneName, true),
                createField("Type", type, true),
                createField("Radius", radius + " chunks", true),
                showRewardsAmount ? createField("Reward", String.format("$%.2f", reward), true) : null,
                createField("Created By", creator, true)
            );
        } else {
            sendPlainText("🆕 New zone **" + zoneName + "** created by **" + creator + "**!");
        }
    }
    
    /**
     * Send zone deleted alert
     */
    public void sendZoneDeleted(String zoneId, String zoneName, String deletedBy) {
        if (!isAlertEnabled("zone-deleted")) return;
        
        if (useEmbeds) {
            sendEmbed("🗑️ Capture Zone Deleted", 
                zoneName + " has been permanently deleted.",
                Color.GRAY,
                createField("Zone Name", zoneName, true),
                createField("Deleted By", deletedBy, true)
            );
        } else {
            sendPlainText("🗑️ Zone **" + zoneName + "** deleted by **" + deletedBy + "**");
        }
    }
    
    /**
     * Send weekly reset alert
     */
    public void sendWeeklyReset(int zonesReset) {
        if (!isAlertEnabled("weekly-reset")) return;
        
        if (useEmbeds) {
            sendEmbed("🔄 Weekly Reset Complete", 
                "Weekly reset completed for configured zones.",
                Color.MAGENTA,
                createField("Zones Reset", String.valueOf(zonesReset), true),
                createField("First Capture Bonus", "Active for reset zones!", false)
            );
        } else {
            sendPlainText("🔄 **Weekly Reset Complete!** " + zonesReset + " zones have been neutralized. First capture bonus is active!");
        }
    }
    
    /**
     * Send first capture bonus alert
     */
    public void sendFirstCaptureBonus(String zoneId, String zoneName, String townName, double bonusAmount) {
        if (!isAlertEnabled("first-capture-bonus")) return;
        
        String amountStr = showRewardsAmount ? String.format("$%.2f", bonusAmount) : "[Hidden]";
        
        if (useEmbeds) {
            sendEmbed("🎁 First Capture Bonus!", 
                townName + " earned a bonus for being first to capture " + zoneName + " after reset!",
                new Color(255, 140, 0), // Dark orange
                createField("Zone", zoneName, true),
                createField("Town", townName, true),
                showRewardsAmount ? createField("Bonus", amountStr, true) : null
            );
        } else {
            sendPlainText("🎁 **" + townName + "** earned " + amountStr + " first capture bonus for **" + zoneName + "**!");
        }
    }
    
    /**
     * Send player death alert
     */
    public void sendPlayerDeath(String zoneId, String zoneName, String victim, String killer, String townName) {
        if (!isAlertEnabled("player-death") || isRateLimited(zoneId)) return;
        
        if (useEmbeds) {
            sendEmbed("☠️ Player Death in Capture Zone", 
                victim + " was killed by " + killer + " during " + townName + "'s capture attempt!",
                Color.DARK_GRAY,
                createField("Zone", zoneName, true),
                createField("Victim", victim, true),
                createField("Killer", killer, true)
            );
        } else {
            sendPlainText("☠️ **" + victim + "** was killed by **" + killer + "** at **" + zoneName + "**!");
        }
    }
    
    /**
     * Send new record alert
     */
    public void sendNewRecord(String recordType, String holder, String value) {
        if (!isAlertEnabled("new-records")) return;
        
        if (useEmbeds) {
            sendEmbed("🏆 New Server Record!", 
                "A new " + recordType + " record has been set!",
                new Color(218, 165, 32), // Goldenrod
                createField("Record Type", recordType, true),
                createField("Holder", holder, true),
                createField("Value", value, true)
            );
        } else {
            sendPlainText("🏆 **New Record!** " + holder + " set a new " + recordType + " record: " + value);
        }
    }
    
    /**
     * Send milestone alert
     */
    public void sendMilestone(String entityName, String entityType, String milestone) {
        if (!isAlertEnabled("milestones")) return;
        
        if (useEmbeds) {
            sendEmbed("🎉 Milestone Reached!", 
                entityName + " has reached a new milestone!",
                Color.PINK,
                createField(entityType, entityName, true),
                createField("Milestone", milestone, false)
            );
        } else {
            sendPlainText("🎉 **" + entityName + "** reached milestone: " + milestone);
        }
    }
    
    /**
     * Create an embed field
     */
    @SuppressWarnings("unchecked")
    private JSONObject createField(String name, String value, boolean inline) {
        if (name == null || value == null) return null;
        
        JSONObject field = new JSONObject();
        field.put("name", name);
        field.put("value", value);
        field.put("inline", inline);
        return field;
    }
    
    /**
     * Send a rich Discord embed
     */
    @SuppressWarnings("unchecked")
    private void sendEmbed(String title, String description, Color color, JSONObject... fields) {
        CompletableFuture.runAsync(() -> {
            try {
                JSONObject embed = buildEmbed(title, description, color, fields);
                
                // Build payload
                JSONObject payload = new JSONObject();
                applyRoleMention(payload, null);
                JSONArray embeds = new JSONArray();
                embeds.add(embed);
                payload.put("embeds", embeds);
                
                sendWebhook(payload);
                
            } catch (Exception e) {
                logger.warning("Failed to send Discord embed: " + e.getMessage());
            }
        });
    }
    
    /**
     * Send plain text message
     */
    @SuppressWarnings("unchecked")
    private void sendPlainText(String message) {
        CompletableFuture.runAsync(() -> {
            try {
                JSONObject payload = new JSONObject();
                applyRoleMention(payload, message);
                sendWebhook(payload);
                
            } catch (Exception e) {
                logger.warning("Failed to send Discord message: " + e.getMessage());
            }
        });
    }

    @SuppressWarnings("unchecked")
    private JSONObject buildEmbed(String title, String description, Color color, JSONObject... fields) {
        JSONObject embed = new JSONObject();
        embed.put("title", title);
        embed.put("description", description);
        embed.put("color", color.getRGB() & 0xFFFFFF);
        if (embedTimestamp) {
            embed.put("timestamp", Instant.now().toString());
        }
        if (embedAuthorName != null && !embedAuthorName.isEmpty()) {
            JSONObject author = new JSONObject();
            author.put("name", embedAuthorName);
            if (embedAuthorIcon != null && !embedAuthorIcon.isEmpty()) {
                author.put("icon_url", embedAuthorIcon);
            }
            embed.put("author", author);
        }
        if (embedFooterText != null && !embedFooterText.isEmpty()) {
            JSONObject footer = new JSONObject();
            footer.put("text", embedFooterText);
            if (embedFooterIcon != null && !embedFooterIcon.isEmpty()) {
                footer.put("icon_url", embedFooterIcon);
            }
            embed.put("footer", footer);
        }
        if (embedThumbnailUrl != null && !embedThumbnailUrl.isEmpty()) {
            JSONObject thumbnail = new JSONObject();
            thumbnail.put("url", embedThumbnailUrl);
            embed.put("thumbnail", thumbnail);
        }
        if (fields != null && fields.length > 0) {
            JSONArray fieldArray = new JSONArray();
            for (JSONObject field : fields) {
                if (field != null) {
                    fieldArray.add(field);
                }
            }
            if (!fieldArray.isEmpty()) {
                embed.put("fields", fieldArray);
            }
        }
        return embed;
    }

    @SuppressWarnings("unchecked")
    private void applyRoleMention(JSONObject payload, String message) {
        if (message == null) {
            message = "";
        }
        if (mentionRole == null || mentionRole.isEmpty()) {
            if (!message.isEmpty()) {
                payload.put("content", message);
            }
            return;
        }
        String content = message.isEmpty() ? mentionRole : mentionRole + " " + message;
        payload.put("content", content);
        if (mentionRoleId != null && !mentionRoleId.isEmpty()) {
            JSONObject allowedMentions = new JSONObject();
            allowedMentions.put("parse", new JSONArray());
            JSONArray roles = new JSONArray();
            roles.add(mentionRoleId);
            allowedMentions.put("roles", roles);
            payload.put("allowed_mentions", allowedMentions);
        }
    }
    
    /**
     * Send webhook HTTP request
     */
    private void sendWebhook(JSONObject payload) {
        try {
            URL url = new URL(webhookUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("User-Agent", "TownyCapture-Webhook/1.0");
            connection.setDoOutput(true);
            
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = payload.toJSONString().getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
            
            int responseCode = connection.getResponseCode();
            if (responseCode == 204 || responseCode == 200) {
                // Success - Discord returns 204 No Content on success
                if (plugin.getConfig().getBoolean("settings.debug-mode", false)) {
                    logger.info("Discord webhook sent successfully.");
                }
            } else {
                logger.warning("Discord webhook returned code " + responseCode);
            }
            
            connection.disconnect();
            
        } catch (Exception e) {
            logger.warning("Failed to send Discord webhook: " + e.getMessage());
            if (plugin.getConfig().getBoolean("settings.debug-mode", false)) {
                e.printStackTrace();
            }
        }
    }
    
    /**
     * Test the webhook connection
     */
    public boolean testWebhook() {
        if (!enabled || webhookUrl.isEmpty()) {
            return false;
        }
        
        try {
            if (useEmbeds) {
                sendEmbed("✅ Webhook Test", 
                    "TownyCapture Discord webhook is working correctly!",
                    Color.GREEN,
                    createField("Status", "Connected", true),
                    createField("Plugin Version", plugin.getDescription().getVersion(), true)
                );
            } else {
                sendPlainText("✅ **TownyCapture webhook test successful!**");
            }
            return true;
        } catch (Exception e) {
            logger.severe("Webhook test failed: " + e.getMessage());
            return false;
        }
    }
}
