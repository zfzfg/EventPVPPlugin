package de.zfzfg.pvpwager.managers;

import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.pvpwager.models.Match;
import de.zfzfg.pvpwager.models.MatchState;
import de.zfzfg.pvpwager.models.Arena;
import de.zfzfg.pvpwager.models.EquipmentSet;
import de.zfzfg.pvpwager.utils.MessageUtil;
import de.zfzfg.pvpwager.utils.InventoryUtil;
import de.zfzfg.pvpwager.models.CommandRequest;
import org.bukkit.Bukkit;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MatchManager {
    private final EventPlugin plugin;
    private final Map<UUID, Match> matches = new HashMap<>();
    private final Map<UUID, BukkitTask> countdownTasks = new HashMap<>();
    private final Map<UUID, BukkitTask> preTeleportCountdownTasks = new HashMap<>();
    private final Map<UUID, BukkitTask> matchTimerTasks = new HashMap<>();
    private final SpawnManager spawnManager;
    // O(1) Lookup: Spieler -> MatchId
    private final Map<UUID, UUID> playerToMatchId = new ConcurrentHashMap<>();
    
    // Track if players have been teleported (thread-safe)
    private final Set<UUID> teleportedPlayers = java.util.concurrent.ConcurrentHashMap.newKeySet();
    // Guard für gleichzeitige Match-Operationen
    private final Object matchOpMutex = new Object();
    
    public MatchManager(EventPlugin plugin) {
        this.plugin = plugin;
        this.spawnManager = new SpawnManager(plugin);
    }

    /**
     * Indexiert einen Spieler für schnelle O(1)-Zuordnung auf ein Match.
     * Nutzt eine thread-sichere Map, um gleichzeitige Zugriffe zu unterstützen.
     */
    public void indexPlayer(java.util.UUID playerId, java.util.UUID matchId) {
        playerToMatchId.put(playerId, matchId);
    }

    /**
     * Liefert die MatchId für einen Spieler in O(1) oder null, wenn nicht vorhanden.
     */
    public java.util.UUID getMatchIdByPlayer(java.util.UUID playerId) {
        return playerToMatchId.get(playerId);
    }
    
    public void startMatchSetup(Player player1, Player player2) {
        Match match = new Match(player1, player2);
        synchronized (matchOpMutex) {
            matches.put(match.getMatchId(), match);
            // Index participants for O(1) lookup
            playerToMatchId.put(player1.getUniqueId(), match.getMatchId());
            playerToMatchId.put(player2.getUniqueId(), match.getMatchId());
            // Store original locations
            match.getOriginalLocations().put(player1.getUniqueId(), player1.getLocation());
            match.getOriginalLocations().put(player2.getUniqueId(), player2.getLocation());
        }
    }
    
    public void handleWagerConfirmation(Player player1, Player player2) {
        Match match = getMatch(player1, player2);
        if (match == null) return;
        
        // Skip validation if no-wager mode
        if (!match.isNoWagerMode()) {
            // Verify wager is valid
            if (!validateWager(match, player1, player2)) {
                return;
            }
            
            // Deduct money from both players if applicable
            if (plugin.hasEconomy()) {
                double p1Money = match.getWagerMoney(player1);
                double p2Money = match.getWagerMoney(player2);
                
                if (p1Money > 0) {
                    if (!plugin.getEconomy().has(player1, p1Money)) {
                        MessageUtil.sendMessage(player1, "&cYou don't have enough money!");
                        endMatch(match, null, true);
                        return;
                    }
                    plugin.getEconomy().withdrawPlayer(player1, p1Money);
                }
                
                if (p2Money > 0) {
                    if (!plugin.getEconomy().has(player2, p2Money)) {
                        MessageUtil.sendMessage(player2, "&cYou don't have enough money!");
                        // Return p1's money
                        if (p1Money > 0) {
                            plugin.getEconomy().depositPlayer(player1, p1Money);
                        }
                        endMatch(match, null, true);
                        return;
                    }
                    plugin.getEconomy().withdrawPlayer(player2, p2Money);
                }
            }
        }
        
        // Start arena selection for command-based matches
        // Since GUI is removed, we need to handle arena selection differently
        // For now, we'll select the first available arena automatically
        if (!plugin.getArenaManager().getArenas().isEmpty()) {
            Arena firstArena = plugin.getArenaManager().getArenas().values().iterator().next();
            handleArenaSelection(player1, player2, firstArena);
        } else {
            MessageUtil.sendMessage(player1, "&cNo arenas available!");
            MessageUtil.sendMessage(player2, "&cNo arenas available!");
            endMatch(match, null, true);
        }
    }
    
    private boolean validateWager(Match match, Player player1, Player player2) {
        // Check minimum wager requirements
        int minItems = plugin.getPvpConfigManager().getConfig().getInt("settings.checks.minimum-bet-items", 1);
        double minMoney = plugin.getPvpConfigManager().getConfig().getDouble("settings.checks.minimum-bet-money", 0);
        
        int p1Items = match.getWagerItems(player1).size();
        int p2Items = match.getWagerItems(player2).size();
        double p1Money = match.getWagerMoney(player1);
        double p2Money = match.getWagerMoney(player2);
        
        if ((p1Items + p2Items < minItems) && (p1Money + p2Money < minMoney)) {
            MessageUtil.sendMessage(player1, "&cMinimum wager not met! At least " + minItems + " items or $" + minMoney + " required.");
            MessageUtil.sendMessage(player2, "&cMinimum wager not met! At least " + minItems + " items or $" + minMoney + " required.");
            return false;
        }
        
        // Check inventory space
        if (plugin.getPvpConfigManager().getConfig().getBoolean("settings.checks.inventory-space", true)) {
            if (!InventoryUtil.canFitItems(player1, match.getWagerItems(player2))) {
                MessageUtil.sendMessage(player1, "&cYou don't have enough inventory space to receive opponent's items!");
                MessageUtil.sendMessage(player2, "&cYour opponent doesn't have enough inventory space!");
                return false;
            }
            
            if (!InventoryUtil.canFitItems(player2, match.getWagerItems(player1))) {
                MessageUtil.sendMessage(player2, "&cYou don't have enough inventory space to receive opponent's items!");
                MessageUtil.sendMessage(player1, "&cYour opponent doesn't have enough inventory space!");
                return false;
            }
        }
        
        return true;
    }
    
    public void handleArenaSelection(Player player1, Player player2, Arena arena) {
        Match match = getMatch(player1, player2);
        if (match == null) return;
        
        match.setArena(arena);
        
        // Zeige Lade-Status an
        match.setWorldLoading(true);
        match.broadcast("");
        match.broadcast("&e&l━━━━━━━━━━━━━━━━━━━━━━━");
        match.broadcast("&6&lARENA-WELT WIRD GELADEN...");
        match.broadcast("&e&l━━━━━━━━━━━━━━━━━━━━━━━");
        match.broadcast("");
        match.broadcast("&7Arena: &e" + arena.getDisplayName());
        match.broadcast("&7Welt: &e" + arena.getArenaWorld());
        match.broadcast("");
        match.broadcast("&7Bitte warte einen Moment...");
        
        // Load arena world with callback
        plugin.getArenaManager().loadArenaWorld(arena.getArenaWorld(), () -> {
            // Welt-Ladung abgeschlossen
            match.setWorldLoading(false);
            
            match.broadcast("");
            match.broadcast("&a&l━━━━━━━━━━━━━━━━━━━━━━━");
            match.broadcast("&2&lWELT ERFOLGREICH GELADEN!");
            match.broadcast("&a&l━━━━━━━━━━━━━━━━━━━━━━━");
            match.broadcast("");
            
            // Select first equipment set automatically since GUI is removed
            if (!plugin.getEquipmentManager().getEquipmentSets().isEmpty()) {
                EquipmentSet firstEquipment = plugin.getEquipmentManager().getEquipmentSets().values().iterator().next();
                handleEquipmentSelection(player1, player2, firstEquipment, firstEquipment);
            } else {
                MessageUtil.sendMessage(player1, "&cNo equipment sets available!");
                MessageUtil.sendMessage(player2, "&cNo equipment sets available!");
                endMatch(match, null, true);
            }
        });
    }
    
    // Neue Methode: Match nach erfolgreicher Weltladung fortsetzen
    private void continueMatchStart(Match match, World arenaWorld) {
        Player player1 = match.getPlayer1();
        Player player2 = match.getPlayer2();
        Arena arena = match.getArena();
        
        plugin.getLogger().info("Starting DIRECT match in world: " + arenaWorld.getName());
        
        // Teleport players
        spawnManager.teleportPlayers(player1, player2, arena, arenaWorld);
        teleportedPlayers.add(player1.getUniqueId());
        teleportedPlayers.add(player2.getUniqueId());
        
        // Wait for teleport, then apply equipment
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            // Verify in correct world
            if (!player1.getWorld().equals(arenaWorld) || !player2.getWorld().equals(arenaWorld)) {
                plugin.getLogger().warning("Players not in arena world after teleport!");
            }
            
            // Clear inventories
            player1.getInventory().clear();
            player2.getInventory().clear();
            player1.getInventory().setArmorContents(null);
            player2.getInventory().setArmorContents(null);
            
            // Apply equipment
            applyEquipment(player1, match.getPlayer1Equipment());
            applyEquipment(player2, match.getPlayer2Equipment());
            
            // Reset health
            player1.setHealth(20.0);
            player1.setFoodLevel(20);
            player1.setSaturation(20.0f);
            player2.setHealth(20.0);
            player2.setFoodLevel(20);
            player2.setSaturation(20.0f);
            
            // Set gamemode to SURVIVAL immediately
            player1.setGameMode(GameMode.SURVIVAL);
            player2.setGameMode(GameMode.SURVIVAL);
            
            // START FIGHT IMMEDIATELY (no countdown)
            match.setState(MatchState.FIGHTING);
            
            match.broadcast("");
            match.broadcast("&a&l━━━━━━━━━━━━━━━━━━━━━━━");
            match.broadcast("&e&lFIGHT!");
            match.broadcast("&a&l━━━━━━━━━━━━━━━━━━━━━━━");
            match.broadcast("");
            
            // Play sound
            player1.playSound(player1.getLocation(), org.bukkit.Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 2.0f);
            player2.playSound(player2.getLocation(), org.bukkit.Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 2.0f);
            
            // Start timer
            startMatchTimer(match);
            
        }, de.zfzfg.core.util.Time.seconds(1)); // 1 second after teleport
    }
    
    public void handleEquipmentSelection(Player player1, Player player2, EquipmentSet p1Equipment, EquipmentSet p2Equipment) {
        Match match = getMatch(player1, player2);
        if (match == null) return;
        
        match.setPlayer1Equipment(p1Equipment);
        match.setPlayer2Equipment(p2Equipment);
        
        // Start the match
        startMatch(match);
    }
    
    private void startMatch(Match match) {
        Player player1 = match.getPlayer1();
        Player player2 = match.getPlayer2();
        Arena arena = match.getArena();
        
        synchronized (matchOpMutex) {
            match.setState(MatchState.STARTING);
            match.setStartTime(System.currentTimeMillis());
        }
        
        // Inform players that arena is loading
        match.broadcast("&eArena wird geladen... &7(5 Sekunden)");
        
        // Ensure arena world is loaded before proceeding
        plugin.getArenaManager().loadArenaWorld(arena.getArenaWorld(), () -> {
            World arenaWorld = Bukkit.getWorld(arena.getArenaWorld());
            if (arenaWorld == null) {
                plugin.getLogger().severe("Arena world not found after loading: " + arena.getArenaWorld());
                MessageUtil.sendMessage(player1, "&cFehler: Arena-Welt konnte nicht geladen werden!");
                MessageUtil.sendMessage(player2, "&cFehler: Arena-Welt konnte nicht geladen werden!");
                endMatch(match, null, true);
                return;
            }

            // 5s PRE-TELEPORT countdown with invite, then perform teleport and continue
            startPreTeleportCountdown(match, 5, () -> {
                plugin.getLogger().info("Starting match in world: " + arenaWorld.getName());

                // Teleport players using SpawnManager
                plugin.getLogger().info("Teleporting players with spawn-type: " + arena.getSpawnType());
                spawnManager.teleportPlayers(player1, player2, arena, arenaWorld);

                // Mark players as teleported
                teleportedPlayers.add(player1.getUniqueId());
                teleportedPlayers.add(player2.getUniqueId());

                // Warte 2 Sekunden nach Teleport für sichere Welt-Ladung
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    afterTeleportVerifyOrRecover(match, player1, player2, arena, arenaWorld);
                }, de.zfzfg.core.util.Time.seconds(2)); // 2 Sekunden warten nach Teleport für sichere Welt-Ladung
            });
        });
    }

    private void afterTeleportVerifyOrRecover(Match match, Player player1, Player player2, Arena arena, World arenaWorld) {
        // Verify players are in correct world
        if (!player1.getWorld().equals(arenaWorld) || !player2.getWorld().equals(arenaWorld)) {
            plugin.getLogger().warning("Players not in arena world after teleport!");
            plugin.getLogger().warning("P1 World: " + player1.getWorld().getName());
            plugin.getLogger().warning("P2 World: " + player2.getWorld().getName());
            plugin.getLogger().warning("Arena World: " + arenaWorld.getName());

            // Versuche Notfall-Teleport und fahre mit Setup fort
            if (!attemptEmergencyTeleport(match, player1, player2, arena, arenaWorld)) {
                return;
            }
            return;
        }

        continueMatchSetup(match, player1, player2, arenaWorld);
    }

    /**
     * Notfall-Teleport, falls Spieler nach dem ersten Teleport nicht in der Zielwelt sind.
     * Führt einen zweiten Teleport durch und setzt das Match fort, wenn erfolgreich.
     * Gibt false zurück, wenn das Match beendet werden musste.
     */
    private boolean attemptEmergencyTeleport(Match match, Player player1, Player player2, Arena arena, World arenaWorld) {
        plugin.getLogger().info("Attempting emergency teleport...");
        try {
            Location spawn1 = (arena.getSpawnConfig() != null && arena.getSpawnConfig().getFixedSpawns() != null && !arena.getSpawnConfig().getFixedSpawns().isEmpty())
                ? arena.getSpawnConfig().getFixedSpawns().get(0).clone()
                : arenaWorld.getSpawnLocation();
            Location spawn2 = (arena.getSpawnConfig() != null && arena.getSpawnConfig().getFixedSpawns() != null && arena.getSpawnConfig().getFixedSpawns().size() > 1)
                ? arena.getSpawnConfig().getFixedSpawns().get(1).clone()
                : arenaWorld.getSpawnLocation();

            spawn1.setWorld(arenaWorld);
            spawn2.setWorld(arenaWorld);

            player1.teleport(spawn1);
            player2.teleport(spawn2);

            // Nochmal warten und prüfen
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!player1.getWorld().equals(arenaWorld) || !player2.getWorld().equals(arenaWorld)) {
                    plugin.getLogger().severe("Emergency teleport failed! Ending match.");
                    MessageUtil.sendMessage(player1, "&cFehler: Teleport in Arena fehlgeschlagen!");
                    MessageUtil.sendMessage(player2, "&cFehler: Teleport in Arena fehlgeschlagen!");
                    endMatch(match, null, true);
                    return;
                }
                continueMatchSetup(match, player1, player2, arenaWorld);
            }, de.zfzfg.core.util.Time.seconds(1));

            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("Emergency teleport failed with exception: " + e.getMessage());
            MessageUtil.sendMessage(player1, "&cFehler: Arena-Setup fehlgeschlagen!");
            MessageUtil.sendMessage(player2, "&cFehler: Arena-Setup fehlgeschlagen!");
            endMatch(match, null, true);
            return false;
        }
    }
    
    private void continueMatchSetup(Match match, Player player1, Player player2, World arenaWorld) {
        // Clear inventories AFTER teleport
        player1.getInventory().clear();
        player2.getInventory().clear();
        player1.getInventory().setArmorContents(null);
        player2.getInventory().setArmorContents(null);
        
        // Apply equipment AFTER teleport
        applyEquipment(player1, match.getPlayer1Equipment());
        applyEquipment(player2, match.getPlayer2Equipment());
        
        // Reset health and hunger
        player1.setHealth(20.0);
        player1.setFoodLevel(20);
        player1.setSaturation(20.0f);
        player2.setHealth(20.0);
        player2.setFoodLevel(20);
        player2.setSaturation(20.0f);
        
        // Set gamemode
        player1.setGameMode(GameMode.SURVIVAL);
        player2.setGameMode(GameMode.SURVIVAL);
        
        // Safety: clear lingering invisibility potion effects from previous plugins
        player1.removePotionEffect(PotionEffectType.INVISIBILITY);
        player2.removePotionEffect(PotionEffectType.INVISIBILITY);
        
        // Start countdown
        startCountdown(match);
    }
    
    private void applyEquipment(Player player, EquipmentSet equipment) {
        if (equipment == null) return;
        
        plugin.getLogger().info("Applying equipment to " + player.getName() + " in world: " + player.getWorld().getName());
        
        // Apply armor
        if (equipment.getHelmet() != null) {
            player.getInventory().setHelmet(equipment.getHelmet().clone());
        }
        if (equipment.getChestplate() != null) {
            player.getInventory().setChestplate(equipment.getChestplate().clone());
        }
        if (equipment.getLeggings() != null) {
            player.getInventory().setLeggings(equipment.getLeggings().clone());
        }
        if (equipment.getBoots() != null) {
            player.getInventory().setBoots(equipment.getBoots().clone());
        }
        
        // Apply inventory items
        if (equipment.getInventory() != null) {
            for (Map.Entry<Integer, ItemStack> entry : equipment.getInventory().entrySet()) {
                player.getInventory().setItem(entry.getKey(), entry.getValue().clone());
            }
        }
        
        plugin.getLogger().info("Equipment applied to " + player.getName());
    }
    
    private void startCountdown(Match match) {
        Player player1 = match.getPlayer1();
        Player player2 = match.getPlayer2();
        
        int countdownTime = plugin.getPvpConfigManager().getConfig().getInt("settings.match.countdown-time", 10);

        // Send global spectate invite once when countdown starts
        sendGlobalSpectateInvite(match);
        
        for (int i = countdownTime; i > 0; i--) {
            final int seconds = i;
            BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (match.getState() != MatchState.STARTING) return;
                
                String message = "&eMatch starts in &6&l" + seconds + " &eseconds!";
                match.broadcast(message);
                
                // Play sound
                player1.playSound(player1.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
                player2.playSound(player2.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
                
            }, (countdownTime - i) * 20L);
            countdownTasks.put(match.getMatchId(), task);
        }
        
        // Start the match after countdown
        BukkitTask startTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (match.getState() == MatchState.STARTING) {
                startFight(match);
            }
        }, (countdownTime + 1) * 20L);
        countdownTasks.put(match.getMatchId(), startTask);
    }

    // Countdown vor dem Teleport mit globaler Spectate-Einladung
    private void startPreTeleportCountdown(Match match, int seconds, Runnable onFinish) {
        // Einladung an alle Nicht-Teilnehmer anzeigen
        sendGlobalSpectateInvite(match);

        Player player1 = match.getPlayer1();
        Player player2 = match.getPlayer2();
        final UUID matchId = match.getMatchId();

        BukkitTask task = new BukkitRunnable() {
            int remaining = seconds;

            @Override
            public void run() {
                if (match.getState() != MatchState.STARTING) {
                    cancel();
                    preTeleportCountdownTasks.remove(matchId);
                    return;
                }

                if (remaining <= 0) {
                    cancel();
                    preTeleportCountdownTasks.remove(matchId);
                    try {
                        onFinish.run();
                    } catch (Exception ignored) {}
                    return;
                }

                match.broadcast("&eTeleport zur Arena in &6&l" + remaining + " &eSekunden...");
                player1.playSound(player1.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
                player2.playSound(player2.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
                remaining--;
            }
        }.runTaskTimer(plugin, 0L, de.zfzfg.core.util.Time.TICKS_PER_SECOND);

        preTeleportCountdownTasks.put(matchId, task);
    }

    private void sendGlobalSpectateInvite(Match match) {
        try {
            Player p1 = match.getPlayer1();
            Player p2 = match.getPlayer2();

            TextComponent header = new TextComponent(MessageUtil.color(
                "\n§6§l━━━━━━━━━━━━━━━━━━━━━━━\n" +
                "§e§lMATCH: §f" + p1.getName() + " §7vs §f" + p2.getName() + "\n" +
                "§6§l━━━━━━━━━━━━━━━━━━━━━━━\n\n"
            ));

            TextComponent spectateBtn1 = new TextComponent(MessageUtil.color("§b§l[► ZUSCHAUEN ◄]"));
            spectateBtn1.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/pvp spectate " + p1.getName()));
            spectateBtn1.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder(MessageUtil.color("§aKlicke, um das Match zu schauen")).create()));

            TextComponent footer = new TextComponent(MessageUtil.color(
                "\n\n§7Verwende auch §e/pvp spectate <spieler>§7.\n" +
                "§6§l━━━━━━━━━━━━━━━━━━━━━━━\n"
            ));

            header.addExtra(spectateBtn1);
            header.addExtra(footer);

            for (Player online : Bukkit.getOnlinePlayers()) {
                // Don't send to match participants; they already see match messages
                if (online.equals(p1) || online.equals(p2)) continue;
                online.spigot().sendMessage(header);
            }
        } catch (Exception e) {
            // Fallback simple broadcast
            for (Player online : Bukkit.getOnlinePlayers()) {
                Player p1 = match.getPlayer1();
                Player p2 = match.getPlayer2();
                if (online.equals(p1) || online.equals(p2)) continue;
                MessageUtil.sendMessage(online, "&eNeues Match: &f" + p1.getName() + " &7vs &f" + p2.getName() + " &8- &7Nutze &e/pvp spectate " + p1.getName());
            }
        }
    }
    
    private void startFight(Match match) {
        match.setState(MatchState.FIGHTING);
        
        Player player1 = match.getPlayer1();
        Player player2 = match.getPlayer2();
        
        // Broadcast
        match.broadcast("");
        match.broadcast("&a&l━━━━━━━━━━━━━━━━━━━━━━━");
        match.broadcast("&e&lFIGHT!");
        match.broadcast("&a&l━━━━━━━━━━━━━━━━━━━━━━━");
        match.broadcast("");
        
        if (match.isNoWagerMode()) {
            match.broadcast("&7Mode: &eNo Wager - Just for Fun!");
            match.broadcast("");
        }
        
        // Play sound
        player1.playSound(player1.getLocation(), org.bukkit.Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 2.0f);
        player2.playSound(player2.getLocation(), org.bukkit.Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 2.0f);
        
        // Start match timer
        startMatchTimer(match);
    }
    
    private void startMatchTimer(Match match) {
        int maxDuration = plugin.getPvpConfigManager().getConfig().getInt("settings.match.max-duration", 600); // 10 minutes
        
        BukkitTask timerTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (match.getState() != MatchState.FIGHTING) return;
            
            long elapsed = (System.currentTimeMillis() - match.getStartTime()) / 1000;
            long remaining = maxDuration - elapsed;
            
            if (remaining <= 0) {
                // Match timeout - draw
                match.broadcast("");
                match.broadcast("&c&l━━━━━━━━━━━━━━━━━━━━━━━");
                match.broadcast("&c&lTIME'S UP!");
                match.broadcast("&c&l━━━━━━━━━━━━━━━━━━━━━━━");
                match.broadcast("");
                match.broadcast("&7Match ended in a draw due to timeout.");
                match.broadcast("");
                
                endMatch(match, null, true);
            } else if (remaining == 60 || remaining == 30 || remaining == 10) {
                match.broadcast("&eMatch ends in &c" + remaining + " &eseconds!");
            }
        }, 0L, de.zfzfg.core.util.Time.TICKS_PER_SECOND);
        
        synchronized (matchOpMutex) {
            matchTimerTasks.put(match.getMatchId(), timerTask);
        }
    }
    
    public void endMatch(Match match, Player winner, boolean isDraw) {
        // Cancel tasks (unter Lock)
        BukkitTask countdownTask;
        BukkitTask preTeleportTask;
        BukkitTask timerTask;
        synchronized (matchOpMutex) {
            countdownTask = countdownTasks.remove(match.getMatchId());
            preTeleportTask = preTeleportCountdownTasks.remove(match.getMatchId());
            timerTask = matchTimerTasks.remove(match.getMatchId());
        }
        if (countdownTask != null) countdownTask.cancel();
        if (preTeleportTask != null) preTeleportTask.cancel();
        if (timerTask != null) timerTask.cancel();
        
        match.setState(MatchState.ENDED);
        
        Player player1 = match.getPlayer1();
        Player player2 = match.getPlayer2();
        
        // Handle winnings/returns based on mode
        if (match.isNoWagerMode()) {
            // No wager mode - nothing to distribute
            MessageUtil.sendMessage(player1, "&7No wager match - no items to distribute.");
            MessageUtil.sendMessage(player2, "&7No wager match - no items to distribute.");
        } else if (isDraw) {
            distributeItemsBack(match);
        } else if (winner != null) {
            distributeWinnings(match, winner);
        } else {
            // Should not happen
            distributeItemsBack(match);
        }
        
        // Welt-Reset je nach Konfiguration
        if (match.getArena() != null) {
            String worldName = match.getArena().getArenaWorld();
            String cloneSource = match.getArena().getCloneSourceWorld();
            if (cloneSource != null && !cloneSource.isEmpty()) {
                plugin.getLogger().info("Scheduling clone reset for arena world: " + worldName + " from " + cloneSource);
                // Nach Rück-Teleport der Spieler ausführen
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    plugin.getArenaManager().resetArenaWorldByClone(cloneSource, worldName);
                }, de.zfzfg.core.util.Time.seconds(7)); // 7 Sekunden nach Match-Ende
            } else if (match.getArena().isRegenerateWorld()) {
                plugin.getLogger().info("Scheduling Multiverse regeneration for arena world: " + worldName);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    plugin.getArenaManager().regenerateArenaWorld(worldName);
                }, de.zfzfg.core.util.Time.seconds(7));
            }
        }
        
        // Teleport players back after delay
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            // Only teleport if they were actually teleported to arena
            if (teleportedPlayers.contains(player1.getUniqueId())) {
                teleportPlayerBack(player1, match);
                teleportedPlayers.remove(player1.getUniqueId());
            }
            
            if (teleportedPlayers.contains(player2.getUniqueId())) {
                teleportPlayerBack(player2, match);
                teleportedPlayers.remove(player2.getUniqueId());
            }
            
            // Handle spectators
            for (UUID spectatorId : new ArrayList<>(match.getSpectators())) {
                Player spectator = Bukkit.getPlayer(spectatorId);
                if (spectator != null && spectator.isOnline()) {
                    if (teleportedPlayers.contains(spectatorId)) {
                        teleportPlayerBack(spectator, match);
                        teleportedPlayers.remove(spectatorId);
                    }
                }
            }
            
            // Unload world if neither regenerating nor cloning reset
            if (match.getArena() != null && match.getArena().getCloneSourceWorld() == null && !match.getArena().isRegenerateWorld()) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    plugin.getArenaManager().unloadArenaWorld(match.getArena().getArenaWorld());
                }, de.zfzfg.core.util.Time.seconds(2));
            }
            
            // Cleanup: remove indexes und Match (unter Lock) + Teleport-Marker
            synchronized (matchOpMutex) {
                UUID matchId = match.getMatchId();
                playerToMatchId.remove(match.getPlayer1().getUniqueId());
                playerToMatchId.remove(match.getPlayer2().getUniqueId());
                for (UUID spectatorId : new ArrayList<>(match.getSpectators())) {
                    playerToMatchId.remove(spectatorId);
                    teleportedPlayers.remove(spectatorId);
                }
                teleportedPlayers.remove(match.getPlayer1().getUniqueId());
                teleportedPlayers.remove(match.getPlayer2().getUniqueId());
                matches.remove(matchId);
            }
            
        }, de.zfzfg.core.util.Time.seconds(4)); // 4 seconds delay
    }
    
    private void distributeWinnings(Match match, Player winner) {
        Player loser = match.getOpponent(winner);
        
        // WICHTIG: Erst zur ursprünglichen Location teleportieren
        Location winnerOriginal = match.getOriginalLocations().get(winner.getUniqueId());
        if (winnerOriginal != null) {
            winner.teleport(winnerOriginal);
            plugin.getLogger().info("Teleported winner " + winner.getName() + " back to original location");
        }
        
        // Give items to winner NACH Teleport
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            List<ItemStack> allItems = new ArrayList<>();
            allItems.addAll(match.getWagerItems(match.getPlayer1()));
            allItems.addAll(match.getWagerItems(match.getPlayer2()));
            
            InventoryUtil.giveItems(winner, allItems);
            
            MessageUtil.sendMessage(winner, "");
            MessageUtil.sendMessage(winner, "&a&l━━━━━━━━━━━━━━━━━━━━━━━");
            MessageUtil.sendMessage(winner, "&a&lYOU WON!");
            MessageUtil.sendMessage(winner, "&a&l━━━━━━━━━━━━━━━━━━━━━━━");
            MessageUtil.sendMessage(winner, "");
            MessageUtil.sendMessage(winner, "&7You received all wager items!");
            
            // Give money to winner
            if (plugin.hasEconomy()) {
                double totalMoney = match.getWagerMoney(match.getPlayer1()) + match.getWagerMoney(match.getPlayer2());
                if (totalMoney > 0) {
                    plugin.getEconomy().depositPlayer(winner, totalMoney);
                    MessageUtil.sendMessage(winner, "&7You won &6$" + String.format("%.2f", totalMoney) + "&7!");
                }
            }
            MessageUtil.sendMessage(winner, "");
            
        }, de.zfzfg.core.util.Time.ticks(10)); // 0.5 Sekunden nach Teleport
        
        // Notify loser
        MessageUtil.sendMessage(loser, "");
        MessageUtil.sendMessage(loser, "&c&l━━━━━━━━━━━━━━━━━━━━━━━");
        MessageUtil.sendMessage(loser, "&c&lYOU LOST!");
        MessageUtil.sendMessage(loser, "&c&l━━━━━━━━━━━━━━━━━━━━━━━");
        MessageUtil.sendMessage(loser, "");
        MessageUtil.sendMessage(loser, "&7Better luck next time!");
        MessageUtil.sendMessage(loser, "");
    }
    
    private void distributeItemsBack(Match match) {
        // Return items to original owners
        Player player1 = match.getPlayer1();
        Player player2 = match.getPlayer2();
        
        // Teleport back first
        Location p1Original = match.getOriginalLocations().get(player1.getUniqueId());
        Location p2Original = match.getOriginalLocations().get(player2.getUniqueId());
        
        if (p1Original != null) player1.teleport(p1Original);
        if (p2Original != null) player2.teleport(p2Original);
        
        // Give items AFTER teleport
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            InventoryUtil.giveItems(player1, match.getWagerItems(player1));
            InventoryUtil.giveItems(player2, match.getWagerItems(player2));
            
            // Return money
            if (plugin.hasEconomy()) {
                double p1Money = match.getWagerMoney(player1);
                double p2Money = match.getWagerMoney(player2);
                
                if (p1Money > 0) {
                    plugin.getEconomy().depositPlayer(player1, p1Money);
                }
                if (p2Money > 0) {
                    plugin.getEconomy().depositPlayer(player2, p2Money);
                }
            }
            
            MessageUtil.sendMessage(player1, "&7Your wager has been returned.");
            MessageUtil.sendMessage(player2, "&7Your wager has been returned.");
            
        }, 10L); // 0.5 Sekunden nach Teleport
    }
    
    private void teleportPlayerBack(Player player, Match match) {
        Location originalLocation = match.getOriginalLocations().get(player.getUniqueId());
        
        if (originalLocation != null) {
            plugin.getLogger().info("Teleporting " + player.getName() + " back to: " + 
                originalLocation.getWorld().getName() + " at " + 
                originalLocation.getBlockX() + "," + originalLocation.getBlockY() + "," + originalLocation.getBlockZ());
            
            player.teleport(originalLocation);
        } else {
            plugin.getLogger().warning("No original location stored for " + player.getName());
        }
        
        // Reset player state
        if (match.getSpectators().contains(player.getUniqueId())) {
            player.setGameMode(GameMode.SURVIVAL);
        } else {
            player.setGameMode(GameMode.SURVIVAL);
        }
        // Ensure any lingering invisibility is cleared
        player.removePotionEffect(PotionEffectType.INVISIBILITY);
    }
    
    public Match getMatch(Player player1, Player player2) {
        for (Match match : matches.values()) {
            if ((match.getPlayer1().equals(player1) && match.getPlayer2().equals(player2)) ||
                (match.getPlayer1().equals(player2) && match.getPlayer2().equals(player1))) {
                return match;
            }
        }
        return null;
    }
    
    public Match getMatchByPlayer(Player player) {
        UUID matchId = playerToMatchId.get(player.getUniqueId());
        return matchId != null ? matches.get(matchId) : null;
    }
    
    public Map<UUID, Match> getMatches() {
        return new HashMap<>(matches);
    }
    
    public boolean isPlayerInMatch(Player player) {
        return playerToMatchId.containsKey(player.getUniqueId());
    }
    
    public int getActiveMatchCount() {
        return matches.size();
    }
    
    public int stopAllMatches() {
        int count = matches.size();
        for (Match match : new ArrayList<>(getMatches().values())) {
            match.broadcast("&cServer is shutting down! Match cancelled.");
            endMatch(match, null, true);
        }
        // Nach dem Abbruch aller Matches: Flüchtige Zustände säubern
        clearTransientState();
        return count;
    }

    // Tasks sauber abbrechen (Reload/Disable)
    public void cancelAllTasks() {
        synchronized (matchOpMutex) {
            for (BukkitTask t : countdownTasks.values()) { try { t.cancel(); } catch (Exception ignored) {} }
            for (BukkitTask t : preTeleportCountdownTasks.values()) { try { t.cancel(); } catch (Exception ignored) {} }
            for (BukkitTask t : matchTimerTasks.values()) { try { t.cancel(); } catch (Exception ignored) {} }
            countdownTasks.clear();
            preTeleportCountdownTasks.clear();
            matchTimerTasks.clear();
        }
    }

    // Flüchtige Zustände zurücksetzen (teleportedPlayers / playerToMatchId)
    public void clearTransientState() {
        synchronized (matchOpMutex) {
            teleportedPlayers.clear();
            playerToMatchId.clear();
        }
    }

    // Command-based match start (OHNE Countdown, DIREKT starten)
    public void startMatchFromCommand(CommandRequest request) {
        Player player1 = request.getSender();
        Player player2 = request.getTarget();
        
        // Create match
        Match match = new Match(player1, player2);
        matches.put(match.getMatchId(), match);
        // Index participants for O(1) lookup
        playerToMatchId.put(player1.getUniqueId(), match.getMatchId());
        playerToMatchId.put(player2.getUniqueId(), match.getMatchId());
        
        // Set no-wager mode if both wagers are empty
        boolean hasWager = (request.getMoney() > 0 || !request.getWagerItems().isEmpty()) ||
                          (request.getTargetWagerMoney() > 0 || !request.getTargetWagerItems().isEmpty());
        
        if (!hasWager) {
            match.setNoWagerMode(true);
        } else {
            // Set wagers
            match.getWagerItems().put(player1.getUniqueId(), new ArrayList<>(request.getWagerItems()));
            match.getWagerItems().put(player2.getUniqueId(), new ArrayList<>(request.getTargetWagerItems()));
            match.getWagerMoney().put(player1.getUniqueId(), request.getMoney());
            match.getWagerMoney().put(player2.getUniqueId(), request.getTargetWagerMoney());
            
            // Remove items from inventories
            for (ItemStack item : request.getWagerItems()) {
                player1.getInventory().removeItem(item);
            }
            for (ItemStack item : request.getTargetWagerItems()) {
                player2.getInventory().removeItem(item);
            }
            
            // Deduct money if applicable
            if (plugin.hasEconomy()) {
                if (request.getMoney() > 0) {
                    plugin.getEconomy().withdrawPlayer(player1, request.getMoney());
                }
                if (request.getTargetWagerMoney() > 0) {
                    plugin.getEconomy().withdrawPlayer(player2, request.getTargetWagerMoney());
                }
            }
        }
        
        // Set arena and equipment
        Arena arena = plugin.getArenaManager().getArena(request.getFinalArenaId());
        EquipmentSet p1Equipment = plugin.getEquipmentManager().getEquipmentSet(request.getFinalEquipmentId());
        EquipmentSet p2Equipment = plugin.getEquipmentManager().getEquipmentSet(request.getFinalEquipmentId());
        
        match.setArena(arena);
        match.setPlayer1Equipment(p1Equipment);
        match.setPlayer2Equipment(p2Equipment);
        
        // Store original locations
        match.getOriginalLocations().put(player1.getUniqueId(), player1.getLocation());
        match.getOriginalLocations().put(player2.getUniqueId(), player2.getLocation());
        
        // Confirm both
        match.confirmArena(player1);
        match.confirmArena(player2);
        match.confirmEquipment(player1);
        match.confirmEquipment(player2);
        
        // Announce
        match.broadcast("");
        match.broadcast("&a&l━━━━━━━━━━━━━━━━━━━━━━━");
        match.broadcast("&a&lMATCH STARTING!");
        match.broadcast("&a&l━━━━━━━━━━━━━━━━━━━━━━━");
        match.broadcast("");
        match.broadcast("&e" + player1.getName() + " &7vs &e" + player2.getName());
        match.broadcast("&7Arena: &e" + arena.getDisplayName());
        match.broadcast("&7Equipment: &e" + p1Equipment.getDisplayName());
        match.broadcast("");
        
        // Start match WITHOUT GUI - DIRECT START
        startMatchDirectly(match);
    }
    
    // Neue Methode: Match direkt starten OHNE Countdown
    private void startMatchDirectly(Match match) {
        Player player1 = match.getPlayer1();
        Player player2 = match.getPlayer2();
        Arena arena = match.getArena();
        
        synchronized (matchOpMutex) {
            match.setState(MatchState.STARTING);
            match.setStartTime(System.currentTimeMillis());
        }
        
        // Load arena world first using /mvload command
        String worldName = arena.getArenaWorld();
        plugin.getLogger().info("Loading arena world via /mvload: " + worldName);
        
        // Use MultiverseHelper to load world with /mvload command
        plugin.getArenaManager().loadArenaWorld(worldName, () -> {
            // World loading completed, proceed with match start
            World arenaWorld = Bukkit.getWorld(worldName);
            if (arenaWorld == null) {
                plugin.getLogger().severe("Arena world failed to load: " + worldName);
                MessageUtil.sendMessage(player1, "&cError: Arena world could not be loaded!");
                MessageUtil.sendMessage(player2, "&cError: Arena world could not be loaded!");
                endMatch(match, null, true);
                return;
            }
            
            plugin.getLogger().info("Arena world loaded successfully: " + worldName);
            // 5s Countdown VOR dem Teleport auch für den Direktstart
            startPreTeleportCountdown(match, 5, () -> continueMatchStart(match, arenaWorld));
        });
    }

    // O(1) Lookup: Spectator management
    public void addSpectator(Match match, Player spectator) {
        if (match == null || spectator == null) return;
        UUID sid = spectator.getUniqueId();
        if (match.getSpectators().contains(sid)) return;
        match.getSpectators().add(sid);
        match.getOriginalLocations().put(sid, spectator.getLocation());
        playerToMatchId.put(sid, match.getMatchId());
    }

    public void removeSpectator(Match match, Player spectator) {
        if (match == null || spectator == null) return;
        UUID sid = spectator.getUniqueId();
        match.getSpectators().remove(sid);
        match.getOriginalLocations().remove(sid);
        playerToMatchId.remove(sid);
    }

    // Track teleported players (used for end-of-match teleport back)
    public void markTeleported(Player player) {
        if (player != null) {
            teleportedPlayers.add(player.getUniqueId());
        }
    }
}