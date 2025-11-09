package de.zfzfg.pvpwager.commands;

import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.pvpwager.models.CommandRequest;
import de.zfzfg.pvpwager.models.Match;
import de.zfzfg.pvpwager.utils.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class PvPNoCommand implements CommandExecutor {
    
    private final EventPlugin plugin;
    
    public PvPNoCommand(EventPlugin plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this command!");
            return true;
        }
        
        Player player = (Player) sender;
        
        // Check if player is sender or target
        CommandRequest request = null;
        boolean isSender = false;
        
        // Check as sender
        for (CommandRequest req : plugin.getCommandRequestManager().getPendingRequests()) {
            if (req.getSender().equals(player)) {
                request = req;
                isSender = true;
                break;
            }
        }
        
        // Check as target
        if (request == null) {
            request = plugin.getCommandRequestManager().getRequestToPlayer(player);
        }
        
        if (request == null) {
            MessageUtil.sendMessage(player, "&cNo pending request!");
            return true;
        }
        
        Player other = isSender ? request.getTarget() : request.getSender();
        
        MessageUtil.sendMessage(player, "&cYou declined the request from/to &e" + other.getName());
        MessageUtil.sendMessage(other, "&e" + player.getName() + " &cdeclined the request!");
        
        plugin.getCommandRequestManager().removeRequest(request.getSender());
        
        return true;
    }
}