package de.zfzfg.eventplugin.session;

import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.eventplugin.manager.TeamManager;
import de.zfzfg.eventplugin.model.EventConfig;
import de.zfzfg.eventplugin.model.EquipmentGroup;
import de.zfzfg.eventplugin.util.ColorUtil;
import org.bukkit.*;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import de.zfzfg.eventplugin.util.MultiverseHelper;

import java.util.*;

public class EventSession {
    
    private final EventPlugin plugin;
    private final EventConfig config;
    
    private final Set<UUID> participants;
    private final Set<UUID> validParticipants;
    private final Set<UUID> excludedPlayers;
    private final Map<UUID, Location> savedLocations;
    private final Set<UUID> eliminatedPlayers;
    private final Set<UUID> spectators;
    private final Set<UUID> leftSpectators;
    
    private TeamManager teamManager;
    
    private volatile EventState state;
    private final Object stateMutex = new Object();
    private int countdown;
    private int joinPhaseCountdown;
    private UUID winner;
    private TeamManager.Team winningTeam;
    // Prevent duplicated scheduling of draw evaluation
    private boolean drawPending = false;
    
    private BukkitTask countdownTask;
    private BukkitTask joinPhaseTask;
    
    public EventSession(EventPlugin plugin, EventConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.participants = new HashSet<>();
        this.validParticipants = new HashSet<>();
        this.excludedPlayers = new HashSet<>();
        this.savedLocations = new HashMap<>();
        this.eliminatedPlayers = new HashSet<>();
        this.spectators = new HashSet<>();
        this.leftSpectators = new HashSet<>();
        this.state = EventState.WAITING;
        
        if (config.getGameMode() == EventConfig.GameMode.TEAM_2 || 
            config.getGameMode() == EventConfig.GameMode.TEAM_3) {
            this.teamManager = new TeamManager();
        }
    }
    
    public boolean addPlayer(Player player) {
        if (state != EventState.WAITING && state != EventState.JOIN_PHASE) {
            return false;
        }
        
        if (participants.size() >= config.getMaxPlayers()) {
            return false;
        }
        
        participants.add(player.getUniqueId());
        // O(1) Index im EventManager
        plugin.getEventManager().indexPlayer(config.getId(), player.getUniqueId());
        
        if (plugin.getConfigManager().shouldSavePlayerLocation()) {
            savedLocations.put(player.getUniqueId(), player.getLocation());
            // Also store globally so it survives session removal
            plugin.getEventManager().savePlayerLocation(player.getUniqueId(), player.getLocation());
        }
        
        String joinMsg = config.getMessage("join");
        if (joinMsg.isEmpty()) {
            joinMsg = plugin.getConfigManager().getMessage("join.success");
        }
        broadcast(joinMsg.replace("{player}", player.getName()));
        
        if (state == EventState.JOIN_PHASE) {
            teleportToLobby(player);
            player.sendMessage(ColorUtil.color(plugin.getConfigManager().getMessage("start.auto-teleport")));
            // Give lobby equipment immediately on join if configured
            if (config.shouldGiveEquipmentInLobby()) {
                giveLobbyEquipmentToPlayer(player);
            }
        }
        
        return true;
    }
    
