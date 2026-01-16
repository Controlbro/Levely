package com.controlbro.levely;

import com.controlbro.levely.command.LevelyCommand;
import com.controlbro.levely.command.PartyChatCommand;
import com.controlbro.levely.command.PartyCommand;
import com.controlbro.levely.command.PartyTeleportCommand;
import com.controlbro.levely.listener.CombatListener;
import com.controlbro.levely.listener.CraftingListener;
import com.controlbro.levely.listener.FishingListener;
import com.controlbro.levely.listener.GeneralListener;
import com.controlbro.levely.listener.MiningListener;
import com.controlbro.levely.listener.MovementListener;
import com.controlbro.levely.manager.AbilityManager;
import com.controlbro.levely.manager.DataManager;
import com.controlbro.levely.manager.MessageManager;
import com.controlbro.levely.manager.PartyManager;
import com.controlbro.levely.manager.PassiveManager;
import com.controlbro.levely.manager.SkillManager;
import com.controlbro.levely.manager.XpBossBarManager;
import com.controlbro.levely.manager.XpEventManager;
import com.controlbro.levely.util.Msg;
import org.bukkit.plugin.java.JavaPlugin;

public class LevelyPlugin extends JavaPlugin {
    private static LevelyPlugin instance;
    private DataManager dataManager;
    private SkillManager skillManager;
    private AbilityManager abilityManager;
    private PassiveManager passiveManager;
    private PartyManager partyManager;
    private MessageManager messageManager;
    private XpEventManager xpEventManager;
    private XpBossBarManager xpBossBarManager;

    public static LevelyPlugin getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        saveResource("messages.yml", false);
        saveResource("skills.yml", false);

        this.dataManager = new DataManager(this);
        this.messageManager = new MessageManager(this);
        Msg.init(this);
        this.partyManager = new PartyManager(this, dataManager);
        this.xpEventManager = new XpEventManager(this);
        this.xpBossBarManager = new XpBossBarManager(this);
        this.skillManager = new SkillManager(this, dataManager, xpEventManager, xpBossBarManager, partyManager);
        this.abilityManager = new AbilityManager(this, dataManager, skillManager);
        this.passiveManager = new PassiveManager(this, dataManager, skillManager);

        registerCommands();
        registerListeners();

        dataManager.startAutosave();
        getLogger().info("Levely enabled.");
    }

    @Override
    public void onDisable() {
        dataManager.shutdown();
        getLogger().info("Levely disabled.");
    }

    private void registerCommands() {
        LevelyCommand levelyCommand = new LevelyCommand(this, dataManager, skillManager, partyManager);
        getCommand("levely").setExecutor(levelyCommand);
        getCommand("levely").setTabCompleter(levelyCommand);

        PartyCommand partyCommand = new PartyCommand(this, partyManager);
        getCommand("party").setExecutor(partyCommand);
        getCommand("party").setTabCompleter(partyCommand);

        PartyChatCommand partyChatCommand = new PartyChatCommand(this, partyManager);
        getCommand("pc").setExecutor(partyChatCommand);

        PartyTeleportCommand partyTeleportCommand = new PartyTeleportCommand(this, partyManager);
        getCommand("ptp").setExecutor(partyTeleportCommand);
        getCommand("ptp").setTabCompleter(partyTeleportCommand);
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new MiningListener(this, skillManager, abilityManager, passiveManager, dataManager), this);
        getServer().getPluginManager().registerEvents(new FishingListener(this, skillManager), this);
        getServer().getPluginManager().registerEvents(new CombatListener(this, skillManager, passiveManager, partyManager), this);
        getServer().getPluginManager().registerEvents(new MovementListener(this, skillManager, passiveManager), this);
        getServer().getPluginManager().registerEvents(new CraftingListener(this, skillManager), this);
        getServer().getPluginManager().registerEvents(new GeneralListener(this, dataManager, partyManager, xpEventManager, xpBossBarManager), this);
    }

    public DataManager getDataManager() {
        return dataManager;
    }

    public SkillManager getSkillManager() {
        return skillManager;
    }

    public AbilityManager getAbilityManager() {
        return abilityManager;
    }

    public PassiveManager getPassiveManager() {
        return passiveManager;
    }

    public PartyManager getPartyManager() {
        return partyManager;
    }

    public MessageManager getMessageManager() {
        return messageManager;
    }

    public XpEventManager getXpEventManager() {
        return xpEventManager;
    }

    public XpBossBarManager getXpBossBarManager() {
        return xpBossBarManager;
    }
}
