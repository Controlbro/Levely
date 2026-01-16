package com.controlbro.levely.manager;

import com.controlbro.levely.LevelyPlugin;
import com.controlbro.levely.model.SkillType;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

public class XpEventManager {
    private final LevelyPlugin plugin;
    private double activeGlobalMultiplier;
    private Instant globalEndsAt;
    private String activeEventName;
    private final Map<SkillType, Double> skillMultipliers;
    private final Map<SkillType, Instant> skillEndsAt;

    public XpEventManager(LevelyPlugin plugin) {
        this.plugin = plugin;
        this.activeGlobalMultiplier = 1.0;
        this.skillMultipliers = new EnumMap<>(SkillType.class);
        this.skillEndsAt = new EnumMap<>(SkillType.class);
        for (SkillType type : SkillType.values()) {
            skillMultipliers.put(type, 1.0);
        }
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("events.enabled", true);
    }

    public double getGlobalMultiplier() {
        if (!isEnabled()) {
            return 1.0;
        }
        if (isExpired(globalEndsAt)) {
            activeGlobalMultiplier = 1.0;
            globalEndsAt = null;
            activeEventName = null;
        }
        return activeGlobalMultiplier;
    }

    public double getSkillMultiplier(SkillType skill) {
        if (!isEnabled()) {
            return 1.0;
        }
        Instant endsAt = skillEndsAt.get(skill);
        if (isExpired(endsAt)) {
            skillMultipliers.put(skill, 1.0);
            skillEndsAt.remove(skill);
        }
        double configMultiplier = plugin.getConfig().getDouble("events.skills." + skill.getKey() + ".multiplier", 1.0);
        return configMultiplier * skillMultipliers.getOrDefault(skill, 1.0);
    }

    public double getCategoryMultiplier(SkillType skill) {
        if (!isEnabled()) {
            return 1.0;
        }
        String category = skill.getCategoryKey();
        return plugin.getConfig().getDouble("events.categories." + category + ".multiplier", 1.0);
    }

    public void setGlobalMultiplier(double multiplier, Duration duration, String name) {
        if (!isEnabled()) {
            return;
        }
        this.activeGlobalMultiplier = multiplier;
        this.activeEventName = name;
        this.globalEndsAt = duration != null ? Instant.now().plus(duration) : null;
    }

    public void setSkillMultiplier(SkillType skill, double multiplier, Duration duration, String name) {
        if (!isEnabled()) {
            return;
        }
        skillMultipliers.put(skill, multiplier);
        if (duration != null) {
            skillEndsAt.put(skill, Instant.now().plus(duration));
        } else {
            skillEndsAt.remove(skill);
        }
        if (name != null && !name.isBlank()) {
            this.activeEventName = name;
        }
    }

    public void clearAll() {
        activeGlobalMultiplier = 1.0;
        globalEndsAt = null;
        activeEventName = null;
        skillEndsAt.clear();
        for (SkillType type : SkillType.values()) {
            skillMultipliers.put(type, 1.0);
        }
    }

    public Optional<String> getActiveEventName() {
        if (activeEventName == null || activeEventName.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(activeEventName);
    }

    public Optional<Instant> getGlobalEndsAt() {
        return Optional.ofNullable(globalEndsAt);
    }

    public Optional<Instant> getSkillEndsAt(SkillType skill) {
        return Optional.ofNullable(skillEndsAt.get(skill));
    }

    public boolean hasActiveMultipliers() {
        if (!isEnabled()) {
            return false;
        }
        if (getGlobalMultiplier() != 1.0) {
            return true;
        }
        for (SkillType skill : SkillType.values()) {
            if (getSkillMultiplier(skill) != 1.0) {
                return true;
            }
        }
        return false;
    }

    private boolean isExpired(Instant endsAt) {
        return endsAt != null && Instant.now().isAfter(endsAt);
    }
}
