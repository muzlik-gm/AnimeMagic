package com.anime.magic.schools.tensura;

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
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

/**
 * <b>Magicule Blade</b> — Materialize a sword of pure magicules.
 *
 * <p>For 10 seconds, the caster gains Strength III and extended melee reach (5 blocks).
 * A 3D {@code chidori_blade} model (repurposed as a magicule blade) spawns in the
 * caster's hand and plays the {@code slash_arc} animation on every strike. Each
 * strike triggers a violet particle slash arc in front of the player and deals
 * bonus magic damage.</p>
 */
public final class MagiculeBlade implements Spell {

    private final AnimeMagicPlugin plugin;

    public MagiculeBlade(AnimeMagicPlugin plugin) { this.plugin = plugin; }

    @Override public @NotNull String id() { return "tensura:magicule_blade"; }
    @Override public @NotNull String displayName() { return "§5Magicule §8» §dBlade"; }
    @Override public @NotNull Spell.SchoolId school() { return Spell.SchoolId.TENSURA; }
    @Override public int manaCost() { return 30; }
    @Override public long cooldownMs() { return 10000; }
    @Override public int requiredLevel() { return 12; }
    @Override public @NotNull String description() {
        return "Materialize a blade of pure magicules. Strength III + extended reach for 10 seconds.";
    }
    @Override public @NotNull Spell.SpellIcon icon() {
        return new SpellIcon("DIAMOND_SWORD", 4001, "§dMagicule Blade");
    }

    @Override
    public boolean cast(@NotNull Caster caster) {
        Player p = caster.player();

        // --- Spawn the 3D blade model in hand (uses the same model as Chidori but with different texture via CustomModelData) ---
        // The slash_arc animation plays repeatedly while the blade is active.
        ModelDisplay blade = SpellEffects.spawnInHand(plugin, p, "chidori_blade", "slash_arc", 200);

        // Violet spiral around player
        plugin.getParticleEngine().play(
                new SpiralAnimation(plugin, p, Particle.PORTAL,
                        200, 0.5, 1.5, 0.3, 8, 0.5));

        // Buffs
        p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 200, 2));
        p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 200, 1));
        p.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 200, 1));

        LocationUtil.sound(p.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 1.5f);

        // Extended-reach melee poller
        new BukkitRunnable() {
            int ticks = 0;
            boolean onCooldown = false;
            @Override public void run() {
                if (ticks++ > 200) {
                    if (blade != null) blade.remove();
                    cancel();
                    return;
                }
                if (onCooldown) { onCooldown = false; return; }
                // Re-trigger slash animation periodically to keep the blade swinging visually
                if (ticks % 40 == 0 && blade != null && !blade.isDead()) {
                    var anim = plugin.getAnimationRegistry().get("slash_arc");
                    if (anim != null) blade.playAnimation(anim);
                }
                // Detect target within extended reach when sneaking (simulating an attack windup)
                if (p.isSneaking()) {
                    LivingEntity target = caster.targetEntity(5.0);
                    if (target != null && !target.equals(p)) {
                        strike(p, target, blade);
                        onCooldown = true;
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 4L);

        return true;
    }

    private void strike(Player caster, LivingEntity target, ModelDisplay blade) {
        // Replay the slash animation at strike moment
        if (blade != null && !blade.isDead()) {
            var slashAnim = plugin.getAnimationRegistry().get("slash_arc");
            if (slashAnim != null) blade.playAnimation(slashAnim);
        }

        Location from = caster.getEyeLocation();
        Location to = target.getLocation().add(0, 1, 0);
        Vector dir = to.toVector().subtract(from.toVector()).normalize();
        Vector perp = dir.clone().crossProduct(new Vector(0, 1, 0)).normalize();
        if (perp.lengthSquared() < 1e-6) perp = new Vector(1, 0, 0);

        // Slash arc — 11 particles across 180°
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

        double dmg = 10.0 * plugin.getConfig().getDouble("schools.tensura.damage-multiplier", 1.0);
        target.damage(dmg, caster);
        LocationUtil.knockback(target, caster.getLocation(), 0.6);
        LocationUtil.sound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_STRONG, 1.0f, 1.5f);
    }
}
