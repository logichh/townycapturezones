/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.Color
 *  org.bukkit.Location
 *  org.bukkit.Particle
 *  org.bukkit.Particle$DustOptions
 *  org.bukkit.command.Command
 *  org.bukkit.command.CommandExecutor
 *  org.bukkit.command.CommandSender
 *  org.bukkit.entity.Player
 *  org.bukkit.plugin.Plugin
 *  org.bukkit.scheduler.BukkitRunnable
 */
package com.logichh.townycapture;

import java.util.Arrays;
import java.util.Map;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

public class CaptureCommands implements CommandExecutor {
    private final TownyCapture plugin;

    public CaptureCommands(TownyCapture plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();
        Player player = sender instanceof Player ? (Player)sender : null;

        switch (subCommand) {
            case "help":
                sendHelp(sender);
                return true;
            case "admin":
                if (!sender.hasPermission("capturepoints.admin")) {
                    sender.sendMessage(plugin.colorize(plugin.getConfig().getString("messages.errors.no-permission", "&cYou don't have permission to use this command!")));
                    return true;
                }
                sendAdminHelp(sender);
                return true;
            case "list":
                if (player != null) {
                    sendCapturePointList(player);
                } else {
                    sender.sendMessage(plugin.colorize("&cThis command can only be used by players!"));
                }
                return true;
            case "info":
                if (player == null) {
                    sender.sendMessage(plugin.colorize("&cThis command can only be used by players!"));
                return true;
            }
                if (args.length < 2) {
                    player.sendMessage(plugin.colorize(plugin.getConfig().getString("messages.errors.usage-info", "&cUsage: /capturepoint info <point>")));
                    return true;
                }
                showPointInfo(player, args[1]);
                return true;
            case "capture":
                if (player == null) {
                    sender.sendMessage(plugin.colorize("&cThis command can only be used by players!"));
                return true;
            }
                startCapture(player);
                return true;
            case "notifications":
            case "silence":
                if (player == null) {
                    sender.sendMessage(plugin.colorize("&cThis command can only be used by players!"));
                return true;
            }
                boolean currentlyDisabled = plugin.disabledNotifications.getOrDefault(player.getUniqueId(), false);
                plugin.disabledNotifications.put(player.getUniqueId(), !currentlyDisabled);
                
                if (!currentlyDisabled) {
                    // Turn off all notifications
                    plugin.hideBossBarForPlayer(player);
                    player.sendMessage(plugin.colorize("&7All TownyCapture notifications disabled."));
                } else {
                    // Turn on all notifications
                plugin.showBossBarForPlayer(player);
                    player.sendMessage(plugin.colorize("&aAll TownyCapture notifications enabled."));
                }
                return true;
            case "create":
                if (!sender.hasPermission("capturepoints.admin.create")) {
                    sender.sendMessage(plugin.colorize(plugin.getConfig().getString("messages.errors.no-permission", "&cYou don't have permission to use this command!")));
                return true;
            }
                if (player == null) {
                    sender.sendMessage(plugin.colorize("&cThis command can only be used by players!"));
                    return true;
                }
                if (args.length < 5) {
                    player.sendMessage(plugin.colorize(plugin.getConfig().getString("messages.errors.usage-create", "&cUsage: /capturepoint create <id> <type> <radius> <reward>")));
                    return true;
                }
                handleCreateCommand(player, args);
                return true;
            case "stop":
                if (!sender.hasPermission("capturepoints.admin.stop")) {
                    sender.sendMessage(plugin.colorize(plugin.getConfig().getString("messages.errors.no-permission", "&cYou don't have permission to use this command!")));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(plugin.colorize(plugin.getConfig().getString("messages.errors.usage-stop", "&cUsage: /capturepoint stop <point>")));
                    return true;
                }
                handleStopCommand(sender, args);
                return true;
            case "deletezone":
                if (!sender.hasPermission("capturepoints.admin.delete")) {
                    sender.sendMessage(plugin.colorize(plugin.getConfig().getString("messages.errors.no-permission", "&cYou don't have permission to use this command!")));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(plugin.colorize(plugin.getConfig().getString("messages.errors.usage-deletezone", "&cUsage: /capturepoint deletezone <point>")));
                    return true;
                }
                deleteZone(sender, args[1]);
                return true;
            case "protectchunk":
                if (!sender.hasPermission("capturepoints.protectchunk")) {
                    sender.sendMessage(plugin.colorize(plugin.getConfig().getString("messages.errors.no-permission", "&cYou don't have permission to use this command!")));
                return true;
            }
                if (player == null) {
                    sender.sendMessage(plugin.colorize("&cThis command can only be used by players!"));
                    return true;
                }
                protectCurrentChunk(player);
                return true;
            case "settype":
                if (!sender.hasPermission("capturepoints.admin.settype")) {
                    sender.sendMessage(plugin.colorize(plugin.getConfig().getString("messages.errors.no-permission", "&cYou don't have permission to use this command!")));
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(plugin.colorize(plugin.getConfig().getString("messages.errors.usage-settype", "&cUsage: /capturepoint settype <point> <type>")));
                    return true;
                }
                handleSetType(sender, args[1], args[2]);
                return true;
            case "types":
                if (!sender.hasPermission("capturepoints.admin.settype")) {
                    sender.sendMessage(plugin.colorize(plugin.getConfig().getString("messages.errors.no-permission", "&cYou don't have permission to use this command!")));
                    return true;
                }
                showPointTypes(sender);
                return true;
            case "forcecapture":
                if (!sender.hasPermission("capturepoints.admin.forcecapture")) {
                    sender.sendMessage(plugin.colorize(plugin.getConfig().getString("messages.errors.no-permission", "&cYou don't have permission to use this command!")));
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(plugin.colorize(plugin.getConfig().getString("messages.errors.usage-forcecapture", "&cUsage: /capturepoint forcecapture <point> <town>")));
                    return true;
                }
                handleForceCapture(sender, args[1], args[2]);
                return true;
            case "reset":
                if (!sender.hasPermission("capturepoints.admin.reset")) {
                    sender.sendMessage(plugin.colorize(plugin.getConfig().getString("messages.errors.no-permission", "&cYou don't have permission to use this command!")));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(plugin.colorize(plugin.getConfig().getString("messages.errors.usage-reset", "&cUsage: /capturepoint reset <point>")));
                    return true;
                }
                handleReset(sender, args[1]);
                return true;
            case "resetall":
                if (!sender.hasPermission("capturepoints.admin.reset")) {
                    sender.sendMessage(plugin.colorize(plugin.getConfig().getString("messages.errors.no-permission", "&cYou don't have permission to use this command!")));
                    return true;
                }
                handleResetAll(sender);
                return true;
            case "showzone":
                if (player == null) {
                    sender.sendMessage(plugin.colorize("&cThis command can only be used by players!"));
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(plugin.colorize("&cUsage: /capturepoint showzone <point|all>"));
                    player.sendMessage(plugin.colorize("&7Use 'all' to toggle all zone boundaries"));
                    return true;
                }
                if (args[1].equalsIgnoreCase("all")) {
                    toggleAllZoneBoundaries(player);
                } else {
                    showZoneBoundaries(player, args[1]);
                }
                return true;
            case "reload":
                if (!sender.hasPermission("capturepoints.admin.reload")) {
                    sender.sendMessage(plugin.colorize(plugin.getConfig().getString("messages.errors.no-permission", "&cYou don't have permission to use this command!")));
                    return true;
                }
                plugin.reloadConfig();
                sender.sendMessage(plugin.colorize("&aConfiguration reloaded!"));
                return true;
            case "togglechat":
                if (!sender.hasPermission("capturepoints.admin.togglechat")) {
                    sender.sendMessage(plugin.colorize(plugin.getConfig().getString("messages.errors.no-permission", "&cYou don't have permission to use this command!")));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(plugin.colorize("&cUsage: /capturepoint togglechat <on/off>"));
                    return true;
                }
                boolean enable = args[1].equalsIgnoreCase("on");
                plugin.toggleChatMessages(enable);
                String status = enable ? "enabled" : "disabled";
                sender.sendMessage(plugin.colorize("&aCapture chat messages have been " + status));
                return true;
            case "test":
                if (!sender.hasPermission("capturepoints.admin")) {
                    sender.sendMessage(plugin.colorize("&cYou don't have permission to use this command!"));
                    return true;
                }
                if (player == null) {
                    sender.sendMessage(plugin.colorize("&cThis command can only be used by players!"));
                    return true;
                }
                runTests(player);
                return true;
            case "testall":
                if (!sender.hasPermission("capturepoints.admin")) {
                    sender.sendMessage(plugin.colorize("&cYou don't have permission to use this command!"));
                    return true;
                }
                if (player == null) {
                    sender.sendMessage(plugin.colorize("&cThis command can only be used by players!"));
                    return true;
                }
                runAllTests(player);
                return true;
            default:
                sendHelp(sender);
                return true;
            }
    }

