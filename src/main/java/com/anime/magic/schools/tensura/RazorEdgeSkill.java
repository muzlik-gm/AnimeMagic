package com.anime.magic.schools.tensura;

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
 * Razor Edge — A pinpoint strike that bypasses armor. Fires a thin Bezier trajectory
 * of crit particles at the target. On hit, applies damage that ignores 50% of armor
 * by directly removing HP and applying bleeding for 4 seconds.
 */
public final class RazorEdgeSkill implements Spell {
    private final AnimeMagicPlugin plugin;

    public RazorEdgeSkill(AnimeMagicPlugin plugin) { this.plugin = plugin; }

    @Override public @NotNull String id() { return "tensura:razor_edge"; }
    @Override public @NotNull String displayName() { return "§5Skill §8» §dRazor Edge"; }
    @Override public @NotNull SchoolId school() { return SchoolId.TENSURA; }
    @Override public int manaCost() { return 20; }
    @Override public long cooldownMs() { return 3000; }
    @Override public int requiredLevel() { return 8; }
    @Override public @NotNull String description() {
        return "A pinpoint strike that pierces armor and inflicts bleeding for 4 seconds.";
    }
    @Override public @NotNull SpellIcon icon() {
        return new SpellIcon("NETHERITE_SWORD", 4003, "§dRazor Edge");
    }

    @Override
    public boolean cast(@NotNull Caster caster) {
        Player p = caster.player();
        LivingEntity target = caster.targetEntity(25.0);
        if (target == null) return false;

        Location start = p.getEyeLocation();
        Location end = target.getEyeLocation();
        Vector ctrl1 = new Vector(0, 1.0, 0);
        Vector ctrl2 = new Vector(0, 0.5, 0);

        plugin.getParticleEngine().play(
                new BezierCurve(plugin, p, start, end, ctrl1, ctrl2,
                        Particle.CRIT, 10, 6, 0.05));

        LocationUtil.sound(start, Sound.ENTITY_ARROW_SHOOT, 1.0f, 2.0f);

        new BukkitRunnable() {
            @Override public void run() {
                if (!p.isOnline()) { cancel(); return; }
                double dmg = 16.0 * plugin.getConfig().getDouble("schools.tensura.damage-multiplier", 1.0);
                double current = target.getHealth();
                target.setHealth(Math.max(0.5, current - dmg / 2));
                target.damage(dmg / 2, p);
                new BukkitRunnable() {
                    int ticks = 0;
                    @Override public void run() {
                        if (!p.isOnline()) { cancel(); return; }
                        if (ticks++ > 80 || target.isDead()) { cancel(); return; }
                        if (ticks % 20 == 0) target.damage(1.5, p);
                        if (target.getWorld() != null) {
                            target.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR,
                                    target.getEyeLocation(), 1, 0.3, 0.3, 0.3, 0.0);
                        }
                    }
                }.runTaskTimer(plugin, 0L, 5L);
                LocationUtil.sound(target.getLocation(), Sound.ENTITY_PLAYER_HURT, 1.0f, 1.8f);
            }
        }.runTaskLater(plugin, 10L);

        return true;
    }
}
