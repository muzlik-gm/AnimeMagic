package com.anime.magic.schools.naruto;

import com.anime.magic.AnimeMagicPlugin;
import com.anime.magic.api.Caster;
import com.anime.magic.api.Spell;
import com.anime.magic.effects.BezierCurve;
import com.anime.magic.effects.RingBurst;
import com.anime.magic.effects.SphereAnimation;
import com.anime.magic.models.ModelDisplay;
import com.anime.magic.util.LocationUtil;
import com.anime.magic.util.SpellEffects;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * <b>Fireball Jutsu v5 — CINEMATIC EDITION</b>
 *
 * <p>Full anime-grade Katon with build-up, directed trail, crater, and scorch:</p>
 *
 * <ol>
 *   <li><b>Hand Seal (20 ticks / 1s):</b> Energy charge converges on player's mouth.
 *       3D fireball_orb model spawns playing charge_v2 animation. Escalating fire
 *       crackle. Flame particles curl around the orb, growing in intensity.</li>
 *   <li><b>Exhale Launch:</b> Orb detaches and travels along a Bezier curve toward
 *       target. Directed flame stream trails behind. Sound: dragon breath.</li>
 *   <li><b>Impact:</b> Impact frame (intensity 1.5) — FLASH + screen shake.
 *       3-layer expanding sphere (FLAME + LAVA + SMOKE). Ring burst of lava.
 *       Crater (radius 2.5). Scorch marks (radius 4). 5s fire on all hit.</li>
 *   <li><b>Aftermath:</b> Embers + smoke for 2 seconds.</li>
 * </ol>
 */
public final class FireballJutsu implements Spell {
    private final AnimeMagicPlugin plugin;

    public FireballJutsu(AnimeMagicPlugin plugin) { this.plugin = plugin; }

    @Override public @NotNull String id() { return "naruto:fireball"; }
    @Override public @NotNull String displayName() { return "§6§lKaton §8» §c§lGōkakyū no Jutsu"; }
    @Override public @NotNull SchoolId school() { return SchoolId.NARUTO; }
    @Override public int manaCost() { return 30; }
    @Override public long cooldownMs() { return 5000; }
    @Override public int requiredLevel() { return 5; }
    @Override public @NotNull String description() {
        return "Exhale a great fireball. Build-up + directed trail + crater + scorch marks + impact frame.";
    }
    @Override public @NotNull SpellIcon icon() {
        return new SpellIcon("FIRE_CHARGE", 3001, "§cFireball Jutsu");
    }

    @Override
    public boolean cast(@NotNull Caster caster) {
        Player p = caster.player();
        var effects = plugin.getCinematicEffects();

        // Phase 1: Hand seal — energy charge + orb model
        Location mouthLoc = p.getEyeLocation().add(p.getLocation().getDirection().multiply(0.6));
        effects.energyCharge(mouthLoc, 20, Particle.FLAME, Sound.BLOCK_FIRE_AMBIENT);
        ModelDisplay orb = SpellEffects.spawnAnimated(plugin, p, "fireball_orb", "animation.fireball.charge_v2",
                mouthLoc, 25, null);

        // Flame particles curling around orb
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (t >= 20) { cancel(); return; }
                Location mouth = p.getEyeLocation().add(p.getLocation().getDirection().multiply(0.6));
                if (mouth.getWorld() == null) return;
                int count = 2 + t / 5;
                for (int i = 0; i < count; i++) {
                    double angle = (t * 0.5) + (i * Math.PI * 2 / count);
                    double r = 0.3 + t * 0.03;
                    Location flame = mouth.clone().add(Math.cos(angle) * r, 0, Math.sin(angle) * r);
                    mouth.getWorld().spawnParticle(Particle.FLAME, flame, 1, 0.05, 0.05, 0.05, 0.02);
                }
                t++;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        // Phase 2 + 3: Launch at 20 ticks
        new BukkitRunnable() {
            @Override public void run() {
                if (orb != null) orb.remove();
                Location start = p.getEyeLocation().add(p.getLocation().getDirection().multiply(0.6));
                LivingEntity target = caster.targetEntity(45);
                Location end = target != null ? target.getEyeLocation()
                        : start.clone().add(p.getLocation().getDirection().multiply(35));

                // Directed flame stream (particles travel WITH the vector)
                effects.directedStream(Particle.FLAME, start, end, 25, 4, null);
                effects.directedStream(Particle.LARGE_SMOKE, start, end, 25, 2, null);

                // Bezier trail for the fireball orb itself
                plugin.getParticleEngine().play(new BezierCurve(plugin, p, start, end, Particle.FLAME, 25, 1.5));

                LocationUtil.sound(start, Sound.ENTITY_ENDER_DRAGON_SHOOT, 1.2f, 0.7f);
                LocationUtil.sound(start, Sound.ENTITY_BLAZE_SHOOT, 1.0f, 0.8f);

                // Phase 4: Impact at end of travel (25 ticks)
                new BukkitRunnable() {
                    @Override public void run() {
                        if (end.getWorld() == null) return;

                        // Impact frame
                        plugin.getImpactFrameSystem().trigger(end, target, 1.5);

                        // 3-layer explosion
                        plugin.getParticleEngine().play(new SphereAnimation(plugin, p, end, Particle.FLAME, 15, 1.0, 5.0, 100));
                        plugin.getParticleEngine().play(new SphereAnimation(plugin, p, end, Particle.LAVA, 12, 0.5, 4.0, 50));
                        plugin.getParticleEngine().play(new SphereAnimation(plugin, p, end, Particle.LARGE_SMOKE, 10, 0.3, 3.0, 40));
                        plugin.getParticleEngine().play(new RingBurst(plugin, p, end, Particle.LAVA, 15, 7.0, 64));

                        LocationUtil.sound(end, Sound.ENTITY_GENERIC_EXPLODE, 1.8f, 0.7f);
                        LocationUtil.sound(end, Sound.ENTITY_DRAGON_FIREBALL_EXPLODE, 1.5f, 0.8f);

                        // Damage + fire
                        double dmg = 12.0 * plugin.getConfig().getDouble("schools.naruto.damage-multiplier", 1.0);
                        List<LivingEntity> hit = LocationUtil.nearbyLiving(end, 5.0, p.getUniqueId());
                        for (LivingEntity e : hit) {
                            e.damage(dmg, p);
                            e.setFireTicks(100);
                            LocationUtil.knockback(e, end, 1.2);
                        }

                        // Destruction — crater + scorch marks
                        plugin.getDestructionSystem().formCrater(end, 2.5);
                        plugin.getDestructionSystem().scorchMark(end, 4.0);

                        // Aftermath — embers + smoke
                        effects.aftermath(end, 40, "embers");
                        effects.aftermath(end, 30, "smoke");
                    }
                }.runTaskLater(plugin, 25L);
            }
        }.runTaskLater(plugin, 20L);

        return true;
    }
}
