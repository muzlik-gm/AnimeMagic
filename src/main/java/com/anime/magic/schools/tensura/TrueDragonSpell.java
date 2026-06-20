package com.anime.magic.schools.tensura;

import com.anime.magic.AnimeMagicPlugin;
import com.anime.magic.api.Caster;
import com.anime.magic.api.Spell;
import com.anime.magic.effects.HelixEffect;
import com.anime.magic.effects.RingBurst;
import com.anime.magic.effects.SphereAnimation;
import com.anime.magic.effects.SpiralAnimation;
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
 * <b>True Dragon Form — Ultimate Skill: Dragon Form</b>
 *
 * <p>Rimuru's final form — transforms into a True Dragon for 30 seconds.</p>
 *
 * <ol>
 *   <li><b>Transform (50 ticks / 2.5s):</b> Player rises 1.5 blocks. 3D sage_aura
 *       model grows to 2.5x scale playing dragon.transform. Black + purple helixes
 *       spiral around them. Sound: dragon roar.</li>
 *   <li><b>Active (600 ticks / 30s):</b> Player gains Flight, Strength V, Resistance IV,
 *       Speed III, Fire Resistance, Regeneration III. Every 2 seconds, an aura pulse
 *       deals 12 damage to all enemies within 8 blocks. The player leaves a trail of
 *       dragon breath particles.</li>
 *   <li><b>End:</b> Player descends. Final massive shockwave ring.</li>
 * </ol>
 */
public final class TrueDragonSpell implements Spell {
    private final AnimeMagicPlugin plugin;

    public TrueDragonSpell(AnimeMagicPlugin plugin) { this.plugin = plugin; }

    @Override public @NotNull String id() { return "tensura:true_dragon"; }
    @Override public @NotNull String displayName() { return "§8§l§k||§r §5§lTrue Dragon Form §8§l§k||§r"; }
    @Override public @NotNull Spell.SchoolId school() { return Spell.SchoolId.TENSURA; }
    @Override public int manaCost() { return 250; }
    @Override public long cooldownMs() { return 180000; }
    @Override public int requiredLevel() { return 70; }
    @Override public @NotNull String description() {
        return "Transform into a True Dragon for 30s. Flight + massive buffs. Aura pulses 12 damage to all nearby every 2s.";
    }
    @Override public @NotNull Spell.SpellIcon icon() {
        return new SpellIcon("NETHER_STAR", 7010, "§5True Dragon");
    }

    @Override
    public boolean cast(@NotNull Caster caster) {
        Player p = caster.player();

        // Cache pre-cast flight state so we don't corrupt creative/spectator
        // flight permission when the spell ends.
        final boolean wasCreative = p.getGameMode() == org.bukkit.GameMode.CREATIVE
                || p.getGameMode() == org.bukkit.GameMode.SPECTATOR;
        final boolean hadAllowFlight = p.getAllowFlight();

        // Phase 1: Transform
        if (!wasCreative) {
            p.setAllowFlight(true);
            p.setFlying(true);
        }
        ModelDisplay aura = SpellEffects.spawnAnimated(plugin, p, "sage_aura", "animation.dragon.transform", p.getLocation(), 660, null);
        if (aura != null) aura.followPlayer(p.getUniqueId(), new org.bukkit.util.Vector(0, 0, 0));

        plugin.getParticleEngine().play(new HelixEffect(plugin, p, Particle.DRAGON_BREATH, 50, 1.5, 0.7, 12, 0.4));
        plugin.getParticleEngine().play(new HelixEffect(plugin, p, Particle.SQUID_INK, 50, 1.2, 0.6, 10, 0.5));
        LocationUtil.sound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 2.5f, 0.4f);
        LocationUtil.sound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 2.0f, 0.5f);

        // Phase 2: Active — buffs + aura pulses
        new BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                if (!p.isOnline()) { cancel(); return; }
                if (ticks >= 600) {
                    // End — restore original flight state (don't revoke creative flight).
                    if (!wasCreative) {
                        p.setFlying(false);
                        p.setAllowFlight(hadAllowFlight);
                    }
                    if (aura != null) aura.remove();
                    plugin.getParticleEngine().play(new RingBurst(plugin, p, p.getLocation(), Particle.DRAGON_BREATH, 30, 10.0, 100));
                    LocationUtil.sound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_DEATH, 2.0f, 0.5f);
                    cancel();
                    return;
                }
                // Re-apply buffs every 60 ticks (3s)
                if (ticks % 60 == 0) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 80, 4));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 80, 3));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 80, 2));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 80, 0));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 80, 2));
                }
                // Aura pulse every 40 ticks (2s)
                if (ticks > 0 && ticks % 40 == 0) {
                    plugin.getParticleEngine().play(new SphereAnimation(plugin, p, p.getLocation(),
                            Particle.DRAGON_BREATH, 12, 0.5, 8.0, 60));
                    LocationUtil.sound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_AMBIENT, 1.5f, 0.8f);
                    double dmg = 12.0 * plugin.getConfig().getDouble("schools.tensura.damage-multiplier", 1.0);
                    for (LivingEntity e : LocationUtil.nearbyLiving(p.getLocation(), 8.0, p.getUniqueId())) {
                        e.damage(dmg, p);
                        LocationUtil.knockback(e, p.getLocation(), 1.0);
                    }
                }
                // Dragon breath trail
                if (ticks % 3 == 0 && p.getWorld() != null) {
                    Location feet = p.getLocation();
                    p.getWorld().spawnParticle(Particle.DRAGON_BREATH, feet, 2, 0.5, 0.1, 0.5, 0.02);
                    p.getWorld().spawnParticle(Particle.FLAME, feet, 1, 0.3, 0.1, 0.3, 0.02);
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 50L, 1L);

        return true;
    }
}
