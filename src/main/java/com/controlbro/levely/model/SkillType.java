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

    public String getDisplayName() {
        String[] parts = key.split("_");
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                builder.append(" ");
            }
            String part = parts[i];
            builder.append(part.substring(0, 1).toUpperCase(Locale.ROOT))
                .append(part.substring(1));
        }
        return builder.toString();
    }

    public String getCategoryKey() {
        return switch (this) {
            case MINING, WOODCUTTING, HERBALISM, EXCAVATION, FISHING -> "gathering";
            case UNARMED, ARCHERY, SWORDS, AXES, TAMING -> "combat";
            default -> "misc";
        };
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
