package de.zfzfg.eventplugin.commands;

import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.eventplugin.util.ColorUtil;
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
            if (!sender.hasPermission("eventpvp.admin")) {
                sender.sendMessage(ColorUtil.color(plugin.getConfigManager().getMessage("general.no-permission")));
                return true;
            }

            // Reload beide Config-Manager
            try {
                if (plugin.getCoreConfigManager() != null) {
                    plugin.getCoreConfigManager().reloadAll();
                }
            } catch (Exception e) {
                sender.sendMessage(ColorUtil.color(plugin.getConfigManager().getPrefix() + " &cFehler beim Reload der Core-Configs: " + e.getMessage()));
            }

            try {
                if (plugin.getConfigManager() != null) {
                    plugin.getConfigManager().reloadConfigs();
                }
            } catch (Exception e) {
                sender.sendMessage(ColorUtil.color(plugin.getConfigManager().getPrefix() + " &cFehler beim Reload der Event-Configs: " + e.getMessage()));
            }

            try {
                if (plugin.getPvpConfigManager() != null) {
                    plugin.getPvpConfigManager().reloadConfigs();
                }
            } catch (Exception e) {
                sender.sendMessage(ColorUtil.color(plugin.getConfigManager().getPrefix() + " &cFehler beim Reload der PvP-Configs: " + e.getMessage()));
            }

            // Reload abhängige Manager (Welten/Arenen & Equipment)
            try {
                if (plugin.getArenaManager() != null) {
                    plugin.getArenaManager().reloadArenas();
                }
            } catch (Exception e) {
                sender.sendMessage(ColorUtil.color(plugin.getConfigManager().getPrefix() + " &cFehler beim Reload der Arenen: " + e.getMessage()));
            }
            try {
                if (plugin.getEquipmentManager() != null) {
                    plugin.getEquipmentManager().reloadEquipmentSets();
                }
            } catch (Exception e) {
                sender.sendMessage(ColorUtil.color(plugin.getConfigManager().getPrefix() + " &cFehler beim Reload der Ausrüstung: " + e.getMessage()));
            }

            // Leere Caches
            try {
                if (plugin.getWorldStateManager() != null) {
                    plugin.getWorldStateManager().clearCache();
                }
            } catch (Exception ignored) {}

            sender.sendMessage(ColorUtil.color(plugin.getConfigManager().getPrefix() + " &aAlle Konfigurationen neu geladen."));
            return true;
        }

        sender.sendMessage(ColorUtil.color(plugin.getConfigManager().getPrefix() + " &cUnbekannter Subcommand."));
        return true;
    }
}