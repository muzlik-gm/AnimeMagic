package com.anime.magic.schools.naruto;

import com.anime.magic.AnimeMagicPlugin;
import com.anime.magic.api.Caster;
import com.anime.magic.api.Spell;
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
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * <b>Rasengan v5 — CINEMATIC EDITION</b>
 *
 * <p>Full anime-grade choreography with all cinematic systems:</p>
 *
 * <ol>
 *   <li><b>Formation (20 ticks):</b> Energy charge — particles spiral inward
 *       toward the player's hand. 3D rasengan_sphere model spawns and grows.
 *       Escalating sound. Screen begins to subtly shake.</li>
 *
 *   <li><b>Charge (20 ticks):</b> Orb pulses brighter. Player gains Speed II +
 *       Jump Boost II. Converging particles increase in density. Sound peaks.</li>
 *
 *   <li><b>Thrust (10 ticks):</b> Player lunges forward 3 blocks (velocity dash).
 *       Orb scales up to 1.5x then snaps back. Motion blur particles trail the dash.</li>
 *
 *   <li><b>Impact:</b> Impact frame triggers — target freezes (Slowness 255 for 5 ticks),
 *       FLASH particle burst, layered impact sounds, screen shake for all nearby players.
 *       3-layer expanding sphere (END_ROD + CLOUD + SPORE_BLOSSOM_AIR). Knockback 2.0
 *       launches all nearby entities. <b>Crater forms at impact point (DestructionSystem).</b></li>
 *
 *   <li><b>Aftermath:</b> Lingering dust cloud for 20 ticks. Embers float up.</li>
 * </ol>
 */
public final class RasenganSpell implements Spell {
    private final AnimeMagicPlugin plugin;

    public RasenganSpell(AnimeMagicPlugin plugin) { this.plugin = plugin; }

    @Override public @NotNull String id() { return "naruto:rasengan"; }
    @Override public @NotNull String displayName() { return "§a§lRasengan §8» §7Spiraling Sphere"; }
    @Override public @NotNull SchoolId school() { return SchoolId.NARUTO; }
    @Override public int manaCost() { return 45; }
    @Override public long cooldownMs() { return 8000; }
    @Override public int requiredLevel() { return 10; }
    @Override public @NotNull String description() {
        return "Form a sphere of pure chakra, lunge forward, and detonate on impact. Crater + screen shake + impact frame.";
    }
    @Override public @NotNull SpellIcon icon() {
        return new SpellIcon("SNOWBALL", 3003, "§aRasengan");
    }

    @Override
    public boolean cast(@NotNull Caster caster) {
        Player p = caster.player();
        var effects = plugin.getCinematicEffects();

        // Phase 1: Formation — energy charge converging on hand
        Location handLoc = p.getEyeLocation().add(p.getLocation().getDirection().multiply(1.0));
        effects.energyCharge(handLoc, 20, Particle.END_ROD, null);

        // Spawn 3D orb model with spin animation
        ModelDisplay orb = SpellEffects.spawnInHand(plugin, p, "rasengan_sphere", "orb_spin", 100);
        if (orb != null) {
            for (int t = 0; t <= 20; t++) {
                final int tick = t;
                new BukkitRunnable() {
                    @Override public void run() {
                        if (orb.isDead()) return;
                        float s = 0.3f + 0.5f * (tick / 20f);
                        orb.setTransform(0, -0.4f, 0.8f, 0, tick * 18f, 0, s, s, s);
                    }
                }.runTaskLater(plugin, t);
            }
        }

        // Phase 2: Charge — escalating converging particles + buffs
        effects.buildUp(handLoc, 20, Particle.CRIT, Sound.ENTITY_ENDERMAN_AMBIENT);
        new BukkitRunnable() {
            @Override public void run() {
                p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 25, 1));
                p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 25, 1));
            }
        }.runTaskLater(plugin, 20L);

        // Phase 3: Thrust — lunge forward at 40 ticks
        new BukkitRunnable() {
            @Override public void run() {
                if (orb != null && !orb.isDead()) {
                    orb.setTransform(0, -0.4f, 0.8f, 0, 0, 0, 1.5f, 1.5f, 1.5f);
                    new BukkitRunnable() {
                        @Override public void run() {
                            if (!orb.isDead()) orb.setTransform(0, -0.4f, 0.8f, 0, 0, 0, 1.0f, 1.0f, 1.0f);
                        }
                    }.runTaskLater(plugin, 4L);
                }
                Vector dash = p.getLocation().getDirection().setY(0).normalize().multiply(3.0).setY(0.2);
                p.setVelocity(dash);
                // Motion blur trail
                Location trailStart = p.getEyeLocation();
                Location trailEnd = trailStart.clone().add(dash);
                effects.directedStream(Particle.END_ROD, trailStart, trailEnd, 8, 3, null);
                LocationUtil.sound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 1.5f);
            }
        }.runTaskLater(plugin, 40L);

        // Phase 4 + 5: Impact + aftermath at 50 ticks
        new BukkitRunnable() {
            @Override public void run() {
                Location hand = p.getEyeLocation().add(p.getLocation().getDirection().multiply(1.0));
                if (orb != null) orb.remove();
                List<LivingEntity> near = LocationUtil.nearbyLiving(hand, 4.0, p.getUniqueId());
                if (near.isEmpty()) {
                    // Dissipate
                    if (hand.getWorld() != null) {
                        hand.getWorld().spawnParticle(Particle.CLOUD, hand, 20, 0.5, 0.5, 0.5, 0.05);
                    }
                    LocationUtil.sound(hand, Sound.ENTITY_ENDERMAN_TELEPORT, 0.5f, 0.7f);
                    return;
                }
                LivingEntity primary = near.get(0);

                // Impact frame — freeze + flash + shake
                plugin.getImpactFrameSystem().trigger(hand, primary, 1.5);

                // 3-layer explosion
                plugin.getParticleEngine().play(
                        new SphereAnimation(plugin, p, hand, Particle.END_ROD, 8, 0.5, 5.0, 80));
                plugin.getParticleEngine().play(
                        new SphereAnimation(plugin, p, hand, Particle.CLOUD, 8, 0.3, 3.5, 40));
                plugin.getParticleEngine().play(
                        new SphereAnimation(plugin, p, hand, Particle.SPORE_BLOSSOM_AIR, 6, 0.2, 2.5, 30));

                // Damage + knockback
                double dmg = 16.0 * plugin.getConfig().getDouble("schools.naruto.damage-multiplier", 1.0);
                for (LivingEntity e : LocationUtil.nearbyLiving(hand, 5.0, p.getUniqueId())) {
                    e.damage(dmg, p);
                    LocationUtil.knockback(e, hand, 2.0);
                    e.setVelocity(e.getVelocity().setY(0.8));
                }

                // Destruction — crater at impact point
                plugin.getDestructionSystem().formCrater(hand, 2.5);

                // Aftermath — lingering dust + embers
                effects.aftermath(hand, 30, "dust");
                effects.aftermath(hand, 20, "embers");
            }
        }.runTaskLater(plugin, 50L);

        return true;
    }
}
