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
 * <b>Rasengan v2 — Production Overhaul</b>
 *
 * <p>A multi-phase anime-accurate Rasengan:</p>
 *
 * <ol>
 *   <li><b>Phase 1 — Formation (20 ticks / 1s):</b> A 3D {@code rasengan_sphere} model
 *       spawns in the player's hand playing the {@code orb_spin} animation. The sphere
 *       grows from 0.3 to full size with a smooth ease-out curve. Spiraling particles
 *       converge from around the player toward the orb.</li>
 *
 *   <li><b>Phase 2 — Charge (20 ticks / 1s):</b> The orb pulses brighter, the player
 *       gains Speed II and Jump Boost II. A second spiral of crit particles orbits the
 *       orb. Sound: escalating ender dragon growls.</li>
 *
 *   <li><b>Phase 3 — Thrust (10 ticks / 0.5s):</b> The player lunges forward 3 blocks
 *       (teleport with velocity set). The orb scales up briefly to 1.5x then snaps back
 *       as the strike lands.</li>
 *
 *   <li><b>Phase 4 — Detonation:</b> On hit, the orb explodes. Three expanding spheres
 *       (END_ROD, CLOUD, SPOKE) ripple out from impact. Knockback of 2.0 launches all
 *       nearby entities. Sound: explosion + thunder.</li>
 * </ol>
 *
 * <p>If no target is within 4 blocks after Phase 3, the sphere dissipates harmlessly.</p>
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
        return "Form a sphere of pure chakra in your palm, lunge forward, and detonate on impact with massive knockback. "
                + "4-phase cast: Formation -> Charge -> Thrust -> Detonation.";
    }
    @Override public @NotNull SpellIcon icon() {
        return new SpellIcon("SNOWBALL", 3003, "§aRasengan");
    }

    @Override
    public boolean cast(@NotNull Caster caster) {
        Player p = caster.player();
        // Phase 1: Formation — spawn orb model with spin animation
        ModelDisplay orb = SpellEffects.spawnInHand(plugin, p, "rasengan_sphere", "orb_spin", 100);
        if (orb != null) {
            // Start small, grow over 20 ticks
            for (int t = 0; t <= 20; t++) {
                final int tick = t;
                new BukkitRunnable() {
                    @Override public void run() {
                        if (orb.isDead()) return;
                        float s = 0.3f + 0.5f * (tick / 20f); // 0.3 -> 0.8
                        orb.setTransform(0, -0.4f, 0.8f, 0, tick * 18f, 0, s, s, s);
                    }
                }.runTaskLater(plugin, t);
            }
        }

        // Phase 1 + 2: Particle spiral converging on orb
        new BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                if (ticks >= 40) { cancel(); return; } // formation + charge phases
                Location hand = p.getEyeLocation().add(p.getLocation().getDirection().multiply(1.0));
                if (hand.getWorld() == null) return;

                // Converging particles (only during phase 1)
                if (ticks < 20) {
                    for (int i = 0; i < 6; i++) {
                        double angle = (ticks * 0.3) + (i * Math.PI / 3);
                        double r = 2.0 - (ticks / 20.0) * 1.8; // 2.0 -> 0.2
                        double x = Math.cos(angle) * r;
                        double z = Math.sin(angle) * r;
                        Location from = hand.clone().add(x, 0, z);
                        hand.getWorld().spawnParticle(Particle.END_ROD, from, 0,
                                -x * 0.1, -0.05, -z * 0.1, 0.05);
                    }
                }
                // Phase 2: Orbital crit particles
                if (ticks >= 20) {
                    double angle = ticks * 0.5;
                    double r = 0.8;
                    Location orb1 = hand.clone().add(Math.cos(angle) * r, 0.2, Math.sin(angle) * r);
                    Location orb2 = hand.clone().add(Math.cos(angle + Math.PI) * r, -0.2, Math.sin(angle + Math.PI) * r);
                    hand.getWorld().spawnParticle(Particle.CRIT, orb1, 1, 0, 0, 0, 0);
                    hand.getWorld().spawnParticle(Particle.CRIT, orb2, 1, 0, 0, 0, 0);
                }
                // Sound escalating
                if (ticks % 8 == 0) {
                    LocationUtil.sound(hand, Sound.ENTITY_ENDERMAN_AMBIENT, 0.4f + ticks * 0.02f, 1.0f + ticks * 0.02f);
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        // Phase 2 buffs
        new BukkitRunnable() {
            @Override public void run() {
                p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 25, 1));
                p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 25, 1));
            }
        }.runTaskLater(plugin, 20L);

        // Phase 3: Thrust — lunge forward after 40 ticks
        new BukkitRunnable() {
            @Override public void run() {
                if (orb != null && !orb.isDead()) {
                    // Brief scale-up then snap-back
                    orb.setTransform(0, -0.4f, 0.8f, 0, 0, 0, 1.5f, 1.5f, 1.5f);
                    new BukkitRunnable() {
                        @Override public void run() {
                            if (!orb.isDead()) orb.setTransform(0, -0.4f, 0.8f, 0, 0, 0, 1.0f, 1.0f, 1.0f);
                        }
                    }.runTaskLater(plugin, 4L);
                }
                // Lunge forward 3 blocks
                Vector dash = p.getLocation().getDirection().setY(0).normalize().multiply(3.0).setY(0.2);
                p.setVelocity(dash);
                LocationUtil.sound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 1.5f);
            }
        }.runTaskLater(plugin, 40L);

        // Phase 4: Detonation check at 50 ticks
        new BukkitRunnable() {
            @Override public void run() {
                Location hand = p.getEyeLocation().add(p.getLocation().getDirection().multiply(1.0));
                if (orb != null) orb.remove();
                List<LivingEntity> near = LocationUtil.nearbyLiving(hand, 4.0, p.getUniqueId());
                if (near.isEmpty()) {
                    // Dissipate harmlessly
                    if (hand.getWorld() != null) {
                        hand.getWorld().spawnParticle(Particle.CLOUD, hand, 20, 0.5, 0.5, 0.5, 0.05);
                    }
                    LocationUtil.sound(hand, Sound.ENTITY_ENDERMAN_TELEPORT, 0.5f, 0.7f);
                    return;
                }
                // Detonate!
                plugin.getParticleEngine().play(
                        new SphereAnimation(plugin, p, hand, Particle.END_ROD, 8, 0.5, 5.0, 80));
                plugin.getParticleEngine().play(
                        new SphereAnimation(plugin, p, hand, Particle.CLOUD, 8, 0.3, 3.5, 40));
                plugin.getParticleEngine().play(
                        new SphereAnimation(plugin, p, hand, Particle.SPORE_BLOSSOM_AIR, 6, 0.2, 2.5, 30));
                LocationUtil.sound(hand, Sound.ENTITY_GENERIC_EXPLODE, 1.6f, 0.8f);
                LocationUtil.sound(hand, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 1.5f);

                double dmg = 16.0 * plugin.getConfig().getDouble("schools.naruto.damage-multiplier", 1.0);
                for (LivingEntity e : LocationUtil.nearbyLiving(hand, 5.0, p.getUniqueId())) {
                    e.damage(dmg, p);
                    LocationUtil.knockback(e, hand, 2.0);
                    e.setVelocity(e.getVelocity().setY(0.8)); // pop up
                }
            }
        }.runTaskLater(plugin, 50L);

        return true;
    }
}
