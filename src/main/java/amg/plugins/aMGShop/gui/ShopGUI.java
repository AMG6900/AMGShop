package amg.plugins.aMGShop.gui;

import amg.plugins.aMGShop.AMGShop;
import amg.plugins.aMGShop.managers.ShopManager.ShopItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;

import java.util.*;

public class ShopGUI implements Listener {
    private final AMGShop plugin;
    private final Map<String, List<Inventory>> categoryPages;
    private final Map<Player, Integer> currentPage;
    private final Map<Player, QuantitySelectionData> quantitySelections;
    private final Map<UUID, Inventory> playerBuyMenus;
    private final Map<UUID, Inventory> playerSellMenus;
    private final Map<Inventory, Boolean> quantityMenus; // Track quantity selection menus
    private final Map<Player, Long> lastClickTime; // Track last click time for cooldown
    private final SellGUI sellGUI;

    public ShopGUI(AMGShop plugin) {
        this.plugin = plugin;
        this.categoryPages = new HashMap<>();
        this.currentPage = new HashMap<>();
        this.quantitySelections = new HashMap<>();
        this.playerBuyMenus = new HashMap<>();
        this.playerSellMenus = new HashMap<>();
        this.quantityMenus = new HashMap<>();
        this.lastClickTime = new HashMap<>();
        this.sellGUI = new SellGUI(plugin);
        
        // Register event listeners
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        
        // Register player quit listener to clean up menus
        plugin.getServer().getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event) {
                cleanup(event.getPlayer());
            }
        }, plugin);
        
        // Initialize category pages
        createCategoryMenus();
    }
    
    private void cleanup(Player player) {
        synchronized (playerBuyMenus) {
            playerBuyMenus.remove(player.getUniqueId());
        }
        synchronized (playerSellMenus) {
            playerSellMenus.remove(player.getUniqueId());
        }
        synchronized (currentPage) {
            currentPage.remove(player);
        }
        synchronized (quantitySelections) {
            quantitySelections.remove(player);
        }
        synchronized (lastClickTime) {
            lastClickTime.remove(player);
        }
        // Clean up quantity menus for this player
        synchronized (quantityMenus) {
            quantityMenus.entrySet().removeIf(entry -> {
                // Check if any player has this inventory open
                return plugin.getServer().getOnlinePlayers().stream()
                    .noneMatch(p -> p.getOpenInventory().getTopInventory().equals(entry.getKey()));
            });
        }
    }

    private Inventory createSellMenu(Player player) {
        Component title = Component.text(plugin.getLocaleManager().getMessage("shop.sell_menu_title"))
            .color(NamedTextColor.GOLD)
            .decoration(TextDecoration.ITALIC, false);
        int rows = plugin.getConfig().getInt("shop.rows", 6);
        Inventory menu = Bukkit.createInventory(null, rows * 9, title);

        // Fill border with glass panes
        for (int i = 0; i < 9; i++) {
            menu.setItem(i, createGlassPane(0)); // Top row
            menu.setItem(menu.getSize() - 9 + i, createGlassPane(0)); // Bottom row
        }
        for (int i = 0; i < rows; i++) {
            menu.setItem(i * 9, createGlassPane(1)); // Left column
            menu.setItem(i * 9 + 8, createGlassPane(1)); // Right column
        }

        // Add category buttons
        ConfigurationSection categories = plugin.getConfig().getConfigurationSection("categories");
        if (categories != null) {
            for (String categoryId : categories.getKeys(false)) {
                ConfigurationSection category = categories.getConfigurationSection(categoryId);
                if (category != null) {
                    createCategoryButton(category, categoryId, menu);
                }
            }
        }

        // Add buttons in the bottom row
        addBottomButtons(menu);

        // Fill remaining slots with glass panes
        fillEmptySlots(menu, 0, menu.getSize() - 1);
        
        // Store menu for this player
        playerSellMenus.put(player.getUniqueId(), menu);
        
        return menu;
    }

    private void debug(String message) {
        if (plugin.getConfig().getBoolean("debug", false)) {
            plugin.getLogger().info("[Debug] " + message);
        }
    }



    private ItemStack createGlassPane(int colorIndex) {
        Material[] glassPanes = {
            Material.RED_STAINED_GLASS_PANE,
            Material.RED_STAINED_GLASS_PANE,
            Material.RED_STAINED_GLASS_PANE
        };
        ItemStack pane = new ItemStack(glassPanes[colorIndex % glassPanes.length]);
        ItemMeta meta = pane.getItemMeta();
        meta.displayName(Component.empty());
        pane.setItemMeta(meta);
        return pane;
    }

    private void fillEmptySlots(Inventory inventory, int startSlot, int endSlot) {
        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null && i >= startSlot && i <= endSlot) {
                inventory.setItem(i, createGlassPane(i));
            }
        }
    }

    private Inventory createBuyMenu(Player player) {
        Component title = Component.text(plugin.getLocaleManager().getMessage("shop.buy_menu_title"))
            .color(NamedTextColor.GOLD)
            .decoration(TextDecoration.ITALIC, false);
        int rows = plugin.getConfig().getInt("shop.rows", 6);
        Inventory menu = Bukkit.createInventory(null, rows * 9, title);

        // Fill border with glass panes
        for (int i = 0; i < 9; i++) {
            menu.setItem(i, createGlassPane(0)); // Top row
            menu.setItem(menu.getSize() - 9 + i, createGlassPane(0)); // Bottom row
        }
        for (int i = 0; i < rows; i++) {
            menu.setItem(i * 9, createGlassPane(1)); // Left column
            menu.setItem(i * 9 + 8, createGlassPane(1)); // Right column
        }

        // Add category buttons
        ConfigurationSection categories = plugin.getConfig().getConfigurationSection("categories");
        if (categories != null) {
            for (String categoryId : categories.getKeys(false)) {
                ConfigurationSection category = categories.getConfigurationSection(categoryId);
                if (category != null) {
                    String name = category.getString("name", categoryId);
                    String iconName = category.getString("icon", "BARRIER");
                    int slot = category.getInt("slot", 0);

                    Material icon;
                    try {
                        icon = Material.valueOf(iconName.toUpperCase());
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("Invalid material for category " + categoryId + ": " + iconName);
                        icon = Material.BARRIER;
                    }

                    ItemStack item = new ItemStack(icon);
                    ItemMeta meta = item.getItemMeta();
                    meta.displayName(Component.text(name.replace("&", "§"))
                        .decoration(TextDecoration.ITALIC, false));

                    // Add category metadata
                    NamespacedKey categoryKey = new NamespacedKey(plugin, "category_id");
                    meta.getPersistentDataContainer().set(categoryKey, PersistentDataType.STRING, categoryId);

                    // Add lore with item count
                    List<Component> lore = new ArrayList<>();
                    FileConfiguration categoryConfig = plugin.getConfigManager().getCategoryConfig(categoryId);
                    if (categoryConfig != null) {
                        ConfigurationSection items = categoryConfig.getConfigurationSection("items");
                        if (items != null) {
                            int itemCount = items.getKeys(false).size();
                            Map<String, String> itemCountPlaceholders = new HashMap<>();
                            itemCountPlaceholders.put("count", String.valueOf(itemCount));
                            lore.add(Component.text(plugin.getLocaleManager().getMessage("shop.lore.item_count", itemCountPlaceholders))
                                .color(NamedTextColor.GRAY)
                                .decoration(TextDecoration.ITALIC, false));
                            lore.add(Component.empty());
                            lore.add(Component.text(plugin.getLocaleManager().getMessage("shop.lore.click_to_buy"))
                                .color(NamedTextColor.YELLOW)
                                .decoration(TextDecoration.ITALIC, false));
                        }
                    }
                    meta.lore(lore);
                    item.setItemMeta(meta);
                    menu.setItem(slot, item);
                }
            }
        }

        // Add buttons in the bottom row
        addBottomButtons(menu);

        // Fill remaining slots with glass panes
        fillEmptySlots(menu, 0, menu.getSize() - 1);
        
        // Store the menu for this player
        playerBuyMenus.put(player.getUniqueId(), menu);
        
        return menu;
    }

    private void addBottomButtons(Inventory inventory) {
        // Add tax collection button if player is shop owner, otherwise add info button
        addSpecialButton(inventory);

        // Add close button
        ItemStack closeButton = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = closeButton.getItemMeta();
        closeMeta.displayName(Component.text(plugin.getLocaleManager().getMessage("shop.buttons.close"))
            .color(NamedTextColor.RED)
            .decoration(TextDecoration.ITALIC, false));
        closeButton.setItemMeta(closeMeta);
        inventory.setItem(50, closeButton);

        // Add player head with balance
        ItemStack playerHead = new ItemStack(Material.PLAYER_HEAD);
        if (playerHead.getItemMeta() instanceof SkullMeta skullMeta) {
            skullMeta.displayName(Component.text(plugin.getLocaleManager().getMessage("shop.buttons.your_balance"))
                .color(NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));
            skullMeta.setOwningPlayer(Bukkit.getOfflinePlayer("MHF_Question")); // Default head
            playerHead.setItemMeta(skullMeta);
        }
        inventory.setItem(48, playerHead);
    }

    private void addSpecialButton(Inventory inventory) {
        // This button will be replaced with either tax collection or info button when a player opens the inventory
        ItemStack placeholderButton = new ItemStack(Material.GOLD_INGOT);
        ItemMeta meta = placeholderButton.getItemMeta();
        meta.displayName(Component.text(plugin.getLocaleManager().getMessage("shop.buttons.loading"))
            .color(NamedTextColor.GRAY)
            .decoration(TextDecoration.ITALIC, false));
        placeholderButton.setItemMeta(meta);
        inventory.setItem(49, placeholderButton);
    }

    private void updatePlayerHead(Player player, Inventory inventory) {
        ItemStack playerHead = inventory.getItem(48);
        if (playerHead != null && playerHead.getItemMeta() instanceof SkullMeta skullMeta) {
            skullMeta.setOwningPlayer(player);
            
            double balance = plugin.getShopManager().getPlayerMoney(player);
            
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(String.format("$%.2f", balance))
                .color(NamedTextColor.GREEN)
                .decoration(TextDecoration.ITALIC, false));
            
            skullMeta.lore(lore);
            playerHead.setItemMeta(skullMeta);
        }
    }



    private void createCategoryButton(ConfigurationSection category, String categoryId, Inventory menu) {
        FileConfiguration categoryConfig = plugin.getConfigManager().getCategoryConfig(categoryId);
        if (categoryConfig == null) return;
        
        Material icon = Material.valueOf(categoryConfig.getString("icon", "BARRIER"));
        int slot = categoryConfig.getInt("slot", 0);

        ItemStack item = new ItemStack(icon);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(plugin.getLocaleManager().getMessage("shop.category." + categoryId))
            .color(NamedTextColor.GOLD)
            .decoration(TextDecoration.ITALIC, false));

        // Add category metadata
        NamespacedKey categoryKey = new NamespacedKey(plugin, "category_id");
        meta.getPersistentDataContainer().set(categoryKey, PersistentDataType.STRING, categoryId);

        // Add lore with item count
        List<Component> lore = new ArrayList<>();
        ConfigurationSection items = categoryConfig.getConfigurationSection("items");
        if (items != null) {
            int itemCount = items.getKeys(false).size();
            lore.add(Component.text("Contains " + itemCount + " items")
                .color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
            lore.add(Component.text(plugin.getLocaleManager().getMessage("shop.lore.click_to_buy"))
                .color(NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);

        item.setItemMeta(meta);
        menu.setItem(slot, item);
    }

    private void addTaxCollectionButton(Player player, Inventory inventory) {
        if (!plugin.getTaxManager().isOwner(player)) {
            addInfoButton(inventory);
            return;
        }

        int slot = plugin.getConfig().getInt("shop.tax.collection_button.slot", 49);
        Material material = Material.valueOf(plugin.getConfig().getString("shop.tax.collection_button.material", "GOLD_INGOT"));

        ItemStack button = new ItemStack(material);
        ItemMeta meta = button.getItemMeta();
        meta.displayName(Component.text(plugin.getLocaleManager().getMessage("tax.button_name"))
            .color(NamedTextColor.GOLD)
            .decoration(TextDecoration.ITALIC, false));

        double taxes = plugin.getTaxManager().getCollectedTaxes();
        List<Component> lore = new ArrayList<>();
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("amount", String.format("%.2f", taxes));
        lore.add(Component.text(plugin.getLocaleManager().getMessage("tax.button_lore", placeholders))
            .color(NamedTextColor.GRAY)
            .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        button.setItemMeta(meta);

        inventory.setItem(slot, button);
    }

    private void addInfoButton(Inventory inventory) {
        ItemStack infoButton = new ItemStack(Material.BOOK);
        ItemMeta meta = infoButton.getItemMeta();
        meta.displayName(Component.text(plugin.getLocaleManager().getMessage("info.title"))
            .color(NamedTextColor.AQUA)
            .decoration(TextDecoration.ITALIC, false));
        
        List<Component> lore = new ArrayList<>();
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("owner", plugin.getConfig().getString("shop.owner.name", "None"));
        lore.add(Component.text(plugin.getLocaleManager().getMessage("info.owner", placeholders))
            .color(NamedTextColor.GRAY)
            .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        meta.lore(lore);
        
        infoButton.setItemMeta(meta);
        inventory.setItem(49, infoButton);
    }

    private void createCategoryMenus() {
        debug("Creating category menus...");
        categoryPages.clear(); // Clear existing menus
        Map<String, Map<String, ShopItem>> categories = plugin.getShopManager().getCategories();
        
        for (Map.Entry<String, Map<String, ShopItem>> entry : categories.entrySet()) {
            String categoryId = entry.getKey();
            Map<String, ShopItem> items = entry.getValue();

            ConfigurationSection category = plugin.getConfig().getConfigurationSection("categories." + categoryId);
            if (category == null) continue;

            String name = category.getString("name", categoryId).replace("&", "§");

            // Use items in the order they appear in the config file
            List<Map.Entry<String, ShopItem>> sortedItems = new ArrayList<>(items.entrySet());
            
            // Get the category config file to preserve order
            FileConfiguration categoryConfig = plugin.getConfigManager().getCategoryConfig(categoryId);
            if (categoryConfig != null && categoryConfig.isConfigurationSection("items")) {
                // Get the keys in the order they appear in the file
                ConfigurationSection itemsSection = categoryConfig.getConfigurationSection("items");
                List<String> orderedKeys = new ArrayList<>(itemsSection.getKeys(false));
                
                // Sort based on file order
                Collections.sort(sortedItems, (a, b) -> {
                    int indexA = orderedKeys.indexOf(a.getKey());
                    int indexB = orderedKeys.indexOf(b.getKey());
                    return Integer.compare(indexA, indexB);
                });
                
                debug("Sorted " + sortedItems.size() + " items by config file order for category: " + categoryId);
            } else {
                // Fallback to alphabetical sorting if config isn't available
                Collections.sort(sortedItems, (a, b) -> a.getKey().compareTo(b.getKey()));
                debug("Using alphabetical sorting for category: " + categoryId);
            }

            List<Inventory> pages = new ArrayList<>();
            int pageNumber = 1;

            // Calculate how many pages we need
            int itemsPerPage = 28; // 7x4 grid for items
            int totalPages = (int) Math.ceil((double) sortedItems.size() / itemsPerPage);
            debug("Category " + categoryId + " requires " + totalPages + " pages for " + sortedItems.size() + " items");

            for (int page = 0; page < totalPages; page++) {
                Component title = Component.text(name + " (Page " + (page + 1) + "/" + totalPages + ")")
                    .color(NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false);
                Inventory menu = Bukkit.createInventory(null, 54, title);

                // Fill border with glass panes
                for (int i = 0; i < 9; i++) {
                    menu.setItem(i, createGlassPane(0)); // Top row
                    menu.setItem(menu.getSize() - 9 + i, createGlassPane(0)); // Bottom row
                }
                for (int i = 0; i < 6; i++) {
                    menu.setItem(i * 9, createGlassPane(1)); // Left column
                    menu.setItem(i * 9 + 8, createGlassPane(1)); // Right column
                }

                // Add items for this page
                int startIndex = page * itemsPerPage;
                int endIndex = Math.min(startIndex + itemsPerPage, sortedItems.size());

                int row = 1; // Start at second row
                int col = 1; // Start after left border

                for (int i = startIndex; i < endIndex; i++) {
                    // Calculate slot
                    int slot = row * 9 + col;
                    
                    Map.Entry<String, ShopItem> itemEntry = sortedItems.get(i);
                    String itemId = itemEntry.getKey();
                    ShopItem shopItem = itemEntry.getValue();

                    ItemStack item = createShopItem(categoryId, itemId, shopItem);
                    menu.setItem(slot, item);

                    // Move to next position
                    col++;
                    if (col == 8) { // If we've reached the right border
                        col = 1;    // Reset to first column
                        row++;      // Move to next row
                    }
                }

                // Add navigation buttons
                if (page > 0) {
                    // Add previous page button
                    ItemStack prevButton = new ItemStack(Material.ARROW);
                    ItemMeta prevMeta = prevButton.getItemMeta();
                    prevMeta.displayName(Component.text(plugin.getLocaleManager().getMessage("shop.buttons.prev_page"))
                        .color(NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false));
                    List<Component> prevLore = new ArrayList<>();
                    Map<String, String> prevPlaceholders = new HashMap<>();
                    prevPlaceholders.put("page", String.valueOf(page));
                    prevLore.add(Component.text(plugin.getLocaleManager().getMessage("shop.buttons.prev_page_lore", prevPlaceholders))
                        .color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false));
                    prevMeta.lore(prevLore);
                    prevButton.setItemMeta(prevMeta);
                    menu.setItem(45, prevButton);
                }

                if (page < totalPages - 1) {
                    // Add next page button
                    ItemStack nextButton = new ItemStack(Material.ARROW);
                    ItemMeta nextMeta = nextButton.getItemMeta();
                    nextMeta.displayName(Component.text(plugin.getLocaleManager().getMessage("shop.buttons.next_page"))
                        .color(NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false));
                    List<Component> nextLore = new ArrayList<>();
                    Map<String, String> nextPlaceholders = new HashMap<>();
                    nextPlaceholders.put("page", String.valueOf(page + 2));
                    nextLore.add(Component.text(plugin.getLocaleManager().getMessage("shop.buttons.next_page_lore", nextPlaceholders))
                        .color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false));
                    nextMeta.lore(nextLore);
                    nextButton.setItemMeta(nextMeta);
                    menu.setItem(53, nextButton);
                    debug("Added next page button on page " + (page + 1) + " to go to page " + (page + 2));
                }

                // Add back button
                ItemStack backButton = new ItemStack(Material.BARRIER);
                ItemMeta backMeta = backButton.getItemMeta();
                backMeta.displayName(Component.text(plugin.getLocaleManager().getMessage("shop.buttons.back_to_main"))
                    .color(NamedTextColor.RED)
                    .decoration(TextDecoration.ITALIC, false));
                backButton.setItemMeta(backMeta);
                menu.setItem(49, backButton);

                pages.add(menu);
                debug("Created page " + pageNumber + " for category " + categoryId + 
                      " with prev=" + (page > 0) + ", next=" + (page < totalPages - 1));
                pageNumber++;
            }

            // Store the pages
            categoryPages.put(categoryId, pages);
            debug("Created category menu for: " + categoryId + " with " + sortedItems.size() + " items across " + pages.size() + " pages");
        }
    }

    private ItemStack createShopItem(String categoryId, String itemId, ShopItem shopItem) {
        ItemStack item = new ItemStack(shopItem.getMaterial());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(itemId.replace("_", " ").toUpperCase())
            .color(NamedTextColor.GOLD)
            .decoration(TextDecoration.ITALIC, false));

        // Store category and item IDs in item metadata
        NamespacedKey categoryKey = new NamespacedKey(plugin, "category_id");
        NamespacedKey itemKey = new NamespacedKey(plugin, "item_id");
        meta.getPersistentDataContainer().set(categoryKey, PersistentDataType.STRING, categoryId);
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, itemId);

        List<Component> lore = new ArrayList<>();
        int currentStock = plugin.getDatabaseManager().getStock(categoryId, itemId);
        double buyPrice = plugin.getShopManager().calculateBuyPrice(categoryId, itemId, 1);
        double taxAmount = plugin.getTaxManager().calculateBuyTax(buyPrice);
        
        Map<String, String> stockPlaceholders = new HashMap<>();
        stockPlaceholders.put("amount", String.valueOf(currentStock));
        lore.add(Component.text(plugin.getLocaleManager().getMessage("shop.lore.stock", stockPlaceholders))
            .color(NamedTextColor.GRAY)
            .decoration(TextDecoration.ITALIC, false));

        Map<String, String> pricePlaceholders = new HashMap<>();
        pricePlaceholders.put("price", String.format("%.2f", buyPrice));
        lore.add(Component.text(plugin.getLocaleManager().getMessage("shop.lore.price", pricePlaceholders))
            .color(NamedTextColor.GREEN)
            .decoration(TextDecoration.ITALIC, false));
        if (plugin.getTaxManager().shouldShowTaxInLore()) {
            lore.add(Component.text(String.format("Includes Tax: $%.2f", taxAmount))
                .color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        }
        if (plugin.getConfig().getBoolean("shop.inflation.enabled", true)) {
            Map<String, String> inflationPlaceholders = new HashMap<>();
            inflationPlaceholders.put("rate", String.format("%.1f", plugin.getInflationManager().getInflationRate()));
            lore.add(Component.text(plugin.getLocaleManager().getMessage("shop.lore.inflation", inflationPlaceholders))
                .color(NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false));
        }
        lore.add(Component.empty());
        lore.add(Component.text(plugin.getLocaleManager().getMessage("shop.lore.click_to_buy"))
            .color(NamedTextColor.YELLOW)
            .decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    public void openBuyMenu(Player player) {
        // Run menu creation in the main thread
        Bukkit.getScheduler().runTask(plugin, () -> {
            synchronized (playerBuyMenus) {
                Inventory menu = playerBuyMenus.get(player.getUniqueId());
                if (menu == null) {
                    menu = createBuyMenu(player);
                    playerBuyMenus.put(player.getUniqueId(), menu);
                }
                player.openInventory(menu);
                updatePlayerHead(player, menu);
            }
        });
    }

    public void openSellMenu(Player player) {
        // Run menu creation in the main thread
        Bukkit.getScheduler().runTask(plugin, () -> {
            synchronized (playerSellMenus) {
                Inventory menu = playerSellMenus.get(player.getUniqueId());
                if (menu == null) {
                    menu = createSellMenu(player);
                    playerSellMenus.put(player.getUniqueId(), menu);
                }
                player.openInventory(menu);
                updatePlayerHead(player, menu);
                sellGUI.openSellMenu(player);
            }
        });
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        
        // Check if this is a quantity selection menu first
        if (quantityMenus.containsKey(event.getView().getTopInventory())) {
            event.setCancelled(true); // Cancel ALL clicks in quantity menu
            if (event.getCurrentItem() != null && event.getClickedInventory() != null) {
                handleQuantitySelectionClick(event);
            }
            return;
        }
        
        // Handle other shop inventories
        if (event.getCurrentItem() == null) return;
        if (event.getClickedInventory() == null) return;

        // Check if this is a shop inventory
        if (!isShopInventory(event.getView().getTopInventory())) {
            return;
        }

        event.setCancelled(true);

        ItemStack clickedItem = event.getCurrentItem();
        debug("Clicked item: " + clickedItem.getType() + " in slot " + event.getSlot());

        if (clickedItem.getType() == Material.WHITE_STAINED_GLASS_PANE ||
            clickedItem.getType() == Material.LIGHT_BLUE_STAINED_GLASS_PANE ||
            clickedItem.getType() == Material.BLUE_STAINED_GLASS_PANE ||
            clickedItem.getType() == Material.RED_STAINED_GLASS_PANE) {
            return;
        }

        // Handle close button (in main menu) and back button (in category menu)
        if (clickedItem.getType() == Material.BARRIER) {
            debug("Barrier clicked in slot " + event.getSlot());
            String title = PlainTextComponentSerializer.plainText().serialize(event.getView().title());
            String mainMenuTitle = plugin.getLocaleManager().getMessage("shop.buy_menu_title");
            debug("Current menu title: '" + title + "', Main menu title: '" + mainMenuTitle + "'");
            
            if (event.getSlot() == 50 || event.getSlot() == 49) {
                // If we're in a category menu, go back to main menu
                if (!title.equals(mainMenuTitle)) {
                    debug("In category menu, returning to main menu");
                    openBuyMenu(player);
                } else if (event.getSlot() == 50) { // Only close if it's the main menu close button
                    // If we're in main menu, close the inventory
                    debug("In main menu, closing inventory");
                    event.getView().close();
                }
                return;
            } else if (event.getSlot() == 26) {
                // Close button in quantity selection menu
                debug("Closing quantity selection menu");
                event.getView().close();
                return;
            }
        }

        // Handle navigation buttons
        if (clickedItem.getType() == Material.ARROW) {
            debug("Navigation button clicked in slot " + event.getSlot());
            handleNavigationClick(event);
            return;
        }

        // Handle player head
        if (clickedItem.getType() == Material.PLAYER_HEAD && event.getSlot() == 48) {
            return; // Just show info, no action
        }

        // Handle info button
        if (clickedItem.getType() == Material.BOOK && event.getSlot() == 49) {
            return; // Just show info, no action
        }

        ItemMeta meta = clickedItem.getItemMeta();
        if (meta == null) return;

        // Check for tax collection button
        Material taxButtonMaterial = Material.valueOf(
            plugin.getConfig().getString("shop.tax.collection_button.material", "GOLD_INGOT"));
        if (clickedItem.getType() == taxButtonMaterial && 
            event.getSlot() == plugin.getConfig().getInt("shop.tax.collection_button.slot", 49)) {
            // Only allow tax collection for shop owner
            if (plugin.getTaxManager().isOwner(player)) {
                handleTaxCollection(player);
            } else {
                String message = plugin.getConfig().getString("messages.not_shop_owner", "&c&lError: &fOnly the shop owner can do this!");
                player.sendMessage(formatMessage(message));
            }
            return;
        }

        // Get stored category and item IDs
        NamespacedKey categoryKey = new NamespacedKey(plugin, "category_id");
        NamespacedKey itemKey = new NamespacedKey(plugin, "item_id");

        String categoryId = meta.getPersistentDataContainer().get(categoryKey, PersistentDataType.STRING);
        String itemId = meta.getPersistentDataContainer().has(itemKey, PersistentDataType.STRING) ?
            meta.getPersistentDataContainer().get(itemKey, PersistentDataType.STRING) : null;

        // Debug logging
        debug("Click detected - Category: " + categoryId + ", Item: " + itemId + 
            ", Material: " + clickedItem.getType() + ", Name: " + meta.displayName());

        if (categoryId != null) {
            if (itemId != null) {
                // This is an item click - open quantity selection menu
                debug("Opening quantity selection menu for: " + itemId + " in category " + categoryId);
                openQuantitySelectionMenu(player, categoryId, itemId);
            } else if (categoryPages.containsKey(categoryId)) {
                // This is a category click
                debug("Opening category: " + categoryId + " (Menu exists: " + (categoryPages.get(categoryId) != null) + ")");
                Inventory categoryMenu = categoryPages.get(categoryId).get(0); // Always start at first page
                if (categoryMenu != null) {
                    currentPage.put(player, 0); // Reset page number
                    player.openInventory(categoryMenu);
                } else {
                    plugin.getLogger().warning("Category menu is null for: " + categoryId);
                }
            } else {
                plugin.getLogger().warning("Category menu not found for: " + categoryId);
            }
        }
    }

    private void handleNavigationClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        String title = PlainTextComponentSerializer.plainText().serialize(event.getView().title());
        debug("Processing navigation click. Title: " + title);
        
        // Extract category name and page info from title
        int pageIndex = title.lastIndexOf(" (Page ");
        if (pageIndex == -1) {
            debug("No page info found in title: " + title);
            return;
        }
        
        try {
            // Get the category name (everything before " (Page ")
            String categoryName = title.substring(0, pageIndex).replaceAll("§[0-9a-fk-or]", "");
            debug("Category name: " + categoryName);
            
            // Parse page numbers (format: "Page X/Y)")
            String pageInfo = title.substring(pageIndex + 7); // Skip " (Page "
            pageInfo = pageInfo.substring(0, pageInfo.length() - 1); // Remove last ")"
            String[] pageParts = pageInfo.split("/");
            if (pageParts.length != 2) {
                debug("Invalid page format in title: " + title);
                return;
            }
            
            int currentPageNum = Integer.parseInt(pageParts[0].trim()) - 1; // Convert to 0-based index
            int totalPages = Integer.parseInt(pageParts[1].trim());
            debug("Current page: " + (currentPageNum + 1) + ", Total pages: " + totalPages);
            
            // Find the category ID
            String categoryId = null;
            for (String id : categoryPages.keySet()) {
                String name = plugin.getConfig().getString("categories." + id + ".name", "").replace("&", "§");
                name = name.replaceAll("§[0-9a-fk-or]", "");
                if (name.equals(categoryName)) {
                    categoryId = id;
                    break;
                }
            }
            
            if (categoryId == null) {
                debug("Could not find category for name: " + categoryName);
                return;
            }
            debug("Found category ID: " + categoryId);
            
            List<Inventory> pages = categoryPages.get(categoryId);
            if (pages == null) {
                debug("No pages found for category: " + categoryId);
                return;
            }
            debug("Category has " + pages.size() + " pages");
            
            // Handle navigation
            int slot = event.getSlot();
            debug("Clicked slot: " + slot + ", Current page: " + (currentPageNum + 1) + "/" + totalPages);
            
            if (slot == 45 && currentPageNum > 0) {
                // Previous page
                debug("Moving to previous page");
                currentPage.put(player, currentPageNum - 1);
                Inventory prevPage = pages.get(currentPageNum - 1);
                if (prevPage != null) {
                    player.openInventory(prevPage);
                    debug("Opened previous page " + currentPageNum + " for " + player.getName());
                } else {
                    debug("Previous page inventory is null!");
                }
            } else if (slot == 53 && currentPageNum < totalPages - 1) {
                // Next page
                debug("Moving to next page");
                currentPage.put(player, currentPageNum + 1);
                Inventory nextPage = pages.get(currentPageNum + 1);
                if (nextPage != null) {
                    player.openInventory(nextPage);
                    debug("Opened next page " + (currentPageNum + 2) + " for " + player.getName());
                } else {
                    debug("Next page inventory is null!");
                }
            } else {
                debug("Navigation conditions not met: slot=" + slot + 
                      ", currentPage=" + currentPageNum + 
                      ", totalPages=" + totalPages);
            }
        } catch (Exception e) {
            debug("Error in navigation: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        // Cancel drags in any shop-related inventory including quantity selection
        if (quantityMenus.containsKey(event.getInventory()) ||
            playerBuyMenus.containsValue(event.getInventory()) ||
            playerSellMenus.containsValue(event.getInventory()) ||
            isInventoryInCategoryPages(event.getInventory())) {
            event.setCancelled(true);
        }
    }

    private boolean isInventoryInCategoryPages(Inventory inventory) {
        for (List<Inventory> pages : categoryPages.values()) {
            if (pages.contains(inventory)) {
                return true;
            }
        }
        return false;
    }

    private boolean isShopInventory(Inventory inventory) {
        return playerBuyMenus.containsValue(inventory) || 
               playerSellMenus.containsValue(inventory) || 
               isInventoryInCategoryPages(inventory);
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!isShopInventory(event.getInventory())) return;

        // Update player head when inventory is opened
        updatePlayerHead(player, event.getInventory());
        
        // Only add tax/info buttons to main menus, not category menus
        if (playerBuyMenus.containsValue(event.getInventory()) || playerSellMenus.containsValue(event.getInventory())) {
            // Update tax collection button or info button based on player
            if (plugin.getTaxManager().isOwner(player)) {
                addTaxCollectionButton(player, event.getInventory());
            } else {
                addInfoButton(event.getInventory());
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        
        // Clean up quantity menu if it's being closed
        if (quantityMenus.containsKey(event.getInventory())) {
            quantityMenus.remove(event.getInventory());
            quantitySelections.remove(player);
            lastClickTime.remove(player); // Clear click cooldown when closing menu
        }
    }

    private void handleBuyTransaction(Player player, String categoryId, String itemId, int quantity) {
        // Check stock first
        int currentStock = plugin.getDatabaseManager().getStock(categoryId, itemId);
        if (currentStock < quantity) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("available", String.valueOf(currentStock));
            placeholders.put("requested", String.valueOf(quantity));
            player.sendMessage(Component.text(plugin.getLocaleManager().getMessage("transaction.not_enough_stock", placeholders))
                .color(NamedTextColor.RED));
            return;
        }

        // Calculate price - the displayed price already includes tax
        double price = plugin.getShopManager().calculateBuyPrice(categoryId, itemId, quantity);
        
        // Check if player can afford it - don't add tax again since it's already included in the price
        if (plugin.getShopManager().getPlayerMoney(player) < price) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("price", String.format("%.2f", price));
            player.sendMessage(Component.text(plugin.getLocaleManager().getMessage("transaction.not_enough_money", placeholders))
                .color(NamedTextColor.RED));
            return;
        }

        // Try to buy the item
        if (plugin.getShopManager().buyItem(player, categoryId, itemId, quantity)) {
            // Success - refresh the category menu
            createCategoryMenus();
            player.openInventory(categoryPages.get(categoryId).get(currentPage.getOrDefault(player, 0)));
            
            // Send success message
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("quantity", String.valueOf(quantity));
            placeholders.put("item", itemId.replace("_", " "));
            placeholders.put("price", String.format("%.2f", price));
            player.sendMessage(Component.text(plugin.getLocaleManager().getMessage("transaction.purchase_success", placeholders))
                .color(NamedTextColor.GREEN));
        }
    }

    private void handleTaxCollection(Player player) {
        if (!plugin.getTaxManager().isOwner(player)) {
            player.sendMessage(Component.text(plugin.getLocaleManager().getMessage("tax.not_owner")));
            return;
        }

        double amount = plugin.getTaxManager().collectTaxes(player);
        if (amount > 0) {
            // Add money to player
            plugin.getShopManager().depositMoney(player, amount);
        }

        // Update the tax collection button immediately
        if (player.getOpenInventory() != null && isShopInventory(player.getOpenInventory().getTopInventory())) {
            addTaxCollectionButton(player, player.getOpenInventory().getTopInventory());
        }
    }

    /**
     * Refreshes all category menus to update prices and stock information
     * This should be called after significant stock changes
     */
    public void refreshCategoryMenus() {
        createCategoryMenus();
    }
    
    private String formatMessage(String message) {
        return message.replace("&", "§");
    }

    // New method to open quantity selection menu
    private void openQuantitySelectionMenu(Player player, String categoryId, String itemId) {
        ShopItem item = plugin.getShopManager().getCategories().get(categoryId).get(itemId);
        if (item == null) return;
        
        // Create inventory with holder to identify it
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("item", itemId.replace("_", " "));
        Component title = Component.text(plugin.getLocaleManager().getMessage("shop.quantity_menu_title", placeholders))
            .color(NamedTextColor.GOLD)
            .decoration(TextDecoration.ITALIC, false);
        
        Inventory menu = Bukkit.createInventory(null, 27, title);
        
        // Fill entire inventory with glass panes first
        ItemStack border = createGlassPane(0);
        for (int i = 0; i < 27; i++) {
            menu.setItem(i, border.clone());
        }
        
        // Add item display in the center with metadata
        ItemStack displayItem = new ItemStack(item.getMaterial());
        ItemMeta displayMeta = displayItem.getItemMeta();
        
        // Add category and item metadata
        NamespacedKey categoryKey = new NamespacedKey(plugin, "category_id");
        NamespacedKey itemKey = new NamespacedKey(plugin, "item_id");
        displayMeta.getPersistentDataContainer().set(categoryKey, PersistentDataType.STRING, categoryId);
        displayMeta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, itemId);
        
        displayMeta.displayName(Component.text(itemId.replace("_", " ").toUpperCase())
            .color(NamedTextColor.GOLD)
            .decoration(TextDecoration.ITALIC, false));
        
        // Get current stock and price
        int currentStock = plugin.getDatabaseManager().getStock(categoryId, itemId);
        double buyPrice = plugin.getShopManager().calculateBuyPrice(categoryId, itemId, 1);
        
        List<Component> displayLore = new ArrayList<>();
        displayLore.add(Component.text("Stock: " + currentStock)
            .color(NamedTextColor.GRAY)
            .decoration(TextDecoration.ITALIC, false));
        displayLore.add(Component.text(String.format("Price: $%.2f each", buyPrice))
            .color(NamedTextColor.GREEN)
            .decoration(TextDecoration.ITALIC, false));
        
        displayMeta.lore(displayLore);
        displayItem.setItemMeta(displayMeta);
        menu.setItem(13, displayItem);
        
        // Set initial quantity to 1
        int initialQuantity = 1;
        
        // Add quantity buttons with metadata
        addQuantityButton(menu, 10, Material.RED_CONCRETE, plugin.getLocaleManager().getMessage("shop.buttons.remove_thirtytwo"), -32, categoryId, itemId);
        addQuantityButton(menu, 11, Material.RED_CONCRETE, plugin.getLocaleManager().getMessage("shop.buttons.remove_sixteen"), -16, categoryId, itemId);
        addQuantityButton(menu, 12, Material.RED_CONCRETE, plugin.getLocaleManager().getMessage("shop.buttons.remove_one"), -1, categoryId, itemId);
        addQuantityButton(menu, 14, Material.LIME_CONCRETE, plugin.getLocaleManager().getMessage("shop.buttons.add_one"), 1, categoryId, itemId);
        addQuantityButton(menu, 15, Material.LIME_CONCRETE, plugin.getLocaleManager().getMessage("shop.buttons.add_sixteen"), 16, categoryId, itemId);
        addQuantityButton(menu, 16, Material.LIME_CONCRETE, plugin.getLocaleManager().getMessage("shop.buttons.add_thirtytwo"), 32, categoryId, itemId);
        
        // Add quantity display
        updateQuantityDisplay(menu, initialQuantity, categoryId, itemId);
        
        // Add confirm button with metadata
        ItemStack confirmButton = new ItemStack(Material.EMERALD);
        ItemMeta confirmMeta = confirmButton.getItemMeta();
        confirmMeta.displayName(Component.text(plugin.getLocaleManager().getMessage("shop.buttons.confirm_purchase"))
            .color(NamedTextColor.GREEN)
            .decoration(TextDecoration.ITALIC, false));
        confirmMeta.getPersistentDataContainer().set(categoryKey, PersistentDataType.STRING, categoryId);
        confirmMeta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, itemId);
        confirmMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "button_type"), PersistentDataType.STRING, "confirm");
        confirmButton.setItemMeta(confirmMeta);
        menu.setItem(22, confirmButton);
        
        // Add back button with metadata
        ItemStack backButton = new ItemStack(Material.BARRIER);
        ItemMeta backMeta = backButton.getItemMeta();
        backMeta.displayName(Component.text(plugin.getLocaleManager().getMessage("shop.buttons.back_to_category"))
            .color(NamedTextColor.RED)
            .decoration(TextDecoration.ITALIC, false));
        backMeta.getPersistentDataContainer().set(categoryKey, PersistentDataType.STRING, categoryId);
        backMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "button_type"), PersistentDataType.STRING, "back");
        backButton.setItemMeta(backMeta);
        menu.setItem(26, backButton);
        
        // Store the data
        quantitySelections.put(player, new QuantitySelectionData(categoryId, itemId, initialQuantity));
        
        // Track this as a quantity selection menu
        quantityMenus.put(menu, true);
        
        // Open the menu
        player.openInventory(menu);
    }
    
    private void addQuantityButton(Inventory menu, int slot, Material material, String label, int change, String categoryId, String itemId) {
        ItemStack button = new ItemStack(material);
        ItemMeta meta = button.getItemMeta();
        meta.displayName(Component.text(label)
            .color(change > 0 ? NamedTextColor.GREEN : NamedTextColor.RED)
            .decoration(TextDecoration.ITALIC, false));
        
        // Store metadata
        NamespacedKey changeKey = new NamespacedKey(plugin, "quantity_change");
        NamespacedKey categoryKey = new NamespacedKey(plugin, "category_id");
        NamespacedKey itemKey = new NamespacedKey(plugin, "item_id");
        NamespacedKey buttonTypeKey = new NamespacedKey(plugin, "button_type");
        
        meta.getPersistentDataContainer().set(changeKey, PersistentDataType.INTEGER, change);
        meta.getPersistentDataContainer().set(categoryKey, PersistentDataType.STRING, categoryId);
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, itemId);
        meta.getPersistentDataContainer().set(buttonTypeKey, PersistentDataType.STRING, "quantity");
        
        button.setItemMeta(meta);
        menu.setItem(slot, button);
    }
    
    private void updateQuantityDisplay(Inventory menu, int quantity, String categoryId, String itemId) {
        // Ensure quantity is at least 1
        quantity = Math.max(1, quantity);
        
        // Get max stock to limit quantity
        int currentStock = plugin.getDatabaseManager().getStock(categoryId, itemId);
        quantity = Math.min(quantity, currentStock);
        
        // Calculate total price (already includes tax)
        double totalPrice = plugin.getShopManager().calculateBuyPrice(categoryId, itemId, quantity);
        // Calculate tax amount for display only (tax is already included in totalPrice)
        double basePrice = totalPrice / plugin.getTaxManager().getBuyPriceMultiplier();
        double taxAmount = totalPrice - basePrice;
        
        // Create display item
        ItemStack quantityItem = new ItemStack(Material.YELLOW_CONCRETE);
        ItemMeta meta = quantityItem.getItemMeta();
        
        // Add metadata
        NamespacedKey categoryKey = new NamespacedKey(plugin, "category_id");
        NamespacedKey itemKey = new NamespacedKey(plugin, "item_id");
        NamespacedKey buttonTypeKey = new NamespacedKey(plugin, "button_type");
        meta.getPersistentDataContainer().set(categoryKey, PersistentDataType.STRING, categoryId);
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, itemId);
        meta.getPersistentDataContainer().set(buttonTypeKey, PersistentDataType.STRING, "fullstack");
        
        meta.displayName(Component.text(plugin.getLocaleManager().getMessage("shop.buttons.quantity"))
            .color(NamedTextColor.GOLD)
            .decoration(TextDecoration.ITALIC, false));
        
        List<Component> lore = new ArrayList<>();
        Map<String, String> placeholders = new HashMap<>();
        
        // Add quantity to lore
        placeholders.put("quantity", String.valueOf(quantity));
        lore.add(Component.text(plugin.getLocaleManager().getMessage("shop.lore.selected_quantity", placeholders))
            .color(NamedTextColor.AQUA)
            .decoration(TextDecoration.ITALIC, false));
        
        // Add price to lore
        placeholders.clear();
        placeholders.put("price", String.format("%.2f", totalPrice));
        lore.add(Component.text(plugin.getLocaleManager().getMessage("shop.lore.total_price", placeholders))
            .color(NamedTextColor.GREEN)
            .decoration(TextDecoration.ITALIC, false));
        
        // Add tax if enabled
        if (plugin.getTaxManager().shouldShowTaxInLore()) {
            placeholders.clear();
            placeholders.put("tax", String.format("%.2f", taxAmount));
            lore.add(Component.text(plugin.getLocaleManager().getMessage("shop.lore.tax_included", placeholders))
                .color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        }
        
        // Add full stack info
        lore.add(Component.empty());
        lore.add(Component.text(plugin.getLocaleManager().getMessage("shop.lore.click_full_stack"))
            .color(NamedTextColor.YELLOW)
            .decoration(TextDecoration.ITALIC, false));
        
        meta.lore(lore);
        quantityItem.setItemMeta(meta);
        menu.setItem(4, quantityItem);
    }
    
    private void handleQuantitySelectionClick(InventoryClickEvent event) {
        // Always cancel the event to prevent any item movement
        event.setCancelled(true);
        
        Player player = (Player) event.getWhoClicked();
        
        // Cancel clicks in player inventory
        if (event.getClickedInventory() == player.getInventory()) {
            return;
        }
        
        // Only handle clicks in the quantity selection menu
        if (!quantityMenus.containsKey(event.getView().getTopInventory())) {
            return;
        }
        
        // Get the current slot
        int slot = event.getSlot();
        
        // Use a synchronized block with the player as the lock to prevent concurrent modifications
        synchronized (player) {
            // Implement click cooldown to prevent fast clicking issues
            long currentTime = System.currentTimeMillis();
            Long lastClick = lastClickTime.get(player);
            if (lastClick != null && (currentTime - lastClick) < 250) { // 300ms cooldown
                debug("Click blocked due to cooldown for player: " + player.getName() + " at slot " + slot);
                return;
            }
            
                // Store both the time and the slot that was clicked
            lastClickTime.put(player, currentTime);
            
            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem == null || !clickedItem.hasItemMeta()) return;
            
            ItemMeta meta = clickedItem.getItemMeta();
            NamespacedKey buttonTypeKey = new NamespacedKey(plugin, "button_type");
            NamespacedKey categoryKey = new NamespacedKey(plugin, "category_id");
            
            // Get the button type from metadata
            String buttonType = meta.getPersistentDataContainer().get(buttonTypeKey, PersistentDataType.STRING);
            if (buttonType == null) return;
            
            String categoryId = meta.getPersistentDataContainer().get(categoryKey, PersistentDataType.STRING);
            if (categoryId == null) return;
            
            QuantitySelectionData data = quantitySelections.get(player);
            if (data == null || !data.categoryId.equals(categoryId)) {
                player.closeInventory();
                return;
            }
            
            switch (buttonType) {
                case "quantity":
                    // Handle quantity adjustment with additional protection
                    NamespacedKey changeKey = new NamespacedKey(plugin, "quantity_change");
                    if (meta.getPersistentDataContainer().has(changeKey, PersistentDataType.INTEGER)) {
                        int change = meta.getPersistentDataContainer().get(changeKey, PersistentDataType.INTEGER);
                        int currentStock = plugin.getDatabaseManager().getStock(data.categoryId, data.itemId);
                        int oldQuantity = data.quantity;
                        data.quantity = Math.max(1, Math.min(currentStock, data.quantity + change));
                        
                        // Only update display if quantity actually changed
                        if (data.quantity != oldQuantity) {
                            debug("Quantity changed from " + oldQuantity + " to " + data.quantity + " for player: " + player.getName());
                            // Schedule the update for the next tick to prevent race conditions
                            final int newQuantity = data.quantity;
                            final String catId = data.categoryId;
                            final String itmId = data.itemId;
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                updateQuantityDisplay(event.getInventory(), newQuantity, catId, itmId);
                            });
                        } else {
                            debug("Quantity unchanged (" + data.quantity + ") for player: " + player.getName());
                        }
                    }
                    break;
                
                case "fullstack":
                    // Handle full stack button
                    int currentStock = plugin.getDatabaseManager().getStock(data.categoryId, data.itemId);
                    data.quantity = Math.min(64, currentStock); // Set to full stack or max stock
                    
                    // Schedule the update for the next tick to prevent race conditions
                    final int fullStackQuantity = data.quantity;
                    final String fullStackCatId = data.categoryId;
                    final String fullStackItemId = data.itemId;
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        updateQuantityDisplay(event.getInventory(), fullStackQuantity, fullStackCatId, fullStackItemId);
                        // Change the full stack button to paper to indicate it was clicked
                        changeFullStackButtonToPaper(event.getInventory(), fullStackCatId, fullStackItemId);
                    });
                    break;
                    
                case "confirm":
                    // Handle purchase confirmation
                    final String confirmCatId = data.categoryId;
                    final String confirmItemId = data.itemId;
                    final int confirmQuantity = data.quantity;
                    
                    // Remove from tracking maps and close inventory
                    quantityMenus.remove(event.getView().getTopInventory());
                    player.closeInventory();
                    
                    // Process the transaction on the next tick
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        handleBuyTransaction(player, confirmCatId, confirmItemId, confirmQuantity);
                        quantitySelections.remove(player);
                    });
                    break;
                    
                case "back":
                    // Handle back button
                    final String backCatId = categoryId;
                    
                    // Remove from tracking maps and close inventory
                    quantityMenus.remove(event.getView().getTopInventory());
                    player.closeInventory();
                    
                    // Open the category menu on the next tick
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        quantitySelections.remove(player);
                        List<Inventory> pages = categoryPages.get(backCatId);
                        if (pages != null && !pages.isEmpty()) {
                            int page = currentPage.getOrDefault(player, 0);
                            page = Math.min(page, pages.size() - 1);
                            player.openInventory(pages.get(page));
                        } else {
                            openBuyMenu(player);
                        }
                    });
                    break;
            }
        }
    }
    
    private void changeFullStackButtonToPaper(Inventory inventory, String categoryId, String itemId) {
        // Find and update the full stack button (slot 4)
        ItemStack fullStackButton = inventory.getItem(4);
        if (fullStackButton != null && fullStackButton.hasItemMeta()) {
            ItemMeta meta = fullStackButton.getItemMeta();
            NamespacedKey buttonTypeKey = new NamespacedKey(plugin, "button_type");
            String buttonType = meta.getPersistentDataContainer().get(buttonTypeKey, PersistentDataType.STRING);
            
            if ("fullstack".equals(buttonType)) {
                // Change material to paper
                ItemStack paperButton = new ItemStack(Material.PAPER);
                ItemMeta paperMeta = paperButton.getItemMeta();
                
                // Copy all metadata from original button
                paperMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "category_id"), PersistentDataType.STRING, categoryId);
                paperMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "item_id"), PersistentDataType.STRING, itemId);
                paperMeta.getPersistentDataContainer().set(buttonTypeKey, PersistentDataType.STRING, "fullstack");
                
                // Copy display name and lore
                paperMeta.displayName(meta.displayName());
                paperMeta.lore(meta.lore());
                
                paperButton.setItemMeta(paperMeta);
                inventory.setItem(4, paperButton);
            }
        }
    }
    
    // Helper class to store quantity selection data
    private static class QuantitySelectionData {
        private final String categoryId;
        private final String itemId;
        private int quantity;
        
        public QuantitySelectionData(String categoryId, String itemId, int quantity) {
            this.categoryId = categoryId;
            this.itemId = itemId;
            this.quantity = quantity;
        }
    }
} 