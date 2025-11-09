package de.zfzfg.pvpwager.commands;

import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.pvpwager.models.CommandRequest;
import de.zfzfg.pvpwager.utils.MessageUtil;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * /pvpanswer <wager> <amount> [arena] [equipment]
 */
public class PvPAnswerCommand implements CommandExecutor, TabCompleter {
    
    private final EventPlugin plugin;
    
    public PvPAnswerCommand(EventPlugin plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this command!");
            return true;
        }
        
        Player player = (Player) sender;
        
        // Get request
        CommandRequest request = plugin.getCommandRequestManager().getRequestToPlayer(player);
        if (request == null) {
            MessageUtil.sendMessage(player, "&cNo pending request!");
            return true;
        }
        
        if (args.length < 2) {
            MessageUtil.sendMessage(player, "&cUsage: /pvpanswer <wager> <amount> [arena] [equipment]");
            MessageUtil.sendMessage(player, "&7Example: /pvpanswer DIAMOND_SWORD 1");
            MessageUtil.sendMessage(player, "&7Example: /pvpanswer MONEY 50 desert diamond");
            return true;
        }
        
        String wagerType = args[0];
        String amountStr = args[1];
        String arenaId = args.length > 2 ? args[2] : null;
        String equipmentId = args.length > 3 ? args[3] : null;
        
        // Validate optional arena
        if (arenaId != null && plugin.getArenaManager().getArena(arenaId) == null) {
            MessageUtil.sendMessage(player, "&cArena not found: " + arenaId);
            return true;
        }
        
        // Validate optional equipment
        if (equipmentId != null && plugin.getEquipmentManager().getEquipmentSet(equipmentId) == null) {
            MessageUtil.sendMessage(player, "&cEquipment not found: " + equipmentId);
            return true;
        }
        
        // Parse wager
        List<ItemStack> items = new ArrayList<>();
        double money = 0.0;
        
        if (wagerType.equalsIgnoreCase("MONEY")) {
            try {
                money = Double.parseDouble(amountStr);
                if (money <= 0) {
                    MessageUtil.sendMessage(player, "&cAmount must be positive!");
                    return true;
                }
                
                if (!plugin.hasEconomy()) {
                    MessageUtil.sendMessage(player, "&cEconomy is not enabled!");
                    return true;
                }
                
                if (!plugin.getEconomy().has(player, money)) {
                    MessageUtil.sendMessage(player, "&cYou don't have enough money!");
                    return true;
                }
            } catch (NumberFormatException e) {
                MessageUtil.sendMessage(player, "&cInvalid money amount: " + amountStr);
                return true;
            }
        } else {
            // Parse items
            String[] itemNames = wagerType.split(",");
            String[] amounts = amountStr.split(",");
            
            if (itemNames.length != amounts.length) {
                MessageUtil.sendMessage(player, "&cMismatch between items and amounts!");
                return true;
            }
            
            for (int i = 0; i < itemNames.length; i++) {
                try {
                    Material material = Material.valueOf(itemNames[i].toUpperCase());
                    int amount = Integer.parseInt(amounts[i]);
                    
                    if (amount <= 0) {
                        MessageUtil.sendMessage(player, "&cAmount must be positive!");
                        return true;
                    }
                    
                    // Check if player has items
                    ItemStack checkItem = new ItemStack(material, amount);
                    if (!player.getInventory().containsAtLeast(checkItem, amount)) {
                        MessageUtil.sendMessage(player, "&cYou don't have enough of: " + material.name());
                        return true;
                    }
                    
                    items.add(new ItemStack(material, amount));
                } catch (IllegalArgumentException e) {
                    MessageUtil.sendMessage(player, "&cInvalid item: " + itemNames[i]);
                    return true;
                }
            }
        }
        
        // Set response
        request.setTargetResponse(items, money, arenaId, equipmentId);
        
        // Notify both players
        Player challenger = request.getSender();
        
        MessageUtil.sendMessage(player, "");
        MessageUtil.sendMessage(player, "&a&l━━━━━━━━━━━━━━━━━━━━━━━");
        MessageUtil.sendMessage(player, "&a&lRESPONSE SENT!");
        MessageUtil.sendMessage(player, "&a&l━━━━━━━━━━━━━━━━━━━━━━━");
        MessageUtil.sendMessage(player, "");
        if (money > 0) {
            MessageUtil.sendMessage(player, "&7Your Wager: &6$" + String.format("%.2f", money));
        } else {
            MessageUtil.sendMessage(player, "&7Your Wager: &e" + MessageUtil.formatItemList(items));
        }
        if (arenaId != null) {
            MessageUtil.sendMessage(player, "&7Arena Override: &e" + arenaId);
        }
        if (equipmentId != null) {
            MessageUtil.sendMessage(player, "&7Equipment Override: &e" + equipmentId);
        }
        MessageUtil.sendMessage(player, "");
        MessageUtil.sendMessage(player, "&7Waiting for &e" + challenger.getName() + " &7to confirm...");
        MessageUtil.sendMessage(player, "");
        
        // Notify sender
        MessageUtil.sendMessage(challenger, "");
        MessageUtil.sendMessage(challenger, "&6&l━━━━━━━━━━━━━━━━━━━━━━━");
        MessageUtil.sendMessage(challenger, "&e&l" + player.getName().toUpperCase() + " RESPONDED!");
        MessageUtil.sendMessage(challenger, "&6&l━━━━━━━━━━━━━━━━━━━━━━━");
        MessageUtil.sendMessage(challenger, "");
        if (money > 0) {
            MessageUtil.sendMessage(challenger, "&7Their Wager: &6$" + String.format("%.2f", money));
        } else {
            MessageUtil.sendMessage(challenger, "&7Their Wager: &e" + MessageUtil.formatItemList(items));
        }
        if (arenaId != null) {
            MessageUtil.sendMessage(challenger, "&7Arena Changed to: &e" + arenaId);
        }
        if (equipmentId != null) {
            MessageUtil.sendMessage(challenger, "&7Equipment Changed to: &e" + equipmentId);
        }
        MessageUtil.sendMessage(challenger, "");
        MessageUtil.sendMessage(challenger, "&aType &e/pvpyes &ato accept");
        MessageUtil.sendMessage(challenger, "&cType &e/pvpno &cto decline");
        MessageUtil.sendMessage(challenger, "");
        
        return true;
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            // Erstes Argument: Wager-Typ
            completions.addAll(Arrays.asList("MONEY", "DIAMOND_SWORD", "DIAMOND_CHESTPLATE", "DIAMOND_HELMET", 
                "DIAMOND_LEGGINGS", "DIAMOND_BOOTS", "IRON_SWORD", "IRON_CHESTPLATE", "GOLDEN_SWORD", "BOW"));
        } else if (args.length == 2) {
            // Zweites Argument: Anzahl/Betrag
            completions.addAll(Arrays.asList("1", "5", "10", "50", "100", "500", "1000"));
        } else if (args.length == 3) {
            // Drittes Argument: Arena (optional)
            completions.addAll(plugin.getArenaManager().getArenas().keySet());
        } else if (args.length == 4) {
            // Viertes Argument: Equipment (optional)
            completions.addAll(plugin.getEquipmentManager().getEquipmentSets().keySet());
        }
        
        return completions;
    }
}