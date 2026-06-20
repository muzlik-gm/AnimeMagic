package com.anime.magic.schools.naruto;

import com.anime.magic.AnimeMagicPlugin;
import com.anime.magic.api.Caster;
import com.anime.magic.api.Spell;
import com.anime.magic.models.ModelDisplay;
import com.anime.magic.util.LocationUtil;
import com.anime.magic.util.SpellEffects;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

/**
 * <b>Sage Mode — Sennin Mōdo</b>
 *
 * <p>Channel natural energy to enter Sage Mode. Lasts 30 seconds.</p>
 *
 * <ol>
 *   <li><b>Awaken (40 ticks / 2s):</b> 3D sage_aura model spawns around the player
 *       playing sage.awaken. Orange particle helix rises from the ground.</li>
 *   <li><b>Active (600 ticks / 30s):</b> Player gains Strength III, Resistance II,
 *       Speed II, Jump Boost II, Regeneration I. Continuous orange spiral aura.</li>
 * </ol>
 */
public final class SageModeSpell implements Spell {
    private final AnimeMagicPlugin plugin;

    public SageModeSpell(AnimeMagicPlugin plugin) { this.plugin = plugin; }

    @Override public @NotNull String id() { return "naruto:sage_mode"; }
    @Override public @NotNull String displayName() { return "§6§lꜱage ᴍode §8» §e§lꜱennin ᴍōdo"; }
    @Override public @NotNull SchoolId school() { return SchoolId.NARUTO; }
    @Override public int manaCost() { return 90; }
    @Override public long cooldownMs() { return 60000; }
    @Override public int requiredLevel() { return 40; }
    @Override public @NotNull String description() {
        return "ᴇnter ꜱage ᴍode for 30 seconds. ꜱtrength ɪɪɪ, ʀesistance ɪɪ, ꜱpeed ɪɪ, ᴊump ʙoost ɪɪ, ʀegeneration ɪ.";
    }
    @Override public @NotNull SpellIcon icon() {
        return new SpellIcon("NETHER_STAR", 7010, "§eSage Mode");
    }

    @Override
    public boolean cast(@NotNull Caster caster) {
        Player p = caster.player();
        // Phase 1: Awaken
        ModelDisplay aura = SpellEffects.spawnAnimated(plugin, p, "sage_aura", "animation.sage.awaken", p.getEyeLocation().add(p.getLocation().getDirection().multiply(1.5)), 640, null);
        if (aura != null && !aura.isDead()) {
            aura.followPlayer(p.getUniqueId(), new org.bukkit.util.Vector(0, 0, 0));
        }

        LocationUtil.sound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.5f, 0.7f);
        LocationUtil.sound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_AMBIENT, 1.0f, 0.5f);

        // Subtle natural-energy gathering effect: 2 END_ROD particles per second
        // at a random point 3 blocks from the player, drifting inward. Off the
        // view axis (3 blocks horizontally) so the player's first-person view
        // stays clear — no screen-blinding aura.
        new BukkitRunnable() {
            @Override public void run() {
                if (!p.isOnline()) { cancel(); return; }
                Location base = p.getLocation();
                if (base.getWorld() == null) return;
                for (int i = 0; i < 2; i++) {
                    double angle = Math.random() * Math.PI * 2;
                    Location from = base.clone().add(Math.cos(angle) * 3.0, 1.0, Math.sin(angle) * 3.0);
                    org.bukkit.util.Vector inward = base.toVector().subtract(from.toVector()).normalize().multiply(0.5);
                    Location spawn = from.clone().add(inward);
                    try { base.getWorld().spawnParticle(Particle.END_ROD, spawn, 1, 0, 0, 0, 0); } catch (Throwable ignored) {}
                }
            }
        }.runTaskTimer(plugin, 40L, 20L);

        // Apply buffs immediately + re-apply every 100 ticks (5s) for 30s
        new BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                if (!p.isOnline()) { cancel(); return; }
                if (ticks >= 600) {
                    if (aura != null) aura.remove();
                    LocationUtil.sound(p.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 0.7f);
                    cancel();
                    return;
                }
                // Re-apply buffs every 100 ticks (5s) — PotionEffects last 5s each
                if (ticks % 100 == 0) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 120, 2));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 120, 1));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 120, 1));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 120, 1));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 120, 0));
                }
                // Periodic sound
                if (ticks % 60 == 0) {
                    LocationUtil.sound(p.getLocation(), Sound.BLOCK_BEACON_AMBIENT, 0.3f, 1.5f);
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 40L, 1L);

        return true;
    }
}
