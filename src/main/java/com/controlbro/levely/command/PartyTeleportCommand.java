package com.controlbro.levely.command;

import com.controlbro.levely.LevelyPlugin;
import com.controlbro.levely.manager.PartyManager;
import com.controlbro.levely.model.Party;
import com.controlbro.levely.util.Msg;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public class PartyTeleportCommand implements CommandExecutor, TabCompleter {
    private final LevelyPlugin plugin;
    private final PartyManager partyManager;
    private final Map<UUID, Instant> cooldowns;

    public PartyTeleportCommand(LevelyPlugin plugin, PartyManager partyManager) {
        this.plugin = plugin;
        this.partyManager = partyManager;
        this.cooldowns = new HashMap<>();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            Msg.send(sender, "errors.playerOnly");
            return true;
        }
        Optional<Party> party = partyManager.getParty(player.getUniqueId());
        if (party.isEmpty()) {
            Msg.send(sender, "errors.notInParty");
            return true;
        }
        if (args.length < 1) {
            Msg.send(sender, "party.teleportUsage");
            return true;
        }
        Party current = party.get();
        int unlockLevel = plugin.getConfig().getInt("party.unlocks.partyTeleport", 2);
        if (current.getLevel() < unlockLevel) {
            Msg.send(sender, "errors.featureLocked", "%level%", String.valueOf(unlockLevel));
            return true;
        }
        int cooldownSeconds = plugin.getConfig().getInt("party.teleport.cooldownSeconds", 120);
        Instant last = cooldowns.get(player.getUniqueId());
        if (last != null && Duration.between(last, Instant.now()).getSeconds() < cooldownSeconds) {
            long remaining = cooldownSeconds - Duration.between(last, Instant.now()).getSeconds();
            Msg.send(sender, "errors.teleportCooldown", "%seconds%", String.valueOf(remaining));
            return true;
        }
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null || !current.getMembers().contains(target.getUniqueId())) {
            Msg.send(sender, "errors.invalidPlayer");
            return true;
        }
        player.teleport(target.getLocation());
        cooldowns.put(player.getUniqueId(), Instant.now());
        Msg.send(player, "party.teleported", "%player%", target.getName());
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender instanceof Player player) {
            return partyManager.getParty(player.getUniqueId())
                .map(party -> party.getMembers().stream()
                    .map(uuid -> Bukkit.getOfflinePlayer(uuid).getName())
                    .filter(name -> name != null)
                    .collect(Collectors.toList()))
                .orElse(List.of());
        }
        return List.of();
    }
}
