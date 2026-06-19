package com.anime.magic.effects;

import com.anime.magic.AnimeMagicPlugin;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import java.util.UUID;

/**
 * Skeleton for any 3D particle animation. Implementations override onTick(int) which
 * is called once per server tick. They receive a 0-indexed tick counter and compute
 * their own particle positions.
 */
public abstract class Animation3D {
    protected final AnimeMagicPlugin plugin;
    protected final Player owner;
    private final UUID ownerId;
    private int tick;
    private boolean cancelled;
    private boolean finished;
    private final int maxTicks;

    protected Animation3D(@NotNull AnimeMagicPlugin plugin, @NotNull Player owner, int maxTicks) {
        this.plugin = plugin;
        this.owner = owner;
        this.ownerId = owner.getUniqueId();
        this.maxTicks = maxTicks;
    }

    protected void start() {}
    protected abstract void onTick(int tick);

    final void tick() {
        if (cancelled || finished) return;
        onTick(tick);
        tick++;
        if (tick >= maxTicks) finish();
    }

    protected final void spawn(@NotNull Location loc, @NotNull Particle particle) {
        spawn(loc, particle, 1, 0, 0, 0, 0);
    }

    protected final void spawn(@NotNull Location loc, @NotNull Particle particle,
                                int count, double ox, double oy, double oz, double speed) {
        World w = loc.getWorld();
        if (w == null) return;
        plugin.getParticleEngine().spawn(
                new ParticleEngine.PlayArgs()
                        .world(w).location(loc.clone()).particle(particle)
                        .count(count).offset(ox, oy, oz).speed(speed));
    }

    protected final void finish() { this.finished = true; }
    public final void markCancelled() { this.cancelled = true; this.finished = true; }
    public final boolean isCancelled() { return cancelled; }
    public final boolean isFinished() { return finished; }
    public final UUID ownerId() { return ownerId; }
    public final int currentTick() { return tick; }
    public final int maxTicks() { return maxTicks; }
}
