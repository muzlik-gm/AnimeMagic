package com.anime.magic.schools.tensura;

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
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

/**
 * <b>Beelzebuth — Unique Skill: Lord of Gluttony</b>
 *
 * <p>Upgraded Gluttony — drains ALL nearby enemies simultaneously for 5 seconds.</p>
 *
 * <p>For 5 seconds, every hostile entity within 10 blocks takes 4 damage per second
 * (20 total). The caster heals for the total damage dealt. Dark tendrils extend from
 * each affected enemy to the caster.</p>
 */
public final class BeelzebuthSpell implements Spell {
    private final AnimeMagicPlugin plugin;

    public BeelzebuthSpell(AnimeMagicPlugin plugin) { this.plugin = plugin; }

    @Override public @NotNull String id() { return "tensura:beelzebuth"; }
    @Override public @NotNull String displayName() { return "§5§l§k||§r §5§lUnique: Beelzebuth §5§l§k||§r"; }
    @Override public @NotNull Spell.SchoolId school() { return Spell.SchoolId.TENSURA; }
    @Override public int manaCost() { return 110; }
    @Override public long cooldownMs() { return 30000; }
    @Override public int requiredLevel() { return 40; }
    @Override public @NotNull String description() {
        return "Drain ALL nearby enemies (10-block radius) for 5 seconds. 4 dmg/s each. You heal for total damage.";
    }
    @Override public @NotNull Spell.SpellIcon icon() {
        return new SpellIcon("BLACK_DYE", 4002, "§5Beelzebuth");
    }

    @Override
    public boolean cast(@NotNull Caster caster) {
        Player p = caster.player();
        // Spawn the 3D beelzebuth_maw model above the caster's head.
        com.anime.magic.util.SpellEffects.spawnAnimated(plugin, p,
                "beelzebuth_maw", "animation.beelzebuth.devour",
                p.getLocation().add(0, 2, 0).clone(), 300, null);

        new BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                if (!p.isOnline()) { cancel(); return; }
                if (ticks >= 100) { cancel(); return; }
                if (ticks % 20 == 0) {  // Once per second
                    double multiplier = plugin.getConfig().getDouble("schools.tensura.damage-multiplier", 1.0);
                    double totalHeal = 0;
                    for (LivingEntity e : LocationUtil.nearbyLiving(p.getLocation(), 10.0, p.getUniqueId())) {
                        e.damage(8.0 * multiplier, p);
                        totalHeal += 8.0 * multiplier;
                        // Tendrils from enemy to caster
                        Location from = e.getEyeLocation();
                        Location to = p.getEyeLocation();
                        if (from.getWorld() != null) {
                            for (double d = 0; d <= 1.0; d += 0.5) {
                                Location loc = from.clone().add(to.toVector().subtract(from.toVector()).multiply(d));
                                from.getWorld().spawnParticle(Particle.SQUID_INK, loc, 1, 0.1, 0.1, 0.1, 0);
                                from.getWorld().spawnParticle(Particle.WITCH, loc, 1, 0.1, 0.1, 0.1, 0);
                            }
                        }
                    }
                    if (totalHeal > 0) {
                        p.setHealth(Math.min(p.getMaxHealth(), p.getHealth() + totalHeal * 0.5));
                        // Heal particles around caster
                        if (p.getWorld() != null) {
                            p.getWorld().spawnParticle(Particle.HEART, p.getLocation().add(0, 2.0, 0), 5, 0.5, 0.5, 0.5, 0);
                        }
                    }
                    LocationUtil.sound(p.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.8f, 0.6f);
                }
                // Continuous aura around caster — spawn ABOVE the head (was at
                // chest level, blinding the caster's first-person view).
                if (ticks % 8 == 0 && p.getWorld() != null) {
                    p.getWorld().spawnParticle(Particle.DRAGON_BREATH, p.getLocation().add(0, 2.5, 0), 3, 1.5, 1.5, 1.5, 0.02);
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        // Initial burst
        plugin.getParticleEngine().play(new SphereAnimation(plugin, p, p.getLocation(),
                Particle.DRAGON_BREATH, 15, 1.0, 10.0, 80));
        LocationUtil.sound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.5f, 0.5f);
        return true;
    }
}
