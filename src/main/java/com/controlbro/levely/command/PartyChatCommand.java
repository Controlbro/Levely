package com.controlbro.levely.command;

import com.controlbro.levely.LevelyPlugin;
import com.controlbro.levely.manager.PartyManager;
import com.controlbro.levely.util.Msg;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class PartyChatCommand implements CommandExecutor {
    private final LevelyPlugin plugin;
    private final PartyManager partyManager;

    public PartyChatCommand(LevelyPlugin plugin, PartyManager partyManager) {
        this.plugin = plugin;
        this.partyManager = partyManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            Msg.send(sender, "errors.playerOnly");
            return true;
        }
        boolean enabled = partyManager.togglePartyChat(player);
        Msg.send(player, enabled ? "party.chatEnabled" : "party.chatDisabled");
        return true;
    }
}
