package com.anime.magic.schools.naruto;

import com.anime.magic.AnimeMagicPlugin;
import com.anime.magic.api.Caster;
import com.anime.magic.api.Spell;
import com.anime.magic.effects.HelixEffect;
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
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

/**
 * <b>Chidori v5 — CINEMATIC EDITION</b>
 *
 * <p>Multi-phase lightning assassination jutsu with directed particle stream,
 * dash strike, impact frame, and residual crackle:</p>
 *
 * <ol>
 *   <li><b>Channel (30 ticks / 1.5s):</b> Energy charge converges on caster's hand.
 *       3D chidori_blade model with slash animation. Lightning helix spirals around
 *       player. Escalating crackle sound. Speed I applied.</li>
 *   <li><b>Dash Strike (10 ticks / 0.5s):</b> When player looks at target within 12
 *       blocks: directed lightning stream from player to target (particles travel
 *       WITH the vector). Player teleport-dashes to target. Blade plays strike anim.</li>
 *   <li><b>Impact:</b> Impact frame (intensity 2.0) — target freezes, FLASH burst,
 *       screen shake for nearby. 18 lightning damage + paralysis (Slowness V 4s) +
 *       Darkness + Weakness. Blade shatters into sparks.</li>
 *   <li><b>Aftermath:</b> Residual electric crackle at impact point for 1s.</li>
 * </ol>
 */
public final class ChidoriSpell implements Spell {
    private final AnimeMagicPlugin plugin;

    public ChidoriSpell(AnimeMagicPlugin plugin) { this.plugin = plugin; }

    @Override public @NotNull String id() { return "naruto:chidori"; }
    @Override public @NotNull String displayName() { return "§b§lᴄhidori §8» §f§lᴏne ᴛhousand ʙirds"; }
    @Override public @NotNull SchoolId school() { return SchoolId.NARUTO; }
    @Override public int manaCost() { return 50; }
    @Override public long cooldownMs() { return 10000; }
    @Override public int requiredLevel() { return 15; }
    @Override public @NotNull String description() {
        return "ᴄhannel lightning, dash-strike target for 18 dmg + paralysis. ᴅirected stream + impact frame + residual crackle.";
    }
    @Override public @NotNull SpellIcon icon() {
        return new SpellIcon("PRISMARINE_SHARD", 3002, "§bChidori");
    }

    @Override
    public boolean cast(@NotNull Caster caster) {
        Player p = caster.player();
        var effects = plugin.getCinematicEffects();

        // Phase 1: Channel — energy charge + blade model + helix
        Location handLoc = p.getEyeLocation().add(p.getLocation().getDirection().multiply(0.8));
        effects.energyCharge(handLoc, 30, Particle.ELECTRIC_SPARK, null);

        ModelDisplay blade = SpellEffects.spawnInHand(plugin, p, "chidori_blade", "slash_arc", 110);
        plugin.getParticleEngine().play(new SpiralAnimation(plugin, p, Particle.ELECTRIC_SPARK, 110, 0.3, 1.0, 0.4, 8, 0.6));
        plugin.getParticleEngine().play(new HelixEffect(plugin, p, Particle.ENCHANTED_HIT, 110, 0.7, 0.5, 6, 0.6));

        effects.buildUp(handLoc, 30, Particle.ELECTRIC_SPARK, Sound.BLOCK_BEACON_ACTIVATE);
        LocationUtil.sound(p.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.5f, 1.8f);
        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 110, 0));

        // Phase 2 + 3: Wait for target acquisition (up to 5 seconds)
        new BukkitRunnable() {
            int ticks = 0;
            boolean triggered = false;
            @Override public void run() {
                if (!p.isOnline()) { cancel(); return; }
                if (triggered) { cancel(); return; }
                if (ticks++ > 100) {
                    if (blade != null) blade.remove();
                    cancel();
                    return;
                }
                if (ticks % 30 == 0 && blade != null && !blade.isDead()) {
                    var anim = plugin.getAnimationRegistry().get("slash_arc");
                    if (anim != null) blade.playAnimation(anim);
                }
                LivingEntity t = caster.targetEntity(12.0);
                if (t != null && !t.equals(p)) {
                    triggered = true;
                    dashStrike(p, t, blade, effects);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 30L, 1L);

        return true;
    }

    private void dashStrike(Player caster, LivingEntity target, ModelDisplay blade, com.anime.magic.cinematic.CinematicEffects effects) {
        // Play strike animation on blade
        if (blade != null && !blade.isDead()) {
            var strikeAnim = plugin.getAnimationRegistry().get("animation.lightning.strike");
            if (strikeAnim != null) blade.playAnimation(strikeAnim);
        }

        Location from = caster.getEyeLocation();
        Location to = target.getEyeLocation();

        // Directed lightning stream (particles travel WITH the vector)
        effects.directedStream(Particle.ELECTRIC_SPARK, from, to, 8, 5, null);
        effects.directedStream(Particle.CRIT, from, to, 8, 3, null);

        // Teleport caster next to target
        Vector dashDir = to.toVector().subtract(from.toVector()).normalize();
        Location dashTo = target.getLocation().add(dashDir.multiply(-1.5));
        caster.teleport(dashTo);

        // Impact frame (intensity 2.0)
        plugin.getImpactFrameSystem().trigger(to, target, 2.0);

        // Damage + paralysis
        double dmg = 18.0 * plugin.getConfig().getDouble("schools.naruto.damage-multiplier", 1.0);
        target.damage(dmg, caster);
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 4));
        target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 80, 2));
        target.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 40, 0));

        // Shatter blade into sparks
        if (blade != null && !blade.isDead()) {
            Location bladeLoc = blade.entity().getLocation();
            if (bladeLoc.getWorld() != null) {
                try { bladeLoc.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, bladeLoc, 1, 0.6, 0.6, 0.6, 0.3); } catch (Throwable ignored) {}
            }
            blade.remove();
        }

        // Aftermath — residual electric crackle
        effects.aftermath(to, 20, "embers");
        final Player casterFinal = caster;
        new BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                if (!casterFinal.isOnline()) { cancel(); return; }
                if (ticks >= 20 || to.getWorld() == null) { cancel(); return; }
                try { to.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, to, 1, 0.5, 0.5, 0.5, 0.1); } catch (Throwable ignored) {}
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 20L);

        LocationUtil.sound(target.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 0.8f);
    }
}
