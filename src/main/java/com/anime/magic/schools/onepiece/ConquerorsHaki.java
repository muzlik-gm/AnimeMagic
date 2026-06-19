package com.anime.magic.schools.onepiece;

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
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

/**
 * <b>Conqueror's Haki v5 — CINEMATIC EDITION</b>
 *
 * <p>Willpower manifestation with full destruction + screen shake:</p>
 *
 * <ol>
 *   <li><b>Charge (15 ticks):</b> Player rooted. Purple lightning crackles.
 *       Energy charge converges on player. 3D haki_dome model spawns tiny.</li>
 *   <li><b>Burst (15 ticks):</b> Dome expands to full size. Ground-traveling
 *       shockwave ring expands 15 blocks. Screen shake (intensity 2.5) for all
 *       within 20 blocks. All hostiles paralyzed. Crater forms at player's feet.</li>
 *   <li><b>Collapse:</b> Dome shrinks to 0. Final ring burst.</li>
 * </ol>
 */
public final class ConquerorsHaki implements Spell {
    private final AnimeMagicPlugin plugin;

    public ConquerorsHaki(AnimeMagicPlugin plugin) { this.plugin = plugin; }

    @Override public @NotNull String id() { return "onepiece:conquerors_haki"; }
    @Override public @NotNull String displayName() { return "§8§lHaki §8» §5§lConqueror's Will"; }
    @Override public @NotNull SchoolId school() { return SchoolId.ONEPIECE; }
    @Override public int manaCost() { return 100; }
    @Override public long cooldownMs() { return 30000; }
    @Override public int requiredLevel() { return 30; }
    @Override public @NotNull String description() {
        return "Unleash your willpower as a 15-block shockwave dome. Mass paralysis + screen shake + crater.";
    }
    @Override public @NotNull SpellIcon icon() {
        return new SpellIcon("PURPLE_GLAZED_TERRACOTTA", 6001, "§5Conqueror's Haki");
    }

    @Override
    public boolean cast(@NotNull Caster caster) {
        Player p = caster.player();
        Location center = p.getLocation();
        var effects = plugin.getCinematicEffects();

        // Phase 1: Charge — root player + energy converge
        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 15, 255));
        p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 15, 250));
        effects.energyCharge(center.clone().add(0, 1, 0), 15, Particle.DRAGON_BREATH, null);

        Location domeSpawn = center.clone().add(0, 0.5, 0);
        ModelDisplay dome = SpellEffects.spawnAnimated(plugin, p, "haki_dome",
                "animation.haki.burst", domeSpawn, 35, null);
        if (dome != null) dome.setTransform(0, 0.5f, 0, 0, 0, 0, 0.1f, 0.1f, 0.1f);

        // Phase 2: Burst at 15 ticks
        new BukkitRunnable() {
            @Override public void run() {
                if (dome != null && !dome.isDead()) {
                    for (int t = 0; t <= 15; t++) {
                        final int tick = t;
                        new BukkitRunnable() {
                            @Override public void run() {
                                if (dome.isDead()) return;
                                float s = 0.1f + 2.9f * (tick / 15f);
                                dome.setTransform(0, 0.5f, 0, 0, tick * 24f, 0, s, s, s);
                            }
                        }.runTaskLater(plugin, t);
                    }
                }

                // Ground-traveling shockwave ring
                effects.shockwaveRing(center, 15.0, 25, Particle.DRAGON_BREATH);

                // Particle spheres
                plugin.getParticleEngine().play(new SphereAnimation(plugin, p, center, Particle.DRAGON_BREATH, 20, 1.0, 15.0, 120));
                plugin.getParticleEngine().play(new SphereAnimation(plugin, p, center, Particle.SQUID_INK, 15, 1.0, 12.0, 80));
                plugin.getParticleEngine().play(new RingBurst(plugin, p, center, Particle.DRAGON_BREATH, 18, 12.0, 80));

                // Screen shake for ALL nearby players (intensity 2.5)
                if (plugin.getScreenShakeSystem() != null) {
                    plugin.getScreenShakeSystem().shakeNearby(center, 20.0, 2.5);
                }

                LocationUtil.sound(center, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.8f, 0.5f);
                LocationUtil.sound(center, Sound.ENTITY_WITHER_SPAWN, 1.2f, 0.6f);
                LocationUtil.sound(center, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 2.0f, 0.4f);

                // Damage + paralysis
                for (LivingEntity e : LocationUtil.nearbyLiving(center, 15.0, p.getUniqueId())) {
                    if (e instanceof Player other && other.getLocation().distanceSquared(center) <= 64) {
                        other.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0));
                        other.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 2));
                        other.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 60, 1));
                    } else if (!(e instanceof Player)) {
                        e.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 120, 4));
                        e.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 120, 2));
                        e.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 120, 0));
                        e.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 60, 0));
                    }
                }
                p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 100, 2));
                p.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 100, 0));

                // Crater at player's feet
                plugin.getDestructionSystem().formCrater(center, 2.0);
            }
        }.runTaskLater(plugin, 15L);

        // Phase 3: Collapse at 35 ticks
        new BukkitRunnable() {
            @Override public void run() {
                if (dome != null && !dome.isDead()) {
                    var collapseAnim = plugin.getAnimationRegistry().get("animation.haki.collapse");
                    if (collapseAnim != null) dome.playAnimation(collapseAnim);
                    new BukkitRunnable() {
                        @Override public void run() { dome.remove(); }
                    }.runTaskLater(plugin, 10L);
                }
                plugin.getParticleEngine().play(new RingBurst(plugin, p, center, Particle.SQUID_INK, 15, 8.0, 50));
                effects.aftermath(center, 30, "dust");
                LocationUtil.sound(center, Sound.ENTITY_WITHER_DEATH, 0.8f, 0.6f);
            }
        }.runTaskLater(plugin, 35L);

        return true;
    }
}
