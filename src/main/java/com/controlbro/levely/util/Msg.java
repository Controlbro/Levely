package com.controlbro.levely.util;

import com.controlbro.levely.LevelyPlugin;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class Msg {
    private static LevelyPlugin plugin;

    private Msg() {
    }

    public static void init(LevelyPlugin plugin) {
        Msg.plugin = plugin;
    }

    public static void send(CommandSender sender, String key, Object... placeholders) {
        send(sender, key, true, placeholders);
    }

    public static void send(CommandSender sender, String key, boolean includePrefix, Object... placeholders) {
        String message = resolveMessage(key, placeholders);
        sendRaw(sender, message, includePrefix);
    }

    public static void sendRaw(CommandSender sender, String message, boolean includePrefix) {
        if (sender == null || message == null || message.isEmpty()) {
            return;
        }
        String prefix = includePrefix ? getPrefix() : "";
        sender.sendMessage(ChatUtil.color(prefix + message));
    }

    public static void broadcast(String key, Object... placeholders) {
        broadcast(key, true, placeholders);
    }

    public static void broadcast(String key, boolean includePrefix, Object... placeholders) {
        String message = resolveMessage(key, placeholders);
        if (message.isEmpty()) {
            return;
        }
        String prefix = includePrefix ? getPrefix() : "";
        String formatted = ChatUtil.color(prefix + message);
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(formatted);
        }
    }

    private static String resolveMessage(String key, Object... placeholders) {
        if (plugin == null) {
            return "";
        }
        String message = plugin.getMessageManager().getString(key);
        if (message == null) {
            message = "";
        }
        Map<String, String> pairs = buildPlaceholders(placeholders);
        for (Map.Entry<String, String> entry : pairs.entrySet()) {
            message = message.replace(entry.getKey(), entry.getValue());
        }
        return message;
    }

    private static String getPrefix() {
        if (plugin == null) {
            return "";
        }
        String prefix = plugin.getMessageManager().getString("messages.prefix");
        return prefix == null ? "" : prefix;
    }

    private static Map<String, String> buildPlaceholders(Object... placeholders) {
        Map<String, String> pairs = new HashMap<>();
        if (placeholders == null) {
            return pairs;
        }
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            String key = String.valueOf(placeholders[i]);
            String value = String.valueOf(placeholders[i + 1]);
            pairs.put(key, value);
        }
        return pairs;
    }
}
