package com.anime.magic.schools.onepiece;

import com.anime.magic.AnimeMagicPlugin;
import com.anime.magic.api.Caster;
import com.anime.magic.api.Spell;
import com.anime.magic.effects.HelixEffect;
import com.anime.magic.effects.SphereAnimation;
import com.anime.magic.util.LocationUtil;
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
 * <b>Gear Fourth — Boundman</b>
 *
 * <p>Luffy's strongest Gear — inflates his muscles with Haki, gaining massive strength
 * and elastic defense for 25 seconds. Every hit deals AoE damage.</p>
 */
public final class GearFourthSpell implements Spell {
    private final AnimeMagicPlugin plugin;

    public GearFourthSpell(AnimeMagicPlugin plugin) { this.plugin = plugin; }

    @Override public @NotNull String id() { return "onepiece:gear_fourth"; }
    @Override public @NotNull String displayName() { return "§c§lɢear §8» §0§lꜰourth: ʙoundman"; }
    @Override public @NotNull SchoolId school() { return SchoolId.ONEPIECE; }
    @Override public int manaCost() { return 150; }
    @Override public long cooldownMs() { return 60000; }
    @Override public int requiredLevel() { return 50; }
    @Override public @NotNull String description() {
        return "ʙoundman transformation for 25s. ꜱtrength ᴠ, ʀesistance ɪɪɪ. ᴇvery hit deals ᴀoᴇ damage + knockback.";
    }
    @Override public @NotNull SpellIcon icon() { return new SpellIcon("OBSIDIAN", 6002, "§0Gear Fourth"); }

    @Override
    public boolean cast(@NotNull Caster caster) {
        Player p = caster.player();
        // Spawn the 3D gear_fourth_boundman model at the caster's location.
        com.anime.magic.util.SpellEffects.spawnAnimated(plugin, p,
                "gear_fourth_boundman", "animation.gear_fourth.bounce",
                p.getEyeLocation().add(p.getLocation().getDirection().multiply(1.5)).clone(), 300, null);
        // Transform burst
        plugin.getParticleEngine().play(new SphereAnimation(plugin, p, p.getLocation(),
                Particle.SQUID_INK, 20, 0.5, 5.0, 80));
        plugin.getParticleEngine().play(new HelixEffect(plugin, p, Particle.SQUID_INK, 40, 1.5, 0.5, 10, 0.4));
        LocationUtil.sound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 2.0f, 0.5f);
        LocationUtil.sound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.5f, 0.6f);

        new BukkitRunnable() {
            int ticks = 0;
            boolean onCd = false;
            @Override public void run() {
                if (!p.isOnline()) { cancel(); return; }
                if (ticks >= 500) {
                    LocationUtil.sound(p.getLocation(), Sound.ENTITY_WITHER_DEATH, 1.5f, 0.5f);
                    cancel();
                    return;
                }
                // Re-apply buffs every 60 ticks
                if (ticks % 60 == 0) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 80, 4));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 80, 2));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 80, 2));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 0));
                }
                // AoE hit
                if (onCd) { onCd = false; return; }
                if (p.isSneaking()) {
                    LivingEntity t = caster.targetEntity(6.0);
                    if (t != null && !t.equals(p)) {
                        aoeHit(p, t);
                        onCd = true;
                    }
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
        return true;
    }

    private void aoeHit(Player caster, LivingEntity target) {
        Location at = target.getLocation();
        if (at.getWorld() == null) return;
        try { at.getWorld().spawnParticle(Particle.SQUID_INK, at, 1, 1.5, 1.5, 1.5, 0.1); } catch (Throwable ignored) {}
        LocationUtil.sound(at, Sound.ENTITY_PLAYER_ATTACK_STRONG, 2.0f, 0.5f);
        LocationUtil.sound(at, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.7f);

        double dmg = 40.0 * plugin.getConfig().getDouble("schools.onepiece.damage-multiplier", 1.0);
        for (LivingEntity e : LocationUtil.nearbyLiving(at, 4.0, caster.getUniqueId())) {
            e.damage(dmg, caster);
            Vector knock = e.getLocation().toVector()
                    .subtract(caster.getLocation().toVector()).normalize().multiply(2.0).setY(1.2);
            e.setVelocity(knock);
        }
    }
}
