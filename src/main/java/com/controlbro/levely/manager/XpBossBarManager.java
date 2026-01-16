package com.controlbro.levely.manager;

import com.controlbro.levely.LevelyPlugin;
import com.controlbro.levely.model.SkillType;
import com.controlbro.levely.util.ChatUtil;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

public class XpBossBarManager {
    private final LevelyPlugin plugin;
    private final Map<UUID, BossBarState> bars;

    public XpBossBarManager(LevelyPlugin plugin) {
        this.plugin = plugin;
        this.bars = new java.util.HashMap<>();
    }

    public void showXp(Player player, SkillType skill, double currentXp, double xpToNext, double deltaXp) {
        if (!plugin.getConfig().getBoolean("ui.xpBossBar.enabled", true)) {
            return;
        }
        if (plugin.getConfig().getBoolean("ui.xpBossBar.onlyInSurvival", false)
            && player.getGameMode().name().equalsIgnoreCase("CREATIVE")) {
            return;
        }
        int throttleTicks = plugin.getConfig().getInt("ui.xpBossBar.updateThrottleTicks", 0);
        BossBarState state = bars.computeIfAbsent(player.getUniqueId(), id -> new BossBarState(Bukkit.createBossBar("", BarColor.BLUE, BarStyle.SOLID)));
        state.setPending(skill, currentXp, xpToNext, deltaXp);
        if (throttleTicks <= 0) {
            applyUpdate(player, state);
        } else if (state.throttleTask == null) {
            state.throttleTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                applyUpdate(player, state);
                state.throttleTask = null;
            }, throttleTicks);
        }
    }

    public void showLevelUp(Player player, SkillType skill, int level) {
        if (!plugin.getConfig().getBoolean("ui.xpBossBar.showOnLevelUp", true)) {
            return;
        }
        BossBarState state = bars.computeIfAbsent(player.getUniqueId(), id -> new BossBarState(Bukkit.createBossBar("", BarColor.GREEN, BarStyle.SOLID)));
        String title = plugin.getConfig().getString("ui.xpBossBar.levelUpTitleFormat",
            "&a%skill% Level Up! &7(Level %level%)");
        title = title.replace("%skill%", formatSkill(skill))
            .replace("%level%", String.valueOf(level));
        state.bar.setTitle(ChatUtil.color(title));
        state.bar.setProgress(1.0);
        if (!state.bar.getPlayers().contains(player)) {
            state.bar.addPlayer(player);
        }
        scheduleHide(player, state);
    }

    public void remove(Player player) {
        BossBarState state = bars.remove(player.getUniqueId());
        if (state != null) {
            state.bar.removeAll();
            cancelTasks(state);
        }
    }

    private void applyUpdate(Player player, BossBarState state) {
        if (state.pending == null) {
            return;
        }
        PendingXp pending = state.pending;
        double progress = pending.xpToNext <= 0 ? 0 : Math.min(1.0, pending.currentXp / pending.xpToNext);
        String title = plugin.getConfig().getString("ui.xpBossBar.titleFormat", "%skill% %xp% (+%delta%xp)");
        title = title.replace("%skill%", formatSkill(pending.skill))
            .replace("%xp%", formatNumber(pending.currentXp))
            .replace("%required%", formatNumber(pending.xpToNext))
            .replace("%delta%", formatNumber(pending.delta));
        state.bar.setTitle(ChatUtil.color(title));
        state.bar.setProgress(progress);
        if (!state.bar.getPlayers().contains(player)) {
            state.bar.addPlayer(player);
        }
        scheduleHide(player, state);
    }

    private void scheduleHide(Player player, BossBarState state) {
        int hideAfter = plugin.getConfig().getInt("ui.xpBossBar.hideAfterSeconds", 6);
        cancelTasks(state);
        if (hideAfter <= 0) {
            return;
        }
        state.hideTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            state.bar.removePlayer(player);
        }, hideAfter * 20L);
    }

    private String formatSkill(SkillType skill) {
        String format = plugin.getConfig().getString("ui.xpBossBar.skillNameFormat", "&6%skill%&r");
        return format.replace("%skill%", skill.getDisplayName());
    }

    private String formatNumber(double value) {
        return String.format("%.0f", value);
    }

    private void cancelTasks(BossBarState state) {
        if (state.hideTask != null) {
            state.hideTask.cancel();
            state.hideTask = null;
        }
        if (state.throttleTask != null) {
            state.throttleTask.cancel();
            state.throttleTask = null;
        }
    }

    private static class BossBarState {
        private final BossBar bar;
        private PendingXp pending;
        private BukkitTask hideTask;
        private BukkitTask throttleTask;

        private BossBarState(BossBar bar) {
            this.bar = bar;
        }

        private void setPending(SkillType skill, double currentXp, double xpToNext, double delta) {
            this.pending = new PendingXp(skill, currentXp, xpToNext, delta);
        }
    }

    private record PendingXp(SkillType skill, double currentXp, double xpToNext, double delta) {
    }
}
