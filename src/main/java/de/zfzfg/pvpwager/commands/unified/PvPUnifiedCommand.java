package de.zfzfg.pvpwager.commands.unified;

import de.zfzfg.core.commands.SubCommand;
import de.zfzfg.core.commands.SmartTabCompleter;
import de.zfzfg.eventplugin.EventPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.*;

public class PvPUnifiedCommand implements CommandExecutor, TabCompleter {
    private final EventPlugin plugin;
    private final Map<String, SubCommand> subCommands = new HashMap<>();
    private final SmartTabCompleter smart;

    public PvPUnifiedCommand(EventPlugin plugin) {
        this.plugin = plugin;
        this.smart = new SmartTabCompleter(plugin);
        // Register subcommands
        register(new de.zfzfg.pvpwager.commands.unified.subcommands.ChallengeSubCommand(plugin));
        register(new de.zfzfg.pvpwager.commands.unified.subcommands.AcceptSubCommand(plugin));
        register(new de.zfzfg.pvpwager.commands.unified.subcommands.DenySubCommand(plugin));
        register(new de.zfzfg.pvpwager.commands.unified.subcommands.SpectateSubCommand(plugin));
        register(new de.zfzfg.pvpwager.commands.unified.subcommands.LeaveSubCommand(plugin));
        register(new de.zfzfg.pvpwager.commands.unified.subcommands.SurrenderSubCommand(plugin));
        register(new de.zfzfg.pvpwager.commands.unified.subcommands.DrawSubCommand(plugin));
    }

    private void register(SubCommand cmd) {
        subCommands.put(cmd.getName().toLowerCase(), cmd);
        for (String alias : cmd.getAliases()) {
            subCommands.put(alias.toLowerCase(), cmd);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        String name = args[0].toLowerCase();
        SubCommand sub = subCommands.get(name);
        if (sub == null) {
            sender.sendMessage("§cUnbekannter Sub-Command: " + args[0]);
            sendHelp(sender);
            return true;
        }
        String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
        return sub.execute(sender, subArgs);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return smart.filterStartsWith(new ArrayList<>(List.of(
                    "challenge", "accept", "deny", "spectate", "leave", "surrender", "draw"
            )), args[0]);
        }
        SubCommand sub = subCommands.get(args[0].toLowerCase());
        if (sub != null) {
            return sub.tabComplete(sender, Arrays.copyOfRange(args, 1, args.length));
        }
        return Collections.emptyList();
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6§l━━━━━━━━━━━━━━━━━━━");
        sender.sendMessage("§e§lPVP HILFE");
        sender.sendMessage("§6§l━━━━━━━━━━━━━━━━━━━");
        sender.sendMessage("§7Verwendung:");
        sender.sendMessage("§e/pvp challenge <player> [wager] [arena] [equipment]");
        sender.sendMessage("§e/pvp accept [player]");
        sender.sendMessage("§e/pvp deny [player]");
        sender.sendMessage("§e/pvp spectate <player>");
        sender.sendMessage("§e/pvp leave");
        sender.sendMessage("§e/pvp surrender [confirm]");
        sender.sendMessage("§e/pvp draw [accept|deny]");
    }
}