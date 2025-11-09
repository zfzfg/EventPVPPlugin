package de.zfzfg.pvpwager.commands.unified.subcommands;

import de.zfzfg.core.commands.SubCommand;
import de.zfzfg.core.util.CommandCooldownManager;
import de.zfzfg.core.util.InputValidator;
import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.pvpwager.utils.MessageUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class ChallengeSubCommand extends SubCommand {
    private final CommandCooldownManager cooldowns = new CommandCooldownManager();

    public ChallengeSubCommand(EventPlugin plugin) { super(plugin); }

    @Override
    public String getName() { return "challenge"; }

    @Override
    public String getPermission() { return "pvpwager.command"; }

    @Override
    public String getUsage() { return "/pvp challenge <player>"; }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this command!");
            return true;
        }
        Player player = (Player) sender;
        if (!cooldowns.checkAndApply(player, "pvp-challenge")) {
            return true;
        }
        if (args.length == 0) {
            MessageUtil.sendMessage(player, "&cUsage: /pvp challenge <player>");
            return true;
        }
        try {
            Player target = InputValidator.validateOnlinePlayer(args[0]);
            if (player.equals(target)) {
                MessageUtil.sendMessage(player, "&cYou cannot send a request to yourself!");
                return true;
            }
            if (plugin.getMatchManager().getMatchByPlayer(player) != null) {
                MessageUtil.sendMessage(player, "&cYou are already in a match!");
                return true;
            }
            if (plugin.getMatchManager().getMatchByPlayer(target) != null) {
                MessageUtil.sendMessage(player, "&c&e" + target.getName() + " &7is already in a match!");
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
        } catch (IllegalArgumentException ex) {
            MessageUtil.sendMessage(player, "&c" + ex.getMessage());
        }
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        // Simple player completion handled by PvPUnifiedCommand's smart completer
        return java.util.Collections.emptyList();
    }
}