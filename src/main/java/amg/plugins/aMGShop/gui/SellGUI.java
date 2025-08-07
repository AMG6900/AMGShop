package amg.plugins.aMGShop.gui;

import amg.plugins.aMGShop.AMGShop;
import amg.plugins.aMGShop.managers.ShopManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SellGUI implements Listener {
    private final AMGShop plugin;
    private final Map<Player, Inventory> sellMenus;
    private final int SELL_BUTTON_SLOT = 49;
    private final int INVENTORY_SIZE = 54; // 6 rows
    private final Map<Player, BukkitTask> updateTasks;

    public SellGUI(AMGShop plugin) {
        this.plugin = plugin;
        this.sellMenus = new HashMap<>();
        this.updateTasks = new HashMap<>();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void openSellMenu(Player player) {
        Component title = Component.text("Sell Items")
            .color(NamedTextColor.GOLD)
            .decoration(TextDecoration.ITALIC, false);
            
        Inventory menu = Bukkit.createInventory(null, INVENTORY_SIZE, title);
        
        // Add only bottom border and sell button
        addBorder(menu);
        addSellButton(menu);
        
        player.openInventory(menu);
        sellMenus.put(player, menu);
        
        // Start automatic price updates
        startPriceUpdates(player, menu);
    }
    
    private void startPriceUpdates(Player player, Inventory inventory) {
        // Cancel any existing task
        stopPriceUpdates(player);
        
        // Create a new task that updates prices every 5 ticks (0.25 seconds)
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (player.isOnline() && player.getOpenInventory().getTopInventory().equals(inventory)) {
                updateSellValue(player, inventory);
            } else {
                stopPriceUpdates(player);
            }
        }, 5L, 5L);
        
        updateTasks.put(player, task);
    }
    
    private void stopPriceUpdates(Player player) {
        BukkitTask task = updateTasks.remove(player);
        if (task != null) {
            task.cancel();
        }
    }

    private void addBorder(Inventory inventory) {
        ItemStack border = createGlassPane();
        
        // Only bottom row
        for (int i = 0; i < 9; i++) {
            inventory.setItem(INVENTORY_SIZE - 9 + i, border.clone());
        }
    }

    private ItemStack createGlassPane() {
        ItemStack pane = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        meta.displayName(Component.empty());
        pane.setItemMeta(meta);
        return pane;
    }

    private void addSellButton(Inventory inventory) {
        ItemStack sellButton = new ItemStack(Material.EMERALD);
        ItemMeta meta = sellButton.getItemMeta();
        meta.displayName(Component.text("Sell All Items")
            .color(NamedTextColor.GREEN)
            .decoration(TextDecoration.ITALIC, false));
        
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Place items in empty slots")
            .color(NamedTextColor.GRAY)
            .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("to see their sell value")
            .color(NamedTextColor.GRAY)
            .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        
        sellButton.setItemMeta(meta);
        inventory.setItem(SELL_BUTTON_SLOT, sellButton);
    }

    private void updateSellValue(Player player, Inventory inventory) {
        double totalValue = 0.0;
        Map<String, Map<String, Integer>> itemsToSell = new HashMap<>();

        // Calculate total value of all items
        for (int i = 0; i < inventory.getSize(); i++) {
            if (!isBorderSlot(i) && i != SELL_BUTTON_SLOT) {
                ItemStack item = inventory.getItem(i);
                if (item != null) {
                    String[] categoryAndItem = findCategoryAndItem(item.getType());
                    if (categoryAndItem != null) {
                        String category = categoryAndItem[0];
                        String itemId = categoryAndItem[1];
                        
                        itemsToSell.computeIfAbsent(category, k -> new HashMap<>());
                        itemsToSell.get(category).merge(itemId, item.getAmount(), Integer::sum);
                        
                        double price = plugin.getShopManager().calculateSellPrice(category, itemId, item.getAmount());
                        if (price > 0) {
                            totalValue += price;
                        }
                    }
                }
            }
        }

        // Update the sell button
        updateSellButton(inventory, totalValue);
    }

    private void updateSellButton(Inventory inventory, double totalValue) {
        ItemStack sellButton = inventory.getItem(SELL_BUTTON_SLOT);
        if (sellButton == null) return;

        ItemMeta meta = sellButton.getItemMeta();
        List<Component> lore = new ArrayList<>();
        
        if (totalValue > 0) {
            // Calculate total tax (already deducted from the price)
            double taxAmount = plugin.getTaxManager().calculateSellTax(totalValue);
            double priceBeforeTax = totalValue + taxAmount;
            
            lore.add(Component.text(String.format("Total Value: $%.2f", totalValue))
                .color(NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));
            if (plugin.getTaxManager().shouldShowTaxInLore()) {
                lore.add(Component.text(String.format("Tax Deducted: $%.2f", taxAmount))
                    .color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text(String.format("Value Before Tax: $%.2f", priceBeforeTax))
                    .color(NamedTextColor.GREEN)
                    .decoration(TextDecoration.ITALIC, false));
            }
            lore.add(Component.empty());
            lore.add(Component.text("Click to sell all items")
                .color(NamedTextColor.GREEN)
                .decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(Component.text("Place items in empty slots")
                .color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("to see their sell value")
                .color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        }
        
        meta.lore(lore);
        sellButton.setItemMeta(meta);
    }

    private void debug(String message) {
        if (plugin.getConfig().getBoolean("debug", false)) {
            plugin.getLogger().info("[Debug] " + message);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Inventory inventory = event.getInventory();
        
        if (!sellMenus.containsValue(inventory)) return;

        // Handle sell button click
        if (event.getRawSlot() == SELL_BUTTON_SLOT) {
            event.setCancelled(true);
            debug("Sell button clicked");
            handleSellAll(player, inventory);
            return;
        }

        // Handle border clicks
        if (isBorderSlot(event.getRawSlot())) {
            event.setCancelled(true);
            return;
        }

        // Allow placing items in empty slots within the sell menu
        if (event.getRawSlot() >= 0 && event.getRawSlot() < INVENTORY_SIZE) {
            // Update prices after a short delay
            Bukkit.getScheduler().runTaskLater(plugin, () -> updateSellValue(player, inventory), 1L);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        if (!sellMenus.containsValue(event.getInventory())) return;

        // Cancel if trying to drag onto border or buttons
        for (int slot : event.getRawSlots()) {
            if (slot >= 0 && slot < INVENTORY_SIZE && 
                (isBorderSlot(slot) || slot == SELL_BUTTON_SLOT)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    private void handleSellAll(Player player, Inventory inventory) {
        Map<String, Map<String, Integer>> itemsToSell = new HashMap<>();
        double totalValue = 0.0;
        StringBuilder logMessage = new StringBuilder();
        logMessage.append(player.getName()).append(" sold: ");

        // First pass: collect all items and verify they can be sold
        for (int i = 0; i < inventory.getSize(); i++) {
            if (!isBorderSlot(i) && i != SELL_BUTTON_SLOT) {
                ItemStack item = inventory.getItem(i);
                if (item != null) {
                    String[] categoryAndItem = findCategoryAndItem(item.getType());
                    if (categoryAndItem != null) {
                        String category = categoryAndItem[0];
                        String itemId = categoryAndItem[1];
                        
                        itemsToSell.computeIfAbsent(category, k -> new HashMap<>());
                        itemsToSell.get(category).merge(itemId, item.getAmount(), Integer::sum);
                    }
                }
            }
        }

        // Debug log
        debug("Items to sell: " + itemsToSell.size() + " categories");
        
        // Second pass: sell items and calculate total value
        for (Map.Entry<String, Map<String, Integer>> categoryEntry : itemsToSell.entrySet()) {
            String category = categoryEntry.getKey();
            debug("Processing category: " + category + " with " + categoryEntry.getValue().size() + " items");
            
            for (Map.Entry<String, Integer> itemEntry : categoryEntry.getValue().entrySet()) {
                String itemId = itemEntry.getKey();
                int amount = itemEntry.getValue();
                
                debug("Selling item: " + itemId + " x" + amount);

                // Get the material for this item
                Material material = null;
                for (ShopManager.ShopItem shopItem : plugin.getShopManager().getCategories().get(category).values()) {
                    if (shopItem.getMaterial().name().equalsIgnoreCase(itemId) || 
                        itemId.equalsIgnoreCase(shopItem.getMaterial().name())) {
                        material = shopItem.getMaterial();
                        break;
                    }
                }
                
                if (material == null) {
                    debug("Material not found for item: " + itemId);
                    continue;
                }

                // Calculate sell price for the total quantity
                double price = plugin.getShopManager().calculateSellPrice(category, itemId, amount);
                debug("Calculated price: $" + String.format("%.2f", price) + " for " + amount + " items");
                
                // Check if player has enough items
                if (!hasEnoughItems(player, material, amount)) {
                    String message = plugin.getConfig().getString("messages.not_enough_items", "&c&lError: &fYou don't have enough items to sell!");
                    player.sendMessage(formatMessage(message));
                    continue;
                }
                
                // Remove items from the inventory
                removeItems(inventory, material, amount);
                
                // Add money to player and update stock
                plugin.getShopManager().depositMoney(player, price);
                
                // Update stock in database
                int currentStock = plugin.getDatabaseManager().getStock(category, itemId);
                plugin.getDatabaseManager().updateStock(category, itemId, currentStock + amount);
                
                // Force recalculation of prices based on new stock level
                plugin.getShopManager().calculateBuyPrice(category, itemId, 1);
                plugin.getShopManager().calculateSellPrice(category, itemId, 1);
                
                // Add tax to collected taxes
                double taxAmount = plugin.getTaxManager().calculateSellTax(price);
                plugin.getTaxManager().addTax(taxAmount);
                
                totalValue += price;
                
                // Add to log message
                logMessage.append(amount).append("x ").append(itemId.toLowerCase())
                         .append(" ($").append(String.format("%.2f", price)).append("), ");
            }
        }

        if (totalValue > 0) {
            // Remove the last comma and space
            logMessage.setLength(logMessage.length() - 2);
            // Add total value to log message
            logMessage.append(" - Total: $").append(String.format("%.2f", totalValue));
            
            // Log the complete transaction
            plugin.getLogManager().logSale(player, "BULK_SALE", logMessage.toString(), 1, totalValue, 0, 0);

            String message = plugin.getConfig().getString("messages.items_sold_bulk", "&a&lSuccess! &fYou sold items for &e$%price%")
                .replace("%price%", String.format("%.2f", totalValue));
            player.sendMessage(formatMessage(message));
            
            // Update player inventory
            player.updateInventory();
            
            // Refresh the shop menus to reflect updated prices and stock
            if (plugin instanceof AMGShop amgShop && amgShop.getShopGUI() != null) {
                amgShop.getShopGUI().refreshCategoryMenus();
            }
        } else {
            player.sendMessage(formatMessage("&c&lError: &fNo items were sold."));
        }

        // Update the inventory
        updateSellValue(player, inventory);
    }

    private String formatMessage(String message) {
        return message.replace("&", "§");
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!sellMenus.containsValue(event.getInventory())) return;

        // Stop price updates
        stopPriceUpdates(player);

        // Return any items in the sell menu to the player
        Inventory inventory = event.getInventory();
        for (int i = 0; i < inventory.getSize(); i++) {
            if (!isBorderSlot(i) && i != SELL_BUTTON_SLOT) {
                ItemStack item = inventory.getItem(i);
                if (item != null) {
                    // Return the item without any metadata
                    ItemStack cleanItem = new ItemStack(item.getType(), item.getAmount());
                    if (!item.getEnchantments().isEmpty()) {
                        cleanItem.addEnchantments(item.getEnchantments());
                    }
                    player.getInventory().addItem(cleanItem);
                }
            }
        }

        sellMenus.remove(player);
    }

    private boolean isBorderSlot(int slot) {
        int row = slot / 9;
        return row == 5; // Only bottom row is border
    }

    private String[] findCategoryAndItem(Material material) {
        Map<String, Map<String, ShopManager.ShopItem>> categories = plugin.getShopManager().getCategories();
        
        for (Map.Entry<String, Map<String, ShopManager.ShopItem>> category : categories.entrySet()) {
            for (Map.Entry<String, ShopManager.ShopItem> item : category.getValue().entrySet()) {
                if (item.getValue().getMaterial() == material) {
                    return new String[]{category.getKey(), item.getKey()};
                }
            }
        }
        
        return null;
    }

    private void removeItems(Inventory inventory, Material material, int amount) {
        int remaining = amount;
        
        for (int i = 0; i < inventory.getSize() && remaining > 0; i++) {
            if (!isBorderSlot(i) && i != SELL_BUTTON_SLOT) {
                ItemStack item = inventory.getItem(i);
                if (item != null && item.getType() == material) {
                    if (item.getAmount() <= remaining) {
                        remaining -= item.getAmount();
                        inventory.setItem(i, null);
                    } else {
                        // Create a new item with reduced amount to avoid modifying the original
                        ItemStack newItem = new ItemStack(material, item.getAmount() - remaining);
                        inventory.setItem(i, newItem);
                        remaining = 0;
                    }
                }
            }
        }
    }

    // Add helper method to check if player has enough items
    private boolean hasEnoughItems(Player player, Material material, int amount) {
        int count = 0;
        // Check items in the sell GUI inventory instead of player's inventory
        Inventory inventory = sellMenus.get(player);
        if (inventory == null) return false;
        
        for (int i = 0; i < inventory.getSize(); i++) {
            // Don't exclude border slots - allow selling from all slots in the inventory
            ItemStack item = inventory.getItem(i);
            if (item != null && item.getType() == material) {
                count += item.getAmount();
            }
        }
        return count >= amount;
    }
} 