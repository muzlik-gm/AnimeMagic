package com.anime.magic.schools.mushoku;

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
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;
import java.util.List;

/**
 * <b>King-class Fire Atomic Flare</b>
 *
 * <p>The strongest fire spell a miniature sun that detonates with nuclear force.</p>
 *
 * <ol>
 *   <li><b>Incantation (4 lines):</b> Player must type the chant.</li>
 *   <li><b>Charge (1s):</b> A growing fire sphere appears above the player.</li>
 *   <li><b>Detonate:</b> The sphere erupts into a 12-block sphere of FLAME + LAVA +
 *       FLASH + EXPLOSION particles. 50 damage to all entities within 12 blocks,
 *       8 seconds of fire, knockback 3.0. Leaves a smoke cloud for 5s.</li>
 * </ol>
 */
public final class AtomicFlareSpell implements Spell {
    private final AnimeMagicPlugin plugin;

    public AtomicFlareSpell(AnimeMagicPlugin plugin) { this.plugin = plugin; }

    @Override public @NotNull String id() { return "mushoku:atomic_flare"; }
    @Override public @NotNull String displayName() { return "§4§l§k||§r §4§lKing-class: Atomic Flare §4§l§k||§r"; }
    @Override public @NotNull SchoolId school() { return SchoolId.MUSHOKU; }
    @Override public int manaCost() { return 140; }
    @Override public long cooldownMs() { return 45000; }
    @Override public int requiredLevel() { return 45; }
    @Override public @NotNull String description() {
        return "Detonate a miniature sun. 50 damage + 8s fire + knockback to all within 12 blocks.";
    }
    @Override public @NotNull SpellIcon icon() { return new SpellIcon("BLAZE_POWDER", 5002, "§4Atomic Flare"); }

    @Override public @NotNull java.util.List<String> incantation() {
        return List.of(
                "Sun, source of all heat",
                "Lend me thy burning wrath",
                "Consume my foes in fire",
                "King-class Atomic Flare."
        );
    }

    @Override
    public boolean cast(@NotNull Caster caster) {
        Player p = caster.player();
        // Skip incantation for now if system not loaded just fire
        // (Real incantation handled by MushokuSchool when it detects this spell)
        chargeAndDetonate(p);
        return true;
    }

    private void chargeAndDetonate(Player p) {
        Location center = p.getLocation().add(0, 3, 0);
        // Charge phase: growing sphere
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (t >= 20) { cancel(); detonate(p, center); return; }
                if (center.getWorld() == null) { cancel(); return; }
                double r = 0.3 + t * 0.06;
                for (int i = 0; i < 30; i++) {
                    double phi = Math.acos(1 - 2 * (i + 0.5) / 30);
                    double theta = Math.PI * (1 + Math.sqrt(5)) * i + t * 0.3;
                    double x = r * Math.cos(theta) * Math.sin(phi);
                    double y = r * Math.sin(theta) * Math.sin(phi);
                    double z = r * Math.cos(phi);
                    center.getWorld().spawnParticle(Particle.FLAME, center.clone().add(x, y, z), 0, 0, 0, 0, 0.02);
                }
                if (t % 4 == 0) LocationUtil.sound(center, Sound.BLOCK_FIRE_AMBIENT, 0.5f + t * 0.05f, 0.6f + t * 0.04f);
                t++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void detonate(Player p, Location center) {
        if (center.getWorld() == null) return;
        // 4-layer explosion
        plugin.getParticleEngine().play(new SphereAnimation(plugin, p, center, Particle.FLAME, 30, 1.0, 12.0, 150));
        plugin.getParticleEngine().play(new SphereAnimation(plugin, p, center, Particle.LAVA, 25, 0.8, 10.0, 100));
        plugin.getParticleEngine().play(new SphereAnimation(plugin, p, center, Particle.FLASH, 8, 0.3, 5.0, 30));
        plugin.getParticleEngine().play(new SphereAnimation(plugin, p, center, Particle.EXPLOSION, 5, 0.2, 3.0, 20));
        plugin.getParticleEngine().play(new RingBurst(plugin, p, center, Particle.LARGE_SMOKE, 25, 12.0, 100));
        LocationUtil.sound(center, Sound.ENTITY_GENERIC_EXPLODE, 3.0f, 0.4f);
        LocationUtil.sound(center, Sound.ENTITY_DRAGON_FIREBALL_EXPLODE, 2.5f, 0.5f);
        LocationUtil.sound(center, Sound.ENTITY_WITHER_DEATH, 1.5f, 0.6f);

        double dmg = 50.0 * plugin.getConfig().getDouble("schools.mushoku.damage-multiplier", 1.0);
        for (LivingEntity e : LocationUtil.nearbyLiving(center, 12.0, p.getUniqueId())) {
            e.damage(dmg, p);
            e.setFireTicks(160);
            LocationUtil.knockback(e, center, 3.0);
        }
    }
}