    private void handleStopCommand(CommandSender sender, String[] args) {
        String pointId = args[1];
        String reason = args.length > 2 ? String.join(" ", Arrays.copyOfRange(args, 2, args.length)) : "No reason provided";
        if (plugin.stopCapture(pointId, reason)) {
            sender.sendMessage(plugin.colorize(plugin.getConfig().getString("messages.admin.capture_stopped", "&aCapture stopped successfully!")));
        } else {
            sender.sendMessage(plugin.colorize(plugin.getConfig().getString("messages.errors.capture_not_found", "&cNo active capture found for this point!")));
        }
    }

    private void deleteZone(CommandSender sender, String pointId) {
        if (plugin.deleteCapturePoint(pointId)) {
            sender.sendMessage(plugin.colorize(plugin.getConfig().getString("messages.admin.point_deleted", "&aCapture point deleted successfully!")));
        } else {
            sender.sendMessage(plugin.colorize(plugin.getConfig().getString("messages.errors.point_not_found", "&cCapture point not found!")));
        }
    }

    private void handleSetType(CommandSender sender, String pointId, String type) {
        if (plugin.setPointType(pointId, type)) {
            sender.sendMessage(plugin.colorize(plugin.getConfig().getString("messages.admin.type_changed", "&aPoint type changed successfully!")));
        } else {
            sender.sendMessage(plugin.colorize(plugin.getConfig().getString("messages.errors.point_not_found", "&cCapture point not found!")));
        }
    }

