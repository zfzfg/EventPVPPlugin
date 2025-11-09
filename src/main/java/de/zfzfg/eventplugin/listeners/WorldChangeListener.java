package de.zfzfg.eventplugin.listeners;

import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.eventplugin.session.EventSession;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.Optional;

public class WorldChangeListener implements Listener {
    
    private final EventPlugin plugin;
    
    public WorldChangeListener(EventPlugin plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        Optional<EventSession> sessionOpt = plugin.getEventManager().getPlayerSession(player);
        
        if (sessionOpt.isPresent()) {
            EventSession session = sessionOpt.get();
            
            // Wenn Spieler spectatet und Welt wechselt, entferne Vanish/Fly
            if (session.isSpectator(player)) {
                String eventWorldName = session.getConfig().getEventWorld();
                String lobbyWorldName = session.getConfig().getLobbyWorld();
                
                // Wenn Spieler Event-Welt oder Lobby-Welt verlässt
                if (!player.getWorld().getName().equals(eventWorldName) && 
                    !player.getWorld().getName().equals(lobbyWorldName)) {
                    
                    // Entferne Vanish und Fly
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "v " + player.getName());
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "fly " + player.getName());
                }
            }
        }
    }
    
    @EventHandler
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        String command = event.getMessage().toLowerCase();
        
        Optional<EventSession> sessionOpt = plugin.getEventManager().getPlayerSession(player);
        
        if (sessionOpt.isPresent()) {
            EventSession session = sessionOpt.get();
            
            // Prüfe ob Spieler in Event-Welt oder Lobby-Welt ist
            String eventWorldName = session.getConfig().getEventWorld();
            String lobbyWorldName = session.getConfig().getLobbyWorld();
            String currentWorld = player.getWorld().getName();
            
            // Hole Command-Restriction Setting
            String restriction = plugin.getConfigManager().getCommandRestriction();
            
            boolean shouldBlock = false;
            
            // Prüfe basierend auf Einstellung
            switch (restriction.toLowerCase()) {
                case "both":
                    shouldBlock = currentWorld.equals(eventWorldName) || currentWorld.equals(lobbyWorldName);
                    break;
                case "event":
                    shouldBlock = currentWorld.equals(eventWorldName);
                    break;
                case "lobby":
                    shouldBlock = currentWorld.equals(lobbyWorldName);
                    break;
                case "none":
                    shouldBlock = false;
                    break;
                default:
                    shouldBlock = currentWorld.equals(eventWorldName) || currentWorld.equals(lobbyWorldName);
                    break;
            }
            
            if (shouldBlock) {
                // OP/Bypass erlaubt alle Befehle
                if (player.isOp() || player.hasPermission("eventpvp.opbypass")) {
                    return;
                }
                // Nur /event leave erlauben (und Aliases)
                if (!command.startsWith("/event leave") && 
                    !command.startsWith("/ev leave") && 
                    !command.startsWith("/events leave")) {
                    
                    event.setCancelled(true);
                    player.sendMessage(org.bukkit.ChatColor.RED + "Commands sind während des Events gesperrt! Nutze /event leave um das Event zu verlassen.");
                }
            }
        }
    }
}