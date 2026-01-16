package com.controlbro.levely.listener;

import com.controlbro.levely.LevelyPlugin;
import com.controlbro.levely.manager.SkillManager;
import com.controlbro.levely.model.SkillType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;

public class FishingListener implements Listener {
    private final LevelyPlugin plugin;
    private final SkillManager skillManager;

    public FishingListener(LevelyPlugin plugin, SkillManager skillManager) {
        this.plugin = plugin;
        this.skillManager = skillManager;
    }

    @EventHandler
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) {
            return;
        }
        Player player = event.getPlayer();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("skillXp.fishing");
        if (section == null) {
            return;
        }
        String key = event.getCaught() != null ? "fish" : "junk";
        double xp = section.getDouble(key, 0);
        if (xp > 0) {
            skillManager.addXp(player, SkillType.FISHING, xp);
        }
    }
}
