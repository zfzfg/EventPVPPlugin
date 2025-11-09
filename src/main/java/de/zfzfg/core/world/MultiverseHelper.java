package de.zfzfg.core.world;

import de.zfzfg.eventplugin.EventPlugin;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Zentrale Multiverse-Hilfsklasse, die von Event- und PvP-Modulen verwendet wird.
 * Vereinigt Lade-, Klon-, Lösch- und Regenerationsfunktionen.
 */
public class MultiverseHelper {

    private final EventPlugin plugin;

    public MultiverseHelper(EventPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isMultiverseAvailable() {
        Plugin mv = Bukkit.getPluginManager().getPlugin("Multiverse-Core");
        return mv != null && mv.isEnabled();
    }

    public void unloadWorld(String worldName) {
        if (!isMultiverseAvailable()) return;
        World world = Bukkit.getWorld(worldName);
        if (world != null) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mv unload " + worldName);
        }
    }

    public void loadWorld(String worldName, LoadCallback callback) {
        if (!isMultiverseAvailable()) {
            if (callback != null) callback.onResult(false, "Multiverse-Core nicht installiert");
            return;
        }
        World world = Bukkit.getWorld(worldName);
        if (world != null) {
            if (callback != null) callback.onResult(true, "Welt bereits geladen");
            return;
        }
        boolean ok = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mv load " + worldName);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (callback != null) callback.onResult(ok, ok ? "mv load ausgeführt" : "mv load fehlgeschlagen");
        }, 40L);
    }

    public void regenerateWorld(String worldName) {
        regenerateWorld(worldName, null);
    }

    public void regenerateWorld(String worldName, Runnable callback) {
        if (!isMultiverseAvailable()) {
            if (callback != null) callback.run();
            return;
        }
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            if (callback != null) callback.run();
            return;
        }
        // Teleportiere alle Spieler aus der Welt in die Hauptwelt
        World mainWorld = Bukkit.getWorld("world");
        if (mainWorld == null && !Bukkit.getWorlds().isEmpty()) {
            mainWorld = Bukkit.getWorlds().get(0);
        }
        if (mainWorld != null) {
            for (Player p : world.getPlayers()) {
                p.teleport(mainWorld.getSpawnLocation());
            }
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mv regen " + worldName);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mv confirm");
                if (callback != null) callback.run();
            }, 40L);
        }, 40L);
    }

    public void deleteWorld(String worldName, Runnable callback) {
        if (!isMultiverseAvailable()) {
            if (callback != null) callback.run();
            return;
        }
        World world = Bukkit.getWorld(worldName);
        if (world != null) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mv unload " + worldName);
        }
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mv delete " + worldName);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mv confirm");
            if (callback != null) callback.run();
        }, 40L);
    }

    public void cloneWorld(String sourceWorld, String targetWorld, Runnable callback) {
        if (!isMultiverseAvailable()) {
            if (callback != null) callback.run();
            return;
        }
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mv clone " + sourceWorld + " " + targetWorld);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (callback != null) callback.run();
        }, 40L);
    }

    public interface LoadCallback {
        void onResult(boolean success, String message);
    }
}