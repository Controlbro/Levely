package com.controlbro.levely.command;

import com.controlbro.levely.LevelyPlugin;
import com.controlbro.levely.manager.PartyManager;
import com.controlbro.levely.model.Party;
import com.controlbro.levely.model.PartyMode;
import com.controlbro.levely.util.Msg;
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
            Msg.send(sender, "errors.playerOnly");
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("info")) {
            Optional<Party> party = partyManager.getParty(player.getUniqueId());
            if (party.isEmpty()) {
                Msg.send(sender, "errors.notInParty");
                return true;
            }
            Party current = party.get();
            Msg.send(sender, "party.info.name", "%party%", current.getName());
            Msg.send(sender, "party.info.leader", "%leader%", Bukkit.getOfflinePlayer(current.getLeader()).getName());
            Msg.send(sender, "party.info.members", "%count%", String.valueOf(current.getMembers().size()));
            Msg.send(sender, "party.info.level", "%level%", String.valueOf(current.getLevel()));
            Msg.send(sender, "party.info.locked", "%locked%", String.valueOf(current.isLocked()));
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "create" -> {
                if (args.length < 2) {
                    Msg.send(sender, "party.usage.create");
                    return true;
                }
                if (partyManager.getParty(player.getUniqueId()).isPresent()) {
                    Msg.send(sender, "party.alreadyInParty");
                    return true;
                }
                String passwordHash = args.length > 2 ? hashPassword(args[2]) : null;
                Party party = partyManager.createParty(player, args[1], passwordHash);
                Msg.send(sender, "party.created", "%party%", party.getName());
                return true;
            }
            case "invite" -> {
                Optional<Party> party = partyManager.getParty(player.getUniqueId());
                if (party.isEmpty()) {
                    Msg.send(sender, "errors.notInParty");
                    return true;
                }
                if (args.length < 2) {
                    Msg.send(sender, "party.usage.invite");
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    Msg.send(sender, "errors.invalidPlayer");
                    return true;
                }
                partyManager.invite(player, target, party.get());
                Msg.send(sender, "party.inviteSent");
                return true;
            }
            case "join" -> {
                if (args.length < 2) {
                    Msg.send(sender, "party.usage.join");
                    return true;
                }
                Optional<Party> party = partyManager.acceptInvite(player);
                if (party.isEmpty()) {
                    Msg.send(sender, "errors.inviteExpired");
                    return true;
                }
                Party targetParty = party.get();
                targetParty.getMembers().add(player.getUniqueId());
                partyManager.broadcast(targetParty, "party.join", "%player%", player.getName());
                return true;
            }
            case "quit" -> {
                Optional<Party> party = partyManager.getParty(player.getUniqueId());
                if (party.isEmpty()) {
                    Msg.send(sender, "errors.notInParty");
                    return true;
                }
                Party current = party.get();
                current.getMembers().remove(player.getUniqueId());
                partyManager.broadcast(current, "party.leave", "%player%", player.getName());
                if (current.getMembers().isEmpty()) {
                    partyManager.disband(current);
                }
                return true;
            }
            case "disband" -> {
                Optional<Party> party = partyManager.getParty(player.getUniqueId());
                if (party.isEmpty()) {
                    Msg.send(sender, "errors.notInParty");
                    return true;
                }
                Party current = party.get();
                if (!current.getLeader().equals(player.getUniqueId())) {
                    Msg.send(sender, "errors.notLeader");
                    return true;
                }
                partyManager.disband(current);
                return true;
            }
            case "mode" -> {
                Optional<Party> party = partyManager.getParty(player.getUniqueId());
                if (party.isEmpty()) {
                    Msg.send(sender, "errors.notInParty");
                    return true;
                }
                if (args.length < 2) {
                    Msg.send(sender, "party.usage.mode");
                    return true;
                }
                Party current = party.get();
                if (!current.getLeader().equals(player.getUniqueId())) {
                    Msg.send(sender, "errors.notLeader");
                    return true;
                }
                PartyMode mode = PartyMode.valueOf(args[1].toUpperCase(Locale.ROOT));
                partyManager.setPartyMode(current, mode);
                return true;
            }
            case "help" -> {
                Msg.send(sender, "party.help.header");
                Msg.send(sender, "party.help.info");
                Msg.send(sender, "party.help.create");
                Msg.send(sender, "party.help.invite");
                Msg.send(sender, "party.help.join");
                Msg.send(sender, "party.help.quit");
                Msg.send(sender, "party.help.disband");
                Msg.send(sender, "party.help.chat");
                Msg.send(sender, "party.help.teleport");
                return true;
            }
            default -> {
                Msg.send(sender, "party.unknownCommand");
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
