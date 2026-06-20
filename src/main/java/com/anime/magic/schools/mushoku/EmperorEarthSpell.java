package com.anime.magic.schools.mushoku;

import com.anime.magic.AnimeMagicPlugin;
import com.anime.magic.api.Caster;
import com.anime.magic.api.Spell;
import com.anime.magic.effects.RingBurst;
import com.anime.magic.effects.SphereAnimation;
import com.anime.magic.util.LocationUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import java.util.List;

/**
 * Emperor-class Earth Earthquake Surge. Five-line incantation (longer = stronger).
 * Causes a ring of cracked blocks to expand outward, launches falling-block debris
 * into the air, and damages all entities in a 10-block radius. Affected entities are
 * launched upward.
 */
public final class EmperorEarthSpell implements Spell {
    private final AnimeMagicPlugin plugin;
    private final IncantationSystem incantation;

    public EmperorEarthSpell(AnimeMagicPlugin plugin, IncantationSystem incantation) {
        this.plugin = plugin;
        this.incantation = incantation;
    }

    @Override public @NotNull String id() { return "mushoku:emperor_earth"; }
    @Override public @NotNull String displayName() { return "§4Emperor §8» §6Earthquake"; }
    @Override public @NotNull SchoolId school() { return SchoolId.MUSHOKU; }
    @Override public int manaCost() { return 90; }
    @Override public long cooldownMs() { return 25000; }
    @Override public int requiredLevel() { return 30; }
    @Override public @NotNull String description() {
        return "Emperor-class spell. Cracks the earth in a 10-block radius and launches all foes skyward.";
    }
    @Override public @NotNull SpellIcon icon() { return new SpellIcon("STONE", 5003, "§6Emperor Earth"); }
    @Override public @NotNull List<String> incantation() {
        return List.of(
                "Earth, foundation of all",
                "Tremble at my command",
                "Shatter the ground beneath",
                "Rise, stones of the deep",
                "Emperor-class Earthquake Surge."
        );
    }

    @Override
    public boolean cast(@NotNull Caster caster) {
        Player p = caster.player();
        if (incantation == null) { fire(p); return true; }
        return incantation.begin(p, this, () -> fire(p));
    }

    private void fire(Player p) {
        // Spawn the 3D emperor_earth_spike model 3 blocks ahead of the caster.
        com.anime.magic.util.SpellEffects.spawnAnimated(plugin, p,
                "emperor_earth_spike", "animation.emperor_earth.rise",
                p.getEyeLocation().add(p.getLocation().getDirection().multiply(1.5)).clone(), 60, null);
        Location center = p.getLocation();
        plugin.getParticleEngine().play(
                new RingBurst(plugin, p, center, Particle.DUST, 30, 10.0, 96));

        for (int i = 0; i < 3; i++) {
            final double r = 2.0 + i * 2.5;
            new BukkitRunnable() {
                @Override public void run() {
                    if (!p.isOnline()) { cancel(); return; }
                    plugin.getParticleEngine().play(
                            new SphereAnimation(plugin, p, center, Particle.DUST, 10, 0.5, r, 40));
                }
            }.runTaskLater(plugin, i * 5L);
        }

        LocationUtil.sound(center, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.2f, 0.7f);
        LocationUtil.sound(center, Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 1.0f, 0.6f);

        if (center.getWorld() != null) {
            for (int i = 0; i < 12; i++) {
                double angle = i * Math.PI * 2 / 12;
                double r = 1.5 + Math.random() * 3.0;
                Location at = center.clone().add(Math.cos(angle) * r, 0.5, Math.sin(angle) * r);
                // Use the modern BlockData overload (the deprecated Material+byte
                // variant mismaps block subtypes). setCancelDrop(true) prevents the
                // falling block from converting to a permanent STONE block on landing
                // (was a griefing vector — 12 permanent blocks per cast).
                FallingBlock fb = center.getWorld().spawnFallingBlock(at, Material.STONE.createBlockData());
                fb.setVelocity(new Vector(Math.cos(angle) * 0.6, 1.0 + Math.random() * 0.5, Math.sin(angle) * 0.6));
                fb.setDropItem(false);
                fb.setHurtEntities(true);
                try { fb.setCancelDrop(true); } catch (NoSuchMethodError ignored) {}
                // Defensive cleanup in case setCancelDrop isn't supported.
                new org.bukkit.scheduler.BukkitRunnable() {
                    @Override public void run() { if (fb.isValid()) fb.remove(); }
                }.runTaskLater(plugin, 80L);
            }
        }

        double dmg = 20.0 * plugin.getConfig().getDouble("schools.mushoku.damage-multiplier", 1.0);
        for (LivingEntity e : LocationUtil.nearbyLiving(center, 10.0, p.getUniqueId())) {
            e.damage(dmg, p);
            e.setVelocity(new Vector(0, 1.2, 0));
        }

        new BukkitRunnable() {
            int r = 1;
            @Override public void run() {
                if (!p.isOnline()) { cancel(); return; }
                if (r > 8) { cancel(); return; }
                if (center.getWorld() == null) return;
                for (int i = 0; i < 3; i++) {
                    double angle = i * Math.PI * 2 / 3;
                    Location at = center.clone().add(Math.cos(angle) * r, -0.5, Math.sin(angle) * r);
                    Block b = at.getBlock();
                    if (b.getType().isSolid()) {
                        try { center.getWorld().spawnParticle(Particle.DUST, at, 1, 0.3, 0.3, 0.3, 0.0, new org.bukkit.Particle.DustOptions(org.bukkit.Color.fromRGB(130, 100, 70), 1.0f)); } catch (Throwable ignored) {}
                    }
                }
                r++;
            }
        }.runTaskTimer(plugin, 5L, 10L);
    }
}
