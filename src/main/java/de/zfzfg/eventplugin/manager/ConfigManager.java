package de.zfzfg.eventplugin.manager;

import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.eventplugin.model.EventConfig;
import de.zfzfg.eventplugin.model.EquipmentGroup;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class ConfigManager {
    
    private final EventPlugin plugin;
    private FileConfiguration config;
    private FileConfiguration equipmentConfig;
    private FileConfiguration messagesConfig;
    
    private Map<String, EventConfig> events;
    private Map<String, EquipmentGroup> equipmentGroups;
    private String prefix;
    private String mainWorld;
    private boolean savePlayerLocation;
    private int joinPhaseDuration;
    private int lobbyCountdown;
    private String commandRestriction;
    private String worldLoading;  // NEU
    private boolean autoEventsEnabled;
    private int autoEventIntervalMin;
    private int autoEventIntervalMax;
    private boolean autoEventRandomSelection;
    private boolean checkOnlinePlayers;
    
    public ConfigManager(EventPlugin plugin) {
        this.plugin = plugin;
        this.events = new HashMap<>();
        this.equipmentGroups = new HashMap<>();
    }
    
    public void loadConfigs() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdir();
        }
        
        loadMainConfig();
        loadEquipmentConfig();
        loadMessagesConfig();
        
        parseEvents();
        parseEquipment();
    }
    
    private void loadMainConfig() {
        // Bevorzuge gemeinsame config.yml, fallback auf events-config.yml
        File unified = new File(plugin.getDataFolder(), "config.yml");
        File legacy = new File(plugin.getDataFolder(), "events-config.yml");
        File configFile = unified.exists() ? unified : legacy;
        if (!configFile.exists()) {
            // Erzeuge Legacy-Datei falls gar nichts vorhanden ist
            plugin.saveResource("events-config.yml", false);
            configFile = legacy;
        }
        config = YamlConfiguration.loadConfiguration(configFile);
        
        prefix = config.getString("settings.prefix", "&6[Event]&r");
        mainWorld = config.getString("settings.main-world", "world");
        savePlayerLocation = config.getBoolean("settings.save-player-location", true);
        joinPhaseDuration = config.getInt("settings.join-phase-duration", 30);
        lobbyCountdown = config.getInt("settings.lobby-countdown", 30);
        commandRestriction = config.getString("settings.command-restriction", "both");
        worldLoading = config.getString("settings.world-loading", "both");  // NEU
        
        ConfigurationSection autoEvents = config.getConfigurationSection("settings.auto-events");
        if (autoEvents != null) {
            autoEventsEnabled = autoEvents.getBoolean("enabled", false);
            autoEventIntervalMin = autoEvents.getInt("interval-min", 1800);
            autoEventIntervalMax = autoEvents.getInt("interval-max", 3600);
            autoEventRandomSelection = autoEvents.getBoolean("random-selection", true);
            checkOnlinePlayers = autoEvents.getBoolean("check-online-players", true);
        }
    }
    
    private void loadEquipmentConfig() {
        // Bevorzuge gemeinsame equipment.yml, fallback auf events-equipment.yml
        File unified = new File(plugin.getDataFolder(), "equipment.yml");
        File legacy = new File(plugin.getDataFolder(), "events-equipment.yml");
        File equipFile = unified.exists() ? unified : legacy;
        if (!equipFile.exists()) {
            plugin.saveResource("events-equipment.yml", false);
            equipFile = legacy;
        }
        equipmentConfig = YamlConfiguration.loadConfiguration(equipFile);
    }
    
    private void loadMessagesConfig() {
        // Bevorzuge gemeinsame messages.yml, fallback auf events-messages.yml
        File unified = new File(plugin.getDataFolder(), "messages.yml");
        File legacy = new File(plugin.getDataFolder(), "events-messages.yml");
        File msgFile = unified.exists() ? unified : legacy;
        if (!msgFile.exists()) {
            plugin.saveResource("events-messages.yml", false);
            msgFile = legacy;
        }
        messagesConfig = YamlConfiguration.loadConfiguration(msgFile);
    }
    
    private void parseEvents() {
        events.clear();
        ConfigurationSection eventsSection = config.getConfigurationSection("events");
        if (eventsSection == null) return;
        
        for (String eventId : eventsSection.getKeys(false)) {
            ConfigurationSection eventSection = eventsSection.getConfigurationSection(eventId);
            if (eventSection == null) continue;
            
            if (!eventSection.getBoolean("enabled", true)) continue;
            
            EventConfig eventConfig = new EventConfig(eventId, eventSection);
            events.put(eventId, eventConfig);
            
            plugin.getLogger().info("Event geladen: " + eventId);
        }
    }
    
    private void parseEquipment() {
        equipmentGroups.clear();
        // Prefer unified 'equipment' section with event-equip-enable flag
        ConfigurationSection unifiedSection = equipmentConfig.getConfigurationSection("equipment");
        if (unifiedSection != null) {
            for (String groupId : unifiedSection.getKeys(false)) {
                ConfigurationSection groupSection = unifiedSection.getConfigurationSection(groupId);
                if (groupSection == null) continue;

                boolean eventEnabled = groupSection.getBoolean("event-equip-enable", true);
                if (!eventEnabled) {
                    plugin.getLogger().info("Equipment '" + groupId + "' nicht für Events aktiviert, überspringe...");
                    continue;
                }

                EquipmentGroup group = new EquipmentGroup(groupId, groupSection);
                equipmentGroups.put(groupId, group);
                plugin.getLogger().info("Equipment für Events geladen: " + groupId);
            }
        }

        // Fallback auf legacy 'equipment-groups'
        if (equipmentGroups.isEmpty()) {
            ConfigurationSection groupsSection = equipmentConfig.getConfigurationSection("equipment-groups");
            if (groupsSection == null) return;
            for (String groupId : groupsSection.getKeys(false)) {
                ConfigurationSection groupSection = groupsSection.getConfigurationSection(groupId);
                if (groupSection == null) continue;

                EquipmentGroup group = new EquipmentGroup(groupId, groupSection);
                equipmentGroups.put(groupId, group);
                plugin.getLogger().info("Legacy Equipment-Gruppe geladen: " + groupId);
            }
        }
    }
    
    public void reloadConfigs() {
        loadConfigs();
    }
    
    public EventConfig getEventConfig(String eventId) {
        return events.get(eventId);
    }
    
    public Map<String, EventConfig> getAllEvents() {
        return events;
    }
    
    public EquipmentGroup getEquipmentGroup(String groupId) {
        // Try direct lookup first
        EquipmentGroup group = equipmentGroups.get(groupId);
        if (group != null) return group;

        // Fallbacks: try commonly used defaults
        if (equipmentGroups.containsKey("default")) {
            plugin.getLogger().warning("Equipment group '" + groupId + "' not found. Falling back to 'default'.");
            return equipmentGroups.get("default");
        }
        if (equipmentGroups.containsKey("starter")) {
            plugin.getLogger().warning("Equipment group '" + groupId + "' not found. Falling back to 'starter'.");
            return equipmentGroups.get("starter");
        }

        // Final fallback: first available group
        if (!equipmentGroups.isEmpty()) {
            Map.Entry<String, EquipmentGroup> any = equipmentGroups.entrySet().iterator().next();
            plugin.getLogger().warning("Equipment group '" + groupId + "' not found. Falling back to '" + any.getKey() + "'.");
            return any.getValue();
        }

        // No equipment configured
        plugin.getLogger().severe("No equipment groups are configured. Players will not receive kits.");
        return null;
    }
    
    public String getPrefix() {
        return prefix;
    }
    
    public String getMainWorld() {
        return mainWorld;
    }
    
    public boolean shouldSavePlayerLocation() {
        return savePlayerLocation;
    }
    
    public int getJoinPhaseDuration() {
        return joinPhaseDuration;
    }
    
    public int getLobbyCountdown() {
        return lobbyCountdown;
    }
    
    public String getCommandRestriction() {
        return commandRestriction;
    }
    
    public String getWorldLoading() {  // NEU
        return worldLoading;
    }
    
    public boolean isAutoEventsEnabled() {
        return autoEventsEnabled;
    }
    
    public int getAutoEventIntervalMin() {
        return autoEventIntervalMin;
    }
    
    public int getAutoEventIntervalMax() {
        return autoEventIntervalMax;
    }
    
    public boolean isAutoEventRandomSelection() {
        return autoEventRandomSelection;
    }
    
    public boolean shouldCheckOnlinePlayers() {
        return checkOnlinePlayers;
    }
    
    public String getMessage(String path) {
        return messagesConfig.getString("messages." + path, "&cMissing message: " + path);
    }
    
    public String getMessage(String path, String... replacements) {
        String message = getMessage(path);
        for (int i = 0; i < replacements.length; i += 2) {
            if (i + 1 < replacements.length) {
                message = message.replace("{" + replacements[i] + "}", replacements[i + 1]);
            }
        }
        return message;
    }
}