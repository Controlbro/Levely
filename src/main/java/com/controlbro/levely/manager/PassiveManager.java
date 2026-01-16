package com.controlbro.levely.manager;

import com.controlbro.levely.LevelyPlugin;
import java.util.Random;

public class PassiveManager {
    private final LevelyPlugin plugin;
    private final Random random;

    public PassiveManager(LevelyPlugin plugin, DataManager dataManager, SkillManager skillManager) {
        this.plugin = plugin;
        this.random = new Random();
    }

    public boolean rollChance(double chance) {
        return random.nextDouble() <= chance;
    }

    public double scaledChance(int level, String path, double defaultBase, double defaultPerLevel, double cap) {
        double base = plugin.getConfig().getDouble(path + ".baseChance", defaultBase);
        double perLevel = plugin.getConfig().getDouble(path + ".chancePerLevel", defaultPerLevel);
        double max = plugin.getConfig().getDouble(path + ".cap", cap);
        double chance = base + (level * perLevel);
        return Math.min(chance, max);
    }
}
