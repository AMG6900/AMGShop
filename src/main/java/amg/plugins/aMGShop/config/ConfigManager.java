package amg.plugins.aMGShop.config;

import amg.plugins.aMGShop.AMGShop;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

public class ConfigManager {
    private final AMGShop plugin;
    private FileConfiguration config;
    private File configFile;
    private File logsFile;
    private FileConfiguration logs;
    private final Map<String, FileConfiguration> categoryConfigs;
    private final File shopsFolder;

    public ConfigManager(AMGShop plugin) {
        this.plugin = plugin;
        this.categoryConfigs = new HashMap<>();
        this.shopsFolder = new File(plugin.getDataFolder(), "shops");
        setupConfig();
        setupLogs();
        setupShopsFolder();
    }

    private void setupConfig() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        configFile = new File(plugin.getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            plugin.saveResource("config.yml", false);
        }

        config = YamlConfiguration.loadConfiguration(configFile);
    }

    private void setupLogs() {
        logsFile = new File(plugin.getDataFolder(), "logs.yml");
        if (!logsFile.exists()) {
            try {
                logsFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not create logs.yml", e);
            }
        }
        logs = YamlConfiguration.loadConfiguration(logsFile);
    }

    private void setupShopsFolder() {
        if (!shopsFolder.exists()) {
            shopsFolder.mkdirs();
            // Save example category files from resources
            saveResourceIfNotExists("blocks.yml");
            saveResourceIfNotExists("combat.yml");
            saveResourceIfNotExists("food.yml");
            saveResourceIfNotExists("tools.yml");
            saveResourceIfNotExists("redstone.yml");
            saveResourceIfNotExists("brewing.yml");
            saveResourceIfNotExists("decoration.yml");
            saveResourceIfNotExists("misc.yml");
        }
        
        // Load all category files
        loadCategoryFiles();
    }
    
    private void saveResourceIfNotExists(String fileName) {
        File file = new File(shopsFolder, fileName);
        if (!file.exists()) {
            plugin.saveResource("shops/" + fileName, false);
            plugin.getLogger().info("Created example category file: " + fileName);
        }
    }
    
    private void loadCategoryFiles() {
        categoryConfigs.clear();
        
        // Get category references from main config
        ConfigurationSection categoriesSection = config.getConfigurationSection("categories");
        if (categoriesSection != null) {
            for (String categoryId : categoriesSection.getKeys(false)) {
                String fileName = categoriesSection.getString(categoryId + ".file");
                if (fileName != null) {
                    File categoryFile = new File(shopsFolder, fileName);
                    if (categoryFile.exists()) {
                        FileConfiguration categoryConfig = YamlConfiguration.loadConfiguration(categoryFile);
                        categoryConfigs.put(categoryId, categoryConfig);
                        plugin.getLogger().info("Loaded category file: " + fileName);
                    } else {
                        plugin.getLogger().warning("Category file not found: " + fileName);
                    }
                }
            }
        }
    }

    public void loadConfig() {
        config = YamlConfiguration.loadConfiguration(configFile);
        loadCategoryFiles();
    }

    public FileConfiguration getConfig() {
        return config;
    }

    public FileConfiguration getLogs() {
        return logs;
    }
    
    public Map<String, FileConfiguration> getCategoryConfigs() {
        return categoryConfigs;
    }
    
    public FileConfiguration getCategoryConfig(String categoryId) {
        return categoryConfigs.get(categoryId);
    }

    public void saveLogs() {
        try {
            logs.save(logsFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save logs.yml", e);
        }
    }

    public void archiveLogs(String archivePath, String section) {
        // Create archives directory if it doesn't exist
        File archiveDir = new File(plugin.getDataFolder(), "archives");
        if (!archiveDir.exists()) {
            archiveDir.mkdirs();
        }

        // Create new archive file
        File archiveFile = new File(archiveDir, archivePath);
        FileConfiguration archive = new YamlConfiguration();

        // Copy section to archive
        archive.set(section, logs.get(section));

        // Save archive
        try {
            archive.save(archiveFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save archive file: " + archivePath, e);
        }
    }

    public void reloadConfig() {
        config = YamlConfiguration.loadConfiguration(configFile);
        loadCategoryFiles();
    }

    public void saveConfig() {
        try {
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save config.yml", e);
        }
    }
    
    public void saveCategoryConfig(String categoryId) {
        FileConfiguration categoryConfig = categoryConfigs.get(categoryId);
        if (categoryConfig != null) {
            String fileName = config.getString("categories." + categoryId + ".file");
            if (fileName != null) {
                File categoryFile = new File(shopsFolder, fileName);
                try {
                    categoryConfig.save(categoryFile);
                } catch (IOException e) {
                    plugin.getLogger().log(Level.SEVERE, "Could not save category file: " + fileName, e);
                }
            }
        }
    }
    
    public void createCategoryConfig(String categoryId, String name, String icon, int slot) {
        // Create a new file for this category
        String fileName = categoryId + ".yml";
        File categoryFile = new File(shopsFolder, fileName);
        FileConfiguration categoryConfig = new YamlConfiguration();
        
        // Set basic category properties
        categoryConfig.set("name", name);
        categoryConfig.set("icon", icon);
        categoryConfig.set("slot", slot);
        categoryConfig.createSection("items");
        
        // Save the category file
        try {
            categoryConfig.save(categoryFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save new category file: " + fileName, e);
            return;
        }
        
        // Add reference to main config
        config.set("categories." + categoryId + ".file", fileName);
        saveConfig();
        
        // Add to loaded categories
        categoryConfigs.put(categoryId, categoryConfig);
    }
} 