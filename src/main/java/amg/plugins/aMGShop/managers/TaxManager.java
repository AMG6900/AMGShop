package amg.plugins.aMGShop.managers;

import amg.plugins.aMGShop.AMGShop;
import org.bukkit.entity.Player;
import net.kyori.adventure.text.Component;

import java.util.HashMap;
import java.util.Map;

public class TaxManager {
    private final AMGShop plugin;
    private double collectedTaxes;
    private final double buyTaxRate;
    private final double sellTaxRate;
    private final boolean showTaxInLore;

    public TaxManager(AMGShop plugin) {
        this.plugin = plugin;
        this.collectedTaxes = 0.0;
        this.buyTaxRate = plugin.getConfig().getDouble("shop.tax.buy_tax", 20.0) / 100.0;
        this.sellTaxRate = plugin.getConfig().getDouble("shop.tax.sell_tax", 20.0) / 100.0;
        this.showTaxInLore = plugin.getConfig().getBoolean("shop.tax.show_tax_in_lore", true);
    }

    public double calculateBuyTax(double price) {
        return price * buyTaxRate;
    }

    public double calculateSellTax(double price) {
        return price * sellTaxRate;
    }

    public double getBuyPriceMultiplier() {
        return 1.0 + buyTaxRate;
    }

    public double getSellPriceMultiplier() {
        return 1.0 - sellTaxRate;
    }

    public void addTax(double amount) {
        collectedTaxes += amount;
    }

    public double collectTaxes(Player player) {
        if (!isOwner(player)) {
            player.sendMessage(Component.text(plugin.getLocaleManager().getMessage("tax.not_owner")));
            return 0;
        }

        if (collectedTaxes <= 0) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("amount", "0.00");
            player.sendMessage(Component.text(plugin.getLocaleManager().getMessage("tax.no_taxes", placeholders)));
            return 0;
        }

        double amount = collectedTaxes;
        collectedTaxes = 0;
        
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("amount", String.format("%.2f", amount));
        player.sendMessage(Component.text(plugin.getLocaleManager().getMessage("tax.collected", placeholders)));
        
        return amount;
    }

    public double getCollectedTaxes() {
        return collectedTaxes;
    }

    public boolean shouldShowTaxInLore() {
        return showTaxInLore;
    }

    public boolean isOwner(Player player) {
        String ownerUUID = plugin.getConfig().getString("shop.owner.uuid", "");
        return player.getUniqueId().toString().equals(ownerUUID);
    }
} 