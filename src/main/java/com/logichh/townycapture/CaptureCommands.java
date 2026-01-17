package com.logichh.townycapture;

import java.util.Arrays;
import java.util.HashMap;
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
                    sender.sendMessage(Messages.get("errors.no-permission"));
                    return true;
                }
                sendAdminHelp(sender);
                return true;
            case "list":
                if (player != null) {
                    sendCapturePointList(player);
                } else {
                    sender.sendMessage(Messages.get("errors.player-only"));
                }
                return true;
            case "info":
                if (player == null) {
                    sender.sendMessage(Messages.get("errors.player-only"));
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(Messages.get("errors.usage-info"));
                    return true;
                }
                showPointInfo(player, args[1]);
                return true;
            case "capture":
                if (player == null) {
                    sender.sendMessage(Messages.get("errors.player-only"));
                    return true;
                }
                startCapture(player);
                return true;
            case "notifications":
            case "silence":
                if (player == null) {
                    sender.sendMessage(Messages.get("errors.player-only"));
                    return true;
                }
                boolean currentlyDisabled = plugin.disabledNotifications.getOrDefault(player.getUniqueId(), false);
                plugin.disabledNotifications.put(player.getUniqueId(), !currentlyDisabled);
                
                if (!currentlyDisabled) {
                    // Turn off all notifications
                    plugin.hideBossBarForPlayer(player);
                    player.sendMessage(Messages.get("messages.notifications-disabled"));
                } else {
                    // Turn on all notifications
                plugin.showBossBarForPlayer(player);
                    player.sendMessage(Messages.get("messages.notifications-enabled"));
                }
                return true;
            case "create":
                if (!sender.hasPermission("capturepoints.admin.create")) {
                    sender.sendMessage(Messages.get("errors.no-permission"));
                    return true;
                }
                if (player == null) {
                    sender.sendMessage(Messages.get("errors.player-only"));
                    return true;
                }
                if (args.length < 5) {
                    player.sendMessage(Messages.get("errors.usage-create"));
                    return true;
                }
                handleCreateCommand(player, args);
                return true;
            case "stop":
                if (!sender.hasPermission("capturepoints.admin.stop")) {
                    sender.sendMessage(Messages.get("errors.no-permission"));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(Messages.get("errors.usage-stop"));
                    return true;
                }
                handleStopCommand(sender, args);
                return true;
            case "deletezone":
                if (!sender.hasPermission("capturepoints.admin.delete")) {
                    sender.sendMessage(Messages.get("errors.no-permission"));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(Messages.get("errors.usage-deletezone"));
                    return true;
                }
                deleteZone(sender, args);
                return true;
            case "protectchunk":
                if (!sender.hasPermission("capturepoints.protectchunk")) {
                    sender.sendMessage(Messages.get("errors.no-permission"));
                    return true;
                }
                if (player == null) {
                    sender.sendMessage(Messages.get("errors.player-only"));
                    return true;
                }
                protectCurrentChunk(player);
                return true;
            case "settype":
                if (!sender.hasPermission("capturepoints.admin.settype")) {
                    sender.sendMessage(Messages.get("errors.no-permission"));
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(Messages.get("errors.usage-settype"));
                    return true;
                }
                handleSetType(sender, args[1], args[2]);
                return true;
            case "types":
                if (!sender.hasPermission("capturepoints.admin.settype")) {
                    sender.sendMessage(Messages.get("errors.no-permission"));
                    return true;
                }
                showPointTypes(sender);
                return true;
            case "forcecapture":
                if (!sender.hasPermission("capturepoints.admin.forcecapture")) {
                    sender.sendMessage(Messages.get("errors.no-permission"));
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(Messages.get("errors.usage-forcecapture"));
                    return true;
                }
                handleForceCapture(sender, args[1], args[2]);
                return true;
            case "reset":
                if (!sender.hasPermission("capturepoints.admin.reset")) {
                    sender.sendMessage(Messages.get("errors.no-permission"));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(Messages.get("errors.usage-reset"));
                    return true;
                }
                handleReset(sender, args[1]);
                return true;
            case "resetall":
                if (!sender.hasPermission("capturepoints.admin.reset")) {
                    sender.sendMessage(Messages.get("errors.no-permission"));
                    return true;
                }
                handleResetAll(sender);
                return true;
            case "showzone":
                if (player == null) {
                    sender.sendMessage(Messages.get("errors.player-only"));
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(Messages.get("errors.usage-showzone"));
                    player.sendMessage(Messages.get("messages.zone.toggle-hint"));
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
                    sender.sendMessage(Messages.get("errors.no-permission"));
                    return true;
                }
                plugin.reloadAll();
                sender.sendMessage(Messages.get("messages.reload-success"));
                return true;
            case "reloadlang":
                if (!sender.hasPermission("capturepoints.admin.reload")) {
                    sender.sendMessage(Messages.get("errors.no-permission"));
                    return true;
                }
                plugin.reloadLang();
                sender.sendMessage(Messages.get("messages.reloadlang-success"));
                return true;
            case "togglechat":
                if (!sender.hasPermission("capturepoints.admin.togglechat")) {
                    sender.sendMessage(Messages.get("errors.no-permission"));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(Messages.get("errors.usage-togglechat"));
                    return true;
                }
                boolean enable = args[1].equalsIgnoreCase("on");
                plugin.toggleChatMessages(enable);
                String status = enable ? "enabled" : "disabled";
                sender.sendMessage(Messages.get("messages.chat-status", Map.of("status", status)));
                return true;
            case "test":
                if (!sender.hasPermission("capturepoints.admin")) {
                    sender.sendMessage(Messages.get("errors.no-permission"));
                    return true;
                }
                if (player == null) {
                    sender.sendMessage(Messages.get("errors.player-only"));
                    return true;
                }
                runTests(player);
                return true;
            case "testall":
                if (!sender.hasPermission("capturepoints.admin")) {
                    sender.sendMessage(Messages.get("errors.no-permission"));
                    return true;
                }
                if (player == null) {
                    sender.sendMessage(Messages.get("errors.player-only"));
                    return true;
                }
                runAllTests(player);
                return true;
            case "stats":
                if (args.length == 1) {
                    // Open stats GUI
                    if (player == null) {
                        sender.sendMessage(Messages.get("errors.player-only"));
                        return true;
                    }
                    handleStatsCommand(player);
                } else {
                    // Admin stats commands
                    if (!sender.hasPermission("capturepoints.admin.stats")) {
                        sender.sendMessage(Messages.get("errors.no-permission"));
                        return true;
                    }
                    handleAdminStatsCommand(sender, args);
                }
                return true;
            case "zoneconfig":
                if (!sender.hasPermission("capturepoints.admin.zoneconfig")) {
                    sender.sendMessage(Messages.get("errors.no-permission"));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(Messages.get("errors.usage-zoneconfig"));
                    return true;
                }
                handleZoneConfigCommand(sender, args);
                return true;
            case "shop":
                if (player == null) {
                    sender.sendMessage(Messages.get("errors.player-only"));
                    return true;
                }
                handleShopCommand(player, args);
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
            sender.sendMessage(Messages.get("admin.capture-stopped"));
        } else {
            sender.sendMessage(Messages.get("errors.capture-not-found"));
        }
    }

    private void deleteZone(CommandSender sender, String[] args) {
        String pointId = args[1];
        boolean confirmed = args.length > 2 && "CONFIRM".equalsIgnoreCase(args[2]);
        if (!confirmed) {
            sender.sendMessage(Messages.get("messages.deletezone.confirm", Map.of("zone", pointId)));
            return;
        }

        if (plugin.deleteCapturePoint(pointId)) {
            sender.sendMessage(Messages.get("admin.point-deleted"));
            
            // Send Discord webhook notification for zone deletion
            if (plugin.getDiscordWebhook() != null) {
                String deletedBy = (sender instanceof Player) ? ((Player) sender).getName() : "Console";
                plugin.getDiscordWebhook().sendZoneDeleted(pointId, pointId, deletedBy);
            }
        } else {
            sender.sendMessage(Messages.get("messages.errors.point_not_found"));
        }
    }

    private void handleSetType(CommandSender sender, String pointId, String type) {
        if (plugin.setPointType(pointId, type)) {
            sender.sendMessage(Messages.get("admin.type-changed"));
        } else {
            sender.sendMessage(Messages.get("messages.errors.point_not_found"));
        }
    }

    private void showPointTypes(CommandSender sender) {
        Map<String, String> types = plugin.getPointTypes();
        if (types.isEmpty()) {
            sender.sendMessage(Messages.get("errors.no-point-types"));
            return;
        }
        sender.sendMessage(Messages.get("help.point-types-header"));
        for (Map.Entry<String, String> entry : types.entrySet()) {
            sender.sendMessage(Messages.get("help.point-types-format", 
                Map.of("type", entry.getKey(), "description", entry.getValue())));
        }
    }

    private void handleForceCapture(CommandSender sender, String pointId, String townName) {
        if (plugin.forceCapture(pointId, townName)) {
            sender.sendMessage(Messages.get("admin.force-capture-success"));
        } else {
            sender.sendMessage(Messages.get("messages.errors.point_not_found"));
        }
    }

    private void handleReset(CommandSender sender, String pointId) {
        if (plugin.resetPoint(pointId)) {
            sender.sendMessage(Messages.get("admin.point-reset"));
        } else {
            sender.sendMessage(Messages.get("messages.errors.point_not_found"));
        }
    }

    private void handleResetAll(CommandSender sender) {
        int count = plugin.resetAllPoints();
        sender.sendMessage(Messages.get("messages.reset-all", Map.of("count", String.valueOf(count))));
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Messages.get("help.main.header"));
        sender.sendMessage(Messages.get("help.main.help"));
        sender.sendMessage(Messages.get("help.main.list"));
        sender.sendMessage(Messages.get("help.main.info"));
        sender.sendMessage(Messages.get("help.main.capture"));
        sender.sendMessage(Messages.get("help.main.showzone"));
        sender.sendMessage(Messages.get("help.main.notifications"));
        
        if (sender.hasPermission("capturepoints.admin")) {
            sender.sendMessage(Messages.get("help.main.admin-section"));
            sender.sendMessage(Messages.get("help.main.admin"));
        }
    }

    private void sendAdminHelp(CommandSender sender) {
        sender.sendMessage(Messages.get("help.admin.header"));
        sender.sendMessage(Messages.get("help.admin.create"));
        sender.sendMessage(Messages.get("help.admin.deletezone"));
        sender.sendMessage(Messages.get("help.admin.stop"));
        sender.sendMessage(Messages.get("help.admin.showzone"));
        sender.sendMessage(Messages.get("help.admin.settype"));
        sender.sendMessage(Messages.get("help.admin.types"));
        sender.sendMessage(Messages.get("help.admin.forcecapture"));
        sender.sendMessage(Messages.get("help.admin.reset"));
        sender.sendMessage(Messages.get("help.admin.resetall"));
        sender.sendMessage(Messages.get("help.admin.reload"));
        sender.sendMessage(Messages.get("help.admin.test"));
        sender.sendMessage(Messages.get("help.admin.testall"));
    }

    private void sendCapturePointList(Player player) {
        Map<String, CapturePoint> points = this.plugin.getCapturePoints();
        if (points.isEmpty()) {
            player.sendMessage(Messages.get("messages.zone.no-points"));
            return;
        }
        player.sendMessage(Messages.get("messages.zone.header"));
        for (CapturePoint point : points.values()) {
            String status;
            String controllingTown = point.getControllingTown();
            if (controllingTown.isEmpty()) {
                status = "&c&lUNCLAIMED";
            } else {
                status = "&a&lCONTROLLED &7by &f" + controllingTown;
            }
            if (this.plugin.getActiveSessions().containsKey(point.getId())) {
                CaptureSession session = this.plugin.getActiveSessions().get(point.getId());
                status = "&6&lCAPTURING &7by &f" + session.getTownName();
            }
            player.sendMessage(Messages.get("messages.zone.point-entry", 
                Map.of("name", point.getName(), "status", status)));
        }
        player.sendMessage(Messages.get("messages.zone.stats-header"));
        player.sendMessage(Messages.get("messages.zone.total-points", Map.of("count", String.valueOf(points.size()))));
        player.sendMessage(Messages.get("messages.zone.active-captures", Map.of("count", String.valueOf(this.plugin.getActiveSessions().size()))));
        player.sendMessage(Messages.get("messages.zone.footer"));
    }

    private void showPointInfo(Player player, String pointId) {
        CapturePoint point = this.plugin.getCapturePoints().get(pointId);
        if (point == null) {
            player.sendMessage(Messages.get("messages.info.point-not-found"));
            return;
        }
        player.sendMessage(Messages.get("messages.info.point-header", Map.of("point", point.getName())));
        player.sendMessage(Messages.get("messages.info.point-id", Map.of("id", point.getId())));
        player.sendMessage(Messages.get("messages.info.point-type", Map.of("type", point.getType())));
        player.sendMessage(Messages.get("messages.info.point-radius", Map.of("radius", String.valueOf(point.getChunkRadius()))));
        player.sendMessage(Messages.get("messages.info.point-reward", Map.of("reward", String.valueOf(plugin.getBaseReward(point)))));
        player.sendMessage(Messages.get("messages.info.controlling-town", Map.of("town", point.getControllingTown().isEmpty() ? "None" : point.getControllingTown())));
        if (this.plugin.getActiveSessions().containsKey(pointId)) {
            CaptureSession session = this.plugin.getActiveSessions().get(pointId);
            player.sendMessage(Messages.get("messages.info.capturing-by", Map.of("town", session.getTownName())));
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
            player.sendMessage(Messages.get("errors.not-in-any-zone"));
            return;
        }
        int minPlayers = this.plugin.getConfig().getInt("settings.min-online-players", 5);
        if (Bukkit.getOnlinePlayers().size() < minPlayers) {
            player.sendMessage(Messages.get("errors.not-enough-players", Map.of("minplayers", String.valueOf(minPlayers))));
            return;
        }
        boolean success = this.plugin.startCapture(player, targetPoint.getId());
        if (success) {
            player.sendMessage(Messages.get("messages.capture.start-success", Map.of("point", targetPoint.getName())));
        } else {
            player.sendMessage(Messages.get("errors.capture-failed"));
        }
    }

    private void protectCurrentChunk(Player player) {
        Location playerLoc = player.getLocation();
        int chunkX = playerLoc.getBlockX() >> 4;
        int chunkZ = playerLoc.getBlockZ() >> 4;
        String id = "protected_chunk_" + chunkX + "_" + chunkZ;
        if (this.plugin.getCapturePoints().containsKey(id)) {
            player.sendMessage(Messages.get("messages.chunk.already-protected"));
            return;
        }
        String name = "Protected Chunk at " + chunkX + "," + chunkZ;
        Location chunkCenter = new Location(playerLoc.getWorld(), (double)((chunkX << 4) + 8), playerLoc.getY(), (double)((chunkZ << 4) + 8));
        this.plugin.createCapturePoint(id, name, chunkCenter, 0, 0.0);
        player.sendMessage(Messages.get("messages.chunk.protected-success"));
        player.sendMessage(Messages.get("messages.chunk.coordinates", 
            Map.of("x", String.valueOf(chunkX), "z", String.valueOf(chunkZ))));
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
            player.sendMessage(Messages.get("errors.invalid-id"));
            return;
        }
        
        if (type.isEmpty() || type.length() > 64) {
            player.sendMessage(Messages.get("errors.invalid-type"));
            return;
        }
        
        try {
            int chunkRadius = Integer.parseInt(radiusStr);
            double reward = Double.parseDouble(rewardStr);
            
            if (chunkRadius <= 0) {
                player.sendMessage(Messages.get("errors.invalid-radius"));
                return;
            }
            
            if (reward < 0.0) {
                player.sendMessage(Messages.get("errors.invalid-reward"));
                return;
            }
            
            if (plugin.getCapturePoints().containsKey(id)) {
                player.sendMessage(Messages.get("errors.id-exists"));
                return;
            }
            
            plugin.createCapturePoint(id, type, player.getLocation(), chunkRadius, reward);
            player.sendMessage(Messages.get("messages.create.success"));
            
            // Send Discord webhook notification for zone creation
            if (plugin.getDiscordWebhook() != null) {
                plugin.getDiscordWebhook().sendZoneCreated(id, id, player.getName(), type, chunkRadius, reward);
            }
            
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("radius", String.valueOf(chunkRadius));
            placeholders.put("blocks", String.valueOf(chunkRadius * 16));
            player.sendMessage(Messages.get("messages.create.radius-info", placeholders));
        } catch (NumberFormatException e) {
            player.sendMessage(Messages.get("errors.invalid-number"));
        }
    }

    private void showZoneBoundaries(Player player, String pointId) {
        CapturePoint point = this.plugin.getCapturePoints().get(pointId);
        if (point == null) {
            player.sendMessage(this.plugin.colorize("&cCapture point not found!"));
            return;
        }

        if (!plugin.boundariesEnabled()) {
            player.sendMessage(Messages.get("messages.zone.boundaries-disabled"));
            return;
        }

        // Check if already showing for this player
        String key = player.getUniqueId() + "_" + pointId;
        if (this.plugin.boundaryTasks.containsKey(key)) {
            // Toggle off
            this.plugin.stopBoundaryVisualization(key);
            player.sendMessage(this.plugin.colorize("&7Zone boundary display hidden for " + point.getName()));
            return;
        }

        // Toggle on
        player.sendMessage(this.plugin.colorize("&eShowing boundaries for " + point.getName() + " (circular zone)"));
        player.sendMessage(this.plugin.colorize("&7Type /capturepoint showzone " + pointId + " again to hide"));
        boolean started = this.plugin.startBoundaryVisualization(player, point);
        if (!started) {
            player.sendMessage(Messages.get("messages.zone.boundaries-unavailable"));
        }
    }
    
    private void toggleAllZoneBoundaries(Player player) {
        if (!this.plugin.boundariesEnabled()) {
            player.sendMessage(Messages.get("messages.zone.boundaries-disabled"));
            return;
        }
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
    
    /**
     * Handle /cap stats command (open GUI)
     */
    private void handleStatsCommand(Player player) {
        if (plugin.getStatisticsGUI() == null) {
            player.sendMessage(Messages.get("errors.stats-disabled"));
            return;
        }
        
        // Check cooldown
        if (!plugin.getStatisticsGUI().canUseStatsCommand(player)) {
            long remaining = plugin.getStatisticsGUI().getCooldownSeconds(player);
            player.sendMessage(Messages.get("errors.stats-cooldown", Map.of("seconds", String.valueOf(remaining))));
            return;
        }
        
        // Set cooldown and open menu
        plugin.getStatisticsGUI().setCooldown(player);
        plugin.getStatisticsGUI().openMainMenu(player);
    }
    
    /**
     * Handle admin stats commands
     */
    private void handleAdminStatsCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Messages.get("errors.usage-stats-admin"));
            return;
        }
        
        String subCmd = args[1].toLowerCase();
        
        switch (subCmd) {
            case "remove":
                if (args.length < 3) {
                    sender.sendMessage(Messages.get("errors.usage-stats-remove"));
                    return;
                }
                handleStatsRemove(sender, args[2]);
                break;
                
            case "reset":
                if (args.length < 3 || !args[2].equals("CONFIRM")) {
                    sender.sendMessage(Messages.get("errors.stats-reset-confirm"));
                    return;
                }
                handleStatsReset(sender);
                break;
                
            default:
                sender.sendMessage(Messages.get("errors.usage-stats-admin"));
                break;
        }
    }
    
    /**
     * Remove player statistics
     */
    private void handleStatsRemove(CommandSender sender, String playerName) {
        if (plugin.getStatisticsManager() == null) {
            sender.sendMessage(Messages.get("errors.stats-disabled"));
            return;
        }
        
        Player target = Bukkit.getPlayer(playerName);
        if (target == null) {
            sender.sendMessage(Messages.get("errors.player-not-found", Map.of("player", playerName)));
            return;
        }
        
        plugin.getStatisticsManager().removePlayerStats(target.getUniqueId());
        sender.sendMessage(Messages.get("messages.stats-removed", Map.of("player", playerName)));
    }
    
    /**
     * Reset all statistics
     */
    private void handleStatsReset(CommandSender sender) {
        if (plugin.getStatisticsManager() == null) {
            sender.sendMessage(Messages.get("errors.stats-disabled"));
            return;
        }
        
        plugin.getStatisticsManager().resetAllStats();
        sender.sendMessage(Messages.get("messages.stats-reset"));
    }
    
    /**
     * Handle zone config commands
     * Usage: /cap zoneconfig <zone_id> <subcommand> [args]
     * Subcommands:
     *   set <path> <value> - Set a zone-specific config value
     *   reset <path> - Reset a config path to default (or reset all if no path)
     *   reload - Reload the zone config from disk
     */
    private void handleZoneConfigCommand(CommandSender sender, String[] args) {
        String zoneId = args[1];
        
        // Verify zone exists
        CapturePoint point = plugin.getCapturePoint(zoneId);
        if (point == null) {
            sender.sendMessage(Messages.get("errors.zone-not-found", Map.of("id", zoneId)));
            return;
        }
        
        if (args.length < 3) {
            sender.sendMessage(Messages.get("errors.usage-zoneconfig"));
            return;
        }
        
        String subCmd = args[2].toLowerCase();
        
        switch (subCmd) {
            case "set":
                if (!sender.hasPermission("capturepoints.admin.zoneconfig.set")) {
                    sender.sendMessage(Messages.get("errors.no-permission"));
                    return;
                }
                if (args.length < 5) {
                    sender.sendMessage("§cUsage: /cap zoneconfig <zone_id> set <path> <value>");
                    return;
                }
                handleZoneConfigSet(sender, zoneId, args[3], args[4]);
                break;
                
            case "reset":
                if (!sender.hasPermission("capturepoints.admin.zoneconfig.reset")) {
                    sender.sendMessage(Messages.get("errors.no-permission"));
                    return;
                }
                if (args.length >= 4) {
                    handleZoneConfigReset(sender, zoneId, args[3]);
                } else {
                    handleZoneConfigReset(sender, zoneId, null);
                }
                break;
                
            case "reload":
                if (!sender.hasPermission("capturepoints.admin.zoneconfig.reload")) {
                    sender.sendMessage(Messages.get("errors.no-permission"));
                    return;
                }
                handleZoneConfigReload(sender, zoneId);
                break;
                
            default:
                sender.sendMessage(Messages.get("errors.usage-zoneconfig"));
                break;
        }
    }
    
    /**
     * Set a zone-specific config value
     */
    private void handleZoneConfigSet(CommandSender sender, String zoneId, String path, String value) {
        try {
            // Try to parse value as different types
            Object parsedValue;
            if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) {
                parsedValue = Boolean.parseBoolean(value);
            } else if (value.matches("-?\\d+")) {
                parsedValue = Integer.parseInt(value);
            } else if (value.matches("-?\\d+\\.\\d+")) {
                parsedValue = Double.parseDouble(value);
            } else {
                parsedValue = value;
            }
            
            // Set the value in zone config
            plugin.getZoneConfigManager().setZoneSetting(zoneId, path, parsedValue);
            plugin.getZoneConfigManager().saveZoneConfig(zoneId);
            
            sender.sendMessage(String.format("§aSet zone config: %s.%s = %s", zoneId, path, value));
            plugin.getLogger().info(String.format("Zone config updated by %s: %s.%s = %s", 
                sender.getName(), zoneId, path, value));
        } catch (Exception e) {
            sender.sendMessage("§cFailed to set config value: " + e.getMessage());
            plugin.getLogger().warning("Failed to set zone config: " + e.getMessage());
        }
    }
    
    /**
     * Reset zone config to defaults
     */
    private void handleZoneConfigReset(CommandSender sender, String zoneId, String path) {
        try {
            if (path == null) {
                // Regenerate entire config from the zone template
                plugin.getZoneConfigManager().generateZoneConfig(zoneId);
                sender.sendMessage(String.format("§aReset all config values for zone: %s", zoneId));
                plugin.getLogger().info(String.format("Zone config fully reset by %s: %s", sender.getName(), zoneId));
            } else {
                // Reset specific path to default
                Object defaultValue = plugin.getZoneConfigManager().getZoneDefault(path);
                plugin.getZoneConfigManager().setZoneSetting(zoneId, path, defaultValue);
                plugin.getZoneConfigManager().saveZoneConfig(zoneId);
                sender.sendMessage(String.format("§aReset zone config path: %s.%s", zoneId, path));
                plugin.getLogger().info(String.format("Zone config path reset by %s: %s.%s", sender.getName(), zoneId, path));
            }
        } catch (Exception e) {
            sender.sendMessage("§cFailed to reset config: " + e.getMessage());
            plugin.getLogger().warning("Failed to reset zone config: " + e.getMessage());
        }
    }
    
    /**
     * Reload zone config from disk
     */
    private void handleZoneConfigReload(CommandSender sender, String zoneId) {
        try {
            plugin.getZoneConfigManager().loadZoneConfig(zoneId);
            sender.sendMessage(String.format("§aReloaded config for zone: %s", zoneId));
            plugin.getLogger().info(String.format("Zone config reloaded by %s: %s", sender.getName(), zoneId));
        } catch (Exception e) {
            sender.sendMessage("§cFailed to reload config: " + e.getMessage());
            plugin.getLogger().warning("Failed to reload zone config: " + e.getMessage());
        }
    }
    
    /**
     * Handle shop commands
     */
    private void handleShopCommand(Player player, String[] args) {
        ShopManager shopManager = plugin.getShopManager();
        if (shopManager == null) {
            player.sendMessage(Messages.get("errors.shop.system-disabled"));
            return;
        }
        
        // /cap shop - Open nearest zone shop
        if (args.length == 1) {
            CapturePoint nearestZone = findNearestZone(player);
            if (nearestZone == null) {
                player.sendMessage(Messages.get("errors.shop.no-zone-nearby"));
                return;
            }
            
            ShopListener listener = plugin.getShopListener();
            if (listener != null) {
                listener.openShop(player, nearestZone.getId());
            }
            return;
        }
        
        String subCommand = args[1].toLowerCase();
        
        switch (subCommand) {
            case "edit":
                // /cap shop edit <zone_id> - Open editor
                if (!player.hasPermission("capturepoints.admin.shop")) {
                    player.sendMessage(Messages.get("errors.no-permission"));
                    return;
                }
                if (args.length < 3) {
                    player.sendMessage(Messages.get("errors.shop.usage-edit"));
                    return;
                }
                String zoneId = args[2];
                if (plugin.getCapturePoint(zoneId) == null) {
                    player.sendMessage(Messages.get("errors.zone-not-found"));
                    return;
                }
                ShopListener listener = plugin.getShopListener();
                if (listener != null) {
                    listener.openEditor(player, zoneId);
                }
                break;
                
            case "reload":
                // /cap shop reload <zone_id> - Reload shop config
                if (!player.hasPermission("capturepoints.admin.shop")) {
                    player.sendMessage(Messages.get("errors.no-permission"));
                    return;
                }
                if (args.length < 3) {
                    player.sendMessage(Messages.get("errors.shop.usage-reload"));
                    return;
                }
                shopManager.saveShop(args[2]);
                shopManager.loadAllShops();
                player.sendMessage(Messages.get("messages.shop.reloaded", Map.of("zone", args[2])));
                break;
                
            case "restock":
                // /cap shop restock <zone_id> - Manual restock
                if (!player.hasPermission("capturepoints.admin.shop.restock")) {
                    player.sendMessage(Messages.get("errors.no-permission"));
                    return;
                }
                if (args.length < 3) {
                    player.sendMessage(Messages.get("errors.shop.usage-restock"));
                    return;
                }
                shopManager.restockShop(args[2]);
                player.sendMessage(Messages.get("messages.shop.restocked", Map.of("zone", args[2])));
                break;
                
            case "enable":
                // /cap shop enable <zone_id> - Enable shop
                if (!player.hasPermission("capturepoints.admin.shop")) {
                    player.sendMessage(Messages.get("errors.no-permission"));
                    return;
                }
                if (args.length < 3) {
                    player.sendMessage(Messages.get("errors.shop.usage-enable"));
                    return;
                }
                ShopData enableShop = shopManager.getShop(args[2]);
                enableShop.setEnabled(true);
                shopManager.saveShop(args[2]);
                player.sendMessage(Messages.get("messages.shop.enabled", Map.of("zone", args[2])));
                break;
                
            case "disable":
                // /cap shop disable <zone_id> - Disable shop
                if (!player.hasPermission("capturepoints.admin.shop")) {
                    player.sendMessage(Messages.get("errors.no-permission"));
                    return;
                }
                if (args.length < 3) {
                    player.sendMessage(Messages.get("errors.shop.usage-disable"));
                    return;
                }
                ShopData disableShop = shopManager.getShop(args[2]);
                disableShop.setEnabled(false);
                shopManager.saveShop(args[2]);
                player.sendMessage(Messages.get("messages.shop.disabled", Map.of("zone", args[2])));
                break;
                
            default:
                player.sendMessage(Messages.get("errors.shop.invalid-subcommand"));
                break;
        }
    }
    
    /**
     * Find nearest capture zone to player
     */
    private CapturePoint findNearestZone(Player player) {
        CapturePoint nearest = null;
        double nearestDist = Double.MAX_VALUE;
        
        for (CapturePoint point : plugin.getCapturePoints().values()) {
            if (point.getLocation().getWorld() != player.getWorld()) continue;
            
            double dist = point.getLocation().distance(player.getLocation());
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = point;
            }
        }
        
        return nearestDist <= 100 ? nearest : null; // Within 100 blocks
    }
}
