package com.anime.magic.schools.tensura;

import com.anime.magic.AnimeMagicPlugin;
import com.anime.magic.api.Caster;
import com.anime.magic.api.Spell;
import com.anime.magic.effects.HelixEffect;
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

/**
 * <b>Gluttony v2 — Production Overhaul (Unique Skill: Predator)</b>
 *
 * <p>Multi-phase life-drain:</p>
 *
 * <ol>
 *   <li><b>Phase 1 — Mark (10 ticks / 0.5s):</b> A 3D {@code magic_orb} model spawns
 *       above the target's head, growing from 0.1 to 1.0 scale. Dark tendrils
 *       (SQUID_INK particles) extend from the target's body toward the orb.</li>
 *
 *   <li><b>Phase 2 — Channel Drain (40 ticks / 2s):</b> The orb plays
 *       {@code animation.charge.grow}. A continuous beam of dark particles flows
 *       from the target's torso to the orb, then from the orb to the caster's chest.
 *       The target takes 1 damage per tick (40 total). The caster heals 0.5 HP per
 *       tick. Sound: elder guardian curse loop.</li>
 *
 *   <li><b>Phase 3 — Absorb (10 ticks / 0.5s):</b> The orb collapses into the caster's
 *       chest with a SQUID_INK sphere burst. The caster gains Strength II + Regeneration II
 *       for 15 seconds. The target is launched upward with knockback.</li>
 * </ol>
 */
public final class GluttonySkill implements Spell {
    private final AnimeMagicPlugin plugin;

    public GluttonySkill(AnimeMagicPlugin plugin) { this.plugin = plugin; }

    @Override public @NotNull String id() { return "tensura:gluttony"; }
    @Override public @NotNull String displayName() { return "§5§lᴜnique ꜱkill §8» §d§lɢluttony"; }
    @Override public @NotNull Spell.SchoolId school() { return Spell.SchoolId.TENSURA; }
    @Override public int manaCost() { return 70; }
    @Override public long cooldownMs() { return 18000; }
    @Override public int requiredLevel() { return 25; }
    @Override public @NotNull String description() {
        return "Drain 40 HP from a target over 2 seconds via a 3D orb. Heal yourself, gain Strength II + Regen II. "
                + "3-phase cast: Mark -> Drain -> Absorb.";
    }
    @Override public @NotNull Spell.SpellIcon icon() {
        return new SpellIcon("BLACK_DYE", 4002, "§dGluttony");
    }

    @Override
    public boolean cast(@NotNull Caster caster) {
        Player p = caster.player();
        LivingEntity target = caster.targetEntity(10.0);
        if (target == null) return false;

        // Phase 1: Mark — orb above target's head
        Location orbLoc = target.getEyeLocation().add(0, 1.8, 0);
        ModelDisplay orb = SpellEffects.spawnAnimated(plugin, p, "magic_orb", "animation.charge.grow",
                orbLoc, 60, null);

        // Tendrils from target to orb during phase 1
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (!p.isOnline()) { cancel(); return; }
                if (t >= 10 || target.isDead()) { cancel(); return; }
                if (target.getWorld() == null) { cancel(); return; }
                if (t % 3 == 0) {
                    LocationUtil.sound(target.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.4f, 0.6f);
                }
                t++;
            }
        }.runTaskTimer(plugin, 0L, 10L);

        // Phase 2: Drain beam — 40 ticks starting at 10
        new BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                if (ticks >= 40 || target.isDead() || !p.isOnline()) {
                    // Move to phase 3 — only if caster is still online.
                    if (p.isOnline()) {
                        new BukkitRunnable() {
                            @Override public void run() {
                                if (!p.isOnline()) { if (orb != null) orb.remove(); return; }
                                absorb(p, target, orb);
                            }
                        }.runTaskLater(plugin, 1L);
                    } else if (orb != null) {
                        orb.remove();
                    }
                    cancel();
                    return;
                }
                if (target.getWorld() == null || p.getWorld() == null) { cancel(); return; }

                // Damage target + heal caster. Reset noDamageTicks so every tick's
                // 1.0 damage actually lands (Bukkit default 10-tick invulnerability
                // would otherwise absorb 9 of every 10 hits → only ~4 damage total).
                target.setNoDamageTicks(0);
                target.damage(2.0, p);
                if (p.getHealth() < p.getMaxHealth()) {
                    p.setHealth(Math.min(p.getMaxHealth(), p.getHealth() + 0.5));
                }

                // Sound loop
                if (ticks % 6 == 0) {
                    LocationUtil.sound(target.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.5f, 0.7f);
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 10L, 1L);

        return true;
    }

    private void absorb(Player caster, LivingEntity target, ModelDisplay orb) {
        // Phase 3: Collapse orb into caster's chest
        if (orb != null && !orb.isDead()) {
            // Beam one last time
            Location orbAt = orb.entity().getLocation();
            if (caster.getWorld() != null) {
                try { caster.getWorld().spawnParticle(Particle.SQUID_INK, orbAt, 1, 0.1, 0.1, 0.1, 0.05); } catch (Throwable ignored) {}
            }
            orb.remove();
        }

        // Sphere burst on caster
        plugin.getParticleEngine().play(
                new SphereAnimation(plugin, caster, caster.getEyeLocation(),
                        Particle.WITCH, 12, 2.0, 0.3, 50));
        plugin.getParticleEngine().play(
                new HelixEffect(plugin, caster, Particle.HEART,
                        25, 0.9, 0.5, 6, 0.5));

        // Buffs
        caster.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 300, 1));
        caster.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 1));
        caster.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 100, 0));

        // Knock target away
        if (!target.isDead()) {
            target.setVelocity(new Vector(0, 1.0, 0));
            LocationUtil.sound(target.getLocation(), Sound.ENTITY_PLAYER_HURT, 1.0f, 0.6f);
        }
        LocationUtil.sound(caster.getLocation(), Sound.ENTITY_PLAYER_BURP, 1.0f, 0.6f);
        LocationUtil.sound(caster.getLocation(), Sound.ENTITY_WITHER_DEATH, 0.5f, 1.5f);
    }
}
