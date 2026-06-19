package com.anime.magic.effects;

import com.anime.magic.AnimeMagicPlugin;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * An expanding ring of particles in the horizontal plane, used for ground impact effects
 * (Chidori land, fireball blast wave). The ring grows from radius 0 to maxRadius over
 * the animation lifetime.
 */
public final class RingBurst extends Animation3D {
    private final Particle particle;
    private final double maxRadius;
    private final int density;
    private final Location center;

    public RingBurst(@NotNull AnimeMagicPlugin plugin, @NotNull Player owner,
                     @NotNull Location center, @NotNull Particle particle,
                     int durationTicks, double maxRadius, int density) {
        super(plugin, owner, durationTicks);
        this.center = center.clone();
        this.particle = particle;
        this.maxRadius = maxRadius;
        this.density = density;
    }

    @Override protected void onTick(int tick) {
        double progress = (double) tick / maxTicks();
        double radius = maxRadius * progress;
        for (int i = 0; i < density; i++) {
            double angle = (i * 2 * Math.PI / density) + (tick * 0.05);
            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;
            Location at = center.clone().add(x, 0.1, z);
            spawn(at, particle, 1, 0.05, 0.0, 0.05, 0.0);
        }
    }
}
