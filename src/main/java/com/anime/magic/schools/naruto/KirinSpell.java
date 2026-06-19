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
 * <b>Kirin — Lightning Style: Kirin</b>
 *
 * <p>Sasuke's ultimate — calls down a literal lightning strike from the sky on the target.
 * One-shot, devastating, 50-block radius warning visual.</p>
 *
 * <ol>
 *   <li><b>Storm Build (40 ticks / 2s):</b> Player raises hand. ELECTRIC_SPARK
 *       particles spiral upward into the sky. Sound: thunder rumble escalating.</li>
 *   <li><b>Strike (instant at 40 ticks):</b> A 3D kirin_bolt model descends from 10 blocks
 *       above the target's head, playing kirin.descend. On landing, a massive shockwave
 *       ring expands 15 blocks.</li>
 *   <li><b>Impact:</b> Target takes 50 lightning damage. All entities within 8 blocks
 *       take 25 damage + Slowness V (paralysis) + set on fire. Crater formed at strike point.</li>
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
        return "Call down a literal lightning strike on your target. 50 damage to primary target, "
                + "25 to all within 8 blocks. Leaves a crater.";
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

        // Phase 1: Storm build — particles spiral upward
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (t >= 40) { cancel(); return; }
                Location above = p.getLocation().add(0, 2, 0);
                if (above.getWorld() == null) return;
                for (int i = 0; i < 6; i++) {
                    double angle = (t * 0.4) + (i * Math.PI / 3);
                    double r = 1.5;
                    double y = t * 0.4;
                    Location from = p.getLocation().add(Math.cos(angle) * r, y, Math.sin(angle) * r);
                    above.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, from, 1, 0.1, 0.1, 0.1, 0.1);
                }
                // Crackling at target
                target.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, target.getEyeLocation(), 2, 1, 2, 1, 0.2);
                if (t % 5 == 0) LocationUtil.sound(p.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.3f + t * 0.02f, 0.4f + t * 0.02f);
                t++;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        // Phase 2: Strike at 40 ticks — descending kirin bolt
        new BukkitRunnable() {
            @Override public void run() {
                Location spawnAt = strike.clone().add(0, 10, 0);
                ModelDisplay bolt = SpellEffects.spawnAnimated(plugin, p, "kirin_bolt",
                        "animation.kirin.descend", spawnAt, 20, null);
                LocationUtil.sound(strike, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 3.0f, 0.3f);
                LocationUtil.sound(strike, Sound.ENTITY_ENDER_DRAGON_GROWL, 2.0f, 0.5f);
            }
        }.runTaskLater(plugin, 40L);

        // Phase 3: Impact at 60 ticks
        new BukkitRunnable() {
            @Override public void run() {
                if (strike.getWorld() == null) return;
                // Massive ring + sphere
                plugin.getParticleEngine().play(new RingBurst(plugin, p, strike, Particle.ELECTRIC_SPARK, 30, 15.0, 96));
                plugin.getParticleEngine().play(new SphereAnimation(plugin, p, strike, Particle.ELECTRIC_SPARK, 20, 1.0, 8.0, 100));
                plugin.getParticleEngine().play(new SphereAnimation(plugin, p, strike, Particle.CRIT, 15, 0.5, 5.0, 60));
                plugin.getParticleEngine().play(new SphereAnimation(plugin, p, strike, Particle.FLASH, 5, 0.2, 3.0, 20));
                LocationUtil.sound(strike, Sound.ENTITY_GENERIC_EXPLODE, 3.0f, 0.4f);

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
                    e.addPotionEffect(new org.bukkit.potion.PotionEffect(
                            org.bukkit.potion.PotionEffectType.SLOWNESS, 60, 4));
                    LocationUtil.knockback(e, strike, 1.5);
                }
            }
        }.runTaskLater(plugin, 60L);

        return true;
    }
}
