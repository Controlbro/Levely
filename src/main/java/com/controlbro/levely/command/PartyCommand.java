package com.controlbro.levely.command;

import com.controlbro.levely.LevelyPlugin;
import com.controlbro.levely.manager.PartyManager;
import com.controlbro.levely.model.Party;
import com.controlbro.levely.model.PartyMode;
import com.controlbro.levely.util.ChatUtil;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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

public class PartyCommand implements CommandExecutor, TabCompleter {
    private final LevelyPlugin plugin;
    private final PartyManager partyManager;

    public PartyCommand(LevelyPlugin plugin, PartyManager partyManager) {
        this.plugin = plugin;
        this.partyManager = partyManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatUtil.color(plugin.getMessageManager().getString("errors.playerOnly")));
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("info")) {
            Optional<Party> party = partyManager.getParty(player.getUniqueId());
            if (party.isEmpty()) {
                sender.sendMessage(ChatUtil.color(plugin.getMessageManager().getString("errors.notInParty")));
                return true;
            }
            Party current = party.get();
            sender.sendMessage(ChatUtil.color("&6Party: &e" + current.getName()));
            sender.sendMessage(ChatUtil.color("&6Leader: &e" + Bukkit.getOfflinePlayer(current.getLeader()).getName()));
            sender.sendMessage(ChatUtil.color("&6Members: &e" + current.getMembers().size()));
            sender.sendMessage(ChatUtil.color("&6Level: &e" + current.getLevel()));
            sender.sendMessage(ChatUtil.color("&6Locked: &e" + current.isLocked()));
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "create" -> {
                if (args.length < 2) {
                    sender.sendMessage(ChatUtil.color("&cUsage: /party create <name> [password]"));
                    return true;
                }
                if (partyManager.getParty(player.getUniqueId()).isPresent()) {
                    sender.sendMessage(ChatUtil.color("&cYou are already in a party."));
                    return true;
                }
                String passwordHash = args.length > 2 ? hashPassword(args[2]) : null;
                Party party = partyManager.createParty(player, args[1], passwordHash);
                sender.sendMessage(ChatUtil.color(plugin.getMessageManager().getString("party.created")
                    .replace("%party%", party.getName())));
                return true;
            }
            case "invite" -> {
                Optional<Party> party = partyManager.getParty(player.getUniqueId());
                if (party.isEmpty()) {
                    sender.sendMessage(ChatUtil.color(plugin.getMessageManager().getString("errors.notInParty")));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(ChatUtil.color("&cUsage: /party invite <player>"));
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage(ChatUtil.color(plugin.getMessageManager().getString("errors.invalidPlayer")));
                    return true;
                }
                partyManager.invite(player, target, party.get());
                sender.sendMessage(ChatUtil.color("&aInvite sent."));
                return true;
            }
            case "join" -> {
                if (args.length < 2) {
                    sender.sendMessage(ChatUtil.color("&cUsage: /party join <partyName> [password]"));
                    return true;
                }
                Optional<Party> party = partyManager.acceptInvite(player);
                if (party.isEmpty()) {
                    sender.sendMessage(ChatUtil.color(plugin.getMessageManager().getString("errors.inviteExpired")));
                    return true;
                }
                Party targetParty = party.get();
                targetParty.getMembers().add(player.getUniqueId());
                partyManager.broadcast(targetParty, plugin.getMessageManager().getString("party.join")
                    .replace("%player%", player.getName()));
                return true;
            }
            case "quit" -> {
                Optional<Party> party = partyManager.getParty(player.getUniqueId());
                if (party.isEmpty()) {
                    sender.sendMessage(ChatUtil.color(plugin.getMessageManager().getString("errors.notInParty")));
                    return true;
                }
                Party current = party.get();
                current.getMembers().remove(player.getUniqueId());
                partyManager.broadcast(current, plugin.getMessageManager().getString("party.leave")
                    .replace("%player%", player.getName()));
                if (current.getMembers().isEmpty()) {
                    partyManager.disband(current);
                }
                return true;
            }
            case "disband" -> {
                Optional<Party> party = partyManager.getParty(player.getUniqueId());
                if (party.isEmpty()) {
                    sender.sendMessage(ChatUtil.color(plugin.getMessageManager().getString("errors.notInParty")));
                    return true;
                }
                Party current = party.get();
                if (!current.getLeader().equals(player.getUniqueId())) {
                    sender.sendMessage(ChatUtil.color(plugin.getMessageManager().getString("errors.notLeader")));
                    return true;
                }
                partyManager.disband(current);
                return true;
            }
            case "mode" -> {
                Optional<Party> party = partyManager.getParty(player.getUniqueId());
                if (party.isEmpty()) {
                    sender.sendMessage(ChatUtil.color(plugin.getMessageManager().getString("errors.notInParty")));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(ChatUtil.color("&cUsage: /party mode <mode>"));
                    return true;
                }
                Party current = party.get();
                if (!current.getLeader().equals(player.getUniqueId())) {
                    sender.sendMessage(ChatUtil.color(plugin.getMessageManager().getString("errors.notLeader")));
                    return true;
                }
                PartyMode mode = PartyMode.valueOf(args[1].toUpperCase(Locale.ROOT));
                partyManager.setPartyMode(current, mode);
                return true;
            }
            case "help" -> {
                sender.sendMessage(ChatUtil.color("&6Party Commands:"));
                sender.sendMessage(ChatUtil.color("&e/party info"));
                sender.sendMessage(ChatUtil.color("&e/party create <name> [password]"));
                sender.sendMessage(ChatUtil.color("&e/party invite <player>"));
                sender.sendMessage(ChatUtil.color("&e/party join <partyName> [password]"));
                sender.sendMessage(ChatUtil.color("&e/party quit"));
                sender.sendMessage(ChatUtil.color("&e/party disband"));
                sender.sendMessage(ChatUtil.color("&e/pc"));
                sender.sendMessage(ChatUtil.color("&e/ptp <member>"));
                return true;
            }
            default -> {
                sender.sendMessage(ChatUtil.color("&cUnknown party command."));
                return true;
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("info", "create", "invite", "join", "quit", "disband", "mode", "help");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("mode")) {
            return Arrays.stream(PartyMode.values()).map(Enum::name).toList();
        }
        return Collections.emptyList();
    }

    private String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte b : hashed) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            return password;
        }
    }
}
