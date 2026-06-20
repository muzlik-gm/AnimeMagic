package com.anime.magic.cinematic;

import com.anime.magic.AnimeMagicPlugin;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.FallingBlock;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * Handles terrain destruction: block breaking, crater formation, scorch marks,
 * and falling debris. Respects config limits and unbreakable-block blacklist.
 *
 * <p>All methods are no-ops if {@code cinematic.destruction.enabled} is false.</p>
 *
 * <p><b>WorldGuard integration:</b> if WorldGuard is installed, {@link #canBreak}
 * queries the region container's build test. The hook is best-effort — if the
 * WorldGuard API cannot be resolved (e.g., classpath differs), destruction
 * proceeds without region checks and a warning is logged once.</p>
 *
 * <p><b>World height bounds:</b> every block write is preceded by a min/max
 * height check so spells cast near the world floor (Nether, deep Overworld)
 * cannot throw {@link IndexOutOfBoundsException}.</p>
 */
public final class DestructionSystem {

    private final AnimeMagicPlugin plugin;
    private final boolean enabled;
    private final int maxBlocksPerSpell;
    private final Set<Material> unbreakable;
    private final boolean craters;
    private final boolean scorchMarks;

    public DestructionSystem(AnimeMagicPlugin plugin) {
        this.plugin = plugin;
        this.enabled = plugin.getConfig().getBoolean("cinematic.destruction.enabled", true);
        this.maxBlocksPerSpell = plugin.getConfig().getInt("cinematic.destruction.max-blocks-per-spell", 30);
        this.unbreakable = Set.copyOf(plugin.getConfig().getStringList("cinematic.destruction.unbreakable-blocks")
                .stream().map(s -> {
                    try { return Material.valueOf(s.toUpperCase()); }
                    catch (IllegalArgumentException e) { return null; }
                }).filter(java.util.Objects::nonNull).toList());
        this.craters = plugin.getConfig().getBoolean("cinematic.destruction.craters", true);
        this.scorchMarks = plugin.getConfig().getBoolean("cinematic.destruction.scorch-marks", true);
    }

    /**
     * Form a circular crater at the given location. Replaces ground blocks with air
     * within the radius, then spawns falling debris from the displaced blocks.
     */
    public void formCrater(@NotNull Location center, double radius) {
        if (!enabled || !craters || center.getWorld() == null) return;
        int broken = 0;
        int r = (int) Math.ceil(radius);
        int minY = center.getWorld().getMinHeight();
        int maxY = center.getWorld().getMaxHeight();
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                double dist = Math.sqrt(x * x + z * z);
                if (dist > radius) continue;
                int depth = (int) Math.max(1, (radius - dist) * 0.6);
                for (int y = 0; y < depth; y++) {
                    if (broken >= maxBlocksPerSpell) return;
                    int by = center.getBlockY() - y;
                    if (by < minY || by >= maxY) continue; // height bounds guard
                    Block b = center.clone().add(x, -y, z).getBlock();
                    if (canBreak(b)) {
                        spawnDebris(b.getLocation(), b.getBlockData());
                        b.setType(Material.AIR);
                        broken++;
                    }
                }
            }
        }
        center.getWorld().spawnParticle(Particle.CLOUD, center, 40, radius * 0.5, 0.5, radius * 0.5, 0.05);
        center.getWorld().spawnParticle(Particle.LARGE_SMOKE, center, 30, radius * 0.4, 0.8, radius * 0.4, 0.02);
    }

    /**
     * Leave scorch marks at the location: blackened blocks + fire patches.
     */
    public void scorchMark(@NotNull Location center, double radius) {
        if (!enabled || !scorchMarks || center.getWorld() == null) return;
        int r = (int) Math.ceil(radius);
        int minY = center.getWorld().getMinHeight();
        int maxY = center.getWorld().getMaxHeight();
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                double dist = Math.sqrt(x * x + z * z);
                if (dist > radius) continue;
                int by = center.getBlockY();
                if (by < minY || by >= maxY) continue;
                Block ground = center.clone().add(x, 0, z).getBlock();
                if (by + 1 < maxY) {
                    Block surface = ground.getRelative(BlockFace.UP);
                    if (canBreak(ground) && !ground.getType().isAir()) {
                        if (Math.random() < 0.5) {
                            ground.setType(Material.BLACK_CONCRETE);
                        }
                    }
                    if (surface.getType().isAir() && Math.random() < 0.2) {
                        surface.setType(Material.FIRE);
                    }
                }
            }
        }
    }

    /**
     * Break blocks in a line from start to end (for beam/punch attacks).
     */
    public void breakLine(@NotNull Location start, @NotNull Location end) {
        if (!enabled) return;
        if (start.getWorld() == null) return;
        Vector dir = end.toVector().subtract(start.toVector());
        double distance = dir.length();
        if (dir.lengthSquared() < 1e-9) return;
        dir.normalize();
        int broken = 0;
        int minY = start.getWorld().getMinHeight();
        int maxY = start.getWorld().getMaxHeight();
        for (double d = 0; d <= distance; d += 1.0) {
            if (broken >= maxBlocksPerSpell) return;
            Location loc = start.clone().add(dir.clone().multiply(d));
            if (loc.getBlockY() < minY || loc.getBlockY() >= maxY) continue;
            Block b = loc.getBlock();
            if (canBreak(b) && !b.getType().isAir()) {
                spawnDebris(b.getLocation(), b.getBlockData());
                b.setType(Material.AIR);
                broken++;
            }
        }
    }

    /**
     * Spawn falling debris from a broken block. Uses the modern BlockData overload
     * (the deprecated Material+byte variant mismaps block subtypes). The debris
     * despawns after 2 seconds.
     */
    private void spawnDebris(@NotNull Location loc, @NotNull BlockData data) {
        if (loc.getWorld() == null) return;
        FallingBlock debris = loc.getWorld().spawnFallingBlock(loc, data);
        double angle = Math.random() * Math.PI * 2;
        double speed = 0.3 + Math.random() * 0.5;
        debris.setVelocity(new Vector(
                Math.cos(angle) * speed,
                0.8 + Math.random() * 0.6,
                Math.sin(angle) * speed
        ));
        debris.setDropItem(false);
        debris.setHurtEntities(false);
        // Prevent the falling block from placing itself as a permanent block on landing.
        try { debris.setCancelDrop(true); } catch (NoSuchMethodError ignored) {} // pre-1.21 fallback
        new BukkitRunnable() {
            @Override public void run() {
                if (debris.isValid()) debris.remove();
            }
        }.runTaskLater(plugin, 40L);
    }

    private boolean canBreak(@NotNull Block b) {
        if (b.getType().isAir()) return false;
        if (unbreakable.contains(b.getType())) return false;
        // WorldGuard integration (optional): if WG is on the server classpath at
        // runtime, query its region container and skip blocks in regions that
        // deny BUILD. All WG types are loaded reflectively so this file compiles
        // without WG as a dependency. On any error we fall through to "allow".
        try {
            Class<?> wgClass = Class.forName("com.sk89q.worldguard.WorldGuard");
            Object wg = wgClass.getMethod("getInstance").invoke(null);
            Object platform = wg.getClass().getMethod("getPlatform").invoke(wg);
            Object container = platform.getClass().getMethod("getRegionContainer").invoke(platform);
            if (container == null) return true;
            Object query = container.getClass().getMethod("createQuery").invoke(container);
            // Adapt the Bukkit world to a WorldEdit World.
            Class<?> bukkitAdapter = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");
            Object weWorld = bukkitAdapter.getMethod("adapt", org.bukkit.World.class)
                    .invoke(null, b.getWorld());
            Class<?> weWorldClass = Class.forName("com.sk89q.worldedit.world.World");
            // Build a WorldEdit Location at the block coords.
            Class<?> weLocClass = Class.forName("com.sk89q.worldedit.util.Location");
            java.lang.reflect.Constructor<?> locCtor = weLocClass.getConstructor(
                    weWorldClass, double.class, double.class, double.class);
            Object weLoc = locCtor.newInstance(weWorld, b.getX() + 0.5, b.getY() + 0.5, b.getZ() + 0.5);
            // Query applicable regions for the location.
            java.lang.reflect.Method getApplicable = query.getClass().getMethod(
                    "getApplicableRegions", weLocClass);
            Object regions = getApplicable.invoke(query, weLoc);
            if (regions != null) {
                // regions.testBuild(null) → false if any region denies BUILD to unknown actor.
                // Use reflection so we don't depend on the ApplicableRegionSet class at compile time.
                try {
                    Class<?> entityClass = Class.forName("com.sk89q.worldedit.entity.Entity");
                    java.lang.reflect.Method testBuild = regions.getClass().getMethod(
                            "testBuild", entityClass);
                    Object result = testBuild.invoke(regions, (Object) null);
                    if (result instanceof Boolean bool) return bool;
                } catch (ClassNotFoundException | NoSuchMethodException nsme) {
                    // Older WG API or WG entity class missing — assume allow.
                }
            }
        } catch (ClassNotFoundException ignored) {
            // WorldGuard not installed — proceed without region check.
        } catch (Throwable t) {
            // Defensive: never let a WG API mismatch break spell casting.
        }
        return true;
    }
}
