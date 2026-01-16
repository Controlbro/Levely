package com.controlbro.levely.model;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class Party {
    private final String id;
    private String name;
    private UUID leader;
    private final Set<UUID> members;
    private boolean locked;
    private String passwordHash;
    private int level;
    private double xp;
    private PartyMode mode;

    public Party(String id, String name, UUID leader) {
        this.id = id;
        this.name = name;
        this.leader = leader;
        this.members = new HashSet<>();
        this.members.add(leader);
        this.locked = false;
        this.level = 1;
        this.xp = 0;
        this.mode = PartyMode.CASUAL;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public UUID getLeader() {
        return leader;
    }

    public void setLeader(UUID leader) {
        this.leader = leader;
        this.members.add(leader);
    }

    public Set<UUID> getMembers() {
        return members;
    }

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = Math.max(level, 1);
    }

    public double getXp() {
        return xp;
    }

    public void addXp(double amount) {
        this.xp = Math.max(0, this.xp + amount);
    }

    public PartyMode getMode() {
        return mode;
    }

    public void setMode(PartyMode mode) {
        this.mode = mode;
    }
}
