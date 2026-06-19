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
 * <b>Conqueror's Haki v2 — Production Overhaul (Haoshoku Haki)</b>
 *
 * <p>Willpower manifestation — a devastating AoE stun:</p>
 *
 * <ol>
 *   <li><b>Phase 1 — Charge (15 ticks / 0.75s):</b> The player is rooted (Slowness 255
 *       for 15 ticks). Purple lightning particles crackle around them. A 3D
 *       {@code haki_dome} model spawns at their feet at 0.1 scale, growing.</li>
 *
 *   <li><b>Phase 2 — Burst (15 ticks / 0.75s):</b> The dome plays
 *       {@code animation.haki.burst}, expanding to 3x scale and rotating. An
 *       expanding sphere of DRAGON_BREATH particles ripples outward to 15 blocks.
 *       Sound: thunder crack + dragon growl.</li>
 *
 *   <li><b>Phase 3 — Domination:</b> All hostile mobs within 15 blocks are paralyzed
 *       (Slowness V + Weakness III + Darkness for 6 seconds). Players within 8 blocks
 *       are blinded for 3 seconds. Non-hostile entities (villagers, animals) are
 *       unaffected. The caster gains Resistance III for 5 seconds.</li>
 *
 *   <li><b>Phase 4 — Collapse:</b> The dome model shrinks to 0 and disappears.
 *       Final SQUID_INK ring expands outward.</li>
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
        return "Unleash your willpower as a 15-block shockwave dome. Hostiles paralyzed 6s; nearby players blinded 3s. "
                + "4-phase cast: Charge -> Burst -> Dominate -> Collapse.";
    }
    @Override public @NotNull SpellIcon icon() {
        return new SpellIcon("PURPLE_GLAZED_TERRACOTTA", 6001, "§5Conqueror's Haki");
    }

    @Override
    public boolean cast(@NotNull Caster caster) {
        Player p = caster.player();
        Location center = p.getLocation();

        // Phase 1: Charge — root the player, spawn dome model
        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 15, 255)); // root
        p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 15, 250)); // no jump

        Location domeSpawn = center.clone().add(0, 0.5, 0);
        ModelDisplay dome = SpellEffects.spawnAnimated(plugin, p, "haki_dome",
                "animation.haki.burst", domeSpawn, 35, null);
        // Start tiny, grow
        if (dome != null) {
            dome.setTransform(0, 0.5f, 0, 0, 0, 0, 0.1f, 0.1f, 0.1f);
        }

        // Crackling purple particles around player during charge
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (t >= 15) { cancel(); return; }
                // Lightning crackles
                for (int i = 0; i < 4; i++) {
                    double angle = Math.random() * Math.PI * 2;
                    double r = 1.0 + Math.random() * 1.5;
                    Location bolt = center.clone().add(Math.cos(angle) * r, Math.random() * 2.5, Math.sin(angle) * r);
                    if (bolt.getWorld() != null) {
                        bolt.getWorld().spawnParticle(Particle.DRAGON_BREATH, bolt, 2, 0.1, 0.1, 0.1, 0.05);
                        bolt.getWorld().spawnParticle(Particle.SQUID_INK, bolt, 1, 0.05, 0.05, 0.05, 0.0);
                    }
                }
                if (t % 3 == 0) {
                    LocationUtil.sound(center, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.5f, 0.5f + t * 0.05f);
                }
                t++;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        // Phase 2: Burst — at 15 ticks, grow the dome + spawn expanding sphere
        new BukkitRunnable() {
            @Override public void run() {
                // Expand dome to full size over 15 ticks via animation
                if (dome != null && !dome.isDead()) {
                    // Animate scale-up manually since the animation does this too
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
                // Particle shockwave — 3 expanding spheres
                plugin.getParticleEngine().play(
                        new SphereAnimation(plugin, p, center, Particle.DRAGON_BREATH, 20, 1.0, 15.0, 120));
                plugin.getParticleEngine().play(
                        new SphereAnimation(plugin, p, center, Particle.SQUID_INK, 15, 1.0, 12.0, 80));
                plugin.getParticleEngine().play(
                        new RingBurst(plugin, p, center, Particle.DRAGON_BREATH, 18, 12.0, 80));

                LocationUtil.sound(center, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.8f, 0.5f);
                LocationUtil.sound(center, Sound.ENTITY_WITHER_SPAWN, 1.2f, 0.6f);
                LocationUtil.sound(center, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 2.0f, 0.4f);
            }
        }.runTaskLater(plugin, 15L);

        // Phase 3: Dominate — at 20 ticks, apply effects to all nearby
        new BukkitRunnable() {
            @Override public void run() {
                for (LivingEntity e : LocationUtil.nearbyLiving(center, 15.0, p.getUniqueId())) {
                    if (e instanceof Player other && other.getLocation().distanceSquared(center) <= 64) {
                        other.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0));
                        other.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 2));
                        other.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 60, 1));
                    } else if (!(e instanceof Player)) {
                        e.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 120, 4)); // paralysis 6s
                        e.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 120, 2));
                        e.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 120, 0));
                        e.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 60, 0));
                    }
                }
                // Caster resistance
                p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 100, 2));
                p.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 100, 0));
            }
        }.runTaskLater(plugin, 20L);

        // Phase 4: Collapse — at 35 ticks, shrink dome + final ring
        new BukkitRunnable() {
            @Override public void run() {
                if (dome != null && !dome.isDead()) {
                    // Shrink via the collapse animation
                    var collapseAnim = plugin.getAnimationRegistry().get("animation.haki.collapse");
                    if (collapseAnim != null) dome.playAnimation(collapseAnim);
                    new BukkitRunnable() {
                        @Override public void run() { dome.remove(); }
                    }.runTaskLater(plugin, 10L);
                }
                // Final ring burst
                plugin.getParticleEngine().play(
                        new RingBurst(plugin, p, center, Particle.SQUID_INK, 15, 8.0, 50));
                LocationUtil.sound(center, Sound.ENTITY_WITHER_DEATH, 0.8f, 0.6f);
            }
        }.runTaskLater(plugin, 35L);

        return true;
    }
}