    public boolean removePlayer(Player player) {
        if (!participants.contains(player.getUniqueId())) {
            return false;
        }
        
        participants.remove(player.getUniqueId());
        // Index entfernen
        plugin.getEventManager().unindexPlayer(player.getUniqueId());
        validParticipants.remove(player.getUniqueId());
        excludedPlayers.remove(player.getUniqueId());
        eliminatedPlayers.remove(player.getUniqueId());
        
        if (teamManager != null) {
            teamManager.removePlayer(player.getUniqueId());
        }
        
        if (spectators.contains(player.getUniqueId())) {
            spectators.remove(player.getUniqueId());
            leftSpectators.add(player.getUniqueId());
            
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "v " + player.getName());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "fly " + player.getName());
        }
        
        teleportBack(player);
        
        String leaveMsg = config.getMessage("leave");
        if (leaveMsg.isEmpty()) {
            leaveMsg = plugin.getConfigManager().getMessage("leave.success");
        }
        broadcast(leaveMsg.replace("{player}", player.getName()));
        
        if (state == EventState.COUNTDOWN && validParticipants.size() < config.getMinPlayers()) {
            cancelEvent();
        }
        
        return true;
    }
    
    public void startJoinPhase() {
        synchronized (stateMutex) { state = EventState.JOIN_PHASE; }
        joinPhaseCountdown = plugin.getConfigManager().getJoinPhaseDuration();

        // Pre-Event-Check und Weltvorbereitung mit /mv
        plugin.getWorldStateManager().ensureEventWorldReady(config);
        // Lade Welten VOR der Join-Phase
        loadWorlds();
        prepareWorlds();
        sendJoinPhaseAnnouncement();
        
        joinPhaseTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (joinPhaseCountdown <= 0) {
                    cancel();
                    closeJoinPhase();
                    return;
                }
                
                if (joinPhaseCountdown == 15 || joinPhaseCountdown == 10 || joinPhaseCountdown <= 5) {
                    String msg = plugin.getConfigManager().getMessage("start.join-phase-ending")
                        .replace("{time}", String.valueOf(joinPhaseCountdown));
                    Bukkit.broadcastMessage(ColorUtil.color(plugin.getConfigManager().getPrefix() + " " + msg));
                }
                
                joinPhaseCountdown--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }
    
    private void sendJoinPhaseAnnouncement() {
        String message = plugin.getConfigManager().getMessage("start.join-phase-started")
            .replace("{event}", config.getDisplayName())
            .replace("{description}", config.getDescription())
            .replace("{min}", String.valueOf(config.getMinPlayers()))
            .replace("{max}", String.valueOf(config.getMaxPlayers()))
            .replace("{time}", String.valueOf(plugin.getConfigManager().getJoinPhaseDuration()));
        
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            onlinePlayer.sendMessage("");
            onlinePlayer.sendMessage(ColorUtil.color(message));
            
            net.md_5.bungee.api.chat.TextComponent component = new net.md_5.bungee.api.chat.TextComponent(
                ColorUtil.color("&a&l[TEILNEHMEN]")
            );
            component.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(
                net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND,
                "/event join " + config.getCommand()
            ));
            component.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(
                net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
                new net.md_5.bungee.api.chat.ComponentBuilder("Klicke um teilzunehmen!").create()
            ));
            
            onlinePlayer.spigot().sendMessage(component);
            onlinePlayer.sendMessage("");
        }
    }
    
    private void closeJoinPhase() {
        if (joinPhaseTask != null) {
            joinPhaseTask.cancel();
        }
        
        Bukkit.broadcastMessage(ColorUtil.color(
            plugin.getConfigManager().getPrefix() + " " +
            plugin.getConfigManager().getMessage("start.join-phase-closed")
        ));
        
        // NEU: PrÃƒÂ¼fe ob genug Spieler angemeldet sind
        if (participants.size() < config.getMinPlayers()) {
            Bukkit.broadcastMessage(ColorUtil.color(
                plugin.getConfigManager().getPrefix() + " " +
                plugin.getConfigManager().getMessage("start.not-enough-players")
                    .replace("{min}", String.valueOf(config.getMinPlayers()))
            ));
            
            // Teleportiere alle Spieler zurÃƒÂ¼ck
            for (UUID playerId : new HashSet<>(participants)) {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null) {
                    teleportBack(player);
                    player.sendMessage(ColorUtil.color(plugin.getConfigManager().getPrefix() + 
                        " &cDas Event wurde abgebrochen - nicht genug Spieler!"));
                }
            }
            
            cancelEvent();
            return;
        }
        
        // Team-Handling: Sortiere ÃƒÂ¼berzÃƒÂ¤hlige Spieler aus
        if (config.getGameMode() == EventConfig.GameMode.TEAM_2 || 
            config.getGameMode() == EventConfig.GameMode.TEAM_3) {
            
            int teamCount = config.getGameMode() == EventConfig.GameMode.TEAM_2 ? 2 : 3;
            int validPlayerCount = (participants.size() / teamCount) * teamCount;
            
            if (validPlayerCount < config.getMinPlayers()) {
                Bukkit.broadcastMessage(ColorUtil.color(
                    plugin.getConfigManager().getPrefix() + " " +
                    plugin.getConfigManager().getMessage("start.not-enough-players")
                        .replace("{min}", String.valueOf(config.getMinPlayers()))
                ));
                
                // Teleportiere alle Spieler zurÃƒÂ¼ck
                for (UUID playerId : new HashSet<>(participants)) {
                    Player player = Bukkit.getPlayer(playerId);
                    if (player != null) {
                        teleportBack(player);
                    }
                }
                
                cancelEvent();
                return;
            }
            
            // WÃƒÂ¤hle zufÃƒÂ¤llige Spieler aus
            List<UUID> allPlayers = new ArrayList<>(participants);
            Collections.shuffle(allPlayers);
            
            validParticipants.addAll(allPlayers.subList(0, validPlayerCount));
            
            // Informiere ausgeschlossene Spieler und teleportiere sie zurÃƒÂ¼ck
            for (UUID playerId : allPlayers.subList(validPlayerCount, allPlayers.size())) {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null) {
                    excludedPlayers.add(playerId);
                    player.sendMessage(ColorUtil.color(plugin.getConfigManager().getPrefix() + 
                        " &cDu wurdest aussortiert, da die TeamgrÃƒÂ¶ÃƒÅ¸e nicht passt!"));
                    teleportBack(player);
                }
            }
            
            // Erstelle Teams
            teamManager.assignTeams(new ArrayList<>(validParticipants), teamCount);
            
            // Informiere Spieler ÃƒÂ¼ber Teams und teleportiere zur Lobby
            for (UUID playerId : validParticipants) {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null) {
                    TeamManager.Team team = teamManager.getPlayerTeam(playerId);
                    if (team != null) {
                        String teamMsg = config.getMessage("team-assignment")
                            .replace("{team}", ColorUtil.color(team.getDisplayName()));
                        player.sendMessage(ColorUtil.color(plugin.getConfigManager().getPrefix() + " " + teamMsg));
                        
                        teleportToLobby(player);

                        // Lobby-Equipment: optional teamfarbig abhängig von Config
                        if (config.shouldGiveEquipmentInLobby()) {
                            if (config.shouldColorLobbyArmor()) {
                                giveEquipmentToPlayer(player, team);
                            } else {
                                giveLobbyEquipmentToPlayer(player);
                            }
                        }
                    }
                }
            }
        } else {
            // Solo-Modus
            validParticipants.addAll(participants);
        }
        
        startCountdown();
    }

    public void startCountdown() {
        synchronized (stateMutex) { state = EventState.COUNTDOWN; }
        countdown = plugin.getConfigManager().getLobbyCountdown();
        
        broadcast(plugin.getConfigManager().getMessage("lobby.welcome"));
        
        countdownTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (countdown <= 0) {
                    cancel();
                    startEvent();
                    return;
                }
                
                if (countdown == 30 || countdown == 20 || countdown == 10 || countdown <= 5) {
                    broadcast(plugin.getConfigManager().getMessage("start.countdown")
                        .replace("{time}", String.valueOf(countdown)));
                    
                    playSound(Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
                }
                
                if (countdown == 3) {
                    sendTitle("&c3", plugin.getConfigManager().getMessage("countdown.preparing"));
                } else if (countdown == 2) {
                    sendTitle("&e2", plugin.getConfigManager().getMessage("countdown.starting"));
                } else if (countdown == 1) {
                    sendTitle("&a1", plugin.getConfigManager().getMessage("countdown.go"));
                }
                
                countdown--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }
    
    public void forceStartCountdown() {
        if (state != EventState.COUNTDOWN) return;
        countdown = 5;
        broadcast(plugin.getConfigManager().getMessage("admin.force-start"));
    }
    
    // NEU: Lade Welten basierend auf world-loading Einstellung
    private void loadWorlds() {
        String worldLoading = plugin.getConfigManager().getWorldLoading().toLowerCase();
        
        boolean loadLobby = false;
        boolean loadEvent = false;
        
        switch (worldLoading) {
            case "both":
                loadLobby = true;
                loadEvent = true;
                break;
            case "lobby":
                loadLobby = true;
                break;
            case "event":
                loadEvent = true;
                break;
            case "none":
                return;
        }
        
        de.zfzfg.eventplugin.util.MultiverseHelper mv = new de.zfzfg.eventplugin.util.MultiverseHelper(plugin);
        
        if (loadLobby) {
            String lobbyWorld = config.getLobbyWorld();
            plugin.getLogger().info("Lade Lobby-Welt (asynchron): " + lobbyWorld);
            broadcast("&7Lobby-Welt wird geladen... &e⏳");
            mv.loadWorld(lobbyWorld, (success, message) -> {
                if (success) {
                    broadcast("&aLobby-Welt geladen! ✓");
                    plugin.getLogger().info("Lobby-Welt geladen: " + lobbyWorld + " - " + message);
                } else {
                    broadcast("&cLobby-Welt konnte nicht geladen werden! ✗");
                    plugin.getLogger().warning("Lobby-Welt Laden fehlgeschlagen: " + lobbyWorld + " - " + message);
                }
            });
        }

        if (loadEvent) {
            String eventWorld = config.getEventWorld();
            plugin.getLogger().info("Lade Event-Welt (asynchron): " + eventWorld);
            broadcast("&7Event-Welt wird geladen... &e⏳");
            mv.loadWorld(eventWorld, (success, message) -> {
                if (success) {
                    broadcast("&aEvent-Welt geladen! ✓");
                    plugin.getLogger().info("Event-Welt geladen: " + eventWorld + " - " + message);
                } else {
                    broadcast("&cEvent-Welt konnte nicht geladen werden! ✗");
                    plugin.getLogger().warning("Event-Welt Laden fehlgeschlagen: " + eventWorld + " - " + message);
                }
            });
        }
    }
    
    // NEU: Entlade Welten nach Event-Ende
    private void unloadWorlds() {
        String worldLoading = plugin.getConfigManager().getWorldLoading().toLowerCase();
        
        boolean unloadLobby = false;
        boolean unloadEvent = false;
        
        switch (worldLoading) {
            case "both":
                unloadLobby = true;
                unloadEvent = true;
                break;
            case "lobby":
                unloadLobby = true;
                break;
            case "event":
                unloadEvent = true;
                break;
            case "none":
                return;
        }
        
        if (unloadLobby) {
            plugin.getLogger().info("Entlade Lobby-Welt: " + config.getLobbyWorld());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mv unload " + config.getLobbyWorld());
        }
        
        if (unloadEvent) {
            plugin.getLogger().info("Entlade Event-Welt: " + config.getEventWorld());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mv unload " + config.getEventWorld());
        }
    }
    
    private void prepareWorlds() {
        broadcast(plugin.getConfigManager().getMessage("start.preparing-worlds"));
        
        // Lade Event-Welt
        World eventWorld = Bukkit.getWorld(config.getEventWorld());
        if (eventWorld == null) {
            plugin.getLogger().warning("Event-Welt wird geladen: " + config.getEventWorld());
        } else {
            plugin.getLogger().info("Event-Welt gefunden: " + config.getEventWorld());
        }
        
        // Lade Lobby-Welt
        World lobbyWorld = Bukkit.getWorld(config.getLobbyWorld());
        if (lobbyWorld == null) {
            plugin.getLogger().warning("Lobby-Welt wird geladen: " + config.getLobbyWorld());
        } else {
            plugin.getLogger().info("Lobby-Welt gefunden: " + config.getLobbyWorld());
        }
        
        if (config.shouldRegenerateEventWorld()) {
            broadcast(plugin.getConfigManager().getMessage("start.regenerating"));
        }
    }
    
    private void teleportToLobby(Player player) {
        World lobbyWorld = Bukkit.getWorld(config.getLobbyWorld());
        if (lobbyWorld != null) {
            Location lobbySpawn = config.getLobbySpawn().clone();
            lobbySpawn.setWorld(lobbyWorld);
            player.teleport(lobbySpawn);
            player.setGameMode(GameMode.ADVENTURE);
            player.getInventory().clear();
            plugin.getLogger().info("Spieler " + player.getName() + " zur Lobby teleportiert: " + lobbySpawn);
        } else {
            plugin.getLogger().severe("Lobby-Welt nicht gefunden: " + config.getLobbyWorld());
            player.sendMessage(ColorUtil.color("&cFehler: Lobby-Welt nicht gefunden!"));
        }
    }
    
    private void startEvent() {
        synchronized (stateMutex) { state = EventState.RUNNING; }
        
        broadcast(config.getMessage("start"));
        broadcast(config.getMessage("objective"));
        
        World eventWorld = Bukkit.getWorld(config.getEventWorld());
        if (eventWorld == null) {
            plugin.getLogger().severe("Event-Welt nicht gefunden: " + config.getEventWorld());
            broadcast("&cFEHLER: Event-Welt nicht gefunden!");
            cancelEvent();
            return;
        }
        
        plugin.getLogger().info("Teleportiere Spieler zur Event-Welt: " + config.getEventWorld());
        plugin.getLogger().info("Spawn-Type: " + config.getSpawnType());
        
        // NEU: COMMAND spawn-type
        if (config.getSpawnType() == EventConfig.SpawnType.COMMAND) {
            executeSpawnCommand();
        } else {
            // Teleportiere Spieler
            if (config.getGameMode() == EventConfig.GameMode.TEAM_2 || 
                config.getGameMode() == EventConfig.GameMode.TEAM_3) {
                teleportTeamsToSpawns(eventWorld);
            } else {
                teleportPlayersToSpawns(eventWorld);
            }
        }
        
        // Gebe Equipment (falls nicht schon in Lobby gegeben)
        if (!config.shouldGiveEquipmentInLobby()) {
            if (config.getGameMode() == EventConfig.GameMode.TEAM_2 || 
                config.getGameMode() == EventConfig.GameMode.TEAM_3) {
                giveTeamEquipment();
            } else {
                giveEquipment();
            }
        }
        
        sendTitle("&a&lLOS!", plugin.getConfigManager().getMessage("event.started-subtitle"));
        playSound(Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 2.0f);
    }
    
    // NEU: FÃƒÂ¼hre Spawn-Command aus
    private void executeSpawnCommand() {
        String command = config.getSpawnConfig().getSpawnCommand();
        if (command == null || command.isEmpty()) {
            plugin.getLogger().warning("Spawn-Command ist leer!");
            return;
        }
        
        for (UUID uuid : validParticipants) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) continue;
            
            String finalCommand = command.replace("{player}", player.getName());
            plugin.getLogger().info("FÃƒÂ¼hre Spawn-Command aus: " + finalCommand);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCommand);
            
            player.setGameMode(GameMode.SURVIVAL);
        }
    }
    
    private void teleportTeamsToSpawns(World world) {
        Map<String, List<Location>> teamSpawns = config.getSpawnConfig().getTeamSpawns();
        
        for (UUID playerId : validParticipants) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null) continue;
            
            TeamManager.Team team = teamManager.getPlayerTeam(playerId);
            if (team == null) continue;
            
            String teamKey = "team-" + team.name().toLowerCase();
            List<Location> spawns = teamSpawns.get(teamKey);
            
            if (spawns != null && !spawns.isEmpty()) {
                Set<UUID> teamMembers = teamManager.getTeamMembers(team);
                List<UUID> teamMembersList = new ArrayList<>(teamMembers);
                int index = teamMembersList.indexOf(playerId);
                
                Location spawn = spawns.get(index % spawns.size()).clone();
                spawn.setWorld(world);
                player.teleport(spawn);
                player.setGameMode(GameMode.SURVIVAL);
            }
        }
    }
    
    private void teleportPlayersToSpawns(World world) {
        List<UUID> playerList = new ArrayList<>(validParticipants);
        
        switch (config.getSpawnType()) {
            case SINGLE_POINT:
                Location spawn = config.getSpawnConfig().getSingleSpawn().clone();
                spawn.setWorld(world);
                for (UUID uuid : playerList) {
                    Player player = Bukkit.getPlayer(uuid);
                    if (player != null) {
                        player.teleport(spawn);
                        player.setGameMode(GameMode.SURVIVAL);
                        plugin.getLogger().info("Spieler " + player.getName() + " zu SINGLE_POINT teleportiert: " + spawn);
                    }
                }
                break;
                
            case MULTIPLE_SPAWNS:
                List<Location> spawns = config.getSpawnConfig().getMultipleSpawns();
                for (int i = 0; i < playerList.size(); i++) {
                    UUID uuid = playerList.get(i);
                    Player player = Bukkit.getPlayer(uuid);
                    if (player == null) continue;
                    
                    Location spawnLoc = spawns.get(i % spawns.size()).clone();
                    spawnLoc.setWorld(world);
                    player.teleport(spawnLoc);
                    player.setGameMode(GameMode.SURVIVAL);
                    plugin.getLogger().info("Spieler " + player.getName() + " zu MULTIPLE_SPAWNS teleportiert: " + spawnLoc);
                }
                break;
                
            case RANDOM_CUBE:
                EventConfig.RandomCubeConfig cubeConfig = config.getSpawnConfig().getRandomCube();
                List<Location> usedLocations = new ArrayList<>();
                
                for (UUID uuid : playerList) {
                    Player player = Bukkit.getPlayer(uuid);
                    if (player == null) continue;
                    
                    Location randomLoc = findRandomCubeLocation(world, cubeConfig, usedLocations);
                    if (randomLoc != null) {
                        usedLocations.add(randomLoc);
                        player.teleport(randomLoc);
                        player.setGameMode(GameMode.SURVIVAL);
                        plugin.getLogger().info("Spieler " + player.getName() + " zu RANDOM_CUBE teleportiert: " + randomLoc);
                    } else {
                        plugin.getLogger().warning("Konnte keinen Spawn fÃƒÂ¼r " + player.getName() + " finden!");
                    }
                }
                break;
                
            case RANDOM_RADIUS:
                EventConfig.RandomRadiusConfig radiusConfig = config.getSpawnConfig().getRandomRadius();
                List<Location> usedRadiusLocations = new ArrayList<>();
                
                for (UUID uuid : playerList) {
                    Player player = Bukkit.getPlayer(uuid);
                    if (player == null) continue;
                    
                    Location randomLoc = findRandomRadiusLocation(world, radiusConfig, usedRadiusLocations);
                    if (randomLoc != null) {
                        usedRadiusLocations.add(randomLoc);
                        player.teleport(randomLoc);
                        player.setGameMode(GameMode.SURVIVAL);
                        plugin.getLogger().info("Spieler " + player.getName() + " zu RANDOM_RADIUS teleportiert: " + randomLoc);
                    }
                }
                break;
                
            case RANDOM_AREA:
                EventConfig.RandomAreaConfig areaConfig = config.getSpawnConfig().getRandomArea();
                List<Location> usedAreaLocations = new ArrayList<>();
                
                for (UUID uuid : playerList) {
                    Player player = Bukkit.getPlayer(uuid);
                    if (player == null) continue;
                    
                    Location randomLoc = findRandomAreaLocation(world, areaConfig, usedAreaLocations);
                    if (randomLoc != null) {
                        usedAreaLocations.add(randomLoc);
                        player.teleport(randomLoc);
                        player.setGameMode(GameMode.SURVIVAL);
                        plugin.getLogger().info("Spieler " + player.getName() + " zu RANDOM_AREA teleportiert: " + randomLoc);
                    }
                }
                break;
        }
    }
    
    private Location findRandomCubeLocation(World world, EventConfig.RandomCubeConfig config, List<Location> usedLocations) {
        Random random = new Random();
        int maxAttempts = 100;
        
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            double x = config.getMinX() + random.nextDouble() * (config.getMaxX() - config.getMinX());
            double y = config.getMinY() + random.nextDouble() * (config.getMaxY() - config.getMinY());
            double z = config.getMinZ() + random.nextDouble() * (config.getMaxZ() - config.getMinZ());
            
            Location candidate = new Location(world, x, y, z);
            
            // PrÃƒÂ¼fe Mindestabstand zu anderen Spielern
            boolean validLocation = true;
            for (Location usedLoc : usedLocations) {
                if (candidate.distance(usedLoc) < config.getMinDistance()) {
                    validLocation = false;
                    break;
                }
            }
            
            if (validLocation) {
                return candidate;
            }
        }
        
        plugin.getLogger().warning("Konnte keine gÃƒÂ¼ltige RANDOM_CUBE Location finden!");
        // Fallback: Gebe trotzdem eine Position zurÃƒÂ¼ck
        double x = config.getMinX() + random.nextDouble() * (config.getMaxX() - config.getMinX());
        double y = config.getMinY();
        double z = config.getMinZ() + random.nextDouble() * (config.getMaxZ() - config.getMinZ());
        return new Location(world, x, y, z);
    }
    
    private Location findRandomRadiusLocation(World world, EventConfig.RandomRadiusConfig config, List<Location> usedLocations) {
        Random random = new Random();
        int maxAttempts = 100;
        
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            double angle = random.nextDouble() * 2 * Math.PI;
            double distance = random.nextDouble() * config.getRadius();
            
            double x = config.getCenterX() + distance * Math.cos(angle);
            double z = config.getCenterZ() + distance * Math.sin(angle);
            double y = world.getHighestBlockYAt((int) x, (int) z) + 1;
            
            Location candidate = new Location(world, x, y, z);
            
            boolean validLocation = true;
            for (Location usedLoc : usedLocations) {
                if (candidate.distance(usedLoc) < config.getMinDistance()) {
                    validLocation = false;
                    break;
                }
            }
            
            if (validLocation) {
                return candidate;
            }
        }
        
        return null;
    }
    
    private Location findRandomAreaLocation(World world, EventConfig.RandomAreaConfig config, List<Location> usedLocations) {
        Random random = new Random();
        int maxAttempts = 100;
        
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            double x = config.getMinX() + random.nextDouble() * (config.getMaxX() - config.getMinX());
            double z = config.getMinZ() + random.nextDouble() * (config.getMaxZ() - config.getMinZ());
            double y = world.getHighestBlockYAt((int) x, (int) z) + 1;
            
            Location candidate = new Location(world, x, y, z);
            
            boolean validLocation = true;
            for (Location usedLoc : usedLocations) {
                if (candidate.distance(usedLoc) < config.getMinDistance()) {
                    validLocation = false;
                    break;
                }
            }
            
            if (validLocation) {
                return candidate;
            }
        }
        
        return null;
    }
    
    private void giveEquipment() {
        EquipmentGroup equipment = plugin.getConfigManager().getEquipmentGroup(config.getEquipmentGroup());
        if (equipment == null) return;
        
        for (UUID uuid : validParticipants) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) continue;
            
            player.getInventory().clear();
            
            EquipmentGroup.ArmorSet armor = equipment.getArmor();
            if (armor.getHelmet() != null) player.getInventory().setHelmet(armor.getHelmet().clone());
            if (armor.getChestplate() != null) player.getInventory().setChestplate(armor.getChestplate().clone());
            if (armor.getLeggings() != null) player.getInventory().setLeggings(armor.getLeggings().clone());
            if (armor.getBoots() != null) player.getInventory().setBoots(armor.getBoots().clone());
            
            for (EquipmentGroup.InventoryItem item : equipment.getInventory()) {
                player.getInventory().setItem(item.getSlot(), item.getItemStack().clone());
            }
            
            player.setHealth(20.0);
            player.setFoodLevel(20);
        }
    }
    
    private void giveTeamEquipment() {
        for (UUID playerId : validParticipants) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null) continue;
            
            TeamManager.Team team = teamManager.getPlayerTeam(playerId);
            if (team != null) {
                giveEquipmentToPlayer(player, team);
            }
        }
    }

    // Give standard equipment to a single player in lobby (no team coloring)
    private void giveLobbyEquipmentToPlayer(Player player) {
        EquipmentGroup equipment = plugin.getConfigManager().getEquipmentGroup(config.getEquipmentGroup());
        if (equipment == null) return;

        player.getInventory().clear();

        EquipmentGroup.ArmorSet armor = equipment.getArmor();
        if (armor.getHelmet() != null) player.getInventory().setHelmet(armor.getHelmet().clone());
        if (armor.getChestplate() != null) player.getInventory().setChestplate(armor.getChestplate().clone());
        if (armor.getLeggings() != null) player.getInventory().setLeggings(armor.getLeggings().clone());
        if (armor.getBoots() != null) player.getInventory().setBoots(armor.getBoots().clone());

        for (EquipmentGroup.InventoryItem item : equipment.getInventory()) {
            player.getInventory().setItem(item.getSlot(), item.getItemStack().clone());
        }

        player.setHealth(20.0);
        player.setFoodLevel(20);
    }
    
    private void giveEquipmentToPlayer(Player player, TeamManager.Team team) {
        EquipmentGroup equipment = plugin.getConfigManager().getEquipmentGroup(config.getEquipmentGroup());
        if (equipment == null) return;
        
        player.getInventory().clear();
        
        // RÃƒÂ¼stung mit Teamfarbe
        EquipmentGroup.ArmorSet armor = equipment.getArmor();
        
        if (armor.getHelmet() != null) {
            ItemStack helmet = armor.getHelmet().clone();
            if (helmet.getType().name().contains("LEATHER")) {
                LeatherArmorMeta meta = (LeatherArmorMeta) helmet.getItemMeta();
                meta.setColor(team.getColor());
                helmet.setItemMeta(meta);
            }
            player.getInventory().setHelmet(helmet);
        }
        
        if (armor.getChestplate() != null) {
            ItemStack chestplate = armor.getChestplate().clone();
            if (chestplate.getType().name().contains("LEATHER")) {
                LeatherArmorMeta meta = (LeatherArmorMeta) chestplate.getItemMeta();
                meta.setColor(team.getColor());
                chestplate.setItemMeta(meta);
            }
            player.getInventory().setChestplate(chestplate);
        }
        
        if (armor.getLeggings() != null) {
            ItemStack leggings = armor.getLeggings().clone();
            if (leggings.getType().name().contains("LEATHER")) {
                LeatherArmorMeta meta = (LeatherArmorMeta) leggings.getItemMeta();
                meta.setColor(team.getColor());
                leggings.setItemMeta(meta);
            }
            player.getInventory().setLeggings(leggings);
        }
        
        if (armor.getBoots() != null) {
            ItemStack boots = armor.getBoots().clone();
            if (boots.getType().name().contains("LEATHER")) {
                LeatherArmorMeta meta = (LeatherArmorMeta) boots.getItemMeta();
                meta.setColor(team.getColor());
                boots.setItemMeta(meta);
            }
            player.getInventory().setBoots(boots);
        }
        
        // Inventar
        for (EquipmentGroup.InventoryItem item : equipment.getInventory()) {
            player.getInventory().setItem(item.getSlot(), item.getItemStack().clone());
        }
        
        player.setHealth(20.0);
        player.setFoodLevel(20);
    }
    
    public void handlePlayerDeath(Player player) {
        if (!config.getDeathHandling().shouldEliminateOnDeath()) return;
        
        eliminatedPlayers.add(player.getUniqueId());
        spectators.add(player.getUniqueId());
        
        String eliminatedMsg = config.getMessage("eliminated");
        if (eliminatedMsg.isEmpty()) {
            eliminatedMsg = plugin.getConfigManager().getMessage("end.eliminated");
        }
        
        if (teamManager != null) {
            TeamManager.Team team = teamManager.getPlayerTeam(player);
            if (team != null) {
                eliminatedMsg = eliminatedMsg
                    .replace("{player}", player.getName())
                    .replace("{team}", ColorUtil.color(team.getDisplayName()));
            }
        } else {
            eliminatedMsg = eliminatedMsg.replace("{player}", player.getName());
        }
        
        broadcast(eliminatedMsg);
        
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) return;
                
                player.getInventory().clear();
                player.getInventory().setArmorContents(null);
                
                player.setGameMode(GameMode.SPECTATOR);
                // Entfernt: Externe Vanish- und Fly-Kommandos, da Spectator-Modus beides abdeckt
                
                player.sendMessage("");
                player.sendMessage(ColorUtil.color(plugin.getConfigManager().getMessage("event.eliminated-spectator")));
                
                net.md_5.bungee.api.chat.TextComponent component = new net.md_5.bungee.api.chat.TextComponent(
                    ColorUtil.color("&c&l[Ã¢Å“â€“ EVENT VERLASSEN Ã¢Å“â€“]")
                );
                component.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(
                    net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND,
                    "/event leave"
                ));
                component.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(
                    net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
                    new net.md_5.bungee.api.chat.ComponentBuilder("Klicke um zur Hauptwelt zurÃƒÂ¼ckzukehren!").create()
                ));
                
                player.spigot().sendMessage(component);
                player.sendMessage("");
            }
        }.runTaskLater(plugin, 20L);
        
        checkRemainingPlayers();
    }

    public void handleItemPickup(Player player, Material material) {
        if (state != EventState.RUNNING) return;
        if (winner != null || winningTeam != null) return;
        
        if (config.getWinCondition().getType().equals("PICKUP_ITEM")) {
            String targetItem = config.getWinCondition().getItem();
            if (material.name().equalsIgnoreCase(targetItem)) {
                declareWinner(player);
            }
        }
    }
    
    private void declareWinner(Player player) {
        if (config.getGameMode() == EventConfig.GameMode.SOLO) {
            winner = player.getUniqueId();
            
            String winnerMsg = config.getMessage("winner");
            if (winnerMsg.isEmpty()) {
                winnerMsg = plugin.getConfigManager().getMessage("end.winner");
            }
            broadcast(winnerMsg.replace("{player}", player.getName()));
            
            giveRewards(player, config.getWinnerRewards());
            
            spawnFirework(player.getLocation());
            
            sendTitleToPlayer(player, "&6&lGEWONNEN!", 
                plugin.getConfigManager().getMessage("rewards.winner-subtitle"));
            playSound(Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        }
        
        new BukkitRunnable() {
            @Override
            public void run() {
                stopEvent();
            }
        }.runTaskLater(plugin, 100L);
    }
    
    private void declareTeamWinner(TeamManager.Team team) {
        winningTeam = team;
        
        String winnerMsg = config.getMessage("team-winner");
        if (winnerMsg.isEmpty()) {
            winnerMsg = "&6&lTEAM {team} HAT GEWONNEN!";
        }
        broadcast(winnerMsg.replace("{team}", ColorUtil.color(team.getDisplayName())));
        
        // Gebe allen Teammitgliedern Belohnungen
        Set<UUID> teamMembers = teamManager.getTeamMembers(team);
        for (UUID memberId : teamMembers) {
            Player member = Bukkit.getPlayer(memberId);
            if (member != null && validParticipants.contains(memberId)) {
                giveRewards(member, config.getTeamWinnerRewards());
                spawnFirework(member.getLocation());
                sendTitleToPlayer(member, "&6&lGEWONNEN!", 
                    "&aDein Team hat gewonnen!");
            }
        }
        
        playSound(Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        
        new BukkitRunnable() {
            @Override
            public void run() {
                stopEvent();
            }
        }.runTaskLater(plugin, 100L);
    }

    // Broadcast a draw and stop the event shortly after
    private void declareDraw() {
        String allEliminated = plugin.getConfigManager().getMessage("end.all-eliminated");
        if (allEliminated != null && !allEliminated.isEmpty()) {
            broadcast(allEliminated);
        }
        String drawMsg = plugin.getConfigManager().getMessage("end.draw");
        if (drawMsg == null || drawMsg.isEmpty()) {
            drawMsg = "&eEs ist ein Unentschieden!";
        }
        broadcast(drawMsg);
        new BukkitRunnable() {
            @Override
            public void run() { stopEvent(); }
        }.runTaskLater(plugin, 100L);
    }
    
    private void checkRemainingPlayers() {
        Set<UUID> alivePlayers = new HashSet<>(validParticipants);
        alivePlayers.removeAll(eliminatedPlayers);
        
        if (config.getGameMode() == EventConfig.GameMode.TEAM_2 || 
            config.getGameMode() == EventConfig.GameMode.TEAM_3) {
            
            // Team-Modus: PrÃƒÂ¼fe ob nur noch ein Team lebt
            int aliveTeams = teamManager.getAliveTeamCount(alivePlayers);
            
            if (aliveTeams == 1 && winningTeam == null) {
                TeamManager.Team winner = teamManager.getWinningTeam(alivePlayers);
                if (winner != null) {
                    declareTeamWinner(winner);
                }
            } else if (aliveTeams == 0 && winningTeam == null) {
                if (!drawPending) {
                    drawPending = true;
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            try {
                                Set<UUID> aliveNow = new HashSet<>(validParticipants);
                                aliveNow.removeAll(eliminatedPlayers);
                                int aliveTeamsNow = teamManager.getAliveTeamCount(aliveNow);
                                if (aliveTeamsNow == 0 && winningTeam == null && winner == null) {
                                    declareDraw();
                                } else {
                                    broadcast(plugin.getConfigManager().getMessage("end.all-eliminated"));
                                    broadcast(plugin.getConfigManager().getMessage("end.no-winner"));
                                    new BukkitRunnable() {
                                        @Override
                                        public void run() { stopEvent(); }
                                    }.runTaskLater(plugin, 60L);
                                }
                            } finally {
                                drawPending = false;
                            }
                        }
                    }.runTaskLater(plugin, 10L);
                }
            }
            
        } else {
            // Solo-Modus
            long aliveCount = alivePlayers.size();
            
            if (aliveCount == 1 && winner == null) {
                UUID lastUuid = alivePlayers.iterator().next();
                Player lastPlayer = Bukkit.getPlayer(lastUuid);
                if (lastPlayer != null) {
                    declareWinner(lastPlayer);
                }
                
            } else if (aliveCount == 0 && winner == null) {
                if (!drawPending) {
                    drawPending = true;
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            try {
                                Set<UUID> aliveNow = new HashSet<>(validParticipants);
                                aliveNow.removeAll(eliminatedPlayers);
                                if (aliveNow.isEmpty() && winner == null && winningTeam == null) {
                                    declareDraw();
                                } else {
                                    broadcast(plugin.getConfigManager().getMessage("end.all-eliminated"));
                                    broadcast(plugin.getConfigManager().getMessage("end.no-winner"));
                                    new BukkitRunnable() {
                                        @Override
                                        public void run() { stopEvent(); }
                                    }.runTaskLater(plugin, 60L);
                                }
                            } finally {
                                drawPending = false;
                            }
                        }
                    }.runTaskLater(plugin, 10L);
                }
            }
        }
    }
    
    private void giveRewards(Player player, EventConfig.RewardConfig rewards) {
        if (rewards == null) return;
        
        if (rewards.areItemsEnabled()) {
            for (String itemStr : rewards.getItems()) {
                String[] parts = itemStr.split(" ");
                if (parts.length >= 2) {
                    try {
                        Material material = Material.valueOf(parts[0].toUpperCase());
                        int amount = Integer.parseInt(parts[1]);
                        ItemStack item = new ItemStack(material, amount);
                        player.getInventory().addItem(item);
                    } catch (Exception e) {
                        plugin.getLogger().warning("UngÃƒÂ¼ltiges Item: " + itemStr);
                    }
                }
            }
            player.sendMessage(ColorUtil.color(plugin.getConfigManager().getMessage("rewards.item-received")));
        }
        
        if (rewards.areCommandsEnabled()) {
            for (String cmd : rewards.getCommands()) {
                String finalCmd = cmd.replace("{player}", player.getName());
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCmd);
                });
            }
            player.sendMessage(ColorUtil.color(plugin.getConfigManager().getMessage("rewards.command-executed")));
        }
    }
    
    public void stopEvent() {
        state = EventState.FINISHED;
        
        if (countdownTask != null) countdownTask.cancel();
        if (joinPhaseTask != null) joinPhaseTask.cancel();
        
        for (UUID uuid : participants) {
            if (leftSpectators.contains(uuid)) {
                continue;
            }
            
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                if (spectators.contains(uuid)) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "v " + player.getName());
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "fly " + player.getName());
                }
                
                teleportBack(player);
                player.sendMessage(ColorUtil.color(plugin.getConfigManager().getMessage("end.thanks")));
            }
        }
        
        broadcast(plugin.getConfigManager().getMessage("admin.event-stopped"));
        
        // NEU: Entlade Welten nach Event
        new BukkitRunnable() {
            @Override
            public void run() {
        String cloneSrcFS = config.getCloneSourceEventWorld();
        if (cloneSrcFS != null && !cloneSrcFS.isEmpty()) {
            MultiverseHelper mv = new MultiverseHelper(plugin);
            mv.deleteWorld(config.getEventWorld(), null);
            mv.cloneWorld(cloneSrcFS, config.getEventWorld(), null);
            mv.unloadWorld(config.getEventWorld());
        } else if (config.shouldRegenerateEventWorld()) {
            MultiverseHelper mv = new MultiverseHelper(plugin);
            mv.regenerateWorld(config.getEventWorld());
        }
}
        }.runTaskLater(plugin, 40L); // 2 Sekunden nach Teleport
        
        plugin.getEventManager().removeSession(config.getId());
    }
    
    public void forceStop() {
        if (countdownTask != null) countdownTask.cancel();
        if (joinPhaseTask != null) joinPhaseTask.cancel();
        
        for (UUID uuid : participants) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                teleportBack(player);
            }
        }
        
        // NEU: Saubere Reset-Sequenz mit Callback-Verkettung
        String cloneSrcFS = config.getCloneSourceEventWorld();
        MultiverseHelper mv = new MultiverseHelper(plugin);
        if (cloneSrcFS != null && !cloneSrcFS.isEmpty()) {
            mv.deleteWorld(config.getEventWorld(), () ->
                mv.cloneWorld(cloneSrcFS, config.getEventWorld(), () ->
                    mv.unloadWorld(config.getEventWorld())
                )
            );
        } else if (config.shouldRegenerateEventWorld()) {
            mv.regenerateWorld(config.getEventWorld());
        }
    }

    public void cancelForFallback() {
        synchronized (stateMutex) { state = EventState.CANCELLED; }
        
        if (countdownTask != null) countdownTask.cancel();
        if (joinPhaseTask != null) joinPhaseTask.cancel();
        
        for (UUID uuid : participants) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                // Nicht zurÃƒÂ¼ckteleportieren - Spieler bleiben fÃƒÂ¼r Fallback-Event
                player.sendMessage(ColorUtil.color(plugin.getConfigManager().getPrefix() + 
                    " &eEvent wird gewechselt - bitte warten..."));
            }
        }
        
        plugin.getEventManager().removeSession(config.getId());
    }
    
    private void cancelEvent() {
        synchronized (stateMutex) { state = EventState.CANCELLED; }
        
        if (countdownTask != null) countdownTask.cancel();
        if (joinPhaseTask != null) joinPhaseTask.cancel();
        
        broadcast(plugin.getConfigManager().getMessage("start.not-enough-players")
            .replace("{min}", String.valueOf(config.getMinPlayers())));
        
        for (UUID uuid : participants) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                teleportBack(player);
            }
        }
        
        // NEU: Saubere Reset-Sequenz bei Abbruch
        String cloneSrcFS = config.getCloneSourceEventWorld();
        MultiverseHelper mv = new MultiverseHelper(plugin);
        if (cloneSrcFS != null && !cloneSrcFS.isEmpty()) {
            mv.deleteWorld(config.getEventWorld(), () ->
                mv.cloneWorld(cloneSrcFS, config.getEventWorld(), () ->
                    mv.unloadWorld(config.getEventWorld())
                )
            );
        } else if (config.shouldRegenerateEventWorld()) {
            mv.regenerateWorld(config.getEventWorld());
        }
