package de.zfzfg.eventplugin;

import de.zfzfg.eventplugin.commands.EventCommand;
import de.zfzfg.eventplugin.listeners.EventListener;
import de.zfzfg.eventplugin.listeners.TeamPvPListener;
import de.zfzfg.eventplugin.listeners.WorldChangeListener;
import de.zfzfg.eventplugin.manager.AutoEventManager;
import de.zfzfg.eventplugin.manager.ConfigManager;
import de.zfzfg.eventplugin.manager.EventManager;
import de.zfzfg.eventplugin.security.PlayerModeListener;
import de.zfzfg.pvpwager.commands.*;
import de.zfzfg.pvpwager.listeners.PvPListener;
import de.zfzfg.pvpwager.managers.*;
import net.milkbowl.vault.economy.Economy;
import de.zfzfg.eventplugin.world.WorldStateManager;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import de.zfzfg.core.config.CoreConfigManager;

public class EventPlugin extends JavaPlugin {

    private static EventPlugin instance;

    // Event-Modul
    private ConfigManager configManager;
    private EventManager eventManager;
    private AutoEventManager autoEventManager;

    // PvP-Wager-Modul
    private de.zfzfg.pvpwager.managers.ConfigManager pvpConfigManager;
    private MatchManager matchManager;
    private RequestManager requestManager;
    private ArenaManager arenaManager;
    private EquipmentManager equipmentManager;
    private CommandRequestManager commandRequestManager;
    private Economy economy;
    private WorldStateManager worldStateManager;
    private CoreConfigManager coreConfigManager;

    @Override
    public void onEnable() {
        long t0 = System.nanoTime();
        instance = this;

        // Zentralen Core-Config-Manager laden (vereinheitlichte Dateien)
        coreConfigManager = new CoreConfigManager(this);
        coreConfigManager.load();

        // Lade Event-Konfigurationen
        configManager = new ConfigManager(this);
        configManager.loadConfigs();

        // Initialisiere Event-Manager
        eventManager = new EventManager(this);
        autoEventManager = new AutoEventManager(this);

        // Registriere Event-Command und Listener
        getCommand("event").setExecutor(new EventCommand(this));
        getCommand("eventpvp").setExecutor(new de.zfzfg.eventplugin.commands.EventPvpCommand(this));
        getServer().getPluginManager().registerEvents(new EventListener(this), this);
        getServer().getPluginManager().registerEvents(new WorldChangeListener(this), this);
        getServer().getPluginManager().registerEvents(new TeamPvPListener(this), this);

        // Zusätzliche Sicherheits-/Modus-Listener
        getServer().getPluginManager().registerEvents(new PlayerModeListener(this), this);
        getServer().getPluginManager().registerEvents(new de.zfzfg.eventplugin.security.WorldProtectionListener(this), this);

        if (configManager.isAutoEventsEnabled()) {
            autoEventManager.start();
        }

        // === PvP-Wager Modul ===
        worldStateManager = new WorldStateManager(this);
        pvpConfigManager = new de.zfzfg.pvpwager.managers.ConfigManager(this);
        arenaManager = new ArenaManager(this);
        equipmentManager = new EquipmentManager(this);
        matchManager = new MatchManager(this);
        requestManager = new RequestManager(this);
        commandRequestManager = new CommandRequestManager(this);

        // Vault Economy Hook
        if (getServer().getPluginManager().getPlugin("Vault") != null) {
            setupEconomy();
        } else {
            getLogger().warning("Vault nicht gefunden! Geld-Wetten sind deaktiviert.");
        }

        // Registriere PvP-Commands
        getCommand("pvp").setExecutor(new PvPCommand(this));
        PvPACommand pvpaCommand = new PvPACommand(this);
        getCommand("pvpa").setExecutor(pvpaCommand);
        getCommand("pvpa").setTabCompleter(pvpaCommand);
        PvPAnswerCommand pvpanswerCommand = new PvPAnswerCommand(this);
        getCommand("pvpanswer").setExecutor(pvpanswerCommand);
        getCommand("pvpanswer").setTabCompleter(pvpanswerCommand);
        getCommand("pvpyes").setExecutor(new PvPYesCommand(this));
        getCommand("pvpno").setExecutor(new PvPNoCommand(this));
        getCommand("pvpadmin").setExecutor(new PvPAdminCommand(this));
        getCommand("surrender").setExecutor(new SurrenderCommand(this));
        getCommand("draw").setExecutor(new DrawCommand(this));
        getCommand("pvpainfo").setExecutor(new PvPInfoCommand(this));

        // Registriere PvP-Listener
        getServer().getPluginManager().registerEvents(new PvPListener(this), this);
        getServer().getPluginManager().registerEvents(new de.zfzfg.pvpwager.listeners.WorldChangeListener(this), this);

        long enableMs = (System.nanoTime() - t0) / 1_000_000L;
        getLogger().info("Event-PVP-Plugin aktiviert in " + enableMs + " ms: Events & PvP-Wager kombiniert.");
    }

    @Override
    public void onDisable() {
        long t0 = System.nanoTime();
        // Stoppe laufende Events
        if (eventManager != null) {
            eventManager.stopAllEvents();
        }
        if (autoEventManager != null) {
            autoEventManager.stop();
        }

        // Stoppe laufende Matches
        if (matchManager != null) {
            matchManager.stopAllMatches();
            matchManager.cancelAllTasks();
            matchManager.clearTransientState();
        }
        if (requestManager != null) {
            requestManager.cleanup();
        }
        if (commandRequestManager != null) {
            commandRequestManager.cleanup();
        }
        if (worldStateManager != null) {
            worldStateManager.clearCache();
        }

        long disableMs = (System.nanoTime() - t0) / 1_000_000L;
        getLogger().info("Event-PVP-Plugin deaktiviert in " + disableMs + " ms.");
    }

    private boolean setupEconomy() {
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        economy = rsp.getProvider();
        getLogger().info("Vault Economy eingebunden: " + (economy != null));
        return economy != null;
    }

    public static EventPlugin getInstance() {
        return instance;
    }

    // Event-Getters
    public ConfigManager getConfigManager() { return configManager; }
    public EventManager getEventManager() { return eventManager; }
    public AutoEventManager getAutoEventManager() { return autoEventManager; }

    // PvP-Getters (für angepasste Klassen)
    public de.zfzfg.pvpwager.managers.ConfigManager getPvpConfigManager() { return pvpConfigManager; }
    public MatchManager getMatchManager() { return matchManager; }
    public RequestManager getRequestManager() { return requestManager; }
    public ArenaManager getArenaManager() { return arenaManager; }
    public EquipmentManager getEquipmentManager() { return equipmentManager; }
    public CommandRequestManager getCommandRequestManager() { return commandRequestManager; }
    public Economy getEconomy() { return economy; }
    public boolean hasEconomy() { return economy != null; }

    public WorldStateManager getWorldStateManager() { return worldStateManager; }
    public CoreConfigManager getCoreConfigManager() { return coreConfigManager; }
}