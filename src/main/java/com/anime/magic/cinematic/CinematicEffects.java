package com.anime.magic.cinematic;

import com.anime.magic.AnimeMagicPlugin;
import com.anime.magic.effects.ParticleEngine;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

/**
 * High-level cinematic particle helpers that produce anime-quality effects:
 *
 * <ul>
 *   <li><b>Directed stream</b> — particles travel WITH a vector (not random scatter)</li>
 *   <li><b>Build-up</b> — escalating particle density + pitch over a duration</li>
 *   <li><b>Shockwave ring</b> — expanding ring that travels outward along the ground</li>
 *   <li><b>Lingering aftermath</b> — smoke, embers, or dust that persists after the hit</li>
 *   <li><b>Energy charge</b> — converging particles that spiral inward toward a point</li>
 * </ul>
 *
 * <p>These compose into full cinematic sequences when combined with
 * {@link ImpactFrameSystem} and {@link DestructionSystem}.</p>
 */
public final class CinematicEffects {

    private final AnimeMagicPlugin plugin;

    public CinematicEffects(AnimeMagicPlugin plugin) { this.plugin = plugin; }

    /**
     * Spawn a directed particle stream that travels from start to end over
     * the given duration. Particles spawn at increasing positions along the
     * path and have velocity in the direction of travel (so they streak,
     * not just appear).
     *
     * @param particle The particle type
     * @param start Origin
     * @param end Destination
     * @param durationTicks Total travel time
     * @param density Particles per tick
     * @param color DustOptions for DUST particles (null for others)
     */
    public void directedStream(@NotNull Particle particle, @NotNull Location start, @NotNull Location end,
                                int durationTicks, int density, Object color) {
        if (start.getWorld() == null) return;
        Vector direction = end.toVector().subtract(start.toVector());
        double totalDistance = direction.length();
        direction.normalize();
        World world = start.getWorld();

        new BukkitRunnable() {
            int tick = 0;
            @Override public void run() {
                if (tick >= durationTicks || world == null) { cancel(); return; }
                double progress = (double) tick / durationTicks;
                Location current = start.clone().add(direction.clone().multiply(totalDistance * progress));
                // Spawn particles at current position with velocity in the travel direction
                for (int i = 0; i < Math.min(3, density); i++) {
                    // Small random spread perpendicular to travel direction
                    Vector perp1 = new Vector(-direction.getZ(), 0, direction.getX()).normalize().multiply((Math.random() - 0.5) * 0.3);
                    Vector perp2 = new Vector(0, 1, 0).multiply((Math.random() - 0.5) * 0.3);
                    Location spawnAt = current.clone().add(perp1).add(perp2);
                    if (particle == Particle.DUST && color != null) {
                        try { world.spawnParticle(particle, spawnAt, 1, direction.getX() * 0.3, direction.getY() * 0.3, direction.getZ() * 0.3, 0.0, color); } catch (Throwable ignored) {}
                    } else {
                        try { world.spawnParticle(particle, spawnAt, 1, direction.getX() * 0.3, direction.getY() * 0.3, direction.getZ() * 0.3, 0.0); } catch (Throwable ignored) {}
                    }
                }
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * Spawn an escalating build-up effect — particles converge on a center point
     * with increasing density and pitch over the duration.
     *
     * @param center The point particles converge toward
     * @param durationTicks Build-up duration
     * @param particle The particle type for converging particles
     * @param sound The sound to play (escalating pitch)
     */
    public void buildUp(@NotNull Location center, int durationTicks, @NotNull Particle particle, @NotNull Sound sound) {
        if (center.getWorld() == null) return;
        World world = center.getWorld();

        new BukkitRunnable() {
            int tick = 0;
            @Override public void run() {
                if (tick >= durationTicks || world == null) { cancel(); return; }
                double progress = (double) tick / durationTicks;
                // Increasing density (1 to 6 particles per tick)
                int count = Math.min(3, 1 + (int)(progress * 5));
                // Decreasing radius (3.0 → 0.3)
                double radius = 3.0 * (1.0 - progress) + 0.3;
                // Converging particles
                for (int i = 0; i < count; i++) {
                    double angle = (tick * 0.5) + (i * Math.PI * 2 / count);
                    double y = Math.random() * 2.0;
                    Location from = center.clone().add(Math.cos(angle) * radius, y - 1.0, Math.sin(angle) * radius);
                    Vector toCenter = center.toVector().subtract(from.toVector()).normalize().multiply(0.2);
                    try { world.spawnParticle(particle, from, 1, toCenter.getX(), toCenter.getY(), toCenter.getZ(), 0.02); } catch (Throwable ignored) {}
                }
                // Escalating sound
                if (tick % 4 == 0) {
                    float pitch = 0.5f + (float)(progress * 1.5f);
                    world.playSound(center, sound, 0.3f + (float)(progress * 0.7f), pitch);
                }
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * Spawn a ground-traveling shockwave ring that expands outward from center.
     * Unlike the particle engine's RingBurst, this one actually travels along
     * the ground and kicks up dust at each position.
     *
     * @param center Center of the shockwave
     * @param maxRadius Final radius
     * @param durationTicks Expansion time
     * @param particle The particle type for the ring
     */
    public void shockwaveRing(@NotNull Location center, double maxRadius, int durationTicks, @NotNull Particle particle) {
        if (center.getWorld() == null) return;
        World world = center.getWorld();

        new BukkitRunnable() {
            int tick = 0;
            @Override public void run() {
                if (tick >= durationTicks || world == null) { cancel(); return; }
                double progress = (double) tick / durationTicks;
                double radius = maxRadius * progress;
                // Ring of particles at current radius
                int segments = 3;
                for (int i = 0; i < segments; i++) {
                    double angle = (i * Math.PI * 2 / segments) + (tick * 0.1);
                    Location ringPoint = center.clone().add(Math.cos(angle) * radius, 0.2, Math.sin(angle) * radius);
                    try { world.spawnParticle(particle, ringPoint, 1, 0.1, 0.1, 0.1, 0.05); } catch (Throwable ignored) {}
                    // Kick up dust
                    if (i % 3 == 0) {
                        try { world.spawnParticle(Particle.CLOUD, ringPoint, 1, 0.05, 0.1, 0.05, 0.02); } catch (Throwable ignored) {}
                    }
                }
                // Sound at each ring expansion step
                if (tick % 3 == 0) {
                    world.playSound(center, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.4f * (1.0f - (float)progress), 0.4f);
                }
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * Spawn lingering aftermath particles — smoke, embers, or dust that persist
     * for a duration after the main impact. Great for making explosions feel
     * like they have weight.
     *
     * @param center Center of the aftermath
     * @param durationTicks How long the aftermath lasts
     * @param type Aftermath type: "smoke", "embers", "dust"
     */
    public void aftermath(@NotNull Location center, int durationTicks, @NotNull String type) {
        if (center.getWorld() == null) return;
        World world = center.getWorld();

        new BukkitRunnable() {
            int tick = 0;
            @Override public void run() {
                if (tick >= durationTicks || world == null) { cancel(); return; }
                double progress = (double) tick / durationTicks;
                double fade = 1.0 - progress; // fades out over time

                switch (type.toLowerCase()) {
                    case "smoke" -> {
                        try { world.spawnParticle(Particle.LARGE_SMOKE, center, 1, 0.8, 0.5, 0.8, 0.02); } catch (Throwable ignored) {}
                        if (tick % 5 == 0) try { world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, center, 1, 0.5, 0.3, 0.5, 0.01); } catch (Throwable ignored) {}
                    }
                    case "embers" -> {
                        try { world.spawnParticle(Particle.FLAME, center, 1, 0.6, 0.6, 0.6, 0.03); } catch (Throwable ignored) {}
                        if (tick % 3 == 0) try { world.spawnParticle(Particle.LAVA, center, 1, 0.3, 0.3, 0.3, 0.0); } catch (Throwable ignored) {}
                    }
                    case "dust" -> {
                        try { world.spawnParticle(Particle.CLOUD, center, 1, 0.8, 0.2, 0.8, 0.01); } catch (Throwable ignored) {}
                        if (tick % 4 == 0) try { world.spawnParticle(Particle.SMOKE, center, 1, 0.5, 0.1, 0.5, 0.01); } catch (Throwable ignored) {}
                    }
                }
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * Spawn an energy charge — particles spiral inward toward a center point,
     * increasing in speed and density. Used as a wind-up for ultimates.
     *
     * @param center The point energy converges to
     * @param durationTicks Charge duration
     * @param particle Particle type for the energy
     * @param color DustOptions for DUST particles (null for others)
     */
    public void energyCharge(@NotNull Location center, int durationTicks, @NotNull Particle particle, Object color) {
        if (center.getWorld() == null) return;
        World world = center.getWorld();

        new BukkitRunnable() {
            int tick = 0;
            @Override public void run() {
                if (tick >= durationTicks || world == null) { cancel(); return; }
                double progress = (double) tick / durationTicks;
                int arms = 3;
                int density = Math.min(3, 2 + (int)(progress * 4));
                double radius = 4.0 * (1.0 - progress) + 0.5;

                for (int arm = 0; arm < arms; arm++) {
                    double baseAngle = arm * (Math.PI * 2 / arms) + (tick * 0.3);
                    for (int i = 0; i < density; i++) {
                        double angle = baseAngle + (i * 0.2);
                        double r = radius * (1.0 - (double)i / density);
                        double y = Math.sin(tick * 0.1 + arm) * 0.5 + 1.0;
                        Location from = center.clone().add(Math.cos(angle) * r, y, Math.sin(angle) * r);
                        Vector inward = center.toVector().subtract(from.toVector()).normalize().multiply(0.15);
                        if (particle == Particle.DUST && color != null) {
                            try { world.spawnParticle(particle, from, 1, inward.getX(), inward.getY(), inward.getZ(), 0.0, color); } catch (Throwable ignored) {}
                        } else {
                            try { world.spawnParticle(particle, from, 1, inward.getX(), inward.getY(), inward.getZ(), 0.0); } catch (Throwable ignored) {}
                        }
                    }
                }
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
}
