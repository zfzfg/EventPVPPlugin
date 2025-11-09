package de.zfzfg.pvpwager.commands.unified.subcommands;

import de.zfzfg.core.commands.SubCommand;
import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.pvpwager.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class DenySubCommand extends SubCommand {
    public DenySubCommand(EventPlugin plugin) { super(plugin); }

    @Override
    public String getName() { return "deny"; }

    @Override
    public String getPermission() { return "pvpwager.command"; }

    @Override
    public String getUsage() { return "/pvp deny [player]"; }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this command!");
            return true;
        }
        Player player = (Player) sender;
        if (args.length >= 1) {
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null || !target.isOnline()) {
                MessageUtil.sendMessage(player, "&cPlayer &e" + args[0] + " &cis not online!");
                return true;
            }
            plugin.getRequestManager().cancelRequest(target.getUniqueId(), player.getUniqueId());
            MessageUtil.sendMessage(player, "&cYou denied the PvP wager request from &e" + target.getName() + " &c!");
            MessageUtil.sendMessage(target, "&e" + player.getName() + " &cdenied your PvP wager request!");
        } else {
            MessageUtil.sendMessage(player, "&cUsage: /pvp deny <player>");
        }
        return true;
    }
}