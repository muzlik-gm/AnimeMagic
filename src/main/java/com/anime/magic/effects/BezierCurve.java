package com.anime.magic.effects;

import com.anime.magic.AnimeMagicPlugin;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

/**
 * Draws a particle stream along a cubic Bezier curve from start to end with two control
 * points. Used for spell projectile trails (Chidori, Magicule Blade, fireballs) where a
 * curved trajectory looks more dynamic than a straight line.
 */
public final class BezierCurve extends Animation3D {
    private final Vector p0, p1, p2, p3;
    private final Particle particle;
    private final double particleSpeed;
    private final int densityPerTick;

    public BezierCurve(@NotNull AnimeMagicPlugin plugin, @NotNull Player owner,
                       @NotNull Location start, @NotNull Location end,
                       @NotNull Vector control1, @NotNull Vector control2,
                       @NotNull Particle particle, int durationTicks,
                       int densityPerTick, double particleSpeed) {
        super(plugin, owner, durationTicks);
        this.p0 = start.toVector();
        this.p3 = end.toVector();
        this.p1 = p0.clone().add(control1);
        this.p2 = p3.clone().add(control2);
        this.particle = particle;
        this.particleSpeed = particleSpeed;
        this.densityPerTick = densityPerTick;
    }

    public BezierCurve(@NotNull AnimeMagicPlugin plugin, @NotNull Player owner,
                       @NotNull Location start, @NotNull Location end,
                       @NotNull Particle particle, int durationTicks, double arcHeight) {
        this(plugin, owner, start, end,
                new Vector(0, arcHeight, 0), new Vector(0, arcHeight, 0),
                particle, durationTicks, 4, 0.02);
    }

    @Override protected void onTick(int tick) {
        // Only draw the NEW segment(s) for this tick — the previous implementation
        // restarted at t=0 every tick, redrawing the entire trail and producing
        // O(n²) particle counts (a 25-tick curve with density 4 drew ~5000
        // particles total, exhausting the per-tick particle budget silently).
        if (maxTicks() <= 0) return;
        double progressPrev = (double) (tick - 1) / maxTicks();
        double progressNow = (double) tick / maxTicks();
        double upto = Math.min(1.0, progressNow + (1.0 / maxTicks()));
        double from = Math.max(0.0, progressPrev);
        if (upto <= from) return;
        // Draw a fixed number of particles spanning [from, upto] — proportional
        // density per tick without cumulative redraw.
        int segments = Math.max(2, densityPerTick);
        for (int i = 0; i <= segments; i++) {
            double t = from + (upto - from) * ((double) i / segments);
            Vector v = bezier(t);
            Location loc = new Location(owner.getWorld(), v.getX(), v.getY(), v.getZ());
            spawn(loc, particle, 1, 0.02, 0.02, 0.02, particleSpeed);
        }
    }

    private Vector bezier(double t) {
        double u = 1 - t;
        double tt = t * t, uu = u * u;
        double uuu = uu * u, ttt = tt * t;
        Vector p = p0.clone().multiply(uuu);
        p.add(p1.clone().multiply(3 * uu * t));
        p.add(p2.clone().multiply(3 * u * tt));
        p.add(p3.clone().multiply(ttt));
        return p;
    }
}
