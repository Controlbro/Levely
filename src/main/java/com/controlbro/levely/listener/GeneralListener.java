package com.controlbro.levely.listener;

import com.controlbro.levely.LevelyPlugin;
import com.controlbro.levely.manager.DataManager;
import com.controlbro.levely.manager.PartyManager;
import com.controlbro.levely.manager.XpBossBarManager;
import com.controlbro.levely.manager.XpEventManager;
import com.controlbro.levely.model.Party;
import com.controlbro.levely.model.SkillType;
import com.controlbro.levely.util.Msg;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class GeneralListener implements Listener {
    private final LevelyPlugin plugin;
    private final DataManager dataManager;
    private final PartyManager partyManager;
    private final XpEventManager xpEventManager;
    private final XpBossBarManager xpBossBarManager;

    public GeneralListener(LevelyPlugin plugin, DataManager dataManager, PartyManager partyManager,
                           XpEventManager xpEventManager, XpBossBarManager xpBossBarManager) {
        this.plugin = plugin;
        this.dataManager = dataManager;
        this.partyManager = partyManager;
        this.xpEventManager = xpEventManager;
        this.xpBossBarManager = xpBossBarManager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        dataManager.getOrCreateProfile(player.getUniqueId(), player.getName());
        announceEvents(player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        dataManager.unloadProfile(event.getPlayer().getUniqueId());
        xpBossBarManager.remove(event.getPlayer());
    }

    @EventHandler
    public void onPartyChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!partyManager.isPartyChatEnabled(player)) {
            return;
        }
        Optional<Party> party = partyManager.getParty(player.getUniqueId());
        if (party.isEmpty()) {
            return;
        }
        event.setCancelled(true);
        String format = plugin.getMessageManager().getString("party.chatFormat")
            .replace("%player%", player.getName())
            .replace("%message%", event.getMessage());
        String partyPrefix = plugin.getMessageManager().getString("party.chatPrefix");
        boolean useOwnPrefix = plugin.getConfig().getBoolean("party.chat.useOwnPrefix", false);
        String message = (partyPrefix == null ? "" : partyPrefix) + format;
        partyManager.broadcastRaw(party.get(), message, !useOwnPrefix);
    }

    private void announceEvents(Player player) {
        if (!plugin.getConfig().getBoolean("events.joinAnnounce.enabled", true)) {
            return;
        }
        if (plugin.getConfig().getBoolean("events.joinAnnounce.onlyIfActive", true)
            && !xpEventManager.hasActiveMultipliers()) {
            return;
        }
        List<String> lines = new ArrayList<>();
        if (xpEventManager.getGlobalMultiplier() != 1.0) {
            lines.add(formatGlobalEvent());
        }
        for (SkillType skill : SkillType.values()) {
            if (xpEventManager.getSkillMultiplier(skill) != 1.0) {
                lines.add(formatSkillEvent(skill));
            }
        }
        if (lines.isEmpty()) {
            return;
        }
        String summary = String.join(", ", lines);
        Msg.send(player, "events.joinAnnounce", "%events%", summary);
    }

    private String formatGlobalEvent() {
        String multiplier = String.format("%.2f", xpEventManager.getGlobalMultiplier());
        String remaining = formatRemaining(xpEventManager.getGlobalEndsAt().orElse(null));
        return remaining.isEmpty()
            ? plugin.getMessageManager().getString("events.globalActive")
                .replace("%multiplier%", multiplier)
            : plugin.getMessageManager().getString("events.globalActiveTimed")
                .replace("%multiplier%", multiplier)
                .replace("%remaining%", remaining);
    }

    private String formatSkillEvent(SkillType skill) {
        String multiplier = String.format("%.2f", xpEventManager.getSkillMultiplier(skill));
        String remaining = formatRemaining(xpEventManager.getSkillEndsAt(skill).orElse(null));
        return remaining.isEmpty()
            ? plugin.getMessageManager().getString("events.skillActive")
                .replace("%skill%", skill.getDisplayName())
                .replace("%multiplier%", multiplier)
            : plugin.getMessageManager().getString("events.skillActiveTimed")
                .replace("%skill%", skill.getDisplayName())
                .replace("%multiplier%", multiplier)
                .replace("%remaining%", remaining);
    }

    private String formatRemaining(Instant endsAt) {
        if (endsAt == null) {
            return "";
        }
        Duration duration = Duration.between(Instant.now(), endsAt);
        if (duration.isNegative()) {
            return "";
        }
        long seconds = duration.getSeconds();
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;
        if (days > 0) {
            return days + "d";
        }
        if (hours > 0) {
            return hours + "h";
        }
        if (minutes > 0) {
            return minutes + "m";
        }
        return seconds + "s";
    }
}