    private void showPointTypes(CommandSender sender) {
        Map<String, String> types = plugin.getPointTypes();
        if (types.isEmpty()) {
            sender.sendMessage(plugin.colorize("&cNo point types defined!"));
            return;
        }
        sender.sendMessage(plugin.colorize("&6&l=== Available Point Types ==="));
        for (Map.Entry<String, String> entry : types.entrySet()) {
            sender.sendMessage(plugin.colorize("&e" + entry.getKey() + " &7- " + entry.getValue()));
        }
    }

    private void handleForceCapture(CommandSender sender, String pointId, String townName) {
        if (plugin.forceCapture(pointId, townName)) {
            sender.sendMessage(plugin.colorize(plugin.getConfig().getString("messages.admin.force_capture_success", "&aPoint force captured successfully!")));
        } else {
            sender.sendMessage(plugin.colorize(plugin.getConfig().getString("messages.errors.point_not_found", "&cCapture point not found!")));
        }
    }

    private void handleReset(CommandSender sender, String pointId) {
        if (plugin.resetPoint(pointId)) {
            sender.sendMessage(plugin.colorize(plugin.getConfig().getString("messages.admin.point_reset", "&aCapture point reset successfully!")));
        } else {
            sender.sendMessage(plugin.colorize(plugin.getConfig().getString("messages.errors.point_not_found", "&cCapture point not found!")));
        }
    }

    private void handleResetAll(CommandSender sender) {
        int count = plugin.resetAllPoints();
        sender.sendMessage(plugin.colorize("&aAll capture points have been reset! " + count + " points processed."));
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(plugin.colorize("&6&l=== TownyCapture Help ==="));
        sender.sendMessage(plugin.colorize("&e/capturepoint help &7- Show this help message"));
        sender.sendMessage(plugin.colorize("&e/capturepoint list &7- List all capture points"));
        sender.sendMessage(plugin.colorize("&e/capturepoint info <point> &7- Show info about a capture point"));
        sender.sendMessage(plugin.colorize("&e/capturepoint capture &7- Start capturing a point"));
        sender.sendMessage(plugin.colorize("&e/capturepoint showzone <point|all> &7- Toggle zone boundary visualization"));
        sender.sendMessage(plugin.colorize("&e/capturepoint notifications &7- Toggle all plugin notifications (boss bars, sounds, messages)"));
        
        if (sender.hasPermission("capturepoints.admin")) {
            sender.sendMessage(plugin.colorize("&6&l=== Admin Commands ==="));
            sender.sendMessage(plugin.colorize("&e/capturepoint admin &7- Show admin commands"));
        }
    }

