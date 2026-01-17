package com.logichh.townycapture;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.logging.Logger;

/**
 * Manages per-zone configuration files.
 * Each zone has its own zones/{zone_id}_config.yml file with complete settings.
 * Falls back to zone-template.yml defaults if zone config doesn't exist.
 */
public class ZoneConfigManager {
    
    private final TownyCapture plugin;
    private final Logger logger;
    private final File legacyZoneConfigsFolder;
    private final File zoneConfigsFolder;
    private final File zoneTemplateFile;
    private final Map<String, FileConfiguration> zoneConfigs;
    private FileConfiguration zoneDefaults;
    
    public ZoneConfigManager(TownyCapture plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.legacyZoneConfigsFolder = plugin.getDataFolder();
        this.zoneConfigsFolder = new File(plugin.getDataFolder(), "zones");
        this.zoneTemplateFile = new File(plugin.getDataFolder(), "zone-template.yml");
        if (!zoneConfigsFolder.exists() && !zoneConfigsFolder.mkdirs()) {
            logger.warning("Failed to create zones config folder: " + zoneConfigsFolder.getAbsolutePath());
        }
        this.zoneConfigs = new HashMap<>();
        loadZoneDefaults();
    }
    
    /**
     * Load all zone configurations
     */
    public void loadAllZoneConfigs() {
        zoneConfigs.clear();
        
        // Load config for each existing zone
        for (String zoneId : plugin.getCapturePoints().keySet()) {
            loadZoneConfig(zoneId);
        }
        
        logger.info("Loaded " + zoneConfigs.size() + " zone configurations.");
    }

    public void reloadDefaults() {
        loadZoneDefaults();
    }
    
