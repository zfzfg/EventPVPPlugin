package de.zfzfg.pvpwager.commands;

import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.pvpwager.utils.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class PvPInfoCommand implements CommandExecutor {
    
    private final EventPlugin plugin;
    
    public PvPInfoCommand(EventPlugin plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(MessageUtil.color("&cDieser Befehl kann nur von Spielern verwendet werden!"));
            return true;
        }
        
        Player player = (Player) sender;
        
        // Header
        player.sendMessage(MessageUtil.color(""));
        player.sendMessage(MessageUtil.color("&6&l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        player.sendMessage(MessageUtil.color("&e&lPVP WAGER - BEFEHLSÜBERSICHT"));
        player.sendMessage(MessageUtil.color("&6&l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        player.sendMessage(MessageUtil.color(""));
        
        // Basis-Befehle
        player.sendMessage(MessageUtil.color("&e&l⚔ BASIS-BEFEHLE:"));
        player.sendMessage(MessageUtil.color("&7/pvp <spieler> &8- &7Fordere jemanden zu einem Kampf heraus"));
        player.sendMessage(MessageUtil.color("&7/pvp accept <spieler> &8- &7Akzeptiere eine Herausforderung"));
        player.sendMessage(MessageUtil.color("&7/pvp deny <spieler> &8- &7Lehne eine Herausforderung ab"));
        player.sendMessage(MessageUtil.color("&7/pvp spectate <spieler> &8- &7Schaue einem Match zu"));
        player.sendMessage(MessageUtil.color(""));
        
        // Match-Befehle
        player.sendMessage(MessageUtil.color("&c&l⚔ MATCH-BEFEHLE:"));
        player.sendMessage(MessageUtil.color("&7/surrender &8- &7Gib das aktuelle Match auf"));
        player.sendMessage(MessageUtil.color("&7/draw &8- &7Bitte um Unentschieden (beide müssen zustimmen)"));
        player.sendMessage(MessageUtil.color(""));
        
        // Antwort-Befehle
        player.sendMessage(MessageUtil.color("&a&l📋 ANTWORT-BEFEHLE:"));
        player.sendMessage(MessageUtil.color("&7/pvpanswer <wette> <menge> <arena> <ausrüstung> &8- &7Antworte auf eine Herausforderung"));
        player.sendMessage(MessageUtil.color("&7/pvpyes &8- &7Bestätige deine aktuelle Wette"));
        player.sendMessage(MessageUtil.color("&7/pvpno &8- &7Lehne deine aktuelle Wette ab"));
        player.sendMessage(MessageUtil.color(""));
        
        // Beispiele für /pvpanswer
        player.sendMessage(MessageUtil.color("&b&l💡 BEISPIELE FÜR /pvpanswer:"));
        player.sendMessage(MessageUtil.color("&7/pvpanswer money 100 &8- &7Setze 100$ als Wette"));
        player.sendMessage(MessageUtil.color("&7/pvpanswer items &8- &7Setze Items aus deinem Inventar als Wette"));
        player.sendMessage(MessageUtil.color("&7/pvpanswer money 50 nether &8- &7Setze 50$ und wähle die Nether-Arena"));
        player.sendMessage(MessageUtil.color("&7/pvpanswer items forest diamond &8- &7Items + Wald-Arena + Diamant-Ausrüstung"));
        player.sendMessage(MessageUtil.color(""));
        
        // Wichtige Hinweise
        player.sendMessage(MessageUtil.color("&6&l⚠ WICHTIGE HINWEISE:"));
        player.sendMessage(MessageUtil.color("&7• &eWelt-Ladung &7- manche Arenen müssen vorher geladen werden"));
        player.sendMessage(MessageUtil.color("&7• &eBestätigung &7- beide Spieler müssen ihre Auswahl bestätigen"));
        player.sendMessage(MessageUtil.color("&7• &eSicherheit &7- bei Abbruch werden alle Items zurückgegeben"));
        player.sendMessage(MessageUtil.color(""));
        
        // Admin-Bereich
        if (player.hasPermission("pvpwager.admin")) {
            player.sendMessage(MessageUtil.color("&4&l🔧 ADMIN-BEFEHLE:"));
            player.sendMessage(MessageUtil.color("&7/pvpadmin reload &8- &7Lädt alle Konfigurationen neu"));
            player.sendMessage(MessageUtil.color("&7/pvpadmin stop <spieler> &8- &7Stoppe das Match eines Spielers"));
            player.sendMessage(MessageUtil.color("&7/pvpadmin arenas &8- &7Zeige alle Arenen an"));
            player.sendMessage(MessageUtil.color("&7/pvpadmin equipment &8- &7Zeige alle Ausrüstungssets an"));
            player.sendMessage(MessageUtil.color(""));
        }
        
        // Footer
        player.sendMessage(MessageUtil.color("&6&l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        player.sendMessage(MessageUtil.color("&eWeitere Hilfe? Frag einen Admin oder schreibe /help"));
        player.sendMessage(MessageUtil.color("&6&l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        player.sendMessage(MessageUtil.color(""));
        
        return true;
    }
}