    private void sendAdminHelp(CommandSender sender) {
        sender.sendMessage(plugin.colorize("&6&l=== TownyCapture Admin Help ==="));
        sender.sendMessage(plugin.colorize("&e/capturepoint create <id> <type> <radius> <reward> &7- Create a new capture point"));
        sender.sendMessage(plugin.colorize("&e/capturepoint deletezone <point> &7- Delete a capture point"));
        sender.sendMessage(plugin.colorize("&e/capturepoint stop <point> &7- Stop an active capture"));
        sender.sendMessage(plugin.colorize("&e/capturepoint showzone <point|all> &7- Toggle zone boundary visualization"));
        sender.sendMessage(plugin.colorize("&e/capturepoint settype <point> <type> &7- Set point type"));
        sender.sendMessage(plugin.colorize("&e/capturepoint types &7- List available point types"));
        sender.sendMessage(plugin.colorize("&e/capturepoint forcecapture <point> <town> &7- Force capture a point"));
        sender.sendMessage(plugin.colorize("&e/capturepoint reset <point> &7- Reset a capture point"));
        sender.sendMessage(plugin.colorize("&e/capturepoint resetall &7- Reset all capture points (keeps owners)"));
        sender.sendMessage(plugin.colorize("&e/capturepoint reload &7- Reload configuration"));
        sender.sendMessage(plugin.colorize("&e/capturepoint test &7- Create test zones for manual testing"));
        sender.sendMessage(plugin.colorize("&e/capturepoint testall &7- Run automated comprehensive tests"));
    }

    private void sendCapturePointList(Player player) {
        Map<String, CapturePoint> points = this.plugin.getCapturePoints();
        if (points.isEmpty()) {
            player.sendMessage(this.plugin.colorize("&c&l\u2716 &7No capture points found!"));
            return;
        }
        player.sendMessage(this.plugin.colorize("&6&l\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac &e&lCapture Points &6&l\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac"));
        for (CapturePoint point : points.values()) {
            Object status;
            String controllingTown = point.getControllingTown();
            Object object = status = controllingTown.isEmpty() ? "&c&lUNCLAIMED" : "&a&lCONTROLLED &7by &f" + controllingTown;
            if (this.plugin.getActiveSessions().containsKey(point.getId())) {
                CaptureSession session = this.plugin.getActiveSessions().get(point.getId());
                status = "&6&lCAPTURING &7by &f" + session.getTownName();
            }
            player.sendMessage(this.plugin.colorize("&e\u27a4 &f" + point.getName() + " &7- " + (String)status));
        }
        player.sendMessage(this.plugin.colorize("&6&l\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac &e&lStatistics &6&l\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac"));
        player.sendMessage(this.plugin.colorize("&e\u27a4 &7Total Points: &f" + points.size()));
        player.sendMessage(this.plugin.colorize("&e\u27a4 &7Active Captures: &f" + this.plugin.getActiveSessions().size()));
        player.sendMessage(this.plugin.colorize("&6&l\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac"));
    }

    private void showPointInfo(Player player, String pointId) {
        CapturePoint point = this.plugin.getCapturePoints().get(pointId);
        if (point == null) {
            player.sendMessage(this.plugin.colorize("&cCapture point not found!"));
            return;
        }
        player.sendMessage(this.plugin.colorize("&6===== " + point.getName() + " ====="));
        player.sendMessage(this.plugin.colorize("&eID: &7" + point.getId()));
        player.sendMessage(this.plugin.colorize("&eType: &7" + point.getType()));
        player.sendMessage(this.plugin.colorize("&eRadius: &7" + point.getChunkRadius() + " chunks"));
        player.sendMessage(this.plugin.colorize("&eReward: &7" + point.getReward()));
        player.sendMessage(this.plugin.colorize("&eControlling Town: &7" + (point.getControllingTown().isEmpty() ? "None" : point.getControllingTown())));
        if (this.plugin.getActiveSessions().containsKey(pointId)) {
            CaptureSession session = this.plugin.getActiveSessions().get(pointId);
            player.sendMessage(this.plugin.colorize("&eBeing captured by: &7" + session.getTownName()));
        }
    }

    private void startCapture(Player player) {
        CapturePoint targetPoint = null;
        for (CapturePoint point : this.plugin.getCapturePoints().values()) {
            if (!this.plugin.isWithinChunkRadius(point.getLocation(), player.getLocation(), point.getChunkRadius())) continue;
            targetPoint = point;
            break;
        }
        if (targetPoint == null) {
            player.sendMessage(this.plugin.colorize(this.plugin.getConfig().getString("messages.errors.not-in-any-zone", "&cYou are not in any capture zone!")));
            return;
        }
        int minPlayers = this.plugin.getConfig().getInt("settings.min-online-players", 5);
        if (Bukkit.getOnlinePlayers().size() < minPlayers) {
            String message = this.plugin.getConfig().getString("messages.errors.not-enough-players", "&cAt least %minplayers% players must be online to start a capture!")
                    .replace("%minplayers%", String.valueOf(minPlayers));
            player.sendMessage(this.plugin.colorize(message));
            return;
        }
        boolean success = this.plugin.startCapture(player, targetPoint.getId());
        if (success) {
            player.sendMessage(this.plugin.colorize(this.plugin.getConfig().getString("messages.capture.start-success", "&aAttempting to capture " + targetPoint.getName() + "!")));
        } else {
            player.sendMessage(this.plugin.colorize(this.plugin.getConfig().getString("messages.errors.capture-failed", "&cFailed to start capture!")));
        }
    }

