package de.zfzfg.pvpwager.commands.unified.subcommands;

import de.zfzfg.core.commands.SubCommand;
import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.pvpwager.models.Match;
import de.zfzfg.pvpwager.models.MatchState;
import de.zfzfg.pvpwager.utils.MessageUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SurrenderSubCommand extends SubCommand {
    private final Map<UUID, Long> confirmations = new HashMap<>();
    private static final long TIMEOUT_MS = 10000L;

    public SurrenderSubCommand(EventPlugin plugin) { super(plugin); }

    @Override
    public String getName() { return "surrender"; }

    @Override
    public String getPermission() { return "pvpwager.command"; }

    @Override
    public String getUsage() { return "/pvp surrender [confirm]"; }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this command!");
            return true;
        }
        Player player = (Player) sender;
        Match match = plugin.getMatchManager().getMatchByPlayer(player);
        if (match == null) {
            MessageUtil.sendMessage(player, plugin.getPvpConfigManager().getMessages().getString("messages.error.not-in-match"));
            return true;
        }
        if (match.getState() != MatchState.FIGHTING) {
            MessageUtil.sendMessage(player, "&cYou can only surrender during an active match!");
            return true;
        }
        if (args.length == 0 || !args[0].equalsIgnoreCase("confirm")) {
            confirmations.put(player.getUniqueId(), System.currentTimeMillis());
            MessageUtil.sendMessage(player, "");
            MessageUtil.sendMessage(player, "&c&l━━━━━━━━━━━━━━━━━━━━━━━");
            MessageUtil.sendMessage(player, "&c&lWARNING: SURRENDER");
            MessageUtil.sendMessage(player, "&c&l━━━━━━━━━━━━━━━━━━━━━━━");
            MessageUtil.sendMessage(player, "");
            MessageUtil.sendMessage(player, "&7Are you sure you want to surrender?");
            MessageUtil.sendMessage(player, "&cYou will lose all your wager!");
            MessageUtil.sendMessage(player, "");
            MessageUtil.sendMessage(player, "&7Type &e/pvp surrender confirm &7to confirm");
            MessageUtil.sendMessage(player, "&7or wait 10 seconds to cancel");
            MessageUtil.sendMessage(player, "");
            return true;
        }
        Long confirmTime = confirmations.get(player.getUniqueId());
        if (confirmTime == null || System.currentTimeMillis() - confirmTime > TIMEOUT_MS) {
            MessageUtil.sendMessage(player, "&cSurrender confirmation expired! Type &e/pvp surrender &cagain.");
            confirmations.remove(player.getUniqueId());
            return true;
        }
        confirmations.remove(player.getUniqueId());
        Player opponent = match.getOpponent(player);
        match.broadcast("");
        match.broadcast("&c&l━━━━━━━━━━━━━━━━━━━━━━━");
        match.broadcast("&c&l" + player.getName() + " HAS SURRENDERED!");
        match.broadcast("&c&l━━━━━━━━━━━━━━━━━━━━━━━");
        match.broadcast("");
        match.broadcast("&e" + (opponent != null ? opponent.getName() : "Opponent") + " &awins the match!");
        match.broadcast("");
        plugin.getMatchManager().endMatch(match, opponent, false);
        return true;
    }
}