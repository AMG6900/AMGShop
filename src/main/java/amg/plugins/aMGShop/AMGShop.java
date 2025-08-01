package amg.plugins.aMGShop;

import amg.plugins.aMGShop.commands.ShopCommand;
import amg.plugins.aMGShop.managers.ConfigManager;
import amg.plugins.aMGShop.database.DatabaseManager;
import amg.plugins.aMGShop.gui.ShopGUI;
import amg.plugins.aMGShop.listeners.NPCListener;
import amg.plugins.aMGShop.managers.*;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public class AMGShop extends JavaPlugin {
    private ConfigManager configManager;
    private DatabaseManager databaseManager;
    private ShopManager shopManager;
    private NPCManager npcManager;
    private LogManager logManager;
    private TaxManager taxManager;
    private InflationManager inflationManager;
    private ExploitChecker exploitChecker;
    private LocaleManager localeManager;
    private ShopGUI shopGUI;

    @Override
    public void onEnable() {
        // Save default resources
        saveDefaultConfig();
        saveDefaultResources();
        // Initialize managers
        this.configManager = new ConfigManager(this);
        
        this.databaseManager = new DatabaseManager(this);
        this.databaseManager.initialize(); // Initialize database first
        
        this.localeManager = new LocaleManager(this);
        this.logManager = new LogManager(this);
        this.taxManager = new TaxManager(this);
        this.inflationManager = new InflationManager(this);
        this.shopManager = new ShopManager(this);
        this.npcManager = new NPCManager(this);
        this.exploitChecker = new ExploitChecker(this);

        // Load remaining data
        this.shopManager.loadShops();
        this.npcManager.loadNPCs();

        // Initialize GUI after data is loaded
        this.shopGUI = new ShopGUI(this);

        // Register commands
        PluginCommand command = getCommand("amgshop");
        if (command != null) {
            ShopCommand shopCommand = new ShopCommand(this);
            command.setExecutor(shopCommand);
            command.setTabCompleter(shopCommand);
        } else {
            getLogger().severe("Failed to register /amgshop command! The command will not work.");
        }

        // Register listeners
        getServer().getPluginManager().registerEvents(new NPCListener(this), this);
        getServer().getPluginManager().registerEvents(npcManager, this); // Register NPCManager as a listener

        // Check for exploits after everything is loaded
        getServer().getScheduler().runTaskLater(this, () -> {
            getLogger().info("Checking for potential crafting exploits...");
            exploitChecker.checkForExploits();
        }, 100L); // Run after 5 seconds to ensure all recipes are loaded

        getLogger().info("AMGShop has been enabled!");
    }

    private void saveDefaultResources() {
        // Save category files if they don't exist
        saveResourceIfNotExists("shops/blocks.yml");
        saveResourceIfNotExists("shops/combat.yml");
        saveResourceIfNotExists("shops/food.yml");
        saveResourceIfNotExists("shops/tools.yml");
        saveResourceIfNotExists("shops/redstone.yml");
        saveResourceIfNotExists("shops/brewing.yml");
        saveResourceIfNotExists("shops/decoration.yml");
        saveResourceIfNotExists("shops/misc.yml");
        
        // Save locale files if they don't exist
        saveResourceIfNotExists("locales/en_US.yml");
    }
    
    private void saveResourceIfNotExists(String resourcePath) {
        // Determine the full path on disk
        String parentFolder = resourcePath.contains("/") ? 
            resourcePath.substring(0, resourcePath.lastIndexOf("/")) : "";
            
        // Create parent folders if needed
        if (!parentFolder.isEmpty()) {
            File folder = new File(getDataFolder(), parentFolder);
            if (!folder.exists()) {
                folder.mkdirs();
            }
        }
        
        // Check if the file exists before saving
        File file = new File(getDataFolder(), resourcePath);
        if (!file.exists()) {
            saveResource(resourcePath, false);
        }
    }

    @Override
    public void onDisable() {
        // Remove NPCs when plugin is disabled
        if (npcManager != null) {
            npcManager.removeNPCs();
        }
        
        if (inflationManager != null) {
            inflationManager.stopUpdateTask();
        }
        if (databaseManager != null) {
            databaseManager.close();
        }
        getLogger().info("AMGShop has been disabled!");
    }

    /**
     * Performs a complete reload of the plugin
     */
    public void reloadPlugin() {
        getLogger().info("Performing full plugin reload...");
        
        // Stop any running tasks
        if (inflationManager != null) {
            inflationManager.stopUpdateTask();
        }
        
        // Remove existing NPCs
        if (npcManager != null) {
            npcManager.removeNPCs();
        }
        
        // Reload config files
        reloadConfig();
        if (configManager != null) {
            configManager.reloadConfig();
        }
        
        // Reload locale
        if (localeManager != null) {
            localeManager.reload();
        }
        
        // Reload shops
        if (shopManager != null) {
            shopManager.loadShops();
        }
        
        // Reload NPCs
        if (npcManager != null) {
            npcManager.loadNPCs();
        }
        
        // Create a new inflation manager to restart the task
        this.inflationManager = new InflationManager(this);
        
        getLogger().info("Plugin reload complete!");
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public ShopManager getShopManager() {
        return shopManager;
    }

    public NPCManager getNPCManager() {
        return npcManager;
    }

    public LogManager getLogManager() {
        return logManager;
    }

    public TaxManager getTaxManager() {
        return taxManager;
    }

    public InflationManager getInflationManager() {
        return inflationManager;
    }
    
    public LocaleManager getLocaleManager() {
        return localeManager;
    }
    
    public ShopGUI getShopGUI() {
        return shopGUI;
    }
}
