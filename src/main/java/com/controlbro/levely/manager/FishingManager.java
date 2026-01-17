package com.controlbro.levely.manager;

import com.controlbro.levely.LevelyPlugin;
import com.controlbro.levely.model.SkillData;
import com.controlbro.levely.model.SkillType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class FishingManager {
    private final LevelyPlugin plugin;
    private final SkillsConfigManager skillsConfigManager;
    private final PassiveManager passiveManager;
    private final DataManager dataManager;
    private final Random random;
    private List<TierRank> tierRanks;
    private final Map<TreasureTier, LootTable<LootItem>> treasureTables;

    public FishingManager(LevelyPlugin plugin, SkillsConfigManager skillsConfigManager,
                          PassiveManager passiveManager, DataManager dataManager) {
        this.plugin = plugin;
        this.skillsConfigManager = skillsConfigManager;
        this.passiveManager = passiveManager;
        this.dataManager = dataManager;
        this.random = new Random();
        this.treasureTables = new EnumMap<>(TreasureTier.class);
        reload();
    }

    public void reload() {
        this.tierRanks = loadTierRanks();
        this.treasureTables.clear();
        loadTreasureTables();
    }

    public Optional<ItemStack> rollTreasure(Player player) {
        int level = dataManager.getOrCreateProfile(player.getUniqueId(), player.getName())
            .getSkillData(SkillType.FISHING).getLevel();
        int required = getTreasureRequiredLevel();
        if (level < required) {
            return Optional.empty();
        }
        Optional<TreasureTier> tier = rollTreasureTier(level);
        if (tier.isEmpty()) {
            return Optional.empty();
        }
        LootTable<LootItem> table = treasureTables.get(tier.get());
        if (table == null || table.isEmpty()) {
            return Optional.empty();
        }
        LootItem item = table.roll(random).orElse(null);
        if (item == null) {
            return Optional.empty();
        }
        ItemStack stack = new ItemStack(item.material(), item.amount());
        if (canEnchantTreasure(level)) {
            applyRandomEnchantment(stack);
        }
        return Optional.of(stack);
    }

    public boolean isIceFishingEnabled(Player player) {
        int level = dataManager.getOrCreateProfile(player.getUniqueId(), player.getName())
            .getSkillData(SkillType.FISHING).getLevel();
        int required = plugin.getConfig().getInt("passives.fishing.iceFishing.requiredLevel", 0);
        return level >= required;
    }

    public int getTreasureRequiredLevel() {
        return skillsConfigManager.getConfig().getInt("fishing.treasureHunter.requiredLevel", 10);
    }

    private Optional<TreasureTier> rollTreasureTier(int level) {
        TierRank rank = tierRanks.stream()
            .filter(entry -> level >= entry.level())
            .max(Comparator.comparingInt(TierRank::level))
            .orElse(null);
        if (rank == null) {
            return Optional.empty();
        }
        LootTable<TreasureTier> table = new LootTable<>();
        for (Map.Entry<TreasureTier, Double> entry : rank.chances().entrySet()) {
            table.add(entry.getKey(), entry.getValue());
        }
        return table.roll(random);
    }

    private boolean canEnchantTreasure(int level) {
        if (!plugin.getConfig().getBoolean("passives.fishing.magicHunter.enabled", true)) {
            return false;
        }
        int required = plugin.getConfig().getInt("passives.fishing.magicHunter.requiredLevel", 20);
        if (level < required) {
            return false;
        }
        double chance = passiveManager.scaledChance(level, "passives.fishing.magicHunter", 0.02, 0.0004, 0.35);
        return passiveManager.rollChance(chance);
    }

    private void applyRandomEnchantment(ItemStack stack) {
        List<Enchantment> candidates = new ArrayList<>();
        for (Enchantment enchantment : Enchantment.values()) {
            if (enchantment.canEnchantItem(stack)) {
                candidates.add(enchantment);
            }
        }
        if (candidates.isEmpty()) {
            return;
        }
        Enchantment selected = candidates.get(random.nextInt(candidates.size()));
        int level = Math.max(1, random.nextInt(selected.getMaxLevel()) + 1);
        stack.addUnsafeEnchantment(selected, level);
    }

    private List<TierRank> loadTierRanks() {
        List<TierRank> ranks = new ArrayList<>();
        YamlConfiguration config = skillsConfigManager.getConfig();
        ConfigurationSection section = config.getConfigurationSection("fishing.treasureHunter.tiers");
        if (section == null) {
            return ranks;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection rankSection = section.getConfigurationSection(key);
            if (rankSection == null) {
                continue;
            }
            int level = rankSection.getInt("level", 0);
            ConfigurationSection chanceSection = rankSection.getConfigurationSection("chance");
            Map<TreasureTier, Double> chances = new LinkedHashMap<>();
            if (chanceSection != null) {
                for (TreasureTier tier : TreasureTier.values()) {
                    if (chanceSection.contains(tier.getKey())) {
                        chances.put(tier, chanceSection.getDouble(tier.getKey()));
                    }
                }
            }
            ranks.add(new TierRank(level, chances));
        }
        return ranks;
    }

    private void loadTreasureTables() {
        YamlConfiguration config = skillsConfigManager.getConfig();
        ConfigurationSection lootSection = config.getConfigurationSection("fishing.treasureHunter.loot");
        if (lootSection == null) {
            return;
        }
        for (TreasureTier tier : TreasureTier.values()) {
            ConfigurationSection tierSection = lootSection.getConfigurationSection(tier.getKey());
            if (tierSection == null) {
                continue;
            }
            LootTable<LootItem> table = new LootTable<>();
            for (String key : tierSection.getKeys(false)) {
                ConfigurationSection itemSection = tierSection.getConfigurationSection(key);
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
                    weight = tierSection.getDouble(key, 1.0);
                }
                table.add(new LootItem(material, amount), weight);
            }
            if (!table.isEmpty()) {
                treasureTables.put(tier, table);
            }
        }
    }

    public record LootItem(Material material, int amount) {
    }

    public record TierRank(int level, Map<TreasureTier, Double> chances) {
    }

    public enum TreasureTier {
        COMMON("Common"),
        UNCOMMON("Uncommon"),
        RARE("Rare"),
        EPIC("Epic"),
        LEGENDARY("Legendary"),
        MYTHIC("Mythic");

        private final String key;

        TreasureTier(String key) {
            this.key = key;
        }

        public String getKey() {
            return key;
        }
    }
}
