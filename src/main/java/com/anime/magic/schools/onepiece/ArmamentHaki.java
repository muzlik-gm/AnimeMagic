package com.anime.magic.schools.onepiece;

import com.anime.magic.AnimeMagicPlugin;
import com.anime.magic.api.Caster;
import com.anime.magic.api.Spell;
import com.anime.magic.effects.SpiralAnimation;
import com.anime.magic.util.LocationUtil;
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
 * Armament Haki (Busoshoku Haki) — Hardening. For 20 seconds, the caster gains
 * Resistance III, Strength III, and Slowness I (the heavy-armor feel). Each melee hit
 * triggers a burst of black particles and applies bonus true damage. Visualized as
 * a continuous dark spiral around the caster's torso.
 */
public final class ArmamentHaki implements Spell {
    private final AnimeMagicPlugin plugin;

    public ArmamentHaki(AnimeMagicPlugin plugin) { this.plugin = plugin; }

    @Override public @NotNull String id() { return "onepiece:armament_haki"; }
    @Override public @NotNull String displayName() { return "§8Haki §8» §0Armament Hardening"; }
    @Override public @NotNull SchoolId school() { return SchoolId.ONEPIECE; }
    @Override public int manaCost() { return 45; }
    @Override public long cooldownMs() { return 18000; }
    @Override public int requiredLevel() { return 18; }
    @Override public @NotNull String description() {
        return "Coat your body in hardened will. Resistance III + Strength III for 20 seconds.";
    }
    @Override public @NotNull SpellIcon icon() {
        return new SpellIcon("OBSIDIAN", 6002, "§0Armament Haki");
    }

    @Override
    public boolean cast(@NotNull Caster caster) {
        Player p = caster.player();
        plugin.getParticleEngine().play(
                new SpiralAnimation(plugin, p, Particle.SQUID_INK, 400, 0.4, 0.9, 0.2, 6, 0.3));

        p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 400, 2));
        p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 400, 2));
        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 400, 0));
        p.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 400, 0));

        LocationUtil.sound(p.getLocation(), Sound.BLOCK_ANVIL_LAND, 1.0f, 0.7f);

        new BukkitRunnable() {
            int ticks = 0;
            boolean onCooldown = false;
            @Override public void run() {
                if (!p.isOnline()) { cancel(); return; }
                if (ticks++ > 400) { cancel(); return; }
                if (onCooldown) { onCooldown = false; return; }
                if (p.isSneaking()) {
                    LivingEntity t = caster.targetEntity(4.0);
                    if (t != null && !t.equals(p)) {
                        strike(p, t);
                        onCooldown = true;
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 8L);

        return true;
    }

    private void strike(Player caster, LivingEntity target) {
        Location at = target.getEyeLocation();
        if (at.getWorld() == null) return;
        at.getWorld().spawnParticle(Particle.SQUID_INK, at, 30, 0.4, 0.4, 0.4, 0.05);
        at.getWorld().spawnParticle(Particle.CRIT, at, 15, 0.3, 0.3, 0.3, 0.3);

        double dmg = 12.0 * plugin.getConfig().getDouble("schools.onepiece.damage-multiplier", 1.0);
        double current = target.getHealth();
        target.setHealth(Math.max(0.5, current - dmg / 2));
        target.damage(dmg / 2, caster);

        LocationUtil.knockback(target, caster.getLocation(), 0.7);
        LocationUtil.sound(at, Sound.ENTITY_PLAYER_ATTACK_STRONG, 1.2f, 0.6f);
    }
}
