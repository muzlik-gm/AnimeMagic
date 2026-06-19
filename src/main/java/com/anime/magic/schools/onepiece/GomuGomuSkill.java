package com.anime.magic.schools.onepiece;

import com.anime.magic.AnimeMagicPlugin;
import com.anime.magic.api.Caster;
import com.anime.magic.api.Spell;
import com.anime.magic.effects.BezierCurve;
import com.anime.magic.util.LocationUtil;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

/**
 * Gomu Gomu no Pistol — Gum-Gum Pistol. The caster winds up for 0.5s, then their arm
 * "stretches" forward (visualized as a Bezier curve of red particles from shoulder
 * to target). On hit, deals heavy knockback punch damage. If no target is found
 * within 12 blocks, the cast fizzles and mana is refunded.
 */
public final class GomuGomuSkill implements Spell {
    private final AnimeMagicPlugin plugin;

    public GomuGomuSkill(AnimeMagicPlugin plugin) { this.plugin = plugin; }

    @Override public @NotNull String id() { return "onepiece:gomu_pistol"; }
    @Override public @NotNull String displayName() { return "§cGomu Gomu §8» §fPistol"; }
    @Override public @NotNull SchoolId school() { return SchoolId.ONEPIECE; }
    @Override public int manaCost() { return 25; }
    @Override public long cooldownMs() { return 4000; }
    @Override public int requiredLevel() { return 6; }
    @Override public @NotNull String description() {
        return "Stretch your arm forward and unleash a powerful knockback punch.";
    }
    @Override public @NotNull SpellIcon icon() {
        return new SpellIcon("RED_DYE", 6003, "§cGomu Gomu Pistol");
    }

    @Override
    public boolean cast(@NotNull Caster caster) {
        Player p = caster.player();

        new BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                if (ticks++ < 10) {
                    Location hand = p.getEyeLocation().add(p.getLocation().getDirection().multiply(0.8));
                    if (hand.getWorld() != null) {
                        hand.getWorld().spawnParticle(Particle.CRIT, hand, 3, 0.2, 0.2, 0.2, 0.1);
                    }
                    LocationUtil.sound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.3f, 1.8f);
                    return;
                }
                cancel();
                fire(p);
            }
        }.runTaskTimer(plugin, 0L, 1L);
        return true;
    }

    private void fire(Player p) {
        LivingEntity target = new Caster(plugin, p, this).targetEntity(12.0);
        Location start = p.getEyeLocation().add(p.getLocation().getDirection().multiply(0.5));

        if (target == null) {
            if (start.getWorld() != null) {
                start.getWorld().spawnParticle(Particle.SMOKE, start, 10, 0.3, 0.3, 0.3, 0.05);
            }
            LocationUtil.sound(start, Sound.ENTITY_ITEM_BREAK, 0.5f, 1.5f);
            plugin.getManaManager().add(p.getUniqueId(), this.manaCost());
            return;
        }

        Vector ctrl = new Vector(0, 0.5, 0);
        plugin.getParticleEngine().play(
                new BezierCurve(plugin, p, start, target.getEyeLocation(),
                        ctrl, ctrl, Particle.DUST, 8, 8, 0.0));

        LocationUtil.sound(start, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 0.7f);

        new BukkitRunnable() {
            @Override public void run() {
                double dmg = 14.0 * plugin.getConfig().getDouble("schools.onepiece.damage-multiplier", 1.0);
                target.damage(dmg, p);
                Vector knock = target.getLocation().toVector()
                        .subtract(p.getLocation().toVector()).normalize().multiply(1.5).setY(0.5);
                target.setVelocity(knock);
                Location at = target.getEyeLocation();
                if (at.getWorld() != null) {
                    at.getWorld().spawnParticle(Particle.DUST, at, 20, 0.4, 0.4, 0.4, 0.1);
                    at.getWorld().spawnParticle(Particle.CLOUD, at, 10, 0.3, 0.3, 0.3, 0.05);
                }
                LocationUtil.sound(at, Sound.ENTITY_PLAYER_ATTACK_STRONG, 1.2f, 0.8f);
            }
        }.runTaskLater(plugin, 8L);
    }
}
