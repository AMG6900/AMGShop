package amg.plugins.aMGShop.commands;

import amg.plugins.aMGShop.AMGShop;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ShopCommand implements CommandExecutor, TabCompleter {
    private final AMGShop plugin;
    private final List<String> COMMANDS = Arrays.asList("reload", "setbuynpc", "setsellnpc", "setowner", "removeowner");

    public ShopCommand(AMGShop plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Admin commands require permission
        if (!sender.hasPermission("amgshop.admin")) {
            sender.sendMessage(formatMessage(plugin.getConfig().getString("messages.no_permission", "&cYou don't have permission to do that!")));
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> {
                // Perform a full plugin reload
                plugin.reloadPlugin();
                sender.sendMessage(formatMessage("&aAMGShop has been fully reloaded!"));
            }
            case "setbuynpc" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(formatMessage("&cThis command can only be used by players!"));
                    return true;
                }
                plugin.getNPCManager().createBuyNPC(player.getLocation());
                sender.sendMessage(formatMessage("&aBuy NPC created at your location!"));
            }
            case "setsellnpc" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(formatMessage("&cThis command can only be used by players!"));
                    return true;
                }
                plugin.getNPCManager().createSellNPC(player.getLocation());
                sender.sendMessage(formatMessage("&aSell NPC created at your location!"));
            }
            case "setowner" -> {
                if (args.length < 2) {
                    sender.sendMessage(formatMessage("&cUsage: /amgshop setowner <player>"));
                    return true;
                }
                String playerName = args[1];
                Player targetPlayer = plugin.getServer().getPlayer(playerName);
                if (targetPlayer == null) {
                    sender.sendMessage(formatMessage("&cPlayer not found!"));
                    return true;
                }
                plugin.getConfig().set("shop.owner.uuid", targetPlayer.getUniqueId().toString());
                plugin.getConfig().set("shop.owner.name", targetPlayer.getName());
                plugin.saveConfig();
                sender.sendMessage(formatMessage("&aShop owner set to " + targetPlayer.getName() + "!"));
            }
            case "removeowner" -> {
                plugin.getConfig().set("shop.owner.uuid", null);
                plugin.getConfig().set("shop.owner.name", null);
                plugin.saveConfig();
                sender.sendMessage(formatMessage("&aShop owner has been removed!"));
            }
            default -> sendHelp(sender);
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (!sender.hasPermission("amgshop.admin")) {
            return completions;
        }

        if (args.length == 1) {
            // First argument - show all available commands
            StringUtil.copyPartialMatches(args[0], COMMANDS, completions);
        } else if (args.length == 2) {
            // Second argument - show relevant suggestions based on first argument
            switch (args[0].toLowerCase()) {
                case "setowner" -> {
                    List<String> onlinePlayers = new ArrayList<>();
                    plugin.getServer().getOnlinePlayers().forEach(player -> onlinePlayers.add(player.getName()));
                    StringUtil.copyPartialMatches(args[1], onlinePlayers, completions);
                }
            }
        }

        Collections.sort(completions); // Sort the suggestions alphabetically
        return completions;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(formatMessage("&6AMGShop Admin Commands:"));
        sender.sendMessage(formatMessage("&e/amgshop reload &7- Reload the configuration"));
        sender.sendMessage(formatMessage("&e/amgshop setbuynpc &7- Create a buy NPC at your location"));
        sender.sendMessage(formatMessage("&e/amgshop setsellnpc &7- Create a sell NPC at your location"));
        sender.sendMessage(formatMessage("&e/amgshop setowner <player> &7- Set the shop owner"));
        sender.sendMessage(formatMessage("&e/amgshop removeowner &7- Remove the shop owner"));
    }
    
    private String formatMessage(String message) {
        return message.replace("&", "§");
    }
} 