package com.anime.magic.schools.mushoku;

import com.anime.magic.AnimeMagicPlugin;
import com.anime.magic.api.Caster;
import com.anime.magic.api.Spell;
import com.anime.magic.effects.BezierCurve;
import com.anime.magic.effects.SphereAnimation;
import com.anime.magic.util.LocationUtil;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;
import java.util.List;

/**
 * Saint-class Fire Explosive Fireball (Mushoku style). Three-line incantation,
 * then a single fireball flies from the caster's outstretched hand toward the target,
 * exploding for AoE fire damage.
 */
public final class SaintFireSpell implements Spell {
    private final AnimeMagicPlugin plugin;
    private final IncantationSystem incantation;

    public SaintFireSpell(AnimeMagicPlugin plugin, IncantationSystem incantation) {
        this.plugin = plugin;
        this.incantation = incantation;
    }

    @Override public @NotNull String id() { return "mushoku:saint_fire"; }
    @Override public @NotNull String displayName() { return "§3Saint §8» §6Fireball"; }
    @Override public @NotNull SchoolId school() { return SchoolId.MUSHOKU; }
    @Override public int manaCost() { return 45; }
    @Override public long cooldownMs() { return 9000; }
    @Override public int requiredLevel() { return 12; }
    @Override public @NotNull String description() {
        return "Classic Saint-class fireball. Travels in a slight arc and explodes on impact.";
    }
    @Override public @NotNull SpellIcon icon() { return new SpellIcon("BLAZE_POWDER", 5002, "§6Saint Fire"); }
    @Override public @NotNull List<String> incantation() {
        return List.of(
                "Flame, gather in my hand",
                "Burn my enemy to ash",
                "Saint-class Fireball."
        );
    }

    @Override
    public boolean cast(@NotNull Caster caster) {
        Player p = caster.player();
        if (incantation == null) { fire(p); return true; }
        return incantation.begin(p, this, () -> fire(p));
    }

    private void fire(Player p) {
        Location start = p.getEyeLocation().add(p.getLocation().getDirection().multiply(0.8));
        LivingEntity target = new Caster(plugin, p, this).targetEntity(40);
        Location end = target != null ? target.getLocation()
                : start.clone().add(p.getLocation().getDirection().multiply(30));

        plugin.getParticleEngine().play(
                new BezierCurve(plugin, p, start, end, Particle.FLAME, 20, 1.0));
        LocationUtil.sound(start, Sound.ENTITY_BLAZE_SHOOT, 1.0f, 1.0f);

        new BukkitRunnable() {
            @Override public void run() {
                plugin.getParticleEngine().play(
                        new SphereAnimation(plugin, p, end, Particle.FLAME, 12, 0.5, 4.0, 60));
                plugin.getParticleEngine().play(
                        new SphereAnimation(plugin, p, end, Particle.LAVA, 10, 0.3, 2.5, 30));
                LocationUtil.sound(end, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 1.0f);
                double dmg = 10.0 * plugin.getConfig().getDouble("schools.mushoku.damage-multiplier", 1.0);
                for (LivingEntity e : LocationUtil.nearbyLiving(end, 3.5, p.getUniqueId())) {
                    e.damage(dmg, p);
                    e.setFireTicks(60);
                }
            }
        }.runTaskLater(plugin, 20L);
    }
}
