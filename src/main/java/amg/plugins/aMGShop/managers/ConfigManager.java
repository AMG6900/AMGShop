package amg.plugins.aMGShop.managers;

import amg.plugins.aMGShop.AMGShop;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class ConfigManager {
    private final AMGShop plugin;
    private FileConfiguration config;
    private final Map<String, FileConfiguration> categoryConfigs;
    private FileConfiguration logs;
    private final File logsFile;

    public ConfigManager(AMGShop plugin) {
        this.plugin = plugin;
        this.categoryConfigs = new HashMap<>();
        this.logsFile = new File(plugin.getDataFolder(), "logs.yml");
        reloadConfig();
    }

    private void loadCategoryConfigs() {
        File shopsFolder = new File(plugin.getDataFolder(), "shops");
        if (!shopsFolder.exists()) {
            shopsFolder.mkdirs();
            // Save default shop categories if folder is empty
            saveDefaultShops();
        }

        // Load all .yml files from the shops folder
        File[] shopFiles = shopsFolder.listFiles((dir, name) -> name.toLowerCase().endsWith(".yml"));
        if (shopFiles != null) {
            for (File file : shopFiles) {
                String categoryId = file.getName().substring(0, file.getName().length() - 4); // Remove .yml
                categoryConfigs.put(categoryId, YamlConfiguration.loadConfiguration(file));
                plugin.getLogger().info("Loaded category file: " + file.getName());
            }
        }
    }

    private void saveDefaultShops() {
        // Default categories - these will only be created if no shop files exist
        String[] defaultCategories = {"blocks", "combat", "food", "tools", "redstone", "brewing", "decoration", "misc"};
        for (String category : defaultCategories) {
            String resourcePath = "shops/" + category + ".yml";
            if (plugin.getResource(resourcePath) != null) {
                plugin.saveResource(resourcePath, false);
                plugin.getLogger().info("Created default shop category: " + category);
            }
        }
    }

    private void loadLogs() {
        if (!logsFile.exists()) {
            try {
                logsFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create logs.yml!");
                e.printStackTrace();
            }
        }
        this.logs = YamlConfiguration.loadConfiguration(logsFile);
    }

    public Set<String> getShopCategories() {
        return categoryConfigs.keySet();
    }

    public FileConfiguration getCategoryConfig(String category) {
        return categoryConfigs.get(category);
    }

    public void reloadConfig() {
        plugin.reloadConfig();
        this.config = plugin.getConfig();
        loadCategoryConfigs();
        loadLogs();
    }

    public FileConfiguration getConfig() {
        return config;
    }

    public FileConfiguration getLogs() {
        return logs;
    }

    public void saveLogs() {
        try {
            logs.save(logsFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save logs.yml!");
            e.printStackTrace();
        }
    }

    public void archiveLogs(String archivePath, String section) {
        File archiveFile = new File(plugin.getDataFolder(), archivePath);
        if (!archiveFile.getParentFile().exists()) {
            archiveFile.getParentFile().mkdirs();
        }

        try {
            // Create new file for archived data
            FileConfiguration archive = new YamlConfiguration();
            archive.set(section, logs.get(section));
            archive.save(archiveFile);
            
            // Clear the section from current logs
            logs.set(section, null);
            saveLogs();
        } catch (IOException e) {
            plugin.getLogger().severe("Could not archive logs to " + archivePath);
            e.printStackTrace();
        }
    }
} 