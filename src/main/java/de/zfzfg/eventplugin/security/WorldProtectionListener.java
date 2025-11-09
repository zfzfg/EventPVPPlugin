package de.zfzfg.eventplugin.security;

import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.eventplugin.session.EventSession;
import de.zfzfg.pvpwager.models.Arena;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;

public class WorldProtectionListener implements Listener {
    private final EventPlugin plugin;

    public WorldProtectionListener(EventPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean isProtectedWorld(String worldName) {
        // Event-Welten aus aktiven Sessions
        for (EventSession session : plugin.getEventManager().getActiveSessions().values()) {
            if (worldName.equalsIgnoreCase(session.getConfig().getEventWorld()) ||
                worldName.equalsIgnoreCase(session.getConfig().getLobbyWorld())) {
                return true;
            }
        }
        // Arena-Welten
        for (Arena arena : plugin.getArenaManager().getArenas().values()) {
            if (worldName.equalsIgnoreCase(arena.getArenaWorld())) {
                return true;
            }
        }
        return false;
    }

    private boolean canModify(Player player) {
        return player.isOp() || player.hasPermission("eventpvp.build");
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Player player = event.getPlayer();
        if (isProtectedWorld(block.getWorld().getName()) && !canModify(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        Player player = event.getPlayer();
        if (isProtectedWorld(block.getWorld().getName()) && !canModify(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        Entity entity = event.getEntity();
        if (entity == null) return;
        if (isProtectedWorld(entity.getWorld().getName())) {
            event.blockList().clear();
        }
    }
}