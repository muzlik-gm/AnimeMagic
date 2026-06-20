package com.anime.magic.util;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;

/**
 * Safe particle spawning wrapper that catches all exceptions on MC 1.21+.
 *
 * <p>On MC 1.21.10+, some particles that previously accepted raw double
 * parameters now require specific data objects (Float, Integer, DustOptions,
 * Color, etc.). Calls with {@code count=0} are especially problematic because
 * the API enters "long-range / velocity" mode which has stricter data
 * requirements.</p>
 *
 * <p>This utility wraps every {@code spawnParticle} call in a try/catch so a
 * single bad particle doesn't crash the repeating task and spam the console.
 * It also normalises {@code count=0} to {@code count=1} for particles that
 * don't need velocity data, which avoids the
 * {@code IllegalArgumentException: missing required data class java.lang.Float}
 * crash.</p>
 */
public final class SafeParticles {
    private SafeParticles() {}

    /**
     * Spawn a particle safely. Never throws — catches all exceptions.
     *
     * @param world     the world
     * @param particle  the particle type
     * @param location  spawn location
     * @param count     number of particles (0 is normalised to 1 for safety)
     * @param offsetX   X offset / spread
     * @param offsetY   Y offset / spread
     * @param offsetZ   Z offset / spread
     * @param speed     particle speed
     */
    public static void spawn(World world, Particle particle, Location location,
                             int count, double offsetX, double offsetY, double offsetZ, double speed) {
        if (world == null || location == null || particle == null) return;
        // Normalise count=0 to count=1 — on MC 1.21+ count=0 enters velocity
        // mode which requires specific data types and crashes for many particles.
        int safeCount = Math.max(1, count);
        try {
            world.spawnParticle(particle, location, safeCount, offsetX, offsetY, offsetZ, speed);
        } catch (Throwable ignored) {
            // Silently swallow — a bad particle should never crash a repeating task.
        }
    }

    /**
     * Spawn a particle safely with count=1 and no offsets.
     */
    public static void spawn(World world, Particle particle, Location location) {
        spawn(world, particle, location, 1, 0, 0, 0, 0);
    }

    /**
     * Spawn a particle safely with a DustOptions data parameter (for Particle.DUST).
     */
    public static void spawnDust(World world, Location location, int count,
                                  double offsetX, double offsetY, double offsetZ,
                                  org.bukkit.Particle.DustOptions dustOptions) {
        if (world == null || location == null || dustOptions == null) return;
        try {
            world.spawnParticle(Particle.DUST, location, Math.max(1, count),
                    offsetX, offsetY, offsetZ, 0, dustOptions);
        } catch (Throwable ignored) {}
    }
}
