package de.zfzfg.eventplugin.security;

import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.eventplugin.session.EventSession;
import de.zfzfg.pvpwager.models.Match;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.entity.Player;

import java.util.Optional;

/**
 * Sicherheits-/Modus-Listener:
 * - Blockiert /v (vanish) und /fly für Nicht-OP-Spieler
 * - Verhindert, dass Zuschauer den Spectator-Modus verlassen
 */
public class PlayerModeListener implements Listener {

    private final EventPlugin plugin;

    public PlayerModeListener(EventPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        String cmd = event.getMessage().toLowerCase();

        // OP-Bypass erlaubt
        if (player.isOp() || player.hasPermission("eventpvp.opbypass")) {
            return;
        }

        // Blockiere Vanish/Fly Kommandos
        if (cmd.startsWith("/v ") || cmd.equals("/v") ||
            cmd.startsWith("/vanish") ||
            cmd.startsWith("/fly")) {
            event.setCancelled(true);
            player.sendMessage("§cDu darfst diesen Modus nicht verwenden.");
        }
    }

    @EventHandler
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        Player player = event.getPlayer();

        // Prüfe Event-Zuschauer
        Optional<EventSession> sessionOpt = plugin.getEventManager().getPlayerSession(player);
        boolean eventSpectator = sessionOpt.isPresent() && sessionOpt.get().isSpectator(player);

        // Prüfe PvP-Zuschauer
        Match match = plugin.getMatchManager().getMatchByPlayer(player);
        boolean pvpSpectator = match != null && match.getSpectators().contains(player.getUniqueId());

        if (eventSpectator || pvpSpectator) {
            // Zuschauer dürfen ausschließlich im Spectator-Modus bleiben
            if (event.getNewGameMode() != org.bukkit.GameMode.SPECTATOR) {
                event.setCancelled(true);
                player.sendMessage("§cAls Zuschauer darfst du nur im Spectator-Modus sein.");
            }
        }
    }
}