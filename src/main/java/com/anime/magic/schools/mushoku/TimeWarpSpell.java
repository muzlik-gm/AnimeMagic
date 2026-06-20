package com.anime.magic.schools.mushoku;

import com.anime.magic.AnimeMagicPlugin;
import com.anime.magic.api.Caster;
import com.anime.magic.api.Spell;
import com.anime.magic.effects.HelixEffect;
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
import java.util.List;

/**
 * <b>God-class Time Warp</b>
 *
 * <p>Bends time around the target. The caster moves at normal speed while the target
 * is slowed to a crawl. After 4 seconds, the target takes 30 "accumulated" damage
 * as time snaps back.</p>
 */
public final class TimeWarpSpell implements Spell {
    private final AnimeMagicPlugin plugin;

    public TimeWarpSpell(AnimeMagicPlugin plugin) { this.plugin = plugin; }

    @Override public @NotNull String id() { return "mushoku:time_warp"; }
    @Override public @NotNull String displayName() { return "§d§lGod-class: Time Warp"; }
    @Override public @NotNull SchoolId school() { return SchoolId.MUSHOKU; }
    @Override public int manaCost() { return 180; }
    @Override public long cooldownMs() { return 60000; }
    @Override public int requiredLevel() { return 60; }
    @Override public @NotNull String description() {
        return "Bend time around your target for 4s. Target is paralyzed. After 4s, target takes 30 accumulated damage.";
    }
    @Override public @NotNull SpellIcon icon() { return new SpellIcon("CLOCK", 0, "§dTime Warp"); }

    @Override public @NotNull List<String> incantation() {
        return List.of(
                "Time, the river that flows",
                "Halt your course for me",
                "Bind my foe in stillness",
                "God-class Time Warp."
        );
    }

    @Override
    public boolean cast(@NotNull Caster caster) {
        Player p = caster.player();
        LivingEntity target = caster.targetEntity(30);
        if (target == null) return false;
        Location targetLoc = target.getLocation();

        // Spawn the 3D time_warp_clock model 2 blocks above the target.
        com.anime.magic.util.SpellEffects.spawnAnimated(plugin, p,
                "time_warp_clock", "animation.time_warp.tick",
                target.getLocation().add(0, 2, 0).clone(), 60, null);

        // Phase 1: Bind in stillness (4 seconds)
        plugin.getParticleEngine().play(new HelixEffect(plugin, p, Particle.END_ROD, 80, 1.2, 0.4, 8, 0.4));
        LocationUtil.sound(targetLoc, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 0.5f);

        new BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                if (!p.isOnline()) { cancel(); return; }
                if (ticks >= 80) {
                    // Phase 2: Time snaps back, accumulated damage
                    plugin.getParticleEngine().play(new SphereAnimation(plugin, p, target.getLocation(),
                            Particle.END_ROD, 20, 0.5, 4.0, 60));
                    plugin.getParticleEngine().play(new SphereAnimation(plugin, p, target.getLocation(),
                            Particle.FLASH, 5, 0.2, 2.0, 15));
                    LocationUtil.sound(target.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 2.0f, 0.6f);
                    double dmg = 30.0 * plugin.getConfig().getDouble("schools.mushoku.damage-multiplier", 1.0);
                    if (!target.isDead()) target.damage(dmg, p);
                    cancel();
                    return;
                }
                if (target.isDead()) { cancel(); return; }
                // Slow to a crawl + nausea
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 5, 100));
                target.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 5, 5));
                target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 5, 5));
                // Clock particles around target — spawn ABOVE the head (was at eye
                // level, blinding the targeted player with white particles in their
                // face). Now at +2.0 Y so the victim can still see.
                if (ticks % 4 == 0 && target.getWorld() != null) {
                    double angle = ticks * 0.15;
                    for (int ring = 0; ring < 3; ring++) {
                        double r = 1.0 + ring * 0.3;
                        Location orb = target.getEyeLocation().add(Math.cos(angle + ring) * r, 2.0, Math.sin(angle + ring) * r);
                        try { target.getWorld().spawnParticle(Particle.END_ROD, orb, 1, 0, 0, 0, 0); } catch (Throwable ignored) {}
                    }
                }
                if (ticks % 20 == 0) LocationUtil.sound(target.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.4f, 0.5f);
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        return true;
    }
}
