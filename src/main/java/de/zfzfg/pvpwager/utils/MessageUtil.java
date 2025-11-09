package de.zfzfg.pvpwager.utils;

import de.zfzfg.core.util.TextUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class MessageUtil {
    
    public static String color(String message) { return TextUtil.color(message); }
    public static List<String> color(List<String> messages) { return messages.stream().map(MessageUtil::color).collect(Collectors.toList()); }
    public static void sendMessage(CommandSender sender, String message) { TextUtil.send(sender, message); }
    public static void sendMessage(Player player, String message) { TextUtil.send(player, message); }
    public static void sendMessages(CommandSender sender, List<String> messages) { for (String m : messages) sendMessage(sender, m); }
    public static void sendMessages(Player player, List<String> messages) { for (String m : messages) sendMessage(player, m); }

    public static String formatTime(long seconds) {
        long minutes = seconds / 60;
        seconds = seconds % 60;
        return minutes > 0 ? String.format("%02d:%02d", minutes, seconds) : String.format("%02d", seconds);
    }

    public static String formatItemList(List<ItemStack> items) {
        if (items == null || items.isEmpty()) return "no items";
        return items.stream()
                .filter(Objects::nonNull)
                .map(item -> item.getType().name() + " x" + item.getAmount())
                .collect(Collectors.joining(", "));
    }
}