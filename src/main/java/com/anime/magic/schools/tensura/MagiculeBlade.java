package com.anime.magic.schools.tensura;

import com.anime.magic.AnimeMagicPlugin;
import com.anime.magic.api.Caster;
import com.anime.magic.api.Spell;
import com.anime.magic.effects.BezierCurve;
import com.anime.magic.effects.SpiralAnimation;
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

/**
 * <b>Magicule Blade v2 — Production Overhaul</b>
 *
 * <p>Multi-phase sword summoning and slashing:</p>
 *
 * <ol>
 *   <li><b>Phase 1 — Sheath Draw (10 ticks / 0.5s):</b> A 3D {@code magicule_sword}
 *       model spawns at the player's side playing {@code animation.sword.draw}. The
 *       blade materializes out of purple magicule particles converging from around
 *       the player.</li>
 *
 *   <li><b>Phase 2 — Active Stance (200 ticks / 10s):</b> The blade follows the player's
 *       hand. Strength III + Resistance I + Haste I. Every 2 seconds a violet spiral
 *       of particles drifts up from the blade.</li>
 *
 *   <li><b>Phase 3 — Slash (on sneak):</b> When the player sneaks, the blade plays
 *       {@code animation.sword.slash_heavy}, the player dashes forward 2 blocks,
 *       a violet slash arc particles spawn in front, and the target takes 14 magic
 *       damage with knockback. Cooldown 2s between slashes.</li>
 * </ol>
 *
 * <p>Replaces the older particle-only version with a tangible 3D blade the player
 * can see in their hand.</p>
 */
public final class MagiculeBlade implements Spell {
    private final AnimeMagicPlugin plugin;

    public MagiculeBlade(AnimeMagicPlugin plugin) { this.plugin = plugin; }

    @Override public @NotNull String id() { return "tensura:magicule_blade"; }
    @Override public @NotNull String displayName() { return "§5§lMagicule §8» §d§lBlade"; }
    @Override public @NotNull Spell.SchoolId school() { return Spell.SchoolId.TENSURA; }
    @Override public int manaCost() { return 40; }
    @Override public long cooldownMs() { return 12000; }
    @Override public int requiredLevel() { return 12; }
    @Override public @NotNull String description() {
        return "Materialize a 3D blade of pure magicules. Strength III + extended reach for 10s. "
                + "Sneak to dash-slash for 14 magic damage + knockback. 3-phase: Draw -> Stance -> Slash.";
    }
    @Override public @NotNull Spell.SpellIcon icon() {
        return new SpellIcon("DIAMOND_SWORD", 4001, "§dMagicule Blade");
    }

