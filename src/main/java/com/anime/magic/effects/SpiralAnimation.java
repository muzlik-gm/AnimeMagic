package com.anime.magic.effects;

import com.anime.magic.AnimeMagicPlugin;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

/**
 * Particles spiral outward from the owner along their look direction. Used for:
 * Rasengan charge-up, Tensura magicule aura, Haki burst expansion. Two interleaved
 * helices create the characteristic "double helix" look.
 */
public final class SpiralAnimation extends Animation3D {
    private final Particle particle;
    private final double startRadius, endRadius, pitch;
    private final int density;
    private final double rotationsPerTick;

    public SpiralAnimation(@NotNull AnimeMagicPlugin plugin, @NotNull Player owner,
                           @NotNull Particle particle, int durationTicks,
                           double startRadius, double endRadius, double pitch,
                           int density, double rotationsPerTick) {
        super(plugin, owner, durationTicks);
        this.particle = particle;
        this.startRadius = startRadius;
        this.endRadius = endRadius;
        this.pitch = pitch;
        this.density = density;
        this.rotationsPerTick = rotationsPerTick;
    }

    @Override protected void onTick(int tick) {
        double progress = (double) tick / maxTicks();
        double radius = startRadius + (endRadius - startRadius) * progress;
        Location origin = owner.getLocation().add(0, 1.2, 0);
        Vector forward = origin.getDirection().normalize();
        Vector right = forward.clone().crossProduct(new Vector(0, 1, 0));
        if (right.lengthSquared() < 1e-6) right = new Vector(1, 0, 0);
        right.normalize();
        Vector up = right.clone().crossProduct(forward).normalize();

        double angle = tick * rotationsPerTick * 2 * Math.PI;
        for (int i = 0; i < density; i++) {
            double a = angle + (i * 2 * Math.PI / density);
            for (int helix = 0; helix < 2; helix++) {
                double ha = a + helix * Math.PI;
                double x = Math.cos(ha) * radius;
                double y = Math.sin(ha) * radius;
                double z = (tick * pitch) % (pitch * 4) - (pitch * 2);
                Vector offset = right.clone().multiply(x)
                        .add(up.clone().multiply(y))
                        .add(forward.clone().multiply(z));
                Location at = origin.clone().add(offset);
                spawn(at, particle, 1, 0.01, 0.01, 0.01, 0.0);
            }
        }
    }
}
