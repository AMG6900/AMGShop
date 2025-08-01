package amg.plugins.aMGShop.managers;

import amg.plugins.aMGShop.AMGShop;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import org.bukkit.configuration.file.FileConfiguration;
import net.kyori.adventure.text.Component;

public class ShopManager {
    private final AMGShop plugin;
    private final Map<String, Map<String, ShopItem>> categories;
    private final boolean priceFluctuationEnabled;
    private final double priceSensitivity;

    public ShopManager(AMGShop plugin) {
        this.plugin = plugin;
        this.categories = new HashMap<>();
        
        // Load price fluctuation settings
        ConfigurationSection fluctuation = plugin.getConfig().getConfigurationSection("shop.price_fluctuation");
        this.priceFluctuationEnabled = fluctuation.getBoolean("enabled", true);
        
        // Load price sensitivity (clamp between 1.0 and 10.0)
        double sensitivity = fluctuation.getDouble("sensitivity", 5.0);
        this.priceSensitivity = Math.max(1.0, Math.min(10.0, sensitivity));
    }

    public Object getPlayerData(Player player) {
        try {
            Plugin amgCore = plugin.getServer().getPluginManager().getPlugin("AMGCore");
            if (amgCore == null) {
                plugin.getLogger().severe("AMGCore plugin not found!");
                return null;
            }

            Method getPlayerDataManager = amgCore.getClass().getMethod("getPlayerDataManager");
            Object playerDataManager = getPlayerDataManager.invoke(amgCore);

            Method getPlayerData = playerDataManager.getClass().getMethod("getPlayerData", Player.class);
            return getPlayerData.invoke(playerDataManager, player);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to get player data using reflection", e);
            return null;
        }
    }

    public double getPlayerMoney(Object playerData) {
        try {
            Method getMoney = playerData.getClass().getMethod("getMoney");
            return (double) getMoney.invoke(playerData);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to get player money using reflection", e);
            return 0.0;
        }
    }

    public void setPlayerMoney(Object playerData, double amount) {
        try {
            Method setMoney = playerData.getClass().getMethod("setMoney", double.class);
            setMoney.invoke(playerData, amount);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to set player money using reflection", e);
        }
    }

    public void loadShops() {
        categories.clear();
        ConfigurationSection categoriesSection = plugin.getConfig().getConfigurationSection("categories");
        
        if (categoriesSection == null) {
            plugin.getLogger().warning("No categories found in config!");
            return;
        }

        for (String categoryId : categoriesSection.getKeys(false)) {
            Map<String, ShopItem> items = new HashMap<>();
            
            // Get the category config file
            FileConfiguration categoryConfig = plugin.getConfigManager().getCategoryConfig(categoryId);
            if (categoryConfig == null) {
                plugin.getLogger().warning("Category config not found for: " + categoryId);
                continue;
            }
            
            // Load items from the category config
            ConfigurationSection itemsSection = categoryConfig.getConfigurationSection("items");
            if (itemsSection != null) {
                for (String itemId : itemsSection.getKeys(false)) {
                    ConfigurationSection itemSection = itemsSection.getConfigurationSection(itemId);
                    Material material = Material.valueOf(itemSection.getString("material"));
                    double buyPrice = itemSection.getDouble("buy_price");
                    double sellPrice = itemSection.getDouble("sell_price");
                    int initialStock = itemSection.getInt("initial_stock");
                    int maxStock = itemSection.getInt("max_stock");

                    ShopItem item = new ShopItem(categoryId, itemId, material, buyPrice, sellPrice, maxStock);
                    items.put(itemId, item);

                    // Initialize item in database
                    plugin.getDatabaseManager().initializeItem(categoryId, itemId, initialStock, buyPrice, sellPrice);
                }
            }
            
            categories.put(categoryId, items);
            plugin.getLogger().info("Loaded category: " + categoryId + " with " + items.size() + " items");
        }
    }

