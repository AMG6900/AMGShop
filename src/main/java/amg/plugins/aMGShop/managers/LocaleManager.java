package amg.plugins.aMGShop.managers;

import amg.plugins.aMGShop.AMGShop;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LocaleManager {
    private final AMGShop plugin;
    private final File localeFolder;
    private FileConfiguration locale;
    private String currentLanguage;
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("%([^%]+)%");

    public LocaleManager(AMGShop plugin) {
        this.plugin = plugin;
        this.localeFolder = new File(plugin.getDataFolder(), "locales");
        setupLocale();
    }

    private void setupLocale() {
        if (!localeFolder.exists()) {
            localeFolder.mkdirs();
            saveDefaultLocales();
        }

        // Load configured language
        currentLanguage = plugin.getConfig().getString("locale.language", "en_US");
        loadLanguage(currentLanguage);
    }

    private void saveDefaultLocales() {
        // Save default locale files
        saveResource("en_US.yml");
        saveResource("bg_BG.yml");
    }

    private void saveResource(String fileName) {
        File file = new File(localeFolder, fileName);
        if (!file.exists()) {
            plugin.saveResource("locales/" + fileName, false);
            plugin.getLogger().info("Created locale file: " + fileName);
        }
    }

    public void loadLanguage(String language) {
        File localeFile = new File(localeFolder, language + ".yml");
        if (!localeFile.exists()) {
            plugin.getLogger().warning("Locale file not found: " + language + ".yml");
            plugin.getLogger().warning("Falling back to en_US");
            localeFile = new File(localeFolder, "en_US.yml");
            if (!localeFile.exists()) {
                saveResource("en_US.yml");
            }
            currentLanguage = "en_US";
        } else {
            currentLanguage = language;
        }

        locale = YamlConfiguration.loadConfiguration(localeFile);
        plugin.getLogger().info("Loaded locale: " + currentLanguage);
    }

    public String getMessage(String path) {
        return getMessage(path, new HashMap<>());
    }

    public String getMessage(String path, Map<String, String> placeholders) {
        String message = locale.getString(path);
        if (message == null) {
            plugin.getLogger().warning("Missing locale message: " + path);
            return "Missing message: " + path;
        }

        // Replace placeholders
        if (!placeholders.isEmpty()) {
            Matcher matcher = PLACEHOLDER_PATTERN.matcher(message);
            StringBuffer buffer = new StringBuffer();
            while (matcher.find()) {
                String placeholder = matcher.group(1);
                String replacement = placeholders.getOrDefault(placeholder, matcher.group());
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
            }
            matcher.appendTail(buffer);
            message = buffer.toString();
        }

        return message.replace("&", "§");
    }

    /**
     * Reloads the locale files and settings
     */
    public void reload() {
        // Reload configured language
        currentLanguage = plugin.getConfig().getString("locale.language", "en_US");
        loadLanguage(currentLanguage);
    }

    public String getCurrentLanguage() {
        return currentLanguage;
    }

    public void setLanguage(String language) {
        loadLanguage(language);
        plugin.getConfig().set("locale.language", language);
        plugin.saveConfig();
    }

    public FileConfiguration getLocale() {
        return locale;
    }
} 