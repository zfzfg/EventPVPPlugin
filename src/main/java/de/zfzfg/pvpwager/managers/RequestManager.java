package de.zfzfg.pvpwager.managers;

import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.pvpwager.models.PvPRequest;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import de.zfzfg.pvpwager.utils.MessageUtil;
import org.bukkit.scheduler.BukkitTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.UUID;

public class RequestManager {
    private final EventPlugin plugin;
    private final Map<UUID, PvPRequest> requests = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> expirationTasks = new ConcurrentHashMap<>();
    
    public RequestManager(EventPlugin plugin) {
        this.plugin = plugin;
    }
    
    public void sendRequest(Player sender, Player target) {
        UUID senderId = sender.getUniqueId();
        UUID targetId = target.getUniqueId();
        
        // Cancel existing requests between these players
        cancelRequest(senderId, targetId);
        cancelRequest(targetId, senderId);
        
        PvPRequest request = new PvPRequest(senderId, targetId);
        requests.put(senderId, request);
        
        // Send clickable message to target
        sendClickableRequest(sender, target);
        
        // Schedule auto-expire and track task
        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (requests.containsKey(senderId)) {
                PvPRequest expiredRequest = requests.get(senderId);
                if (expiredRequest.getTargetId().equals(targetId)) {
                    cancelRequest(senderId, targetId);
                    MessageUtil.sendMessage(sender, 
                        plugin.getPvpConfigManager().getMessage("request.expired", "player", target.getName()));
                    MessageUtil.sendMessage(target, 
                        plugin.getPvpConfigManager().getMessage("request.expired", "player", sender.getName()));
                }
            }
        }, 1200L); // 60 seconds
        expirationTasks.put(senderId, task);
    }
    
    private void sendClickableRequest(Player sender, Player target) {
        try {
            // Create main message
            TextComponent message = new TextComponent(MessageUtil.color(
                "\n§6§l━━━━━━━━━━━━━━━━━━━━━━━\n" +
                "§e§lPVP-ANFRAGE\n" +
                "§6§l━━━━━━━━━━━━━━━━━━━━━━━\n\n" +
                "§e" + sender.getName() + " §amöchte gegen dich kämpfen!\n\n"
            ));
            
            // Create clickable ACCEPT button
            TextComponent acceptButton = new TextComponent(MessageUtil.color("§a§l[► ANNEHMEN ◄]"));
            acceptButton.setClickEvent(new ClickEvent(
                ClickEvent.Action.RUN_COMMAND,
                "/pvp accept " + sender.getName()
            ));
            acceptButton.setHoverEvent(new HoverEvent(
                HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder(MessageUtil.color("§aKlicke um die Anfrage anzunehmen!")).create()
            ));
            
            // Create DENY button
            TextComponent denyButton = new TextComponent(MessageUtil.color(" §c§l[✖ ABLEHNEN]"));
            denyButton.setClickEvent(new ClickEvent(
                ClickEvent.Action.RUN_COMMAND,
                "/pvp deny " + sender.getName()
            ));
            denyButton.setHoverEvent(new HoverEvent(
                HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder(MessageUtil.color("§cKlicke um die Anfrage abzulehnen")).create()
            ));
            
            // Create footer
            TextComponent footer = new TextComponent(MessageUtil.color(
                "\n\n§7Die Anfrage läuft in §e60 Sekunden §7ab.\n" +
                "§6§l━━━━━━━━━━━━━━━━━━━━━━━\n"
            ));
            
            // Combine all components
            message.addExtra(acceptButton);
            message.addExtra(denyButton);
            message.addExtra(footer);
            
            // Send to target
            target.spigot().sendMessage(message);
            
        } catch (Exception e) {
            // Fallback to simple message if chat components fail
            MessageUtil.sendMessage(target, 
                plugin.getPvpConfigManager().getMessage("request.received", "player", sender.getName()));
            MessageUtil.sendMessage(target, 
                "&a/pvp accept " + sender.getName() + " &7- Annehmen");
            MessageUtil.sendMessage(target, 
                "&c/pvp deny " + sender.getName() + " &7- Ablehnen");
        }
    }
    
    public boolean acceptRequest(Player target, Player sender) {
        UUID senderId = sender.getUniqueId();
        UUID targetId = target.getUniqueId();
        
        if (requests.containsKey(senderId)) {
            PvPRequest request = requests.get(senderId);
            
            if (request.getTargetId().equals(targetId)) {
                // Remove request and cancel expiration
                requests.remove(senderId);
                BukkitTask task = expirationTasks.remove(senderId);
                if (task != null) task.cancel();
                
                // Send acceptance messages
                MessageUtil.sendMessage(target, 
                    plugin.getPvpConfigManager().getMessage("request.accepted", "player", sender.getName()));
                MessageUtil.sendMessage(sender, 
                    plugin.getPvpConfigManager().getMessage("request.accepted", "player", target.getName()));
                
                // Start match setup
                plugin.getMatchManager().startMatchSetup(sender, target);
                return true;
            }
        }
        
        return false;
    }
    
    public void cancelRequest(UUID senderId, UUID targetId) {
        if (requests.containsKey(senderId)) {
            PvPRequest request = requests.get(senderId);
            if (request.getTargetId().equals(targetId)) {
                requests.remove(senderId);
                BukkitTask task = expirationTasks.remove(senderId);
                if (task != null) task.cancel();
            }
        }
    }
    
    public boolean hasPendingRequest(Player player) {
        UUID playerId = player.getUniqueId();
        if (requests.containsKey(playerId)) return true;
        // Snapshot sichern gegen gleichzeitige Änderungen
        for (PvPRequest req : new java.util.ArrayList<>(requests.values())) {
            if (req.getTargetId().equals(playerId)) return true;
        }
        return false;
    }
    
    public void cleanup() {
        // cancel tasks and clear all maps
        for (BukkitTask task : expirationTasks.values()) {
            task.cancel();
        }
        expirationTasks.clear();
        requests.clear();
    }
}