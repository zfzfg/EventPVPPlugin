package de.zfzfg.core.config;

import de.zfzfg.eventplugin.EventPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

/**
 * Zentraler Config-Manager für vereinte Konfigurationen:
 * - config.yml (allgemeine Einstellungen)
 * - messages.yml (Nachrichten)
 * - worlds.yml (Welt-/Arena-Definitionen für Events & PvP)
 * - equipment.yml (Ausrüstungen für Events & PvP)
 */
public class CoreConfigManager {

    private final EventPlugin plugin;

    private File configFile;
    private File messagesFile;
    private File worldsFile;
    private File equipmentFile;

    private FileConfiguration config;
    private FileConfiguration messages;
    private FileConfiguration worlds;
    private FileConfiguration equipment;

    public CoreConfigManager(EventPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        // Hauptconfig
        configFile = new File(plugin.getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            // Fallback: events-config.yml falls vorhanden
            File legacy = new File(plugin.getDataFolder(), "events-config.yml");
            if (legacy.exists()) configFile = legacy; else plugin.saveResource("config.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(configFile);

        // Messages
        messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            File legacy = new File(plugin.getDataFolder(), "events-messages.yml");
            if (legacy.exists()) messagesFile = legacy; else plugin.saveResource("messages.yml", false);
        }
        messages = YamlConfiguration.loadConfiguration(messagesFile);

        // Worlds/Arenas
        worldsFile = new File(plugin.getDataFolder(), "worlds.yml");
        if (!worldsFile.exists()) {
            File legacy = new File(plugin.getDataFolder(), "arenas.yml");
            if (legacy.exists()) worldsFile = legacy; else plugin.saveResource("worlds.yml", false);
        }
        worlds = YamlConfiguration.loadConfiguration(worldsFile);

        // Equipment
        equipmentFile = new File(plugin.getDataFolder(), "equipment.yml");
        if (!equipmentFile.exists()) {
            File legacy = new File(plugin.getDataFolder(), "events-equipment.yml");
            if (legacy.exists()) equipmentFile = legacy; else plugin.saveResource("equipment.yml", false);
        }
        equipment = YamlConfiguration.loadConfiguration(equipmentFile);
    }

    public void reloadAll() {
        config = YamlConfiguration.loadConfiguration(configFile);
        messages = YamlConfiguration.loadConfiguration(messagesFile);
        worlds = YamlConfiguration.loadConfiguration(worldsFile);
        equipment = YamlConfiguration.loadConfiguration(equipmentFile);
        plugin.getLogger().info("Core-Konfigurationen neu geladen.");
    }

    public FileConfiguration getConfig() { return config; }
    public FileConfiguration getMessages() { return messages; }
    public FileConfiguration getWorlds() { return worlds; }
    public FileConfiguration getEquipment() { return equipment; }
}