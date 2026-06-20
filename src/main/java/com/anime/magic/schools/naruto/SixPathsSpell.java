package com.anime.magic.schools.naruto;

import com.anime.magic.AnimeMagicPlugin;
import com.anime.magic.api.Caster;
import com.anime.magic.api.Spell;
import com.anime.magic.effects.HelixEffect;
import com.anime.magic.effects.RingBurst;
import com.anime.magic.effects.SphereAnimation;
import com.anime.magic.models.ModelDisplay;
import com.anime.magic.util.LocationUtil;
import com.anime.magic.util.SpellEffects;
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
 * <b>Six Paths Sage Mode — Rikudō Sennin Mōdo</b>
 *
 * <p>Naruto's strongest form. The caster ascends into the air, gains flight,
 * massive stat boosts, and damages all nearby enemies with truth-seeking orbs.</p>
 *
 * <ol>
 *   <li><b>Ascend (60 ticks / 3s):</b> Player levitates 5 blocks up. 3D sage_aura
 *       model grows to 3x scale playing sixpaths.ascend. White + gold helixes spiral
 *       around them.</li>
 *   <li><b>Active (400 ticks / 20s):</b> Player gets Flight, Strength V, Resistance III,
 *       Speed III, Regeneration II, Fire Resistance, Night Vision. Truth-seeking orbs
 *       (END_ROD particles) orbit the player. Every 3 seconds, an expanding sphere of
 *       damage pulses outward, dealing 15 damage to all enemies within 10 blocks.</li>
 *   <li><b>End:</b> Player descends. Final shockwave ring.</li>
 * </ol>
 */
public final class SixPathsSpell implements Spell {
    private final AnimeMagicPlugin plugin;

    public SixPathsSpell(AnimeMagicPlugin plugin) { this.plugin = plugin; }

    @Override public @NotNull String id() { return "naruto:six_paths"; }
    @Override public @NotNull String displayName() { return "§f§lSix Paths Sage Mode"; }
    @Override public @NotNull SchoolId school() { return SchoolId.NARUTO; }
    @Override public int manaCost() { return 200; }
    @Override public long cooldownMs() { return 120000; }
    @Override public int requiredLevel() { return 60; }
    @Override public @NotNull String description() {
        return "Ultimate transformation. Ascend, gain flight + massive buffs for 20s. Pulses 15 damage to all nearby enemies every 3s.";
    }
    @Override public @NotNull SpellIcon icon() {
        return new SpellIcon("NETHER_STAR", 7010, "§fSix Paths Sage Mode");
    }

    @Override
    public boolean cast(@NotNull Caster caster) {
        Player p = caster.player();
        Location startLoc = p.getLocation();

        // Cache pre-cast flight state so we don't corrupt creative/spectator
        // flight permission when the spell ends.
        final boolean wasCreative = p.getGameMode() == org.bukkit.GameMode.CREATIVE
                || p.getGameMode() == org.bukkit.GameMode.SPECTATOR;
        final boolean hadAllowFlight = p.getAllowFlight();

        // Phase 1: Ascend
        if (!wasCreative) {
            p.setAllowFlight(true);
            p.setFlying(true);
        }
        ModelDisplay aura = SpellEffects.spawnAnimated(plugin, p, "sage_aura", "animation.sixpaths.ascend", p.getEyeLocation().add(p.getLocation().getDirection().multiply(1.5)), 460, null);
        if (aura != null) aura.followPlayer(p.getUniqueId(), new org.bukkit.util.Vector(0, 0, 0));

        plugin.getParticleEngine().play(new HelixEffect(plugin, p, Particle.END_ROD, 60, 1.5, 0.6, 10, 0.4));
        plugin.getParticleEngine().play(new HelixEffect(plugin, p, Particle.CRIT, 60, 1.2, 0.5, 8, 0.5));

        LocationUtil.sound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 2.0f, 0.5f);
        LocationUtil.sound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 2.0f, 0.8f);
        LocationUtil.sound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.5f, 0.6f);

        // Phase 2: Active — buffs + periodic damage pulses + truth orbs
        new BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                if (!p.isOnline()) { cancel(); return; }
                if (ticks >= 400) {
                    // End: descend. Only revoke flight for non-creative players
                    // and restore the original allowFlight state.
                    if (!wasCreative) {
                        p.setFlying(false);
                        p.setAllowFlight(hadAllowFlight);
                    }
                    if (aura != null) aura.remove();
                    plugin.getParticleEngine().play(new RingBurst(plugin, p, p.getLocation(), Particle.END_ROD, 25, 8.0, 80));
                    LocationUtil.sound(p.getLocation(), Sound.ENTITY_WITHER_DEATH, 1.5f, 0.6f);
                    cancel();
                    return;
                }
                // Re-apply buffs every 60 ticks (3s)
                if (ticks % 60 == 0) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 80, 4));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 80, 2));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 80, 2));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 80, 1));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 80, 0));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 80, 0));
                }
                // Damage pulse every 60 ticks (3s)
                if (ticks > 0 && ticks % 60 == 0) {
                    plugin.getParticleEngine().play(new SphereAnimation(plugin, p, p.getLocation(),
                            Particle.END_ROD, 15, 0.5, 10.0, 80));
                    LocationUtil.sound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_AMBIENT, 1.0f, 1.2f);
                    double dmg = 15.0 * plugin.getConfig().getDouble("schools.naruto.damage-multiplier", 1.0);
                    for (LivingEntity e : LocationUtil.nearbyLiving(p.getLocation(), 10.0, p.getUniqueId())) {
                        e.damage(dmg, p);
                        LocationUtil.knockback(e, p.getLocation(), 1.0);
                    }
                }
                // Truth-seeking orbs orbiting (every 4 ticks spawn orbiting particles)
                if (ticks % 4 == 0) {
                    Location center = p.getLocation().add(0, 2.0, 0);
                    for (int i = 0; i < 3; i++) {
                        double angle = (ticks * 0.1) + (i * Math.PI * 2 / 3);
                        double r = 1.8;
                        Location orb = center.clone().add(Math.cos(angle) * r, Math.sin(ticks * 0.05) * 0.3, Math.sin(angle) * r);
                        if (orb.getWorld() != null) {
                            try { orb.getWorld().spawnParticle(Particle.END_ROD, orb, 1, 0, 0, 0, 0); } catch (Throwable ignored) {}
                        }
                    }
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 60L, 1L);

        return true;
    }
}
