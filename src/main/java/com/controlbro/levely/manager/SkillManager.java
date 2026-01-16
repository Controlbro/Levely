package com.controlbro.levely.manager;

import com.controlbro.levely.LevelyPlugin;
import com.controlbro.levely.model.PlayerProfile;
import com.controlbro.levely.model.SkillData;
import com.controlbro.levely.model.SkillType;
import com.controlbro.levely.util.ChatUtil;
import java.util.Locale;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

public class SkillManager {
    private final LevelyPlugin plugin;
    private final DataManager dataManager;

    public SkillManager(LevelyPlugin plugin, DataManager dataManager) {
        this.plugin = plugin;
        this.dataManager = dataManager;
    }

    public void addXp(Player player, SkillType skill, double amount) {
        if (amount <= 0) {
            return;
        }
        PlayerProfile profile = dataManager.getOrCreateProfile(player.getUniqueId(), player.getName());
        SkillData data = profile.getSkillData(skill);
        data.addXp(applyGlobalMultipliers(skill, amount));
        levelUpIfNeeded(player, skill, data);
    }

    private double applyGlobalMultipliers(SkillType skill, double amount) {
        double global = plugin.getConfig().getDouble("xp.globalMultiplier", 1.0);
        double perSkill = plugin.getConfig().getDouble("xp.perSkillMultiplier." + skill.getKey(), 1.0);
        return amount * global * perSkill;
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
        String message = plugin.getConfig().getString("messages.chat.levelUp", "&a%skill% leveled up to %level%!");
        message = message.replace("%skill%", skill.getKey())
            .replace("%level%", String.valueOf(level));
        player.sendMessage(ChatUtil.color(message));
        String soundName = plugin.getConfig().getString("general.levelUp.sound", "ENTITY_PLAYER_LEVELUP");
        player.playSound(player.getLocation(), Sound.valueOf(soundName), 1.0f, 1.0f);
    }
}
