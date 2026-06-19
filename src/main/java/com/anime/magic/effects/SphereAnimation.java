package com.anime.magic.effects;

import com.anime.magic.AnimeMagicPlugin;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Renders a sphere (or hollow shell) of particles that expands from the owner. Used for
 * explosion-style spells, Conqueror's Haki burst, Saint-level Mushoku spells. Uses a
 * Fibonacci sphere distribution for even particle density regardless of radius.
 */
public final class SphereAnimation extends Animation3D {
    private final Particle particle;
    private final double startRadius, endRadius;
    private final int density;
    private final Location center;

    public SphereAnimation(@NotNull AnimeMagicPlugin plugin, @NotNull Player owner,
                           @NotNull Location center, @NotNull Particle particle,
                           int durationTicks, double startRadius, double endRadius, int density) {
        super(plugin, owner, durationTicks);
        this.center = center.clone();
        this.particle = particle;
        this.startRadius = startRadius;
        this.endRadius = endRadius;
        this.density = density;
    }

    @Override protected void onTick(int tick) {
        double progress = (double) tick / maxTicks();
        double radius = startRadius + (endRadius - startRadius) * progress;
        double golden = Math.PI * (3.0 - Math.sqrt(5.0));
        for (int i = 0; i < density; i++) {
            double y = 1 - (i / (double) (density - 1)) * 2;
            double r = Math.sqrt(1 - y * y);
            double theta = golden * i + (tick * 0.1);
            double x = Math.cos(theta) * r;
            double z = Math.sin(theta) * r;
            Location at = center.clone().add(x * radius, y * radius, z * radius);
            spawn(at, particle, 1, 0.02, 0.02, 0.02, 0.02);
        }
    }
}
