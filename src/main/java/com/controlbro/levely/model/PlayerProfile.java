package com.controlbro.levely.model;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

public class PlayerProfile {
    private final UUID uuid;
    private String name;
    private final Map<SkillType, SkillData> skills;
    private boolean partyChat;

    public PlayerProfile(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
        this.skills = new EnumMap<>(SkillType.class);
        for (SkillType skill : SkillType.values()) {
            skills.put(skill, new SkillData());
        }
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Map<SkillType, SkillData> getSkills() {
        return skills;
    }

    public SkillData getSkillData(SkillType skill) {
        return skills.get(skill);
    }

    public boolean isPartyChat() {
        return partyChat;
    }

    public void setPartyChat(boolean partyChat) {
        this.partyChat = partyChat;
    }
}
