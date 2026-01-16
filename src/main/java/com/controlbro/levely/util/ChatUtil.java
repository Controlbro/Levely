package com.controlbro.levely.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.ChatColor;

public final class ChatUtil {
    private ChatUtil() {
    }

    public static String color(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    public static Component mini(String message) {
        return MiniMessage.miniMessage().deserialize(color(message));
    }
}
