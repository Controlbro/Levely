package com.controlbro.levely.manager;

import com.controlbro.levely.LevelyPlugin;
import com.controlbro.levely.model.PlayerProfile;
import com.controlbro.levely.model.SkillType;
import com.controlbro.levely.util.ChatUtil;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

public class AbilityManager {
    private final LevelyPlugin plugin;
    private final DataManager dataManager;
    private final SkillManager skillManager;
    private final Map<UUID, Map<String, Instant>> cooldowns;

    public AbilityManager(LevelyPlugin plugin, DataManager dataManager, SkillManager skillManager) {
        this.plugin = plugin;
        this.dataManager = dataManager;
        this.skillManager = skillManager;
        this.cooldowns = new HashMap<>();
    }

    public boolean canActivate(Player player, String abilityKey, int cooldownSeconds) {
        if (player.hasPermission(plugin.getConfig().getString("general.cooldownBypassPermission", ""))) {
            return true;
        }
        Instant last = cooldowns
            .computeIfAbsent(player.getUniqueId(), key -> new HashMap<>())
            .get(abilityKey);
        if (last == null) {
            return true;
        }
        return Duration.between(last, Instant.now()).getSeconds() >= cooldownSeconds;
    }

    public long cooldownRemaining(Player player, String abilityKey, int cooldownSeconds) {
        Instant last = cooldowns
            .computeIfAbsent(player.getUniqueId(), key -> new HashMap<>())
            .get(abilityKey);
        if (last == null) {
            return 0;
        }
        long elapsed = Duration.between(last, Instant.now()).getSeconds();
        return Math.max(0, cooldownSeconds - elapsed);
    }

    public void activate(Player player, SkillType skill, String abilityKey, String displayName, int cooldownSeconds) {
        cooldowns.computeIfAbsent(player.getUniqueId(), key -> new HashMap<>())
            .put(abilityKey, Instant.now());
        String message = plugin.getMessageManager().getString("abilities.activated")
            .replace("%ability%", displayName);
        player.sendMessage(ChatUtil.color(message));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.2f);
    }

    public PlayerProfile getProfile(Player player) {
        return dataManager.getOrCreateProfile(player.getUniqueId(), player.getName());
    }
}