    private void protectCurrentChunk(Player player) {
        Location playerLoc = player.getLocation();
        int chunkX = playerLoc.getBlockX() >> 4;
        int chunkZ = playerLoc.getBlockZ() >> 4;
        String id = "protected_chunk_" + chunkX + "_" + chunkZ;
        if (this.plugin.getCapturePoints().containsKey(id)) {
            player.sendMessage(this.plugin.colorize("&cThis chunk is already protected!"));
            return;
        }
        String name = "Protected Chunk at " + chunkX + "," + chunkZ;
        Location chunkCenter = new Location(playerLoc.getWorld(), (double)((chunkX << 4) + 8), playerLoc.getY(), (double)((chunkZ << 4) + 8));
        this.plugin.createCapturePoint(id, name, chunkCenter, 0, 0.0);
        player.sendMessage(this.plugin.colorize("&aChunk protected! This chunk now has the same protection as a capture zone."));
        player.sendMessage(this.plugin.colorize("&aChunk coordinates: " + chunkX + "," + chunkZ));
        this.showChunkBoundaries(player, chunkX, chunkZ);
    }

    private void showChunkBoundaries(final Player player, int chunkX, int chunkZ) {
        final int minX = chunkX << 4;
        final int minZ = chunkZ << 4;
        final int maxX = minX + 15;
        final int maxZ = minZ + 15;
        final int y = player.getLocation().getBlockY();
        new BukkitRunnable(){
            int count = 0;

            public void run() {
                if (this.count >= 200) {
                    this.cancel();
                    return;
                }
                for (int x = minX; x <= maxX; ++x) {
                    for (int z = minZ; z <= maxZ; ++z) {
                        if (x != minX && x != maxX && z != minZ && z != maxZ) continue;
                        Location particleLoc = new Location(player.getWorld(), (double)x + 0.5, (double)(y + 1), (double)z + 0.5);
                        player.getWorld().spawnParticle(Particle.REDSTONE, particleLoc, 1, (Object)new Particle.DustOptions(Color.RED, 1.0f));
                    }
                }
                ++this.count;
            }
        }.runTaskTimer((Plugin)this.plugin, 0L, 1L);
    }

    private void handleCreateCommand(Player player, String[] args) {
        String id = args[1];
        String type = args[2];
        String radiusStr = args[3];
        String rewardStr = args[4];
        
        if (id.isEmpty() || id.length() > 32) {
            player.sendMessage(plugin.colorize(plugin.getConfig().getString("messages.errors.invalid-id", "&cInvalid ID! Must be between 1 and 32 characters.")));
            return;
        }
        
        if (type.isEmpty() || type.length() > 64) {
            player.sendMessage(plugin.colorize(plugin.getConfig().getString("messages.errors.invalid-type", "&cInvalid type! Must be between 1 and 64 characters.")));
            return;
        }
        
        try {
            int chunkRadius = Integer.parseInt(radiusStr);
            double reward = Double.parseDouble(rewardStr);
            
            if (chunkRadius <= 0) {
                player.sendMessage(plugin.colorize(plugin.getConfig().getString("messages.errors.invalid-radius", "&cRadius must be positive!")));
                return;
            }
            
            if (reward < 0.0) {
                player.sendMessage(plugin.colorize(plugin.getConfig().getString("messages.errors.invalid-reward", "&cReward cannot be negative!")));
                return;
            }
            
            if (plugin.getCapturePoints().containsKey(id)) {
                player.sendMessage(plugin.colorize(plugin.getConfig().getString("messages.errors.id-exists", "&cA capture point with this ID already exists!")));
                return;
            }
            
            plugin.createCapturePoint(id, type, player.getLocation(), chunkRadius, reward);
            player.sendMessage(plugin.colorize(plugin.getConfig().getString("messages.create.success", "&aCapture point created successfully!")));
            player.sendMessage(plugin.colorize("&aRadius: " + chunkRadius + " chunks (" + chunkRadius * 16 + " blocks)"));
        } catch (NumberFormatException e) {
            player.sendMessage(plugin.colorize(plugin.getConfig().getString("messages.errors.invalid-number", "&cRadius and reward must be numbers!")));
        }
    }

