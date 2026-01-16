package com.controlbro.levely.listener;

import com.controlbro.levely.LevelyPlugin;
import com.controlbro.levely.manager.PassiveManager;
import com.controlbro.levely.manager.PartyManager;
import com.controlbro.levely.manager.SkillManager;
import com.controlbro.levely.model.SkillType;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

public class CombatListener implements Listener {
    private final LevelyPlugin plugin;
    private final SkillManager skillManager;
    private final PassiveManager passiveManager;
    private final PartyManager partyManager;

    public CombatListener(LevelyPlugin plugin, SkillManager skillManager, PassiveManager passiveManager, PartyManager partyManager) {
        this.plugin = plugin;
        this.skillManager = skillManager;
        this.passiveManager = passiveManager;
        this.partyManager = partyManager;
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity target)) {
            return;
        }
        Player damager = null;
        SkillType skill = null;
        if (event.getDamager() instanceof Player player) {
            damager = player;
            skill = resolveSkill(player);
        } else if (event.getDamager() instanceof Arrow arrow && arrow.getShooter() instanceof Player player) {
            damager = player;
            skill = SkillType.ARCHERY;
        }
        if (damager == null || skill == null) {
            return;
        }
        double baseXp = plugin.getConfig().getDouble("combatXp.base." + skill.getKey(), 1.0);
        double multiplier = getMobMultiplier(target);
        if (multiplier <= 0) {
            return;
        }
        double xp = event.getFinalDamage() * baseXp * multiplier;
        skillManager.addXp(damager, skill, xp);
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        // Placeholder for taming xp and party XP sharing hooks
        Entity entity = event.getEntity();
        if (entity instanceof LivingEntity) {
            // Future hooks for party XP sharing on kill.
        }
    }

    private SkillType resolveSkill(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR) {
            return SkillType.UNARMED;
        }
        Material type = item.getType();
        if (type.name().endsWith("_SWORD")) {
            return SkillType.SWORDS;
        }
        if (type.name().endsWith("_AXE")) {
            return SkillType.AXES;
        }
        return SkillType.UNARMED;
    }

    private double getMobMultiplier(LivingEntity entity) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("xp.mobMultipliers");
        if (section == null) {
            return 1.0;
        }
        if (entity instanceof Animals) {
            return section.getDouble("ANIMALS", 1.0);
        }
        EntityType type = entity.getType();
        return section.getDouble(type.name(), 1.0);
    }
}
