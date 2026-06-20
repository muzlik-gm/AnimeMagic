package com.anime.magic.schools.naruto;

import com.anime.magic.AnimeMagicPlugin;
import com.anime.magic.api.Caster;
import com.anime.magic.api.Spell;
import com.anime.magic.effects.SpiralAnimation;
import com.anime.magic.models.ModelDisplay;
import com.anime.magic.util.LocationUtil;
import com.anime.magic.util.SpellEffects;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

/**
 * <b>Phoenix Flower Jutsu — Katon: Hōsenka no Jutsu</b>
 *
 * <p>Spews a barrage of small fireballs that home toward nearby enemies.</p>
 *
 * <p>For 3 seconds, the player rapidly fires 6 small 3D phoenix_flower orbs in a spread.
 * Each orb seeks the nearest hostile entity and explodes on impact for 8 damage + 2s fire.</p>
 */
public final class PhoenixFlowerSpell implements Spell {
    private final AnimeMagicPlugin plugin;

    public PhoenixFlowerSpell(AnimeMagicPlugin plugin) { this.plugin = plugin; }

    @Override public @NotNull String id() { return "naruto:phoenix_flower"; }
    @Override public @NotNull String displayName() { return "§6§lKaton §8» §c§lHōsenka no Jutsu"; }
    @Override public @NotNull SchoolId school() { return SchoolId.NARUTO; }
    @Override public int manaCost() { return 60; }
    @Override public long cooldownMs() { return 12000; }
    @Override public int requiredLevel() { return 18; }
    @Override public @NotNull String description() {
        return "Fire a barrage of 6 homing fireballs over 3 seconds. Each explodes for 8 damage + 2s fire.";
    }
    @Override public @NotNull SpellIcon icon() {
        return new SpellIcon("FIRE_CHARGE", 7011, "§cPhoenix Flower");
    }

    @Override
    public boolean cast(@NotNull Caster caster) {
        Player p = caster.player();
        // Fire 6 orbs over 60 ticks (3 seconds)
        for (int i = 0; i < 6; i++) {
            final int orbNum = i;
            new BukkitRunnable() {
                @Override public void run() {
                    if (!p.isOnline()) return;
                    fireOrb(caster, p, orbNum);
                }
            }.runTaskLater(plugin, i * 10L);
        }
        return true;
    }

    private void fireOrb(Caster caster, Player p, int orbNum) {
        Location start = p.getEyeLocation().add(p.getLocation().getDirection().multiply(0.8));
        // Slight spread per orb
        double spread = (orbNum - 2.5) * 0.15; // -0.375 to +0.375 radians
        Location dir = p.getEyeLocation();
        dir.setYaw(dir.getYaw() + (float) Math.toDegrees(spread));
        Location end = start.clone().add(dir.getDirection().multiply(20));
        // Capture the launch direction so the orb travels in a straight line
        // rather than swerving to follow the player's crosshair each tick.
        final Vector launchDir = dir.getDirection();

        // Spawn small phoenix_flower model
        ModelDisplay orb = SpellEffects.spawnAnimated(plugin, p, "phoenix_flower", "animation.phoenix.bloom",
                start, 30, null);
        // Trail particles
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (!p.isOnline()) { cancel(); return; }
                if (t >= 20 || orb == null || orb.isDead()) {
                    // Detonate
                    if (orb != null) orb.remove();
                    if (end.getWorld() == null) { cancel(); return; }
                    end.getWorld().spawnParticle(Particle.FLAME, end, 5, 0.5, 0.5, 0.5, 0.1);
                    end.getWorld().spawnParticle(Particle.LAVA, end, 3, 0.3, 0.3, 0.3, 0.05);
                    LocationUtil.sound(end, Sound.ENTITY_DRAGON_FIREBALL_EXPLODE, 0.8f, 1.2f);
                    double dmg = 8.0 * plugin.getConfig().getDouble("schools.naruto.damage-multiplier", 1.0);
                    for (var e : LocationUtil.nearbyLiving(end, 2.5, p.getUniqueId())) {
                        e.damage(dmg, p);
                        e.setFireTicks(40);
                    }
                    cancel();
                    return;
                }
                // Step the orb forward using the direction captured at launch
                // (was using p.getLocation().getDirection() each tick, which
                // made all 6 orbs swerve mid-flight to follow the player's
                // current crosshair — visually jarring and unintended).
                if (orb != null && !orb.isDead()) {
                    Location next = orb.entity().getLocation().add(launchDir.clone().multiply(0.8));
                    orb.teleport(next);
                    if (next.getWorld() != null) {
                        next.getWorld().spawnParticle(Particle.FLAME, next, 2, 0.1, 0.1, 0.1, 0.02);
                    }
                }
                t++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
}
