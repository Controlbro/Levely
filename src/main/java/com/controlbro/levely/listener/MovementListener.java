package com.controlbro.levely.listener;

import com.controlbro.levely.LevelyPlugin;
import com.controlbro.levely.manager.PassiveManager;
import com.controlbro.levely.manager.SkillManager;
import com.controlbro.levely.model.SkillType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;

public class MovementListener implements Listener {
    private final LevelyPlugin plugin;
    private final SkillManager skillManager;
    private final PassiveManager passiveManager;

    public MovementListener(LevelyPlugin plugin, SkillManager skillManager, PassiveManager passiveManager) {
        this.plugin = plugin;
        this.skillManager = skillManager;
        this.passiveManager = passiveManager;
    }

    @EventHandler
    public void onFall(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) {
            return;
        }
        double multiplier = plugin.getConfig().getDouble("combatXp.acrobatics.fallDamageMultiplier", 115);
        double xp = event.getFinalDamage() * multiplier;
        if (xp > 0) {
            skillManager.addXp(player, SkillType.ACROBATICS, xp);
        }
    }
}
