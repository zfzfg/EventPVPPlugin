package de.zfzfg.pvpwager.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.pvpwager.utils.MessageUtil;

public class PvPAdminCommand implements CommandExecutor {
    private final EventPlugin plugin;
    
    public PvPAdminCommand(EventPlugin plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("pvpwager.admin")) {
            MessageUtil.sendMessage(sender, "&cYou don't have permission to use this command!");
            return true;
        }
        
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        
        switch (args[0].toLowerCase()) {
            case "reload":
                handleReload(sender);
                break;
                
            case "stopall":
                handleStopAll(sender);
                break;
                
            case "info":
                handleInfo(sender);
                break;
                
            default:
                sendHelp(sender);
                break;
        }
        
        return true;
    }
    
    private void handleReload(CommandSender sender) {
        try {
            // Reload all configs
            plugin.getPvpConfigManager().reloadConfigs();
            
            // Reload arenas
            plugin.getArenaManager().reloadArenas();
            
            // Reload equipment sets
            plugin.getEquipmentManager().reloadEquipmentSets();
            
            MessageUtil.sendMessage(sender, "");
            MessageUtil.sendMessage(sender, "&a&l━━━━━━━━━━━━━━━━━━━━━━━");
            MessageUtil.sendMessage(sender, "&a&lCONFIG RELOADED!");
            MessageUtil.sendMessage(sender, "&a&l━━━━━━━━━━━━━━━━━━━━━━━");
            MessageUtil.sendMessage(sender, "");
            MessageUtil.sendMessage(sender, "&7✓ Main config reloaded");
            MessageUtil.sendMessage(sender, "&7✓ Messages reloaded");
            MessageUtil.sendMessage(sender, "&7✓ Arenas reloaded: &e" + plugin.getArenaManager().getArenas().size());
            MessageUtil.sendMessage(sender, "&7✓ Equipment sets reloaded: &e" + plugin.getEquipmentManager().getEquipmentSets().size());
            MessageUtil.sendMessage(sender, "");
            MessageUtil.sendMessage(sender, "&eNote: &7Active matches are not affected");
            MessageUtil.sendMessage(sender, "");
            
        } catch (Exception e) {
            MessageUtil.sendMessage(sender, "&cError reloading configs: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void handleStopAll(CommandSender sender) {
        int count = plugin.getMatchManager().stopAllMatches();
        
        MessageUtil.sendMessage(sender, "");
        MessageUtil.sendMessage(sender, "&e&l━━━━━━━━━━━━━━━━━━━━━━━");
        MessageUtil.sendMessage(sender, "&e&lMATCHES STOPPED");
        MessageUtil.sendMessage(sender, "&e&l━━━━━━━━━━━━━━━━━━━━━━━");
        MessageUtil.sendMessage(sender, "");
        MessageUtil.sendMessage(sender, "&7Stopped &e" + count + " &7active match(es)");
        MessageUtil.sendMessage(sender, "&7All wagers have been returned");
        MessageUtil.sendMessage(sender, "");
    }
    
    private void handleInfo(CommandSender sender) {
        int activeMatches = plugin.getMatchManager().getActiveMatchCount();
        
        MessageUtil.sendMessage(sender, "");
        MessageUtil.sendMessage(sender, "&6&l━━━━━━━━━━━━━━━━━━━━━━━");
        MessageUtil.sendMessage(sender, "&6&lPVPWAGER INFO");
        MessageUtil.sendMessage(sender, "&6&l━━━━━━━━━━━━━━━━━━━━━━━");
        MessageUtil.sendMessage(sender, "");
        MessageUtil.sendMessage(sender, "&7Version: &e" + plugin.getDescription().getVersion());
        MessageUtil.sendMessage(sender, "&7Active Matches: &e" + activeMatches);
        MessageUtil.sendMessage(sender, "&7Loaded Arenas: &e" + plugin.getArenaManager().getArenas().size());
        MessageUtil.sendMessage(sender, "&7Loaded Equipment: &e" + plugin.getEquipmentManager().getEquipmentSets().size());
        MessageUtil.sendMessage(sender, "&7Economy: " + (plugin.hasEconomy() ? "&aEnabled" : "&cDisabled"));
        MessageUtil.sendMessage(sender, "");
    }
    
    private void sendHelp(CommandSender sender) {
        MessageUtil.sendMessage(sender, "");
        MessageUtil.sendMessage(sender, "&6&l━━━━━━━━━━━━━━━━━━━━━━━");
        MessageUtil.sendMessage(sender, "&6&lPVPWAGER ADMIN");
        MessageUtil.sendMessage(sender, "&6&l━━━━━━━━━━━━━━━━━━━━━━━");
        MessageUtil.sendMessage(sender, "");
        MessageUtil.sendMessage(sender, "&e/pvpadmin reload &8- &7Reload all configs");
        MessageUtil.sendMessage(sender, "&e/pvpadmin stopall &8- &7Stop all active matches");
        MessageUtil.sendMessage(sender, "&e/pvpadmin info &8- &7Show plugin information");
        MessageUtil.sendMessage(sender, "");
    }
}
