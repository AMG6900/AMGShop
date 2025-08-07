package amg.plugins.aMGShop.managers;

import amg.plugins.aMGShop.AMGShop;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Method;
import java.util.logging.Level;

/**
 * Manages economy interactions with different economy providers (AMGCore or Vault/EssentialsX)
 */
public class EconomyManager {
    private final AMGShop plugin;
    private boolean amgCoreEnabled = false;
    private boolean vaultEnabled = false;
    private Economy vaultEconomy = null;
    private String priority = "auto";
    private boolean checkBalanceSync = true;
    
    /**
     * Creates a new EconomyManager and attempts to hook into available economy providers
     * @param plugin The AMGShop plugin instance
     */
    public EconomyManager(AMGShop plugin) {
        this.plugin = plugin;
        
        // Load configuration
        loadConfig();
        
        // Try to hook into AMGCore first
        if (hookIntoAMGCore()) {
            plugin.getLogger().info("Successfully hooked into AMGCore economy system!");
            amgCoreEnabled = true;
        } else {
            plugin.getLogger().warning("AMGCore not found or failed to hook into its economy system.");
        }
        
        // Then try to hook into Vault
        if (hookIntoVault()) {
            plugin.getLogger().info("Successfully hooked into Vault economy system!");
            vaultEnabled = true;
        } else {
            plugin.getLogger().warning("Vault not found or failed to hook into an economy plugin.");
        }
        
        // Check if at least one economy provider is available
        if (!amgCoreEnabled && !vaultEnabled) {
            plugin.getLogger().severe("No economy provider found! AMGShop requires either AMGCore or Vault with an economy plugin.");
            plugin.getLogger().severe("The plugin will be disabled.");
            Bukkit.getPluginManager().disablePlugin(plugin);
        } else {
            String providers = "";
            if (amgCoreEnabled) providers += "AMGCore";
            if (vaultEnabled) {
                if (!providers.isEmpty()) providers += " and ";
                providers += "Vault";
            }
            plugin.getLogger().info("Economy providers enabled: " + providers);
            
            // Log priority if both are available
            if (amgCoreEnabled && vaultEnabled) {
                plugin.getLogger().info("Economy priority: " + priority);
            }
        }
    }
    
    /**
     * Loads configuration settings for the economy manager
     */
    private void loadConfig() {
        // Load priority setting
        priority = plugin.getConfig().getString("economy.priority", "auto").toLowerCase();
        if (!priority.equals("auto") && !priority.equals("amgcore_first") && !priority.equals("vault_first")) {
            plugin.getLogger().warning("Invalid economy priority setting: " + priority + ". Defaulting to 'auto'.");
            priority = "auto";
        }
        
        // Load balance sync setting
        checkBalanceSync = plugin.getConfig().getBoolean("economy.check_balance_sync", true);
    }
    
    /**
     * Attempts to hook into AMGCore's economy system
     * @return true if successful, false otherwise
     */
    private boolean hookIntoAMGCore() {
        try {
            Plugin amgCore = plugin.getServer().getPluginManager().getPlugin("AMGCore");
            return amgCore != null && amgCore.isEnabled();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error while hooking into AMGCore", e);
            return false;
        }
    }
    
