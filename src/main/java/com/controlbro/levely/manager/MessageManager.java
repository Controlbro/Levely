package com.controlbro.levely.manager;

import com.controlbro.levely.LevelyPlugin;
import java.io.File;
import org.bukkit.configuration.file.YamlConfiguration;

public class MessageManager {
    private final LevelyPlugin plugin;
    private YamlConfiguration messages;

    public MessageManager(LevelyPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        this.messages = YamlConfiguration.loadConfiguration(file);
    }

    public String getString(String path) {
        return messages.getString(path, "");
    }
}
