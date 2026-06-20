package com.anime.magic.schools.onepiece;

import com.anime.magic.AnimeMagicPlugin;
import com.anime.magic.api.Caster;
import com.anime.magic.api.Spell;
import com.anime.magic.effects.HelixEffect;
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

/**
 * <b>Voice of All Things</b>
 *
 * <p>Luffy's mysterious ability — commune with the world itself. For 10 seconds,
 * all hostile entities within 30 blocks stop attacking (Slowness 255 + Weakness 10
 * + Peace), and the caster regenerates 5 HP per second.</p>
 */
public final class VoiceOfAllThingsSpell implements Spell {
    private final AnimeMagicPlugin plugin;

    public VoiceOfAllThingsSpell(AnimeMagicPlugin plugin) { this.plugin = plugin; }

    @Override public @NotNull String id() { return "onepiece:voice_of_all_things"; }
    @Override public @NotNull String displayName() { return "§f§lᴠoice of ᴀll ᴛhings"; }
    @Override public @NotNull SchoolId school() { return SchoolId.ONEPIECE; }
    @Override public int manaCost() { return 130; }
    @Override public long cooldownMs() { return 75000; }
    @Override public int requiredLevel() { return 55; }
    @Override public @NotNull String description() {
        return "ᴄommune with the world for 10s. ᴀll hostiles within 30 blocks become passive. ʏou regen 5 ʜᴘ/s.";
    }
    @Override public @NotNull SpellIcon icon() { return new SpellIcon("NETHER_STAR", 2004, "§fVoice of All Things"); }

    @Override
    public boolean cast(@NotNull Caster caster) {
        Player p = caster.player();
        // Spawn the 3D voice_waves model above the caster's head.
        com.anime.magic.util.SpellEffects.spawnAnimated(plugin, p,
                "voice_waves", "animation.voice.pulse",
                p.getEyeLocation().add(p.getLocation().getDirection().multiply(1.5)).clone(), 300, null);
        // Initial burst
        plugin.getParticleEngine().play(new SphereAnimation(plugin, p, p.getLocation(),
                Particle.END_ROD, 25, 1.0, 15.0, 100));
        plugin.getParticleEngine().play(new HelixEffect(plugin, p, Particle.END_ROD, 50, 2.0, 0.5, 14, 0.3));
        LocationUtil.sound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 2.0f, 1.5f);
        LocationUtil.sound(p.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1.0f, 0.5f);

        new BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                if (!p.isOnline()) { cancel(); return; }
                if (ticks >= 200) {
                    plugin.getParticleEngine().play(new RingBurst(plugin, p, p.getLocation(),
                            Particle.END_ROD, 20, 8.0, 60));
                    LocationUtil.sound(p.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1.5f, 1.2f);
                    cancel();
                    return;
                }
                // Pulse every 20 ticks (1s)
                if (ticks % 20 == 0) {
                    // Pacify all hostiles within 30 blocks
                    for (LivingEntity e : LocationUtil.nearbyLiving(p.getLocation(), 30.0, p.getUniqueId())) {
                        e.addPotionEffect(new org.bukkit.potion.PotionEffect(
                                org.bukkit.potion.PotionEffectType.SLOWNESS, 30, 100));
                        e.addPotionEffect(new org.bukkit.potion.PotionEffect(
                                org.bukkit.potion.PotionEffectType.WEAKNESS, 30, 10));
                        e.addPotionEffect(new org.bukkit.potion.PotionEffect(
                                org.bukkit.potion.PotionEffectType.GLOWING, 30, 0));
                    }
                    // Regen caster
                    if (p.getHealth() < p.getMaxHealth()) {
                        p.setHealth(Math.min(p.getMaxHealth(), p.getHealth() + 5));
                    }
                    LocationUtil.sound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.5f, 1.5f);
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        return true;
    }
}
