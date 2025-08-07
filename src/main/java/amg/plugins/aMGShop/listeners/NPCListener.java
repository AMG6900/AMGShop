package amg.plugins.aMGShop.listeners;

import amg.plugins.aMGShop.AMGShop;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;

public class NPCListener implements Listener {
    private final AMGShop plugin;

    public NPCListener(AMGShop plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Send a debug message if debug mode is enabled
     * @param message The message to send
     */
    private void debug(String message) {
        if (plugin.getConfig().getBoolean("debug", false)) {
            plugin.getLogger().info("[Debug] " + message);
        }
    }

    @EventHandler
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        Entity clickedEntity = event.getRightClicked();
        
        // Check if the clicked entity is one of our shop NPCs
        if (!(clickedEntity instanceof Villager)) return;
        
        if (!plugin.getNPCManager().isShopNPC(clickedEntity)) return;
        
        // Get NPC type
        String npcType = plugin.getNPCManager().getNPCTypeFromEntity(clickedEntity);
        
        // Debug information
        debug("Shop NPC clicked: Player=" + player.getName() + ", Type=" + npcType);
        
        if (npcType == null) return;

        // Cancel the event to prevent default villager behavior
        event.setCancelled(true);

        if (!player.hasPermission("amgshop.use")) {
            player.sendMessage(plugin.getConfig().getString("messages.no_permission", "§cYou don't have permission to do that!"));
            debug("Player " + player.getName() + " doesn't have permission to use shop");
            return;
        }

        switch (npcType) {
            case "buy" -> {
                debug("Opening buy menu for " + player.getName());
                plugin.getShopGUI().openBuyMenu(player);
            }
            case "sell" -> {
                debug("Opening sell menu for " + player.getName());
                plugin.getShopGUI().openSellMenu(player);
            }
        }
    }
}