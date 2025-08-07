package amg.plugins.aMGShop.managers;

import amg.plugins.aMGShop.AMGShop;
import org.bukkit.scheduler.BukkitTask;

public class InflationManager {
    private final AMGShop plugin;
    private final boolean enabled;
    private double inflationRate;
    private BukkitTask updateTask;

    public InflationManager(AMGShop plugin) {
        this.plugin = plugin;
        this.enabled = plugin.getConfig().getBoolean("shop.inflation.enabled", true);
        this.inflationRate = plugin.getConfig().getDouble("shop.inflation.rate", 5.0);
        
        if (enabled) {
            startUpdateTask();
        }
    }

    private void startUpdateTask() {
        if (updateTask != null) {
            updateTask.cancel();
        }

        long updateInterval = plugin.getConfig().getLong("shop.inflation.update_interval", 60) * 20 * 60; // Convert minutes to ticks
        updateTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::updateInflation, updateInterval, updateInterval);
    }

    public void stopUpdateTask() {
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }
    }

    private void updateInflation() {
        // Load the current rate from config in case it was changed
        this.inflationRate = plugin.getConfig().getDouble("shop.inflation.rate", 5.0);
        plugin.getLogger().info("Updated inflation rate: " + inflationRate + "%");
    }

    public double getInflationMultiplier() {
        if (!enabled) return 1.0;
        
        // Convert percentage to multiplier
        double inflationMultiplier = 1.0 + (inflationRate / 100.0);
        return inflationMultiplier;
    }

    public double getBuyPriceMultiplier() {
        if (!enabled) return 1.0;
        
        // For buy prices, inflation increases the price
        return getInflationMultiplier();
    }

    public double getSellPriceMultiplier() {
        if (!enabled) return 1.0;
        
        // For sell prices, inflation decreases the price
        // The higher the inflation, the lower the sell prices
        double inflationMultiplier = getInflationMultiplier();
        return 1.0 / inflationMultiplier;
    }

    public double getInflationRate() {
        return inflationRate;
    }
} 