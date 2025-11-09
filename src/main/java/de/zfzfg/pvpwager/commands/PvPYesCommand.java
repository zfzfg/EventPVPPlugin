package de.zfzfg.pvpwager.commands;

import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.pvpwager.managers.ArenaManager;
import de.zfzfg.pvpwager.models.CommandRequest;
import de.zfzfg.pvpwager.models.Match;
import de.zfzfg.pvpwager.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class PvPYesCommand implements CommandExecutor {
    
    private final EventPlugin plugin;
    
    public PvPYesCommand(EventPlugin plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this command!");
            return true;
        }
        
        Player player = (Player) sender;
        
        // Get request where player is sender
        CommandRequest request = null;
        for (CommandRequest req : plugin.getCommandRequestManager().getPendingRequests()) {
            if (req.getSender().equals(player)) {
                request = req;
                break;
            }
        }
        
        // Make request final for inner class usage
        final CommandRequest finalRequest = request;
        
        if (request == null) {
            MessageUtil.sendMessage(player, "&cNo pending request!");
            return true;
        }
        
        if (!request.hasTargetResponded()) {
            MessageUtil.sendMessage(player, "&cWaiting for " + request.getTarget().getName() + " to respond!");
            return true;
        }
        
        // Verify items/money still available
        Player target = request.getTarget();
        
        // Check sender's items/money
        if (request.getMoney() > 0) {
            if (!plugin.getEconomy().has(player, request.getMoney())) {
                MessageUtil.sendMessage(player, "&cYou no longer have enough money!");
                plugin.getCommandRequestManager().removeRequest(player);
                return true;
            }
        } else {
            for (ItemStack item : request.getWagerItems()) {
                if (!player.getInventory().containsAtLeast(item, item.getAmount())) {
                    MessageUtil.sendMessage(player, "&cYou no longer have the required items!");
                    plugin.getCommandRequestManager().removeRequest(player);
                    return true;
                }
            }
        }
        
        // Check target's items/money
        if (request.getTargetWagerMoney() > 0) {
            if (!plugin.getEconomy().has(target, request.getTargetWagerMoney())) {
                MessageUtil.sendMessage(player, "&c" + target.getName() + " no longer has enough money!");
                MessageUtil.sendMessage(target, "&cYou no longer have enough money - request cancelled!");
                plugin.getCommandRequestManager().removeRequest(player);
                return true;
            }
        } else {
            for (ItemStack item : request.getTargetWagerItems()) {
                if (!target.getInventory().containsAtLeast(item, item.getAmount())) {
                    MessageUtil.sendMessage(player, "&c" + target.getName() + " no longer has the required items!");
                    MessageUtil.sendMessage(target, "&cYou no longer have the required items - request cancelled!");
                    plugin.getCommandRequestManager().removeRequest(player);
                    return true;
                }
            }
        }
        
        // Welt aus Arena-Konfiguration holen (nicht die Arena-ID!)
        String resolvedWorldName = null;
        if (request.getFinalArenaId() != null && !request.getFinalArenaId().isEmpty()) {
            de.zfzfg.pvpwager.models.Arena arenaCfg = plugin.getArenaManager().getArena(request.getFinalArenaId());
            if (arenaCfg != null) {
                resolvedWorldName = arenaCfg.getArenaWorld();
            }
        }
        final String worldName = resolvedWorldName;
        final Player finalPlayer = player;
        final Player finalTarget = target;
        
        // Welt laden mit robuster Fehlerbehandlung
        if (worldName != null && !worldName.isEmpty()) {
            MessageUtil.sendMessage(player, "&eWelt wird geladen...");
            MessageUtil.sendMessage(target, "&eWelt wird geladen...");
            
            // Nutze ArenaManager für zuverlässige Welt-Ladung
            plugin.getArenaManager().loadArenaWorld(worldName, new Runnable() {
                @Override
                public void run() {
                    // Prüfe ob Welt geladen wurde
                    if (Bukkit.getWorld(worldName) == null) {
                        MessageUtil.sendMessage(finalPlayer, "&cFehler: Welt konnte nicht geladen werden!");
                        MessageUtil.sendMessage(finalTarget, "&cFehler: Welt konnte nicht geladen werden!");
                        plugin.getCommandRequestManager().removeRequest(finalPlayer);
                        return;
                    }
                    
                    MessageUtil.sendMessage(finalPlayer, "&aWelt geladen! Match startet...");
                    MessageUtil.sendMessage(finalTarget, "&aWelt geladen! Match startet...");
                    
                    // Start match via command system
                    plugin.getMatchManager().startMatchFromCommand(finalRequest);
                    plugin.getCommandRequestManager().removeRequest(finalPlayer);
                }
            });
            
        } else {
            // Keine Welt angegeben, direkt starten
            plugin.getMatchManager().startMatchFromCommand(finalRequest);
            plugin.getCommandRequestManager().removeRequest(player);
        }
        
        return true;
    }
}