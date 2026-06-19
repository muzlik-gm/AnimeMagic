package com.anime.magic.effects;

import com.anime.magic.AnimeMagicPlugin;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * A vertically-rising helix of particles around the player. Used for ultimate-skill
 * activation, Mushoku incantation completion, One Piece Haki armament visual.
 * Two counter-rotating helices produce a "DNA strand" silhouette.
 */
public final class HelixEffect extends Animation3D {
    private final Particle particle;
    private final double radius;
    private final double verticalSpeed;
    private final int densityPerTick;
    private final double rotationsPerTick;

    public HelixEffect(@NotNull AnimeMagicPlugin plugin, @NotNull Player owner,
                       @NotNull Particle particle, int durationTicks, double radius,
                       double verticalSpeed, int densityPerTick, double rotationsPerTick) {
        super(plugin, owner, durationTicks);
        this.particle = particle;
        this.radius = radius;
        this.verticalSpeed = verticalSpeed;
        this.densityPerTick = densityPerTick;
        this.rotationsPerTick = rotationsPerTick;
    }

    @Override protected void onTick(int tick) {
        Location base = owner.getLocation();
        double yBase = (tick * verticalSpeed) % 3.0;
        for (int i = 0; i < densityPerTick; i++) {
            double phase = i / (double) densityPerTick;
            double angle = (tick + phase) * rotationsPerTick * 2 * Math.PI;
            for (int strand = 0; strand < 2; strand++) {
                double a = angle + strand * Math.PI;
                double x = Math.cos(a) * radius;
                double z = Math.sin(a) * radius;
                double y = yBase + phase * 3.0;
                Location at = base.clone().add(x, y, z);
                spawn(at, particle, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
    }
}
