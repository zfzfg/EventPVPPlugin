package de.zfzfg.pvpwager.commands;

import org.bukkit.Bukkit;
import de.zfzfg.core.util.Time;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.pvpwager.models.Match;
import de.zfzfg.pvpwager.models.MatchState;
import de.zfzfg.pvpwager.utils.MessageUtil;

public class DrawCommand implements CommandExecutor {
    
    private final EventPlugin plugin;
    
    public DrawCommand(EventPlugin plugin) {
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
            MessageUtil.sendMessage(player, "&cYou can only vote for a draw during an active match!");
            return true;
        }
        
        Player opponent = match.getOpponent(player);
        
        // Handle accept/deny
        if (args.length > 0) {
            if (args[0].equalsIgnoreCase("accept")) {
                // Check if there's an active draw vote
                if (!match.isDrawVoteActive()) {
                    MessageUtil.sendMessage(player, "&cNo active draw vote!");
                    return true;
                }
                
                // Check if player is the one who should accept (not the initiator)
                if (match.getDrawVoteInitiator().equals(player.getUniqueId())) {
                    MessageUtil.sendMessage(player, "&cYou initiated the draw vote, wait for your opponent!");
                    return true;
                }
                
                // Accept draw
                match.broadcast("");
                match.broadcast("&a&l━━━━━━━━━━━━━━━━━━━━━━━");
                match.broadcast("&a&lDRAW ACCEPTED!");
                match.broadcast("&a&l━━━━━━━━━━━━━━━━━━━━━━━");
                match.broadcast("");
                match.broadcast("&7Both players agreed to a draw.");
                match.broadcast("&7All wagers will be returned.");
                match.broadcast("");
                
                plugin.getMatchManager().endMatch(match, null, true);
                return true;
            }
            
            if (args[0].equalsIgnoreCase("deny")) {
                // Check if there's an active draw vote
                if (!match.isDrawVoteActive()) {
                    MessageUtil.sendMessage(player, "&cNo active draw vote!");
                    return true;
                }
                
                // Check if player is the one who should deny (not the initiator)
                if (match.getDrawVoteInitiator().equals(player.getUniqueId())) {
                    MessageUtil.sendMessage(player, "&cYou initiated the draw vote! Use &e/draw &cagain to cancel.");
                    return true;
                }
                
                // Deny draw
                match.setDrawVoteActive(false);
                match.setDrawVoteInitiator(null);
                
                match.broadcast("");
                match.broadcast("&c&l━━━━━━━━━━━━━━━━━━━━━━━");
                match.broadcast("&c&lDRAW DENIED!");
                match.broadcast("&c&l━━━━━━━━━━━━━━━━━━━━━━━");
                match.broadcast("");
                match.broadcast("&e" + player.getName() + " &7denied the draw vote.");
                match.broadcast("&7The match continues!");
                match.broadcast("");
                
                return true;
            }
        }
        
        // Initiate or cancel draw vote
        if (match.isDrawVoteActive()) {
            // Check if player is the initiator
            if (match.getDrawVoteInitiator().equals(player.getUniqueId())) {
                // Cancel own vote
                match.setDrawVoteActive(false);
                match.setDrawVoteInitiator(null);
                
                MessageUtil.sendMessage(player, "&cYou cancelled your draw vote.");
                MessageUtil.sendMessage(opponent, "&e" + player.getName() + " &ccancelled their draw vote.");
                return true;
            } else {
                MessageUtil.sendMessage(player, "&e" + opponent.getName() + " &7has already voted for a draw!");
                MessageUtil.sendMessage(player, "&7Type &a/draw accept &7or &c/draw deny");
                return true;
            }
        }
        
        // Initiate new draw vote
        match.setDrawVoteActive(true);
        match.setDrawVoteInitiator(player.getUniqueId());
        
        // Broadcast to players
        match.broadcast("");
        match.broadcast("&e&l━━━━━━━━━━━━━━━━━━━━━━━");
        match.broadcast("&e&lDRAW VOTE STARTED");
        match.broadcast("&e&l━━━━━━━━━━━━━━━━━━━━━━━");
        match.broadcast("");
        match.broadcast("&e" + player.getName() + " &7wants to end the match in a draw.");
        match.broadcast("");
        match.broadcast("&e" + opponent.getName() + "&7, type:");
        match.broadcast("  &a/draw accept &7- Accept draw");
        match.broadcast("  &c/draw deny &7- Deny draw");
        match.broadcast("");
            match.broadcast("&7Time limit: &e" + plugin.getPvpConfigManager().getConfig().getInt("settings.match.draw-vote-time", 30) + " seconds");
        match.broadcast("");
        
        // Schedule auto-cancel
        int drawVoteTime = plugin.getPvpConfigManager().getConfig().getInt("settings.match.draw-vote-time", 30);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (match.isDrawVoteActive() && match.getDrawVoteInitiator().equals(player.getUniqueId())) {
                match.setDrawVoteActive(false);
                match.setDrawVoteInitiator(null);
                
                match.broadcast("");
                match.broadcast("&c&lDraw vote expired!");
                match.broadcast("&7The match continues.");
                match.broadcast("");
            }
        }, Time.seconds(drawVoteTime));
        
        return true;
    }
}