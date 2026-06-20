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
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import java.util.List;

/**
 * <b>King-class Gravity</b>
 *
 * <p>Creates a gravitational singularity at the target location that pulls all nearby
 * entities inward, then explodes them outward. 4-second duration.</p>
 */
public final class GravitySpell implements Spell {
    private final AnimeMagicPlugin plugin;

    public GravitySpell(AnimeMagicPlugin plugin) { this.plugin = plugin; }

    @Override public @NotNull String id() { return "mushoku:gravity"; }
    @Override public @NotNull String displayName() { return "§5§l§k||§r §5§lKing-class: Gravity §5§l§k||§r"; }
    @Override public @NotNull SchoolId school() { return SchoolId.MUSHOKU; }
    @Override public int manaCost() { return 120; }
    @Override public long cooldownMs() { return 30000; }
    @Override public int requiredLevel() { return 45; }
    @Override public @NotNull String description() {
        return "Create a gravitational singularity that pulls all enemies in, then explodes them outward for 40 damage.";
    }
    @Override public @NotNull SpellIcon icon() { return new SpellIcon("BLACK_DYE", 4002, "§5Gravity"); }

    @Override public @NotNull List<String> incantation() {
        return List.of(
                "Force that binds all things",
                "Pull my enemies to the abyss",
                "King-class Gravity."
        );
    }

    @Override
    public boolean cast(@NotNull Caster caster) {
        Player p = caster.player();
        LivingEntity target = caster.targetEntity(30);
        Location center = target != null ? target.getLocation() : p.getEyeLocation().add(p.getLocation().getDirection().multiply(20));

        // Phase 1: Pull (3 seconds)
        new BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                if (!p.isOnline()) { cancel(); return; }
                if (ticks >= 60) { explode(p, center); cancel(); return; }
                if (center.getWorld() == null) { cancel(); return; }
                // Inward spiraling particles
                for (int i = 0; i < 8; i++) {
                    double angle = (ticks * 0.3) + (i * Math.PI / 4);
                    double r = 5.0 - (ticks / 60.0) * 4.5;
                    Location from = center.clone().add(Math.cos(angle) * r, Math.sin(ticks * 0.2) * 1.0, Math.sin(angle) * r);
                    center.getWorld().spawnParticle(Particle.DRAGON_BREATH, from, 0,
                            -Math.cos(angle) * 0.3, -0.1, -Math.sin(angle) * 0.3, 0.0);
                    center.getWorld().spawnParticle(Particle.SQUID_INK, from, 0,
                            -Math.cos(angle) * 0.2, 0, -Math.sin(angle) * 0.2, 0.0);
                }
                // Pull entities inward
                for (LivingEntity e : LocationUtil.nearbyLiving(center, 10.0, p.getUniqueId())) {
                    Vector pull = center.toVector().subtract(e.getLocation().toVector()).normalize().multiply(0.4);
                    e.setVelocity(e.getVelocity().add(pull));
                    e.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 5, 3));
                }
                if (ticks % 10 == 0) LocationUtil.sound(center, Sound.ENTITY_WITHER_AMBIENT, 0.6f, 0.5f);
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        return true;
    }

    private void explode(Player p, Location center) {
        if (center.getWorld() == null) return;
        plugin.getParticleEngine().play(new SphereAnimation(plugin, p, center, Particle.DRAGON_BREATH, 25, 0.5, 8.0, 100));
        plugin.getParticleEngine().play(new SphereAnimation(plugin, p, center, Particle.SQUID_INK, 20, 0.3, 6.0, 60));
        LocationUtil.sound(center, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.5f);
        LocationUtil.sound(center, Sound.ENTITY_WITHER_DEATH, 1.5f, 0.6f);

        double dmg = 40.0 * plugin.getConfig().getDouble("schools.mushoku.damage-multiplier", 1.0);
        for (LivingEntity e : LocationUtil.nearbyLiving(center, 8.0, p.getUniqueId())) {
            e.damage(dmg, p);
            // Knock outward
            Vector push = e.getLocation().toVector().subtract(center.toVector()).normalize().multiply(2.0).setY(0.8);
            e.setVelocity(push);
        }
    }
}