    private double calculatePriceMultiplier(int currentStock, int maxStock) {
        // Calculate stock percentage (0-100%)
        double stockPercentage = (double) currentStock / maxStock * 100;

        // Calculate price range based on sensitivity
        // sensitivity 1.0: max=1.5, min=0.75
        // sensitivity 5.0: max=2.5, min=0.4
        // sensitivity 10.0: max=4.0, min=0.25
        double maxMultiplier = 1.0 + (priceSensitivity * 0.3);  // Increases with sensitivity
        double minMultiplier = 1.0 / (1.0 + (priceSensitivity * 0.15));  // Decreases with sensitivity
        double curve = 1.0 + (priceSensitivity * 0.1);  // Steeper curve with higher sensitivity
        
        // Calculate multiplier using exponential decay
        double multiplier = maxMultiplier * Math.exp(-curve * (stockPercentage / 100.0)) + minMultiplier;
        
        // Debug log the calculation
        if (plugin.getConfig().getBoolean("debug", false)) {
            plugin.getLogger().info(String.format(
                "[Debug] Price multiplier calculation: Stock=%d/%d (%.1f%%), Sensitivity=%.1f, Range=%.2f-%.2f, Curve=%.1f, Final=%.2f",
                currentStock, maxStock, stockPercentage, priceSensitivity, maxMultiplier, minMultiplier, curve, multiplier
            ));
        }
        
        return multiplier;
    }

    public double calculateBuyPrice(String category, String itemId, int amount) {
        ShopItem item = getItem(category, itemId);
        if (item == null) return -1;

        double[] currentPrices = plugin.getDatabaseManager().getCurrentPrices(category, itemId);
        if (currentPrices == null) return -1;

        // Get base price and current stock
        double basePrice = item.getBuyPrice() * amount;
        int currentStock = plugin.getDatabaseManager().getStock(category, itemId);
        
        // Apply stock-based price fluctuation
        if (priceFluctuationEnabled) {
            double stockMultiplier = calculatePriceMultiplier(currentStock, item.getMaxStock());
            basePrice *= stockMultiplier;
            
            // Debug log the price calculation
            if (plugin.getConfig().getBoolean("debug", false)) {
                plugin.getLogger().info(String.format(
                    "[Debug] Buy price calculation for %s: Base=%.2f, Stock=%d/%d (%.1f%%), Multiplier=%.2f, Final=%.2f",
                    itemId, item.getBuyPrice(), currentStock, item.getMaxStock(),
                    (double) currentStock / item.getMaxStock() * 100,
                    stockMultiplier, basePrice
                ));
            }
        }

        // Apply inflation to buy price (increases price)
        basePrice *= plugin.getInflationManager().getBuyPriceMultiplier();
        
        // Ensure minimum price and round to 2 decimal places
        if (basePrice < 0.01) {
            basePrice = 0.01;
        } else {
            basePrice = Math.round(basePrice * 100.0) / 100.0;
        }
        
        // Update prices in database if they've changed
        if (Math.abs(currentPrices[0] - basePrice / amount) > 0.01) {
            double newBuyPrice = Math.round((basePrice / amount) * 100.0) / 100.0; // Store per-item price
            double newSellPrice = calculateSellPrice(category, itemId, 1);
            plugin.getDatabaseManager().updatePrices(category, itemId, newBuyPrice, newSellPrice);
        }

        return basePrice;
    }

    public double calculateSellPrice(String category, String itemId, int amount) {
        ShopItem item = getItem(category, itemId);
        if (item == null) return -1;

        double[] currentPrices = plugin.getDatabaseManager().getCurrentPrices(category, itemId);
        if (currentPrices == null) return -1;

        // Get base price and current stock
        double basePrice = item.getSellPrice() * amount;
        int currentStock = plugin.getDatabaseManager().getStock(category, itemId);
        
        // Apply stock-based price fluctuation
        if (priceFluctuationEnabled) {
            double stockMultiplier = calculatePriceMultiplier(currentStock, item.getMaxStock());
            basePrice *= stockMultiplier;
            
            // Debug log the price calculation
            if (plugin.getConfig().getBoolean("debug", false)) {
                plugin.getLogger().info(String.format(
                    "[Debug] Sell price calculation for %s: Base=%.2f, Stock=%d/%d (%.1f%%), Multiplier=%.2f, Final=%.2f",
                    itemId, item.getSellPrice(), currentStock, item.getMaxStock(),
                    (double) currentStock / item.getMaxStock() * 100,
                    stockMultiplier, basePrice
                ));
            }
        }

        // Apply inflation to sell price (decreases price)
        basePrice *= plugin.getInflationManager().getSellPriceMultiplier();
        
        // Apply tax
        basePrice *= plugin.getTaxManager().getSellPriceMultiplier();
        
        // Ensure minimum price and round to 2 decimal places
        if (basePrice < 0.01) {
            basePrice = 0.01;
        } else {
            basePrice = Math.round(basePrice * 100.0) / 100.0;
        }

        return basePrice;
    }

