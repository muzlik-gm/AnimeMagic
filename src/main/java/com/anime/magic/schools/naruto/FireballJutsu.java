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
 * <b>Fireball Jutsu v2 — Production Overhaul (Katon: Gōkakyū no Jutsu)</b>
 *
 * <p>Multi-phase anime-accurate fireball:</p>
 *
 * <ol>
 *   <li><b>Phase 1 — Hand Seal (15 ticks / 0.75s):</b> Player sneaks to form the seal.
 *       A 3D {@code fireball_orb} model spawns in front of the mouth, playing
 *       {@code animation.fireball.charge_v2}. Flame particles curl around the orb,
 *       growing in intensity. Sound: ambient fire crackle.</li>
 *
 *   <li><b>Phase 2 — Inhale (5 ticks / 0.25s):</b> The orb shrinks slightly and brightens.
 *       Sound: deep inhale.</li>
 *
 *   <li><b>Phase 3 — Exhale Launch:</b> The orb detaches and travels along a Bezier
 *       curve toward the target. Flame particles trail behind. Sound: dragon breath.</li>
 *
 *   <li><b>Phase 4 — Impact:</b> On hit, three expanding spheres (FLAME, LAVA, SMOKE)
 *       ripple outward. All entities within 5 blocks take 12 damage + 4 seconds of fire.
 *       A ring of lava particles expands for the blast wave.</li>
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
        return "Exhale a great fireball that explodes on impact, scorching nearby foes. "
                + "4-phase cast: Hand Seal -> Inhale -> Exhale -> Impact.";
    }
    @Override public @NotNull SpellIcon icon() {
        return new SpellIcon("FIRE_CHARGE", 3001, "§cFireball Jutsu");
    }

    @Override
    public boolean cast(@NotNull Caster caster) {
        Player p = caster.player();

        // Phase 1: Hand seal — spawn fireball orb model with charge animation
        Location mouthLoc = p.getEyeLocation().add(p.getLocation().getDirection().multiply(0.6));
        ModelDisplay orb = SpellEffects.spawnAnimated(plugin, p, "fireball_orb", "animation.fireball.charge_v2",
                mouthLoc, 25, null);

        // Flame particles curling around orb during phase 1
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (t >= 20) { cancel(); return; }
                Location mouth = p.getEyeLocation().add(p.getLocation().getDirection().multiply(0.6));
                if (mouth.getWorld() == null) return;
                // Curl flames
                int count = 2 + t / 5; // 2 to 6 over time
                for (int i = 0; i < count; i++) {
                    double angle = (t * 0.5) + (i * Math.PI * 2 / count);
                    double r = 0.3 + t * 0.03;
                    Location flame = mouth.clone().add(Math.cos(angle) * r, 0, Math.sin(angle) * r);
                    mouth.getWorld().spawnParticle(Particle.FLAME, flame, 1, 0.05, 0.05, 0.05, 0.02);
                }
                if (t % 5 == 0) {
                    LocationUtil.sound(mouth, Sound.BLOCK_FIRE_AMBIENT, 0.4f + t * 0.02f, 0.8f + t * 0.03f);
                }
                t++;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        // Phase 2 + 3: Inhale then launch at 20 ticks
        new BukkitRunnable() {
            @Override public void run() {
                // Brief inhale: orb shrinks + brightens
                if (orb != null && !orb.isDead()) {
                    orb.setTransform(0, 0, 0, 0, 0, 0, 0.7f, 0.7f, 0.7f);
                }
                Location mouth = p.getEyeLocation().add(p.getLocation().getDirection().multiply(0.6));
                LocationUtil.sound(mouth, Sound.ENTITY_PLAYER_BREATH, 1.0f, 0.5f);
                // Launch after 5 ticks
                new BukkitRunnable() {
                    @Override public void run() { launch(caster, p, orb); }
                }.runTaskLater(plugin, 5L);
            }
        }.runTaskLater(plugin, 20L);

        return true;
    }

    private void launch(Caster caster, Player p, ModelDisplay orb) {
        // Remove the orb model — it's "consumed" by the launch
        if (orb != null) orb.remove();

        Location start = p.getEyeLocation().add(p.getLocation().getDirection().multiply(0.6));
        LivingEntity target = caster.targetEntity(45);
        Location end = target != null ? target.getEyeLocation()
                : start.clone().add(p.getLocation().getDirection().multiply(35));

        // Phase 3: Bezier flame trail from mouth to target
        plugin.getParticleEngine().play(
                new BezierCurve(plugin, p, start, end, Particle.FLAME, 25, 1.5));
        // Secondary smoke trail
        plugin.getParticleEngine().play(
                new BezierCurve(plugin, p, start, end, Particle.LARGE_SMOKE, 25, 1.0));

        LocationUtil.sound(start, Sound.ENTITY_ENDER_DRAGON_SHOOT, 1.2f, 0.7f);
        LocationUtil.sound(start, Sound.ENTITY_BLAZE_SHOOT, 1.0f, 0.8f);

        // Phase 4: Impact at end of bezier duration
        new BukkitRunnable() {
            @Override public void run() {
                if (end.getWorld() == null) return;
                // Three expanding spheres
                plugin.getParticleEngine().play(
                        new SphereAnimation(plugin, p, end, Particle.FLAME, 15, 1.0, 5.0, 100));
                plugin.getParticleEngine().play(
                        new SphereAnimation(plugin, p, end, Particle.LAVA, 12, 0.5, 4.0, 50));
                plugin.getParticleEngine().play(
                        new SphereAnimation(plugin, p, end, Particle.LARGE_SMOKE, 10, 0.3, 3.0, 40));
                // Ring burst
                plugin.getParticleEngine().play(
                        new RingBurst(plugin, p, end, Particle.LAVA, 15, 7.0, 64));

                LocationUtil.sound(end, Sound.ENTITY_GENERIC_EXPLODE, 1.8f, 0.7f);
                LocationUtil.sound(end, Sound.ENTITY_DRAGON_FIREBALL_EXPLODE, 1.5f, 0.8f);

                double dmg = 12.0 * plugin.getConfig().getDouble("schools.naruto.damage-multiplier", 1.0);
                List<LivingEntity> hit = LocationUtil.nearbyLiving(end, 5.0, p.getUniqueId());
                for (LivingEntity e : hit) {
                    e.damage(dmg, p);
                    e.setFireTicks(100); // 5 seconds of fire
                    LocationUtil.knockback(e, end, 1.2);
                }
            }
        }.runTaskLater(plugin, 25L);
    }
}
