package com.anime.magic.util;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Geometry helpers for spell targeting and effect placement. */
public final class LocationUtil {
    private LocationUtil() {}

    public static List<LivingEntity> nearbyLiving(Location center, double radius, UUID exclude) {
        List<LivingEntity> result = new ArrayList<>();
        if (center.getWorld() == null) return result;
        double r2 = radius * radius;
        Collection<LivingEntity> all = center.getWorld().getEntitiesByClass(LivingEntity.class);
        for (LivingEntity e : all) {
            if (exclude != null && e.getUniqueId().equals(exclude)) continue;
            if (e.getLocation().distanceSquared(center) <= r2) result.add(e);
        }
        return result;
    }

    public static Location inFront(Location origin, double distance) {
        Vector dir = origin.getDirection().normalize();
        return origin.clone().add(dir.multiply(distance));
    }

    public static void knockback(LivingEntity target, Location origin, double strength) {
        Location t = target.getLocation();
        Vector v = new Vector(t.getX() - origin.getX(), 0, t.getZ() - origin.getZ());
        if (v.lengthSquared() < 1e-6) v = new Vector(0, 0, 1);
        v.normalize().multiply(strength).setY(0.3);
        target.setVelocity(v);
    }

    public static void sound(Location loc, Sound sound, float volume, float pitch) {
        if (loc.getWorld() == null) return;
        loc.getWorld().playSound(loc, sound, volume, pitch);
    }
}
