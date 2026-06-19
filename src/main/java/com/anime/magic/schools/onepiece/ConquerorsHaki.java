package com.anime.magic.schools.onepiece;

import com.anime.magic.AnimeMagicPlugin;
import com.anime.magic.api.Caster;
import com.anime.magic.api.Spell;
import com.anime.magic.effects.RingBurst;
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

/**
 * Conqueror's Haki (Haoshoku Haki) — Willpower manifestation. An expanding sphere of
 * dark-purple particles emanates from the caster. All hostile mobs within 15 blocks
 * are paralyzed (Slowness V + Weakness III for 5 seconds), and players within 8 blocks
 * are blinded for 2 seconds. Non-hostile entities (villagers, animals) are unaffected.
 */
public final class ConquerorsHaki implements Spell {
    private final AnimeMagicPlugin plugin;

    public ConquerorsHaki(AnimeMagicPlugin plugin) { this.plugin = plugin; }

    @Override public @NotNull String id() { return "onepiece:conquerors_haki"; }
    @Override public @NotNull String displayName() { return "§8Haki §8» §5Conqueror's Will"; }
    @Override public @NotNull SchoolId school() { return SchoolId.ONEPIECE; }
    @Override public int manaCost() { return 80; }
    @Override public long cooldownMs() { return 20000; }
    @Override public int requiredLevel() { return 30; }
    @Override public @NotNull String description() {
        return "Unleash your willpower. Hostiles are paralyzed; nearby players are blinded.";
    }
    @Override public @NotNull SpellIcon icon() {
        return new SpellIcon("PURPLE_GLAZED_TERRACOTTA", 6001, "§5Conqueror's Haki");
    }

    @Override
    public boolean cast(@NotNull Caster caster) {
        Player p = caster.player();
        Location center = p.getLocation();

        plugin.getParticleEngine().play(
                new SphereAnimation(plugin, p, center, Particle.DRAGON_BREATH, 30, 0.5, 15.0, 120));
        plugin.getParticleEngine().play(
                new RingBurst(plugin, p, center, Particle.SQUID_INK, 20, 12.0, 64));

        LocationUtil.sound(center, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.5f, 0.5f);
        LocationUtil.sound(center, Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.7f);

        for (LivingEntity e : LocationUtil.nearbyLiving(center, 15.0, p.getUniqueId())) {
            if (e instanceof Player other && other.getLocation().distanceSquared(center) <= 64) {
                other.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 0));
                other.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 2));
            } else if (!(e instanceof Player)) {
                e.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 4));
                e.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 100, 2));
                e.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 100, 0));
            }
        }

        p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 40, 2));
        return true;
    }
}