    public boolean buyItem(Player player, String categoryId, String itemId, int quantity) {
        // Get item data
        ShopItem item = categories.get(categoryId).get(itemId);
        if (item == null) return false;

        // Check stock
        int currentStock = plugin.getDatabaseManager().getStock(categoryId, itemId);
        if (currentStock < quantity) {
            player.sendMessage(Component.text(plugin.getLocaleManager().getMessage("transaction.not_enough_stock")));
            return false;
        }

        // Check if player has enough inventory space FIRST
        if (!hasEnoughSpace(player, item.getMaterial(), quantity)) {
            player.sendMessage(Component.text(plugin.getLocaleManager().getMessage("transaction.inventory_full")));
            return false;
        }

        // Calculate total price
        double basePrice = calculateBuyPrice(categoryId, itemId, quantity);
        double tax = plugin.getTaxManager().calculateBuyTax(basePrice);
        double totalPrice = basePrice; // Player pays the base price, tax is calculated from it

        // Check if player has enough money
        Object playerData = getPlayerData(player);
        if (playerData == null) return false;

        double playerMoney = getPlayerMoney(playerData);
        if (playerMoney < totalPrice) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("price", String.format("%.2f", basePrice));
            placeholders.put("tax", String.format("%.2f", tax));
            player.sendMessage(Component.text(plugin.getLocaleManager().getMessage("transaction.not_enough_money", placeholders)));
            return false;
        }

        // Update stock
        plugin.getDatabaseManager().updateStock(categoryId, itemId, currentStock - quantity);

        // Update player money - remove only the base price
        setPlayerMoney(playerData, playerMoney - totalPrice);

        // Add tax to collected taxes (tax is a percentage of the base price)
        plugin.getTaxManager().addTax(tax);

        // Give items to player
        ItemStack boughtItem = new ItemStack(item.getMaterial(), quantity);
        player.getInventory().addItem(boughtItem);

        // Send success message
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("amount", String.valueOf(quantity));
        placeholders.put("item", item.getMaterial().name());
        placeholders.put("price", String.format("%.2f", basePrice));
        placeholders.put("tax", String.format("%.2f", tax));
        player.sendMessage(Component.text(plugin.getLocaleManager().getMessage("transaction.item_bought", placeholders)));

