package de.zfzfg.pvpwager.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.pvpwager.models.Match;
import de.zfzfg.pvpwager.models.MatchState;
import de.zfzfg.pvpwager.utils.MessageUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SurrenderCommand implements CommandExecutor {
    
    private final EventPlugin plugin;
    private final Map<UUID, Long> surrenderConfirmations = new HashMap<>();
    private static final long CONFIRMATION_TIMEOUT = 10000; // 10 seconds
    
    public SurrenderCommand(EventPlugin plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this command!");
            return true;
        }
        
        Player player = (Player) sender;
        Match match = plugin.getMatchManager().getMatchByPlayer(player);
        
        // Check if player is in a match
        if (match == null) {
            MessageUtil.sendMessage(player, plugin.getPvpConfigManager().getMessages().getString("messages.error.not-in-match"));
            return true;
        }
        
        // Check if match is active
        if (match.getState() != MatchState.FIGHTING) {
            MessageUtil.sendMessage(player, "&cYou can only surrender during an active match!");
            return true;
        }
        
        // Handle confirmation
        if (args.length == 0 || !args[0].equalsIgnoreCase("confirm")) {
            // First surrender attempt - require confirmation
            surrenderConfirmations.put(player.getUniqueId(), System.currentTimeMillis());
            
            MessageUtil.sendMessage(player, "");
            MessageUtil.sendMessage(player, "&c&l━━━━━━━━━━━━━━━━━━━━━━━");
            MessageUtil.sendMessage(player, "&c&lWARNING: SURRENDER");
            MessageUtil.sendMessage(player, "&c&l━━━━━━━━━━━━━━━━━━━━━━━");
            MessageUtil.sendMessage(player, "");
            MessageUtil.sendMessage(player, "&7Are you sure you want to surrender?");
            MessageUtil.sendMessage(player, "&cYou will lose all your wager!");
            MessageUtil.sendMessage(player, "");
            MessageUtil.sendMessage(player, "&7Type &e/surrender confirm &7to confirm");
            MessageUtil.sendMessage(player, "&7or wait 10 seconds to cancel");
            MessageUtil.sendMessage(player, "");
            
            return true;
        }
        
        // Check if confirmation is valid
        Long confirmTime = surrenderConfirmations.get(player.getUniqueId());
        if (confirmTime == null || System.currentTimeMillis() - confirmTime > CONFIRMATION_TIMEOUT) {
            MessageUtil.sendMessage(player, "&cSurrender confirmation expired! Type &e/surrender &cagain.");
            surrenderConfirmations.remove(player.getUniqueId());
            return true;
        }
        
        // Confirm surrender
        surrenderConfirmations.remove(player.getUniqueId());
        
        // Announce surrender
        Player opponent = match.getOpponent(player);
        
        match.broadcast("");
        match.broadcast("&c&l━━━━━━━━━━━━━━━━━━━━━━━");
        match.broadcast("&c&l" + player.getName() + " HAS SURRENDERED!");
        match.broadcast("&c&l━━━━━━━━━━━━━━━━━━━━━━━");
        match.broadcast("");
        match.broadcast("&e" + opponent.getName() + " &awins the match!");
        match.broadcast("");
        
        // End match with opponent as winner
        plugin.getMatchManager().endMatch(match, opponent, false);
        
        return true;
    }
}