    /**
     * Attempts to hook into Vault's economy system
     * @return true if successful, false otherwise
     */
    private boolean hookIntoVault() {
        try {
            if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
                return false;
            }
            
            RegisteredServiceProvider<Economy> economyProvider = 
                Bukkit.getServicesManager().getRegistration(Economy.class);
                
            if (economyProvider == null) {
                return false;
            }
            
            vaultEconomy = economyProvider.getProvider();
            return vaultEconomy != null;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error while hooking into Vault", e);
            return false;
        }
    }
    
    /**
     * Gets the player's balance
     * @param player The player
     * @return The player's balance, or 0 if an error occurs
     */
    public double getBalance(Player player) {
        double balance = 0.0;
        boolean success = false;
        
        // Determine which provider to try first based on priority
        boolean tryAMGCoreFirst = amgCoreEnabled && 
            (priority.equals("auto") || priority.equals("amgcore_first"));
        boolean tryVaultFirst = vaultEnabled && 
            (priority.equals("vault_first") || (priority.equals("auto") && !amgCoreEnabled));
        
        // Try the first provider
        if (tryAMGCoreFirst) {
            Object playerData = getAMGCorePlayerData(player);
            if (playerData != null) {
                balance = getAMGCoreBalance(playerData);
                success = true;
            }
        } else if (tryVaultFirst && vaultEconomy != null) {
            balance = vaultEconomy.getBalance(player);
            success = true;
        }
        
        // If the first provider failed, try the second one
        if (!success) {
            if (tryAMGCoreFirst && vaultEnabled && vaultEconomy != null) {
                balance = vaultEconomy.getBalance(player);
                success = true;
            } else if (tryVaultFirst && amgCoreEnabled) {
                Object playerData = getAMGCorePlayerData(player);
                if (playerData != null) {
                    balance = getAMGCoreBalance(playerData);
                    success = true;
                }
            }
        }
        
        // Check for balance discrepancy if both providers are enabled
        if (success && amgCoreEnabled && vaultEnabled && vaultEconomy != null && checkBalanceSync) {
            Object playerData = getAMGCorePlayerData(player);
            if (playerData != null) {
                double amgCoreBalance = getAMGCoreBalance(playerData);
                double vaultBalance = vaultEconomy.getBalance(player);
                
                if (Math.abs(amgCoreBalance - vaultBalance) > 0.01) {
                    // Log the discrepancy
                    plugin.getLogger().warning("Balance discrepancy detected for " + player.getName() + 
                        ": AMGCore=" + amgCoreBalance + ", Vault=" + vaultBalance);
                }
            }
        }
        
        // If all methods failed, log a warning
        if (!success) {
            plugin.getLogger().warning("Failed to get balance for " + player.getName() + " from any economy provider!");
        }
        
        return balance;
    }
    
    /**
     * Withdraws money from the player's account
     * @param player The player
     * @param amount The amount to withdraw
     * @return true if successful, false otherwise
     */
    public boolean withdrawMoney(Player player, double amount) {
        boolean success = false;
        
        // Determine which provider to try first based on priority
        boolean tryAMGCoreFirst = amgCoreEnabled && 
            (priority.equals("auto") || priority.equals("amgcore_first"));
        boolean tryVaultFirst = vaultEnabled && 
            (priority.equals("vault_first") || (priority.equals("auto") && !amgCoreEnabled));
        
        // Try the first provider
        if (tryAMGCoreFirst) {
            Object playerData = getAMGCorePlayerData(player);
            if (playerData != null) {
                double currentBalance = getAMGCoreBalance(playerData);
                if (currentBalance >= amount) {
                    setAMGCoreBalance(playerData, currentBalance - amount);
                    success = true;
                }
            }
        } else if (tryVaultFirst && vaultEconomy != null) {
            if (vaultEconomy.has(player, amount)) {
                vaultEconomy.withdrawPlayer(player, amount);
                success = true;
            }
        }
        
        // If both providers are enabled, update the other one for synchronization
        if (amgCoreEnabled && vaultEnabled && vaultEconomy != null) {
            if (tryAMGCoreFirst && success) {
                // AMGCore was successful, now update Vault
                if (vaultEconomy.has(player, amount)) {
                    vaultEconomy.withdrawPlayer(player, amount);
                } else if (checkBalanceSync) {
                    // Log discrepancy if Vault doesn't have enough funds
                    plugin.getLogger().warning("Balance discrepancy: " + player.getName() + 
                        " has insufficient funds in Vault but operation proceeded with AMGCore.");
                }
            } else if (tryVaultFirst && success) {
                // Vault was successful, now update AMGCore
                Object playerData = getAMGCorePlayerData(player);
                if (playerData != null) {
                    double currentBalance = getAMGCoreBalance(playerData);
                    if (currentBalance >= amount) {
                        setAMGCoreBalance(playerData, currentBalance - amount);
                    } else if (checkBalanceSync) {
                        // Log discrepancy if AMGCore doesn't have enough funds
                        plugin.getLogger().warning("Balance discrepancy: " + player.getName() + 
                            " has insufficient funds in AMGCore but operation proceeded with Vault.");
                    }
                }
            }
        }
        
        // If the first provider failed, try the second one
        if (!success) {
            if (tryAMGCoreFirst && vaultEnabled && vaultEconomy != null) {
                if (vaultEconomy.has(player, amount)) {
                    vaultEconomy.withdrawPlayer(player, amount);
                    success = true;
                }
            } else if (tryVaultFirst && amgCoreEnabled) {
                Object playerData = getAMGCorePlayerData(player);
                if (playerData != null) {
                    double currentBalance = getAMGCoreBalance(playerData);
                    if (currentBalance >= amount) {
                        setAMGCoreBalance(playerData, currentBalance - amount);
                        success = true;
                    }
                }
            }
        }
        
        if (!success) {
            plugin.getLogger().warning("Failed to withdraw " + amount + " from " + player.getName() + 
                " using any economy provider!");
        }
        
        return success;
    }
    
    /**
     * Deposits money to the player's account
     * @param player The player
     * @param amount The amount to deposit
     * @return true if successful, false otherwise
     */
    public boolean depositMoney(Player player, double amount) {
        boolean success = false;
        
        // Determine which provider to try first based on priority
        boolean tryAMGCoreFirst = amgCoreEnabled && 
            (priority.equals("auto") || priority.equals("amgcore_first"));
        boolean tryVaultFirst = vaultEnabled && 
            (priority.equals("vault_first") || (priority.equals("auto") && !amgCoreEnabled));
        
        // Try the first provider
        if (tryAMGCoreFirst) {
            Object playerData = getAMGCorePlayerData(player);
            if (playerData != null) {
                double currentBalance = getAMGCoreBalance(playerData);
                setAMGCoreBalance(playerData, currentBalance + amount);
                success = true;
            }
        } else if (tryVaultFirst && vaultEconomy != null) {
            vaultEconomy.depositPlayer(player, amount);
            success = true;
        }
        
        // If both providers are enabled, update the other one for synchronization
        if (amgCoreEnabled && vaultEnabled && vaultEconomy != null) {
            if (tryAMGCoreFirst && success) {
                // AMGCore was successful, now update Vault
                vaultEconomy.depositPlayer(player, amount);
            } else if (tryVaultFirst && success) {
                // Vault was successful, now update AMGCore
                Object playerData = getAMGCorePlayerData(player);
                if (playerData != null) {
                    double currentBalance = getAMGCoreBalance(playerData);
                    setAMGCoreBalance(playerData, currentBalance + amount);
                }
            }
        }
        
        // If the first provider failed, try the second one
        if (!success) {
            if (tryAMGCoreFirst && vaultEnabled && vaultEconomy != null) {
                vaultEconomy.depositPlayer(player, amount);
                success = true;
            } else if (tryVaultFirst && amgCoreEnabled) {
                Object playerData = getAMGCorePlayerData(player);
                if (playerData != null) {
                    double currentBalance = getAMGCoreBalance(playerData);
                    setAMGCoreBalance(playerData, currentBalance + amount);
                    success = true;
                }
            }
        }
        
        if (!success) {
            plugin.getLogger().warning("Failed to deposit " + amount + " to " + player.getName() + 
                " using any economy provider!");
        }
        
        return success;
    }
    
    /**
     * Checks if the player has enough money
     * @param player The player
     * @param amount The amount to check
     * @return true if the player has enough money, false otherwise
     */
    public boolean hasEnoughMoney(Player player, double amount) {
        boolean hasEnough = false;
        boolean checked = false;
        
        // Determine which provider to try first based on priority
        boolean tryAMGCoreFirst = amgCoreEnabled && 
            (priority.equals("auto") || priority.equals("amgcore_first"));
        boolean tryVaultFirst = vaultEnabled && 
            (priority.equals("vault_first") || (priority.equals("auto") && !amgCoreEnabled));
        
        // Try the first provider
        if (tryAMGCoreFirst) {
            Object playerData = getAMGCorePlayerData(player);
            if (playerData != null) {
                hasEnough = getAMGCoreBalance(playerData) >= amount;
                checked = true;
            }
        } else if (tryVaultFirst && vaultEconomy != null) {
            hasEnough = vaultEconomy.has(player, amount);
            checked = true;
        }
        
        // Check for discrepancy if both providers are enabled
        if (checked && hasEnough && amgCoreEnabled && vaultEnabled && vaultEconomy != null && checkBalanceSync) {
            boolean amgCoreHasEnough = false;
            boolean vaultHasEnough = false;
            
            // Check AMGCore
            Object playerData = getAMGCorePlayerData(player);
            if (playerData != null) {
                amgCoreHasEnough = getAMGCoreBalance(playerData) >= amount;
            }
            
            // Check Vault
            vaultHasEnough = vaultEconomy.has(player, amount);
            
            // Log discrepancy if there is one
            if (amgCoreHasEnough != vaultHasEnough) {
                plugin.getLogger().warning("Balance discrepancy detected for " + player.getName() + 
                    ": AMGCore has enough=" + amgCoreHasEnough + ", Vault has enough=" + vaultHasEnough);
            }
        }
        
        // If the first provider failed, try the second one
        if (!checked) {
            if (tryAMGCoreFirst && vaultEnabled && vaultEconomy != null) {
                hasEnough = vaultEconomy.has(player, amount);
                checked = true;
            } else if (tryVaultFirst && amgCoreEnabled) {
                Object playerData = getAMGCorePlayerData(player);
                if (playerData != null) {
                    hasEnough = getAMGCoreBalance(playerData) >= amount;
                    checked = true;
                }
            }
        }
        
        // If all methods failed, log a warning
        if (!checked) {
            plugin.getLogger().warning("Failed to check if " + player.getName() + 
                " has enough money using any economy provider!");
        }
        
        return hasEnough;
    }
    
    /**
     * Formats the given amount as a currency string
     * @param amount The amount to format
     * @return The formatted amount
     */
    public String formatMoney(double amount) {
        if (vaultEnabled && vaultEconomy != null) {
            return vaultEconomy.format(amount);
        }
        
        // Default formatting if Vault is not available
        return String.format("$%.2f", amount);
    }
    
    /**
     * Gets the player data from AMGCore
     * @param player The player
     * @return The player data object, or null if an error occurs
     */
    private Object getAMGCorePlayerData(Player player) {
        try {
            Plugin amgCore = plugin.getServer().getPluginManager().getPlugin("AMGCore");
            if (amgCore == null) {
                return null;
            }
            
            Method getPlayerDataManager = amgCore.getClass().getMethod("getPlayerDataManager");
            Object playerDataManager = getPlayerDataManager.invoke(amgCore);
            
            Method getPlayerData = playerDataManager.getClass().getMethod("getPlayerData", Player.class);
            return getPlayerData.invoke(playerDataManager, player);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to get player data from AMGCore", e);
            return null;
        }
    }
    
    /**
     * Gets the player's balance from AMGCore
     * @param playerData The player data object from AMGCore
     * @return The player's balance, or 0 if an error occurs
     */
    private double getAMGCoreBalance(Object playerData) {
        try {
            Method getMoney = playerData.getClass().getMethod("getMoney");
            return (double) getMoney.invoke(playerData);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to get player money from AMGCore", e);
            return 0.0;
        }
    }
    
    /**
     * Sets the player's balance in AMGCore
     * @param playerData The player data object from AMGCore
     * @param amount The new balance
     */
    private void setAMGCoreBalance(Object playerData, double amount) {
        try {
            Method setMoney = playerData.getClass().getMethod("setMoney", double.class);
            setMoney.invoke(playerData, amount);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to set player money in AMGCore", e);
        }
    }
    
    /**
     * Checks if AMGCore economy is enabled
     * @return true if AMGCore economy is enabled, false otherwise
     */
    public boolean isAMGCoreEnabled() {
        return amgCoreEnabled;
    }
    
    /**
     * Checks if Vault economy is enabled
     * @return true if Vault economy is enabled, false otherwise
     */
    public boolean isVaultEnabled() {
        return vaultEnabled && vaultEconomy != null;
    }
}