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
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * <b>Rasenshuriken — Wind Style: Spiraling Shuriken</b>
 *
 * <p>The ultimate evolution of Rasengan — a throwable spinning shuriken of wind
 * chakra that detonates into a cellular-destroying sphere on impact.</p>
 *
 * <ol>
 *   <li><b>Form (30 ticks):</b> 3D rasenshuriken model spawns in hand playing
 *       rasenshuriken.spin. Wind particles spiral inward.</li>
 *   <li><b>Throw (10 ticks):</b> Player sneaks → shuriken launches along a Bezier
 *       curve toward target, spinning at 1440°/s via rasenshuriken.throw animation.</li>
 *   <li><b>Detonate:</b> 5-layer expanding sphere (CLOUD, SPORE_BLOSSOM_AIR,
 *       END_ROD, CRIT, SWEEP_ATTACK). All entities within 6 blocks take 30 damage
 *       and Slowness III for 3s (cellular destruction slows movement).</li>
 * </ol>
 */
public final class RasenshurikenSpell implements Spell {
    private final AnimeMagicPlugin plugin;

    public RasenshurikenSpell(AnimeMagicPlugin plugin) { this.plugin = plugin; }

    @Override public @NotNull String id() { return "naruto:rasenshuriken"; }
    @Override public @NotNull String displayName() { return "§a§lFūton §8» §f§lRasenshuriken"; }
    @Override public @NotNull SchoolId school() { return SchoolId.NARUTO; }
    @Override public int manaCost() { return 80; }
    @Override public long cooldownMs() { return 20000; }
    @Override public int requiredLevel() { return 35; }
    @Override public @NotNull String description() {
        return "Throw a spinning wind shuriken that detonates on impact for massive AoE damage. "
                + "Cellular destruction slows all survivors for 3s.";
    }
    @Override public @NotNull SpellIcon icon() {
        return new SpellIcon("SNOWBALL", 7008, "§aRasenshuriken");
    }

    @Override
    public boolean cast(@NotNull Caster caster) {
        Player p = caster.player();
        // Form phase
        ModelDisplay shuriken = SpellEffects.spawnInHand(plugin, p, "rasenshuriken", "animation.rasenshuriken.spin", 200);
        // Wind spiral converging
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (!p.isOnline()) { cancel(); return; }
                if (t >= 30) { cancel(); return; }
                Location hand = p.getEyeLocation().add(p.getLocation().getDirection().multiply(1.0));
                if (hand.getWorld() == null) return;
                for (int i = 0; i < 3; i++) {
                    double angle = (t * 0.3) + (i * Math.PI / 3);
                    double r = 2.0 - t * 0.06;
                    Location from = hand.clone().add(Math.cos(angle) * r, 0, Math.sin(angle) * r);
                    try { hand.getWorld().spawnParticle(Particle.CLOUD, from, 1, -Math.cos(angle) * 0.2, -0.05, -Math.sin(angle) * 0.2, 0.05); } catch (Throwable ignored) {}
                }
                if (t % 10 == 0) LocationUtil.sound(hand, Sound.ENTITY_ENDERMAN_AMBIENT, 0.6f, 1.8f);
                t++;
            }
        }.runTaskTimer(plugin, 0L, 10L);

        // Wait for player to sneak to throw (or auto-throw after 2s)
        new BukkitRunnable() {
            int ticks = 0;
            boolean thrown = false;
            @Override public void run() {
                if (!p.isOnline()) { cancel(); return; }
                if (thrown) { cancel(); return; }
                if (ticks++ > 60 || p.isSneaking()) {
                    thrown = true;
                    throwShuriken(caster, p, shuriken);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 30L, 1L);
        return true;
    }

    private void throwShuriken(Caster caster, Player p, ModelDisplay shuriken) {
        if (shuriken != null && !shuriken.isDead()) {
            // Play throw animation
            var throwAnim = plugin.getAnimationRegistry().get("animation.rasenshuriken.throw");
            if (throwAnim != null) shuriken.playAnimation(throwAnim);
        }
        Location start = p.getEyeLocation().add(p.getLocation().getDirection().multiply(0.8));
        LivingEntity target = caster.targetEntity(40);
        Location end = target != null ? target.getLocation()
                : start.clone().add(p.getLocation().getDirection().multiply(30));
        // Bezier trail
        plugin.getParticleEngine().play(
                new BezierCurve(plugin, p, start, end, Particle.CLOUD, 20, 1.5));
        plugin.getParticleEngine().play(
                new BezierCurve(plugin, p, start, end, Particle.SWEEP_ATTACK, 20, 1.0));
        LocationUtil.sound(start, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.5f, 2.0f);

        // Detonate
        new BukkitRunnable() {
            @Override public void run() {
                if (!p.isOnline()) { cancel(); return; }
                if (shuriken != null) shuriken.remove();
                if (end.getWorld() == null) return;
                // 5-layer explosion
                plugin.getParticleEngine().play(new SphereAnimation(plugin, p, end, Particle.CLOUD, 15, 0.5, 6.0, 120));
                plugin.getParticleEngine().play(new SphereAnimation(plugin, p, end, Particle.SPORE_BLOSSOM_AIR, 15, 0.5, 5.5, 80));
                plugin.getParticleEngine().play(new SphereAnimation(plugin, p, end, Particle.END_ROD, 12, 0.3, 4.5, 60));
                plugin.getParticleEngine().play(new SphereAnimation(plugin, p, end, Particle.CRIT, 10, 0.3, 3.5, 40));
                plugin.getParticleEngine().play(new RingBurst(plugin, p, end, Particle.SWEEP_ATTACK, 18, 8.0, 60));
                LocationUtil.sound(end, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.6f);
                LocationUtil.sound(end, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.5f, 0.4f);

                double dmg = 30.0 * plugin.getConfig().getDouble("schools.naruto.damage-multiplier", 1.0);
                List<LivingEntity> hit = LocationUtil.nearbyLiving(end, 6.0, p.getUniqueId());
                for (LivingEntity e : hit) {
                    e.damage(dmg, p);
                    LocationUtil.knockback(e, end, 2.0);
                    e.addPotionEffect(new org.bukkit.potion.PotionEffect(
                            org.bukkit.potion.PotionEffectType.SLOWNESS, 60, 2));
                }
            }
        }.runTaskLater(plugin, 20L);
    }
}
