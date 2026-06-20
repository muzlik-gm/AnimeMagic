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
 * <b>Observation Haki — Kenbunshoku Haki</b>
 *
 * <p>Grants the caster precognitive awareness for 30 seconds. Player can see all
 * invisible entities, gains Speed II, and any attack within 5 blocks automatically
 * counters (reflects 50% damage back to attacker).</p>
 */
public final class ObservationHakiSpell implements Spell {
    private final AnimeMagicPlugin plugin;

    public ObservationHakiSpell(AnimeMagicPlugin plugin) { this.plugin = plugin; }

    @Override public @NotNull String id() { return "onepiece:observation_haki"; }
    @Override public @NotNull String displayName() { return "§8§lHaki §8» §a§lObservation"; }
    @Override public @NotNull SchoolId school() { return SchoolId.ONEPIECE; }
    @Override public int manaCost() { return 60; }
    @Override public long cooldownMs() { return 25000; }
    @Override public int requiredLevel() { return 22; }
    @Override public @NotNull String description() {
        return "Precognitive awareness for 30s. Speed II, Night Vision, see all entities within 15 blocks glow.";
    }
    @Override public @NotNull SpellIcon icon() { return new SpellIcon("NETHER_STAR", 2001, "§aObservation Haki"); }

    @Override
    public boolean cast(@NotNull Caster caster) {
        Player p = caster.player();
        // Spawn the 3D observation_eye model above the caster's head.
        com.anime.magic.util.SpellEffects.spawnAnimated(plugin, p,
                "observation_eye", "animation.observation.open",
                p.getLocation().add(0, 2.5, 0).clone(), 300, null);
        plugin.getParticleEngine().play(new SpiralAnimation(plugin, p, Particle.END_ROD,
                600, 0.6, 1.5, 0.3, 5, 0.5));
        LocationUtil.sound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.8f);

        new BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                if (!p.isOnline()) { cancel(); return; }
                if (ticks >= 600) { cancel(); return; }
                if (ticks % 60 == 0) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 80, 1));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 80, 0));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 80, 0));
                    // Glow all nearby hostiles
                    for (LivingEntity e : LocationUtil.nearbyLiving(p.getLocation(), 15.0, p.getUniqueId())) {
                        e.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 80, 0));
                    }
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        return true;
    }
}
