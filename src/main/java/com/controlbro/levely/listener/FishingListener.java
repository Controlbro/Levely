package com.controlbro.levely.listener;

import com.controlbro.levely.LevelyPlugin;
import com.controlbro.levely.manager.AbilityManager;
import com.controlbro.levely.manager.FishingManager;
import com.controlbro.levely.manager.LootTable;
import com.controlbro.levely.manager.PassiveManager;
import com.controlbro.levely.manager.SkillManager;
import com.controlbro.levely.model.PlayerProfile;
import com.controlbro.levely.model.SkillType;
import com.controlbro.levely.util.Msg;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;

public class FishingListener implements Listener {
    private final LevelyPlugin plugin;
    private final SkillManager skillManager;
    private final AbilityManager abilityManager;
    private final PassiveManager passiveManager;
    private final FishingManager fishingManager;
    private final Map<UUID, Instant> shakeTargetCooldowns;

    public FishingListener(LevelyPlugin plugin, SkillManager skillManager, AbilityManager abilityManager,
                           PassiveManager passiveManager, FishingManager fishingManager) {
        this.plugin = plugin;
        this.skillManager = skillManager;
        this.abilityManager = abilityManager;
        this.passiveManager = passiveManager;
        this.fishingManager = fishingManager;
        this.shakeTargetCooldowns = new HashMap<>();
    }

