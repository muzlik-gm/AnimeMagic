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
 * <b>Katon: Gōkakyū no Jutsu</b> — Fire Style: Fireball Jutsu.
 *
 * <p>The caster charges up the fireball in their hand for 1 second, visualized by
 * a 3D {@code magic_orb} model playing the {@code cast_charge} animation in front
 * of the hand. Then a stream of fire particles travels along a Bézier curve toward
 * the target. On impact, the orb model is removed and a sphere of expanding flame
 * particles bursts out, dealing fire damage to all living entities within 4 blocks.
 * A ring of lava particles expands outward for the visual blast wave.</p>
 */
public final class FireballJutsu implements Spell {

    private final AnimeMagicPlugin plugin;

    public FireballJutsu(AnimeMagicPlugin plugin) { this.plugin = plugin; }

    @Override public @NotNull String id() { return "naruto:fireball"; }
    @Override public @NotNull String displayName() { return "§6Katon §8» §cGōkakyū no Jutsu"; }
    @Override public @NotNull SchoolId school() { return SchoolId.NARUTO; }
    @Override public int manaCost() { return 25; }
    @Override public long cooldownMs() { return 4000; }
    @Override public int requiredLevel() { return 5; }
    @Override public @NotNull String description() {
        return "Exhale a great fireball that explodes on impact, scorching nearby foes.";
    }
    @Override public @NotNull SpellIcon icon() {
        return new SpellIcon("FIRE_CHARGE", 3001, "§cFireball Jutsu");
    }

    @Override
    public boolean cast(@NotNull Caster caster) {
        Player p = caster.player();

        // --- Charge phase: 3D orb model with cast_charge animation in hand ---
        ModelDisplay chargeOrb = SpellEffects.spawnInHand(plugin, p, "magic_orb", "cast_charge", 30);
        // Apply a slight orange tint effect by spawning flame particles around the model
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (t++ > 30) { cancel(); return; }
                Location hand = p.getEyeLocation().add(p.getLocation().getDirection().multiply(0.8));
                if (hand.getWorld() == null) return;
                hand.getWorld().spawnParticle(Particle.FLAME, hand, 3, 0.2, 0.2, 0.2, 0.05);
                if (t % 5 == 0) {
                    LocationUtil.sound(hand, Sound.BLOCK_FIRE_AMBIENT, 0.5f, 1.2f);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);

        // --- After 1.5s charge, launch the fireball ---
        new BukkitRunnable() {
            @Override public void run() {
                if (chargeOrb != null) chargeOrb.remove(); // The charge orb is "consumed" by the launch
                launch(caster, p);
            }
        }.runTaskLater(plugin, 30L);

        return true;
    }

    private void launch(Caster caster, Player p) {
        Location start = p.getEyeLocation().add(p.getLocation().getDirection().multiply(0.5));
        LivingEntity target = caster.targetEntity(40);
        Location end = target != null ? target.getEyeLocation()
                : start.clone().add(p.getLocation().getDirection().multiply(30));

        // Bezier trail from mouth → target
        plugin.getParticleEngine().play(
                new BezierCurve(plugin, p, start, end,
                        Particle.FLAME, 30, 1.5));

        // Smoke trail at the start
        new BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                if (ticks++ > 10) { cancel(); return; }
                LocationUtil.sound(start, Sound.ENTITY_BLAZE_SHOOT, 0.5f, 1.4f);
            }
        }.runTaskTimer(plugin, 0L, 2L);

        // Delayed explosion at end
        int delay = 30;
        new BukkitRunnable() {
            @Override public void run() {
                if (end.getWorld() == null) return;
                // Visual: expanding spheres of flame + lava + smoke ring
                plugin.getParticleEngine().play(
                        new SphereAnimation(plugin, p, end, Particle.FLAME, 20, 1.0, 4.0, 80));
                plugin.getParticleEngine().play(
                        new SphereAnimation(plugin, p, end, Particle.LAVA, 15, 0.5, 3.0, 40));
                plugin.getParticleEngine().play(
                        new RingBurst(plugin, p, end, Particle.LARGE_SMOKE, 15, 6.0, 48));
                LocationUtil.sound(end, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.8f);

                // Damage
                double dmg = 8.0 * plugin.getConfig().getDouble("schools.naruto.damage-multiplier", 1.0);
                List<LivingEntity> hit = LocationUtil.nearbyLiving(end, 4.0, p.getUniqueId());
                for (LivingEntity e : hit) {
                    e.damage(dmg, p);
                    e.setFireTicks(80);
                    LocationUtil.knockback(e, end, 0.8);
                }
            }
        }.runTaskLater(plugin, delay);
    }
}
