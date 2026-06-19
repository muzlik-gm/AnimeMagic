package com.anime.magic.cinematic;

import com.anime.magic.AnimeMagicPlugin;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.FallingBlock;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

/**
 * Handles terrain destruction: block breaking, crater formation, scorch marks,
 * and falling debris. Respects config limits and unbreakable-block blacklist.
 *
 * <p>All methods are no-ops if {@code cinematic.destruction.enabled} is false.</p>
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
                .stream().map(s -> Material.valueOf(s.toUpperCase())).toList());
        this.craters = plugin.getConfig().getBoolean("cinematic.destruction.craters", true);
        this.scorchMarks = plugin.getConfig().getBoolean("cinematic.destruction.scorch-marks", true);
    }

    /**
     * Form a circular crater at the given location. Replaces ground blocks with air
     * within the radius, then spawns falling debris from the displaced blocks.
     *
     * @param center The center of the crater (at ground level)
     * @param radius The radius in blocks
     */
    public void formCrater(@NotNull Location center, double radius) {
        if (!enabled || !craters || center.getWorld() == null) return;
        int broken = 0;
        int r = (int) Math.ceil(radius);
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                double dist = Math.sqrt(x * x + z * z);
                if (dist > radius) continue;
                // Depth decreases toward the edge (bowl shape)
                int depth = (int) Math.max(1, (radius - dist) * 0.6);
                for (int y = 0; y < depth; y++) {
                    if (broken >= maxBlocksPerSpell) return;
                    Block b = center.clone().add(x, -y, z).getBlock();
                    if (canBreak(b)) {
                        // Spawn falling debris from the original block
                        spawnDebris(b.getLocation(), b.getType(), b.getBlockData());
                        b.setType(Material.AIR);
                        broken++;
                    }
                }
            }
        }
        // Dust cloud at crater center
        center.getWorld().spawnParticle(Particle.CLOUD, center, 40, radius * 0.5, 0.5, radius * 0.5, 0.05);
        center.getWorld().spawnParticle(Particle.LARGE_SMOKE, center, 30, radius * 0.4, 0.8, radius * 0.4, 0.02);
    }

    /**
     * Leave scorch marks at the location: blackened blocks + fire patches.
     *
     * @param center The center of the scorch
     * @param radius The radius in blocks
     */
    public void scorchMark(@NotNull Location center, double radius) {
        if (!enabled || !scorchMarks || center.getWorld() == null) return;
        int r = (int) Math.ceil(radius);
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                double dist = Math.sqrt(x * x + z * z);
                if (dist > radius) continue;
                Block ground = center.clone().add(x, 0, z).getBlock();
                Block surface = ground.getRelative(BlockFace.UP);
                // Blacken the ground block
                if (canBreak(ground) && !ground.getType().isAir()) {
                    // Random chance to blacken (not every block)
                    if (Math.random() < 0.5) {
                        ground.setType(Material.BLACK_CONCRETE);
                    }
                }
                // Random fire patches
                if (surface.getType().isAir() && Math.random() < 0.2) {
                    surface.setType(Material.FIRE);
                }
            }
        }
    }

    /**
     * Break blocks in a line from start to end (for beam/punch attacks).
     *
     * @param start The starting location
     * @param end The ending location
     */
    public void breakLine(@NotNull Location start, @NotNull Location end) {
        if (!enabled) return;
        if (start.getWorld() == null) return;
        Vector dir = end.toVector().subtract(start.toVector());
        double distance = dir.length();
        dir.normalize();
        int broken = 0;
        for (double d = 0; d <= distance; d += 1.0) {
            if (broken >= maxBlocksPerSpell) return;
            Location loc = start.clone().add(dir.clone().multiply(d));
            Block b = loc.getBlock();
            if (canBreak(b) && !b.getType().isAir()) {
                spawnDebris(b.getLocation(), b.getType(), b.getBlockData());
                b.setType(Material.AIR);
                broken++;
            }
        }
    }

    /**
     * Spawn falling debris from a broken block. The debris flies upward and outward
     * with random velocity, then despawns after 2 seconds.
     */
    private void spawnDebris(@NotNull Location loc, @NotNull Material material, @NotNull BlockData data) {
        if (loc.getWorld() == null) return;
        @SuppressWarnings("deprecation")
        FallingBlock debris = loc.getWorld().spawnFallingBlock(loc, material, (byte) 0);
        double angle = Math.random() * Math.PI * 2;
        double speed = 0.3 + Math.random() * 0.5;
        debris.setVelocity(new Vector(
                Math.cos(angle) * speed,
                0.8 + Math.random() * 0.6,
                Math.sin(angle) * speed
        ));
        debris.setDropItem(false);
        debris.setHurtEntities(false);
        // Remove after 40 ticks (2s)
        new BukkitRunnable() {
            @Override public void run() {
                if (debris.isValid()) debris.remove();
            }
        }.runTaskLater(plugin, 40L);
    }

    private boolean canBreak(@NotNull Block b) {
        if (b.getType().isAir()) return false;
        if (unbreakable.contains(b.getType())) return false;
        // Respect WorldGuard if installed
        // (WorldGuard integration would go here if WG is present)
        return true;
    }
}