    /**
     * Load configuration for a specific zone
     */
    public FileConfiguration loadZoneConfig(String zoneId) {
        File zoneConfigFile = getZoneConfigFile(zoneId);
        
        if (!zoneConfigFile.exists()) {
            if (migrateLegacyZoneConfig(zoneId)) {
                zoneConfigFile = getZoneConfigFile(zoneId);
            } else {
                logger.info("Zone config not found for '" + zoneId + "', generating from defaults...");
                generateZoneConfig(zoneId);
            }
        }
        
        try {
            FileConfiguration zoneConfig = YamlConfiguration.loadConfiguration(zoneConfigFile);
            zoneConfigs.put(zoneId, zoneConfig);
            
            if (plugin.getConfig().getBoolean("settings.debug-mode", false)) {
                logger.info("Loaded config for zone: " + zoneId);
            }
            
            return zoneConfig;
        } catch (Exception e) {
            logger.severe("Failed to load config for zone '" + zoneId + "': " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Generate a new zone config file from the zone template
     */
    public boolean generateZoneConfig(String zoneId) {
        ensureZoneConfigsFolder();
        File zoneConfigFile = getZoneConfigFile(zoneId);
        
        try {
            FileConfiguration defaults = getZoneDefaults();
            if (zoneTemplateFile.exists()) {
                try {
                    Files.copy(zoneTemplateFile.toPath(), zoneConfigFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    FileConfiguration zoneConfig = YamlConfiguration.loadConfiguration(zoneConfigFile);
                    zoneConfigs.put(zoneId, zoneConfig);
                    logger.info("Generated config file: " + zoneId + "_config.yml");
                    return true;
                } catch (IOException e) {
                    logger.warning("Failed to copy zone template for '" + zoneId + "': " + e.getMessage());
                }
            }

            if (defaults == null) {
                logger.severe("Zone template defaults not found! Cannot generate zone config.");
                return false;
            }

            FileConfiguration zoneConfig = new YamlConfiguration();
            // Deep copy all settings (comments are not preserved in this fallback path)
            copySection(defaults, zoneConfig);
            zoneConfig.save(zoneConfigFile);
            zoneConfigs.put(zoneId, zoneConfig);

            logger.info("Generated config file: " + zoneId + "_config.yml");
            return true;
        } catch (IOException e) {
            logger.severe("Failed to generate config for zone '" + zoneId + "': " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Deep copy a configuration section
     */
    private void copySection(ConfigurationSection source, ConfigurationSection target) {
        for (String key : source.getKeys(false)) {
            if (source.isConfigurationSection(key)) {
                ConfigurationSection subSection = target.createSection(key);
                copySection(source.getConfigurationSection(key), subSection);
            } else {
                target.set(key, source.get(key));
            }
        }
    }
    
    /**
     * Get configuration for a specific zone
     */
    public FileConfiguration getZoneConfig(String zoneId) {
        FileConfiguration config = zoneConfigs.get(zoneId);
        
        if (config == null) {
            // Try to load it
            config = loadZoneConfig(zoneId);
        }
        
        // Final fallback to zone template defaults
        if (config == null && zoneDefaults != null) {
            logger.warning("Using zone defaults for zone: " + zoneId);
            return zoneDefaults;
        }
        if (config == null) {
            logger.warning("Zone defaults not available for zone: " + zoneId);
            return plugin.getConfig();
        }
        
        return config;
    }
    
    /**
     * Get a setting for a specific zone with fallback
     */
    public Object getZoneSetting(String zoneId, String path, Object defaultValue) {
        FileConfiguration zoneConfig = getZoneConfig(zoneId);
        
        if (zoneConfig != null && zoneConfig.contains(path)) {
            return zoneConfig.get(path);
        }
        
        // Fallback to zone defaults template
        if (zoneDefaults != null && zoneDefaults.contains(path)) {
            return zoneDefaults.get(path);
        }

        // Legacy fallback to zone-defaults in main config
        String legacyPath = "zone-defaults." + path;
        if (plugin.getConfig().contains(legacyPath)) {
            return plugin.getConfig().get(legacyPath);
        }
        
        return defaultValue;
    }
    
    /**
     * Get string setting
     */
    public String getString(String zoneId, String path, String defaultValue) {
        Object value = getZoneSetting(zoneId, path, defaultValue);
        return value != null ? value.toString() : defaultValue;
    }
    
    /**
     * Get int setting
     */
    public int getInt(String zoneId, String path, int defaultValue) {
        Object value = getZoneSetting(zoneId, path, defaultValue);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return defaultValue;
    }
    
    /**
     * Get double setting
     */
    public double getDouble(String zoneId, String path, double defaultValue) {
        Object value = getZoneSetting(zoneId, path, defaultValue);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return defaultValue;
    }

    public long getLong(String zoneId, String path, long defaultValue) {
        Object value = getZoneSetting(zoneId, path, defaultValue);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return defaultValue;
    }
    
    /**
     * Get boolean setting
     */
    public boolean getBoolean(String zoneId, String path, boolean defaultValue) {
        Object value = getZoneSetting(zoneId, path, defaultValue);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return defaultValue;
    }
    
    /**
     * Get list setting
     */
    public List<?> getList(String zoneId, String path, List<?> defaultValue) {
        Object value = getZoneSetting(zoneId, path, defaultValue);
        if (value instanceof List) {
            return (List<?>) value;
        }
        return defaultValue;
    }
    
    /**
     * Get configuration section
     */
    public ConfigurationSection getSection(String zoneId, String path) {
        FileConfiguration zoneConfig = getZoneConfig(zoneId);
        
        if (zoneConfig != null && zoneConfig.contains(path)) {
            return zoneConfig.getConfigurationSection(path);
        }
        
        // Fallback to zone defaults template
        if (zoneDefaults != null) {
            return zoneDefaults.getConfigurationSection(path);
        }

        // Legacy fallback to zone-defaults
        String legacyPath = "zone-defaults." + path;
        return plugin.getConfig().getConfigurationSection(legacyPath);
    }
    
    /**
     * Set a value in zone config and save
     */
    public boolean setZoneSetting(String zoneId, String path, Object value) {
        FileConfiguration zoneConfig = getZoneConfig(zoneId);
        if (zoneConfig == null) {
            return false;
        }
        
        zoneConfig.set(path, value);
        return saveZoneConfig(zoneId);
    }
    
    /**
     * Save a zone's configuration file
     */
    public boolean saveZoneConfig(String zoneId) {
        FileConfiguration zoneConfig = zoneConfigs.get(zoneId);
        if (zoneConfig == null) {
            return false;
        }
        
        ensureZoneConfigsFolder();
        File zoneConfigFile = getZoneConfigFile(zoneId);
        
        try {
            zoneConfig.save(zoneConfigFile);
            
            if (plugin.getConfig().getBoolean("settings.debug-mode", false)) {
                logger.info("Saved config for zone: " + zoneId);
            }
            
            return true;
        } catch (IOException e) {
            logger.severe("Failed to save config for zone '" + zoneId + "': " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Delete a zone's configuration file (with backup)
     */
    public boolean deleteZoneConfig(String zoneId, boolean createBackup) {
        File zoneConfigFile = getZoneConfigFile(zoneId);
        
        if (!zoneConfigFile.exists()) {
            return true; // Already doesn't exist
        }
        
        try {
            if (createBackup) {
                File backupFile = new File(zoneConfigsFolder, zoneId + "_config.yml.backup");
                if (backupFile.exists()) {
                    backupFile.delete();
                }
                zoneConfigFile.renameTo(backupFile);
                logger.info("Backed up config to: " + backupFile.getName());
            } else {
                zoneConfigFile.delete();
                logger.info("Deleted config file: " + zoneConfigFile.getName());
            }
            
            zoneConfigs.remove(zoneId);
            return true;
            
        } catch (Exception e) {
            logger.severe("Failed to delete config for zone '" + zoneId + "': " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Reload a zone's configuration from disk
     */
    public boolean reloadZoneConfig(String zoneId) {
        zoneConfigs.remove(zoneId);
        FileConfiguration config = loadZoneConfig(zoneId);
        return config != null;
    }
    
    /**
     * Get all loaded zone IDs
     */
    public Set<String> getLoadedZones() {
        return new HashSet<>(zoneConfigs.keySet());
    }
    
    /**
     * Check if zone has its own config file
     */
    public boolean hasZoneConfig(String zoneId) {
        if (getZoneConfigFile(zoneId).exists()) {
            return true;
        }
        return getLegacyZoneConfigFile(zoneId).exists();
    }
    
    /**
     * Migrate existing zones to new config system
     * Called on first load after update
     */
    public void migrateExistingZones() {
        logger.info("Checking for zones without individual configs...");
        
        int moved = 0;
        int generated = 0;
        for (String zoneId : plugin.getCapturePoints().keySet()) {
            if (migrateLegacyZoneConfig(zoneId)) {
                moved++;
                continue;
            }
            if (!getZoneConfigFile(zoneId).exists()) {
                logger.info("Generating config for zone: " + zoneId);
                if (generateZoneConfig(zoneId)) {
                    generated++;
                }
            }
        }
        
        if (moved > 0) {
            logger.info("Moved " + moved + " legacy zone configs into the zones folder.");
        }
        if (generated > 0) {
            logger.info("Generated " + generated + " zone configs from defaults.");
        }
        if (moved == 0 && generated == 0) {
            logger.info("All zones already have individual configs.");
        }
    }
    
    /**
     * Get the default value for a setting from the zone template
     */
    public Object getZoneDefault(String path) {
        if (zoneDefaults != null && zoneDefaults.contains(path)) {
            return zoneDefaults.get(path);
        }
        String legacyPath = "zone-defaults." + path;
        return plugin.getConfig().get(legacyPath);
    }

    public boolean getDefaultBoolean(String path, boolean defaultValue) {
        Object value = getZoneDefault(path);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof String) {
            return Boolean.parseBoolean((String) value);
        }
        return defaultValue;
    }

    public int getDefaultInt(String path, int defaultValue) {
        Object value = getZoneDefault(path);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return defaultValue;
    }

    public long getDefaultLong(String path, long defaultValue) {
        Object value = getZoneDefault(path);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return defaultValue;
    }

    public double getDefaultDouble(String path, double defaultValue) {
        Object value = getZoneDefault(path);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return defaultValue;
    }

    public List<?> getDefaultList(String path, List<?> defaultValue) {
        Object value = getZoneDefault(path);
        if (value instanceof List) {
            return (List<?>) value;
        }
        return defaultValue;
    }
    
    private File getZoneConfigFile(String zoneId) {
        return new File(zoneConfigsFolder, zoneId + "_config.yml");
    }
    
    private File getLegacyZoneConfigFile(String zoneId) {
        return new File(legacyZoneConfigsFolder, zoneId + "_config.yml");
    }
    
    private void ensureZoneConfigsFolder() {
        if (!zoneConfigsFolder.exists()) {
            zoneConfigsFolder.mkdirs();
        }
    }

    private FileConfiguration getZoneDefaults() {
        if (zoneDefaults == null) {
            loadZoneDefaults();
        }
        return zoneDefaults;
    }

    private void loadZoneDefaults() {
        if (!zoneTemplateFile.exists()) {
            if (!migrateZoneDefaultsFromConfig()) {
                try {
                    plugin.saveResource("zone-template.yml", false);
                } catch (IllegalArgumentException e) {
                    logger.warning("zone-template.yml not found in plugin jar.");
                }
            }
        }

        if (zoneTemplateFile.exists()) {
            zoneDefaults = YamlConfiguration.loadConfiguration(zoneTemplateFile);
        } else {
            zoneDefaults = null;
        }
    }

    private boolean migrateZoneDefaultsFromConfig() {
        ConfigurationSection legacyDefaults = plugin.getConfig().getConfigurationSection("zone-defaults");
        if (legacyDefaults == null) {
            return false;
        }
        try {
            FileConfiguration template = new YamlConfiguration();
            template.options().header(
                "Zone defaults template (migrated from config.yml)\n" +
                "Edit this file to change defaults for new zones."
            );
            copySection(legacyDefaults, template);
            template.save(zoneTemplateFile);
            logger.info("Migrated zone-defaults from config.yml into zone-template.yml.");
            return true;
        } catch (IOException e) {
            logger.warning("Failed to migrate zone-defaults into zone-template.yml: " + e.getMessage());
            return false;
        }
    }
    
    private boolean migrateLegacyZoneConfig(String zoneId) {
        File legacyFile = getLegacyZoneConfigFile(zoneId);
        File newFile = getZoneConfigFile(zoneId);
        
        if (!legacyFile.exists() || newFile.exists()) {
            return false;
        }
        
        if (!zoneConfigsFolder.exists() && !zoneConfigsFolder.mkdirs()) {
            logger.warning("Failed to create zones config folder: " + zoneConfigsFolder.getAbsolutePath());
            return false;
        }
        
        try {
            Files.move(legacyFile.toPath(), newFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            logger.info("Moved legacy zone config for '" + zoneId + "' into the zones folder.");
            return true;
        } catch (IOException moveException) {
            try {
                Files.copy(legacyFile.toPath(), newFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                logger.warning("Failed to move legacy zone config for '" + zoneId + "'. Copied into the zones folder.");
                return true;
            } catch (IOException copyException) {
                logger.warning("Failed to migrate legacy zone config for '" + zoneId + "': " + copyException.getMessage());
                return false;
            }
        }
    }
}

