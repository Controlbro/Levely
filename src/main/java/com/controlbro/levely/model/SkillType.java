package com.controlbro.levely.model;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public enum SkillType {
    MINING("mining", "mine"),
    WOODCUTTING("woodcutting", "woodcut", "woodcutting"),
    HERBALISM("herbalism", "herb"),
    EXCAVATION("excavation", "excavate"),
    FISHING("fishing", "fish"),
    UNARMED("unarmed", "fists"),
    ARCHERY("archery", "bow"),
    SWORDS("swords", "sword"),
    AXES("axes", "axe"),
    TAMING("taming", "tame"),
    REPAIR("repair", "rep"),
    ACROBATICS("acrobatics", "acro"),
    ALCHEMY("alchemy", "brew"),
    SALVAGE("salvage", "sal"),
    SMELTING("smelting", "smelt");

    private final String key;
    private final String[] aliases;

    SkillType(String key, String... aliases) {
        this.key = key;
        this.aliases = aliases;
    }

    public String getKey() {
        return key;
    }

    public static Optional<SkillType> fromString(String input) {
        if (input == null) {
            return Optional.empty();
        }
        String normalized = input.toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
            .filter(type -> type.key.equalsIgnoreCase(normalized)
                || Arrays.stream(type.aliases).anyMatch(alias -> alias.equalsIgnoreCase(normalized)))
            .findFirst();
    }
}
