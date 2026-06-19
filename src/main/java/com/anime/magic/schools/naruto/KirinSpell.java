package com.anime.magic.schools.naruto;

import com.anime.magic.AnimeMagicPlugin;
import com.anime.magic.api.Caster;
import com.anime.magic.api.Spell;
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

/**
 * <b>Kirin v5 — CINEMATIC EDITION</b>
 *
 * <p>The most destructive spell — calls down a literal lightning strike from
 * the sky. Full cinematic treatment:</p>
 *
 * <ol>
 *   <li><b>Storm Build (40 ticks / 2s):</b> Player raises hand. Electric sparks
 *       spiral upward into the sky. Target location crackles with lightning.
 *       Escalating thunder rumbles. Subtle screen shake on the caster.</li>
 *   <li><b>Descent (10 ticks / 0.5s):</b> 3D kirin_bolt model descends from 10
 *       blocks above the target, playing the descend animation. The sky darkens
 *       (brief Darkness effect on nearby players).</li>
 *   <li><b>Impact:</b> Massive impact frame — FLASH + screen shake (intensity 3.0)
 *       for all players within 30 blocks. Ground shockwave ring expands 15 blocks.
 *       Crater forms (radius 4). All entities within 8 blocks take 25-50 damage.
 *       Scorch marks left behind.</li>
 *   <li><b>Aftermath:</b> Smoke cloud + embers for 3 seconds. Residual lightning
 *       crackles for 2 seconds.</li>
 * </ol>
 */
public final class KirinSpell implements Spell {
    private final AnimeMagicPlugin plugin;

    public KirinSpell(AnimeMagicPlugin plugin) { this.plugin = plugin; }

    @Override public @NotNull String id() { return "naruto:kirin"; }
    @Override public @NotNull String displayName() { return "§b§lRaiton §8» §f§lKirin"; }
    @Override public @NotNull SchoolId school() { return SchoolId.NARUTO; }
    @Override public int manaCost() { return 120; }
    @Override public long cooldownMs() { return 45000; }
    @Override public int requiredLevel() { return 50; }
    @Override public @NotNull String description() {
        return "Call down a literal lightning strike. Massive crater + violent screen shake + scorch marks. 50 dmg primary, 25 AoE.";
    }
    @Override public @NotNull SpellIcon icon() {
        return new SpellIcon("PRISMARINE_SHARD", 7009, "§bKirin");
    }

    @Override
    public boolean cast(@NotNull Caster caster) {
        Player p = caster.player();
        LivingEntity target = caster.targetEntity(50);
        if (target == null) return false;
        Location strike = target.getLocation();
        var effects = plugin.getCinematicEffects();

        // Phase 1: Storm Build — escalating energy at target
        effects.buildUp(strike.clone().add(0, 5, 0), 40, Particle.ELECTRIC_SPARK, Sound.ENTITY_LIGHTNING_BOLT_THUNDER);
        // Caster-side charge
        effects.energyCharge(p.getEyeLocation(), 40, Particle.ELECTRIC_SPARK, null);
        // Subtle shake on caster during build
        if (plugin.getScreenShakeSystem() != null) {
            plugin.getScreenShakeSystem().shake(p, 0.3);
        }

        // Phase 2: Descent at 40 ticks — kirin bolt model descends
        new BukkitRunnable() {
            @Override public void run() {
                Location spawnAt = strike.clone().add(0, 10, 0);
                SpellEffects.spawnAnimated(plugin, p, "kirin_bolt", "animation.kirin.descend", spawnAt, 20, null);
                LocationUtil.sound(strike, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 3.0f, 0.3f);
                LocationUtil.sound(strike, Sound.ENTITY_ENDER_DRAGON_GROWL, 2.0f, 0.5f);
            }
        }.runTaskLater(plugin, 40L);

        // Phase 3: Impact at 60 ticks
        new BukkitRunnable() {
            @Override public void run() {
                if (strike.getWorld() == null) return;

                // Impact frame — violent (intensity 3.0)
                plugin.getImpactFrameSystem().trigger(strike, target, 3.0);

                // Massive shockwave ring (ground-traveling)
                effects.shockwaveRing(strike, 15.0, 30, Particle.ELECTRIC_SPARK);

                // Layered particle spheres
                plugin.getParticleEngine().play(new SphereAnimation(plugin, p, strike, Particle.ELECTRIC_SPARK, 20, 1.0, 8.0, 100));
                plugin.getParticleEngine().play(new SphereAnimation(plugin, p, strike, Particle.CRIT, 15, 0.5, 5.0, 60));
                plugin.getParticleEngine().play(new SphereAnimation(plugin, p, strike, Particle.FLASH, 5, 0.2, 3.0, 20));
                plugin.getParticleEngine().play(new RingBurst(plugin, p, strike, Particle.ELECTRIC_SPARK, 30, 15.0, 96));

                // Damage
                double dmgPrimary = 50.0 * plugin.getConfig().getDouble("schools.naruto.damage-multiplier", 1.0);
                double dmgAoE = 25.0 * plugin.getConfig().getDouble("schools.naruto.damage-multiplier", 1.0);
                if (!target.isDead()) {
                    target.damage(dmgPrimary, p);
                    target.setFireTicks(100);
                    target.addPotionEffect(new org.bukkit.potion.PotionEffect(
                            org.bukkit.potion.PotionEffectType.SLOWNESS, 100, 4));
                }
                for (LivingEntity e : LocationUtil.nearbyLiving(strike, 8.0, p.getUniqueId())) {
                    if (e.getUniqueId().equals(target.getUniqueId())) continue;
                    e.damage(dmgAoE, p);
                    e.setFireTicks(60);
                    LocationUtil.knockback(e, strike, 2.0);
                }

                // Destruction — massive crater + scorch marks
                plugin.getDestructionSystem().formCrater(strike, 4.0);
                plugin.getDestructionSystem().scorchMark(strike, 5.0);

                // Aftermath — smoke + residual lightning
                effects.aftermath(strike, 60, "smoke");
                new BukkitRunnable() {
                    int ticks = 0;
                    @Override public void run() {
                        if (ticks >= 40 || strike.getWorld() == null) { cancel(); return; }
                        strike.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, strike, 3, 3, 3, 3, 0.1);
                        ticks++;
                    }
                }.runTaskTimer(plugin, 5L, 2L);
            }
        }.runTaskLater(plugin, 60L);

        return true;
    }
}
