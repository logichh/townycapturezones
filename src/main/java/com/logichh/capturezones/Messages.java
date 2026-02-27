package com.logichh.capturezones;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import org.bukkit.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;

/**
 * Handles localization for the CaptureZones plugin.
 * Supports JSON-based language files with automatic fallback to English.
 * Features placeholder replacement and ChatColor translation.
 *
 * @author LogicHH
 * @version 1.0
 */
public class Messages {

    private static final String LANG_DIR = "lang";
    private static final String DEFAULT_LANG = "en";
    private static final Gson GSON = new Gson();

    private static JavaPlugin plugin;
    private static String activeLanguage;
    private static Map<String, Map<String, Object>> languageCache = new HashMap<>();
    private static Map<String, Object> fallbackCache;

    /**
     * Initializes the Messages system.
     * Must be called in onEnable() after config is loaded.
     *
     * @param plugin The main plugin instance
     */
    public static void init(JavaPlugin plugin) {
        Messages.plugin = plugin;
        loadLanguages();
    }

    /**
     * Reloads all language files.
     * Should be called when language files are updated.
     */
    public static void reload() {
        languageCache.clear();
        fallbackCache = null;
        loadLanguages();
    }

    /**
     * Loads all available language files from the lang/ directory.
     */
    private static void loadLanguages() {
        File langDir = new File(plugin.getDataFolder(), LANG_DIR);
        if (!langDir.exists()) {
            langDir.mkdirs();
            // Copy default language files from jar
            copyDefaultLanguageFiles();
        }

        File[] langFiles = langDir.listFiles((dir, name) -> name.endsWith(".json"));
        if (langFiles != null) {
            for (File file : langFiles) {
                String langCode = file.getName().replace(".json", "");
                try (FileReader reader = new FileReader(file, StandardCharsets.UTF_8)) {
                    JsonObject json = GSON.fromJson(reader, JsonObject.class);
                    if (json != null) {
                        Map<String, Object> translations = parseJsonObject(json);
                        Map<String, Object> bundled = loadBundledLanguage(langCode);
                        if (bundled != null) {
                            bundled.putAll(translations);
                            languageCache.put(langCode, bundled);
                        } else {
                            languageCache.put(langCode, translations);
                        }
                        plugin.getLogger().info("Loaded language: " + langCode);
                    }
                } catch (IOException | JsonSyntaxException e) {
                    plugin.getLogger().log(Level.WARNING, "Failed to load language file: " + file.getName(), e);
                }
            }
        }

        // Ensure default language is loaded
        if (!languageCache.containsKey(DEFAULT_LANG)) {
            loadDefaultEnglish();
        }

        // Set fallback cache
        fallbackCache = languageCache.get(DEFAULT_LANG);

        // Set active language from config
        String configLang = plugin.getConfig().getString("settings.language", DEFAULT_LANG);
        setActiveLanguage(configLang);
    }

    /**
     * Copies default language files from the plugin jar to the data folder.
     */
    private static void copyDefaultLanguageFiles() {
        copyDefaultFile("lang/en.json");
    }

    /**
     * Copies a default file from the jar resources.
     *
     * @param resourcePath The path to the resource in the jar
     */
    private static void copyDefaultFile(String resourcePath) {
        try (InputStream in = plugin.getResource(resourcePath)) {
            if (in != null) {
                File outFile = new File(plugin.getDataFolder(), resourcePath);
                outFile.getParentFile().mkdirs();
                java.nio.file.Files.copy(in, outFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to copy default language file: " + resourcePath, e);
        }
    }

    /**
     * Loads the default English translations from the jar.
     */
    private static void loadDefaultEnglish() {
        Map<String, Object> translations = loadBundledLanguage(DEFAULT_LANG);
        if (translations != null) {
            languageCache.put(DEFAULT_LANG, translations);
            plugin.getLogger().info("Loaded default English language");
            return;
        }
        plugin.getLogger().log(Level.SEVERE, "Failed to load default English language file");
    }

    private static Map<String, Object> loadBundledLanguage(String langCode) {
        String resourcePath = LANG_DIR + "/" + langCode + ".json";
        try (InputStream in = plugin.getResource(resourcePath)) {
            if (in == null) {
                return null;
            }
            try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                JsonObject json = GSON.fromJson(reader, JsonObject.class);
                if (json != null) {
                    return parseJsonObject(json);
                }
            }
        } catch (IOException | JsonSyntaxException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to load bundled language file: " + resourcePath, e);
        }
        return null;
    }

    /**
     * Parses a JsonObject into a Map<String, Object>.
     * Handles nested objects and arrays.
     *
     * @param json The JsonObject to parse
     * @return A map representation of the JSON
     */
    private static Map<String, Object> parseJsonObject(JsonObject json) {
        Map<String, Object> map = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            map.put(entry.getKey(), parseJsonElement(entry.getValue()));
        }
        return map;
    }

