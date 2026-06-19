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
import org.jetbrains.annotations.NotNull;

/**
 * <b>Raiton: Chidori</b> — Lightning Style: Chidori (a.k.a. "One Thousand Birds").
 *
 * <p>The caster channels lightning around their hand for 1 second, visualized by:
 * <ul>
 *   <li>A 3D {@code chidori_blade} model spawned in the hand, playing the
 *       {@code slash_arc} animation on a loop.</li>
 *   <li>Spiraling electric sparks around the player's torso.</li>
 *   <li>A double-helix of crit particles rising from the ground.</li>
 * </ul>
 * After the windup, the next melee attack within 5 seconds triggers the Chidori
 * strike: the blade model thrusts forward (animation plays once at high speed),
 * particles cascade along the strike vector, and the target takes lightning
 * damage + paralysis.</p>
 */
public final class ChidoriSpell implements Spell {

    private final AnimeMagicPlugin plugin;

    public ChidoriSpell(AnimeMagicPlugin plugin) { this.plugin = plugin; }

    @Override public @NotNull String id() { return "naruto:chidori"; }
    @Override public @NotNull String displayName() { return "§bRaiton §8» §fChidori"; }
    @Override public @NotNull SchoolId school() { return SchoolId.NARUTO; }
    @Override public int manaCost() { return 40; }
    @Override public long cooldownMs() { return 8000; }
    @Override public int requiredLevel() { return 15; }
    @Override public @NotNull String description() {
        return "Channel lightning through your hand. Your next strike deals bonus damage and paralyzes.";
    }
    @Override public @NotNull SpellIcon icon() {
        return new SpellIcon("PRISMARINE_SHARD", 3002, "§bChidori");
    }

    @Override
    public boolean cast(@NotNull Caster caster) {
        Player p = caster.player();

        // --- Spawn the 3D lightning blade model in hand with the slash animation ---
        ModelDisplay blade = SpellEffects.spawnInHand(plugin, p, "chidori_blade", "slash_arc", 120);

        // --- Windup visuals: 20 ticks of spiral + helix around the player ---
        plugin.getParticleEngine().play(
                new SpiralAnimation(plugin, p, Particle.ELECTRIC_SPARK,
                        20, 0.4, 1.2, 0.5, 6, 0.4));
        plugin.getParticleEngine().play(
                new HelixEffect(plugin, p, Particle.ENCHANTED_HIT,
                        20, 0.8, 0.4, 4, 0.5));

        LocationUtil.sound(p.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.6f, 1.8f);

        // --- Apply attack buff for 5 seconds ---
        p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 100, 2));
        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 1));
        p.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 100, 0));

        // --- One-shot lightning strike at the next melee target ---
        new BukkitRunnable() {
            int ticks = 0;
            boolean triggered = false;
            @Override public void run() {
                if (triggered || ticks++ > 100) {
                    if (blade != null) blade.remove();
                    cancel();
                    return;
                }
                // Pulse the blade model: replay the slash animation every 30 ticks so the
                // blade keeps crackling while we wait for a target
                if (ticks % 30 == 0 && blade != null && !blade.isDead()) {
                    var anim = plugin.getAnimationRegistry().get("slash_arc");
                    if (anim != null) blade.playAnimation(anim);
                }
                // Look for a target within 3.5 blocks — if found, trigger
                LivingEntity t = caster.targetEntity(3.5);
                if (t != null && !t.equals(p)) {
                    triggered = true;
                    strike(p, t, blade);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 20L, 1L);

        return true;
    }

    private void strike(Player caster, LivingEntity target, ModelDisplay blade) {
        // Thrust the blade forward (one-shot slash animation at the strike moment)
        if (blade != null && !blade.isDead()) {
            var slashAnim = plugin.getAnimationRegistry().get("slash_arc");
            if (slashAnim != null) blade.playAnimation(slashAnim);
            // Remove the blade shortly after the strike visual completes
            new BukkitRunnable() {
                @Override public void run() { blade.remove(); }
            }.runTaskLater(plugin, 10L);
        }

        Location from = caster.getEyeLocation();
        Location to = target.getEyeLocation();

        // Damage + paralysis
        double dmg = 14.0 * plugin.getConfig().getDouble("schools.naruto.damage-multiplier", 1.0);
        target.damage(dmg, caster);
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 4));
        target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 60, 2));

        // Particle cascade along the strike vector
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (t++ > 3) { cancel(); return; }
                for (double d = 0; d <= 1.0; d += 0.05) {
                    Location loc = from.clone().add(to.toVector().subtract(from.toVector()).multiply(d));
                    if (loc.getWorld() == null) continue;
                    loc.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, loc, 2,
                            0.1, 0.1, 0.1, 0.05);
                    loc.getWorld().spawnParticle(Particle.CRIT, loc, 1, 0.1, 0.1, 0.1, 0.1);
                }
                LocationUtil.sound(to, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 1.2f, 2.0f);
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
}
