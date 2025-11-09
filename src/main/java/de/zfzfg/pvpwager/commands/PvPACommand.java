package de.zfzfg.pvpwager.commands;

import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.pvpwager.managers.CommandRequestManager;
import de.zfzfg.pvpwager.models.CommandRequest;
import de.zfzfg.pvpwager.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * /pvpa <player> <wager> <amount> <arena> <equipment>
 * 
 * Examples:
 * /pvpa Steve DIAMOND_SWORD 1 desert diamond
 * /pvpa Steve MONEY 100 forest standard
 * /pvpa Steve DIAMOND_SWORD,GOLDEN_APPLE 1,5 colosseum netherite
 */
public class PvPACommand implements CommandExecutor, TabCompleter {
    
    private final EventPlugin plugin;
    private final CommandRequestManager requestManager;
    
    public PvPACommand(EventPlugin plugin) {
        this.plugin = plugin;
        this.requestManager = plugin.getCommandRequestManager();
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this command!");
            return true;
        }
        
        Player player = (Player) sender;
        
        if (args.length < 5) {
            sendUsage(player);
            return true;
        }
        
        // Parse arguments
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null || !target.isOnline()) {
            MessageUtil.sendMessage(player, "&cPlayer not found: " + args[0]);
            return true;
        }
        
        if (player.equals(target)) {
            MessageUtil.sendMessage(player, "&cYou cannot challenge yourself!");
            return true;
        }
        
        String wagerType = args[1];
        String amountStr = args[2];
        String arenaId = args[3];
        String equipmentId = args[4];
        
        // Validate Arena
        if (plugin.getArenaManager().getArena(arenaId) == null) {
            MessageUtil.sendMessage(player, "&cArena not found: " + arenaId);
            sendAvailableArenas(player);
            return true;
        }
        
        // Validate Equipment
        if (plugin.getEquipmentManager().getEquipmentSet(equipmentId) == null) {
            MessageUtil.sendMessage(player, "&cEquipment not found: " + equipmentId);
            sendAvailableEquipment(player);
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
                    MessageUtil.sendMessage(player, "&cYou don't have enough money! You have: $" + 
                        String.format("%.2f", plugin.getEconomy().getBalance(player)));
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
        
        // Create request
        CommandRequest request = new CommandRequest(
            player, target, items, money, arenaId, equipmentId
        );
        
        requestManager.addRequest(request);
        
        // Send messages
        MessageUtil.sendMessage(player, "");
        MessageUtil.sendMessage(player, "&a&l━━━━━━━━━━━━━━━━━━━━━━━");
        MessageUtil.sendMessage(player, "&a&lREQUEST SENT!");
        MessageUtil.sendMessage(player, "&a&l━━━━━━━━━━━━━━━━━━━━━━━");
        MessageUtil.sendMessage(player, "");
        MessageUtil.sendMessage(player, "&7Target: &e" + target.getName());
        MessageUtil.sendMessage(player, "&7Arena: &e" + arenaId);
        MessageUtil.sendMessage(player, "&7Equipment: &e" + equipmentId);
        if (money > 0) {
            MessageUtil.sendMessage(player, "&7Wager: &6$" + String.format("%.2f", money));
        } else {
            MessageUtil.sendMessage(player, "&7Wager: &e" + items.size() + " item(s)");
        }
        MessageUtil.sendMessage(player, "");
        MessageUtil.sendMessage(player, "&7Waiting for &e" + target.getName() + " &7to respond...");
        MessageUtil.sendMessage(player, "");
        
        // Notify target
        requestManager.sendRequestNotification(request);
        
        return true;
    }
    
    private void sendUsage(Player player) {
        MessageUtil.sendMessage(player, "");
        MessageUtil.sendMessage(player, "&6&l━━━━━━━━━━━━━━━━━━━━━━━");
        MessageUtil.sendMessage(player, "&e&lPVPA COMMAND");
        MessageUtil.sendMessage(player, "&6&l━━━━━━━━━━━━━━━━━━━━━━━");
        MessageUtil.sendMessage(player, "");
        MessageUtil.sendMessage(player, "&7Usage:");
        MessageUtil.sendMessage(player, "&e/pvpa <player> <wager> <amount> <arena> <equipment>");
        MessageUtil.sendMessage(player, "");
        MessageUtil.sendMessage(player, "&7Examples:");
        MessageUtil.sendMessage(player, "&e/pvpa Steve DIAMOND_SWORD 1 desert diamond");
        MessageUtil.sendMessage(player, "&e/pvpa Steve MONEY 100 forest standard");
        MessageUtil.sendMessage(player, "&e/pvpa Steve DIAMOND_SWORD,GOLDEN_APPLE 1,5 colosseum netherite");
        MessageUtil.sendMessage(player, "");
        MessageUtil.sendMessage(player, "&7Use &e/pvpa arenas &7to list arenas");
        MessageUtil.sendMessage(player, "&7Use &e/pvpa equipment &7to list equipment");
        MessageUtil.sendMessage(player, "");
    }
    
    private void sendAvailableArenas(Player player) {
        MessageUtil.sendMessage(player, "&7Available arenas:");
        plugin.getArenaManager().getArenas().values().forEach(arena -> {
            MessageUtil.sendMessage(player, "  &e" + arena.getId() + " &7- " + arena.getDisplayName());
        });
    }
    
    private void sendAvailableEquipment(Player player) {
        MessageUtil.sendMessage(player, "&7Available equipment:");
        plugin.getEquipmentManager().getEquipmentSets().values().forEach(equipment -> {
            MessageUtil.sendMessage(player, "  &e" + equipment.getId() + " &7- " + equipment.getDisplayName());
        });
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (!(sender instanceof Player)) {
            return completions;
        }
        
        Player player = (Player) sender;
        
        if (args.length == 1) {
            // Player names
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(args[0].toLowerCase())) {
                    completions.add(p.getName());
                }
            }
        } else if (args.length == 2) {
            // Wager type - list all items in inventory + MONEY
            completions.add("MONEY");
            
            // Get all unique items from player's inventory
            Map<Material, Integer> itemCounts = new HashMap<>();
            for (ItemStack item : player.getInventory().getContents()) {
                if (item != null && item.getType() != Material.AIR) {
                    itemCounts.put(item.getType(), 
                        itemCounts.getOrDefault(item.getType(), 0) + item.getAmount());
                }
            }
            
            // Add all items that match the partial input
            for (Material mat : itemCounts.keySet()) {
                if (mat.name().toLowerCase().startsWith(args[1].toLowerCase())) {
                    completions.add(mat.name());
                }
            }
        } else if (args.length == 3) {
            // Amount - suggest total count of selected item or common amounts
            if (args[1].equalsIgnoreCase("MONEY")) {
                completions.add("10");
                completions.add("100");
                completions.add("1000");
            } else {
                try {
                    Material selectedMaterial = Material.valueOf(args[1].toUpperCase());
                    
                    // Count how many of this item the player has
                    int totalCount = 0;
                    for (ItemStack item : player.getInventory().getContents()) {
                        if (item != null && item.getType() == selectedMaterial) {
                            totalCount += item.getAmount();
                        }
                    }
                    
                    if (totalCount > 0) {
                        completions.add(String.valueOf(totalCount)); // Total amount
                        completions.add("1"); // Single item
                        
                        // Add some common fractions
                        if (totalCount > 2) {
                            completions.add(String.valueOf(totalCount / 2)); // Half
                        }
                        if (totalCount > 4) {
                            completions.add(String.valueOf(totalCount / 4)); // Quarter
                        }
                    }
                } catch (IllegalArgumentException e) {
                    // Invalid material, suggest common amounts
                    completions.add("1");
                    completions.add("10");
                }
            }
        } else if (args.length == 4) {
            // Arenas
            for (String arenaId : plugin.getArenaManager().getArenas().keySet()) {
                if (arenaId.toLowerCase().startsWith(args[3].toLowerCase())) {
                    completions.add(arenaId);
                }
            }
        } else if (args.length == 5) {
            // Equipment
            for (String equipId : plugin.getEquipmentManager().getEquipmentSets().keySet()) {
                if (equipId.toLowerCase().startsWith(args[4].toLowerCase())) {
                    completions.add(equipId);
                }
            }
        }
        
        return completions;
    }
}