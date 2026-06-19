package com.anime.magic.cinematic;

import com.anime.magic.AnimeMagicPlugin;
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
 * <p>The offsets are very small (0.05-0.12 blocks) and only last ~10 ticks
 * (0.5 seconds), so it feels like a shake rather than actual movement.</p>
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
        if (activeShakes.contains(player.getUniqueId())) return; // don't stack
        activeShakes.add(player.getUniqueId());

        new BukkitRunnable() {
            int tick = 0;
            @Override public void run() {
                if (tick >= durationTicks || !player.isOnline()) {
                    activeShakes.remove(player.getUniqueId());
                    cancel();
                    return;
                }
                // Calculate random offset that decreases over time (ease-out)
                double progress = (double) tick / durationTicks;
                double decay = 1.0 - progress; // 1.0 → 0.0
                double offset = maxOffset * intensity * decay;
                // Random direction
                double yawOffset = (Math.random() - 0.5) * 2 * offset * 20; // degrees
                double pitchOffset = (Math.random() - 0.5) * 2 * offset * 20;
                Location loc = player.getLocation();
                loc.setYaw(loc.getYaw() + (float) yawOffset);
                loc.setPitch(Math.max(-90, Math.min(90, loc.getPitch() + (float) pitchOffset)));
                // Teleport without changing position (only view direction)
                player.teleport(loc);
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * Shake the screen of all players within a radius of the epicenter.
     *
     * @param epicenter The center of the impact
     * @param radius The radius in blocks
     * @param intensity Base intensity (1.0 = normal)
     */
    public void shakeNearby(@NotNull Location epicenter, double radius, double intensity) {
        if (!enabled || epicenter.getWorld() == null) return;
        for (Player p : epicenter.getWorld().getPlayers()) {
            double dist = p.getLocation().distance(epicenter);
            if (dist <= radius) {
                // Intensity falls off with distance
                double falloff = 1.0 - (dist / radius) * 0.5; // 1.0 at center, 0.5 at edge
                shake(p, intensity * falloff);
            }
        }
    }
}
