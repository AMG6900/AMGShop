package amg.plugins.aMGShop.managers;

import amg.plugins.aMGShop.AMGShop;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class LogManager {
    private final AMGShop plugin;
    private final DateTimeFormatter formatter;
    private static final int MAX_LOGS_PER_FILE = 1000;

    public LogManager(AMGShop plugin) {
        this.plugin = plugin;
        this.formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    }

    public String getCurrentTime() {
        return LocalDateTime.now().format(formatter);
    }

    public void logPurchase(Player player, String category, String itemId, int amount, double price, double oldStock, double newStock) {
        String time = getCurrentTime();
        FileConfiguration logs = plugin.getConfigManager().getLogs();
        String path = "transactions." + time.replace(":", "-");

        // Format: "Player bought Xx item for $Y.YY - TIME"
        String message = String.format("%s bought %dx %s for $%.2f - %s", 
            player.getName(), amount, itemId.toLowerCase(), price, time);
        
        logs.set(path, message);

        checkAndRotateLogs();
        plugin.getConfigManager().saveLogs();
    }

    public void logSale(Player player, String category, String itemId, int amount, double pricePerItem, double oldStock, double newStock) {
        String time = getCurrentTime();
        FileConfiguration logs = plugin.getConfigManager().getLogs();
        String path = "transactions." + time.replace(":", "-");

        // Format: "Player sold Xx item for $Y.YY total ($Z.ZZ each) - TIME"
        double totalPrice = pricePerItem * amount;
        String message = String.format("%s sold %dx %s for $%.2f total ($%.2f each) - %s", 
            player.getName(), amount, itemId.toLowerCase(), totalPrice, pricePerItem, time);
        
        logs.set(path, message);

        checkAndRotateLogs();
        plugin.getConfigManager().saveLogs();
    }

    private void checkAndRotateLogs() {
        FileConfiguration logs = plugin.getConfigManager().getLogs();
        if (logs.getConfigurationSection("transactions") != null) {
            List<String> transactions = new ArrayList<>(logs.getConfigurationSection("transactions").getKeys(false));
            if (transactions.size() >= MAX_LOGS_PER_FILE) {
                // Archive old transactions
                String archiveDate = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                String archivePath = "archives/transactions_" + archiveDate + "_" + System.currentTimeMillis() + ".yml";
                plugin.getConfigManager().archiveLogs(archivePath, "transactions");
                
                // Clear current transactions
                logs.set("transactions", null);
            }
        }
    }
}