    @EventHandler
    public void onFish(PlayerFishEvent event) {
        if (event.getState() == PlayerFishEvent.State.FISHING) {
            applyMasterAngler(event);
            return;
        }
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) {
            return;
        }
        Player player = event.getPlayer();
        if (isBlocked(player)) {
            return;
        }
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("skillXp.fishing");
        if (section == null) {
            return;
        }
        if (!(event.getCaught() instanceof Item caughtItem)) {
            return;
        }
        ItemStack caughtStack = caughtItem.getItemStack();
        String category = resolveCategory(caughtStack.getType());
        if (shouldRollTreasure(category)) {
            Optional<ItemStack> treasure = fishingManager.rollTreasure(player);
            if (treasure.isPresent()) {
                caughtItem.setItemStack(treasure.get());
                category = "treasure";
            }
        }
        double xp = section.getDouble(category, 0);
        if (xp > 0) {
            skillManager.addXp(player, SkillType.FISHING, xp);
        }
    }

    @EventHandler
    public void onIceFish(PlayerInteractEvent event) {
        if (!event.getAction().isRightClick()) {
            return;
        }
        if (event.getClickedBlock() == null) {
            return;
        }
        Player player = event.getPlayer();
        if (!fishingManager.isIceFishingEnabled(player)) {
            return;
        }
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType() != Material.FISHING_ROD) {
            return;
        }
        Material type = event.getClickedBlock().getType();
        if (!plugin.getConfig().getStringList("passives.fishing.iceFishing.allowedBlocks").contains(type.name())) {
            return;
        }
        event.getClickedBlock().setType(Material.WATER);
    }

    @EventHandler
    public void onShake(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        if (isBlocked(player)) {
            return;
        }
        if (player.getInventory().getItemInMainHand().getType() != Material.FISHING_ROD) {
            return;
        }
        if (!plugin.getConfig().getBoolean("abilities.actives.fishing.shake.enabled", true)) {
            return;
        }
        Entity target = event.getRightClicked();
        if (!(target instanceof LivingEntity living) || target instanceof Player) {
            return;
        }
        PlayerProfile profile = abilityManager.getProfile(player);
        int level = profile.getSkillData(SkillType.FISHING).getLevel();
        int required = plugin.getConfig().getInt("abilities.actives.fishing.shake.requiredLevel", 15);
        if (level < required) {
            return;
        }
        int playerCooldown = plugin.getConfig().getInt("abilities.actives.fishing.shake.cooldowns.playerSeconds", 240);
        int targetCooldown = plugin.getConfig().getInt("abilities.actives.fishing.shake.cooldowns.targetSeconds", 60);
        if (!abilityManager.canActivate(player, "shake", playerCooldown)) {
            long remaining = abilityManager.cooldownRemaining(player, "shake", playerCooldown);
            Msg.send(player, "abilities.cooldown", "%seconds%", String.valueOf(remaining));
            return;
        }
        if (!canShakeTarget(player, target.getUniqueId(), targetCooldown)) {
            return;
        }
        abilityManager.activate(player, SkillType.FISHING, "shake",
            plugin.getConfig().getString("abilities.actives.fishing.shake.displayName", "Shake"),
            playerCooldown);
        applyShakeDamage(player, living);
        dropShakeLoot(target.getType(), target.getLocation().toBlockLocation().toCenterLocation());
        shakeTargetCooldowns.put(target.getUniqueId(), Instant.now());
    }

    @EventHandler
    public void onFishEat(PlayerItemConsumeEvent event) {
        if (!plugin.getConfig().getBoolean("passives.fishing.fishermansDiet.enabled", true)) {
            return;
        }
        ItemStack item = event.getItem();
        if (item == null || !Tag.ITEMS_FISHES.isTagged(item.getType())) {
            return;
        }
        Player player = event.getPlayer();
        PlayerProfile profile = abilityManager.getProfile(player);
        int level = profile.getSkillData(SkillType.FISHING).getLevel();
        ConfigurationSection ranks = plugin.getConfig().getConfigurationSection("passives.fishing.fishermansDiet.ranks");
        if (ranks == null) {
            return;
        }
        int bonus = ranks.getKeys(false).stream()
            .map(key -> ranks.getConfigurationSection(key))
            .filter(section -> section != null)
            .filter(section -> level >= section.getInt("level", 0))
            .map(section -> section.getInt("hungerBonus", 0))
            .max(Integer::compareTo)
            .orElse(0);
        if (bonus <= 0) {
            return;
        }
        int newFood = Math.min(20, player.getFoodLevel() + bonus);
        player.setFoodLevel(newFood);
    }

    private void applyMasterAngler(PlayerFishEvent event) {
        Player player = event.getPlayer();
        PlayerProfile profile = abilityManager.getProfile(player);
        int level = profile.getSkillData(SkillType.FISHING).getLevel();
        int required = plugin.getConfig().getInt("passives.fishing.masterAngler.requiredLevel", 15);
        if (level < required) {
            return;
        }
        FishHook hook = event.getHook();
        if (hook == null) {
            return;
        }
        double baseBonus = plugin.getConfig().getDouble("passives.fishing.masterAngler.baseBiteBonus", 0.1);
        double boatMultiplier = plugin.getConfig().getDouble("passives.fishing.masterAngler.boatBonusMultiplier", 1.5);
        double bonus = baseBonus;
        if (player.getVehicle() instanceof org.bukkit.entity.Boat) {
            bonus *= boatMultiplier;
        }
        if (bonus <= 0) {
            return;
        }
        int minWait = Math.max(1, (int) Math.round(hook.getMinWaitTime() * (1.0 - bonus)));
        int maxWait = Math.max(minWait, (int) Math.round(hook.getMaxWaitTime() * (1.0 - bonus)));
        hook.setMinWaitTime(minWait);
        hook.setMaxWaitTime(maxWait);
    }

    private boolean shouldRollTreasure(String category) {
        if (!plugin.getConfig().getBoolean("passives.fishing.treasureHunter.enabled", true)) {
            return false;
        }
        return "fish".equalsIgnoreCase(category) || "junk".equalsIgnoreCase(category);
    }

    private String resolveCategory(Material type) {
        ConfigurationSection categorySection = plugin.getConfig().getConfigurationSection("fishing.categories");
        if (categorySection != null) {
            for (String category : categorySection.getKeys(false)) {
                if (categorySection.getStringList(category).contains(type.name())) {
                    return category;
                }
            }
        }
        if (Tag.ITEMS_FISHES.isTagged(type)) {
            return "fish";
        }
        return "junk";
    }

    private boolean canShakeTarget(Player player, UUID targetId, int cooldownSeconds) {
        if (player.hasPermission(plugin.getConfig().getString("general.cooldownBypassPermission", ""))) {
            return true;
        }
        Instant last = shakeTargetCooldowns.get(targetId);
        if (last == null) {
            return true;
        }
        return Duration.between(last, Instant.now()).getSeconds() >= cooldownSeconds;
    }

    private void applyShakeDamage(Player player, LivingEntity target) {
        double percent = plugin.getConfig().getDouble("abilities.actives.fishing.shake.damagePercent", 0.25);
        double cap = plugin.getConfig().getDouble("abilities.actives.fishing.shake.maxDamage", 10.0);
        double damage = Math.min(target.getMaxHealth() * percent, cap);
        target.damage(damage, player);
    }

    private void dropShakeLoot(EntityType type, org.bukkit.Location location) {
        ConfigurationSection tableSection = plugin.getConfig()
            .getConfigurationSection("abilities.actives.fishing.shake.dropTables." + type.name());
        if (tableSection == null) {
            tableSection = plugin.getConfig()
                .getConfigurationSection("abilities.actives.fishing.shake.dropTables.default");
        }
        if (tableSection != null) {
            LootTable<ItemStack> table = new LootTable<>();
            for (String key : tableSection.getKeys(false)) {
                ConfigurationSection itemSection = tableSection.getConfigurationSection(key);
                Material material = Material.matchMaterial(key);
                if (material == null) {
                    continue;
                }
                int amount = 1;
                double weight = 1.0;
                if (itemSection != null) {
                    amount = Math.max(1, itemSection.getInt("amount", 1));
                    weight = itemSection.getDouble("weight", 1.0);
                } else {
                    weight = tableSection.getDouble(key, 1.0);
                }
                table.add(new ItemStack(material, amount), weight);
            }
            table.roll(new java.util.Random()).ifPresent(item -> location.getWorld().dropItemNaturally(location, item));
        }
        ConfigurationSection skullSection = plugin.getConfig()
            .getConfigurationSection("abilities.actives.fishing.shake.skullDrops." + type.name());
        if (skullSection != null && skullSection.getBoolean("enabled", true)) {
            double chance = skullSection.getDouble("chance", 0.0);
            Material skullMaterial = Material.matchMaterial(skullSection.getString("material", ""));
            if (skullMaterial != null && passiveManager.rollChance(chance)) {
                location.getWorld().dropItemNaturally(location, new ItemStack(skullMaterial));
            }
        }
    }

    private boolean isBlocked(Player player) {
        if (plugin.getConfig().getBoolean("abilities.blockedInCreative", true)
            && player.getGameMode() == GameMode.CREATIVE) {
            return true;
        }
        return plugin.getConfig().getStringList("abilities.blockedWorlds").stream()
            .anyMatch(world -> world.equalsIgnoreCase(player.getWorld().getName()));
    }
}
