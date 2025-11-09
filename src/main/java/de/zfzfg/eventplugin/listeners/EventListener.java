package de.zfzfg.eventplugin.listeners;

import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.eventplugin.session.EventSession;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.Optional;

public class EventListener implements Listener {
    
    private final EventPlugin plugin;
    
    public EventListener(EventPlugin plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        Optional<EventSession> sessionOpt = plugin.getEventManager().getPlayerSession(player);
        
        if (sessionOpt.isPresent()) {
            EventSession session = sessionOpt.get();
            if (session.getState() == EventSession.EventState.RUNNING) {
                session.handlePlayerDeath(player);
            }
        }
    }
    
    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Optional<EventSession> sessionOpt = plugin.getEventManager().getPlayerSession(player);
        
        if (sessionOpt.isPresent()) {
            EventSession session = sessionOpt.get();
            if (session.getState() == EventSession.EventState.RUNNING) {
                // Halte Spieler im Event (als Spectator wenn eliminiert) und respawne sicher
                if (session.getConfig().getDeathHandling().isSpectatorMode()) {
                    // Bevorzugt: Event-Lobby-Spawn
                    if (session.getConfig().getLobbySpawn() != null) {
                        event.setRespawnLocation(session.getConfig().getLobbySpawn());
                    } else {
                        // Fallback: Event-Welt Spawn oder Hauptwelt Spawn
                        Location safe = null;
                        String eventWorldName = session.getConfig().getEventWorld();
                        if (eventWorldName != null) {
                            org.bukkit.World eventWorld = org.bukkit.Bukkit.getWorld(eventWorldName);
                            if (eventWorld != null) safe = eventWorld.getSpawnLocation();
                        }
                        if (safe == null) {
                            org.bukkit.World main = org.bukkit.Bukkit.getWorld(plugin.getConfigManager().getMainWorld());
                            if (main != null) safe = main.getSpawnLocation();
                        }
                        if (safe != null) {
                            event.setRespawnLocation(safe);
                        }
                    }
                }
            }
        }
    }
    
    @EventHandler
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getEntity();
        Optional<EventSession> sessionOpt = plugin.getEventManager().getPlayerSession(player);
        
        if (sessionOpt.isPresent()) {
            EventSession session = sessionOpt.get();
            if (session.getState() == EventSession.EventState.RUNNING) {
                session.handleItemPickup(player, event.getItem().getItemStack().getType());
            }
        }
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        Optional<EventSession> sessionOpt = plugin.getEventManager().getPlayerSession(player);
        
        if (sessionOpt.isPresent()) {
            sessionOpt.get().removePlayer(player);
        }
    }
}