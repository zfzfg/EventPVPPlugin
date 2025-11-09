package de.zfzfg.eventplugin.commands;

import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.eventplugin.util.ColorUtil;
import de.zfzfg.core.security.Permission;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class EventPvpCommand implements CommandExecutor {

    private final EventPlugin plugin;

    public EventPvpCommand(EventPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ColorUtil.color(plugin.getConfigManager().getPrefix() + " &7Nutze: &e/" + label + " reload"));
            return true;
        }

        String sub = args[0].toLowerCase();
        if (sub.equals("reload")) {
            if (!Permission.EVENTPVP_ADMIN.check(sender)) {
                sender.sendMessage(ColorUtil.color(plugin.getConfigManager().getMessage("general.no-permission")));
                return true;
            }
            // Zentraler Reload über ConfigurationService
            plugin.getConfigurationService().reloadAll();
            sender.sendMessage(ColorUtil.color(plugin.getConfigManager().getPrefix() + " &aAlle Konfigurationen neu geladen."));
            return true;
        }

        sender.sendMessage(ColorUtil.color(plugin.getConfigManager().getPrefix() + " &cUnbekannter Subcommand."));
        return true;
    }
}