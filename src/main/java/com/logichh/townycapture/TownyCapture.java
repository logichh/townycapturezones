package com.logichh.townycapture;

import com.logichh.townycapture.AreaStyle;
import com.logichh.townycapture.CaptureCommandTabCompleter;
import com.logichh.townycapture.CaptureCommands;
import com.logichh.townycapture.CaptureDeathListener;
import com.logichh.townycapture.CaptureEvents;
import com.logichh.townycapture.CapturePoint;
import com.logichh.townycapture.CaptureSession;
import com.logichh.townycapture.CommandBlockListener;
import com.logichh.townycapture.NewDayListener;
import com.logichh.townycapture.UpdateZones;
import com.logichh.townycapture.ZoneProtectionListener;
import com.logichh.townycapture.ReinforcementListener;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Town;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarFlag;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.Particle;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SingleLineChart;
import org.bukkit.scheduler.BukkitRunnable;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.ChatColor;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public class TownyCapture
extends JavaPlugin {
    private FileConfiguration config;
    private File capturePointsFile;
    private FileConfiguration capturePointsConfig;
    private Map<String, CapturePoint> capturePoints = Collections.synchronizedMap(new HashMap());
    private Map<String, CaptureSession> activeSessions = Collections.synchronizedMap(new HashMap());
    private Map<String, BossBar> captureBossBars = Collections.synchronizedMap(new HashMap());
    private Map<String, BukkitTask> captureTasks = Collections.synchronizedMap(new HashMap());
    private Map<String, String> pointTypes = Collections.synchronizedMap(new HashMap());
    private Map<UUID, Boolean> warnedPlayers = Collections.synchronizedMap(new HashMap());
    private Map<Location, BlockData> originalBlocks = Collections.synchronizedMap(new HashMap());
    private Map<UUID, Long> firstJoinTimes = Collections.synchronizedMap(new HashMap());
    public Map<UUID, Boolean> hiddenBossBars = Collections.synchronizedMap(new HashMap());
    public Map<String, BukkitTask> boundaryTasks = Collections.synchronizedMap(new HashMap());
    public Map<UUID, Boolean> disabledNotifications = Collections.synchronizedMap(new HashMap()); // Track who has disabled notifications
    private BukkitTask hourlyRewardTask;
    private Map<String, Long> lastHourlyRewardTimes = Collections.synchronizedMap(new HashMap());
    private List<MapProvider> mapProviders = new ArrayList<>();
    private UpdateZones townUpdater;  // Keep for backward compatibility with Dynmap
    private boolean worldGuardEnabled = false;
    private ReinforcementListener reinforcementListener;
    private Map<String, Integer> captureAttempts = Collections.synchronizedMap(new HashMap());
    private Map<String, Integer> successfulCaptures = Collections.synchronizedMap(new HashMap());
    private Map<String, Long> lastErrorTime = Collections.synchronizedMap(new HashMap());
    private static final int ERROR_THROTTLE_MS = 60000;
    private final Map<String, Long> lastWeeklyResetEpochDays = new HashMap<>();
    private BukkitTask weeklyResetTask;
    
    // Statistics system
    private StatisticsManager statisticsManager;
    private StatisticsGUI statisticsGUI;
    
    // Zone configuration manager
    private ZoneConfigManager zoneConfigManager;
    
    // Discord webhook manager
    private DiscordWebhook discordWebhook;
    
    // Shop system
    private ShopManager shopManager;
    private ShopListener shopListener;
    
    // Update checker
    private UpdateChecker updateChecker;

    @Override
    public void onEnable() {
        // Save default config if it doesn't exist
        saveDefaultConfig();
        this.capturePointsFile = new File(getDataFolder(), "capture_points.yml");
        
        // Initialize messages system
        Messages.init(this);
        
        // Load config
        this.config = getConfig();
        
        // Create default config values if needed
        createDefaultConfig();
        
        // Check required plugins
        if (!checkRequiredPlugins()) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        
        // Validate config
        if (!validateConfig()) {
            getLogger().severe("Failed to validate config! Disabling plugin...");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        
        // Load point types
        loadPointTypes();
        
        // Load capture points
        loadCapturePoints();
        
        // Initialize zone configuration manager
        zoneConfigManager = new ZoneConfigManager(this);
        zoneConfigManager.migrateExistingZones();
        zoneConfigManager.loadAllZoneConfigs();
        
        // Setup integrations
        setupMapProviders();
        setupWorldGuard();
        
        // Initialize statistics system
        setupStatistics();
        
        // Initialize Discord webhook
        discordWebhook = new DiscordWebhook(this);
        
        // Initialize shop system
        setupShopSystem();
        
        // Start tasks
        startSessionTimeoutChecker();
        startAutoSave();
        startHourlyRewards();
        startWeeklyResetTask();
        
        // Register listeners
        getServer().getPluginManager().registerEvents(new CaptureEvents(this), this);
        getServer().getPluginManager().registerEvents(new CommandBlockListener(this), this);
        getServer().getPluginManager().registerEvents(new ZoneProtectionListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerEventListener(), this);
        getServer().getPluginManager().registerEvents(new CaptureDeathListener(this), this);
        getServer().getPluginManager().registerEvents(new NewDayListener(this), this);
        reinforcementListener = new ReinforcementListener(this);
        getServer().getPluginManager().registerEvents(reinforcementListener, this);
        setupMobSpawner();
        
        // Register commands
        CaptureCommands commandExecutor = new CaptureCommands(this);
        PluginCommand command = getCommand("capturepoint");
        if (command == null) {
            getLogger().severe("Failed to register the capturepoint command!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        command.setExecutor(commandExecutor);
        command.setTabCompleter(new CaptureCommandTabCompleter(this));
        
        // Auto-show boundaries for all online players if enabled
        if (this.config.getBoolean("settings.auto-show-boundaries", true)) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                this.autoShowBoundariesForPlayer(player);
            }
        }
        
        // Display startup banner
        getLogger().info("═════════════════════════════════════════════════════════");
        getLogger().info("  ████████╗ ██████╗ ██╗    ██╗███╗   ██╗██╗   ██╗");
        getLogger().info("  ╚══██╔══╝██╔═══██╗██║    ██║████╗  ██║╚██╗ ██╔╝");
        getLogger().info("     ██║   ██║   ██║██║ █╗ ██║██╔██╗ ██║ ╚████╔╝ ");
        getLogger().info("     ██║   ██║   ██║██║███╗██║██║╚██╗██║  ╚██╔╝  ");
        getLogger().info("     ██║   ╚██████╔╝╚███╔███╔╝██║ ╚████║   ██║   ");
        getLogger().info("     ╚═╝    ╚═════╝  ╚══╝╚══╝ ╚═╝  ╚═══╝   ╚═╝   ");
        getLogger().info("");
        getLogger().info("   ██████╗ █████╗ ██████╗ ████████╗██╗   ██╗██████╗ ███████╗");
        getLogger().info("  ██╔════╝██╔══██╗██╔══██╗╚══██╔══╝██║   ██║██╔══██╗██╔════╝");
        getLogger().info("  ██║     ███████║██████╔╝   ██║   ██║   ██║██████╔╝█████╗  ");
        getLogger().info("  ██║     ██╔══██║██╔═══╝    ██║   ██║   ██║██╔══██╗██╔══╝  ");
        getLogger().info("  ╚██████╗██║  ██║██║        ██║   ╚██████╔╝██║  ██║███████╗");
        getLogger().info("   ╚═════╝╚═╝  ╚═╝╚═╝        ╚═╝    ╚═════╝ ╚═╝  ╚═╝╚══════╝");
        getLogger().info("");
        getLogger().info("                 v" + getDescription().getVersion() + " by Milin");
        getLogger().info("═════════════════════════════════════════════════════════");
        getLogger().info("TownyCapture has been enabled!");

        // Initialize bStats metrics
        int pluginId = 28044; // bStats plugin ID for TownyCapture
        Metrics metrics = new Metrics(this, pluginId);

        // Add custom charts
        metrics.addCustomChart(new SingleLineChart("capture_points", () -> capturePoints.size()));
        metrics.addCustomChart(new SingleLineChart("active_sessions", () -> activeSessions.size()));
        
        // Initialize update checker and check for updates
        if (getConfig().getBoolean("settings.check-for-updates", true)) {
            updateChecker = new UpdateChecker(this);
            updateChecker.checkForUpdates().thenAccept(updateAvailable -> {
                if (updateAvailable) {
                    getLogger().info("╔═══════════════════════════════════════════════╗");
                    getLogger().info("║    New version available: " + updateChecker.getLatestVersion() + "              ║");
                    getLogger().info("║    Download: modrinth.com/plugin/towny-capture-points  ║");
                    getLogger().info("╚═══════════════════════════════════════════════╝");
                }
            });
        }
    }
    
    public void autoShowBoundariesForPlayer(Player player) {
        if (getBoundaryMode() == BoundaryMode.OFF) {
            return;
        }
        for (CapturePoint point : this.capturePoints.values()) {
            startBoundaryVisualization(player, point);
        }
    }

    public boolean startBoundaryVisualization(Player player, CapturePoint point) {
        BoundaryMode mode = getBoundaryMode();
        if (mode == BoundaryMode.OFF || player == null || point == null) {
            return false;
        }

        String key = boundaryKey(player.getUniqueId(), point.getId());
        BukkitTask existing = boundaryTasks.get(key);
        if (existing != null && !existing.isCancelled()) {
            return true;
        }

        BoundaryVisualSettings settings = getBoundarySettings(mode);
        int blockRadius = point.getChunkRadius() * 16;
        int sides = Math.max(settings.minSides, (int) Math.round(blockRadius * settings.sidesMultiplier));
        long interval = Math.max(1L, settings.intervalTicks);

        final TownyCapture pluginRef = this;
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || getBoundaryMode() == BoundaryMode.OFF) {
                    this.cancel();
                    pluginRef.boundaryTasks.remove(key);
                    return;
                }

                Location center = point.getLocation();
                World world = center.getWorld();
                if (world == null) {
                    return;
                }

                for (int i = 0; i < sides; i++) {
                    double angle = 2.0 * Math.PI * i / sides;
                    double x = center.getX() + blockRadius * Math.cos(angle);
                    double z = center.getZ() + blockRadius * Math.sin(angle);

                    int surfaceY = world.getHighestBlockYAt((int)Math.floor(x), (int)Math.floor(z));
                    int startY = Math.max(0, surfaceY + settings.minHeight);
                    int endY = Math.min(320, surfaceY + settings.maxHeight);

                    for (int yPos = startY; yPos <= endY; yPos++) {
                        Location particleLoc = new Location(world, x, yPos, z);
                        try {
                            player.getWorld().spawnParticle(Particle.GLOW, particleLoc, 1);
                        } catch (Exception e) {
                            player.getWorld().spawnParticle(Particle.SOUL, particleLoc, 1);
                        }
                    }
                }
            }
        }.runTaskTimer(this, 0L, interval);

        boundaryTasks.put(key, task);
        return true;
    }

    public void stopBoundaryVisualization(String key) {
        BukkitTask task = boundaryTasks.remove(key);
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
    }

    public void cancelAllBoundaryTasks() {
        for (BukkitTask task : this.boundaryTasks.values()) {
            if (task != null && !task.isCancelled()) {
                task.cancel();
            }
        }
        this.boundaryTasks.clear();
    }

    private String boundaryKey(UUID playerId, String pointId) {
        return playerId + "_" + pointId;
    }

    public boolean boundariesEnabled() {
        return getBoundaryMode() != BoundaryMode.OFF;
    }

    private BoundaryMode getBoundaryMode() {
        String value = this.config.getString("boundary-visuals.mode", "ON");
        try {
            return BoundaryMode.valueOf(value.toUpperCase());
        } catch (Exception e) {
            return BoundaryMode.ON;
        }
    }

    private BoundaryVisualSettings getBoundarySettings(BoundaryMode mode) {
        String path = mode == BoundaryMode.REDUCED ? "boundary-visuals.reduced." : "boundary-visuals.on.";
        int minSides = Math.max(16, this.config.getInt(path + "min-sides", mode == BoundaryMode.REDUCED ? 96 : 256));
        double multiplier = Math.max(1.0, this.config.getDouble(path + "sides-multiplier", mode == BoundaryMode.REDUCED ? 2.0 : 4.0));
        int minHeight = this.config.getInt(path + "vertical-range.min", mode == BoundaryMode.REDUCED ? -2 : -10);
        int maxHeight = this.config.getInt(path + "vertical-range.max", mode == BoundaryMode.REDUCED ? 20 : 40);
        long interval = Math.max(1L, this.config.getLong(path + "interval-ticks", mode == BoundaryMode.REDUCED ? 30L : 10L));
        return new BoundaryVisualSettings(minSides, multiplier, minHeight, maxHeight, interval);
    }

    private boolean checkRequiredPlugins() {
        Plugin towny = this.getServer().getPluginManager().getPlugin("Towny");
        if (towny == null || !towny.isEnabled()) {
            this.getLogger().severe("Towny plugin not found or not enabled!");
            return false;
        }
        return true;
    }

    private boolean validateConfig() {
        try {
            ConfigurationSection capturePointSection;
            int maxActiveCaptures;
            int maxCapturePoints;
            int autoSaveInterval;
            long errorThrottle;
            long sessionTimeout;
            int minPlayers = this.config.getInt("settings.min-online-players", 5);
            if (minPlayers < 1) {
                this.getLogger().warning("Invalid min-online-players value, using default: 5");
                this.config.set("settings.min-online-players", (Object)5);
            }
            if ((sessionTimeout = this.config.getLong("settings.session-timeout", 3600000L)) < 300000L) {
                this.getLogger().warning("Invalid session-timeout value, using default: 3600000");
                this.config.set("settings.session-timeout", (Object)3600000);
            }
            if ((errorThrottle = this.config.getLong("settings.error-throttle-ms", 60000L)) < 1000L) {
                this.getLogger().warning("Invalid error-throttle-ms value, using default: 60000");
                this.config.set("settings.error-throttle-ms", (Object)60000);
            }
            if ((autoSaveInterval = this.config.getInt("settings.auto-save-interval", 300)) < 60) {
                this.getLogger().warning("Invalid auto-save-interval value, using default: 300");
                this.config.set("settings.auto-save-interval", (Object)300);
            }
            if ((maxCapturePoints = this.config.getInt("settings.max-capture-points", 50)) < 1) {
                this.getLogger().warning("Invalid max-capture-points value, using default: 50");
                this.config.set("settings.max-capture-points", (Object)50);
            }
            if ((maxActiveCaptures = this.config.getInt("settings.max-active-captures", 10)) < 1) {
                this.getLogger().warning("Invalid max-active-captures value, using default: 10");
                this.config.set("settings.max-active-captures", (Object)10);
            }
            if ((capturePointSection = this.config.getConfigurationSection("settings.capture-point")) != null) {
                int minRadius = capturePointSection.getInt("min-radius", 1);
                int maxRadius = capturePointSection.getInt("max-radius", 10);
                if (minRadius < 1) {
                    this.getLogger().warning("Invalid min-radius value, using default: 1");
                    capturePointSection.set("min-radius", (Object)1);
                }
                if (maxRadius < minRadius) {
                    this.getLogger().warning("Invalid max-radius value, using default: 10");
                    capturePointSection.set("max-radius", (Object)10);
                }
                double minReward = capturePointSection.getDouble("min-reward", 100.0);
                double maxReward = capturePointSection.getDouble("max-reward", 10000.0);
                if (minReward < 0.0) {
                    this.getLogger().warning("Invalid min-reward value, using default: 100");
                    capturePointSection.set("min-reward", (Object)100);
                }
                if (maxReward < minReward) {
                    this.getLogger().warning("Invalid max-reward value, using default: 10000");
                    capturePointSection.set("max-reward", (Object)10000);
                }
                int minPlayersPerPoint = capturePointSection.getInt("min-players", 1);
                int maxPlayersPerPoint = capturePointSection.getInt("max-players", 10);
                if (minPlayersPerPoint < 1) {
                    this.getLogger().warning("Invalid min-players value, using default: 1");
                    capturePointSection.set("min-players", (Object)1);
                }
                if (maxPlayersPerPoint < minPlayersPerPoint) {
                    this.getLogger().warning("Invalid max-players value, using default: 10");
                    capturePointSection.set("max-players", (Object)10);
                }
            }
            for (CapturePoint point : this.capturePoints.values()) {
                if (point.getChunkRadius() < this.config.getInt("settings.capture-point.min-radius", 1)) {
                    this.getLogger().warning("Point " + point.getId() + " radius too small, setting to minimum");
                    point.setChunkRadius(this.config.getInt("settings.capture-point.min-radius", 1));
                }
                if (point.getChunkRadius() > this.config.getInt("settings.capture-point.max-radius", 10)) {
                    this.getLogger().warning("Point " + point.getId() + " radius too large, setting to maximum");
                    point.setChunkRadius(this.config.getInt("settings.capture-point.max-radius", 10));
                }
                if (point.getReward() < this.config.getDouble("settings.capture-point.min-reward", 100.0)) {
                    this.getLogger().warning("Point " + point.getId() + " reward too small, setting to minimum");
                    point.setReward(this.config.getDouble("settings.capture-point.min-reward", 100.0));
                }
                if (point.getReward() > this.config.getDouble("settings.capture-point.max-reward", 10000.0)) {
                    this.getLogger().warning("Point " + point.getId() + " reward too large, setting to maximum");
                    point.setReward(this.config.getDouble("settings.capture-point.max-reward", 10000.0));
                }
                if (point.getMinPlayers() < this.config.getInt("settings.capture-point.min-players", 1)) {
                    this.getLogger().warning("Point " + point.getId() + " min players too low, setting to minimum");
                    point.setMinPlayers(this.config.getInt("settings.capture-point.min-players", 1));
                }
                if (point.getMaxPlayers() <= this.config.getInt("settings.capture-point.max-players", 10)) continue;
                this.getLogger().warning("Point " + point.getId() + " max players too high, setting to maximum");
                point.setMaxPlayers(this.config.getInt("settings.capture-point.max-players", 10));
            }
            String boundaryMode = this.config.getString("boundary-visuals.mode", "ON");
            if (!"ON".equalsIgnoreCase(boundaryMode) && !"REDUCED".equalsIgnoreCase(boundaryMode) && !"OFF".equalsIgnoreCase(boundaryMode)) {
                this.config.set("boundary-visuals.mode", "ON");
            }
            this.getLogger().info("Configuration validation completed successfully");
            return true;
        }
        catch (Exception e) {
            this.getLogger().severe("Error validating config: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private void startSessionTimeoutChecker() {
        long timeout = this.config.getLong("settings.session-timeout", 3600000L);
        new BukkitRunnable() {
            @Override
            public void run() {
                checkSessionTimeouts(timeout);
            }
        }.runTaskTimer(this, 1200L, 1200L);
    }

    private void checkSessionTimeouts(long timeout) {
        long currentTime = System.currentTimeMillis();
        for (Map.Entry<String, CaptureSession> entry : this.activeSessions.entrySet()) {
            if (currentTime - entry.getValue().getStartTime() <= timeout) continue;
            this.cancelCapture(entry.getKey(), "Session timeout");
        }
    }

    private void startAutoSave() {
        int interval = this.config.getInt("settings.auto-save-interval", 300);
        new BukkitRunnable() {
            @Override
            public void run() {
                saveCapturePoints();
            }
        }.runTaskTimer(this, interval * 20L, interval * 20L);
    }

    private void startHourlyRewards() {
        if (this.hourlyRewardTask != null && !this.hourlyRewardTask.isCancelled()) {
            this.hourlyRewardTask.cancel();
        }
        lastHourlyRewardTimes.clear();

        long intervalTicks = 20L; // Check once per second for per-zone intervals
        this.hourlyRewardTask = new BukkitRunnable() {
            @Override
            public void run() {
                distributeHourlyRewards();
            }
        }.runTaskTimer(this, intervalTicks, intervalTicks);
    }

    private void startWeeklyResetTask() {
        if (this.weeklyResetTask != null && !this.weeklyResetTask.isCancelled()) {
            this.weeklyResetTask.cancel();
        }
        this.weeklyResetTask = new BukkitRunnable() {
            @Override
            public void run() {
                checkWeeklyReset();
            }
        }.runTaskTimer(this, 200L, 72000L); // Check hourly
    }

    private void checkWeeklyReset() {
        LocalDateTime now = LocalDateTime.now();
        if (zoneConfigManager == null) {
            return;
        }

        List<CapturePoint> resetPoints = new ArrayList<>();
        long today = now.toLocalDate().toEpochDay();

        for (CapturePoint point : this.capturePoints.values()) {
            String pointId = point.getId();
            if (!zoneConfigManager.getBoolean(pointId, "weekly-reset.enabled", true)) {
                continue;
            }

            DayOfWeek targetDay = parseDay(zoneConfigManager.getString(pointId, "weekly-reset.day", "FRIDAY"));
            LocalTime targetTime = parseTime(zoneConfigManager.getString(pointId, "weekly-reset.time", "00:00"));
            if (now.getDayOfWeek() != targetDay) {
                continue;
            }
            if (now.toLocalTime().isBefore(targetTime)) {
                continue;
            }

            long lastReset = lastWeeklyResetEpochDays.getOrDefault(pointId, -1L);
            if (lastReset == today) {
                continue;
            }

            resetPoints.add(point);
            lastWeeklyResetEpochDays.put(pointId, today);
        }

        if (resetPoints.isEmpty()) {
            return;
        }

        performWeeklyReset(resetPoints);
        saveCapturePoints();
    }

    private DayOfWeek parseDay(String value) {
        try {
            return DayOfWeek.valueOf(value.toUpperCase());
        } catch (Exception e) {
            return DayOfWeek.FRIDAY;
        }
    }

    private LocalTime parseTime(String value) {
        try {
            return LocalTime.parse(value);
        } catch (Exception e) {
            return LocalTime.MIDNIGHT;
        }
    }

    private void performWeeklyReset(List<CapturePoint> points) {
        int resetCount = 0;
        for (CapturePoint point : points) {
            if (point == null) {
                continue;
            }
            String pointId = point.getId();

            if (this.activeSessions.containsKey(pointId)) {
                this.stopCapture(pointId, "Weekly reset");
            }

            point.setCapturingTown(null);
            point.setCaptureProgress(0.0);
            point.setControllingTown("");
            point.setLastCapturingTown("");
            point.setLastCaptureTime(0L);
            point.setColor("#8B0000");
            point.setFirstCaptureBonusAvailable(true);
            resetCount++;
        }

        if (resetCount == 0) {
            return;
        }

        if (this.isDynmapEnabled()) {
            this.updateAllMarkers();
        }

        String message = Messages.get("messages.weekly.reset", Map.of("count", String.valueOf(resetCount)));
        broadcastMessage(message);
        
        // Send Discord webhook notification for weekly reset
        if (discordWebhook != null) {
            discordWebhook.sendWeeklyReset(resetCount);
        }
    }

    public void onDisable() {
        for (BossBar bossBar : this.captureBossBars.values()) {
            if (bossBar == null) continue;
            for (Player player : Bukkit.getOnlinePlayers()) {
                bossBar.removePlayer(player);
            }
            bossBar.removeAll();
        }
        this.captureBossBars.clear();
        for (BukkitTask task : this.captureTasks.values()) {
            if (task == null || task.isCancelled()) continue;
            task.cancel();
        }
        this.captureTasks.clear();
        for (BukkitTask task : this.boundaryTasks.values()) {
            if (task == null || task.isCancelled()) continue;
            task.cancel();
        }
        this.boundaryTasks.clear();
        for (CapturePoint point : this.capturePoints.values()) {
            if (!this.activeSessions.containsKey(point.getId())) continue;
            this.removeBeacon(point);
        }
        this.saveCapturePoints();
        if (this.hourlyRewardTask != null && !this.hourlyRewardTask.isCancelled()) {
            this.hourlyRewardTask.cancel();
        }
        if (this.weeklyResetTask != null && !this.weeklyResetTask.isCancelled()) {
            this.weeklyResetTask.cancel();
        }
        if (this.reinforcementListener != null) {
            this.reinforcementListener.shutdown();
        }
        
        // Cleanup map providers
        for (MapProvider provider : mapProviders) {
            try {
                provider.cleanup();
                getLogger().info("Cleaned up " + provider.getName() + " markers");
            } catch (Exception e) {
                getLogger().warning("Error cleaning up " + provider.getName() + ": " + e.getMessage());
            }
        }
        mapProviders.clear();
        
        // Save and cleanup shop system
        if (shopManager != null) {
            shopManager.shutdown();
            getLogger().info("Shop system shutdown complete");
        }
        
        this.cleanupResources();
        this.getLogger().info("TownyCapture has been disabled!");
    }

    private void cleanupResources() {
        this.capturePoints.clear();
        this.activeSessions.clear();
        this.captureBossBars.clear();
        this.captureTasks.clear();
        this.pointTypes.clear();
        this.warnedPlayers.clear();
        this.originalBlocks.clear();
        this.firstJoinTimes.clear();
        this.hiddenBossBars.clear();
        this.boundaryTasks.clear();
        this.disabledNotifications.clear();
        this.lastWeeklyResetEpochDays.clear();
        for (BossBar bossBar : this.captureBossBars.values()) {
            if (bossBar == null) continue;
            bossBar.removeAll();
        }
    }

    private void createDefaultConfig() {
        if (!this.config.contains("point_types")) {
            this.config.set("point_types.default", (Object)"Strategic Point");
            this.config.set("point_types.movecraft", (Object)"Movecraft");
            this.config.set("point_types.resource", (Object)"Resource Node");
            this.config.set("point_types.military", (Object)"Military Base");
        }
        if (!this.config.contains("settings.min-online-players")) {
            this.config.set("settings.min-online-players", (Object)5);
        }
        if (!this.config.contains("settings.auto-show-boundaries")) {
            this.config.set("settings.auto-show-boundaries", (Object)true);
        }
        if (!this.config.contains("blocked-commands")) {
            ArrayList<String> defaultCommands = new ArrayList<String>();
            defaultCommands.add("/home");
            defaultCommands.add("/tp");
            defaultCommands.add("/spawn");
            this.config.set("blocked-commands", defaultCommands);
        }
        if (!this.config.contains("boundary-visuals.mode")) {
            this.config.set("boundary-visuals.mode", "ON");
            this.config.set("boundary-visuals.on.interval-ticks", 10);
            this.config.set("boundary-visuals.on.min-sides", 256);
            this.config.set("boundary-visuals.on.sides-multiplier", 4.0);
            this.config.set("boundary-visuals.on.vertical-range.min", -10);
            this.config.set("boundary-visuals.on.vertical-range.max", 40);
            this.config.set("boundary-visuals.reduced.interval-ticks", 30);
            this.config.set("boundary-visuals.reduced.min-sides", 96);
            this.config.set("boundary-visuals.reduced.sides-multiplier", 2.0);
            this.config.set("boundary-visuals.reduced.vertical-range.min", -2);
            this.config.set("boundary-visuals.reduced.vertical-range.max", 20);
        }
        try {
            this.saveConfig();
        } catch (Exception e) {
            this.getLogger().warning("Failed to save default config: " + e.getMessage());
        }
        this.loadPointTypes();
    }

    public void loadPointTypes() {
        this.pointTypes.clear();
        ConfigurationSection section = this.config.getConfigurationSection("point_types");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                this.pointTypes.put(key, section.getString(key));
            }
        }
    }

    private void startTownUpdater() {
        if (!this.isDynmapEnabled() || this.townUpdater == null) {
            this.getLogger().warning("Cannot start town updater: Dynmap is not enabled or townUpdater is not initialized!");
            return;
        }
        new BukkitRunnable() {
            @Override
            public void run() {
                townUpdater.run();
            }
        }.runTaskTimer(this, 0L, 300L);
    }

    public int convertBlockRadiusToChunkRadius(int blockRadius) {
        return (blockRadius + 15) / 16;
    }

    public int chunkRadiusToBlockRadius(int chunkRadius) {
        return chunkRadius * 16;
    }

    public int[] getChunkCoordinates(Location location) {
        int chunkX = location.getBlockX() >> 4;
        int chunkZ = location.getBlockZ() >> 4;
        return new int[]{chunkX, chunkZ};
    }

    public double getChunkDistance(Location loc1, Location loc2) {
        if (loc1 == null || loc2 == null || loc1.getWorld() == null || loc2.getWorld() == null) {
            return Double.MAX_VALUE;
        }
        if (!loc1.getWorld().getUID().equals(loc2.getWorld().getUID())) {
            return Double.MAX_VALUE;
        }
        double dx = loc1.getX() - loc2.getX();
        double dz = loc1.getZ() - loc2.getZ();
        return Math.sqrt(dx * dx + dz * dz) / 16.0;
    }

    public boolean isWithinChunkRadius(Location center, Location target, int chunkRadius) {
        if (center.getWorld() != target.getWorld()) {
            return false;
        }
        return this.getChunkDistance(center, target) <= (double)chunkRadius;
    }

    private enum BoundaryMode {
        ON, REDUCED, OFF
    }

    private static class BoundaryVisualSettings {
        final int minSides;
        final double sidesMultiplier;
        final int minHeight;
        final int maxHeight;
        final long intervalTicks;

        BoundaryVisualSettings(int minSides, double sidesMultiplier, int minHeight, int maxHeight, long intervalTicks) {
            this.minSides = minSides;
            this.sidesMultiplier = sidesMultiplier;
            this.minHeight = minHeight;
            this.maxHeight = maxHeight;
            this.intervalTicks = intervalTicks;
        }
    }

    public void showBossBarForPlayer(Player player) {
        // Don't show if player has disabled notifications
        if (disabledNotifications.getOrDefault(player.getUniqueId(), false)) {
            return;
        }
        
        if (!this.hiddenBossBars.getOrDefault(player.getUniqueId(), false).booleanValue()) {
            for (BossBar bossBar : this.captureBossBars.values()) {
                bossBar.addPlayer(player);
            }
        }
    }

    public void hideBossBarForPlayer(Player player) {
        for (BossBar bossBar : this.captureBossBars.values()) {
            bossBar.removePlayer(player);
        }
    }

    public boolean isNotificationsDisabled(Player player) {
        return player != null && disabledNotifications.getOrDefault(player.getUniqueId(), false);
    }

    public void sendNotification(Player player, String message) {
        if (player == null || isNotificationsDisabled(player)) {
            return;
        }
        if (message == null || message.isEmpty()) {
            return;
        }
        player.sendMessage(colorize(message));
    }

    private void setupMapProviders() {
        mapProviders.clear();
        
        // Initialize Dynmap if enabled
        if (getConfig().getBoolean("dynmap.enabled", true)) {
            try {
                DynmapProvider dynmapProvider = new DynmapProvider(this);
                if (dynmapProvider.initialize()) {
                    mapProviders.add(dynmapProvider);
                    
                    // Keep UpdateZones for backward compatibility
                    this.townUpdater = dynmapProvider.getTownUpdater();
                    if (this.townUpdater != null) {
                        startTownUpdater();
                    }
                }
            } catch (NoClassDefFoundError | Exception e) {
                getLogger().warning("Failed to initialize Dynmap: " + e.getMessage());
                if (getConfig().getBoolean("settings.debug-mode", false)) {
                    e.printStackTrace();
                }
            }
        } else {
            getLogger().info("Dynmap integration disabled in config.");
        }
        
        // Initialize BlueMap if enabled
        if (getConfig().getBoolean("bluemap.enabled", true)) {
            try {
                BlueMapProvider blueMapProvider = new BlueMapProvider(this);
                if (blueMapProvider.initialize()) {
                    mapProviders.add(blueMapProvider);
                }
            } catch (NoClassDefFoundError | Exception e) {
                getLogger().warning("Failed to initialize BlueMap: " + e.getMessage());
                if (getConfig().getBoolean("settings.debug-mode", false)) {
                    e.printStackTrace();
                }
            }
        } else {
            getLogger().info("BlueMap integration disabled in config.");
        }
        
        // Log active map providers
        if (mapProviders.isEmpty()) {
            getLogger().warning("No map providers active. Capture points will not be visible on web maps.");
        } else {
            StringBuilder providers = new StringBuilder("Active map providers: ");
            for (int i = 0; i < mapProviders.size(); i++) {
                if (i > 0) providers.append(", ");
                providers.append(mapProviders.get(i).getName());
            }
            getLogger().info(providers.toString());
        }
    }

    public void updateAllMarkers() {
        for (MapProvider provider : mapProviders) {
            try {
                provider.updateAllMarkers();
            } catch (Exception e) {
                getLogger().warning("Error updating markers for " + provider.getName() + ": " + e.getMessage());
            }
        }
    }
    
    public void createOrUpdateMarker(CapturePoint point) {
        for (MapProvider provider : mapProviders) {
            try {
                provider.createOrUpdateMarker(point);
            } catch (Exception e) {
                getLogger().warning("Error updating marker for " + provider.getName() + ": " + e.getMessage());
            }
        }
    }
    
    public void removeMarker(String pointId) {
        for (MapProvider provider : mapProviders) {
            try {
                provider.removeMarker(pointId);
            } catch (Exception e) {
                getLogger().warning("Error removing marker for " + provider.getName() + ": " + e.getMessage());
            }
        }
    }

    private void setupWorldGuard() {
        try {
            Plugin worldGuardPlugin = this.getServer().getPluginManager().getPlugin("WorldGuard");
            if (worldGuardPlugin != null && worldGuardPlugin.isEnabled()) {
                this.worldGuardEnabled = true;
                this.getLogger().info("WorldGuard integration enabled!");
            } else {
                this.getLogger().warning("WorldGuard not found or not enabled. WorldGuard integration disabled.");
            }
        }
        catch (Exception e) {
            this.getLogger().warning("Error setting up WorldGuard integration: " + e.getMessage());
        }
    }

    private void setupMobSpawner() {
        try {
            // Create vanilla mob spawner as the base
            VanillaMobSpawner vanillaSpawner = new VanillaMobSpawner(this);
            
            // Check if MythicMobs integration is enabled in config (global or per-zone)
            boolean mythicEnabled = false;
            if (zoneConfigManager != null) {
                mythicEnabled = zoneConfigManager.getDefaultBoolean("reinforcements.mythicmobs.enabled", false);
            } else {
                mythicEnabled = getConfig().getBoolean("reinforcements.mythicmobs.enabled", false);
            }
            if (!mythicEnabled && zoneConfigManager != null) {
                for (String zoneId : capturePoints.keySet()) {
                    if (zoneConfigManager.getBoolean(zoneId, "reinforcements.mythicmobs.enabled", false)) {
                        mythicEnabled = true;
                        break;
                    }
                }
            }
            
            MobSpawner spawner;
            
            if (mythicEnabled) {
                // Try to create MythicMobs handler with vanilla fallback
                try {
                    MythicMobsHandler mythicHandler = new MythicMobsHandler(this, vanillaSpawner);
                    spawner = mythicHandler;
                    getLogger().info("MythicMobs integration initialized!");
                } catch (NoClassDefFoundError | Exception e) {
                    getLogger().warning("Failed to initialize MythicMobs integration: " + e.getMessage());
                    getLogger().warning("Falling back to vanilla mob spawning.");
                    spawner = vanillaSpawner;
                }
            } else {
                // Use vanilla spawner
                spawner = vanillaSpawner;
                getLogger().info("Using vanilla mob spawning for reinforcements.");
            }
            
            // Set the spawner in the reinforcement listener
            if (reinforcementListener != null) {
                reinforcementListener.setMobSpawner(spawner);
            } else {
                getLogger().warning("ReinforcementListener not initialized! Mob spawner cannot be set.");
            }
            
            // Log configured mobs if debug mode is enabled
            if (getConfig().getBoolean("settings.debug-mode", false)) {
                getLogger().info("Configured mobs: " + String.join(", ", spawner.getConfiguredMobs()));
            }
            
        } catch (Exception e) {
            getLogger().severe("Error setting up mob spawner: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Initialize statistics tracking system
     */
    private void setupStatistics() {
        try {
            getLogger().info("Initializing statistics system...");
            
            // Check if statistics are enabled in config
            boolean statsEnabled = getConfig().getBoolean("statistics.enabled", true);
            
            if (!statsEnabled) {
                getLogger().info("Statistics system is disabled in config.");
                return;
            }
            
            // Initialize statistics manager
            statisticsManager = new StatisticsManager(this);
            getLogger().info("Statistics manager initialized.");
            
            // Initialize statistics GUI
            statisticsGUI = new StatisticsGUI(this, statisticsManager);
            getLogger().info("Statistics GUI initialized.");
            
            // Register GUI listener
            getServer().getPluginManager().registerEvents(new StatisticsGUIListener(this, statisticsGUI), this);
            getLogger().info("Statistics GUI listener registered.");
            
            // Register statistics tracking listener
            getServer().getPluginManager().registerEvents(new StatisticsTrackingListener(this), this);
            getLogger().info("Statistics tracking listener registered.");
            
            // Schedule auto-save task
            int saveInterval = getConfig().getInt("statistics.auto-save-interval", 300);
            getServer().getScheduler().runTaskTimer(this, () -> {
                if (statisticsManager != null) {
                    statisticsManager.saveStatistics();
                }
            }, saveInterval * 20L, saveInterval * 20L);
            
            getLogger().info("Statistics system enabled with auto-save every " + saveInterval + " seconds.");
            
        } catch (Exception e) {
            getLogger().severe("Error setting up statistics system: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Setup shop system
     */
    private void setupShopSystem() {
        try {
            // Check if shop system is enabled in config
            boolean shopsEnabled = getConfig().getBoolean("shops.enabled", false);
            
            if (!shopsEnabled) {
                getLogger().info("Shop system is disabled in config.");
                return;
            }
            
            // Initialize shop manager
            shopManager = new ShopManager(this);
            getLogger().info("Shop manager initialized.");
            
            // Initialize shop listener
            shopListener = new ShopListener(this);
            getServer().getPluginManager().registerEvents(shopListener, this);
            getLogger().info("Shop listener registered.");
            
            getLogger().info("Shop system enabled! Use /cap shop to access zone shops.");
            
        } catch (Exception e) {
            getLogger().severe("Error setting up shop system: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void listCapturePoints(Player player) {
        player.sendMessage(Messages.get("messages.list.header"));
        if (this.capturePoints.isEmpty()) {
            player.sendMessage(Messages.get("messages.list.empty"));
            return;
        }
        for (CapturePoint point : this.capturePoints.values()) {
            String status = point.getControllingTown().isEmpty() ? 
                Messages.get("messages.list.unclaimed") : 
                Messages.get("messages.list.controlled", Map.of("town", point.getControllingTown()));
            player.sendMessage(Messages.get("messages.list.format", Map.of(
                "id", point.getId(),
                "name", point.getName(),
                "status", status)));
        }
    }

    public void showCapturePointInfo(Player player, String id) {
        if (!this.capturePoints.containsKey(id)) {
            player.sendMessage(Messages.get("messages.info.not-found"));
            return;
        }
        CapturePoint point = this.capturePoints.get(id);
        player.sendMessage(Messages.get("messages.info.header", Map.of("point", point.getName())));
        player.sendMessage(Messages.get("messages.info.id", Map.of("id", point.getId())));
        player.sendMessage(Messages.get("messages.info.location", Map.of(
            "world", point.getLocation().getWorld().getName(),
            "x", String.valueOf(Math.round(point.getLocation().getX())),
            "y", String.valueOf(Math.round(point.getLocation().getY())),
            "z", String.valueOf(Math.round(point.getLocation().getZ())))));
        player.sendMessage(Messages.get("messages.info.radius", Map.of("radius", String.valueOf(point.getChunkRadius()))));
        player.sendMessage(Messages.get("messages.info.reward", Map.of("reward", String.valueOf(getBaseReward(point)))));
        player.sendMessage(Messages.get("messages.info.type", Map.of("type", this.getPointTypeName(point.getType()))));
        if (point.getControllingTown().isEmpty()) {
            player.sendMessage(Messages.get("messages.info.status-unclaimed"));
        } else {
            player.sendMessage(Messages.get("messages.info.status-controlled", Map.of("town", point.getControllingTown())));
        }
        if (this.activeSessions.containsKey(id)) {
            CaptureSession session = this.activeSessions.get(id);
            player.sendMessage(Messages.get("messages.info.capturing-town", Map.of("town", session.getTownName())));
            player.sendMessage(Messages.get("messages.info.capturing-time", Map.of("time", this.formatTime(session.getRemainingTime()))));
            player.sendMessage(Messages.get("messages.info.capturing-phase", Map.of("phase", (session.isInPreparationPhase() ? "Preparation" : "Capture"))));
        }
        if (this.isDynmapEnabled()) {
            player.sendMessage(Messages.get("messages.info.dynmap"));
        }
        if (this.isWorldGuardEnabled()) {
            player.sendMessage(Messages.get("messages.info.worldguard"));
        }
    }

    public void listPointTypes(Player player) {
        player.sendMessage(Messages.get("messages.types.header"));
        for (Map.Entry<String, String> entry : this.pointTypes.entrySet()) {
            player.sendMessage(Messages.get("messages.types.format", 
                "type", entry.getKey(),
                "description", entry.getValue()));
        }
    }

    public void reloadAll() {
        this.reloadConfig();
        this.config = this.getConfig();
        Messages.reload();
        if (this.capturePointsFile == null) {
            this.capturePointsFile = new File(getDataFolder(), "capture_points.yml");
        }
        this.capturePointsConfig = YamlConfiguration.loadConfiguration((File)this.capturePointsFile);
        this.capturePoints.clear();
        this.loadCapturePoints();
        this.loadPointTypes();
        if (zoneConfigManager != null) {
            zoneConfigManager.reloadDefaults();
            zoneConfigManager.loadAllZoneConfigs();
        }
        if (this.isDynmapEnabled()) {
            this.updateAllMarkers();
        }
        startWeeklyResetTask();
        if (!boundariesEnabled()) {
            cancelAllBoundaryTasks();
        } else if (this.config.getBoolean("settings.auto-show-boundaries", true)) {
            cancelAllBoundaryTasks();
            for (Player player : Bukkit.getOnlinePlayers()) {
                this.autoShowBoundariesForPlayer(player);
            }
        }
    }

    public void reloadLang() {
        Messages.reload();
    }

    public void saveCapturePoints() {
        try {
            if (this.capturePointsFile == null) {
                this.capturePointsFile = new File(this.getDataFolder(), "capture_points.yml");
            }
            File file = this.capturePointsFile;
            if (!file.exists()) {
                file.createNewFile();
            }
            YamlConfiguration config = new YamlConfiguration();
            config.options().header(
                "AUTO-GENERATED FILE - DO NOT EDIT UNLESS YOU KNOW WHAT YOU ARE DOING.\n" +
                "This file stores capture point state and metadata."
            );
            ConfigurationSection resetSection = config.createSection("metadata.weekly-reset-epoch-days");
            for (Map.Entry<String, Long> entry : this.lastWeeklyResetEpochDays.entrySet()) {
                resetSection.set(entry.getKey(), entry.getValue());
            }
            for (Map.Entry<String, CapturePoint> entry : this.capturePoints.entrySet()) {
                String path = "points." + entry.getKey();
                CapturePoint point = entry.getValue();
                config.set(path + ".name", (Object)point.getName());
                config.set(path + ".world", (Object)point.getWorldUUID().toString());
                config.set(path + ".x", (Object)point.getLocation().getX());
                config.set(path + ".y", (Object)point.getLocation().getY());
                config.set(path + ".z", (Object)point.getLocation().getZ());
                config.set(path + ".radius", (Object)point.getChunkRadius());
                config.set(path + ".reward", (Object)point.getReward());
                config.set(path + ".controllingTown", (Object)point.getControllingTown());
                config.set(path + ".type", (Object)point.getType());
                config.set(path + ".showOnMap", (Object)point.isShowOnMap());
                config.set(path + ".lastCaptureTime", (Object)point.getLastCaptureTime());
                config.set(path + ".firstCaptureBonusAvailable", point.isFirstCaptureBonusAvailable());
            }
            config.save(file);
        }
        catch (Exception e) {
            this.getLogger().severe("Failed to save capture points: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void loadCapturePoints() {
        try {
            if (this.capturePointsFile == null) {
                this.capturePointsFile = new File(this.getDataFolder(), "capture_points.yml");
            }
            File file = this.capturePointsFile;
            if (!file.exists()) {
                return;
            }
            YamlConfiguration config = new YamlConfiguration();
            config.load(file);
            this.lastWeeklyResetEpochDays.clear();
            ConfigurationSection resetSection = config.getConfigurationSection("metadata.weekly-reset-epoch-days");
            if (resetSection != null) {
                for (String key : resetSection.getKeys(false)) {
                    this.lastWeeklyResetEpochDays.put(key, resetSection.getLong(key, -1L));
                }
            }
            long legacyReset = config.getLong("metadata.lastWeeklyResetEpochDay", -1L);
            ConfigurationSection pointsSection = config.getConfigurationSection("points");
            if (pointsSection == null) {
                return;
            }
            for (String id : pointsSection.getKeys(false)) {
                String path = "points." + id;
                String name = config.getString(path + ".name");
                UUID worldUUID = UUID.fromString(config.getString(path + ".world"));
                double x = config.getDouble(path + ".x");
                double y = config.getDouble(path + ".y");
                double z = config.getDouble(path + ".z");
                int radius = config.getInt(path + ".radius");
                double reward = config.getDouble(path + ".reward");
                String controllingTown = config.getString(path + ".controllingTown", "");
                String type = config.getString(path + ".type", "default");
                boolean showOnMap = config.getBoolean(path + ".showOnMap", true);
                long lastCaptureTime = config.getLong(path + ".lastCaptureTime", 0L);
                boolean bonusAvailable = config.getBoolean(path + ".firstCaptureBonusAvailable", false);
                World world = Bukkit.getWorld((UUID)worldUUID);
                if (world == null) {
                    this.getLogger().warning("World not found for capture point: " + id);
                    continue;
                }
                Location location = new Location(world, x, y, z);
                CapturePoint point = new CapturePoint(id, name, location, radius, reward);
                point.setType(type);
                point.setShowOnMap(showOnMap);
                point.setLastCaptureTime(lastCaptureTime);
                point.setFirstCaptureBonusAvailable(bonusAvailable);
                if (!controllingTown.isEmpty()) {
                    point.setControllingTown(controllingTown);
                }
                if (!this.lastWeeklyResetEpochDays.containsKey(id) && legacyReset >= 0L) {
                    this.lastWeeklyResetEpochDays.put(id, legacyReset);
                }
                this.capturePoints.put(id, point);
                this.getLogger().info("Loaded capture point: " + name);
            }
        }
        catch (Exception e) {
            this.getLogger().severe("Failed to load capture points: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void createBeacon(Location location) {
        World world = location.getWorld();
        if (world == null) return;
        
        Location glassLoc = location.clone();
        glassLoc.setY((double)world.getHighestBlockYAt(glassLoc));
        Location beaconLoc = glassLoc.clone().subtract(0.0, 1.0, 0.0);
        
        // Store original blocks before modifying them
        for (int x = -1; x <= 1; ++x) {
            for (int z = -1; z <= 1; ++z) {
                Location ironLoc = beaconLoc.clone().add((double)x, -1.0, (double)z);
                storeOriginalBlock(ironLoc);
            }
        }
        
        storeOriginalBlock(beaconLoc);
        storeOriginalBlock(glassLoc);
        
        // Place beacon structure
        for (int x = -1; x <= 1; ++x) {
            for (int z = -1; z <= 1; ++z) {
                Location ironLoc = beaconLoc.clone().add((double)x, -1.0, (double)z);
                Block ironBlock = ironLoc.getBlock();
                ironBlock.setType(Material.IRON_BLOCK);
            }
        }
        
        Block beaconBlock = beaconLoc.getBlock();
        beaconBlock.setType(Material.BEACON);
        
        Block glassBlock = glassLoc.getBlock();
        glassBlock.setType(Material.GLASS);
    }

    private void storeOriginalBlock(Location loc) {
        Block block = loc.getBlock();
        this.originalBlocks.put(loc.clone(), block.getBlockData().clone());
    }

    private void removeBeacon(CapturePoint point) {
        Location center = point.getLocation().clone();
        World world = center.getWorld();
        Location glassLoc = center.clone();
        glassLoc.setY((double)world.getHighestBlockYAt(glassLoc));
        Location beaconLoc = glassLoc.clone().subtract(0.0, 1.0, 0.0);
        for (int x = -1; x <= 1; ++x) {
            for (int z = -1; z <= 1; ++z) {
                Location ironLoc = beaconLoc.clone().add((double)x, -1.0, (double)z);
                this.restoreBlock(ironLoc);
            }
        }
        this.restoreBlock(beaconLoc);
        this.restoreBlock(glassLoc);
    }

    private void restoreBlock(Location loc) {
        BlockData originalData = this.originalBlocks.remove(loc);
        if (originalData != null) {
            Block block = loc.getBlock();
            block.setBlockData(originalData);
        }
    }

    public boolean startCapture(Player player, String pointId) {
        CapturePoint point = capturePoints.get(pointId);
        if (point == null) {
            player.sendMessage(Messages.get("messages.capture.point-not-found"));
            return false;
        }
        ZoneConfigManager zoneManager = zoneConfigManager;

        // Check minimum online players
        int minOnlinePlayers = config.getInt("settings.min-online-players", 5);
        if (Bukkit.getOnlinePlayers().size() < minOnlinePlayers) {
            String message = Messages.get("errors.not-enough-players", Map.of("minplayers", String.valueOf(minOnlinePlayers)));
            player.sendMessage(message);
            return false;
        }

        // Check if player is in a town
        Town town = TownyAPI.getInstance().getTown(player);
        if (town == null) {
            player.sendMessage(Messages.get("messages.capture.not-in-town"));
            return false;
        }

        // Check if player is within chunk radius
        if (!isWithinChunkRadius(player.getLocation(), point.getLocation(), point.getChunkRadius())) {
            player.sendMessage(Messages.get("messages.capture.not-in-radius"));
            return false;
        }
        
        // Block self-capture when town already controls the point
        boolean preventSelfCapture = zoneManager != null
            ? zoneManager.getBoolean(pointId, "capture-conditions.prevent-self-capture", true)
            : config.getBoolean("capture-conditions.prevent-self-capture", true);
        String controllingTown = point.getControllingTown();
        if (preventSelfCapture && controllingTown != null && !controllingTown.isEmpty() &&
            controllingTown.equalsIgnoreCase(town.getName())) {
            player.sendMessage(Messages.get("errors.already-controls-point", Map.of(
                "point", point.getName()
            )));
            return false;
        }

        // Enforce cooldown after a successful capture
        boolean cooldownEnabled = zoneManager != null
            ? zoneManager.getBoolean(pointId, "capture-conditions.capture-cooldown.enabled", true)
            : config.getBoolean("capture-conditions.capture-cooldown.enabled", true);
        if (cooldownEnabled && 
            controllingTown != null && !controllingTown.isEmpty()) {
            long cooldownMs = zoneManager != null
                ? zoneManager.getLong(pointId, "capture-conditions.capture-cooldown.duration-ms", 300000L)
                : config.getLong("capture-conditions.capture-cooldown.duration-ms", 300000L);
            long lastCaptureTime = point.getLastCaptureTime();
            if (cooldownMs > 0 && lastCaptureTime > 0L) {
                long elapsed = System.currentTimeMillis() - lastCaptureTime;
                if (elapsed < cooldownMs) {
                    int secondsLeft = (int)Math.ceil((cooldownMs - elapsed) / 1000.0);
                    String timeLeft = formatTime(Math.max(secondsLeft, 1));
                    player.sendMessage(Messages.get("errors.capture-cooldown-active", Map.of(
                        "time", timeLeft,
                        "point", point.getName()
                    )));
                    return false;
                }
            }
        }

        // Check if point is already being captured
        CaptureSession existingSession = activeSessions.get(pointId);
        if (existingSession != null) {
            // If the point is being captured by the same town, allow the player to join
            if (existingSession.getTownName().equals(town.getName())) {
                existingSession.getPlayers().add(player);
                // Show boss bar to the joining player
                BossBar bossBar = captureBossBars.get(pointId);
                if (bossBar != null && !hiddenBossBars.getOrDefault(player.getUniqueId(), false) &&
                    !disabledNotifications.getOrDefault(player.getUniqueId(), false)) {
                    bossBar.addPlayer(player);
                }
                player.sendMessage(Messages.get("messages.capture.joined-own", Map.of("point", point.getName())));
                return true;
            }
            player.sendMessage(ChatColor.RED + "This capture point is already being captured!");
            player.sendMessage(Messages.get("messages.capture.already-capturing"));
            return false;
        }

        // Create beacon at capture point
        createBeacon(point.getLocation());

        // Start preparation phase
        int preparationTime = zoneManager != null
            ? zoneManager.getInt(pointId, "capture.preparation.duration", 1)
            : config.getInt("capture.preparation.duration", 1);
        int captureTime = zoneManager != null
            ? zoneManager.getInt(pointId, "capture.capture.duration", 15)
            : config.getInt("capture.capture.duration", 15);
        boolean showCountdown = zoneManager != null
            ? zoneManager.getBoolean(pointId, "capture.preparation.show-countdown", true)
            : config.getBoolean("capture.preparation.show-countdown", true);

        // Create capture session
        CaptureSession session = new CaptureSession(point, town, player, preparationTime, captureTime);
        session.getPlayers().add(player);
        activeSessions.put(pointId, session);
        
        // Track statistics for capture start
        if (statisticsManager != null) {
            statisticsManager.onCaptureStart(point.getId(), town.getName(), player.getUniqueId());
        }
        
        // Send Discord webhook notification
        if (discordWebhook != null) {
            Location loc = point.getLocation();
            String locationStr = loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ();
            discordWebhook.sendCaptureStarted(point.getId(), point.getName(), town.getName(), locationStr);
        }

        // Create boss bar
        BossBar bossBar = Bukkit.createBossBar(
            Messages.get("bossbar.preparing", Map.of("zone", point.getName())),
            BarColor.BLUE,
            BarStyle.SOLID
        );
        captureBossBars.put(pointId, bossBar);
        
        // Only show boss bar to members of the capturing town
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!hiddenBossBars.getOrDefault(online.getUniqueId(), false) &&
                !disabledNotifications.getOrDefault(online.getUniqueId(), false)) {
                try {
                    Town playerTown = TownyAPI.getInstance().getTown(online);
                    if (playerTown != null && playerTown.getName().equals(town.getName())) {
                        bossBar.addPlayer(online);
                    }
        } catch (Exception e) {
                    // Skip if there's an error getting the player's town
                    getLogger().warning("Error checking town for player " + online.getName() + ": " + e.getMessage());
                }
            }
        }

        // Broadcast preparation message
        String prepMessage = Messages.get("messages.capture.started", Map.of(
            "town", town.getName(),
            "point", point.getName()
        ));
        broadcastMessage(prepMessage);
        
        // Play capture started sound
        playCaptureSoundAtLocation("capture-started", point.getLocation());

        // Start preparation task
        AtomicInteger prepTimeLeft = new AtomicInteger(preparationTime * 60);
        BukkitTask prepTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (!session.isActive()) {
                return;
            }

            session.decrementPreparationTime();
            int timeLeft = session.getRemainingPreparationTime();
            
            if (timeLeft <= 0) {
                // Start capture phase
                startCapturePhase(point, town, player);
                return;
            }

            if (showCountdown) {
                // Update boss bar with preparation time
                String title = Messages.get("bossbar.preparation-timer", Map.of(
                    "zone", point.getName(),
                    "time", formatTime(timeLeft)
                ));
                bossBar.setTitle(title);
                bossBar.setProgress(Math.max(0.0, (double)timeLeft / session.getInitialPreparationTime()));
            }
        }, 20L, 20L);

        // Store preparation task
        captureTasks.put(pointId, prepTask);

        return true;
    }

    private void startCapturePhase(CapturePoint point, Town town, Player player) {
        String pointId = point.getId();
        CaptureSession session = activeSessions.get(pointId);
        if (session == null || !session.isInPreparationPhase()) {  // Add check to prevent multiple phase changes
            return;
        }

        // Cancel the preparation task first
        BukkitTask prepTask = captureTasks.get(pointId);
        if (prepTask != null && !prepTask.isCancelled()) {
            prepTask.cancel();
        }

        // Start capture phase first
        session.startCapturePhase();
        point.setCapturingTown(town.getName());

        // Update boss bar
        BossBar bossBar = captureBossBars.get(pointId);
        if (bossBar != null) {
            String title = Messages.get("bossbar.capturing", Map.of(
                "town", town.getName(),
                "zone", point.getName(),
                "time", formatTime(session.getRemainingCaptureTime())
            ));
            bossBar.setTitle(title);
            bossBar.setColor(BarColor.RED);
            
            // Show capture phase boss bar to all players
            bossBar.removeAll();
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (!hiddenBossBars.getOrDefault(online.getUniqueId(), false) &&
                    !disabledNotifications.getOrDefault(online.getUniqueId(), false)) {
                    bossBar.addPlayer(online);
                }
            }
        }

        // Broadcast capture phase start message only once
        String phaseMessage = Messages.get("messages.capture.phase-started", Map.of(
            "town", town.getName(),
            "zone", point.getName()
        ));
        broadcastMessage(phaseMessage);
        
        // Play phase started sound
        playCaptureSoundAtLocation("capture-phase-started", point.getLocation());
        
        // Start reinforcement waves
        if (reinforcementListener != null) {
            reinforcementListener.startReinforcementWaves(pointId, point);
        }
        
        // Start capture task
        BukkitTask captureTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (!session.isActive()) {
                return;
            }

            session.decrementCaptureTime();
            int timeLeft = session.getRemainingCaptureTime();

            if (timeLeft <= 0) {
                completeCapture(point);
                return;
            }

            // Update boss bar
            if (bossBar != null) {
                String title = Messages.get("bossbar.capturing", Map.of(
                    "town", town.getName(),
                    "zone", point.getName(),
                    "time", formatTime(timeLeft)
                ));
                bossBar.setTitle(title);
                bossBar.setProgress(Math.max(0.0, (double)timeLeft / session.getInitialCaptureTime()));
            }
        }, 20L, 20L);

        captureTasks.put(pointId, captureTask);
    }

    private void completeCapture(CapturePoint point) {
        // Defensive guards: ensure point still exists and session is valid
        if (point == null) {
            return;
        }
        String pointId = point.getId();
        if (!this.capturePoints.containsKey(pointId)) {
            // Point was removed; bail out
            return;
        }
        CaptureSession session = activeSessions.get(pointId);
        // Get the capturing town name before clearing it
        String capturingTown = point.getCapturingTown();
        
        // If capturingTown is null, try to get it from the active session
        if ((capturingTown == null || capturingTown.isEmpty()) && session != null) {
            capturingTown = session.getTownName();
        }
        
        // If still null, use a default value
        if (capturingTown == null || capturingTown.isEmpty()) {
            capturingTown = "Unknown";
            getLogger().warning("Capture completed for point " + point.getId() + " but no town was found!");
        }
        
        // Set the controlling town
        point.setControllingTown(capturingTown);
        point.setCapturingTown(null);
        point.setCaptureProgress(0.0);
        point.setLastCapturingTown(capturingTown);
        point.setLastCaptureTime(System.currentTimeMillis());
        
        // Track statistics for capture completion
        if (statisticsManager != null && session != null) {
            java.util.Set<java.util.UUID> playerUUIDs = session.getPlayers().stream()
                .map(org.bukkit.entity.Player::getUniqueId)
                .collect(java.util.stream.Collectors.toSet());
            statisticsManager.onCaptureComplete(
                point.getId(),
                point.getName(),
                capturingTown,
                session.getInitiatorUUID(),
                playerUUIDs
            );
        }
        
        // Send Discord webhook notification
        if (discordWebhook != null && session != null) {
            long captureTimeMs = System.currentTimeMillis() - session.getStartTime();
            int captureTimeSeconds = (int) (captureTimeMs / 1000);
            String captureTimeStr = captureTimeSeconds + "s";
            discordWebhook.sendCaptureCompleted(point.getId(), point.getName(), capturingTown, captureTimeStr);
        }
        
        // Set the town's color for Dynmap visualization
        String townColor = getTownColor(capturingTown);
        point.setColor(townColor);
        
        // Broadcast capture completion message only once
        String completeMessage = Messages.get("messages.capture.complete", Map.of(
            "town", capturingTown,
            "zone", point.getName()
        ));
        broadcastMessage(completeMessage);
        
        // Send personal message to capturing players
        String personalMessage = Messages.get("messages.capture.congratulations", Map.of("zone", point.getName()));
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                Town playerTown = TownyAPI.getInstance().getTown(player);
                if (playerTown != null && playerTown.getName().equals(capturingTown) &&
                    isWithinChunkRadius(player.getLocation(), point.getLocation(), point.getChunkRadius())) {
                    sendNotification(player, personalMessage);
                }
            } catch (Exception e) {
                // Ignore errors
            }
        }
        
        // Play capture complete sound
        playCaptureSoundAtLocation("capture-complete", point.getLocation());

        // First capture bonus after weekly reset
        boolean bonusEnabled = zoneConfigManager != null
            ? zoneConfigManager.getBoolean(pointId, "weekly-reset.first-capture-bonus.enabled", true)
            : this.config.getBoolean("weekly-reset.first-capture-bonus.enabled", true);
        if (point.isFirstCaptureBonusAvailable() && bonusEnabled) {
            double minBonus = zoneConfigManager != null
                ? zoneConfigManager.getDouble(pointId, "weekly-reset.first-capture-bonus.amount-range.min", 500.0)
                : this.config.getDouble("weekly-reset.first-capture-bonus.amount-range.min", 500.0);
            double maxBonus = zoneConfigManager != null
                ? zoneConfigManager.getDouble(pointId, "weekly-reset.first-capture-bonus.amount-range.max", 1500.0)
                : this.config.getDouble("weekly-reset.first-capture-bonus.amount-range.max", 1500.0);
            if (maxBonus < minBonus) {
                maxBonus = minBonus;
            }
            double bonus = minBonus + (Math.random() * (maxBonus - minBonus));
            Player bonusTarget = null;
            if (session != null && session.getInitiatorUUID() != null) {
                bonusTarget = Bukkit.getPlayer(session.getInitiatorUUID());
            }
            if (bonusTarget != null) {
                try {
                    com.palmergames.bukkit.towny.object.Resident resident = com.palmergames.bukkit.towny.TownyAPI.getInstance().getResident(bonusTarget.getUniqueId());
                    if (resident != null && resident.getAccount() != null) {
                        resident.getAccount().deposit(bonus, "First capture weekly bonus for " + point.getName());
                        sendNotification(bonusTarget, Messages.get("messages.weekly.first-capture-bonus", Map.of(
                            "zone", point.getName(),
                            "amount", String.format("%.1f", bonus)
                        )));
                        
                        // Send Discord webhook notification for first capture bonus
                        if (discordWebhook != null) {
                            discordWebhook.sendFirstCaptureBonus(point.getId(), point.getName(), capturingTown, bonus);
                        }
                    }
                } catch (Exception e) {
                    getLogger().warning("Failed to grant first capture bonus to " + bonusTarget.getName() + ": " + e.getMessage());
                }
            }
            point.setFirstCaptureBonusAvailable(false);
        }
        
        // Remove the boss bar
        removeCaptureBossBar(pointId);
        
        // Remove the beacon
        removeBeacon(point);
        
        // Clear reinforcements
        if (reinforcementListener != null) {
            reinforcementListener.clearReinforcements(point.getId());
        }
        
        // Update Dynmap if enabled
        if (isDynmapEnabled()) {
            updateAllMarkers();
        }
        
        // Stop the capture session
        stopCapture(pointId);
        
        // Cancel the capture task
        BukkitTask task = captureTasks.remove(pointId);
        if (task != null) {
            task.cancel();
        }
        
        // Log successful capture
        logSuccessfulCapture(point.getId());
        
        // Save capture points
        saveCapturePoints();
    }

    public void cancelCaptureByDeath(String pointId, String victimName, String killerName) {
        BukkitTask task;
        if (!this.activeSessions.containsKey(pointId)) {
            return;
        }
        CaptureSession session = this.activeSessions.get(pointId);
        CapturePoint point = this.capturePoints.get(pointId);
        
        // Track statistics for failed capture
        if (statisticsManager != null && session != null) {
            statisticsManager.onCaptureFailed(point.getId(), session.getTownName(), session.getInitiatorUUID());
        }
        
        // Send Discord webhook notification for capture failure
        if (discordWebhook != null && session != null) {
            String reason = victimName + " was killed by " + killerName;
            discordWebhook.sendCaptureFailed(point.getId(), point.getName(), session.getTownName(), reason);
        }
        
        // Clear reinforcements
        if (reinforcementListener != null) {
            reinforcementListener.clearReinforcements(pointId);
        }
        
        this.removeBeacon(point);
        BossBar bossBar = this.captureBossBars.remove(pointId);
        if (bossBar != null) {
            String deathTitle = zoneConfigManager != null
                ? zoneConfigManager.getString(pointId, "bossbar.death_title", "&c%victim% has been defeated by %killer% at %point%!")
                : this.config.getString("bossbar.death_title", "&c%victim% has been defeated by %killer% at %point%!");
            deathTitle = deathTitle.replace("%victim%", victimName).replace("%killer%", killerName).replace("%point%", point.getName());
            bossBar.setTitle(this.colorize(deathTitle));
            bossBar.setColor(BarColor.RED);
            Bukkit.getScheduler().runTaskLater((Plugin)this, () -> {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    bossBar.removePlayer(player);
                }
                bossBar.removeAll();
            }, 100L);
        }
        if ((task = this.captureTasks.remove(pointId)) != null) {
            task.cancel();
        }
        this.activeSessions.remove(pointId);
        String message = Messages.get("messages.capture.death_broadcast", Map.of(
            "town", session.getTownName(),
            "zone", point.getName(),
            "victim", victimName,
            "killer", killerName
        ));
        broadcastMessage(message);
        if (this.isDynmapEnabled()) {
            this.townUpdater.updateMarker(point);
        }
    }

    public boolean stopCapture(String pointId, String reason) {
        if (!this.activeSessions.containsKey(pointId)) {
            return false;
        }
        CaptureSession session = this.activeSessions.get(pointId);
        CapturePoint point = this.capturePoints.get(pointId);
        
        // Track statistics for failed capture (admin stopped)
        if (statisticsManager != null && session != null) {
            statisticsManager.onCaptureFailed(point.getId(), session.getTownName(), session.getInitiatorUUID());
        }
        
        // Send Discord webhook notification for capture cancellation
        if (discordWebhook != null && session != null) {
            String webhookReason = (reason != null && !reason.isEmpty()) ? reason : Messages.get("discord.capture.cancelled.admin-reason");
            discordWebhook.sendCaptureCancelled(point.getId(), point.getName(), session.getTownName(), webhookReason);
        }
        
        // Clear reinforcements
        if (reinforcementListener != null) {
            reinforcementListener.clearReinforcements(pointId);
        }
        
        this.removeBeacon(point);
        this.removeCaptureBossBar(pointId);

        // Cancel any scheduled preparation/capture tasks
        BukkitTask scheduled = this.captureTasks.remove(pointId);
        if (scheduled != null && !scheduled.isCancelled()) {
            scheduled.cancel();
        }

        this.activeSessions.remove(pointId);
        String message = Messages.get("messages.capture.admin_cancelled", Map.of(
            "town", session.getTownName(),
            "zone", point.getName()
        ));
        if (reason != null && !reason.isEmpty()) {
            message = message + " Reason: " + reason;
        }
        broadcastMessage(message);
        if (this.isDynmapEnabled()) {
            this.townUpdater.updateMarker(point);
        }
        return true;
    }

    public boolean setPointType(String pointId, String type) {
        CapturePoint point = capturePoints.get(pointId);
        if (point == null) {
            return false;
        }
        point.setType(type);
        saveCapturePoints();
        return true;
    }

    public boolean forceCapture(String pointId, String townName) {
        CapturePoint point = capturePoints.get(pointId);
        if (point == null) {
            return false;
        }
        Town town = TownyAPI.getInstance().getTown(townName);
        if (town == null) {
            return false;
        }
        
        // Stop any active capture session
        if (activeSessions.containsKey(pointId)) {
            stopCapture(pointId, "Force captured by admin");
        }
        
        // Set the controlling town and capture progress
        point.setControllingTown(town.getName());
        point.setCaptureProgress(100.0);
        point.setLastCaptureTime(System.currentTimeMillis());
        point.setLastCapturingTown(town.getName());
        
        // Set the town's color for Dynmap
        String townColor = getTownColor(townName);
        point.setColor(townColor);
        
        // Update Dynmap if enabled
        if (this.isDynmapEnabled()) {
            this.updateAllMarkers();
        }
        
        // Save changes
        saveCapturePoints();
        
        // Broadcast the capture
        String message = Messages.get("messages.capture.complete", Map.of(
            "town", town.getName(),
            "zone", point.getName()
        ));
        broadcastMessage(message);
        
        return true;
    }

    public boolean resetPoint(String pointId) {
        CapturePoint point = capturePoints.get(pointId);
        if (point == null) {
            return false;
        }

        // Stop any active capture session
        if (activeSessions.containsKey(pointId)) {
            stopCapture(pointId, "Zone reset by admin");
        }

        // Reset all fields to default values
        point.setCapturingTown(null);
        point.setCaptureProgress(0.0);
        point.setControllingTown("");
        point.setLastCapturingTown("");
        point.setLastCaptureTime(0L);
        point.setColor("#8B0000"); // Reset to default dark red color

        // Update Dynmap if enabled
        if (isDynmapEnabled()) {
            updateAllMarkers();
        }

        // Save changes
        saveCapturePoints();

        // Broadcast reset message
        String message = Messages.get("messages.capture.reset", Map.of("zone", point.getName()));
        broadcastMessage(message);

        return true;
    }

    public int resetAllPoints() {
        int count = 0;
        for (String pointId : capturePoints.keySet()) {
            CapturePoint point = capturePoints.get(pointId);
            if (point != null) {
                // Stop any active capture session
                if (activeSessions.containsKey(pointId)) {
                    stopCapture(pointId, "Zone reset by admin");
                }

                // Reset fields but keep controlling town
                point.setCapturingTown(null);
                point.setCaptureProgress(0.0);
                point.setLastCapturingTown("");
                point.setLastCaptureTime(0L);
                point.setColor("#8B0000"); // Reset to default dark red color

                count++;
            }
        }

        // Update Dynmap if enabled
        if (isDynmapEnabled()) {
            updateAllMarkers();
        }

        // Save changes
        saveCapturePoints();

        // Broadcast reset message
        String message = Messages.get("messages.reset-all", Map.of("count", String.valueOf(count)));
        broadcastMessage(message);

        return count;
    }

    public boolean deleteCapturePoint(String pointId) {
        CapturePoint point = capturePoints.get(pointId);
        if (point == null) {
            return false;
        }
        
        // Stop any active capture session first
        if (activeSessions.containsKey(pointId)) {
            stopCapture(pointId, "Zone deleted by admin");
        }
        
        // Clear reinforcements if any
        if (reinforcementListener != null) {
            reinforcementListener.clearReinforcements(pointId);
        }
        
        // Remove beacon if exists
        removeBeacon(point);

        // Cancel any lingering scheduled tasks (preparation/capture)
        BukkitTask scheduled = captureTasks.remove(pointId);
        if (scheduled != null && !scheduled.isCancelled()) {
            scheduled.cancel();
        }

        // Ensure boss bar is removed if present
        removeCaptureBossBar(pointId);
        
        // Clean up boundary visualization tasks for this zone
        boundaryTasks.entrySet().removeIf(entry -> {
            String key = entry.getKey();
            if (key.endsWith("_" + pointId)) {
                BukkitTask task = entry.getValue();
                if (task != null && !task.isCancelled()) {
                    task.cancel();
                }
                return true;
            }
            return false;
        });
        
        // Remove Dynmap markers if enabled
        if (isDynmapEnabled() && townUpdater != null) {
            townUpdater.removeMarkers(pointId);
        }
        
        // Now remove the point from all maps
        capturePoints.remove(pointId);
        captureTasks.remove(pointId);
        captureBossBars.remove(pointId);
        lastHourlyRewardTimes.remove(pointId);
        lastWeeklyResetEpochDays.remove(pointId);
        
        // Backup zone config file (safe delete)
        if (zoneConfigManager != null) {
            zoneConfigManager.deleteZoneConfig(pointId, true);
            getLogger().info("Backed up config for deleted zone: " + pointId);
        }
        
        // Save the updated capture points
        saveCapturePoints();
        
        getLogger().info("Capture point '" + pointId + "' has been deleted and all associated activities stopped.");
        
        return true;
    }

    public void createCapturePoint(String id, String type, Location location, int chunkRadius, double reward) {
        if (capturePoints.size() >= config.getInt("settings.max-capture-points", 50)) {
            getLogger().warning("Maximum number of capture points reached!");
            return;
        }
        
        int minRadius = config.getInt("settings.capture-point.min-radius", 1);
        int maxRadius = config.getInt("settings.capture-point.max-radius", 10);
        if (chunkRadius < minRadius) {
            chunkRadius = minRadius;
        } else if (chunkRadius > maxRadius) {
            chunkRadius = maxRadius;
        }
        
        double minReward = config.getDouble("settings.capture-point.min-reward", 100.0);
        double maxReward = config.getDouble("settings.capture-point.max-reward", 10000.0);
        if (reward < minReward) {
            reward = minReward;
        } else if (reward > maxReward) {
            reward = maxReward;
        }
        
        CapturePoint point = new CapturePoint(id, type, location, chunkRadius, reward);
        int minPlayers = config.getInt("settings.capture-point.min-players", 1);
        int maxPlayers = config.getInt("settings.capture-point.max-players", 10);
        point.setMinPlayers(minPlayers);
        point.setMaxPlayers(maxPlayers);
        capturePoints.put(id, point);
        saveCapturePoints();
        
        // Generate zone config from defaults
        if (zoneConfigManager != null) {
            zoneConfigManager.generateZoneConfig(id);
            zoneConfigManager.setZoneSetting(id, "rewards.base-reward", reward);
            getLogger().info("Generated config file for zone: " + id);
        }
        
        // Update Dynmap markers immediately for the new point
        if (isDynmapEnabled() && townUpdater != null) {
            townUpdater.updateMarker(point);
        }
        
        // Auto-show boundaries for all online players if enabled
        if (config.getBoolean("settings.auto-show-boundaries", true)) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                startBoundaryVisualization(player, point);
            }
        }
    }

    public void handleNewDay() {
        this.getLogger().info("New day event triggered");
        distributeRewards();
    }

    public void distributeRewards() {
        for (CapturePoint point : this.capturePoints.values()) {
            if (!isDailyReward(point.getId())) {
                continue;
            }
            if (point.getControllingTown().isEmpty()) continue;
            TownyAPI api = TownyAPI.getInstance();
            Town town = api.getTown(point.getControllingTown());
            if (town == null) {
                point.setControllingTown("");
                if (!this.isDynmapEnabled()) continue;
                this.townUpdater.updateMarker(point);
                continue;
            }
            try {
                double baseReward = getBaseReward(point);
                town.getAccount().deposit(baseReward, "Reward for controlling " + point.getName());
                this.getLogger().info("Gave " + baseReward + " to " + town.getName() + " for controlling " + point.getName());
                
                // Track reward statistics
                if (statisticsManager != null) {
                    statisticsManager.onRewardDistributed(
                        point.getId(), 
                        point.getName(), 
                        town.getName(), 
                        baseReward
                    );
                }
                
                // Send Discord webhook notification
                if (discordWebhook != null) {
                    discordWebhook.sendRewardsDistributed(
                        point.getId(), 
                        point.getName(), 
                        town.getName(), 
                        baseReward, 
                        "daily"
                    );
                }
                
                String message = Messages.get("messages.reward.distributed", Map.of(
                    "town", town.getName(),
                    "reward", String.valueOf(baseReward),
                    "zone", point.getName()
                ));
                broadcastMessage(message);
            }
            catch (Exception e) {
                this.getLogger().warning("Failed to give reward to " + town.getName());
                e.printStackTrace();
            }
        }
    }

    public void distributeHourlyRewards() {
        long now = System.currentTimeMillis();
        boolean debug = this.config.getBoolean("settings.debug-mode", false);
        for (CapturePoint point : this.capturePoints.values()) {
            String pointId = point.getId();
            if (!isHourlyReward(pointId)) {
                lastHourlyRewardTimes.remove(pointId);
                continue;
            }
            if (point.getControllingTown().isEmpty()) {
                lastHourlyRewardTimes.remove(pointId);
                continue;
            }

            int intervalSeconds = getHourlyIntervalSeconds(pointId);
            if (intervalSeconds < 1) {
                intervalSeconds = 1;
            }

            Long lastRewardTime = lastHourlyRewardTimes.get(pointId);
            if (lastRewardTime == null) {
                lastHourlyRewardTimes.put(pointId, now);
                continue;
            }

            long elapsedMs = now - lastRewardTime;
            long intervalMs = intervalSeconds * 1000L;
            if (elapsedMs < intervalMs) {
                continue;
            }

            lastHourlyRewardTimes.put(pointId, now);

            TownyAPI api = TownyAPI.getInstance();
            if (api == null) {
                this.getLogger().warning("TownyAPI is null!");
                continue;
            }
            Town town = api.getTown(point.getControllingTown());
            if (town == null) {
                this.getLogger().warning("Town '" + point.getControllingTown() + "' not found, resetting point");
                point.setControllingTown("");
                if (!this.isDynmapEnabled()) continue;
                this.townUpdater.updateMarker(point);
                continue;
            }
            try {
                double hourlyReward = calculateHourlyReward(point);
                if (debug) {
                    this.getLogger().info("Depositing " + hourlyReward + " to town " + town.getName());
                }
                town.getAccount().deposit(hourlyReward, "Hourly reward for controlling " + point.getName());
                if (debug) {
                    this.getLogger().info("Gave " + hourlyReward + " to " + town.getName() + " for controlling " + point.getName() + " (hourly)");
                }
                
                // Track reward statistics
                if (statisticsManager != null) {
                    statisticsManager.onRewardDistributed(
                        point.getId(), 
                        point.getName(), 
                        town.getName(), 
                        hourlyReward
                    );
                }
                
                // Send Discord webhook notification
                if (discordWebhook != null) {
                    discordWebhook.sendRewardsDistributed(
                        point.getId(), 
                        point.getName(), 
                        town.getName(), 
                        hourlyReward, 
                        "hourly"
                    );
                }
                
                String message = Messages.get("messages.reward.hourly_distributed", Map.of(
                    "town", town.getName(),
                    "reward", String.format("%.1f", hourlyReward),
                    "zone", point.getName()
                ));
                broadcastMessage(message);
            }
            catch (Exception e) {
                this.getLogger().warning("Failed to give hourly reward to " + town.getName() + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private double calculateHourlyReward(CapturePoint point) {
        // Use zone-specific config, fallback to zone defaults
        String mode = "STATIC";
        if (zoneConfigManager != null) {
            Object modeValue = zoneConfigManager.getZoneSetting(point.getId(), "rewards.hourly-mode", "STATIC");
            if (modeValue != null) {
                mode = modeValue.toString();
            }
        }
        double base = getBaseReward(point) / 24.0;
        if ("DYNAMIC".equalsIgnoreCase(mode)) {
            Object minValue = zoneConfigManager != null
                ? zoneConfigManager.getZoneSetting(point.getId(), "rewards.hourly-dynamic.min", base)
                : base;
            Object maxValue = zoneConfigManager != null
                ? zoneConfigManager.getZoneSetting(point.getId(), "rewards.hourly-dynamic.max", base)
                : base;
            double min = getDoubleSetting(minValue, base);
            double max = getDoubleSetting(maxValue, base);
            if (max < min) {
                max = min;
            }
            return min + (Math.random() * (max - min));
        }
        return base;
    }

    private String getRewardType(String pointId) {
        if (zoneConfigManager == null || pointId == null) {
            return "daily";
        }
        String value = zoneConfigManager.getString(pointId, "rewards.reward-type", "daily");
        if (value == null) {
            return "daily";
        }
        String normalized = value.trim();
        if (!"daily".equalsIgnoreCase(normalized) && !"hourly".equalsIgnoreCase(normalized)) {
            return "daily";
        }
        return normalized;
    }

    private boolean isHourlyReward(String pointId) {
        return "hourly".equalsIgnoreCase(getRewardType(pointId));
    }

    private boolean isDailyReward(String pointId) {
        return "daily".equalsIgnoreCase(getRewardType(pointId));
    }

    private int getHourlyIntervalSeconds(String pointId) {
        if (zoneConfigManager == null || pointId == null) {
            return 3600;
        }
        int interval = zoneConfigManager.getInt(pointId, "rewards.hourly-interval", 3600);
        return Math.max(1, interval);
    }

    public double getBaseReward(CapturePoint point) {
        if (point == null) {
            return 0.0;
        }
        double fallback = point.getReward();
        if (zoneConfigManager == null) {
            return Math.max(0.0, fallback);
        }
        Object value = zoneConfigManager.getZoneSetting(point.getId(), "rewards.base-reward", fallback);
        double reward = getDoubleSetting(value, fallback);
        return Math.max(0.0, reward);
    }

    private double getDoubleSetting(Object value, double fallback) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value != null) {
            try {
                return Double.parseDouble(value.toString());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    public String getPointTypeName(String typeKey) {
        return this.pointTypes.getOrDefault(typeKey, "Strategic Point");
    }
    
    public DiscordWebhook getDiscordWebhook() {
        return this.discordWebhook;
    }

    public String colorize(String message) {
        if (message == null) {
            return "";
        }
        message = message.replace("&", "\u00a7");
        Pattern pattern = Pattern.compile("\\{#([a-zA-Z0-9]+)>(.*?)\\}");
        Matcher matcher = pattern.matcher(message);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String colorName = matcher.group(1);
            String text = matcher.group(2);
            String colorCode = this.getColorCodeFromName(colorName);
            if (colorCode != null) {
                matcher.appendReplacement(sb, colorCode + text + "\u00a7r");
                continue;
            }
            matcher.appendReplacement(sb, "\u00a7cInvalid Color: " + colorName + "\u00a7r" + text);
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private String getColorCodeFromName(String colorName) {
        if (colorName == null) {
            return null;
        }
        switch (colorName = colorName.toLowerCase()) {
            case "black": {
                return "\u00a70";
            }
            case "darkblue": {
                return "\u00a71";
            }
            case "darkgreen": {
                return "\u00a72";
            }
            case "darkaqua": 
            case "darkcyan": {
                return "\u00a73";
            }
            case "darkred": {
                return "\u00a74";
            }
            case "darkpurple": 
            case "darkmagenta": {
                return "\u00a75";
            }
            case "gold": {
                return "\u00a76";
            }
            case "darkyellow": {
                return "\u00a76";
            }
            case "gray": {
                return "\u00a77";
            }
            case "grey": {
                return "\u00a77";
            }
            case "darkgray": {
                return "\u00a78";
            }
            case "darkgrey": {
                return "\u00a78";
            }
            case "blue": {
                return "\u00a79";
            }
            case "green": {
                return "\u00a7a";
            }
            case "aqua": {
                return "\u00a7b";
            }
            case "cyan": {
                return "\u00a7b";
            }
            case "red": {
                return "\u00a7c";
            }
            case "lightpurple": {
                return "\u00a7d";
            }
            case "magenta": {
                return "\u00a7d";
            }
            case "yellow": {
                return "\u00a7e";
            }
            case "white": {
                return "\u00a7f";
            }
        }
        if (colorName.startsWith("#") && colorName.length() == 7) {
            return "\u00a7x" + colorName.charAt(1) + colorName.charAt(1) + colorName.charAt(2) + colorName.charAt(2) + colorName.charAt(3) + colorName.charAt(3) + colorName.charAt(4) + colorName.charAt(4) + colorName.charAt(5) + colorName.charAt(5) + colorName.charAt(6) + colorName.charAt(6);
        }
        return null;
    }

    public Map<String, CapturePoint> getCapturePoints() {
        return this.capturePoints;
    }

    public Map<String, CaptureSession> getActiveSessions() {
        return this.activeSessions;
    }

    public boolean isDynmapEnabled() {
        return !mapProviders.isEmpty();
    }

    public boolean isWorldGuardEnabled() {
        return this.worldGuardEnabled;
    }

    public Map<String, String> getPointTypes() {
        return this.pointTypes;
    }

    public void cancelCapture(String pointId, String reason) {
        if (!this.activeSessions.containsKey(pointId)) {
            return;
        }
        CaptureSession session = this.activeSessions.get(pointId);
        CapturePoint point = this.capturePoints.get(pointId);
        this.removeBeacon(point);
        this.removeCaptureBossBar(pointId);
        this.activeSessions.remove(pointId);
        // Cancel the capture task
        BukkitTask task = this.captureTasks.remove(pointId);
        if (task != null) {
            task.cancel();
        }
        if (this.reinforcementListener != null) {
            this.reinforcementListener.clearReinforcements(pointId);
        }
        // Reset capturing town
        point.setCapturingTown(null);
        String message;
        if (reason != null && !reason.isEmpty()) {
            message = Messages.get("messages.capture.broadcast-cancelled-reason", Map.of(
                "town", session.getTownName(),
                "zone", point.getName(),
                "reason", getLocalizedReason(reason)
            ));
        } else {
            message = Messages.get("messages.capture.broadcast-cancelled", Map.of(
                "town", session.getTownName(),
                "zone", point.getName()
            ));
        }
        broadcastMessage(message);
        if (this.isDynmapEnabled()) {
            this.townUpdater.updateMarker(point);
        }
    }

    public CapturePoint getCapturePoint(String id) {
        if (id == null || id.trim().isEmpty()) {
            return null;
        }
        return this.capturePoints.get(id.trim());
    }

    public CaptureSession getActiveSession(String pointId) {
        if (pointId == null || pointId.trim().isEmpty()) {
            return null;
        }
        return this.activeSessions.get(pointId.trim());
    }

    public boolean isPointActive(String pointId) {
        if (pointId == null || pointId.trim().isEmpty()) {
            return false;
        }
        return this.activeSessions.containsKey(pointId.trim());
    }

    public void stopCapture(String pointId) {
        if (pointId == null || pointId.trim().isEmpty()) {
            return;
        }
        CaptureSession session = this.activeSessions.remove(pointId.trim());
        if (session != null) {
            session.stop();
        }
    }

    public void removeCapturePoint(String pointId) {
        if (pointId == null || pointId.trim().isEmpty()) {
            return;
        }
        this.stopCapture(pointId.trim());
        this.capturePoints.remove(pointId.trim());
        this.lastHourlyRewardTimes.remove(pointId.trim());
    }

    public void logCaptureAttempt(String pointId) {
        this.captureAttempts.merge(pointId, 1, Integer::sum);
        this.getLogger().info("Capture attempt recorded for point: " + pointId);
    }

    public void logSuccessfulCapture(String pointId) {
        this.successfulCaptures.merge(pointId, 1, Integer::sum);
        this.getLogger().info("Successful capture recorded for point: " + pointId);
    }

    public void logError(String pointId, String error) {
        long currentTime = System.currentTimeMillis();
        Long lastError = this.lastErrorTime.get(pointId);
        long throttleMs = this.config.getLong("settings.error-throttle-ms", 60000L);
        if (lastError == null || currentTime - lastError >= throttleMs) {
            if (this.config.getBoolean("settings.debug-mode", false)) {
                this.getLogger().severe("Error for point " + pointId + ": " + error);
            }
            this.lastErrorTime.put(pointId, currentTime);
        }
    }

    public Map<String, Integer> getCaptureAttempts() {
        return new HashMap<String, Integer>(this.captureAttempts);
    }

    public Map<String, Integer> getSuccessfulCaptures() {
        return new HashMap<String, Integer>(this.successfulCaptures);
    }

    public void resetPointStatistics(String pointId) {
        this.captureAttempts.remove(pointId);
        this.successfulCaptures.remove(pointId);
        this.lastErrorTime.remove(pointId);
        this.getLogger().info("Statistics reset for point: " + pointId);
    }

    public void setPointActive(String pointId, boolean active) {
        CapturePoint point = this.getCapturePoint(pointId);
        if (point != null) {
            point.setActive(active);
            this.getLogger().info("Point " + pointId + " set to " + (active ? "active" : "inactive"));
        }
    }

    public void setPointPlayerLimits(String pointId, int minPlayers, int maxPlayers) {
        CapturePoint point = this.getCapturePoint(pointId);
        if (point != null) {
            point.setMinPlayers(minPlayers);
            point.setMaxPlayers(maxPlayers);
            this.getLogger().info("Player limits set for point " + pointId + ": min=" + minPlayers + ", max=" + maxPlayers);
        }
    }

    private String getTownColor(String townName) {
        Town town = TownyAPI.getInstance().getTown(townName);
        if (town == null) {
            return "#8B0000"; // Default dark red color
        }
        
        java.awt.Color color = town.getMapColor();
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }

    private void removeCaptureBossBar(String pointId) {
        BossBar bossBar = captureBossBars.remove(pointId);
        if (bossBar != null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                bossBar.removePlayer(player);
            }
            bossBar.removeAll();
        }
    }

    private String formatTime(int seconds) {
        int minutes = seconds / 60;
        int remainingSeconds = seconds % 60;
        return String.format("%d:%02d", minutes, remainingSeconds);
    }
    
    // Sound System Methods
    public void playCaptureSound(String soundEvent) {
        if (!config.getBoolean("sounds.enabled", true)) {
            return;
        }
        
        String soundName = config.getString("sounds." + soundEvent + ".sound", "entity.player.levelup");
        float volume = (float) config.getDouble("sounds." + soundEvent + ".volume", 1.0);
        float pitch = (float) config.getDouble("sounds." + soundEvent + ".pitch", 1.0);
        
        for (Player player : Bukkit.getOnlinePlayers()) {
            // Skip if player has disabled notifications
            if (disabledNotifications.getOrDefault(player.getUniqueId(), false)) {
                continue;
            }
            try {
                org.bukkit.Sound sound = org.bukkit.Sound.valueOf(soundName);
                player.playSound(player.getLocation(), sound, volume, pitch);
            } catch (Exception e) {
                getLogger().warning("Invalid sound: " + soundName);
            }
        }
    }
    
    public void playCaptureSoundAtLocation(String soundEvent, Location location) {
        if (!config.getBoolean("sounds.enabled", true)) {
            return;
        }
        
        String soundName = config.getString("sounds." + soundEvent + ".sound", "entity.player.levelup");
        float volume = (float) config.getDouble("sounds." + soundEvent + ".volume", 1.0);
        float pitch = (float) config.getDouble("sounds." + soundEvent + ".pitch", 1.0);
        
        // Only play for players who haven't disabled notifications
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld() == location.getWorld() && 
                player.getLocation().distance(location) < 100 &&
                !disabledNotifications.getOrDefault(player.getUniqueId(), false)) {
                try {
                    org.bukkit.Sound sound = org.bukkit.Sound.valueOf(soundName);
                    player.playSound(location, sound, volume, pitch);
                } catch (Exception e) {
                    getLogger().warning("Invalid sound: " + soundName);
                }
            }
        }
    }

    private int getActiveCapturesForTown(String townName) {
        return (int)this.capturePoints.values().stream()
            .filter(point -> point.getCapturingTown() != null && point.getCapturingTown().equals(townName))
            .count();
    }

    private void broadcastMessage(String message) {
        if (!config.getBoolean("settings.show-chat-messages", true)) {
            return;
        }
        if (message == null || message.isEmpty()) {
            return;
        }
        String formatted = colorize(message);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (disabledNotifications.getOrDefault(player.getUniqueId(), false)) {
                continue;
            }
            player.sendMessage(formatted);
        }
    }

    public void toggleChatMessages(boolean enabled) {
        config.set("settings.show-chat-messages", enabled);
        saveConfig();
        String status = enabled ? "enabled" : "disabled";
        getLogger().info("Capture chat messages have been " + status + " globally");
        if (enabled) {
            broadcastMessage(colorize("&6[TownyCapture] &eCapture messages have been enabled globally"));
        }
    }

    private class PlayerEventListener
    implements Listener {
        @EventHandler
        public void onPlayerJoin(PlayerJoinEvent event) {
            Player player = event.getPlayer();
            if (!TownyCapture.this.hiddenBossBars.getOrDefault(player.getUniqueId(), false).booleanValue() &&
                !TownyCapture.this.disabledNotifications.getOrDefault(player.getUniqueId(), false)) {
                for (BossBar bossBar : TownyCapture.this.captureBossBars.values()) {
                    bossBar.addPlayer(player);
                }
            }
            
            // Auto-show boundaries for new player if enabled
            if (TownyCapture.this.config.getBoolean("settings.auto-show-boundaries", true)) {
                TownyCapture.this.autoShowBoundariesForPlayer(player);
            }
            
            // Show update notification to admins
            if (TownyCapture.this.getConfig().getBoolean("settings.check-for-updates", true) && 
                player.hasPermission("capturepoints.admin.update-notify") &&
                TownyCapture.this.updateChecker != null) {
                TownyCapture.this.updateChecker.showUpdateNotification(player);
            }
        }
    }

    // Test method: Start a capture with custom duration for testing
    public boolean startTestCapture(Player player, String pointId, int preparationSeconds, int captureSeconds) {
        CapturePoint point = capturePoints.get(pointId);
        if (point == null) {
            return false;
        }

        // Check if player is in a town
        Town town = TownyAPI.getInstance().getTown(player);
        if (town == null) {
            return false;
        }

        // Check if player is within chunk radius
        if (!isWithinChunkRadius(player.getLocation(), point.getLocation(), point.getChunkRadius())) {
            return false;
        }

        // Create beacon at capture point
        createBeacon(point.getLocation());

        // Create capture session with custom duration
        CaptureSession session = new CaptureSession(point, town, player, preparationSeconds / 60, captureSeconds / 60);
        session.getPlayers().add(player);
        activeSessions.put(pointId, session);

        // Create boss bar
        BossBar bossBar = Bukkit.createBossBar(
            colorize("&e[TEST] Capturing: " + point.getName()),
            BarColor.BLUE,
            BarStyle.SOLID
        );
        captureBossBars.put(pointId, bossBar);
        
        // Show boss bar to all players for testing
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!disabledNotifications.getOrDefault(online.getUniqueId(), false)) {
                bossBar.addPlayer(online);
            }
        }

        // Short preparation phase
        BukkitTask prepTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (!session.isActive()) {
                return;
            }

            session.decrementPreparationTime();
            int timeLeft = session.getRemainingPreparationTime();
            
            if (timeLeft <= 0) {
                startCapturePhase(point, town, player);
                return;
            }
        }, 20L, 20L);

        captureTasks.put(pointId, prepTask);
        return true;
    }

    private String getLocalizedReason(String reason) {
        switch (reason) {
            case "Player moved too far from capture zone":
                return Messages.get("reasons.moved-too-far");
            case "All participants disconnected":
                return Messages.get("reasons.all-disconnected");
            case "Session timeout":
                return Messages.get("reasons.session-timeout");
            default:
                return reason; // Return original reason if not recognized
        }
    }
    
    /**
     * Get the statistics manager
     */
    public StatisticsManager getStatisticsManager() {
        return statisticsManager;
    }
    
    /**
     * Get the statistics GUI
     */
    public StatisticsGUI getStatisticsGUI() {
        return statisticsGUI;
    }
    
    /**
     * Get the zone configuration manager
     */
    public ZoneConfigManager getZoneConfigManager() {
        return zoneConfigManager;
    }
    
    /**
     * Get the shop manager
     */
    public ShopManager getShopManager() {
        return shopManager;
    }
    
    /**
     * Get the shop listener
     */
    public ShopListener getShopListener() {
        return shopListener;
    }
}
