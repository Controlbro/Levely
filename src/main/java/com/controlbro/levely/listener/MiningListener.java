package com.controlbro.levely.listener;

import com.controlbro.levely.LevelyPlugin;
import com.controlbro.levely.manager.AbilityManager;
import com.controlbro.levely.manager.DataManager;
import com.controlbro.levely.manager.PassiveManager;
import com.controlbro.levely.manager.SkillManager;
import com.controlbro.levely.model.PlayerProfile;
import com.controlbro.levely.model.SkillData;
import com.controlbro.levely.model.SkillType;
import com.controlbro.levely.util.Msg;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class MiningListener implements Listener {
    private final LevelyPlugin plugin;
    private final SkillManager skillManager;
    private final AbilityManager abilityManager;
    private final PassiveManager passiveManager;
    private final DataManager dataManager;
    private final Map<UUID, ActiveAbility> superBreakerActive;
    private final Map<String, Instant> placedBlocks;
    private final Map<String, UUID> placedTnt;
    private final NamespacedKey blastMiningKey;
    private final NamespacedKey blastMiningOwnerKey;

    public MiningListener(LevelyPlugin plugin, SkillManager skillManager, AbilityManager abilityManager,
                          PassiveManager passiveManager, DataManager dataManager) {
        this.plugin = plugin;
        this.skillManager = skillManager;
        this.abilityManager = abilityManager;
        this.passiveManager = passiveManager;
        this.dataManager = dataManager;
        this.superBreakerActive = new HashMap<>();
        this.placedBlocks = new HashMap<>();
        this.placedTnt = new HashMap<>();
        this.blastMiningKey = new NamespacedKey(plugin, "blast_mining");
        this.blastMiningOwnerKey = new NamespacedKey(plugin, "blast_mining_owner");
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Material type = event.getBlock().getType();
        String locationKey = toKey(event.getBlock().getLocation());
        placedBlocks.remove(locationKey);
        placedTnt.remove(locationKey);
        double miningXp = getConfiguredXp(type, "skillXp.mining");
        if (miningXp > 0 && !isPlacedBlock(locationKey)) {
            skillManager.addXp(player, SkillType.MINING, miningXp);
        }
        handleMiningDrops(player, event.getBlock().getDrops(player.getInventory().getItemInMainHand()), event.getBlock());
        grantIfConfigured(player, type, "skillXp.woodcutting", SkillType.WOODCUTTING);
        grantIfConfigured(player, type, "skillXp.herbalism", SkillType.HERBALISM);
        grantIfConfigured(player, type, "skillXp.excavation", SkillType.EXCAVATION);
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!plugin.getConfig().getBoolean("xp.antiExploit.trackPlacedBlocks", true)) {
            return;
        }
        String key = toKey(event.getBlock().getLocation());
        placedBlocks.put(key, Instant.now());
        if (event.getBlock().getType() == Material.TNT) {
            placedTnt.put(key, event.getPlayer().getUniqueId());
        }
    }

    @EventHandler
    public void onSuperBreakerActivate(PlayerInteractEvent event) {
        if (!event.getAction().isRightClick()) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!isPickaxe(item)) {
            return;
        }
        if (!plugin.getConfig().getBoolean("abilities.actives.mining.superBreaker.enabled", true)) {
            return;
        }
        if (plugin.getConfig().getBoolean("abilities.blockedInCreative", true)
            && player.getGameMode() == org.bukkit.GameMode.CREATIVE) {
            return;
        }
        if (isBlockedWorld(player)) {
            return;
        }
        PlayerProfile profile = dataManager.getOrCreateProfile(player.getUniqueId(), player.getName());
        SkillData data = profile.getSkillData(SkillType.MINING);
        int requiredLevel = plugin.getConfig().getInt("abilities.actives.mining.superBreaker.requiredLevel", 50);
        if (data.getLevel() < requiredLevel) {
            return;
        }
        if (superBreakerActive.containsKey(player.getUniqueId())) {
            return;
        }
        int cooldownSeconds = plugin.getConfig().getInt("abilities.actives.mining.superBreaker.cooldownSeconds", 240);
        if (!abilityManager.canActivate(player, "superBreaker", cooldownSeconds)) {
            long remaining = abilityManager.cooldownRemaining(player, "superBreaker", cooldownSeconds);
            Msg.send(player, "abilities.cooldown", "%seconds%", String.valueOf(remaining));
            return;
        }
        activateSuperBreaker(player, data.getLevel());
    }

    @EventHandler
    public void onItemSwap(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        if (!superBreakerActive.containsKey(player.getUniqueId())) {
            return;
        }
        ItemStack next = player.getInventory().getItem(event.getNewSlot());
        if (!isPickaxe(next)) {
            endSuperBreaker(player, true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        endSuperBreaker(event.getPlayer(), false);
    }

    @EventHandler
    public void onBlastMiningInteract(PlayerInteractEvent event) {
        if (!event.getAction().isRightClick()) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.isSneaking()) {
            return;
        }
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType() != Material.FLINT_AND_STEEL) {
            return;
        }
        if (!plugin.getConfig().getBoolean("abilities.actives.mining.blastMining.enabled", true)) {
            return;
        }
        if (plugin.getConfig().getBoolean("abilities.blockedInCreative", true)
            && player.getGameMode() == org.bukkit.GameMode.CREATIVE) {
            return;
        }
        if (isBlockedWorld(player)) {
            return;
        }
        PlayerProfile profile = dataManager.getOrCreateProfile(player.getUniqueId(), player.getName());
        int requiredLevel = plugin.getConfig().getInt("abilities.actives.mining.blastMining.requiredLevel", 50);
        if (profile.getSkillData(SkillType.MINING).getLevel() < requiredLevel) {
            return;
        }
        int range = plugin.getConfig().getInt("abilities.actives.mining.blastMining.range", 6);
        String tntKey = findOwnedTnt(player, range);
        if (tntKey == null) {
            return;
        }
        event.setCancelled(true);
        detonateTnt(player, tntKey);
    }

    @EventHandler
    public void onBlastMiningPrime(ExplosionPrimeEvent event) {
        if (!(event.getEntity() instanceof TNTPrimed primed)) {
            return;
        }
        if (!isBlastMining(primed)) {
            return;
        }
        Player player = getBlastOwner(primed);
        if (player == null) {
            return;
        }
        int level = dataManager.getOrCreateProfile(player.getUniqueId(), player.getName())
            .getSkillData(SkillType.MINING).getLevel();
        int bonus = getBiggerBombsBonus(level);
        if (bonus > 0) {
            event.setRadius(event.getRadius() + bonus);
        }
    }

    @EventHandler
    public void onBlastMiningExplode(EntityExplodeEvent event) {
        if (!(event.getEntity() instanceof TNTPrimed primed)) {
            return;
        }
        if (!isBlastMining(primed)) {
            return;
        }
        Player player = getBlastOwner(primed);
        if (player == null) {
            return;
        }
        event.setYield(0f);
        ConfigurationSection miningSection = plugin.getConfig().getConfigurationSection("skillXp.mining");
        if (miningSection == null) {
            return;
        }
        double xpMultiplier = plugin.getConfig().getDouble("abilities.actives.mining.blastMining.xpMultiplier", 0.35);
        double oreDropChance = getBlastDropChance(player, "abilities.actives.mining.blastMining.oreDrops");
        double debrisDropChance = plugin.getConfig().getDouble("abilities.actives.mining.blastMining.debrisDropChance", 0.1);
        double oreYieldMultiplier = plugin.getConfig().getDouble("abilities.actives.mining.blastMining.oreYieldMultiplier", 1.0);
        double[] xpAwarded = new double[]{0};
        event.blockList().forEach(block -> {
            Material blockType = block.getType();
            boolean mineable = miningSection.contains(blockType.name());
            String key = toKey(block.getLocation());
            boolean placed = isPlacedBlock(key);
            placedBlocks.remove(key);
            if (mineable && !placed) {
                double baseXp = miningSection.getDouble(blockType.name());
                xpAwarded[0] += baseXp * xpMultiplier;
            }
            double dropChance = mineable ? oreDropChance : debrisDropChance;
            if (Math.random() > dropChance) {
                return;
            }
            for (ItemStack drop : block.getDrops()) {
                int amount = (int) Math.max(1, Math.round(drop.getAmount() * (mineable ? oreYieldMultiplier : 1.0)));
                ItemStack clone = drop.clone();
                clone.setAmount(amount);
                block.getWorld().dropItemNaturally(block.getLocation(), clone);
            }
        });
        if (xpAwarded[0] > 0) {
            skillManager.addXp(player, SkillType.MINING, xpAwarded[0]);
        }
    }

    @EventHandler
    public void onBlastMiningDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!(event.getDamager() instanceof TNTPrimed primed)) {
            return;
        }
        if (!isBlastMining(primed)) {
            return;
        }
        Player owner = getBlastOwner(primed);
        if (owner == null || !owner.getUniqueId().equals(player.getUniqueId())) {
            return;
        }
        int level = dataManager.getOrCreateProfile(player.getUniqueId(), player.getName())
            .getSkillData(SkillType.MINING).getLevel();
        double reduction = getDemolitionReduction(level);
        if (reduction >= 1.0) {
            event.setCancelled(true);
            return;
        }
        if (reduction > 0) {
            event.setDamage(event.getDamage() * (1.0 - reduction));
        }
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

    private double getConfiguredXp(Material type, String section) {
        ConfigurationSection config = plugin.getConfig().getConfigurationSection(section);
        if (config == null || !config.contains(type.name())) {
            return 0;
        }
        return config.getDouble(type.name());
    }

    private void handleMiningDrops(Player player, Iterable<ItemStack> drops, org.bukkit.block.Block block) {
        ConfigurationSection miningXp = plugin.getConfig().getConfigurationSection("skillXp.mining");
        if (miningXp == null || !miningXp.contains(block.getType().name())) {
            return;
        }
        ConfigurationSection config = plugin.getConfig().getConfigurationSection("passives.mining.doubleDrops");
        if (config == null) {
            return;
        }
        if (!config.getBoolean("enabled", true)) {
            return;
        }
        ItemStack tool = player.getInventory().getItemInMainHand();
        boolean respectSilk = config.getBoolean("respectSilkTouch", true);
        if (!respectSilk && tool != null && tool.containsEnchantment(Enchantment.SILK_TOUCH)) {
            return;
        }
        PlayerProfile profile = dataManager.getOrCreateProfile(player.getUniqueId(), player.getName());
        int level = profile.getSkillData(SkillType.MINING).getLevel();
        boolean superBreaker = superBreakerActive.containsKey(player.getUniqueId());
        double chance = superBreaker
            ? passiveManager.scaledChance(level, "abilities.actives.mining.superBreaker.tripleDrops", 0.05, 0.0005, 0.6)
            : passiveManager.scaledChance(level, "passives.mining.doubleDrops", 0.08, 0.0006, 0.5);
        if (!passiveManager.rollChance(chance)) {
            return;
        }
        int extraSets = superBreaker ? 2 : 1;
        for (ItemStack drop : drops) {
            for (int i = 0; i < extraSets; i++) {
                ItemStack clone = drop.clone();
                block.getWorld().dropItemNaturally(block.getLocation(), clone);
            }
        }
    }

    private boolean isPickaxe(ItemStack item) {
        if (item == null) {
            return false;
        }
        return item.getType().name().endsWith("_PICKAXE");
    }

    private void activateSuperBreaker(Player player, int level) {
        String displayName = plugin.getConfig().getString("abilities.actives.mining.superBreaker.displayName", "Super Breaker");
        int baseDuration = plugin.getConfig().getInt("abilities.actives.mining.superBreaker.durationSeconds", 12);
        double perLevel = plugin.getConfig().getDouble("abilities.actives.mining.superBreaker.durationSecondsPerLevel", 0.0);
        int maxDuration = plugin.getConfig().getInt("abilities.actives.mining.superBreaker.maxDurationSeconds", 30);
        int duration = (int) Math.min(maxDuration, baseDuration + (level * perLevel));
        int hasteAmplifier = plugin.getConfig().getInt("abilities.actives.mining.superBreaker.hasteAmplifier", 1);
        abilityManager.activateWithoutCooldown(player, displayName);
        player.addPotionEffect(new PotionEffect(PotionEffectType.FAST_DIGGING, duration * 20, hasteAmplifier, true, false, true));
        ActiveAbility active = new ActiveAbility();
        active.task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> endSuperBreaker(player, false), duration * 20L);
        superBreakerActive.put(player.getUniqueId(), active);
    }

    private boolean isBlockedWorld(Player player) {
        return plugin.getConfig().getStringList("abilities.blockedWorlds")
            .stream()
            .anyMatch(world -> world.equalsIgnoreCase(player.getWorld().getName()));
    }

    private void endSuperBreaker(Player player, boolean cancelledEarly) {
        ActiveAbility active = superBreakerActive.remove(player.getUniqueId());
        if (active == null) {
            return;
        }
        if (active.task != null) {
            active.task.cancel();
        }
        player.removePotionEffect(PotionEffectType.FAST_DIGGING);
        String displayName = plugin.getConfig().getString("abilities.actives.mining.superBreaker.displayName", "Super Breaker");
        Msg.send(player, "abilities.ended", "%ability%", displayName);
        abilityManager.startCooldown(player, "superBreaker");
    }

    private boolean isPlacedBlock(String key) {
        if (!plugin.getConfig().getBoolean("xp.antiExploit.trackPlacedBlocks", true)) {
            return false;
        }
        Instant placedAt = placedBlocks.get(key);
        if (placedAt == null) {
            return false;
        }
        int expireMinutes = plugin.getConfig().getInt("xp.antiExploit.placedBlockExpireMinutes", 120);
        if (Duration.between(placedAt, Instant.now()).toMinutes() > expireMinutes) {
            placedBlocks.remove(key);
            return false;
        }
        return true;
    }

    private String findOwnedTnt(Player player, int range) {
        String found = null;
        double closest = Double.MAX_VALUE;
        for (Map.Entry<String, UUID> entry : placedTnt.entrySet()) {
            if (!entry.getValue().equals(player.getUniqueId())) {
                continue;
            }
            String key = entry.getKey();
            org.bukkit.Location location = parseLocation(key);
            if (location == null || !location.getWorld().equals(player.getWorld())) {
                continue;
            }
            double distance = location.distance(player.getLocation());
            if (distance <= range && distance < closest) {
                closest = distance;
                found = key;
            }
        }
        return found;
    }

    private void detonateTnt(Player player, String tntKey) {
        org.bukkit.Location location = parseLocation(tntKey);
        if (location == null) {
            return;
        }
        if (location.getBlock().getType() != Material.TNT) {
            placedTnt.remove(tntKey);
            return;
        }
        location.getBlock().setType(Material.AIR);
        TNTPrimed primed = location.getWorld().spawn(location.add(0.5, 0.0, 0.5), TNTPrimed.class);
        primed.setFuseTicks(1);
        primed.getPersistentDataContainer().set(blastMiningKey, PersistentDataType.INTEGER, 1);
        primed.getPersistentDataContainer().set(blastMiningOwnerKey, PersistentDataType.STRING, player.getUniqueId().toString());
        primed.setSource(player);
        placedTnt.remove(tntKey);
    }

    private String toKey(org.bukkit.Location location) {
        return location.getWorld().getName() + ":" + location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
    }

    private org.bukkit.Location parseLocation(String key) {
        String[] parts = key.split(":");
        if (parts.length != 4) {
            return null;
        }
        org.bukkit.World world = plugin.getServer().getWorld(parts[0]);
        if (world == null) {
            return null;
        }
        try {
            int x = Integer.parseInt(parts[1]);
            int y = Integer.parseInt(parts[2]);
            int z = Integer.parseInt(parts[3]);
            return new org.bukkit.Location(world, x, y, z);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private boolean isBlastMining(TNTPrimed primed) {
        return primed.getPersistentDataContainer().has(blastMiningKey, PersistentDataType.INTEGER);
    }

    private Player getBlastOwner(TNTPrimed primed) {
        String owner = primed.getPersistentDataContainer().get(blastMiningOwnerKey, PersistentDataType.STRING);
        if (owner == null) {
            return null;
        }
        return plugin.getServer().getPlayer(UUID.fromString(owner));
    }

    private int getBiggerBombsBonus(int level) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("passives.mining.biggerBombs");
        if (section == null || !section.getBoolean("enabled", true)) {
            return 0;
        }
        int bonus = 0;
        ConfigurationSection thresholds = section.getConfigurationSection("thresholds");
        if (thresholds == null) {
            return 0;
        }
        for (String key : thresholds.getKeys(false)) {
            int threshold = Integer.parseInt(key);
            if (level >= threshold) {
                bonus = Math.max(bonus, thresholds.getInt(key));
            }
        }
        return bonus;
    }

    private double getDemolitionReduction(int level) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("passives.mining.demolitionsExpertise");
        if (section == null || !section.getBoolean("enabled", true)) {
            return 0;
        }
        double reduction = 0;
        ConfigurationSection thresholds = section.getConfigurationSection("thresholds");
        if (thresholds == null) {
            return 0;
        }
        for (String key : thresholds.getKeys(false)) {
            int threshold = Integer.parseInt(key);
            if (level >= threshold) {
                reduction = Math.max(reduction, thresholds.getDouble(key));
            }
        }
        return reduction;
    }

    private double getBlastDropChance(Player player, String path) {
        PlayerProfile profile = dataManager.getOrCreateProfile(player.getUniqueId(), player.getName());
        int level = profile.getSkillData(SkillType.MINING).getLevel();
        return passiveManager.scaledChance(level, path, 0.3, 0.0005, 0.9);
    }

    private static class ActiveAbility {
        private org.bukkit.scheduler.BukkitTask task;
    }
}