        return true;
    }

    public boolean sellItem(Player player, ItemStack item, int quantity) {
        // Find the item in the shop
        ShopItem shopItem = findShopItem(item.getType());
        if (shopItem == null) {
            player.sendMessage(Component.text(plugin.getLocaleManager().getMessage("shop.lore.not_in_shop")));
            return false;
        }

        // Check if player has enough items
        if (!hasEnoughItems(player, item.getType(), quantity)) {
            player.sendMessage(Component.text(plugin.getLocaleManager().getMessage("transaction.not_enough_items")));
            return false;
        }

        // Check if selling would exceed max stock
        int currentStock = plugin.getDatabaseManager().getStock(shopItem.getCategoryId(), shopItem.getItemId());
        if (currentStock + quantity > shopItem.getMaxStock()) {
            player.sendMessage(Component.text(plugin.getLocaleManager().getMessage("transaction.max_stock_reached")));
            return false;
        }

        // Calculate sell price and tax
        double basePrice = calculateSellPrice(shopItem.getCategoryId(), shopItem.getItemId(), quantity);
        double tax = plugin.getTaxManager().calculateSellTax(basePrice);
        double finalPrice = basePrice - tax; // Player receives price minus tax

        // Update player money
        Object playerData = getPlayerData(player);
        if (playerData == null) return false;

        double playerMoney = getPlayerMoney(playerData);
        setPlayerMoney(playerData, playerMoney + finalPrice);

        // Remove items from player
        removeItems(player, item.getType(), quantity);

        // Update stock
        plugin.getDatabaseManager().updateStock(shopItem.getCategoryId(), shopItem.getItemId(), currentStock + quantity);

        // Add tax to collected taxes
        plugin.getTaxManager().addTax(tax);

        // Send success message
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("amount", String.valueOf(quantity));
        placeholders.put("item", item.getType().name());
        placeholders.put("price", String.format("%.2f", basePrice));
        placeholders.put("tax", String.format("%.2f", tax));
        player.sendMessage(Component.text(plugin.getLocaleManager().getMessage("transaction.item_sold", placeholders)));

        return true;
    }

    public void sellAllItems(Player player) {
        double totalPrice = 0;
        double totalTax = 0;
        boolean soldAny = false;

        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null) continue;

            ShopItem shopItem = findShopItem(item.getType());
            if (shopItem == null) continue;

            int quantity = item.getAmount();
            
            // Check if selling would exceed max stock
            int currentStock = plugin.getDatabaseManager().getStock(shopItem.getCategoryId(), shopItem.getItemId());
            if (currentStock + quantity > shopItem.getMaxStock()) {
                // Skip this item if it would exceed max stock
                continue;
            }

            double basePrice = calculateSellPrice(shopItem.getCategoryId(), shopItem.getItemId(), quantity);
            double tax = plugin.getTaxManager().calculateSellTax(basePrice);
            double finalPrice = basePrice - tax;

            // Update stock
            plugin.getDatabaseManager().updateStock(shopItem.getCategoryId(), shopItem.getItemId(), currentStock + quantity);

            // Remove items from player
            removeItems(player, item.getType(), quantity);

            totalPrice += finalPrice;
            totalTax += tax;
            soldAny = true;
        }

        if (soldAny) {
            // Update player money
            Object playerData = getPlayerData(player);
            if (playerData != null) {
                double playerMoney = getPlayerMoney(playerData);
                setPlayerMoney(playerData, playerMoney + totalPrice);
            }

            // Add tax to collected taxes
            plugin.getTaxManager().addTax(totalTax);

            // Send success message
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("price", String.format("%.2f", totalPrice));
            player.sendMessage(Component.text(plugin.getLocaleManager().getMessage("transaction.items_sold_bulk", placeholders)));
        } else {
            player.sendMessage(Component.text(plugin.getLocaleManager().getMessage("shop.lore.no_items")));
        }
    }

    private boolean hasEnoughItems(Player player, Material material, int amount) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == material) {
                count += item.getAmount();
            }
        }
        return count >= amount;
    }

    private void removeItems(Player player, Material material, int amount) {
        int remaining = amount;
        ItemStack[] contents = player.getInventory().getContents();
        
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack item = contents[i];
            if (item != null && item.getType() == material) {
                if (item.getAmount() <= remaining) {
                    remaining -= item.getAmount();
                    player.getInventory().setItem(i, null);
                } else {
                    // Create a new item with reduced amount to avoid modifying the original
                    ItemStack newItem = new ItemStack(material, item.getAmount() - remaining);
                    player.getInventory().setItem(i, newItem);
                    remaining = 0;
                }
            }
        }
    }

    private boolean hasEnoughSpace(Player player, Material material, int amount) {
        int freeSpace = 0;
        ItemStack[] contents = player.getInventory().getStorageContents();
        
        for (ItemStack item : contents) {
            if (item == null) {
                freeSpace += material.getMaxStackSize();
            } else if (item.getType() == material) {
                freeSpace += material.getMaxStackSize() - item.getAmount();
            }
        }
        
        return freeSpace >= amount;
    }

    private ShopItem findShopItem(Material material) {
        for (Map<String, ShopItem> categoryItems : categories.values()) {
            for (ShopItem item : categoryItems.values()) {
                if (item.getMaterial() == material) {
                    return item;
                }
            }
        }
        return null;
    }

    private ShopItem getItem(String category, String itemId) {
        Map<String, ShopItem> items = categories.get(category);
        return items != null ? items.get(itemId) : null;
    }

    public Map<String, Map<String, ShopItem>> getCategories() {
        return categories;
    }

    public static class ShopItem {
        private final String categoryId;
        private final String itemId;
        private final Material material;
        private final double buyPrice;
        private final double sellPrice;
        private final int maxStock;

        public ShopItem(String categoryId, String itemId, Material material, double buyPrice, double sellPrice, int maxStock) {
            this.categoryId = categoryId;
            this.itemId = itemId;
            this.material = material;
            this.buyPrice = buyPrice;
            this.sellPrice = sellPrice;
            this.maxStock = maxStock;
        }

        public String getCategoryId() {
            return categoryId;
        }

        public String getItemId() {
            return itemId;
        }

        public Material getMaterial() {
            return material;
        }

        public double getBuyPrice() {
            return buyPrice;
        }

        public double getSellPrice() {
            return sellPrice;
        }

        public int getMaxStock() {
            return maxStock;
        }
    }
} 