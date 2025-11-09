package de.zfzfg.pvpwager.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.pvpwager.utils.MessageUtil;

public class PvPAcceptCommand implements CommandExecutor {
    
    private final EventPlugin plugin;
    
    public PvPAcceptCommand(EventPlugin plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this command!");
            return true;
        }
        
        Player player = (Player) sender;
        
        if (args.length == 0) {
            MessageUtil.sendMessage(player, "&cUsage: /pvpa <player>");
            return true;
        }
        
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null || !target.isOnline()) {
            MessageUtil.sendMessage(player, "&cPlayer &e" + args[0] + " &cis not online!");
            return true;
        }
        
        if (player.equals(target)) {
            MessageUtil.sendMessage(player, "&cYou cannot send a request to yourself!");
            return true;
        }
        
        if (plugin.getRequestManager().hasPendingRequest(player)) {
            MessageUtil.sendMessage(player, "&cYou already have a pending request!");
            return true;
        }
        
        if (plugin.getRequestManager().hasPendingRequest(target)) {
            MessageUtil.sendMessage(player, "&c&e" + target.getName() + " &7already has a pending request!");
            return true;
        }
        
        plugin.getRequestManager().sendRequest(player, target);
        MessageUtil.sendMessage(player, "&aSent PvP wager request to &e" + target.getName() + "&a!");
        return true;
    }
}