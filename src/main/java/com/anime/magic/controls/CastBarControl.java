package com.anime.magic.controls;

import com.anime.magic.AnimeMagicPlugin;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Cast Bar Control — a boss-bar progress bar that fills over a configurable duration.
 * Used to visualize long channeled spells. If the player takes damage, moves, or casts
 * another spell while active, the channel can be cancelled via cancel(UUID).
 */
public final class CastBarControl implements ControlScheme {
    private final AnimeMagicPlugin plugin;
    private final Map<UUID, Channel> active = new HashMap<>();

    public CastBarControl(AnimeMagicPlugin plugin) { this.plugin = plugin; }

    @Override public @NotNull String id() { return "castbar"; }
    @Override public @NotNull String displayName() { return "Cast Bar"; }
    @Override public @NotNull String description() {
        return "Boss-bar channeling bar for long casts. Other code triggers it via start().";
    }

    public boolean start(@NotNull Player player, int ticks, @NotNull String title,
                         @NotNull Consumer<Boolean> onComplete) {
        if (active.containsKey(player.getUniqueId())) return false;
        BossBar bar = plugin.getServer().createBossBar(title, BarColor.PURPLE, BarStyle.SEGMENTED_20);
        bar.setProgress(0.0);
        bar.addPlayer(player);
        Channel c = new Channel(player.getUniqueId(), bar, ticks, 0, onComplete);
        active.put(player.getUniqueId(), c);

        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (!active.containsKey(player.getUniqueId())) { cancel(); return; }
                if (player == null || !player.isOnline()) {
                    bar.removeAll();
                    active.remove(player.getUniqueId());
                    cancel();
                    return;
                }
                t += 2;
                c.tick = t;
                double progress = Math.min(1.0, (double) t / ticks);
                bar.setProgress(progress);
                if (t >= ticks) {
                    bar.removeAll();
                    active.remove(player.getUniqueId());
                    onComplete.accept(true);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);
        return true;
    }

    public boolean cancel(@NotNull UUID playerId) { return cancel(playerId, false); }

    public boolean cancel(@NotNull UUID playerId, boolean silent) {
        Channel c = active.remove(playerId);
        if (c == null) return false;
        c.bar.removeAll();
        c.onComplete.accept(false);
        return true;
    }

    public boolean isActive(@NotNull UUID playerId) { return active.containsKey(playerId); }

    @Override
    public void onSneak(@NotNull Player player, @NotNull org.bukkit.event.player.PlayerToggleSneakEvent e) {
        if (!e.isSneaking()) return;
        if (!plugin.getConfig().getBoolean("controls.castbar.interrupt-on-sneak", true)) return;
        cancel(player.getUniqueId());
    }

    private static final class Channel {
        final UUID playerId;
        final BossBar bar;
        final int totalTicks;
        int tick;
        final Consumer<Boolean> onComplete;
        Channel(UUID playerId, BossBar bar, int totalTicks, int tick, Consumer<Boolean> onComplete) {
            this.playerId = playerId; this.bar = bar; this.totalTicks = totalTicks;
            this.tick = tick; this.onComplete = onComplete;
        }
    }
}
