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
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * <b>Rasengan</b> — Spiraling Sphere.
 *
 * <p>Spawns a 3D {@code magic_orb} model (ItemDisplay with the rasengan sphere texture)
 * in front of the caster's hand, plays the {@code orb_spin} animation on it, and the
 * orb continuously swirls while the caster moves around. When the caster approaches a
 * target within 2.5 blocks, the sphere detonates with a powerful blast wave and
 * knockback. If no target is hit within 5 seconds, the sphere dissipates.</p>
 *
 * <p>The 3D model + animation replace the older pure-particle version, giving the
 * spell a tangible in-world presence. Particle effects are layered on top for the
 * swirl trail and detonation burst.</p>
 */
public final class RasenganSpell implements Spell {

    private final AnimeMagicPlugin plugin;

    public RasenganSpell(AnimeMagicPlugin plugin) { this.plugin = plugin; }

    @Override public @NotNull String id() { return "naruto:rasengan"; }
    @Override public @NotNull String displayName() { return "§aRasengan §8» §7Spiraling Sphere"; }
    @Override public @NotNull SchoolId school() { return SchoolId.NARUTO; }
    @Override public int manaCost() { return 35; }
    @Override public long cooldownMs() { return 6000; }
    @Override public int requiredLevel() { return 10; }
    @Override public @NotNull String description() {
        return "Form a 3D sphere of pure chakra in your palm. Detonates on contact with massive knockback.";
    }
    @Override public @NotNull SpellIcon icon() {
        return new SpellIcon("SNOWBALL", 3003, "§aRasengan");
    }

    @Override
    public boolean cast(@NotNull Caster caster) {
        Player p = caster.player();

        // --- Spawn the 3D orb model with the spin animation ---
        // The model follows the player's hand position every tick.
        ModelDisplay orb = SpellEffects.spawnInHand(plugin, p, "rasengan_sphere", "orb_spin", 100);

        // --- Continuous particle swirl on top of the model ---
        // The model gives us the 3D presence; particles add the magical aura.
        new BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                if (ticks++ > 100) { cancel(); return; }
                if (orb != null && !orb.isDead()) {
                    // Continuously restart the spin animation if it's finished (orb_spin loops but
                    // the player halts at the last keyframe; force a replay every 40 ticks = 2s)
                    if (ticks % 40 == 0) {
                        var anim = plugin.getAnimationRegistry().get("orb_spin");
                        if (anim != null) orb.playAnimation(anim);
                    }
                }
                Location hand = p.getEyeLocation().add(p.getLocation().getDirection().multiply(1.0));
                if (hand.getWorld() == null) return;

                // Outer particle sphere (smaller than the model, just for sparkle)
                double r = 0.5;
                for (int i = 0; i < 16; i++) {
                    double phi = Math.acos(1 - 2 * (i + 0.5) / 16);
                    double theta = Math.PI * (1 + Math.sqrt(5)) * i + (ticks * 0.4);
                    double x = r * Math.cos(theta) * Math.sin(phi);
                    double y = r * Math.sin(theta) * Math.sin(phi);
                    double z = r * Math.cos(phi);
                    hand.getWorld().spawnParticle(Particle.END_ROD, hand, 0, x, y, z, 0.05);
                }
                // Spiral wisps
                if (ticks % 2 == 0) {
                    hand.getWorld().spawnParticle(Particle.CLOUD, hand, 1, 0.3, 0.3, 0.3, 0.0);
                }

                // Sound cue every second
                if (ticks % 20 == 0) {
                    LocationUtil.sound(hand, Sound.ENTITY_ENDERMAN_AMBIENT, 0.5f, 1.5f);
                }

                // Check for nearby target → detonate
                List<LivingEntity> near = LocationUtil.nearbyLiving(hand, 2.5, p.getUniqueId());
                if (!near.isEmpty()) {
                    detonate(p, hand, near.get(0));
                    if (orb != null) orb.remove();
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);

        return true;
    }

    private void detonate(Player caster, Location at, LivingEntity primary) {
        // Big sphere burst (particles — model is already removed)
        plugin.getParticleEngine().play(
                new SphereAnimation(plugin, caster, at, Particle.END_ROD, 10, 0.4, 5.0, 100));
        plugin.getParticleEngine().play(
                new SphereAnimation(plugin, caster, at, Particle.CLOUD, 10, 0.5, 3.0, 40));

        LocationUtil.sound(at, Sound.ENTITY_GENERIC_EXPLODE, 1.4f, 1.2f);

        double dmg = 12.0 * plugin.getConfig().getDouble("schools.naruto.damage-multiplier", 1.0);
        List<LivingEntity> hit = LocationUtil.nearbyLiving(at, 4.0, caster.getUniqueId());
        for (LivingEntity e : hit) {
            e.damage(dmg, caster);
            LocationUtil.knockback(e, at, 1.5);
        }
    }
}