    @Override
    public boolean cast(@NotNull Caster caster) {
        Player p = caster.player();

        // Phase 1: Draw — spawn sword model with draw animation
        ModelDisplay blade = SpellEffects.spawnInHand(plugin, p, "magicule_sword", "animation.sword.draw", 210);
        if (blade != null) {
            // Start invisible/small
            blade.setTransform(0, -0.4f, 0.8f, 0, -90, 0, 0.5f, 0.5f, 0.5f);
        }

        // Converging magicule particles during draw
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (!p.isOnline()) { cancel(); return; }
                if (t >= 10) { cancel(); return; }
                Location hand = p.getEyeLocation().add(p.getLocation().getDirection().multiply(0.8));
                if (hand.getWorld() == null) return;
                for (int i = 0; i < 5; i++) {
                    double angle = Math.random() * Math.PI * 2;
                    double r = 2.0 - t * 0.18;
                    Location from = hand.clone().add(Math.cos(angle) * r, Math.random() * 2 - 1, Math.sin(angle) * r);
                    hand.getWorld().spawnParticle(Particle.PORTAL, from, 0,
                            -Math.cos(angle) * 0.15, -0.1, -Math.sin(angle) * 0.15, 0.05);
                }
                if (t % 3 == 0) {
                    LocationUtil.sound(hand, Sound.BLOCK_BEACON_POWER_SELECT, 0.5f, 1.5f + t * 0.05f);
                }
                t++;
            }
        }.runTaskTimer(plugin, 0L, 4L);

        // Phase 2: Active stance — continuous spiral + buffs
        plugin.getParticleEngine().play(
                new SpiralAnimation(plugin, p, Particle.PORTAL,
                        200, 0.4, 1.2, 0.3, 6, 0.4));
        p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 200, 2));
        p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 200, 1));
        p.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 200, 1));

        // Poller for sneak-triggered slash
        new BukkitRunnable() {
            int ticks = 0;
            boolean slashCooldown = false;
            @Override public void run() {
                if (!p.isOnline()) { cancel(); return; }
                if (ticks++ > 200) {
                    if (blade != null) blade.remove();
                    cancel();
                    return;
                }
                if (slashCooldown) { slashCooldown = false; return; }
                if (p.isSneaking()) {
                    LivingEntity target = caster.targetEntity(6.0);
                    if (target != null && !target.equals(p)) {
                        slash(p, target, blade);
                        slashCooldown = true; // 4-tick cooldown = 0.2s
                    }
                }
            }
        }.runTaskTimer(plugin, 10L, 4L);

        return true;
    }

    private void slash(Player caster, LivingEntity target, ModelDisplay blade) {
        // Play heavy slash animation
        if (blade != null && !blade.isDead()) {
            var slashAnim = plugin.getAnimationRegistry().get("animation.sword.slash_heavy");
            if (slashAnim != null) blade.playAnimation(slashAnim);
        }

        // Dash forward 2 blocks toward target
        Vector dash = target.getLocation().toVector().subtract(caster.getLocation().toVector()).normalize().multiply(2.0);
        dash.setY(0.2);
        caster.setVelocity(dash);

        // Violet slash arc
        Location from = caster.getEyeLocation();
        Location to = target.getLocation().add(0, 1, 0);
        Vector dir = to.toVector().subtract(from.toVector());
        if (dir.lengthSquared() < 1e-9) dir = new Vector(0, 0, 1);
        dir.normalize();
        Vector perp = dir.clone().crossProduct(new Vector(0, 1, 0));
        if (perp.lengthSquared() < 1e-6) perp = new Vector(1, 0, 0);
        perp.normalize();

        // Bezier slash trail — clone perp for each control point so the second
        // multiply(-1.0) does NOT retroactively mutate the first argument.
        // (Vector.multiply mutates in place and returns `this`.)
        Vector cp1 = perp.clone();
        Vector cp2 = perp.clone().multiply(-1.0);
        plugin.getParticleEngine().play(
                new BezierCurve(plugin, caster, from, to, cp1, cp2,
                        Particle.DRAGON_BREATH, 8, 6, 0.05));

        // Slash arc particles
        for (int i = -5; i <= 5; i++) {
            double angle = i * Math.PI / 10;
            Vector offset = perp.clone().multiply(Math.cos(angle) * 1.5)
                    .add(new Vector(0, Math.sin(angle) * 0.5, 0));
            Location at = caster.getEyeLocation().add(dir.clone().multiply(2.5)).add(offset);
            if (at.getWorld() != null) {
                at.getWorld().spawnParticle(Particle.PORTAL, at, 5, 0.05, 0.05, 0.05, 0.2);
                at.getWorld().spawnParticle(Particle.DRAGON_BREATH, at, 1, 0.1, 0.1, 0.1, 0.02);
            }
        }

        double dmg = 14.0 * plugin.getConfig().getDouble("schools.tensura.damage-multiplier", 1.0);
        target.damage(dmg, caster);
        LocationUtil.knockback(target, caster.getLocation(), 0.8);
        LocationUtil.sound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_STRONG, 1.0f, 1.5f);
        LocationUtil.sound(target.getLocation(), Sound.BLOCK_GLASS_BREAK, 0.6f, 1.8f);
    }
}
