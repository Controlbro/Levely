package com.controlbro.levely.model;

public class SkillData {
    private int level;
    private double xp;

    public SkillData() {
        this(0, 0.0);
    }

    public SkillData(int level, double xp) {
        this.level = level;
        this.xp = xp;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = Math.max(level, 0);
    }

    public double getXp() {
        return xp;
    }

    public void setXp(double xp) {
        this.xp = Math.max(0.0, xp);
    }

    public void addXp(double amount) {
        this.xp = Math.max(0.0, this.xp + amount);
    }
}
