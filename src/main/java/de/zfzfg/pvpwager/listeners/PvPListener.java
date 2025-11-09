package de.zfzfg.pvpwager.listeners;

import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.pvpwager.models.Match;
import de.zfzfg.pvpwager.models.MatchState;
import de.zfzfg.pvpwager.utils.MessageUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;

public class PvPListener implements Listener {
    
    private final EventPlugin plugin;
    
    public PvPListener(EventPlugin plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler
    public void onPlayerDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player) || !(event.getEntity() instanceof Player)) {
            return;
        }
        
        Player attacker = (Player) event.getDamager();
        Player victim = (Player) event.getEntity();
        
        // Check if attacker is a spectator
        Match attackerMatch = plugin.getMatchManager().getMatchByPlayer(attacker);
        if (attackerMatch != null && attackerMatch.getSpectators().contains(attacker.getUniqueId())) {
            event.setCancelled(true);
            MessageUtil.sendMessage(attacker, "&cDu kannst als Zuschauer nicht angreifen!");
            return;
        }
        
        // Check if victim is a spectator
        Match victimMatch = plugin.getMatchManager().getMatchByPlayer(victim);
        if (victimMatch != null && victimMatch.getSpectators().contains(victim.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        
        // Check if both are in the same match
        Match match = plugin.getMatchManager().getMatch(attacker, victim);
        if (match == null) {
            // PvP außerhalb von Matches ist erlaubt (für Events)
            return;
        }
        
        // Check match state - only allow damage during FIGHTING
        if (match.getState() != MatchState.FIGHTING) {
            event.setCancelled(true);
            MessageUtil.sendMessage(attacker, "&cDas Match hat noch nicht begonnen oder ist beendet!");
            return;
        }
        
        // Allow damage in active match
    }
    
    @EventHandler
    public void onSpectatorDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        
        Player player = (Player) event.getEntity();
        Match match = plugin.getMatchManager().getMatchByPlayer(player);
        
        // Protect spectators from all damage
        if (match != null && match.getSpectators().contains(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }
    
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        Match match = plugin.getMatchManager().getMatchByPlayer(player);
        
        if (match != null && match.getState() == MatchState.FIGHTING) {
            Player killer = player.getKiller();
            // Evaluate outcome one tick later to catch simultaneous deaths (double-kill/void)
            org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (match.getState() != MatchState.FIGHTING) return; // already handled

                Player p1 = match.getPlayer1();
                Player p2 = match.getPlayer2();
                boolean p1Dead = p1 == null || p1.isDead() || p1.getHealth() <= 0.0;
                boolean p2Dead = p2 == null || p2.isDead() || p2.getHealth() <= 0.0;

                if (p1Dead && p2Dead) {
                    match.broadcast("");
                    match.broadcast("&a&l━━━━━━━━━━━━━━━━━━━━━━━");
                    match.broadcast("&a&lDRAW (double-death)!");
                    match.broadcast("&a&l━━━━━━━━━━━━━━━━━━━━━━━");
                    match.broadcast("");
                    match.broadcast("&7Both players died nearly simultaneously.");
                    plugin.getMatchManager().endMatch(match, null, true);
                    return;
                }

                // Standard outcome
                if (killer != null && (killer.equals(match.getPlayer1()) || killer.equals(match.getPlayer2()))) {
                    plugin.getMatchManager().endMatch(match, killer, false);
                    event.setDeathMessage(MessageUtil.color(
                        "&c" + player.getName() + " &7wurde von &c" + killer.getName() + 
                        " &7im PvP-Match besiegt!"
                    ));
                } else {
                    Player opponent = match.getOpponent(player);
                    if (opponent != null) {
                        plugin.getMatchManager().endMatch(match, opponent, false);
                        String deathCause = getDeathCause(event.getEntity().getLastDamageCause());
                        event.setDeathMessage(MessageUtil.color(
                            "&c" + player.getName() + " &7ist " + deathCause + " &7im PvP-Match gestorben!"
                        ));
                    }
                }
            }, 1L);
            
            // Prevent item/XP drops for all deaths in match
            event.getDrops().clear();
            event.setDroppedExp(0);
            
            // Keep inventory
            event.setKeepInventory(true);
        }
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Match match = plugin.getMatchManager().getMatchByPlayer(player);
        if (match != null) {
            // Respawn directly at original location to avoid void or unsafe spawns
            java.util.Map<java.util.UUID, org.bukkit.Location> origins = match.getOriginalLocations();
            org.bukkit.Location origin = origins.get(player.getUniqueId());
            if (origin != null) {
                event.setRespawnLocation(origin);
            }
        }
    }
    
    private String getDeathCause(EntityDamageEvent damageEvent) {
        if (damageEvent == null) return "&7unbekannt";
        
        DamageCause cause = damageEvent.getCause();
        
        if (cause == DamageCause.FALL) {
            return "&7beim Fallen";
        } else if (cause == DamageCause.FIRE || cause == DamageCause.FIRE_TICK) {
            return "&7im Feuer";
        } else if (cause == DamageCause.LAVA) {
            return "&7in Lava";
        } else if (cause == DamageCause.DROWNING) {
            return "&7beim Ertrinken";
        } else if (cause == DamageCause.SUFFOCATION) {
            return "&7am Ersticken";
        } else if (cause == DamageCause.STARVATION) {
            return "&7am Verhungern";
        } else if (cause == DamageCause.VOID) {
            return "&7in der Void";
        } else if (cause == DamageCause.LIGHTNING) {
            return "&7durch einen Blitz";
        } else if (cause == DamageCause.BLOCK_EXPLOSION || cause == DamageCause.ENTITY_EXPLOSION) {
            return "&7in einer Explosion";
        } else if (cause == DamageCause.MAGIC) {
            return "&7durch Magie";
        } else if (cause == DamageCause.WITHER) {
            return "&7am Verwelken";
        } else if (cause == DamageCause.CONTACT) {
            return "&7an einem Kaktus";
        } else {
            return "&7durch Umweltschaden";
        }
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        Match match = plugin.getMatchManager().getMatchByPlayer(player);
        
        if (match != null) {
            // Check if player is spectator
            if (match.getSpectators().contains(player.getUniqueId())) {
                plugin.getMatchManager().removeSpectator(match, player);
                match.broadcast("&e" + player.getName() + " &7hat das Zuschauen beendet.");
                return;
            }
            
            // Player disconnected during match setup or fighting
            if (match.getState() == MatchState.SETUP || match.getState() == MatchState.STARTING) {
                // Cancel match setup
                Player opponent = match.getOpponent(player);
                if (opponent != null) {
                    MessageUtil.sendMessage(opponent, 
                        "&e" + player.getName() + " &chat die Verbindung getrennt! Match abgebrochen.");
                }
                plugin.getMatchManager().endMatch(match, null, true);
            } else if (match.getState() == MatchState.FIGHTING) {
                // Player disconnected during fight - opponent wins
                Player opponent = match.getOpponent(player);
                if (opponent != null) {
                    plugin.getMatchManager().endMatch(match, opponent, false);
                    MessageUtil.sendMessage(opponent, 
                        "&e" + player.getName() + " &chat die Verbindung getrennt! Du gewinnst das Match!");
                }
            }
        }
    }
}