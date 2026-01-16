package com.controlbro.levely.listener;

import com.controlbro.levely.LevelyPlugin;
import com.controlbro.levely.manager.DataManager;
import com.controlbro.levely.manager.PartyManager;
import com.controlbro.levely.model.Party;
import com.controlbro.levely.util.ChatUtil;
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

    public GeneralListener(LevelyPlugin plugin, DataManager dataManager, PartyManager partyManager) {
        this.plugin = plugin;
        this.dataManager = dataManager;
        this.partyManager = partyManager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        dataManager.getOrCreateProfile(player.getUniqueId(), player.getName());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        dataManager.unloadProfile(event.getPlayer().getUniqueId());
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
        partyManager.broadcast(party.get(), ChatUtil.color(format));
    }
}
