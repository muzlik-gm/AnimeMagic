package com.anime.magic.util;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

/** Geometry helpers for spell targeting and effect placement. */
public final class LocationUtil {
    private LocationUtil() {}

    /**
     * Default hostile-targeting predicate: skip ArmorStands, dead entities,
     * spectators, creative-mode players, and invulnerable entities. This prevents
     * spells from killing NPCs, decorative armor stands, and admins.
     */
    public static final Predicate<LivingEntity> COMBAT_TARGET_FILTER = e -> {
        if (e instanceof ArmorStand) return false;       // Decorative / NPC displays
        if (e.isDead()) return false;                    // Already dying
        if (e.isInvulnerable()) return false;            // God-mode NPCs
        if (e instanceof Player p) {
            GameMode gm = p.getGameMode();
            if (gm == GameMode.SPECTATOR || gm == GameMode.CREATIVE) return false;
        }
        return true;
    };

    /**
     * Find all living entities within {@code radius} of {@code center} (excluding
     * the player identified by {@code exclude}), filtered to valid combat targets.
     *
     * <p>Uses {@link org.bukkit.World#getNearbyEntities} (bounding-box, cheap) and
     * then filters by spherical distance + the {@link #COMBAT_TARGET_FILTER}
     * predicate. This is dramatically cheaper than {@code getEntitiesByClass} on
     * large worlds.</p>
     */
    public static List<LivingEntity> nearbyLiving(Location center, double radius, UUID exclude) {
        List<LivingEntity> result = new ArrayList<>();
        if (center.getWorld() == null) return result;
        double r2 = radius * radius;
        Collection<?> nearby = center.getWorld().getNearbyEntities(center, radius, radius, radius);
        for (Object o : nearby) {
            if (!(o instanceof LivingEntity e)) continue;
            if (exclude != null && e.getUniqueId().equals(exclude)) continue;
            if (!COMBAT_TARGET_FILTER.test(e)) continue;
            if (e.getLocation().distanceSquared(center) <= r2) result.add(e);
        }
        return result;
    }

    public static Location inFront(Location origin, double distance) {
        Vector dir = safeNormalize(origin.getDirection());
        return origin.clone().add(dir.multiply(distance));
    }

    public static void knockback(LivingEntity target, Location origin, double strength) {
        Location t = target.getLocation();
        Vector v = new Vector(t.getX() - origin.getX(), 0, t.getZ() - origin.getZ());
        if (v.lengthSquared() < 1e-6) v = new Vector(0, 0, 1);
        v = safeNormalize(v).multiply(strength).setY(0.3);
        target.setVelocity(v);
    }

    public static void sound(Location loc, Sound sound, float volume, float pitch) {
        if (loc.getWorld() == null) return;
        loc.getWorld().playSound(loc, sound, volume, pitch);
    }

    /**
     * Normalize that returns the zero vector instead of (NaN, NaN, NaN) when the
     * input has zero length. Bukkit's {@link Vector#normalize()} divides by
     * length with no guard, producing NaN which then propagates into velocity
     * packets and kicks the player ("Invalid move player packet").
     */
    public static Vector safeNormalize(Vector v) {
        if (v == null) return new Vector(0, 0, 0);
        double lenSq = v.lengthSquared();
        if (lenSq < 1e-9) return new Vector(0, 0, 0);
        double len = Math.sqrt(lenSq);
        return new Vector(v.getX() / len, v.getY() / len, v.getZ() / len);
    }

    /** Same as {@link #safeNormalize} but mutates and returns the input vector. */
    public static Vector safeNormalizeInPlace(Vector v) {
        if (v == null) return new Vector(0, 0, 0);
        double lenSq = v.lengthSquared();
        if (lenSq < 1e-9) {
            v.setX(0); v.setY(0); v.setZ(0);
            return v;
        }
        double len = Math.sqrt(lenSq);
        v.setX(v.getX() / len);
        v.setY(v.getY() / len);
        v.setZ(v.getZ() / len);
        return v;
    }
}
