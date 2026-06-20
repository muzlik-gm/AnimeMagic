package com.anime.magic.effects;

import com.anime.magic.AnimeMagicPlugin;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Central coordinator for all particle effects. Spawns particles in a version-agnostic
 * way, tracks active Animation3D instances per player (so they can be cancelled on
 * logout), and respects the global particle budget.
 */
public final class ParticleEngine {
    private final AnimeMagicPlugin plugin;
    private final Map<UUID, Deque<Animation3D>> active = new ConcurrentHashMap<>();
    private final AtomicInteger budgetUsed = new AtomicInteger(0);
    private volatile int budgetPerTick;
    private volatile int maxPerPlayer;
    private volatile double renderDistanceSq;
    private volatile int tickInterval;
    private org.bukkit.scheduler.BukkitRunnable ticker;

    public ParticleEngine(AnimeMagicPlugin plugin) {
        this.plugin = plugin;
        reconfigure();
        this.ticker = new org.bukkit.scheduler.BukkitRunnable() {
            @Override public void run() { tickAll(); }
        };
        this.ticker.runTaskTimer(plugin, 1L, tickInterval);
    }

    public void reconfigure() {
        this.budgetPerTick = plugin.getConfig().getInt("effects.particle-budget-per-tick", 4000);
        this.maxPerPlayer = plugin.getConfig().getInt("effects.max-per-player", 8);
        double rd = plugin.getConfig().getDouble("effects.render-distance", 64);
        this.renderDistanceSq = rd * rd;
        this.tickInterval = Math.max(1, plugin.getConfig().getInt("effects.tick-interval", 1));
    }

    public Animation3D play(@NotNull Animation3D anim) {
        UUID owner = anim.ownerId();
        Deque<Animation3D> q = active.computeIfAbsent(owner, k -> new ArrayDeque<>());
        while (q.size() >= maxPerPlayer) {
            Animation3D evicted = q.poll();
            if (evicted != null) evicted.markCancelled();
        }
        q.add(anim);
        anim.start();
        return anim;
    }

    public void cancelAll(UUID playerId) {
        Deque<Animation3D> q = active.remove(playerId);
        if (q != null) q.forEach(Animation3D::markCancelled);
    }

    public void shutdown() {
        if (ticker != null) ticker.cancel();
        active.values().forEach(q -> q.forEach(Animation3D::markCancelled));
        active.clear();
    }

    public void spawn(@NotNull PlayArgs args) {
        if (args.world == null) return;
        if (budgetUsed.incrementAndGet() > budgetPerTick) {
            budgetUsed.decrementAndGet();
            return;
        }
        boolean anyNear = false;
        for (Player p : args.world.getPlayers()) {
            if (p.getLocation().distanceSquared(args.location) <= renderDistanceSq) { anyNear = true; break; }
        }
        if (!anyNear) return;

        // Auto-provide required data for particles that need it (prevents crash on 1.21+)
        Object data = args.data;
        if (data == null) {
            if (args.particle == Particle.DUST) {
                data = new org.bukkit.Particle.DustOptions(org.bukkit.Color.RED, 1.0f);
            } else if (args.particle == Particle.DUST_COLOR_TRANSITION) {
                data = new org.bukkit.Particle.DustTransition(org.bukkit.Color.RED, org.bukkit.Color.ORANGE, 1.0f);
            } else if (args.particle == Particle.SONIC_BOOM || args.particle == Particle.GUST
                    || args.particle == Particle.SHRIEK) {
                data = 0.0f;
            } else if (args.particle == Particle.BLOCK || args.particle == Particle.FALLING_DUST) {
                data = org.bukkit.Material.STONE.createBlockData();
            } else if (args.particle == Particle.ITEM) {
                data = new org.bukkit.inventory.ItemStack(org.bukkit.Material.STONE);
            } else if (args.particle == Particle.ENTITY_EFFECT) {
                data = org.bukkit.Color.WHITE;
            }
        }

        try {
            args.world.spawnParticle(args.particle, args.location, args.count,
                    args.offsetX, args.offsetY, args.offsetZ, args.speed, data);
        } catch (Exception ignored) {
            // Silently skip particles that still fail after data provision
        }
    }

    private void tickAll() {
        budgetUsed.set(0);
        for (var entry : new HashMap<>(active).entrySet()) {
            Deque<Animation3D> q = entry.getValue();
            if (q == null) continue;
            q.removeIf(Animation3D::isFinished);
            for (Animation3D a : q) if (!a.isCancelled()) a.tick();
            if (q.isEmpty()) active.remove(entry.getKey());
        }
    }

    public static final class PlayArgs {
        public @Nullable World world;
        public @NotNull Location location;
        public @NotNull Particle particle;
        public int count = 1;
        public double offsetX, offsetY, offsetZ;
        public double speed = 0;
        public Object data = null;

        public PlayArgs world(World w) { this.world = w; return this; }
        public PlayArgs location(Location l) { this.location = l; return this; }
        public PlayArgs particle(Particle p) { this.particle = p; return this; }
        public PlayArgs count(int c) { this.count = c; return this; }
        public PlayArgs offset(double x, double y, double z) { this.offsetX = x; this.offsetY = y; this.offsetZ = z; return this; }
        public PlayArgs speed(double s) { this.speed = s; return this; }
        public PlayArgs data(Object d) { this.data = d; return this; }
    }
}
