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
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

/**
 * <b>Gear Third — Bone Balloon</b>
 *
 * <p>Luffy inflates his arm to giant size, gaining massive damage on his next 3 melee
 * hits. Each hit deals bonus knockback.</p>
 */
public final class GearThirdSpell implements Spell {
    private final AnimeMagicPlugin plugin;

    public GearThirdSpell(AnimeMagicPlugin plugin) { this.plugin = plugin; }

    @Override public @NotNull String id() { return "onepiece:gear_third"; }
    @Override public @NotNull String displayName() { return "§c§lGear §8» §f§lThird"; }
    @Override public @NotNull SchoolId school() { return SchoolId.ONEPIECE; }
    @Override public int manaCost() { return 90; }
    @Override public long cooldownMs() { return 35000; }
    @Override public int requiredLevel() { return 35; }
    @Override public @NotNull String description() {
        return "Inflate your arm to giant size for 20s. Next 3 melee hits deal bonus knockback + 25 damage.";
    }
    @Override public @NotNull SpellIcon icon() { return new SpellIcon("RED_DYE", 6003, "§cGear Third"); }

    @Override
    public boolean cast(@NotNull Caster caster) {
        Player p = caster.player();
        plugin.getParticleEngine().play(new SpiralAnimation(plugin, p, Particle.CLOUD,
                400, 0.5, 1.5, 0.4, 8, 0.4));
        LocationUtil.sound(p.getLocation(), Sound.ENTITY_SLIME_SQUISH, 2.0f, 0.5f);
        LocationUtil.sound(p.getLocation(), Sound.ENTITY_SLIME_SQUISH, 1.5f, 0.6f);
        p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 400, 3));
        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 400, 0));
        p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 400, 1));

        // Strike poller
        new BukkitRunnable() {
            int ticks = 0;
            int hitsLeft = 3;
            boolean onCd = false;
            @Override public void run() {
                if (ticks >= 400 || hitsLeft <= 0) { cancel(); return; }
                if (onCd) { onCd = false; return; }
                if (p.isSneaking()) {
                    LivingEntity t = caster.targetEntity(5.0);
                    if (t != null && !t.equals(p)) {
                        giantHit(p, t);
                        hitsLeft--;
                        onCd = true;
                    }
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 6L);

        return true;
    }

    private void giantHit(Player caster, LivingEntity target) {
        Location at = target.getEyeLocation();
        if (at.getWorld() == null) return;
        at.getWorld().spawnParticle(Particle.CLOUD, at, 30, 0.8, 0.8, 0.8, 0.1);
        at.getWorld().spawnParticle(Particle.DUST, at, 25, 0.5, 0.5, 0.5, 0.2);
        at.getWorld().spawnParticle(Particle.SWEEP_ATTACK, at, 3, 0.5, 0.5, 0.5, 0);
        LocationUtil.sound(at, Sound.ENTITY_PLAYER_ATTACK_STRONG, 2.0f, 0.5f);
        LocationUtil.sound(at, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.8f);

        double dmg = 25.0 * plugin.getConfig().getDouble("schools.onepiece.damage-multiplier", 1.0);
        target.damage(dmg, caster);
        Vector knock = target.getLocation().toVector()
                .subtract(caster.getLocation().toVector()).normalize().multiply(3.5).setY(1.0);
        target.setVelocity(knock);
    }
}
