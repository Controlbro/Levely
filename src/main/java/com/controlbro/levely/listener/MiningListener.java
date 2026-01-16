package com.controlbro.levely.listener;

import com.controlbro.levely.LevelyPlugin;
import com.controlbro.levely.manager.SkillManager;
import com.controlbro.levely.model.SkillType;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

public class MiningListener implements Listener {
    private final LevelyPlugin plugin;
    private final SkillManager skillManager;

    public MiningListener(LevelyPlugin plugin, SkillManager skillManager) {
        this.plugin = plugin;
        this.skillManager = skillManager;
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Material type = event.getBlock().getType();
        grantIfConfigured(player, type, "skillXp.mining", SkillType.MINING);
        grantIfConfigured(player, type, "skillXp.woodcutting", SkillType.WOODCUTTING);
        grantIfConfigured(player, type, "skillXp.herbalism", SkillType.HERBALISM);
        grantIfConfigured(player, type, "skillXp.excavation", SkillType.EXCAVATION);
    }

    private void grantIfConfigured(Player player, Material type, String section, SkillType skill) {
        ConfigurationSection config = plugin.getConfig().getConfigurationSection(section);
        if (config == null) {
            return;
        }
        if (!config.contains(type.name())) {
            return;
        }
        double xp = config.getDouble(type.name());
        if (xp > 0) {
            skillManager.addXp(player, skill, xp);
        }
    }
}
