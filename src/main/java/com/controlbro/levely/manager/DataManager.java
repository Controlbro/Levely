package com.controlbro.levely.manager;

import com.controlbro.levely.LevelyPlugin;
import com.controlbro.levely.model.Party;
import com.controlbro.levely.model.PartyMode;
import com.controlbro.levely.model.PlayerProfile;
import com.controlbro.levely.model.SkillData;
import com.controlbro.levely.model.SkillType;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitTask;

public class DataManager {
    private final LevelyPlugin plugin;
    private final File playerDir;
    private final File partyDir;
    private final Map<UUID, PlayerProfile> profiles;
    private final Map<String, Party> parties;
    private BukkitTask autosaveTask;

    public DataManager(LevelyPlugin plugin) {
        this.plugin = plugin;
        this.playerDir = new File(plugin.getDataFolder(), "playerdata");
        this.partyDir = new File(plugin.getDataFolder(), "parties");
        this.profiles = new ConcurrentHashMap<>();
        this.parties = new ConcurrentHashMap<>();
        if (!playerDir.exists()) {
            playerDir.mkdirs();
        }
        if (!partyDir.exists()) {
            partyDir.mkdirs();
        }
        loadParties();
    }

    public PlayerProfile getOrCreateProfile(UUID uuid, String name) {
        return profiles.computeIfAbsent(uuid, id -> loadProfile(uuid, name));
    }

    public Optional<PlayerProfile> getProfile(UUID uuid) {
        return Optional.ofNullable(profiles.get(uuid));
    }

    public Collection<PlayerProfile> getProfiles() {
        return Collections.unmodifiableCollection(profiles.values());
    }

    public void unloadProfile(UUID uuid) {
        PlayerProfile profile = profiles.remove(uuid);
        if (profile != null) {
            saveProfile(profile);
        }
    }

    public void saveProfile(PlayerProfile profile) {
        File file = new File(playerDir, profile.getUuid() + ".yml");
        YamlConfiguration config = new YamlConfiguration();
        config.set("name", profile.getName());
        ConfigurationSection skillsSection = config.createSection("skills");
        for (Map.Entry<SkillType, SkillData> entry : profile.getSkills().entrySet()) {
            ConfigurationSection section = skillsSection.createSection(entry.getKey().getKey());
            section.set("level", entry.getValue().getLevel());
            section.set("xp", entry.getValue().getXp());
        }
        config.set("toggles.partyChat", profile.isPartyChat());
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save player data: " + profile.getUuid() + ": " + e.getMessage());
        }
    }

    public PlayerProfile loadProfile(UUID uuid, String name) {
        File file = new File(playerDir, uuid + ".yml");
        PlayerProfile profile = new PlayerProfile(uuid, name);
        if (!file.exists()) {
            return profile;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        profile.setName(config.getString("name", name));
        ConfigurationSection skillsSection = config.getConfigurationSection("skills");
        if (skillsSection != null) {
            for (SkillType skill : SkillType.values()) {
                ConfigurationSection section = skillsSection.getConfigurationSection(skill.getKey());
                if (section != null) {
                    SkillData data = profile.getSkillData(skill);
                    data.setLevel(section.getInt("level", 0));
                    data.setXp(section.getDouble("xp", 0.0));
                }
            }
        }
        profile.setPartyChat(config.getBoolean("toggles.partyChat", false));
        return profile;
    }

    public Map<String, Party> getParties() {
        return parties;
    }

    public Optional<Party> getPartyById(String id) {
        return Optional.ofNullable(parties.get(id));
    }

    public Optional<Party> getPartyByName(String name) {
        return parties.values().stream()
            .filter(party -> party.getName().equalsIgnoreCase(name))
            .findFirst();
    }

    public void addParty(Party party) {
        parties.put(party.getId(), party);
        saveParty(party);
    }

    public void removeParty(String id) {
        parties.remove(id);
        File file = new File(partyDir, id + ".yml");
        if (file.exists()) {
            file.delete();
        }
    }

    public void saveParty(Party party) {
        File file = new File(partyDir, party.getId() + ".yml");
        YamlConfiguration config = new YamlConfiguration();
        config.set("id", party.getId());
        config.set("name", party.getName());
        config.set("leader", party.getLeader().toString());
        config.set("members", party.getMembers().stream().map(UUID::toString).toList());
        config.set("locked", party.isLocked());
        config.set("passwordHash", party.getPasswordHash());
        config.set("level", party.getLevel());
        config.set("xp", party.getXp());
        config.set("settings.mode", party.getMode().name());
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save party data: " + party.getName() + ": " + e.getMessage());
        }
    }

    private void loadParties() {
        File[] files = partyDir.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) {
            return;
        }
        for (File file : files) {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            String id = config.getString("id", file.getName().replace(".yml", ""));
            String name = config.getString("name", id);
            UUID leader = UUID.fromString(config.getString("leader"));
            Party party = new Party(id, name, leader);
            party.setLocked(config.getBoolean("locked", false));
            party.setPasswordHash(config.getString("passwordHash"));
            party.setLevel(config.getInt("level", 1));
            party.addXp(config.getDouble("xp", 0));
            party.setMode(PartyMode.valueOf(config.getString("settings.mode", PartyMode.CASUAL.name())));
            for (String member : config.getStringList("members")) {
                party.getMembers().add(UUID.fromString(member));
            }
            parties.put(id, party);
        }
    }

    public void startAutosave() {
        boolean enabled = plugin.getConfig().getBoolean("general.autosave.enabled", true);
        int interval = plugin.getConfig().getInt("general.autosave.intervalSeconds", 300);
        if (!enabled) {
            return;
        }
        autosaveTask = plugin.getServer().getScheduler().runTaskTimerAsynchronously(
            plugin,
            this::saveAll,
            interval * 20L,
            interval * 20L
        );
    }

    public void saveAll() {
        profiles.values().forEach(this::saveProfile);
        parties.values().forEach(this::saveParty);
    }

    public void shutdown() {
        if (autosaveTask != null) {
            autosaveTask.cancel();
        }
        saveAll();
    }
}
