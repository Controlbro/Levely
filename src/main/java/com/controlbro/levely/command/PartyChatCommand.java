package com.controlbro.levely.command;

import com.controlbro.levely.LevelyPlugin;
import com.controlbro.levely.manager.PartyManager;
import com.controlbro.levely.util.ChatUtil;
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
            sender.sendMessage(ChatUtil.color(plugin.getMessageManager().getString("errors.playerOnly")));
            return true;
        }
        boolean enabled = partyManager.togglePartyChat(player);
        player.sendMessage(ChatUtil.color("&eParty chat " + (enabled ? "enabled" : "disabled") + "."));
        return true;
    }
}
