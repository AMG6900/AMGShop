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
    private final SellGUI sellGUI;

    public ShopGUI(AMGShop plugin) {
        this.plugin = plugin;
        this.categoryPages = new HashMap<>();
        this.currentPage = new HashMap<>();
        this.quantitySelections = new HashMap<>();
        this.playerBuyMenus = new HashMap<>();
        this.playerSellMenus = new HashMap<>();
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
                            lore.add(Component.text("Contains " + itemCount + " items")
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
        meta.displayName(Component.text("Loading...")
            .color(NamedTextColor.GRAY)
            .decoration(TextDecoration.ITALIC, false));
        placeholderButton.setItemMeta(meta);
        inventory.setItem(49, placeholderButton);
    }

    private void updatePlayerHead(Player player, Inventory inventory) {
        ItemStack playerHead = inventory.getItem(48);
        if (playerHead != null && playerHead.getItemMeta() instanceof SkullMeta skullMeta) {
            skullMeta.setOwningPlayer(player);
            
            Object playerData = plugin.getShopManager().getPlayerData(player);
            double balance = playerData != null ? plugin.getShopManager().getPlayerMoney(playerData) : 0.0;
            
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

            // Sort items by name
            List<Map.Entry<String, ShopItem>> sortedItems = new ArrayList<>(items.entrySet());
            Collections.sort(sortedItems, (a, b) -> a.getKey().compareTo(b.getKey()));

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
        
        lore.add(Component.text("Stock: " + currentStock)
            .color(NamedTextColor.GRAY)
            .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text(String.format("Price: $%.2f", buyPrice))
            .color(NamedTextColor.GREEN)
            .decoration(TextDecoration.ITALIC, false));
        if (plugin.getTaxManager().shouldShowTaxInLore()) {
            lore.add(Component.text(String.format("Includes Tax: $%.2f", taxAmount))
                .color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        }
        if (plugin.getConfig().getBoolean("shop.inflation.enabled", true)) {
            lore.add(Component.text(String.format("Inflation: %.1f%%", 
                plugin.getInflationManager().getInflationRate()))
                .color(NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false));
        }
        lore.add(Component.empty());
        lore.add(Component.text("Click to Buy")
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
        if (event.getCurrentItem() == null) return;
        if (event.getClickedInventory() == null) return;

        // Check if this is a shop inventory
        if (!isShopInventory(event.getView().getTopInventory())) {
            // Check if this is a quantity selection menu
            if (event.getView().title().toString().contains("Select Quantity")) {
                handleQuantitySelectionClick(event);
                return;
            }
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
        if (playerBuyMenus.containsValue(event.getInventory()) || 
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

    private void handleBuyTransaction(Player player, String categoryId, String itemId, int quantity) {
        if (plugin.getShopManager().buyItem(player, categoryId, itemId, quantity)) {
            // Refresh the category menu
            createCategoryMenus();
            player.openInventory(categoryPages.get(categoryId).get(currentPage.getOrDefault(player, 0)));
        } else {
            double price = plugin.getShopManager().calculateBuyPrice(categoryId, itemId, quantity);
            double tax = plugin.getTaxManager().calculateBuyTax(price);
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("price", String.format("%.2f", price));
            placeholders.put("tax", String.format("%.2f", tax));
            player.sendMessage(Component.text(plugin.getLocaleManager().getMessage("transaction.not_enough_money", placeholders)));
        }
    }

    private void handleTaxCollection(Player player) {
        if (!plugin.getTaxManager().isOwner(player)) {
            player.sendMessage(Component.text(plugin.getLocaleManager().getMessage("tax.not_owner")));
            return;
        }

        double amount = plugin.getTaxManager().collectTaxes(player);
        if (amount > 0) {
            // Add money to player using AMGCore
            Object playerData = plugin.getShopManager().getPlayerData(player);
            if (playerData != null) {
                double currentMoney = plugin.getShopManager().getPlayerMoney(playerData);
                plugin.getShopManager().setPlayerMoney(playerData, currentMoney + amount);
            }
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
        
        // Create inventory
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("item", itemId.replace("_", " "));
        Component title = Component.text(plugin.getLocaleManager().getMessage("shop.quantity_menu_title", placeholders))
            .color(NamedTextColor.GOLD)
            .decoration(TextDecoration.ITALIC, false);
        
        Inventory menu = Bukkit.createInventory(null, 27, title);
        
        // Add border
        ItemStack border = createGlassPane(0);
        for (int i = 0; i < 27; i++) {
            menu.setItem(i, border.clone());
        }
        
        // Add item display in the center
        ItemStack displayItem = new ItemStack(item.getMaterial());
        ItemMeta displayMeta = displayItem.getItemMeta();
        displayMeta.displayName(Component.text(itemId.replace("_", " ").toUpperCase())
            .color(NamedTextColor.GOLD)
            .decoration(TextDecoration.ITALIC, false));
        
        // Get current stock and price
        int currentStock = plugin.getDatabaseManager().getStock(categoryId, itemId);
        double buyPrice = plugin.getShopManager().calculateBuyPrice(categoryId, itemId, 1);
        
        List<Component> displayLore = new ArrayList<>();
        placeholders.clear();
        placeholders.put("current", String.valueOf(currentStock));
        placeholders.put("max", String.valueOf(item.getMaxStock()));
        displayLore.add(Component.text(plugin.getLocaleManager().getMessage("shop.lore.current_stock", placeholders))
            .color(NamedTextColor.GRAY)
            .decoration(TextDecoration.ITALIC, false));

        placeholders.clear();
        placeholders.put("price", String.format("%.2f", buyPrice));
        displayLore.add(Component.text(plugin.getLocaleManager().getMessage("shop.lore.price_per_item", placeholders))
            .color(NamedTextColor.GREEN)
            .decoration(TextDecoration.ITALIC, false));
        displayLore.add(Component.empty());
        displayLore.add(Component.text(plugin.getLocaleManager().getMessage("shop.lore.click_to_buy"))
            .color(NamedTextColor.YELLOW)
            .decoration(TextDecoration.ITALIC, false));
        
        displayMeta.lore(displayLore);
        displayItem.setItemMeta(displayMeta);
        menu.setItem(13, displayItem);
        
        // Set initial quantity to 1
        int initialQuantity = 1;
        
        // Add quantity display
        updateQuantityDisplay(menu, initialQuantity, categoryId, itemId);
        
        // Add quantity adjustment buttons
        addQuantityButton(menu, 10, Material.RED_CONCRETE, plugin.getLocaleManager().getMessage("shop.buttons.remove_thirtytwo"), -32);
        addQuantityButton(menu, 11, Material.RED_CONCRETE, plugin.getLocaleManager().getMessage("shop.buttons.remove_sixteen"), -16);
        addQuantityButton(menu, 12, Material.RED_CONCRETE, plugin.getLocaleManager().getMessage("shop.buttons.remove_one"), -1);
        addQuantityButton(menu, 14, Material.LIME_CONCRETE, plugin.getLocaleManager().getMessage("shop.buttons.add_one"), 1);
        addQuantityButton(menu, 15, Material.LIME_CONCRETE, plugin.getLocaleManager().getMessage("shop.buttons.add_sixteen"), 16);
        addQuantityButton(menu, 16, Material.LIME_CONCRETE, plugin.getLocaleManager().getMessage("shop.buttons.add_thirtytwo"), 32);
        
        // Add full stack button (64)
        ItemStack stackButton = new ItemStack(Material.YELLOW_CONCRETE);
        ItemMeta stackMeta = stackButton.getItemMeta();
        stackMeta.displayName(Component.text(plugin.getLocaleManager().getMessage("shop.buttons.full_stack"))
            .color(NamedTextColor.GOLD)
            .decoration(TextDecoration.ITALIC, false));
        stackButton.setItemMeta(stackMeta);
        menu.setItem(4, stackButton);
        
        // Add confirm button
        ItemStack confirmButton = new ItemStack(Material.EMERALD);
        ItemMeta confirmMeta = confirmButton.getItemMeta();
        confirmMeta.displayName(Component.text(plugin.getLocaleManager().getMessage("shop.buttons.confirm"))
            .color(NamedTextColor.GREEN)
            .decoration(TextDecoration.ITALIC, false));
        confirmButton.setItemMeta(confirmMeta);
        menu.setItem(22, confirmButton);
        
        // Add cancel button
        ItemStack cancelButton = new ItemStack(Material.BARRIER);
        ItemMeta cancelMeta = cancelButton.getItemMeta();
        cancelMeta.displayName(Component.text(plugin.getLocaleManager().getMessage("shop.buttons.cancel"))
            .color(NamedTextColor.RED)
            .decoration(TextDecoration.ITALIC, false));
        cancelButton.setItemMeta(cancelMeta);
        menu.setItem(26, cancelButton);
        
        // Store the data
        quantitySelections.put(player, new QuantitySelectionData(categoryId, itemId, initialQuantity));
        
        // Open the menu
        player.openInventory(menu);
    }
    
    private void addQuantityButton(Inventory menu, int slot, Material material, String label, int change) {
        ItemStack button = new ItemStack(material);
        ItemMeta meta = button.getItemMeta();
        meta.displayName(Component.text(label)
            .color(change > 0 ? NamedTextColor.GREEN : NamedTextColor.RED)
            .decoration(TextDecoration.ITALIC, false));
        
        // Store the change amount in the item's NBT data
        NamespacedKey changeKey = new NamespacedKey(plugin, "quantity_change");
        meta.getPersistentDataContainer().set(changeKey, PersistentDataType.INTEGER, change);
        
        button.setItemMeta(meta);
        menu.setItem(slot, button);
    }
    
    private void updateQuantityDisplay(Inventory menu, int quantity, String categoryId, String itemId) {
        // Ensure quantity is at least 1
        quantity = Math.max(1, quantity);
        
        // Get max stock to limit quantity
        int currentStock = plugin.getDatabaseManager().getStock(categoryId, itemId);
        quantity = Math.min(quantity, currentStock);
        
        // Calculate total price
        double totalPrice = plugin.getShopManager().calculateBuyPrice(categoryId, itemId, quantity);
        double taxAmount = plugin.getTaxManager().calculateBuyTax(totalPrice);
        
        // Create display item
        ItemStack quantityItem = new ItemStack(Material.PAPER);
        ItemMeta meta = quantityItem.getItemMeta();
        meta.displayName(Component.text("Quantity: " + quantity)
            .color(NamedTextColor.AQUA)
            .decoration(TextDecoration.ITALIC, false));
        
        List<Component> lore = new ArrayList<>();
        Map<String, String> placeholders = new HashMap<>();
        
        placeholders.put("price", String.format("%.2f", totalPrice));
        lore.add(Component.text(plugin.getLocaleManager().getMessage("shop.lore.total_price", placeholders))
            .color(NamedTextColor.GREEN)
            .decoration(TextDecoration.ITALIC, false));
        
        if (plugin.getTaxManager().shouldShowTaxInLore()) {
            placeholders.clear();
            placeholders.put("tax", String.format("%.2f", taxAmount));
            lore.add(Component.text(plugin.getLocaleManager().getMessage("shop.lore.tax_included", placeholders))
                .color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        }
        
        meta.lore(lore);
        quantityItem.setItemMeta(meta);
        menu.setItem(4, quantityItem);
    }
    
    private void handleQuantitySelectionClick(InventoryClickEvent event) {
        event.setCancelled(true);
        
        Player player = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null) return;
        
        QuantitySelectionData data = quantitySelections.get(player);
        if (data == null) return;
        
        // Get current quantity and stock
        int quantity = data.quantity;
        int currentStock = plugin.getDatabaseManager().getStock(data.categoryId, data.itemId);
        
        // Handle quantity adjustment buttons
        if (clickedItem.getType() == Material.RED_CONCRETE || clickedItem.getType() == Material.LIME_CONCRETE) {
            ItemMeta meta = clickedItem.getItemMeta();
            NamespacedKey changeKey = new NamespacedKey(plugin, "quantity_change");
            if (meta.getPersistentDataContainer().has(changeKey, PersistentDataType.INTEGER)) {
                int change = meta.getPersistentDataContainer().get(changeKey, PersistentDataType.INTEGER);
                quantity = Math.max(1, Math.min(currentStock, quantity + change)); // Ensure quantity is between 1 and current stock
                data.quantity = quantity;
                updateQuantityDisplay(event.getInventory(), data.quantity, data.categoryId, data.itemId);
            }
            return;
        }
        
        // Handle full stack button
        if (clickedItem.getType() == Material.YELLOW_CONCRETE) {
            data.quantity = Math.min(64, currentStock); // Limit to current stock
            updateQuantityDisplay(event.getInventory(), data.quantity, data.categoryId, data.itemId);
            return;
        }
        
        // Handle confirm button
        if (clickedItem.getType() == Material.EMERALD) {
            // Close the menu first to prevent double-clicking
            player.closeInventory();
            
            // Process the purchase
            handleBuyTransaction(player, data.categoryId, data.itemId, data.quantity);
            
            // Remove the data
            quantitySelections.remove(player);
            return;
        }
        
        // Handle cancel button
        if (clickedItem.getType() == Material.BARRIER) {
            // Return to category menu
            Inventory categoryMenu = categoryPages.get(data.categoryId).get(currentPage.getOrDefault(player, 0));
            if (categoryMenu != null) {
                player.openInventory(categoryMenu);
            } else {
                player.closeInventory();
            }
            
            // Remove the data
            quantitySelections.remove(player);
            return;
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