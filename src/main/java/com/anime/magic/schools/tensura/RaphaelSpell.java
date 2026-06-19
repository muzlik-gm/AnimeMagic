package com.anime.magic.schools.tensura;

import com.anime.magic.AnimeMagicPlugin;
import com.anime.magic.api.Caster;
import com.anime.magic.api.Spell;
import com.anime.magic.effects.HelixEffect;
import com.anime.magic.effects.SpiralAnimation;
import com.anime.magic.util.LocationUtil;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

/**
 * <b>Raphael — Ultimate Skill: Wisdom Lord</b>
 *
 * <p>Smart-cast buff: analyzes the battlefield and gives the caster perfect combat awareness.</p>
 *
 * <p>For 20 seconds, the caster gains:
 * <ul>
 *   <li>Strength IV (perfect offense)</li>
 *   <li>Resistance III (perfect defense)</li>
 *   <li>Speed III (perfect mobility)</li>
 *   <li>Haste III (perfect attack speed)</li>
 *   <li>Night Vision (perfect awareness)</li>
 *   <li>Regeneration II (perfect recovery)</li>
 *   <li>Mana cost reduced by 50% (via instant mana refill every 3s)</li>
 * </ul></p>
 *
 * <p>Visual: Blue + cyan helixes spiral around the player. A wisdom orb hovers above.</p>
 */
public final class RaphaelSpell implements Spell {
    private final AnimeMagicPlugin plugin;

    public RaphaelSpell(AnimeMagicPlugin plugin) { this.plugin = plugin; }

    @Override public @NotNull String id() { return "tensura:raphael"; }
    @Override public @NotNull String displayName() { return "§b§l§k||§r §b§lUltimate: Raphael §b§l§k||§r"; }
    @Override public @NotNull Spell.SchoolId school() { return Spell.SchoolId.TENSURA; }
    @Override public int manaCost() { return 130; }
    @Override public long cooldownMs() { return 90000; }
    @Override public int requiredLevel() { return 50; }
    @Override public @NotNull String description() {
        return "Perfect combat awareness for 20s. Strength IV, Resistance III, Speed III, Haste III, Regen II, Night Vision, +50% mana regen.";
    }
    @Override public @NotNull Spell.SpellIcon icon() {
        return new SpellIcon("NETHER_STAR", 2003, "§bRaphael");
    }

    @Override
    public boolean cast(@NotNull Caster caster) {
        Player p = caster.player();
        // Initial aura
        plugin.getParticleEngine().play(new HelixEffect(plugin, p, Particle.END_ROD, 40, 1.5, 0.5, 12, 0.5));
        plugin.getParticleEngine().play(new SpiralAnimation(plugin, p, Particle.SONIC_BOOM,
                400, 0.8, 1.5, 0.3, 6, 0.5));
        LocationUtil.sound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 2.0f, 1.5f);
        LocationUtil.sound(p.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1.0f, 1.8f);

        new BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                if (ticks >= 400) {
                    LocationUtil.sound(p.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 1.5f);
                    cancel();
                    return;
                }
                // Re-apply buffs every 60 ticks (3s)
                if (ticks % 60 == 0) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 80, 3));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 80, 2));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 80, 2));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 80, 2));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 80, 0));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 80, 1));
                    // Mana refill
                    int cur = plugin.getManaManager().current(p.getUniqueId());
                    int max = plugin.getManaManager().max(p.getUniqueId());
                    plugin.getManaManager().set(p.getUniqueId(), Math.min(max, cur + 30));
                }
                // Continuous orbiting wisdom orb
                if (ticks % 4 == 0 && p.getWorld() != null) {
                    double angle = ticks * 0.1;
                    Location orb = p.getLocation().add(0, 2.5, 0).add(Math.cos(angle) * 0.8, 0, Math.sin(angle) * 0.8);
                    p.getWorld().spawnParticle(Particle.END_ROD, orb, 1, 0, 0, 0, 0);
                    p.getWorld().spawnParticle(Particle.SONIC_BOOM, orb, 1, 0.05, 0.05, 0.05, 0);
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        return true;
    }
}
