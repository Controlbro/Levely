package com.controlbro.levely.listener;

import com.controlbro.levely.LevelyPlugin;
import com.controlbro.levely.manager.SkillManager;
import com.controlbro.levely.model.SkillType;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.FurnaceExtractEvent;

public class CraftingListener implements Listener {
    private final LevelyPlugin plugin;
    private final SkillManager skillManager;

    public CraftingListener(LevelyPlugin plugin, SkillManager skillManager) {
        this.plugin = plugin;
        this.skillManager = skillManager;
    }

    @EventHandler
    public void onFurnaceExtract(FurnaceExtractEvent event) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("skillXp.smelting");
        if (section == null) {
            return;
        }
        Material type = event.getItemType();
        if (!section.contains(type.name())) {
            return;
        }
        double xp = section.getDouble(type.name());
        if (xp > 0) {
            skillManager.addXp(event.getPlayer(), SkillType.SMELTING, xp * event.getItemAmount());
        }
    }
}
