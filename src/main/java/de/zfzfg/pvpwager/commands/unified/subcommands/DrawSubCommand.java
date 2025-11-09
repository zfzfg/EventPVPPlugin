package de.zfzfg.pvpwager.commands.unified.subcommands;

import de.zfzfg.core.commands.SubCommand;
import de.zfzfg.core.util.Time;
import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.pvpwager.models.Match;
import de.zfzfg.pvpwager.models.MatchState;
import de.zfzfg.pvpwager.utils.MessageUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class DrawSubCommand extends SubCommand {
    public DrawSubCommand(EventPlugin plugin) { super(plugin); }

    @Override
    public String getName() { return "draw"; }

    @Override
    public String getPermission() { return "pvpwager.command"; }

    @Override
    public String getUsage() { return "/pvp draw [accept|deny]"; }

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
            MessageUtil.sendMessage(player, "&cYou can only vote for a draw during an active match!");
            return true;
        }

        if (args.length > 0) {
            if (args[0].equalsIgnoreCase("accept")) {
                if (!match.isDrawVoteActive()) {
                    MessageUtil.sendMessage(player, "&cNo active draw vote!");
                    return true;
                }
                if (match.getDrawVoteInitiator().equals(player.getUniqueId())) {
                    MessageUtil.sendMessage(player, "&cYou initiated the draw vote, wait for your opponent!");
                    return true;
                }
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
                if (!match.isDrawVoteActive()) {
                    MessageUtil.sendMessage(player, "&cNo active draw vote!");
                    return true;
                }
                if (match.getDrawVoteInitiator().equals(player.getUniqueId())) {
                    MessageUtil.sendMessage(player, "&cYou initiated the draw vote! Use &e/pvp draw &cagain to cancel.");
                    return true;
                }
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

        if (match.isDrawVoteActive()) {
            if (match.getDrawVoteInitiator().equals(player.getUniqueId())) {
                match.setDrawVoteActive(false);
                match.setDrawVoteInitiator(null);
                MessageUtil.sendMessage(player, "&cYou cancelled your draw vote.");
                Player opponent = match.getOpponent(player);
                if (opponent != null) {
                    MessageUtil.sendMessage(opponent, "&e" + player.getName() + " &ccancelled their draw vote.");
                }
                return true;
            } else {
                Player opponent = match.getOpponent(player);
                MessageUtil.sendMessage(player, "&e" + (opponent != null ? opponent.getName() : "Opponent") + " &7has already voted for a draw!");
                MessageUtil.sendMessage(player, "&7Type &a/pvp draw accept &7or &c/pvp draw deny");
                return true;
            }
        }

        match.setDrawVoteActive(true);
        match.setDrawVoteInitiator(player.getUniqueId());
        match.broadcast("");
        match.broadcast("&e&l━━━━━━━━━━━━━━━━━━━━━━━");
        match.broadcast("&e&lDRAW VOTE STARTED");
        match.broadcast("&e&l━━━━━━━━━━━━━━━━━━━━━━━");
        match.broadcast("");
        match.broadcast("&e" + player.getName() + " &7wants to end the match in a draw.");
        match.broadcast("");
        Player opponent = match.getOpponent(player);
        match.broadcast("&e" + (opponent != null ? opponent.getName() : "Opponent") + "&7, type:");
        match.broadcast("  &a/pvp draw accept &7- Accept draw");
        match.broadcast("  &c/pvp draw deny &7- Deny draw");
        match.broadcast("");
        match.broadcast("&7Time limit: &e" + plugin.getPvpConfigManager().getConfig().getInt("settings.match.draw-vote-time", 30) + " seconds");
        match.broadcast("");

        int drawVoteTime = plugin.getPvpConfigManager().getConfig().getInt("settings.match.draw-vote-time", 30);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
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