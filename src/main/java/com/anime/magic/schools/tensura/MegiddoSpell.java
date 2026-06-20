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
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

/**
 * <b>Megiddo — Ultimate Skill: Light of Judgment</b>
 *
 * <p>Rimuru's signature skill — focuses sunlight into a lethal pinpoint beam.
 * Instantly disintegrates the target with holy light.</p>
 *
 * <ol>
 *   <li><b>Charge (20 ticks / 1s):</b> Particle sphere of END_ROD + SPORE_BLOSSOM_AIR
 *       converges on the target's location. Sound: beacon power-up escalating.</li>
 *   <li><b>Strike (instant):</b> A vertical pillar of light descends from the sky onto
 *       the target. Flash particle burst. Sound: thunder + explosion.</li>
 *   <li><b>Damage:</b> Target takes 60 holy damage. If target has <50% HP, executes
 *       (instant kill). All entities within 3 blocks take 20 damage.</li>
 * </ol>
 */
public final class MegiddoSpell implements Spell {
    private final AnimeMagicPlugin plugin;

    public MegiddoSpell(AnimeMagicPlugin plugin) { this.plugin = plugin; }

    @Override public @NotNull String id() { return "tensura:megiddo"; }
    @Override public @NotNull String displayName() { return "§f§l§k||§r §f§lUltimate: Megiddo §f§l§k||§r"; }
    @Override public @NotNull Spell.SchoolId school() { return Spell.SchoolId.TENSURA; }
    @Override public int manaCost() { return 150; }
    @Override public long cooldownMs() { return 60000; }
    @Override public int requiredLevel() { return 55; }
    @Override public @NotNull String description() {
        return "Focus sunlight into a lethal pinpoint beam. 60 holy damage, executes targets below 50% HP. 20 AoE.";
    }
    @Override public @NotNull Spell.SpellIcon icon() {
        return new SpellIcon("NETHER_STAR", 2002, "§fMegiddo");
    }

    @Override
    public boolean cast(@NotNull Caster caster) {
        Player p = caster.player();
        LivingEntity target = caster.targetEntity(40);
        if (target == null) return false;
        Location strike = target.getLocation();

        // Spawn the 3D light pillar model at the strike location — descends
        // from above and plays animation.megiddo.descend.
        com.anime.magic.util.SpellEffects.spawnAnimated(plugin, p,
                "megiddo_pillar", "animation.megiddo.descend",
                strike.clone().add(0, 0, 0), 60, null);

        // Phase 1: Charge — converging light sphere on target
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (!p.isOnline()) { cancel(); return; }
                if (t >= 20) { cancel(); return; }
                if (strike.getWorld() == null) { cancel(); return; }
                for (int i = 0; i < 6; i++) {
                    double angle = (t * 0.4) + (i * Math.PI / 3);
                    double r = 3.0 - t * 0.14;
                    double y = 4.0 - t * 0.18;
                    Location from = strike.clone().add(Math.cos(angle) * r, y, Math.sin(angle) * r);
                    strike.getWorld().spawnParticle(Particle.END_ROD, from, 0,
                            -Math.cos(angle) * 0.3, -0.4, -Math.sin(angle) * 0.3, 0.0);
                    strike.getWorld().spawnParticle(Particle.SPORE_BLOSSOM_AIR, from, 0,
                            -Math.cos(angle) * 0.2, -0.3, -Math.sin(angle) * 0.2, 0.0);
                }
                if (t % 4 == 0) LocationUtil.sound(strike, Sound.BLOCK_BEACON_ACTIVATE, 0.5f + t * 0.05f, 1.5f);
                t++;
            }
        }.runTaskTimer(plugin, 0L, 4L);

        // Phase 2 + 3: Strike + damage at 20 ticks
        new BukkitRunnable() {
            @Override public void run() {
                if (!p.isOnline()) { cancel(); return; }
                if (strike.getWorld() == null) return;
                // Vertical pillar from sky
                for (int y = 0; y < 30; y++) {
                    Location pillar = strike.clone().add(0, y, 0);
                    strike.getWorld().spawnParticle(Particle.END_ROD, pillar, 2, 0.1, 0, 0.1, 0);
                    strike.getWorld().spawnParticle(Particle.FLASH, pillar, 1, 0.2, 0, 0.2, 0);
                }
                // Expanding sphere
                plugin.getParticleEngine().play(new SphereAnimation(plugin, p, strike, Particle.END_ROD, 25, 0.5, 6.0, 100));
                plugin.getParticleEngine().play(new SphereAnimation(plugin, p, strike, Particle.FLASH, 5, 0.2, 3.0, 20));
                LocationUtil.sound(strike, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 3.0f, 1.8f);
                LocationUtil.sound(strike, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 1.5f);
                LocationUtil.sound(strike, Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 0.5f);

                double dmg = 60.0 * plugin.getConfig().getDouble("schools.tensura.damage-multiplier", 1.0);
                if (!target.isDead()) {
                    if (target.getHealth() < target.getMaxHealth() * 0.5) {
                        // Execute: use damage() instead of setHealth(0) so the death
                        // fires a proper EntityDamageEvent attributed to the caster.
                        // WorldGuard / anti-cheat / damage-tracking plugins can react.
                        target.damage(target.getHealth(), p);
                    } else {
                        target.damage(dmg, p);
                    }
                }
                double aoe = 20.0 * plugin.getConfig().getDouble("schools.tensura.damage-multiplier", 1.0);
                for (LivingEntity e : LocationUtil.nearbyLiving(strike, 3.0, p.getUniqueId())) {
                    if (e.getUniqueId().equals(target.getUniqueId())) continue;
                    e.damage(aoe, p);
                }
            }
        }.runTaskLater(plugin, 20L);

        return true;
    }
}
