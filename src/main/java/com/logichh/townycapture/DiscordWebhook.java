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
            sendEmbed(Messages.get("discord.capture.started.title"), 
                Messages.get("discord.capture.started.description", Map.of(
                    "town", townName,
                    "zone", zoneName
                )),
                Color.YELLOW,
                createField(Messages.get("discord.field.zone"), zoneName, true),
                createField(Messages.get("discord.field.town"), townName, true),
                showCoordinates ? createField(Messages.get("discord.field.location"), location, true) : null
            );
        } else {
            sendPlainText(Messages.get("discord.capture.started.plain", Map.of(
                "town", townName,
                "zone", zoneName
            )));
        }
    }
    
    /**
     * Send capture completed alert
     */
    public void sendCaptureCompleted(String zoneId, String zoneName, String townName, String captureTime) {
        if (!isAlertEnabled("capture-completed") || isRateLimited(zoneId)) return;
        
        if (useEmbeds) {
            sendEmbed(Messages.get("discord.capture.completed.title"), 
                Messages.get("discord.capture.completed.description", Map.of(
                    "town", townName,
                    "zone", zoneName
                )),
                Color.GREEN,
                createField(Messages.get("discord.field.zone"), zoneName, true),
                createField(Messages.get("discord.field.controlling-town"), townName, true),
                createField(Messages.get("discord.field.capture-time"), captureTime, true)
            );
        } else {
            sendPlainText(Messages.get("discord.capture.completed.plain", Map.of(
                "town", townName,
                "zone", zoneName
            )));
        }
    }
    
    /**
     * Send capture failed alert
     */
    public void sendCaptureFailed(String zoneId, String zoneName, String townName, String reason) {
        if (!isAlertEnabled("capture-failed") || isRateLimited(zoneId)) return;
        
        if (useEmbeds) {
            sendEmbed(Messages.get("discord.capture.failed.title"), 
                Messages.get("discord.capture.failed.description", Map.of(
                    "town", townName,
                    "zone", zoneName
                )),
                Color.RED,
                createField(Messages.get("discord.field.zone"), zoneName, true),
                createField(Messages.get("discord.field.town"), townName, true),
                createField(Messages.get("discord.field.reason"), reason, false)
            );
        } else {
            sendPlainText(Messages.get("discord.capture.failed.plain", Map.of(
                "town", townName,
                "zone", zoneName,
                "reason", reason
            )));
        }
    }
    
    /**
     * Send capture cancelled alert
     */
    public void sendCaptureCancelled(String zoneId, String zoneName, String townName, String reason) {
        if (!isAlertEnabled("capture-cancelled") || isRateLimited(zoneId)) return;
        
        if (useEmbeds) {
            sendEmbed(Messages.get("discord.capture.cancelled.title"), 
                Messages.get("discord.capture.cancelled.description", Map.of(
                    "town", townName,
                    "zone", zoneName
                )),
                Color.ORANGE,
                createField(Messages.get("discord.field.zone"), zoneName, true),
                createField(Messages.get("discord.field.town"), townName, true),
                createField(Messages.get("discord.field.reason"), reason, false)
            );
        } else {
            sendPlainText(Messages.get("discord.capture.cancelled.plain", Map.of(
                "town", townName,
                "zone", zoneName,
                "reason", reason
            )));
        }
    }
    
    /**
     * Send rewards distributed alert
     */
    public void sendRewardsDistributed(String zoneId, String zoneName, String townName, double amount, String rewardType) {
        if (!isAlertEnabled("rewards-distributed")) return;
        
        String amountStr = showRewardsAmount ? 
            Messages.get("discord.format.currency", Map.of("amount", String.format("%.2f", amount))) : 
            Messages.get("discord.value.hidden-amount");
        
        if (useEmbeds) {
            sendEmbed(Messages.get("discord.rewards.distributed.title"), 
                Messages.get("discord.rewards.distributed.description", Map.of(
                    "town", townName,
                    "type", rewardType,
                    "zone", zoneName
                )),
                new Color(255, 215, 0), // Gold
                createField(Messages.get("discord.field.zone"), zoneName, true),
                createField(Messages.get("discord.field.town"), townName, true),
                showRewardsAmount ? createField(Messages.get("discord.field.amount"), amountStr, true) : null,
                createField(Messages.get("discord.field.type"), rewardType, true)
            );
        } else {
            sendPlainText(Messages.get("discord.rewards.distributed.plain", Map.of(
                "town", townName,
                "amount", amountStr,
                "type", rewardType,
                "zone", zoneName
            )));
        }
    }
    
    /**
     * Send reinforcement phase alert
     */
    public void sendReinforcementPhase(String zoneId, String zoneName, int phase, int mobCount) {
        if (!isAlertEnabled("reinforcement-phases") || isRateLimited(zoneId)) return;
        
        if (useEmbeds) {
            sendEmbed(Messages.get("discord.reinforcements.title"), 
                Messages.get("discord.reinforcements.description", Map.of(
                    "phase", String.valueOf(phase),
                    "zone", zoneName
                )),
                Color.RED,
                createField(Messages.get("discord.field.zone"), zoneName, true),
                createField(Messages.get("discord.field.phase"), String.valueOf(phase), true),
                createField(Messages.get("discord.field.defenders"), 
                    Messages.get("discord.value.mobs", Map.of("count", String.valueOf(mobCount))), true)
            );
        } else {
            sendPlainText(Messages.get("discord.reinforcements.plain", Map.of(
                "phase", String.valueOf(phase),
                "zone", zoneName,
                "count", String.valueOf(mobCount)
            )));
        }
    }
    
    /**
     * Send zone created alert
     */
    public void sendZoneCreated(String zoneId, String zoneName, String creator, String type, int radius, double reward) {
        if (!isAlertEnabled("zone-created")) return;
        
        if (useEmbeds) {
            String rewardStr = showRewardsAmount ? 
                Messages.get("discord.format.currency", Map.of("amount", String.format("%.2f", reward))) : 
                Messages.get("discord.value.hidden-amount");
            sendEmbed(Messages.get("discord.zone.created.title"), 
                Messages.get("discord.zone.created.description"),
                Color.CYAN,
                createField(Messages.get("discord.field.zone-name"), zoneName, true),
                createField(Messages.get("discord.field.type"), type, true),
                createField(Messages.get("discord.field.radius"), 
                    Messages.get("discord.value.chunks", Map.of("count", String.valueOf(radius))), true),
                showRewardsAmount ? createField(Messages.get("discord.field.reward"), rewardStr, true) : null,
                createField(Messages.get("discord.field.created-by"), creator, true)
            );
        } else {
            sendPlainText(Messages.get("discord.zone.created.plain", Map.of(
                "zone", zoneName,
                "creator", creator
            )));
        }
    }
    
    /**
     * Send zone deleted alert
     */
    public void sendZoneDeleted(String zoneId, String zoneName, String deletedBy) {
        if (!isAlertEnabled("zone-deleted")) return;
        
        if (useEmbeds) {
            sendEmbed(Messages.get("discord.zone.deleted.title"), 
                Messages.get("discord.zone.deleted.description", Map.of("zone", zoneName)),
                Color.GRAY,
                createField(Messages.get("discord.field.zone-name"), zoneName, true),
                createField(Messages.get("discord.field.deleted-by"), deletedBy, true)
            );
        } else {
            sendPlainText(Messages.get("discord.zone.deleted.plain", Map.of(
                "zone", zoneName,
                "deleted_by", deletedBy
            )));
        }
    }
    
    /**
     * Send weekly reset alert
     */
    public void sendWeeklyReset(int zonesReset) {
        if (!isAlertEnabled("weekly-reset")) return;
        
        if (useEmbeds) {
            sendEmbed(Messages.get("discord.weekly-reset.title"), 
                Messages.get("discord.weekly-reset.description"),
                Color.MAGENTA,
                createField(Messages.get("discord.field.zones-reset"), String.valueOf(zonesReset), true),
                createField(Messages.get("discord.field.first-capture-bonus"), 
                    Messages.get("discord.value.first-capture-bonus-active"), false)
            );
        } else {
            sendPlainText(Messages.get("discord.weekly-reset.plain", Map.of(
                "count", String.valueOf(zonesReset)
            )));
        }
    }
    
    /**
     * Send first capture bonus alert
     */
    public void sendFirstCaptureBonus(String zoneId, String zoneName, String townName, double bonusAmount) {
        if (!isAlertEnabled("first-capture-bonus")) return;
        
        String amountStr = showRewardsAmount ? 
            Messages.get("discord.format.currency", Map.of("amount", String.format("%.2f", bonusAmount))) : 
            Messages.get("discord.value.hidden-amount");
        
        if (useEmbeds) {
            sendEmbed(Messages.get("discord.first-capture-bonus.title"), 
                Messages.get("discord.first-capture-bonus.description", Map.of(
                    "town", townName,
                    "zone", zoneName
                )),
                new Color(255, 140, 0), // Dark orange
                createField(Messages.get("discord.field.zone"), zoneName, true),
                createField(Messages.get("discord.field.town"), townName, true),
                showRewardsAmount ? createField(Messages.get("discord.field.bonus"), amountStr, true) : null
            );
        } else {
            sendPlainText(Messages.get("discord.first-capture-bonus.plain", Map.of(
                "town", townName,
                "amount", amountStr,
                "zone", zoneName
            )));
        }
    }
    
    /**
     * Send player death alert
     */
    public void sendPlayerDeath(String zoneId, String zoneName, String victim, String killer, String townName) {
        if (!isAlertEnabled("player-death") || isRateLimited(zoneId)) return;
        
        if (useEmbeds) {
            sendEmbed(Messages.get("discord.player-death.title"), 
                Messages.get("discord.player-death.description", Map.of(
                    "victim", victim,
                    "killer", killer,
                    "town", townName
                )),
                Color.DARK_GRAY,
                createField(Messages.get("discord.field.zone"), zoneName, true),
                createField(Messages.get("discord.field.victim"), victim, true),
                createField(Messages.get("discord.field.killer"), killer, true)
            );
        } else {
            sendPlainText(Messages.get("discord.player-death.plain", Map.of(
                "victim", victim,
                "killer", killer,
                "zone", zoneName
            )));
        }
    }
    
    /**
     * Send new record alert
     */
    public void sendNewRecord(String recordType, String holder, String value) {
        if (!isAlertEnabled("new-records")) return;
        
        if (useEmbeds) {
            sendEmbed(Messages.get("discord.records.new.title"), 
                Messages.get("discord.records.new.description", Map.of("record", recordType)),
                new Color(218, 165, 32), // Goldenrod
                createField(Messages.get("discord.field.record-type"), recordType, true),
                createField(Messages.get("discord.field.holder"), holder, true),
                createField(Messages.get("discord.field.value"), value, true)
            );
        } else {
            sendPlainText(Messages.get("discord.records.new.plain", Map.of(
                "holder", holder,
                "record", recordType,
                "value", value
            )));
        }
    }
    
    /**
     * Send milestone alert
     */
    public void sendMilestone(String entityName, String entityType, String milestone) {
        if (!isAlertEnabled("milestones")) return;
        
        if (useEmbeds) {
            sendEmbed(Messages.get("discord.milestone.title"), 
                Messages.get("discord.milestone.description", Map.of("entity", entityName)),
                Color.PINK,
                createField(entityType, entityName, true),
                createField(Messages.get("discord.field.milestone"), milestone, false)
            );
        } else {
            sendPlainText(Messages.get("discord.milestone.plain", Map.of(
                "entity", entityName,
                "milestone", milestone
            )));
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
                sendEmbed(Messages.get("discord.test.title"), 
                    Messages.get("discord.test.description"),
                    Color.GREEN,
                    createField(Messages.get("discord.field.status"), Messages.get("discord.value.connected"), true),
                    createField(Messages.get("discord.field.plugin-version"), plugin.getDescription().getVersion(), true)
                );
            } else {
                sendPlainText(Messages.get("discord.test.plain"));
            }
            return true;
        } catch (Exception e) {
            logger.severe("Webhook test failed: " + e.getMessage());
            return false;
        }
    }
}
