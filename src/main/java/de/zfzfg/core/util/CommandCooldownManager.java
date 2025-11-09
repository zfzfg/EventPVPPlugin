package de.zfzfg.core.util;

import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CommandCooldownManager {
    private final Map<UUID, Map<String, Long>> cooldowns = new ConcurrentHashMap<>();
    private final long defaultCooldownMs;

    public CommandCooldownManager() {
        this(3000L);
    }

    public CommandCooldownManager(long defaultCooldownMs) {
        this.defaultCooldownMs = defaultCooldownMs;
    }

    public boolean checkAndApply(Player player, String commandName) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();

        Map<String, Long> playerCooldowns = cooldowns.computeIfAbsent(uuid, k -> new HashMap<>());
        Long lastUse = playerCooldowns.get(commandName);

        if (lastUse != null && (now - lastUse) < defaultCooldownMs) {
            long remaining = (defaultCooldownMs - (now - lastUse)) / 1000;
            player.sendMessage("§cBitte warte noch " + remaining + " Sekunden!");
            return false;
        }

        playerCooldowns.put(commandName, now);
        return true;
    }
}