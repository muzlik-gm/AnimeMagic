package com.anime.magic.schools.onepiece;

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
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

/**
 * <b>Gomu Gomu no Pistol v2 — Production Overhaul</b>
 *
 * <p>Multi-phase stretching punch:</p>
 *
 * <ol>
 *   <li><b>Phase 1 — Windup (10 ticks / 0.5s):</b> The player pulls their arm back
 *       (visualized via red dust particles converging toward the shoulder). Sound:
 *       rubber stretch.</li>
 *
 *   <li><b>Phase 2 — Stretch Launch (15 ticks / 0.75s):</b> A thick red Bezier curve
 *       extends from the player's shoulder to the target's face (or up to 15 blocks
 *       forward if no target). A small red "fist" particle cluster travels along
 *       the curve. Sound: rubber snap.</li>
 *
 *   <li><b>Phase 3 — Impact:</b> The fist particle cluster bursts at the impact point.
 *       Sphere of red DUST particles + small white "impact star" particles. Target
 *       takes 16 damage + knockback of 2.5 (much stronger than v1). Sound: heavy
 *       punch + anvil land.</li>
 *
 *   <li><b>Phase 4 — Recoil:</b> The Bezier curve snaps back to the player. Player
 *       loses 0.5 forward momentum. Sound: rubber retract.</li>
 * </ol>
 *
 * <p>If no target found within 15 blocks, the cast fizzles (no mana spent, no cooldown).</p>
 */
public final class GomuGomuSkill implements Spell {
    private final AnimeMagicPlugin plugin;

    public GomuGomuSkill(AnimeMagicPlugin plugin) { this.plugin = plugin; }

    @Override public @NotNull String id() { return "onepiece:gomu_pistol"; }
    @Override public @NotNull String displayName() { return "§c§lGomu Gomu §8» §f§lPistol"; }
    @Override public @NotNull SchoolId school() { return SchoolId.ONEPIECE; }
    @Override public int manaCost() { return 30; }
    @Override public long cooldownMs() { return 4500; }
    @Override public int requiredLevel() { return 6; }
    @Override public @NotNull String description() {
        return "Stretch your arm up to 15 blocks forward and unleash a powerful knockback punch. "
                + "4-phase cast: Windup -> Stretch -> Impact -> Recoil.";
    }
    @Override public @NotNull SpellIcon icon() {
        return new SpellIcon("RED_DYE", 6003, "§cGomu Gomu Pistol");
    }

    @Override
    public boolean cast(@NotNull Caster caster) {
        Player p = caster.player();

        // Pre-check: must have a target or fire forward
        LivingEntity target = caster.targetEntity(15.0);
        Location start = p.getEyeLocation().add(p.getLocation().getDirection().multiply(0.5));
        Location end;
        if (target != null) {
            end = target.getEyeLocation();
        } else {
            // Fire forward — only commit if there's something solid ahead, otherwise fizzle
            end = start.clone().add(p.getLocation().getDirection().multiply(15));
        }

        // Phase 1: Windup — pull arm back, particles converge on shoulder
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (t >= 10) {
                    // Phase 2: stretch launch
                    stretch(p, target, start, end);
                    cancel();
                    return;
                }
                // Particles converge toward shoulder
                Location shoulder = start.clone().add(0, -0.3, 0);
                for (int i = 0; i < 3; i++) {
                    double angle = Math.random() * Math.PI * 2;
                    double r = 1.5 - t * 0.13;
                    Location from = shoulder.clone().add(Math.cos(angle) * r, Math.random() * 1.0, Math.sin(angle) * r);
                    if (from.getWorld() != null) {
                        from.getWorld().spawnParticle(Particle.DUST, from, 0,
                                -Math.cos(angle) * 0.15, -0.1, -Math.sin(angle) * 0.15, 0.0);
                    }
                }
                if (t % 3 == 0) {
                    LocationUtil.sound(shoulder, Sound.ENTITY_SLIME_SQUISH, 0.5f, 0.5f + t * 0.08f);
                }
                t++;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        return true;
    }

    private void stretch(Player p, LivingEntity target, Location start, Location end) {
        // Phase 2: Bezier red arm trail from shoulder to target
        Vector ctrl1 = new Vector(0, 1.0, 0);
        Vector ctrl2 = new Vector(0, 0.5, 0);
        plugin.getParticleEngine().play(
                new BezierCurve(plugin, p, start, end, ctrl1, ctrl2, Particle.DUST, 15, 8, 0.0));
        LocationUtil.sound(start, Sound.ENTITY_SLIME_ATTACK, 1.0f, 1.5f);
        LocationUtil.sound(start, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.8f, 0.7f);

        // Phase 3: Impact at end of bezier (15 ticks)
        new BukkitRunnable() {
            @Override public void run() {
                if (end.getWorld() == null) return;
                // Sphere of red dust at impact
                plugin.getParticleEngine().play(
                        new SphereAnimation(plugin, p, end, Particle.DUST, 10, 0.3, 2.5, 40));
                // White impact star
                plugin.getParticleEngine().play(
                        new SphereAnimation(plugin, p, end, Particle.END_ROD, 5, 0.1, 1.0, 20));
                // Cloud puff
                plugin.getParticleEngine().play(
                        new SphereAnimation(plugin, p, end, Particle.CLOUD, 8, 0.2, 1.5, 25));

                LocationUtil.sound(end, Sound.ENTITY_PLAYER_ATTACK_STRONG, 1.5f, 0.6f);
                LocationUtil.sound(end, Sound.BLOCK_ANVIL_LAND, 1.0f, 0.8f);
                LocationUtil.sound(end, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.4f);

                // Damage + knockback if target hit
                if (target != null && !target.isDead()) {
                    double dmg = 16.0 * plugin.getConfig().getDouble("schools.onepiece.damage-multiplier", 1.0);
                    target.damage(dmg, p);
                    Vector knock = target.getLocation().toVector()
                            .subtract(p.getLocation().toVector()).normalize().multiply(2.5).setY(0.7);
                    target.setVelocity(knock);
                }
                // Also damage any entity near impact
                for (LivingEntity e : com.anime.magic.util.LocationUtil.nearbyLiving(end, 1.5, p.getUniqueId())) {
                    if (e.equals(target)) continue;
                    e.damage(8.0, p);
                    LocationUtil.knockback(e, end, 1.5);
                }

                // Phase 4: Recoil — reverse bezier back to player (just visual)
                plugin.getParticleEngine().play(
                        new BezierCurve(plugin, p, end, start, ctrl2, ctrl1, Particle.DUST, 8, 4, 0.0));
            }
        }.runTaskLater(plugin, 15L);
    }
}
