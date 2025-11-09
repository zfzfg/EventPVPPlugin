package de.zfzfg.pvpwager.commands.unified.subcommands;

import de.zfzfg.core.commands.SubCommand;
import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.pvpwager.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class AcceptSubCommand extends SubCommand {
    public AcceptSubCommand(EventPlugin plugin) { super(plugin); }

    @Override
    public String getName() { return "accept"; }

    @Override
    public String getPermission() { return "pvpwager.command"; }

    @Override
    public String getUsage() { return "/pvp accept [player]"; }

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
            if (plugin.getRequestManager().acceptRequest(player, target)) {
                MessageUtil.sendMessage(player, "&aYou accepted the PvP wager request from &e" + target.getName() + "&a!");
                MessageUtil.sendMessage(target, "&e" + player.getName() + " &aaccepted your PvP wager request!");
            } else {
                MessageUtil.sendMessage(player, "&cNo pending request from &e" + target.getName() + "&c!");
            }
        } else {
            MessageUtil.sendMessage(player, "&cUsage: /pvp accept <player>");
        }
        return true;
    }
}