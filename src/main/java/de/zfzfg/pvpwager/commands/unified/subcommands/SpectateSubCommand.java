package de.zfzfg.pvpwager.commands.unified.subcommands;

import de.zfzfg.core.commands.SubCommand;
import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.pvpwager.models.Match;
import de.zfzfg.pvpwager.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SpectateSubCommand extends SubCommand {
    public SpectateSubCommand(EventPlugin plugin) { super(plugin); }

    @Override
    public String getName() { return "spectate"; }

    @Override
    public String getPermission() { return "pvpwager.spectate"; }

    @Override
    public String getUsage() { return "/pvp spectate <player>"; }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this command!");
            return true;
        }
        Player player = (Player) sender;
        if (args.length < 1) {
            MessageUtil.sendMessage(player, "&cUsage: /pvp spectate <player>");
            return true;
        }
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null || !target.isOnline()) {
            MessageUtil.sendMessage(player, "&cPlayer &e" + args[0] + " &cis not online!");
            return true;
        }
        Match match = plugin.getMatchManager().getMatchByPlayer(target);
        if (match == null) {
            MessageUtil.sendMessage(player, "&c&e" + target.getName() + " &cis not in a match!");
            return true;
        }
        if (match.getSpectators().size() >= plugin.getPvpConfigManager().getConfig().getInt("max-spectators", 10)) {
            MessageUtil.sendMessage(player, "&cThe match is full of spectators!");
            return true;
        }
        plugin.getMatchManager().addSpectator(match, player);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            player.teleport(match.getArena().getSpectatorSpawn());
            player.setGameMode(GameMode.SPECTATOR);
            plugin.getMatchManager().markTeleported(player);
            MessageUtil.sendMessage(player, "&aYou are now spectating &e" + match.getPlayer1().getName() + " &7vs &e" + match.getPlayer2().getName() + "&a!");
            match.broadcast("&e" + player.getName() + " &7is now spectating the match!");
        }, 20L);
        return true;
    }
}