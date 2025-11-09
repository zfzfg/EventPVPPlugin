package de.zfzfg.pvpwager.commands;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.core.util.Time;
import org.bukkit.potion.PotionEffectType;
import de.zfzfg.pvpwager.models.Match;
import de.zfzfg.pvpwager.managers.MatchManager;
import de.zfzfg.pvpwager.utils.MessageUtil;

public class PvPCommand implements CommandExecutor {
    
    private final EventPlugin plugin;
    
    public PvPCommand(EventPlugin plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this command!");
            return true;
        }
        
        Player player = (Player) sender;
        MatchManager matchManager = plugin.getMatchManager();
        
        if (args.length == 0) {
            player.sendMessage("§e§lPvPWager §8» §7Usage:");
            player.sendMessage("§e/pvpa <player> §8- §7Send PvP wager request");
            player.sendMessage("§e/pvp accept <player> §8- §7Accept request");
            player.sendMessage("§e/pvp deny <player> §8- §7Deny request");
            player.sendMessage("§e/pvp spectate <player> §8- §7Spectate match");
            player.sendMessage("§e/pvp leave §8- §7Leave spectator mode");
            return true;
        }
        
        if (args[0].equalsIgnoreCase("accept") && args.length >= 2) {
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null || !target.isOnline()) {
                MessageUtil.sendMessage(player, "&cPlayer &e" + args[1] + " &cis not online!");
                return true;
            }
            
            if (plugin.getRequestManager().acceptRequest(player, target)) {
                MessageUtil.sendMessage(player, "&aYou accepted the PvP wager request from &e" + target.getName() + "&a!");
                MessageUtil.sendMessage(target, "&e" + player.getName() + " &aaccepted your PvP wager request!");
            } else {
                MessageUtil.sendMessage(player, "&cNo pending request from &e" + target.getName() + "&c!");
            }
            return true;
        }
        
        if (args[0].equalsIgnoreCase("deny") && args.length >= 2) {
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null || !target.isOnline()) {
                MessageUtil.sendMessage(player, "&cPlayer &e" + args[1] + " &cis not online!");
                return true;
            }
            
            plugin.getRequestManager().cancelRequest(target.getUniqueId(), player.getUniqueId());
            MessageUtil.sendMessage(player, "&cYou denied the PvP wager request from &e" + target.getName() + "&c!");
            MessageUtil.sendMessage(target, "&e" + player.getName() + " &cdenied your PvP wager request!");
            return true;
        }
        
        if (args[0].equalsIgnoreCase("spectate") && args.length >= 2) {
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null || !target.isOnline()) {
                MessageUtil.sendMessage(player, "&cPlayer &e" + args[1] + " &cis not online!");
                return true;
            }
            
            Match match = matchManager.getMatchByPlayer(target);
            if (match == null) {
                MessageUtil.sendMessage(player, "&c&e" + target.getName() + " &cis not in a match!");
                return true;
            }
            
            if (match.getSpectators().size() >= plugin.getPvpConfigManager().getConfig().getInt("max-spectators", 10)) {
                MessageUtil.sendMessage(player, "&cThe match is full of spectators!");
                return true;
            }
            
            // Add spectator to match (managed by MatchManager for O(1) lookup)
            matchManager.addSpectator(match, player);
            
            // Teleport to spectator spawn
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                player.teleport(match.getArena().getSpectatorSpawn());
                player.setGameMode(org.bukkit.GameMode.SPECTATOR);
                // Mark spectator as teleported for end-of-match return
                plugin.getMatchManager().markTeleported(player);
                
                MessageUtil.sendMessage(player, "&aYou are now spectating &e" + match.getPlayer1().getName() + " &7vs &e" + match.getPlayer2().getName() + "&a!");
                match.broadcast("&e" + player.getName() + " &7is now spectating the match!");
            }, Time.TICKS_PER_SECOND);
            
            return true;
        }
        
        if (args[0].equalsIgnoreCase("leave")) {
            Match match = matchManager.getMatchByPlayer(player);
            if (match == null || !match.getSpectators().contains(player.getUniqueId())) {
                MessageUtil.sendMessage(player, "&cYou are not spectating any match!");
                return true;
            }
            
            // Fetch original location and remove spectator via manager
            Location originalLocation = match.getOriginalLocation(player);
            matchManager.removeSpectator(match, player);
            
            // Teleport back to original location
            if (originalLocation != null) {
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    player.teleport(originalLocation);
                    player.setGameMode(org.bukkit.GameMode.SURVIVAL);
                    // Safety cleanup: clear lingering invisibility (from older plugins)
                    player.removePotionEffect(PotionEffectType.INVISIBILITY);
                    
                    MessageUtil.sendMessage(player, "&aYou left the spectator mode!");
                    match.broadcast("&e" + player.getName() + " &7left the spectator mode!");
                }, Time.TICKS_PER_SECOND);
            }
            
            return true;
        }
        
        player.sendMessage("§e§lPvPWager §8» §7Usage:");
        player.sendMessage("§e/pvpa <player> §8- §7Send PvP wager request");
        player.sendMessage("§e/pvp accept <player> §8- §7Accept request");
        player.sendMessage("§e/pvp deny <player> §8- §7Deny request");
        player.sendMessage("§e/pvp spectate <player> §8- §7Spectate match");
        player.sendMessage("§e/pvp leave §8- §7Leave spectator mode");
        return false;
    }
}