package de.zfzfg.eventplugin.manager;

import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.eventplugin.model.EventConfig;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Startet automatisch Events nach konfigurierten Intervallen und
 * wählt geeignete Events basierend auf Online-Spielern (optional).
 */
public class AutoEventManager {
    
    private final EventPlugin plugin;
    private BukkitTask autoEventTask;
    private final Random random;
    
    public AutoEventManager(EventPlugin plugin) {
        this.plugin = plugin;
        this.random = new Random();
    }
    
    public void start() {
        if (autoEventTask != null) {
            autoEventTask.cancel();
        }
        
        scheduleNextEvent();
        plugin.getLogger().info("Auto-Event System gestartet!");
    }
    
    public void stop() {
        if (autoEventTask != null) {
            autoEventTask.cancel();
            autoEventTask = null;
        }
        plugin.getLogger().info("Auto-Event System gestoppt!");
    }
    
    private void scheduleNextEvent() {
        int minInterval = plugin.getConfigManager().getAutoEventIntervalMin();
        int maxInterval = plugin.getConfigManager().getAutoEventIntervalMax();
        
        int interval = minInterval + random.nextInt(maxInterval - minInterval + 1);
        
        plugin.getLogger().info("Nächstes Auto-Event in " + interval + " Sekunden");
        
        autoEventTask = new BukkitRunnable() {
            @Override
            public void run() {
                startRandomEvent();
                scheduleNextEvent();
            }
        }.runTaskLater(plugin, de.zfzfg.core.util.Time.seconds(interval));
    }
    
    private void startRandomEvent() {
        // Prüfe ob genug Spieler online sind
        if (plugin.getConfigManager().shouldCheckOnlinePlayers()) {
            int onlinePlayers = Bukkit.getOnlinePlayers().size();
            
            // Sammle Events mit erfüllter Mindestspielerzahl
            List<EventConfig> eligibleEvents = new ArrayList<>();
            
            for (EventConfig config : plugin.getConfigManager().getAllEvents().values()) {
                if (!plugin.getEventManager().isEventActive(config.getId())) {
                    if (onlinePlayers >= config.getMinPlayers()) {
                        eligibleEvents.add(config);
                    }
                }
            }
            
            // Wenn keine Events möglich sind
            if (eligibleEvents.isEmpty()) {
                plugin.getLogger().info("Nicht genug Spieler online für Auto-Events (" + onlinePlayers + " Spieler)");
                return;
            }
            
            // Wähle zufälliges Event
            EventConfig selectedEvent = eligibleEvents.get(random.nextInt(eligibleEvents.size()));
            
            plugin.getLogger().info("Starte Auto-Event: " + selectedEvent.getDisplayName() + " (Min: " + selectedEvent.getMinPlayers() + ")");
            
            // Erstelle Event-Session
            if (plugin.getEventManager().createEvent(selectedEvent.getId())) {
                plugin.getEventManager().getSession(selectedEvent.getId())
                    .ifPresent(session -> {
                        session.startJoinPhase();
                        
                        // Speichere Event für Fallback
                        scheduleJoinPhaseCheck(selectedEvent, eligibleEvents);
                    });
            }
        } else {
            // Alte Logik ohne Online-Check
            List<EventConfig> availableEvents = new ArrayList<>();
            
            for (EventConfig config : plugin.getConfigManager().getAllEvents().values()) {
                if (!plugin.getEventManager().isEventActive(config.getId())) {
                    availableEvents.add(config);
                }
            }
            
            if (availableEvents.isEmpty()) {
                plugin.getLogger().warning("Keine verfügbaren Events für Auto-Start!");
                return;
            }
            
            EventConfig selectedEvent = availableEvents.get(random.nextInt(availableEvents.size()));
            
            plugin.getLogger().info("Starte Auto-Event: " + selectedEvent.getDisplayName());
            
            if (plugin.getEventManager().createEvent(selectedEvent.getId())) {
                plugin.getEventManager().getSession(selectedEvent.getId())
                    .ifPresent(session -> session.startJoinPhase());
            }
        }
    }
    
    private void scheduleJoinPhaseCheck(EventConfig originalEvent, List<EventConfig> eligibleEvents) {
        // Prüfe nach Join-Phase ob genug Spieler beigetreten sind
        int joinPhaseDuration = plugin.getConfigManager().getJoinPhaseDuration();
        
        new BukkitRunnable() {
            @Override
            public void run() {
                plugin.getEventManager().getSession(originalEvent.getId()).ifPresent(session -> {
                    // Wenn Event noch in Join-Phase oder Countdown ist
                    if (session.getState() == de.zfzfg.eventplugin.session.EventSession.EventState.JOIN_PHASE ||
                        session.getState() == de.zfzfg.eventplugin.session.EventSession.EventState.COUNTDOWN) {
                        
                        int participantCount = session.getParticipantCount();
                        
                        // Wenn zu wenig Spieler
                        if (participantCount < originalEvent.getMinPlayers()) {
                            plugin.getLogger().info("Zu wenig Spieler für " + originalEvent.getDisplayName() + 
                                " (" + participantCount + "/" + originalEvent.getMinPlayers() + ")");
                            
                            // Finde Event mit niedrigerer Mindestanzahl
                            EventConfig fallbackEvent = findFallbackEvent(eligibleEvents, participantCount);
                            
                            if (fallbackEvent != null) {
                                plugin.getLogger().info("Starte Fallback-Event: " + fallbackEvent.getDisplayName());
                                
                                // Breche aktuelles Event ab
                                session.cancelForFallback();
                                
                                // Starte neues Event nach kurzer Verzögerung
                                new BukkitRunnable() {
                                    @Override
                                    public void run() {
                                        if (plugin.getEventManager().createEvent(fallbackEvent.getId())) {
                                            plugin.getEventManager().getSession(fallbackEvent.getId())
                                                .ifPresent(newSession -> newSession.startJoinPhase());
                                        }
                                    }
                                }.runTaskLater(plugin, de.zfzfg.core.util.Time.seconds(2)); // 2 Sekunden Verzögerung
                                
                            } else {
                                plugin.getLogger().info("Kein Fallback-Event gefunden - Event wird verschoben");
                                session.cancelForFallback();
                            }
                        }
                    }
                });
            }
        }.runTaskLater(plugin, de.zfzfg.core.util.Time.seconds(joinPhaseDuration + 5)); // 5 Sekunden nach Join-Phase
    }
    
    private EventConfig findFallbackEvent(List<EventConfig> eligibleEvents, int availablePlayers) {
        // Finde Events mit niedrigerer Mindestanzahl als das ursprüngliche
        EventConfig bestFallback = null;
        int bestMinPlayers = 0;
        
        for (EventConfig event : eligibleEvents) {
            int minPlayers = event.getMinPlayers();
            
            // Event muss weniger Spieler benötigen als verfügbar sind
            if (minPlayers <= availablePlayers) {
                // Wähle das Event mit der höchsten Mindestanzahl (aber <= verfügbaren Spielern)
                if (minPlayers > bestMinPlayers) {
                    bestMinPlayers = minPlayers;
                    bestFallback = event;
                }
            }
        }
        
        return bestFallback;
    }
}