    private void showZoneBoundaries(Player player, String pointId) {
        CapturePoint point = this.plugin.getCapturePoints().get(pointId);
        if (point == null) {
            player.sendMessage(this.plugin.colorize("&cCapture point not found!"));
            return;
        }

        // Check if already showing for this player
        String key = player.getUniqueId() + "_" + pointId;
        if (this.plugin.boundaryTasks.containsKey(key)) {
            // Toggle off
            this.plugin.boundaryTasks.get(key).cancel();
            this.plugin.boundaryTasks.remove(key);
            player.sendMessage(this.plugin.colorize("&7Zone boundary display hidden for " + point.getName()));
            return;
        }

        // Toggle on
        player.sendMessage(this.plugin.colorize("&eShowing boundaries for " + point.getName() + " (circular zone)"));
        player.sendMessage(this.plugin.colorize("&7Type /capturepoint showzone " + pointId + " again to hide"));

        Location center = point.getLocation();
        int blockRadius = point.getChunkRadius() * 16;
        int sides = Math.max(256, blockRadius * 4); // Dense particles with minimal gaps

        final TownyCapture pluginRef = this.plugin;
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    this.cancel();
                    pluginRef.boundaryTasks.remove(key);
                    return;
                }

                // Get world's surface height for each position
                World world = center.getWorld();
                
