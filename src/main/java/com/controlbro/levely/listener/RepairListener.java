package com.controlbro.levely.listener;

import com.controlbro.levely.LevelyPlugin;
import com.controlbro.levely.manager.DataManager;
import com.controlbro.levely.manager.PassiveManager;
import com.controlbro.levely.manager.SkillManager;
import com.controlbro.levely.model.PlayerProfile;
import com.controlbro.levely.model.SkillData;
import com.controlbro.levely.model.SkillType;
import java.util.Comparator;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

public class RepairListener implements Listener {
    private final LevelyPlugin plugin;
    private final SkillManager skillManager;
    private final PassiveManager passiveManager;
    private final DataManager dataManager;

    public RepairListener(LevelyPlugin plugin, SkillManager skillManager, PassiveManager passiveManager,
                          DataManager dataManager) {
        this.plugin = plugin;
        this.skillManager = skillManager;
        this.passiveManager = passiveManager;
        this.dataManager = dataManager;
    }

    @EventHandler
    public void onRepair(PlayerInteractEvent event) {
        if (!event.getAction().isRightClick()) {
            return;
        }
        if (event.getClickedBlock() == null || event.getClickedBlock().getType() != Material.IRON_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        if (!plugin.getConfig().getBoolean("skills.enabled.repair", true)) {
            return;
        }
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof Damageable damageable)) {
            return;
        }
        int damage = damageable.getDamage();
        if (damage <= 0) {
            return;
        }
        ConfigurationSection itemConfig = plugin.getConfig()
            .getConfigurationSection("repair.items." + item.getType().name());
        if (itemConfig == null) {
            return;
        }
        Material primaryMaterial = Material.matchMaterial(itemConfig.getString("material", ""));
        if (primaryMaterial == null) {
            return;
        }
        if (!player.getInventory().containsAtLeast(new ItemStack(primaryMaterial), 1)) {
            return;
        }
        PlayerProfile profile = dataManager.getOrCreateProfile(player.getUniqueId(), player.getName());
        SkillData data = profile.getSkillData(SkillType.REPAIR);
        double basePercent = itemConfig.getDouble("restorePercent", 0.0);
        double perLevel = plugin.getConfig().getDouble("repair.restorePercentPerLevel", 0.0);
        double maxPercent = plugin.getConfig().getDouble("repair.maxRestorePercent", 1.0);
        double restorePercent = Math.min(maxPercent, basePercent + (data.getLevel() * perLevel));
        int maxDurability = item.getType().getMaxDurability();
        int restored = (int) Math.round(maxDurability * restorePercent);
        if (restored <= 0) {
            return;
        }
        int newDamage = Math.max(0, damage - restored);
        int actualRestored = damage - newDamage;
        if (actualRestored <= 0) {
            return;
        }
        player.getInventory().removeItem(new ItemStack(primaryMaterial, 1));
        applyArcaneForging(data.getLevel(), item);
        damageable.setDamage(newDamage);
        item.setItemMeta((ItemMeta) damageable);
        String category = itemConfig.getString("materialCategory", "");
        double multiplier = plugin.getConfig().getDouble("repair.materialMultipliers." + category, 1.0);
        double xp = actualRestored * multiplier;
        if (xp > 0) {
            skillManager.addXp(player, SkillType.REPAIR, xp);
        }
    }

    private void applyArcaneForging(int level, ItemStack item) {
        if (!plugin.getConfig().getBoolean("passives.repair.arcaneForging.enabled", true)) {
            return;
        }
        if (item.getEnchantments().isEmpty()) {
            return;
        }
        ConfigurationSection ranks = plugin.getConfig().getConfigurationSection("passives.repair.arcaneForging.ranks");
        if (ranks == null) {
            return;
        }
        ArcaneRank rank = ranks.getKeys(false).stream()
            .map(key -> ranks.getConfigurationSection(key))
            .filter(section -> section != null)
            .map(section -> new ArcaneRank(
                section.getInt("level", 0),
                section.getDouble("keepChance", 0.0),
                section.getDouble("downgradeChance", 0.0)))
            .filter(entry -> level >= entry.level())
            .max(Comparator.comparingInt(ArcaneRank::level))
            .orElse(null);
        if (rank == null) {
            return;
        }
        boolean keep = passiveManager.rollChance(rank.keepChance());
        if (keep) {
            return;
        }
        boolean allowDowngrade = plugin.getConfig().getBoolean("passives.repair.arcaneForging.allowDowngrade", true);
        if (allowDowngrade && passiveManager.rollChance(rank.downgradeChance())) {
            Map<org.bukkit.enchantments.Enchantment, Integer> enchants = item.getEnchantments();
            item.getEnchantments().keySet().forEach(item::removeEnchantment);
            for (Map.Entry<org.bukkit.enchantments.Enchantment, Integer> entry : enchants.entrySet()) {
                int newLevel = entry.getValue() - 1;
                if (newLevel > 0) {
                    item.addUnsafeEnchantment(entry.getKey(), newLevel);
                }
            }
            return;
        }
        item.getEnchantments().keySet().forEach(item::removeEnchantment);
    }

    private record ArcaneRank(int level, double keepChance, double downgradeChance) {
    }
}
