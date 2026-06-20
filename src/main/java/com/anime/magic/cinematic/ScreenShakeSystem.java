package com.anime.magic.cinematic;

import com.anime.magic.AnimeMagicPlugin;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simulates screen shake by rapidly teleporting the player's view by tiny
 * random offsets for a few ticks. This is the closest vanilla-Paper can get
 * to camera shake without NMS packets.
 *
 * <p><b>Drift fix:</b> the original implementation added offsets to the
 * player's <em>current</em> yaw/pitch each tick, so small errors accumulated
 * and the player's aim was permanently shifted after a shake. We now snapshot
 * the original yaw/pitch at shake start and write ABSOLUTE yaw/pitch each tick
 * (original + offset * decay), so the view returns to its starting orientation
 * when the shake ends.</p>
 *
 * <p>Players who are sensitive to motion can disable this in config:
 * {@code cinematic.screen-shake.enabled: false}</p>
 */
public final class ScreenShakeSystem {

    private final AnimeMagicPlugin plugin;
    private final boolean enabled;
    private final double maxOffset;
    private final int durationTicks;
    private final java.util.Set<UUID> activeShakes = ConcurrentHashMap.newKeySet();

    public ScreenShakeSystem(AnimeMagicPlugin plugin) {
        this.plugin = plugin;
        this.enabled = plugin.getConfig().getBoolean("cinematic.screen-shake.enabled", true);
        this.maxOffset = plugin.getConfig().getDouble("cinematic.screen-shake.max-offset", 0.12);
        this.durationTicks = plugin.getConfig().getInt("cinematic.screen-shake.duration-ticks", 10);
    }

    /**
     * Shake the screen of a single player.
     *
     * @param player The player whose screen to shake
     * @param intensity Multiplier on the max-offset (1.0 = normal, 2.0 = violent)
     */
    public void shake(@NotNull Player player, double intensity) {
        if (!enabled) return;
        // Skip dead / spectator / creative players — they shouldn't be shaken.
        if (player.isDead() || player.getGameMode() == GameMode.SPECTATOR
                || player.getGameMode() == GameMode.CREATIVE) return;
        if (activeShakes.contains(player.getUniqueId())) return; // don't stack
        activeShakes.add(player.getUniqueId());

        // Snapshot original view so we can write ABSOLUTE offsets (no drift).
        final float originalYaw = player.getLocation().getYaw();
        final float originalPitch = player.getLocation().getPitch();

        new BukkitRunnable() {
            int tick = 0;
            @Override public void run() {
                if (tick >= durationTicks || !player.isOnline()) {
                    // Restore exact original view to undo any rounding drift.
                    if (player.isOnline()) {
                        Location restore = player.getLocation();
                        restore.setYaw(originalYaw);
                        restore.setPitch(originalPitch);
                        player.teleport(restore);
                    }
                    activeShakes.remove(player.getUniqueId());
                    cancel();
                    return;
                }
                // Calculate random offset that decreases over time (ease-out)
                double progress = (double) tick / durationTicks;
                double decay = 1.0 - progress; // 1.0 → 0.0
                double offset = maxOffset * intensity * decay;
                double yawOffset = (Math.random() - 0.5) * 2 * offset * 20; // degrees
                double pitchOffset = (Math.random() - 0.5) * 2 * offset * 20;
                // ABSOLUTE write (no accumulation) — original + offset.
                Location loc = player.getLocation();
                loc.setYaw(originalYaw + (float) yawOffset);
                loc.setPitch(Math.max(-90, Math.min(90, originalPitch + (float) pitchOffset)));
                player.teleport(loc);
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * Shake the screen of all players within a radius of the epicenter.
     */
    public void shakeNearby(@NotNull Location epicenter, double radius, double intensity) {
        if (!enabled || epicenter.getWorld() == null) return;
        for (Player p : epicenter.getWorld().getPlayers()) {
            if (p.isDead() || p.getGameMode() == GameMode.SPECTATOR) continue;
            double dist = p.getLocation().distance(epicenter);
            if (dist <= radius) {
                double falloff = 1.0 - (dist / radius) * 0.5;
                shake(p, intensity * falloff);
            }
        }
    }
}