plugin.getEventManager().removeSession(config.getId());
    }
    
    private void teleportBack(Player player) {
        Location savedLoc = savedLocations.get(player.getUniqueId());
        if (savedLoc == null) {
            // Try global saved location if local is missing
            savedLoc = plugin.getEventManager().getSavedLocation(player.getUniqueId());
        }
        if (savedLoc != null) {
            player.teleport(savedLoc);
            // Clear global saved location after teleport
            plugin.getEventManager().clearSavedLocation(player.getUniqueId());
        } else {
            World mainWorld = Bukkit.getWorld(plugin.getConfigManager().getMainWorld());
            if (mainWorld != null) {
                player.teleport(mainWorld.getSpawnLocation());
            }
        }
        player.setGameMode(GameMode.SURVIVAL);
        // Sicherheit: Entferne evtl. Ã¼brig gebliebene Unsichtbarkeit aus anderen Plugins
        player.removePotionEffect(org.bukkit.potion.PotionEffectType.INVISIBILITY);
    }
    
    private void spawnFirework(Location location) {
        Firework firework = location.getWorld().spawn(location, Firework.class);
        FireworkMeta meta = firework.getFireworkMeta();
        meta.addEffect(FireworkEffect.builder()
            .withColor(Color.YELLOW, Color.ORANGE)
            .with(FireworkEffect.Type.BALL_LARGE)
            .withTrail()
            .withFlicker()
            .build());
        meta.setPower(1);
        firework.setFireworkMeta(meta);
    }
    
    private void broadcast(String message) {
        String prefix = plugin.getConfigManager().getPrefix();
        String fullMessage = ColorUtil.color(prefix + " " + message);
        
        for (UUID uuid : validParticipants) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.sendMessage(fullMessage);
            }
        }
    }
    
    private void sendTitle(String title, String subtitle) {
        for (UUID uuid : validParticipants) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.sendTitle(ColorUtil.color(title), ColorUtil.color(subtitle), 10, 40, 10);
            }
        }
    }
    
    private void sendTitleToPlayer(Player player, String title, String subtitle) {
        player.sendTitle(ColorUtil.color(title), ColorUtil.color(subtitle), 10, 60, 10);
    }
    
    private void playSound(Sound sound, float volume, float pitch) {
        for (UUID uuid : validParticipants) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.playSound(player.getLocation(), sound, volume, pitch);
            }
        }
    }
    
    public boolean isParticipant(Player player) {
        return participants.contains(player.getUniqueId());
    }
    
    public boolean isSpectator(Player player) {
        return spectators.contains(player.getUniqueId());
    }
    
    public EventState getState() {
        return state;
    }
    
    public EventConfig getConfig() {
        return config;
    }
    
    public int getParticipantCount() {
        return participants.size();
    }

    public java.util.Set<java.util.UUID> getParticipants() {
        return new java.util.HashSet<>(participants);
    }
    
    public TeamManager getTeamManager() {
        return teamManager;
    }
    
    public enum EventState {
        WAITING,
        JOIN_PHASE,
        COUNTDOWN,
        RUNNING,
        FINISHED,
        CANCELLED
    }
}






