package com.controlbro.levely.command;

import com.controlbro.levely.LevelyPlugin;
import com.controlbro.levely.manager.AbilityManager;
import com.controlbro.levely.manager.DataManager;
import com.controlbro.levely.manager.PartyManager;
import com.controlbro.levely.manager.SkillManager;
import com.controlbro.levely.model.PlayerProfile;
import com.controlbro.levely.model.SkillData;
import com.controlbro.levely.model.SkillType;
import com.controlbro.levely.util.ChatUtil;
import java.util.ArrayList;
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
    private final AbilityManager abilityManager;

    public LevelyCommand(LevelyPlugin plugin, DataManager dataManager, SkillManager skillManager,
                         PartyManager partyManager, AbilityManager abilityManager) {
        this.plugin = plugin;
        this.dataManager = dataManager;
        this.skillManager = skillManager;
        this.partyManager = partyManager;
        this.abilityManager = abilityManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("info")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatUtil.color(plugin.getMessageManager().getString("errors.playerOnly")));
                return true;
            }
            PlayerProfile profile = dataManager.getOrCreateProfile(player.getUniqueId(), player.getName());
            int power = profile.getSkills().values().stream().mapToInt(SkillData::getLevel).sum();
            sender.sendMessage(ChatUtil.color("&ePower Level: &6" + power));
            sender.sendMessage(ChatUtil.color("&eParty: &6" + partyManager.getParty(player.getUniqueId())
                .map(party -> party.getName() + " (Lv " + party.getLevel() + ")")
                .orElse("None")));
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "skills" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ChatUtil.color(plugin.getMessageManager().getString("errors.playerOnly")));
                    return true;
                }
                PlayerProfile profile = dataManager.getOrCreateProfile(player.getUniqueId(), player.getName());
                sender.sendMessage(ChatUtil.color("&6Skills:"));
                for (SkillType skill : SkillType.values()) {
                    SkillData data = profile.getSkillData(skill);
                    sender.sendMessage(ChatUtil.color("&e- " + skill.getKey() + ": &6" + data.getLevel()));
                }
                return true;
            }
            case "skill" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ChatUtil.color(plugin.getMessageManager().getString("errors.playerOnly")));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(ChatUtil.color("&cUsage: /levely skill <skill>"));
                    return true;
                }
                Optional<SkillType> skillType = SkillType.fromString(args[1]);
                if (skillType.isEmpty()) {
                    sender.sendMessage(ChatUtil.color(plugin.getMessageManager().getString("errors.invalidSkill")));
                    return true;
                }
                PlayerProfile profile = dataManager.getOrCreateProfile(player.getUniqueId(), player.getName());
                SkillData data = profile.getSkillData(skillType.get());
                double required = skillManager.xpRequired(data.getLevel());
                sender.sendMessage(ChatUtil.color("&6" + skillType.get().getKey() + " &7Level " + data.getLevel()));
                sender.sendMessage(ChatUtil.color("&eXP: &6" + data.getXp() + "&7/&6" + required));
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
                    sender.sendMessage(ChatUtil.color(plugin.getMessageManager().getString("errors.noPermission")));
                    return true;
                }
                plugin.reloadConfig();
                plugin.getMessageManager().reload();
                sender.sendMessage(ChatUtil.color(plugin.getMessageManager().getString("admin.reloadComplete")));
                return true;
            }
            case "admin" -> {
                if (!sender.hasPermission("levely.admin.command")) {
                    sender.sendMessage(ChatUtil.color(plugin.getMessageManager().getString("errors.noPermission")));
                    return true;
                }
                return handleAdmin(sender, args);
            }
            default -> {
                sender.sendMessage(ChatUtil.color("&cUnknown subcommand."));
                return true;
            }
        }
    }

    private boolean handleAdmin(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatUtil.color("&cUsage: /levely admin <action>"));
            return true;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "addxp", "setxp", "addlevel", "setlevel" -> {
                if (args.length < 5) {
                    sender.sendMessage(ChatUtil.color("&cUsage: /levely admin " + action + " <player> <skill> <amount>"));
                    return true;
                }
                Player target = Bukkit.getPlayer(args[2]);
                if (target == null) {
                    sender.sendMessage(ChatUtil.color(plugin.getMessageManager().getString("errors.invalidPlayer")));
                    return true;
                }
                Optional<SkillType> skillType = SkillType.fromString(args[3]);
                if (skillType.isEmpty()) {
                    sender.sendMessage(ChatUtil.color(plugin.getMessageManager().getString("errors.invalidSkill")));
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
                sender.sendMessage(ChatUtil.color("&aUpdated " + target.getName() + " " + skillType.get().getKey()));
                return true;
            }
            case "reset" -> {
                if (args.length < 3) {
                    sender.sendMessage(ChatUtil.color("&cUsage: /levely admin reset <player> [skill|all]"));
                    return true;
                }
                Player target = Bukkit.getPlayer(args[2]);
                if (target == null) {
                    sender.sendMessage(ChatUtil.color(plugin.getMessageManager().getString("errors.invalidPlayer")));
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
                        sender.sendMessage(ChatUtil.color(plugin.getMessageManager().getString("errors.invalidSkill")));
                        return true;
                    }
                    SkillData data = profile.getSkillData(skillType.get());
                    data.setLevel(0);
                    data.setXp(0);
                }
                sender.sendMessage(ChatUtil.color("&aReset data for " + target.getName()));
                return true;
            }
            default -> {
                sender.sendMessage(ChatUtil.color("&cUnknown admin action."));
                return true;
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("info", "skills", "skill", "top", "party", "reload", "admin");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("skill")) {
            return Arrays.stream(SkillType.values()).map(SkillType::getKey).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("admin")) {
            return Arrays.asList("addxp", "setxp", "addlevel", "setlevel", "reset", "powerlevel", "party", "debug");
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("admin") &&
            Arrays.asList("addxp", "setxp", "addlevel", "setlevel").contains(args[1].toLowerCase(Locale.ROOT))) {
            return Arrays.stream(SkillType.values()).map(SkillType::getKey).toList();
        }
        return Collections.emptyList();
    }
}
