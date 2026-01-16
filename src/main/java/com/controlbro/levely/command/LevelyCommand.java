package com.controlbro.levely.command;

import com.controlbro.levely.LevelyPlugin;
import com.controlbro.levely.manager.DataManager;
import com.controlbro.levely.manager.PartyManager;
import com.controlbro.levely.manager.SkillManager;
import com.controlbro.levely.manager.XpEventManager;
import com.controlbro.levely.model.PlayerProfile;
import com.controlbro.levely.model.SkillData;
import com.controlbro.levely.model.SkillType;
import com.controlbro.levely.util.Msg;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public class LevelyCommand implements CommandExecutor, TabCompleter {
    private final LevelyPlugin plugin;
    private final DataManager dataManager;
    private final SkillManager skillManager;
    private final PartyManager partyManager;
    private final XpEventManager xpEventManager;

    public LevelyCommand(LevelyPlugin plugin, DataManager dataManager, SkillManager skillManager,
                         PartyManager partyManager) {
        this.plugin = plugin;
        this.dataManager = dataManager;
        this.skillManager = skillManager;
        this.partyManager = partyManager;
        this.xpEventManager = plugin.getXpEventManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("info")) {
            if (!(sender instanceof Player player)) {
                Msg.send(sender, "errors.playerOnly");
                return true;
            }
            PlayerProfile profile = dataManager.getOrCreateProfile(player.getUniqueId(), player.getName());
            int power = profile.getSkills().values().stream().mapToInt(SkillData::getLevel).sum();
            Msg.send(sender, "player.powerLevel", "%power%", String.valueOf(power));
            Msg.send(sender, "player.partyStatus", "%party%", partyManager.getParty(player.getUniqueId())
                .map(party -> party.getName() + " (Lv " + party.getLevel() + ")")
                .orElse("None"));
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "skills" -> {
                if (!(sender instanceof Player player)) {
                    Msg.send(sender, "errors.playerOnly");
                    return true;
                }
                PlayerProfile profile = dataManager.getOrCreateProfile(player.getUniqueId(), player.getName());
                Msg.send(sender, "skills.listHeader");
                for (SkillType skill : SkillType.values()) {
                    SkillData data = profile.getSkillData(skill);
                    Msg.send(sender, "skills.listLine", "%skill%", skill.getDisplayName(),
                        "%level%", String.valueOf(data.getLevel()));
                }
                return true;
            }
            case "skill" -> {
                if (!(sender instanceof Player player)) {
                    Msg.send(sender, "errors.playerOnly");
                    return true;
                }
                if (args.length < 2) {
                    Msg.send(sender, "skills.usage");
                    return true;
                }
                Optional<SkillType> skillType = SkillType.fromString(args[1]);
                if (skillType.isEmpty()) {
                    Msg.send(sender, "errors.invalidSkill");
                    return true;
                }
                PlayerProfile profile = dataManager.getOrCreateProfile(player.getUniqueId(), player.getName());
                SkillData data = profile.getSkillData(skillType.get());
                double required = skillManager.xpRequired(data.getLevel());
                Msg.send(sender, "skills.detailHeader", "%skill%", skillType.get().getDisplayName(),
                    "%level%", String.valueOf(data.getLevel()));
                Msg.send(sender, "skills.detailXp", "%xp%", String.valueOf((int) data.getXp()),
                    "%required%", String.valueOf((int) required));
                return true;
            }
            case "party" -> {
                if (sender instanceof Player player) {
                    player.performCommand("party");
                }
                return true;
            }
            case "reload" -> {
                if (!sender.hasPermission("levely.admin.reload")) {
                    Msg.send(sender, "errors.noPermission");
                    return true;
                }
                plugin.reloadConfig();
                plugin.getMessageManager().reload();
                Msg.send(sender, "admin.reloadComplete");
                return true;
            }
            case "event" -> {
                if (!sender.hasPermission("levely.admin.event.*")) {
                    Msg.send(sender, "errors.noPermission");
                    return true;
                }
                return handleEvent(sender, args);
            }
            case "admin" -> {
                if (!sender.hasPermission("levely.admin.command")) {
                    Msg.send(sender, "errors.noPermission");
                    return true;
                }
                return handleAdmin(sender, args);
            }
            default -> {
                Msg.send(sender, "errors.unknownCommand");
                return true;
            }
        }
    }

    private boolean handleAdmin(CommandSender sender, String[] args) {
        if (args.length < 2) {
            Msg.send(sender, "admin.usage");
            return true;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "addxp", "setxp", "addlevel", "setlevel" -> {
                if (args.length < 5) {
                    Msg.send(sender, "admin.usageAction", "%action%", action);
                    return true;
                }
                Player target = Bukkit.getPlayer(args[2]);
                if (target == null) {
                    Msg.send(sender, "errors.invalidPlayer");
                    return true;
                }
                Optional<SkillType> skillType = SkillType.fromString(args[3]);
                if (skillType.isEmpty()) {
                    Msg.send(sender, "errors.invalidSkill");
                    return true;
                }
                double amount = Double.parseDouble(args[4]);
                PlayerProfile profile = dataManager.getOrCreateProfile(target.getUniqueId(), target.getName());
                SkillData data = profile.getSkillData(skillType.get());
                switch (action) {
                    case "addxp" -> data.addXp(amount);
                    case "setxp" -> data.setXp(amount);
                    case "addlevel" -> data.setLevel(data.getLevel() + (int) amount);
                    case "setlevel" -> data.setLevel((int) amount);
                    default -> {
                    }
                }
                Msg.send(sender, "admin.updatedSkill", "%player%", target.getName(),
                    "%skill%", skillType.get().getDisplayName());
                return true;
            }
            case "reset" -> {
                if (args.length < 3) {
                    Msg.send(sender, "admin.usageReset");
                    return true;
                }
                Player target = Bukkit.getPlayer(args[2]);
                if (target == null) {
                    Msg.send(sender, "errors.invalidPlayer");
                    return true;
                }
                PlayerProfile profile = dataManager.getOrCreateProfile(target.getUniqueId(), target.getName());
                if (args.length == 3 || args[3].equalsIgnoreCase("all")) {
                    profile.getSkills().values().forEach(skill -> {
                        skill.setLevel(0);
                        skill.setXp(0);
                    });
                } else {
                    Optional<SkillType> skillType = SkillType.fromString(args[3]);
                    if (skillType.isEmpty()) {
                        Msg.send(sender, "errors.invalidSkill");
                        return true;
                    }
                    SkillData data = profile.getSkillData(skillType.get());
                    data.setLevel(0);
                    data.setXp(0);
                }
                Msg.send(sender, "admin.reset", "%player%", target.getName());
                return true;
            }
            default -> {
                Msg.send(sender, "admin.unknownAction");
                return true;
            }
        }
    }

    private boolean handleEvent(CommandSender sender, String[] args) {
        if (args.length < 2) {
            Msg.send(sender, "events.usage");
            return true;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "info" -> {
                if (!sender.hasPermission("levely.admin.event.info")) {
                    Msg.send(sender, "errors.noPermission");
                    return true;
                }
                sendEventInfo(sender);
                return true;
            }
            case "global" -> {
                if (!sender.hasPermission("levely.admin.event.global")) {
                    Msg.send(sender, "errors.noPermission");
                    return true;
                }
                if (args.length < 3) {
                    Msg.send(sender, "events.usageGlobal");
                    return true;
                }
                boolean enabled = args[2].equalsIgnoreCase("on");
                xpEventManager.setGlobalMultiplier(enabled ? 2.0 : 1.0, null, "double xp");
                Msg.send(sender, enabled ? "events.globalEnabled" : "events.globalDisabled");
                return true;
            }
            case "skill" -> {
                if (!sender.hasPermission("levely.admin.event.skill")) {
                    Msg.send(sender, "errors.noPermission");
                    return true;
                }
                if (args.length < 4) {
                    Msg.send(sender, "events.usageSkill");
                    return true;
                }
                Optional<SkillType> skill = SkillType.fromString(args[2]);
                if (skill.isEmpty()) {
                    Msg.send(sender, "errors.invalidSkill");
                    return true;
                }
                boolean enabled = args[3].equalsIgnoreCase("on");
                xpEventManager.setSkillMultiplier(skill.get(), enabled ? 2.0 : 1.0, null,
                    "double " + skill.get().getKey() + " xp");
                Msg.send(sender, enabled ? "events.skillEnabled" : "events.skillDisabled",
                    "%skill%", skill.get().getDisplayName());
                return true;
            }
            case "setglobal" -> {
                if (!sender.hasPermission("levely.admin.event.global")) {
                    Msg.send(sender, "errors.noPermission");
                    return true;
                }
                if (args.length < 3) {
                    Msg.send(sender, "events.usageSetGlobal");
                    return true;
                }
                double multiplier = Double.parseDouble(args[2]);
                Duration duration = parseDuration(args.length > 3 ? args[3] : null);
                xpEventManager.setGlobalMultiplier(multiplier, duration, "custom global");
                Msg.send(sender, "events.globalSet", "%multiplier%", String.valueOf(multiplier),
                    "%duration%", formatDuration(duration));
                return true;
            }
            case "setskill" -> {
                if (!sender.hasPermission("levely.admin.event.skill")) {
                    Msg.send(sender, "errors.noPermission");
                    return true;
                }
                if (args.length < 4) {
                    Msg.send(sender, "events.usageSetSkill");
                    return true;
                }
                Optional<SkillType> skill = SkillType.fromString(args[2]);
                if (skill.isEmpty()) {
                    Msg.send(sender, "errors.invalidSkill");
                    return true;
                }
                double multiplier = Double.parseDouble(args[3]);
                Duration duration = parseDuration(args.length > 4 ? args[4] : null);
                xpEventManager.setSkillMultiplier(skill.get(), multiplier, duration, "custom " + skill.get().getKey());
                Msg.send(sender, "events.skillSet", "%skill%", skill.get().getDisplayName(),
                    "%multiplier%", String.valueOf(multiplier), "%duration%", formatDuration(duration));
                return true;
            }
            case "clear" -> {
                if (!sender.hasPermission("levely.admin.event.clear")) {
                    Msg.send(sender, "errors.noPermission");
                    return true;
                }
                xpEventManager.clearAll();
                Msg.send(sender, "events.cleared");
                return true;
            }
            default -> {
                Msg.send(sender, "events.usage");
                return true;
            }
        }
    }

    private void sendEventInfo(CommandSender sender) {
        Msg.send(sender, "events.infoHeader");
        Msg.send(sender, "events.infoGlobal", "%multiplier%", formatMultiplier(xpEventManager.getGlobalMultiplier()),
            "%remaining%", formatInstant(xpEventManager.getGlobalEndsAt().orElse(null)));
        for (SkillType skill : SkillType.values()) {
            double multiplier = xpEventManager.getSkillMultiplier(skill);
            if (multiplier != 1.0) {
                Msg.send(sender, "events.infoSkill", "%skill%", skill.getDisplayName(),
                    "%multiplier%", formatMultiplier(multiplier),
                    "%remaining%", formatInstant(xpEventManager.getSkillEndsAt(skill).orElse(null)));
            }
        }
    }

    private String formatMultiplier(double multiplier) {
        return String.format("%.2f", multiplier);
    }

    private Duration parseDuration(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String lower = raw.toLowerCase(Locale.ROOT);
        if (lower.endsWith("m")) {
            return Duration.ofMinutes(Long.parseLong(lower.substring(0, lower.length() - 1)));
        }
        if (lower.endsWith("h")) {
            return Duration.ofHours(Long.parseLong(lower.substring(0, lower.length() - 1)));
        }
        if (lower.endsWith("d")) {
            return Duration.ofDays(Long.parseLong(lower.substring(0, lower.length() - 1)));
        }
        return Duration.ofSeconds(Long.parseLong(lower));
    }

    private String formatDuration(Duration duration) {
        if (duration == null) {
            return plugin.getMessageManager().getString("events.durationNone");
        }
        long seconds = duration.getSeconds();
        if (seconds % 3600 == 0) {
            return (seconds / 3600) + "h";
        }
        if (seconds % 60 == 0) {
            return (seconds / 60) + "m";
        }
        return seconds + "s";
    }

    private String formatInstant(Instant endsAt) {
        if (endsAt == null) {
            return plugin.getMessageManager().getString("events.durationNone");
        }
        long seconds = Duration.between(Instant.now(), endsAt).getSeconds();
        if (seconds <= 0) {
            return plugin.getMessageManager().getString("events.durationNone");
        }
        if (seconds >= 3600) {
            return (seconds / 3600) + "h";
        }
        if (seconds >= 60) {
            return (seconds / 60) + "m";
        }
        return seconds + "s";
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("info", "skills", "skill", "top", "party", "reload", "admin", "event");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("skill")) {
            return Arrays.stream(SkillType.values()).map(SkillType::getKey).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("admin")) {
            return Arrays.asList("addxp", "setxp", "addlevel", "setlevel", "reset", "powerlevel", "party", "debug");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("event")) {
            return Arrays.asList("info", "global", "skill", "setglobal", "setskill", "clear");
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("admin") &&
            Arrays.asList("addxp", "setxp", "addlevel", "setlevel").contains(args[1].toLowerCase(Locale.ROOT))) {
            return Arrays.stream(SkillType.values()).map(SkillType::getKey).toList();
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("event") && args[1].equalsIgnoreCase("skill")) {
            return Arrays.stream(SkillType.values()).map(SkillType::getKey).toList();
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("event") && args[1].equalsIgnoreCase("setskill")) {
            return Arrays.stream(SkillType.values()).map(SkillType::getKey).toList();
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("event") && args[1].equalsIgnoreCase("skill")) {
            return Arrays.asList("on", "off");
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("event") && args[1].equalsIgnoreCase("global")) {
            return Arrays.asList("on", "off");
        }
        return Collections.emptyList();
    }
}
