package com.controlbro.levely.manager;

import com.controlbro.levely.LevelyPlugin;
import java.io.File;
import java.io.IOException;
import org.bukkit.configuration.file.YamlConfiguration;

public class SkillsConfigManager {
    private final LevelyPlugin plugin;
    private final File file;
    private YamlConfiguration config;

    public SkillsConfigManager(LevelyPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "skills.yml");
        reload();
    }

    public void reload() {
        if (!file.exists()) {
            plugin.saveResource("skills.yml", false);
        }
        this.config = YamlConfiguration.loadConfiguration(file);
    }

    public YamlConfiguration getConfig() {
        return config;
    }

    public void save() {
        if (config == null) {
            return;
        }
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save skills.yml: " + e.getMessage());
        }
    }
}