    /**
     * Parses a JsonElement into the appropriate Java object.
     *
     * @param element The JsonElement to parse
     * @return The parsed object (String, List, Map, etc.)
     */
    private static Object parseJsonElement(JsonElement element) {
        if (element.isJsonPrimitive()) {
            return element.getAsString();
        } else if (element.isJsonArray()) {
            List<Object> list = new ArrayList<>();
            for (JsonElement item : element.getAsJsonArray()) {
                list.add(parseJsonElement(item));
            }
            return list;
        } else if (element.isJsonObject()) {
            return parseJsonObject(element.getAsJsonObject());
        }
        return element.toString();
    }

    /**
     * Sets the active language.
     * If the language doesn't exist, tries the short code, then falls back to English.
     *
     * @param language The language code to set
     */
    public static void setActiveLanguage(String language) {
        if (languageCache.containsKey(language)) {
            activeLanguage = language;
        } else if (language.contains("_") && languageCache.containsKey(language.split("_")[0])) {
            activeLanguage = language.split("_")[0];
            plugin.getLogger().info("Language '" + language + "' not found, using '" + activeLanguage + "'");
        } else {
            activeLanguage = DEFAULT_LANG;
            plugin.getLogger().warning("Language '" + language + "' not found, falling back to '" + DEFAULT_LANG + "'");
        }
    }

    /**
     * Gets the active language code.
     *
     * @return The current active language
     */
    public static String getActiveLanguage() {
        return activeLanguage;
    }

    /**
     * Gets a list of available language codes.
     *
     * @return A sorted set of available languages
     */
    public static Set<String> getAvailableLanguages() {
        return new TreeSet<>(languageCache.keySet());
    }

    /**
     * Gets a translated message by key.
     *
     * @param key The translation key
     * @return The translated message with ChatColor applied, or the key if not found
     */
    public static String get(String key) {
        return get(key, (Map<String, String>) null);
    }

    /**
     * Gets a translated message by key with placeholder replacement.
     *
     * @param key The translation key
     * @param placeholders Map of placeholder keys to values
     * @return The translated message with placeholders replaced and ChatColor applied
     */
    public static String get(String key, Map<String, String> placeholders) {
        String message = getRaw(key);
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                message = message.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    /**
     * Gets a translated message by key with variable arguments for placeholders.
     * Placeholders are replaced in order: {0}, {1}, etc.
     *
     * @param key The translation key
     * @param args The values to replace {0}, {1}, etc.
     * @return The translated message with placeholders replaced and ChatColor applied
     */
    public static String get(String key, Object... args) {
        String message = getRaw(key);
        for (int i = 0; i < args.length; i++) {
            message = message.replace("{" + i + "}", String.valueOf(args[i]));
        }
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    /**
     * Gets a translated list of messages by key.
     *
     * @param key The translation key
     * @return A list of translated messages, or empty list if not found or not a list
     */
    public static List<String> getList(String key) {
        return getList(key, null);
    }

    /**
     * Gets a translated list of messages by key with placeholder replacement.
     *
     * @param key The translation key
     * @param placeholders Map of placeholder keys to values
     * @return A list of translated messages with placeholders replaced and ChatColor applied
     */
    public static List<String> getList(String key, Map<String, String> placeholders) {
        Object value = getRawObject(key);
        if (!(value instanceof List)) {
            return new ArrayList<>();
        }

        List<String> result = new ArrayList<>();
        for (Object item : (List<?>) value) {
            String message = String.valueOf(item);
            if (placeholders != null) {
                for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                    message = message.replace("{" + entry.getKey() + "}", entry.getValue());
                }
            }
            result.add(ChatColor.translateAlternateColorCodes('&', message));
        }
        return result;
    }

    /**
     * Gets the raw (uncolored) translation value.
     *
     * @param key The translation key
     * @return The raw translation string
     */
    private static String getRaw(String key) {
        Object value = getRawObject(key);
        return value != null ? String.valueOf(value) : key;
    }

    /**
     * Gets the raw translation object with fallback logic.
     *
     * @param key The translation key
     * @return The translation object, or null if not found
     */
    private static Object getRawObject(String key) {
        Map<String, Object> activeMap = languageCache.get(activeLanguage);
        if (activeMap != null && activeMap.containsKey(key)) {
            return activeMap.get(key);
        }

        // Try fallback to English
        if (fallbackCache != null && fallbackCache.containsKey(key)) {
            return fallbackCache.get(key);
        }

        // Last resort: return the key itself
        return key;
    }
}

