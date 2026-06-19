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
 * <b>Chidori v2 — Production Overhaul</b>
 *
 * <p>Multi-phase lightning-style assassination jutsu:</p>
 *
 * <ol>
 *   <li><b>Phase 1 — Channel (30 ticks / 1.5s):</b> The caster crouches (no movement
 *       enforced, but Speed I applied). A 3D {@code chidori_blade} model spawns in
 *       the right hand playing {@code slash_arc} on loop. Lightning particles crackle
 *       around the player's torso in a tight double-helix. Sound: thunder rumble.</li>
 *
 *   <li><b>Phase 2 — Dash Strike (10 ticks / 0.5s):</b> When the player looks at a
 *       target within 12 blocks, they teleport-dash to it (lightning trail particles
 *       along the dash vector) and strike. The blade model plays
 *       {@code animation.lightning.strike} once.</li>
 *
 *   <li><b>Phase 3 — Impact:</b> Lightning cascades along the strike vector. Target
 *       takes 18 damage, gets Slowness V (paralysis) for 4 seconds, and Weakness II.
 *       The blade shatters into spark particles.</li>
 * </ol>
 *
 * <p>If no target acquired within 5 seconds of channel, the blade fizzles out.</p>
 */
public final class ChidoriSpell implements Spell {
    private final AnimeMagicPlugin plugin;

    public ChidoriSpell(AnimeMagicPlugin plugin) { this.plugin = plugin; }

    @Override public @NotNull String id() { return "naruto:chidori"; }
    @Override public @NotNull String displayName() { return "§b§lChidori §8» §fOne Thousand Birds"; }
    @Override public @NotNull SchoolId school() { return SchoolId.NARUTO; }
    @Override public int manaCost() { return 50; }
    @Override public long cooldownMs() { return 10000; }
    @Override public int requiredLevel() { return 15; }
    @Override public @NotNull String description() {
        return "Channel lightning through your hand for 1.5s. Look at a target within 12 blocks to dash-strike "
                + "for 18 damage + paralysis. 3-phase cast: Channel -> Dash -> Impact.";
    }
    @Override public @NotNull SpellIcon icon() {
        return new SpellIcon("PRISMARINE_SHARD", 3002, "§bChidori");
    }

    @Override
    public boolean cast(@NotNull Caster caster) {
        Player p = caster.player();

        // Phase 1: Channel
        ModelDisplay blade = SpellEffects.spawnInHand(plugin, p, "chidori_blade", "slash_arc", 110);
        plugin.getParticleEngine().play(
                new SpiralAnimation(plugin, p, Particle.ELECTRIC_SPARK,
                        110, 0.3, 1.0, 0.4, 8, 0.6));
        plugin.getParticleEngine().play(
                new HelixEffect(plugin, p, Particle.ENCHANTED_HIT,
                        110, 0.7, 0.5, 6, 0.6));

        LocationUtil.sound(p.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.5f, 1.8f);
        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 110, 0));

        // Channel sound — escalating crackle
        new BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                if (ticks >= 30 || blade == null || blade.isDead()) { cancel(); return; }
                if (ticks % 5 == 0) {
                    Location hand = p.getEyeLocation().add(p.getLocation().getDirection().multiply(0.8));
                    LocationUtil.sound(hand, Sound.BLOCK_BEACON_ACTIVATE,
                            0.3f, 1.5f + ticks * 0.03f);
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        // Phase 2 + 3: Wait for target acquisition (up to 5 seconds = 100 ticks)
        new BukkitRunnable() {
            int ticks = 0;
            boolean triggered = false;
            @Override public void run() {
                if (triggered) { cancel(); return; }
                if (ticks++ > 100) {
                    if (blade != null) blade.remove();
                    cancel();
                    return;
                }
                // Replay slash animation periodically
                if (ticks % 30 == 0 && blade != null && !blade.isDead()) {
                    var anim = plugin.getAnimationRegistry().get("slash_arc");
                    if (anim != null) blade.playAnimation(anim);
                }
                // Look for target within 12 blocks
                LivingEntity t = caster.targetEntity(12.0);
                if (t != null && !t.equals(p)) {
                    triggered = true;
                    dashStrike(p, t, blade);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 30L, 1L);

        return true;
    }

    private void dashStrike(Player caster, LivingEntity target, ModelDisplay blade) {
        // Play lightning.strike animation on the blade
        if (blade != null && !blade.isDead()) {
            var strikeAnim = plugin.getAnimationRegistry().get("animation.lightning.strike");
            if (strikeAnim != null) blade.playAnimation(strikeAnim);
        }

        Location from = caster.getEyeLocation();
        Location to = target.getEyeLocation();
        Vector dashDir = to.toVector().subtract(from.toVector()).normalize();
        double distance = from.distance(to);

        // Lightning trail along the dash vector
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (t++ > 4) { cancel(); return; }
                for (double d = 0; d <= 1.0; d += 0.05) {
                    Location loc = from.clone().add(to.toVector().subtract(from.toVector()).multiply(d));
                    if (loc.getWorld() == null) continue;
                    loc.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, loc, 3, 0.15, 0.15, 0.15, 0.1);
                    loc.getWorld().spawnParticle(Particle.CRIT, loc, 1, 0.1, 0.1, 0.1, 0.1);
                }
                LocationUtil.sound(to, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 1.4f, 2.0f);
            }
        }.runTaskTimer(plugin, 0L, 1L);

        // Teleport caster next to target
        Location dashTo = target.getLocation().add(dashDir.multiply(-1.5));
        caster.teleport(dashTo);

        // Phase 3: Impact damage + paralysis
        double dmg = 18.0 * plugin.getConfig().getDouble("schools.naruto.damage-multiplier", 1.0);
        target.damage(dmg, caster);
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 4)); // paralysis 4s
        target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 80, 2));
        target.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 40, 0)); // brief blind

        // Shatter blade into sparks
        if (blade != null && !blade.isDead()) {
            Location bladeLoc = blade.entity().getLocation();
            if (bladeLoc.getWorld() != null) {
                bladeLoc.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, bladeLoc, 60, 0.6, 0.6, 0.6, 0.3);
                bladeLoc.getWorld().spawnParticle(Particle.FIREWORK, bladeLoc, 20, 0.4, 0.4, 0.4, 0.2);
            }
            blade.remove();
        }
        LocationUtil.sound(target.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 0.8f);
    }
}
