package de.zfzfg.pvpwager.managers;

import de.zfzfg.eventplugin.EventPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

public class ConfigManager {
    private final EventPlugin plugin;
    
    private FileConfiguration config;
    private FileConfiguration messages;
    private FileConfiguration arenaConfig;
    private FileConfiguration equipmentConfig;
    
    private File configFile;
    private File messagesFile;
    private File arenaFile;
    private File equipmentFile;
    
    public ConfigManager(EventPlugin plugin) {
        this.plugin = plugin;
        loadConfigs();
    }
    
    public void loadConfigs() {
        // Create plugin folder if it doesn't exist
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        
        // Main config
        configFile = new File(plugin.getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            plugin.saveResource("config.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(configFile);
        
        // Messages config
        messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        messages = YamlConfiguration.loadConfiguration(messagesFile);
        
        // Arena/Worlds config (Unified worlds.yml bevorzugt, Fallback auf arenas.yml)
        File unifiedWorlds = new File(plugin.getDataFolder(), "worlds.yml");
        File legacyArenas = new File(plugin.getDataFolder(), "arenas.yml");
        arenaFile = unifiedWorlds.exists() ? unifiedWorlds : legacyArenas;
        if (!arenaFile.exists()) {
            plugin.saveResource("arenas.yml", false);
            arenaFile = legacyArenas;
        }
        arenaConfig = YamlConfiguration.loadConfiguration(arenaFile);
        
        // Equipment config
        equipmentFile = new File(plugin.getDataFolder(), "equipment.yml");
        if (!equipmentFile.exists()) {
            plugin.saveResource("equipment.yml", false);
        }
        equipmentConfig = YamlConfiguration.loadConfiguration(equipmentFile);
    }
    
    public void reloadConfigs() {
        config = YamlConfiguration.loadConfiguration(configFile);
        messages = YamlConfiguration.loadConfiguration(messagesFile);
        arenaConfig = YamlConfiguration.loadConfiguration(arenaFile);
        equipmentConfig = YamlConfiguration.loadConfiguration(equipmentFile);
        
        plugin.getLogger().info("All configurations reloaded!");
    }
    
    public void saveConfig(FileConfiguration config, File file) {
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save config to " + file.getName());
            e.printStackTrace();
        }
    }
    
    public FileConfiguration getConfig() {
        return config;
    }
    
    public FileConfiguration getMessages() {
        return messages;
    }
    
    public FileConfiguration getArenaConfig() {
        return arenaConfig;
    }
    
    public FileConfiguration getEquipmentConfig() {
        return equipmentConfig;
    }
    
    public String getMessage(String path, String... replacements) {
        String message = messages.getString(path, "&cMessage not found: " + path);
        
        // Apply replacements in pairs (key, value)
        for (int i = 0; i < replacements.length - 1; i += 2) {
            message = message.replace("{" + replacements[i] + "}", replacements[i + 1]);
        }
        
        return message;
    }
}