                // Draw circular boundary with vertical columns of particles
                // Update every 10 ticks to be less intensive
                for (int i = 0; i < sides; i++) {
                    double angle = 2.0 * Math.PI * i / sides;
                    double x = center.getX() + blockRadius * Math.cos(angle);
                    double z = center.getZ() + blockRadius * Math.sin(angle);
                    
                    // Get surface height at this position
                    int surfaceY = world.getHighestBlockYAt((int)Math.floor(x), (int)Math.floor(z));
                    
                    // Spawn particles from below ground up 40 blocks, going through any blocks
                    for (int h = -10; h <= 40; h++) {
                        int yPos = surfaceY + h;
                        // Only spawn if within reasonable world bounds
                        if (yPos > 0 && yPos < 320) {
                            Location particleLoc = new Location(world, x, yPos, z);
                            // Try GLOW particle first for maximum visibility, fallback to SOUL if not available
                            try {
                                player.getWorld().spawnParticle(Particle.GLOW, particleLoc, 1);
                            } catch (Exception e) {
                                // Fallback to SOUL particle which glows bright cyan
                                player.getWorld().spawnParticle(Particle.SOUL, particleLoc, 1);
                            }
                        }
                    }
                }
            }
        }.runTaskTimer((Plugin)pluginRef, 0L, 10L);
        
        this.plugin.boundaryTasks.put(key, task);
    }
    
    private void toggleAllZoneBoundaries(Player player) {
        int hiddenCount = 0;
        int shownCount = 0;
        
        for (CapturePoint point : this.plugin.getCapturePoints().values()) {
            String key = player.getUniqueId() + "_" + point.getId();
            if (this.plugin.boundaryTasks.containsKey(key)) {
                // Toggle off
                this.plugin.boundaryTasks.get(key).cancel();
                this.plugin.boundaryTasks.remove(key);
                hiddenCount++;
            } else {
                // Show zone
                showZoneBoundaries(player, point.getId());
                shownCount++;
            }
        }
        
        if (hiddenCount > 0 && shownCount > 0) {
            player.sendMessage(plugin.colorize("&7Toggled " + hiddenCount + " zones off, " + shownCount + " zones on"));
        } else if (hiddenCount > 0) {
            player.sendMessage(plugin.colorize("&cAll zone boundaries hidden"));
        } else if (shownCount > 0) {
            player.sendMessage(plugin.colorize("&aAll zone boundaries visible"));
        } else {
            player.sendMessage(plugin.colorize("&cNo capture points found"));
        }
    }
    
    private void runTests(Player player) {
        player.sendMessage(plugin.colorize("&6&l=== Running Basic Tests ==="));
        player.sendMessage(plugin.colorize("&7Creating test zones..."));
        
        // Test 1: Create test zones
        Location loc1 = player.getLocation().clone().add(20, 0, 0);
        Location loc2 = player.getLocation().clone().add(-20, 0, 0);
        Location loc3 = player.getLocation().clone().add(0, 0, 20);
        
        plugin.createCapturePoint("test_zone_1", "Test Zone 1", loc1, 3, 1000.0);
        plugin.createCapturePoint("test_zone_2", "Test Zone 2", loc2, 5, 2000.0);
        plugin.createCapturePoint("test_zone_3", "Test Zone 3", loc3, 2, 500.0);
        
        player.sendMessage(plugin.colorize("&a[OK] Created 3 test zones"));
        player.sendMessage(plugin.colorize("&7You can now test manually by running /capturepoint capture"));
        player.sendMessage(plugin.colorize("&7Or run &e/capturepoint testall &7for automated testing"));
    }
    
    private void runAllTests(Player player) {
        player.sendMessage(plugin.colorize("&6&l========== COMPREHENSIVE CAPTURE TESTS =========="));
        
        final int[] testsPassed = {0};
        final int[] testsTotal = {0};
        
        // Test 1-3: Basic Zone Creation
        player.sendMessage(plugin.colorize("&7[Test 1-3] Creating multiple test zones..."));
        Location loc1 = player.getLocation().clone().add(50, 0, 50);
        Location loc2 = player.getLocation().clone().add(-50, 0, 50);
        Location loc3 = player.getLocation().clone().add(50, 0, -50);
        
        plugin.createCapturePoint("test_zone_1", "Test Zone 1", loc1, 5, 1000.0);
        plugin.createCapturePoint("test_zone_2", "Test Zone 2", loc2, 5, 2000.0);
        plugin.createCapturePoint("test_zone_3", "Test Zone 3", loc3, 5, 1500.0);
        
        for (int i = 1; i <= 3; i++) {
            testsTotal[0]++;
            if (plugin.getCapturePoints().containsKey("test_zone_" + i)) {
                player.sendMessage(plugin.colorize("&a[PASS] Test zone " + i + " created"));
                testsPassed[0]++;
        } else {
                player.sendMessage(plugin.colorize("&c[FAIL] Test zone " + i + " creation failed"));
            }
        }
        
        // Test 4: Normal Capture
        player.sendMessage(plugin.colorize("&7[Test 4] Testing normal capture (5 seconds)..."));
        testsTotal[0]++;
        Bukkit.getScheduler().runTaskLater((Plugin)plugin, () -> {
            boolean started = plugin.startTestCapture(player, "test_zone_1", 1, 5);
            if (started && plugin.getActiveSession("test_zone_1") != null) {
                player.sendMessage(plugin.colorize("&a[PASS] Normal capture started"));
                testsPassed[0]++;
                
                // Test 5: Attempt capture while already being captured
                testsTotal[0]++;
                Bukkit.getScheduler().runTaskLater((Plugin)plugin, () -> {
                    // Try to start another capture (should fail or be prevented)
                    boolean duplicateStarted = plugin.startTestCapture(player, "test_zone_1", 1, 5);
                    if (!duplicateStarted || (plugin.getActiveSessions().size() == 1)) {
                        player.sendMessage(plugin.colorize("&a[PASS] Duplicate capture prevented"));
                        testsPassed[0]++;
                    } else {
                        player.sendMessage(plugin.colorize("&c[FAIL] Duplicate capture allowed"));
                    }
                    
                    // Wait for capture 1 to complete
                    Bukkit.getScheduler().runTaskLater((Plugin)plugin, () -> {
                        // Test 6: Verify capture completion
                        testsTotal[0]++;
                        CapturePoint point = plugin.getCapturePoint("test_zone_1");
                        if (point != null && !point.getControllingTown().isEmpty()) {
                            player.sendMessage(plugin.colorize("&a[PASS] Capture completed"));
                            testsPassed[0]++;
        } else {
                            player.sendMessage(plugin.colorize("&c[FAIL] Capture did not complete"));
                        }
                        
                        // Test 7: Attempt to capture already-captured zone
                        testsTotal[0]++;
                        boolean alreadyCapturedStarted = plugin.startTestCapture(player, "test_zone_1", 1, 5);
                        if (!alreadyCapturedStarted || plugin.getActiveSession("test_zone_1") == null) {
                            player.sendMessage(plugin.colorize("&a[PASS] Cannot capture already-controlled zone"));
                            testsPassed[0]++;
        } else {
                            player.sendMessage(plugin.colorize("&c[FAIL] Allowed capture of controlled zone"));
                        }
                        
                        // Test 8: Admin reset zone
                        testsTotal[0]++;
                        if (plugin.resetPoint("test_zone_1")) {
                            player.sendMessage(plugin.colorize("&a[PASS] Zone reset successful"));
                            testsPassed[0]++;
        } else {
                            player.sendMessage(plugin.colorize("&c[FAIL] Zone reset failed"));
                        }
                        
                        // Test 9: Capture after reset
                        testsTotal[0]++;
                        player.sendMessage(plugin.colorize("&7[Test 9] Capturing reset zone..."));
                        Bukkit.getScheduler().runTaskLater((Plugin)plugin, () -> {
                            boolean resetCaptureStarted = plugin.startTestCapture(player, "test_zone_1", 1, 5);
                            if (resetCaptureStarted) {
                                player.sendMessage(plugin.colorize("&a[PASS] Capture after reset works"));
                                testsPassed[0]++;
        } else {
                                player.sendMessage(plugin.colorize("&c[FAIL] Cannot capture after reset"));
                            }
                            
                            // Wait for second capture to complete
                            Bukkit.getScheduler().runTaskLater((Plugin)plugin, () -> {
                                // Test 10: Admin stop capture
                                testsTotal[0]++;
                                if (plugin.stopCapture("test_zone_1", "Test cancellation")) {
                                    player.sendMessage(plugin.colorize("&a[PASS] Admin stop capture works"));
                                    testsPassed[0]++;
        } else {
                                    player.sendMessage(plugin.colorize("&c[FAIL] Admin stop failed"));
                                }
                                
                                // Test 11: Force capture
                                testsTotal[0]++;
                                player.sendMessage(plugin.colorize("&7[Test 11] Testing force capture..."));
                                plugin.resetPoint("test_zone_1");
            Bukkit.getScheduler().runTaskLater((Plugin)plugin, () -> {
                                    // Get player's town for force capture test
                                    Town playerTown = TownyAPI.getInstance().getTown(player);
                                    if (playerTown != null) {
                                        boolean forceResult = plugin.forceCapture("test_zone_1", playerTown.getName());
                                        if (forceResult) {
                                            player.sendMessage(plugin.colorize("&a[PASS] Force capture works"));
                                            testsPassed[0]++;
                                        } else {
                                            player.sendMessage(plugin.colorize("&c[FAIL] Force capture failed"));
                                        }
                                    } else {
                                        player.sendMessage(plugin.colorize("&7[SKIP] Force capture - no town"));
                                        testsPassed[0]++;
                                    }
                                    
                                    // Test 12: Multiple concurrent captures
                                    testsTotal[0]++;
                                    player.sendMessage(plugin.colorize("&7[Test 12] Testing multiple concurrent captures..."));
                                    plugin.resetPoint("test_zone_2");
                                    Bukkit.getScheduler().runTaskLater((Plugin)plugin, () -> {
                                        boolean concurrent2 = plugin.startTestCapture(player, "test_zone_2", 1, 5);
                                        boolean concurrent3 = plugin.startTestCapture(player, "test_zone_3", 1, 5);
                                        if (concurrent2 && concurrent3) {
                                            player.sendMessage(plugin.colorize("&a[PASS] Multiple concurrent captures work"));
                                            testsPassed[0]++;
                                        } else {
                                            player.sendMessage(plugin.colorize("&c[FAIL] Multiple captures failed"));
                                        }
                                        
                                        // Wait for concurrent captures
                                        Bukkit.getScheduler().runTaskLater((Plugin)plugin, () -> {
                                            // Test 13-14: Boundary cleanup on deletion
                                            testsTotal[0]++;
                                            if (plugin.deleteCapturePoint("test_zone_1")) {
                                                player.sendMessage(plugin.colorize("&a[PASS] Zone 1 deleted with boundary cleanup"));
                                                testsPassed[0]++;
                                            } else {
                                                player.sendMessage(plugin.colorize("&c[FAIL] Zone 1 deletion failed"));
                                            }
                                            
                                            testsTotal[0]++;
                                            if (plugin.deleteCapturePoint("test_zone_2") && plugin.deleteCapturePoint("test_zone_3")) {
                                                player.sendMessage(plugin.colorize("&a[PASS] All zones cleaned up"));
                                                testsPassed[0]++;
        } else {
                                                player.sendMessage(plugin.colorize("&c[FAIL] Zone cleanup failed"));
                                            }
                                            
                                            // Final results
                                            player.sendMessage(plugin.colorize("&6&l========== FINAL TEST RESULTS =========="));
                                            player.sendMessage(plugin.colorize("&eTests Passed: &a" + testsPassed[0] + "&7/&f" + testsTotal[0]));
                                            
                                            if (testsPassed[0] == testsTotal[0]) {
                                                player.sendMessage(plugin.colorize("&a&l[SUCCESS] ALL TESTS PASSED!"));
                                            } else {
                                                player.sendMessage(plugin.colorize("&c&l[ERROR] Some tests failed: &7" + testsTotal[0] + " - " + testsPassed[0]));
                                            }
                                        }, 120L);
                                    }, 20L);
                                }, 20L);
                            }, 120L);
                        }, 120L);
                    }, 120L);
                }, 20L);
        } else {
                player.sendMessage(plugin.colorize("&c[FAIL] Normal capture failed"));
        }
        }, 20L);
    }
}
