package com.anime.magic.schools.onepiece;

import com.anime.magic.AnimeMagicPlugin;
import com.anime.magic.api.Caster;
import com.anime.magic.api.Spell;
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
 * <b>Gear Second — Nikyu Nikyu no Mi Acceleration</b>
 *
 * <p>Luffy pumps blood through his body at high speed, gaining massive attack and
 * movement speed for 15 seconds. Skin turns red (via particle aura).</p>
 */
public final class GearSecondSpell implements Spell {
    private final AnimeMagicPlugin plugin;

    public GearSecondSpell(AnimeMagicPlugin plugin) { this.plugin = plugin; }

    @Override public @NotNull String id() { return "onepiece:gear_second"; }
    @Override public @NotNull String displayName() { return "§c§lGear §8» §f§lSecond"; }
    @Override public @NotNull SchoolId school() { return SchoolId.ONEPIECE; }
    @Override public int manaCost() { return 80; }
    @Override public long cooldownMs() { return 30000; }
    @Override public int requiredLevel() { return 30; }
    @Override public @NotNull String description() {
        return "Pump blood at high speed for 15s. Strength II, Speed III, Haste III. Steam aura.";
    }
    @Override public @NotNull SpellIcon icon() { return new SpellIcon("RED_DYE", 6003, "§cGear Second"); }

    @Override
    public boolean cast(@NotNull Caster caster) {
        Player p = caster.player();
        plugin.getParticleEngine().play(new SpiralAnimation(plugin, p, Particle.CLOUD,
                300, 0.4, 1.2, 0.3, 6, 0.6));
        LocationUtil.sound(p.getLocation(), Sound.ENTITY_PLAYER_BREATH, 1.5f, 1.8f);
        LocationUtil.sound(p.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 1.0f, 1.5f);

        new BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                if (ticks >= 300) {
                    LocationUtil.sound(p.getLocation(), Sound.ENTITY_PLAYER_BREATH, 1.0f, 0.8f);
                    cancel();
                    return;
                }
                // Re-apply buffs every 60 ticks
                if (ticks % 60 == 0) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 80, 1));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 80, 2));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 80, 2));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 80, 1));
                }
                // Steam aura
                if (ticks % 3 == 0 && p.getWorld() != null) {
                    Location feet = p.getLocation();
                    p.getWorld().spawnParticle(Particle.CLOUD, feet, 1, 0.3, 0.1, 0.3, 0.1);
                    p.getWorld().spawnParticle(Particle.DUST, feet, 1, 0.3, 0.8, 0.3, 0, new org.bukkit.Particle.DustOptions(org.bukkit.Color.fromRGB(255, 110, 110), 1.0f));
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        return true;
    }
}
