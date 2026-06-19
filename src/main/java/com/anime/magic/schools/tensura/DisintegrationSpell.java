package com.anime.magic.schools.tensura;

import com.anime.magic.AnimeMagicPlugin;
import com.anime.magic.api.Caster;
import com.anime.magic.api.Spell;
import com.anime.magic.effects.BezierCurve;
import com.anime.magic.effects.SphereAnimation;
import com.anime.magic.util.LocationUtil;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

/**
 * <b>Disintegration — Soul Series: Disintegration</b>
 *
 * <p>Fires a continuous beam of magicule energy that disintegrates everything in its path.</p>
 *
 * <p>For 2 seconds, a beam of DRAGON_BREATH + SMOKE particles extends 25 blocks from the
 * caster. Any entity caught in the beam takes 5 damage per tick (200 total possible).
 * The beam sways slowly, sweeping across the battlefield.</p>
 */
public final class DisintegrationSpell implements Spell {
    private final AnimeMagicPlugin plugin;

    public DisintegrationSpell(AnimeMagicPlugin plugin) { this.plugin = plugin; }

    @Override public @NotNull String id() { return "tensura:disintegration"; }
    @Override public @NotNull String displayName() { return "§5§lSoul §8» §d§lDisintegration"; }
    @Override public @NotNull Spell.SchoolId school() { return Spell.SchoolId.TENSURA; }
    @Override public int manaCost() { return 100; }
    @Override public long cooldownMs() { return 25000; }
    @Override public int requiredLevel() { return 35; }
    @Override public @NotNull String description() {
        return "Fire a 25-block beam of disintegration energy for 2s. 5 damage/tick to anything in the beam.";
    }
    @Override public @NotNull Spell.SpellIcon icon() {
        return new SpellIcon("BLACK_DYE", 4002, "§dDisintegration");
    }

    @Override
    public boolean cast(@NotNull Caster caster) {
        Player p = caster.player();

        new BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                if (ticks >= 40) { cancel(); return; }
                // Beam from player eye, sweeping slightly
                Location eye = p.getEyeLocation();
                double sweep = Math.sin(ticks * 0.15) * 15; // ±15 degrees sweep
                Location dir = eye.clone();
                dir.setYaw(dir.getYaw() + (float) sweep);
                Location end = eye.clone().add(dir.getDirection().multiply(25));

                // Beam particles along the line
                for (double d = 0; d <= 1.0; d += 0.02) {
                    Location loc = eye.clone().add(end.toVector().subtract(eye.toVector()).multiply(d));
                    if (loc.getWorld() == null) continue;
                    loc.getWorld().spawnParticle(Particle.DRAGON_BREATH, loc, 1, 0.1, 0.1, 0.1, 0.0);
                    loc.getWorld().spawnParticle(Particle.SMOKE, loc, 1, 0.05, 0.05, 0.05, 0.0);
                }
                // Damage any entity near the beam line
                for (double d = 0; d <= 1.0; d += 0.1) {
                    Location loc = eye.clone().add(end.toVector().subtract(eye.toVector()).multiply(d));
                    for (LivingEntity e : LocationUtil.nearbyLiving(loc, 1.0, p.getUniqueId())) {
                        e.damage(5.0, p);
                    }
                }
                if (ticks % 5 == 0) {
                    LocationUtil.sound(eye, Sound.ENTITY_DRAGON_FIREBALL_EXPLODE, 0.5f, 1.5f);
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        return true;
    }
}
