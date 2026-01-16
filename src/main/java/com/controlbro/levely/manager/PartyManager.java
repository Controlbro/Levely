package com.controlbro.levely.manager;

import com.controlbro.levely.LevelyPlugin;
import com.controlbro.levely.model.Party;
import com.controlbro.levely.model.PartyMode;
import com.controlbro.levely.model.PlayerProfile;
import com.controlbro.levely.util.Msg;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class PartyManager {
    private final LevelyPlugin plugin;
    private final DataManager dataManager;
    private final Map<UUID, PartyInvite> invites;

    public PartyManager(LevelyPlugin plugin, DataManager dataManager) {
        this.plugin = plugin;
        this.dataManager = dataManager;
        this.invites = new HashMap<>();
    }

    public Optional<Party> getParty(UUID playerId) {
        return dataManager.getParties().values().stream()
            .filter(party -> party.getMembers().contains(playerId))
            .findFirst();
    }

    public Party createParty(Player leader, String name, String passwordHash) {
        String id = UUID.randomUUID().toString();
        Party party = new Party(id, name, leader.getUniqueId());
        party.setPasswordHash(passwordHash);
        dataManager.addParty(party);
        return party;
    }

    public void disband(Party party) {
        party.getMembers().forEach(member -> {
            Player player = Bukkit.getPlayer(member);
            if (player != null) {
                Msg.send(player, "party.disband");
            }
        });
        dataManager.removeParty(party.getId());
    }

    public void invite(Player inviter, Player target, Party party) {
        int expiry = plugin.getConfig().getInt("party.invite.expireSeconds", 60);
        invites.put(target.getUniqueId(), new PartyInvite(party.getId(), inviter.getUniqueId(), Instant.now(), expiry));
        Msg.send(target, "party.invite", "%party%", party.getName(), "%player%", inviter.getName());
    }

    public Optional<Party> acceptInvite(Player player) {
        PartyInvite invite = invites.remove(player.getUniqueId());
        if (invite == null || invite.isExpired()) {
            return Optional.empty();
        }
        return dataManager.getPartyById(invite.partyId());
    }

    public boolean togglePartyChat(Player player) {
        PlayerProfile profile = dataManager.getOrCreateProfile(player.getUniqueId(), player.getName());
        profile.setPartyChat(!profile.isPartyChat());
        return profile.isPartyChat();
    }

    public boolean isPartyChatEnabled(Player player) {
        return dataManager.getOrCreateProfile(player.getUniqueId(), player.getName()).isPartyChat();
    }

    public void setPartyMode(Party party, PartyMode mode) {
        party.setMode(mode);
        broadcast(party, "party.modeSet", "%mode%", mode.name());
    }

    public void broadcast(Party party, String key, Object... placeholders) {
        party.getMembers().forEach(member -> {
            Player player = Bukkit.getPlayer(member);
            if (player != null) {
                Msg.send(player, key, placeholders);
            }
        });
    }

    public void broadcastRaw(Party party, String message, boolean includePrefix) {
        party.getMembers().forEach(member -> {
            Player player = Bukkit.getPlayer(member);
            if (player != null) {
                Msg.sendRaw(player, message, includePrefix);
            }
        });
    }

    public boolean isInviteValid(Player player) {
        PartyInvite invite = invites.get(player.getUniqueId());
        return invite != null && !invite.isExpired();
    }

    public record PartyInvite(String partyId, UUID inviter, Instant createdAt, int expiresSeconds) {
        public boolean isExpired() {
            return Duration.between(createdAt, Instant.now()).getSeconds() > expiresSeconds;
        }
    }
}
