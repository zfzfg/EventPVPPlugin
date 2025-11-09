package de.zfzfg.pvpwager.managers;

import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.pvpwager.models.CommandRequest;
import de.zfzfg.pvpwager.utils.MessageUtil;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.Collection;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

public class CommandRequestManager {
    private final EventPlugin plugin;
    private final Map<UUID, CommandRequest> pendingRequests = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> expirationTasks = new ConcurrentHashMap<>();
    // O(1) Index: Zielspieler -> letzte empfangene Anfrage
    private final Map<UUID, CommandRequest> targetToLatestRequest = new ConcurrentHashMap<>();
    
    public CommandRequestManager(EventPlugin plugin) {
        this.plugin = plugin;
    }
    
    public void addRequest(CommandRequest request) {
        pendingRequests.put(request.getSender().getUniqueId(), request);
        targetToLatestRequest.put(request.getTarget().getUniqueId(), request);
        
        // Auto-expire after 60 seconds and track task
        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            CommandRequest existing = pendingRequests.get(request.getSender().getUniqueId());
            if (existing != null && existing.equals(request)) {
                pendingRequests.remove(request.getSender().getUniqueId());
                // Entferne Ziel-Index, wenn er auf diese Anfrage zeigt
                CommandRequest latest = targetToLatestRequest.get(request.getTarget().getUniqueId());
                if (latest != null && latest.equals(request)) {
                    targetToLatestRequest.remove(request.getTarget().getUniqueId());
                }
                BukkitTask t = expirationTasks.remove(request.getSender().getUniqueId());
                if (t != null) t.cancel();
                MessageUtil.sendMessage(request.getSender(), "&cYour request to &e" + 
                    request.getTarget().getName() + " &chas expired!");
                MessageUtil.sendMessage(request.getTarget(), "&cRequest from &e" + 
                    request.getSender().getName() + " &chas expired!");
            }
        }, 1200L);
        expirationTasks.put(request.getSender().getUniqueId(), task);
    }
    
    public CommandRequest getRequest(Player sender, Player target) {
        CommandRequest request = pendingRequests.get(sender.getUniqueId());
        if (request != null && request.getTarget().equals(target)) {
            return request;
        }
        return null;
    }
    
    public CommandRequest getRequestToPlayer(Player target) {
        return targetToLatestRequest.get(target.getUniqueId());
    }
    
    public void removeRequest(Player sender) {
        CommandRequest existing = pendingRequests.remove(sender.getUniqueId());
        if (existing != null) {
            CommandRequest latest = targetToLatestRequest.get(existing.getTarget().getUniqueId());
            if (latest != null && latest.equals(existing)) {
                targetToLatestRequest.remove(existing.getTarget().getUniqueId());
            }
        }
        BukkitTask task = expirationTasks.remove(sender.getUniqueId());
        if (task != null) task.cancel();
    }
    
    public void sendRequestNotification(CommandRequest request) {
        Player target = request.getTarget();
        
        MessageUtil.sendMessage(target, "");
        MessageUtil.sendMessage(target, "&6&l━━━━━━━━━━━━━━━━━━━━━━━");
        MessageUtil.sendMessage(target, "&e&lPVP CHALLENGE!");
        MessageUtil.sendMessage(target, "&6&l━━━━━━━━━━━━━━━━━━━━━━━");
        MessageUtil.sendMessage(target, "");
        MessageUtil.sendMessage(target, "&e" + request.getSender().getName() + " &7has challenged you!");
        MessageUtil.sendMessage(target, "");
        MessageUtil.sendMessage(target, "&7Arena: &e" + request.getArenaId());
        MessageUtil.sendMessage(target, "&7Equipment: &e" + request.getEquipmentId());
        
        if (request.getMoney() > 0) {
            MessageUtil.sendMessage(target, "&7Their Wager: &6$" + String.format("%.2f", request.getMoney()));
        } else {
            MessageUtil.sendMessage(target, "&7Their Wager: &e" + MessageUtil.formatItemList(request.getWagerItems()));
        }
        
        MessageUtil.sendMessage(target, "");
        MessageUtil.sendMessage(target, "&7Respond with:");
        MessageUtil.sendMessage(target, "&a/pvpanswer <your_wager> <amount> &8[optional: arena] [equipment]");
        MessageUtil.sendMessage(target, "&7Or decline with: &c/pvpno");
        MessageUtil.sendMessage(target, "");
        MessageUtil.sendMessage(target, "&7Request expires in &e60 seconds");
        MessageUtil.sendMessage(target, "");
    }
    
    public Collection<CommandRequest> getPendingRequests() {
        return new ArrayList<>(pendingRequests.values());
    }
    
    public boolean hasPendingRequest(Player player) {
        // Check if player is sender
        if (pendingRequests.containsKey(player.getUniqueId())) {
            return true;
        }
       
        // Check if player is target (Snapshot sichern gegen gleichzeitige Änderungen)
        for (CommandRequest request : new ArrayList<>(pendingRequests.values())) {
            if (request.getTarget().equals(player)) {
                return true;
            }
        }
       
        return false;
    }

    public void cleanup() {
        for (BukkitTask t : expirationTasks.values()) {
            try { t.cancel(); } catch (Exception ignored) {}
        }
        expirationTasks.clear();
        pendingRequests.clear();
        targetToLatestRequest.clear();
    }
}