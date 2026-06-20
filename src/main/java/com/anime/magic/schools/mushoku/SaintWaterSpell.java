package com.anime.magic.schools.mushoku;

import com.anime.magic.AnimeMagicPlugin;
import com.anime.magic.api.Caster;
import com.anime.magic.api.Spell;
import com.anime.magic.effects.SphereAnimation;
import com.anime.magic.util.LocationUtil;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;
import java.util.List;

/**
 * Saint-class Water Cleansing Water Burst. After the incantation, summons a sphere
 * of water around the caster that extinguishes fire, removes negative effects, and
 * damages nearby hostiles.
 */
public final class SaintWaterSpell implements Spell {
    private final AnimeMagicPlugin plugin;
    private final IncantationSystem incantation;

    public SaintWaterSpell(AnimeMagicPlugin plugin, IncantationSystem incantation) {
        this.plugin = plugin;
        this.incantation = incantation;
    }

    @Override public @NotNull String id() { return "mushoku:saint_water"; }
    @Override public @NotNull String displayName() { return "§3Saint §8» §bWater Burst"; }
    @Override public @NotNull SchoolId school() { return SchoolId.MUSHOKU; }
    @Override public int manaCost() { return 35; }
    @Override public long cooldownMs() { return 7000; }
    @Override public int requiredLevel() { return 8; }
    @Override public @NotNull String description() {
        return "A purifying sphere of water that douses flames, cures poison, and knocks back foes.";
    }
    @Override public @NotNull SpellIcon icon() { return new SpellIcon("WATER_BUCKET", 5001, "§bSaint Water"); }
    @Override public @NotNull List<String> incantation() {
        return List.of(
                "O water, heed my call",
                "Wash away the unclean",
                "Saint-class Water Burst."
        );
    }

    @Override
    public boolean cast(@NotNull Caster caster) {
        Player p = caster.player();
        if (incantation == null) { fire(p); return true; }
        return incantation.begin(p, this, () -> fire(p));
    }

    private void fire(Player p) {
        // Spawn the 3D saint_water_drop model at the target's location (or forward if no target).
        LivingEntity target = new Caster(plugin, p, this).targetEntity(40);
        com.anime.magic.util.SpellEffects.spawnAnimated(plugin, p,
                "saint_water_drop", "animation.saint_water.splash",
                (target != null ? target.getLocation()
                        : p.getEyeLocation().add(p.getLocation().getDirection().multiply(5))).clone(),
                60, null);

        Location center = p.getLocation();
        plugin.getParticleEngine().play(
                new SphereAnimation(plugin, p, center, Particle.SPLASH, 25, 0.5, 5.0, 100));
        plugin.getParticleEngine().play(
                new SphereAnimation(plugin, p, center, Particle.BUBBLE, 20, 0.5, 4.5, 60));
        LocationUtil.sound(center, Sound.ENTITY_PLAYER_SPLASH, 1.5f, 1.0f);

        p.setFireTicks(0);
        for (PotionEffect eff : p.getActivePotionEffects()) {
            PotionEffectType type = eff.getType();
            if (type == PotionEffectType.POISON || type == PotionEffectType.WITHER
                    || type == PotionEffectType.WEAKNESS || type == PotionEffectType.SLOWNESS
                    || type == PotionEffectType.NAUSEA || type == PotionEffectType.BLINDNESS
                    || type == PotionEffectType.DARKNESS) {
                p.removePotionEffect(eff.getType());
            }
        }

        double dmg = 6.0 * plugin.getConfig().getDouble("schools.mushoku.damage-multiplier", 1.0);
        for (LivingEntity e : LocationUtil.nearbyLiving(center, 5.0, p.getUniqueId())) {
            e.damage(dmg, p);
            e.setFireTicks(0);
            LocationUtil.knockback(e, center, 1.0);
        }
    }
}
