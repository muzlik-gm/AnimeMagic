package com.anime.magic.schools.mushoku;

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
import java.util.List;

/**
 * <b>Saint-class Wind Storm</b>
 *
 * <p>Summons a swirling storm around the caster for 8 seconds that damages and
 * slows all nearby enemies. Wind particles spiral outward continuously.</p>
 */
public final class StormSpell implements Spell {
    private final AnimeMagicPlugin plugin;

    public StormSpell(AnimeMagicPlugin plugin) { this.plugin = plugin; }

    @Override public @NotNull String id() { return "mushoku:storm"; }
    @Override public @NotNull String displayName() { return "§3§lSaint §8» §f§lStorm"; }
    @Override public @NotNull SchoolId school() { return SchoolId.MUSHOKU; }
    @Override public int manaCost() { return 70; }
    @Override public long cooldownMs() { return 20000; }
    @Override public int requiredLevel() { return 25; }
    @Override public @NotNull String description() {
        return "Summon a swirling storm around you for 8s. 3 dmg/s + Slowness II to all enemies within 12 blocks.";
    }
    @Override public @NotNull SpellIcon icon() { return new SpellIcon("SNOWBALL", 3003, "§fStorm"); }

    @Override public @NotNull List<String> incantation() {
        return List.of(
                "Winds, heed my call",
                "Swirl and consume my foes",
                "Saint-class Storm."
        );
    }

    @Override
    public boolean cast(@NotNull Caster caster) {
        Player p = caster.player();
        // Spawn the 3D storm_vortex model 8 blocks ahead of the caster.
        com.anime.magic.util.SpellEffects.spawnAnimated(plugin, p,
                "storm_vortex", "animation.storm.swirl",
                p.getLocation().add(p.getLocation().getDirection().multiply(8)).clone(), 60, null);
        plugin.getParticleEngine().play(new SpiralAnimation(plugin, p, Particle.CLOUD,
                160, 0.5, 3.0, 0.4, 10, 0.6));
        plugin.getParticleEngine().play(new SpiralAnimation(plugin, p, Particle.SWEEP_ATTACK,
                160, 0.3, 2.5, 0.3, 8, 0.4));
        LocationUtil.sound(p.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.5f, 0.8f);

        new BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                if (!p.isOnline()) { cancel(); return; }
                if (ticks >= 160) { cancel(); return; }
                if (ticks % 20 == 0) {  // every second
                    double dmg = 5.0 * plugin.getConfig().getDouble("schools.mushoku.damage-multiplier", 1.0);
                    for (LivingEntity e : LocationUtil.nearbyLiving(p.getLocation(), 12.0, p.getUniqueId())) {
                        e.damage(dmg, p);
                        e.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 1));
                    }
                    LocationUtil.sound(p.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.5f, 1.5f);
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        return true;
    }
}
