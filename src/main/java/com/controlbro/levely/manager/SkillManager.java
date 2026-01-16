package com.controlbro.levely.manager;

import com.controlbro.levely.LevelyPlugin;
import com.controlbro.levely.model.PlayerProfile;
import com.controlbro.levely.model.SkillData;
import com.controlbro.levely.model.SkillType;
import com.controlbro.levely.util.Msg;
import java.util.Locale;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

public class SkillManager {
    private final LevelyPlugin plugin;
    private final DataManager dataManager;
    private final XpEventManager xpEventManager;
    private final XpBossBarManager xpBossBarManager;
    private final PartyManager partyManager;

    public SkillManager(LevelyPlugin plugin, DataManager dataManager, XpEventManager xpEventManager,
                        XpBossBarManager xpBossBarManager, PartyManager partyManager) {
        this.plugin = plugin;
        this.dataManager = dataManager;
        this.xpEventManager = xpEventManager;
        this.xpBossBarManager = xpBossBarManager;
        this.partyManager = partyManager;
    }

    public void addXp(Player player, SkillType skill, double amount) {
        addXp(player, skill, amount, 1.0);
    }

    public void addXp(Player player, SkillType skill, double amount, double otherMultiplier) {
        if (amount <= 0) {
            return;
        }
        PlayerProfile profile = dataManager.getOrCreateProfile(player.getUniqueId(), player.getName());
        SkillData data = profile.getSkillData(skill);
        double finalXp = applyGlobalMultipliers(player, skill, amount, otherMultiplier);
        data.addXp(finalXp);
        levelUpIfNeeded(player, skill, data);
        double required = xpRequired(data.getLevel());
        xpBossBarManager.showXp(player, skill, data.getXp(), required, finalXp);
    }

    private double applyGlobalMultipliers(Player player, SkillType skill, double amount, double otherMultiplier) {
        double global = plugin.getConfig().getDouble("xp.globalMultiplier", 1.0);
        double perSkill = plugin.getConfig().getDouble("xp.perSkillMultiplier." + skill.getKey(), 1.0);
        double eventGlobal = xpEventManager.getGlobalMultiplier();
        double eventSkill = xpEventManager.getSkillMultiplier(skill);
        double category = xpEventManager.getCategoryMultiplier(skill);
        double partyBonus = getPartyBonus(player);
        return amount * global * perSkill * eventGlobal * eventSkill * category * partyBonus * otherMultiplier;
    }

    private double getPartyBonus(Player player) {
        if (partyManager == null) {
            return 1.0;
        }
        return plugin.getConfig().getDouble("party.xpBonusMultiplier", 1.0);
    }

    private void levelUpIfNeeded(Player player, SkillType skill, SkillData data) {
        double required = xpRequired(data.getLevel());
        while (data.getXp() >= required) {
            data.addXp(-required);
            data.setLevel(data.getLevel() + 1);
            sendLevelUp(player, skill, data.getLevel());
            required = xpRequired(data.getLevel());
        }
    }

    public double xpRequired(int level) {
        String type = plugin.getConfig().getString("xp.formula.type", "MCMO_LIKE").toUpperCase(Locale.ROOT);
        double base = plugin.getConfig().getDouble("xp.formula.base", 100);
        double multiplier = plugin.getConfig().getDouble("xp.formula.multiplier", 1.08);
        double exponent = plugin.getConfig().getDouble("xp.formula.exponent", 1.2);
        return switch (type) {
            case "LINEAR" -> base + (level * multiplier * 100);
            case "EXPONENTIAL" -> base * Math.pow(multiplier, level + exponent);
            default -> base * Math.pow(level + 1, exponent) * multiplier;
        };
    }

    private void sendLevelUp(Player player, SkillType skill, int level) {
        if (!plugin.getConfig().getBoolean("general.levelUp.chat", true)) {
            return;
        }
        Msg.send(player, "skills.levelUp", "%skill%", skill.getDisplayName(), "%level%", String.valueOf(level));
        xpBossBarManager.showLevelUp(player, skill, level);
        String soundName = plugin.getConfig().getString("general.levelUp.sound", "ENTITY_PLAYER_LEVELUP");
        player.playSound(player.getLocation(), Sound.valueOf(soundName), 1.0f, 1.0f);
    }
}
