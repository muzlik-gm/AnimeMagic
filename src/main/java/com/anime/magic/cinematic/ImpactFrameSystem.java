package com.anime.magic.cinematic;

import com.anime.magic.AnimeMagicPlugin;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Simulates anime-style "impact frames" — the moment of contact is emphasized
 * with a freeze-frame effect on the target, a bright flash, and a heavy
 * impact sound. Combined with screen shake, this makes every hit feel weighty.
 *
 * <p>An impact frame consists of:
 * <ol>
 *   <li><b>Freeze:</b> Target gets Slowness 255 + Mining Fatigue 5 for a few
 *       ticks (can't move or act — simulates time stopping)</li>
 *   <li><b>Flash:</b> A bright FLASH particle burst at the impact point +
 *       END_ROD particles for sparkle</li>
 *   <li><b>Sound:</b> A deep explosion + anvil-land sound at the impact</li>
 *   <li><b>Shake:</b> Screen shake for all nearby players (via ScreenShakeSystem)</li>
 *   <li><b>Aftermath:</b> Lingering smoke + dust particles for 1 second after</li>
 * </ol></p>
 */
public final class ImpactFrameSystem {

    private final AnimeMagicPlugin plugin;
    private final boolean enabled;
    private final int freezeTicks;

    public ImpactFrameSystem(AnimeMagicPlugin plugin) {
        this.plugin = plugin;
        this.enabled = plugin.getConfig().getBoolean("cinematic.impact-frame.enabled", true);
        this.freezeTicks = plugin.getConfig().getInt("cinematic.impact-frame.freeze-ticks", 5);
    }

    /**
     * Trigger an impact frame at the given location.
     *
     * @param impactPoint Where the hit landed
     * @param target The entity being hit (optional — if null, only visual effects)
     * @param intensity Multiplier on the effect (1.0 = normal, 2.0 = ultimate)
     */
    public void trigger(@NotNull Location impactPoint, @Nullable LivingEntity target, double intensity) {
        if (!enabled) return;
        if (impactPoint.getWorld() == null) return;

        // 1. Freeze the target. Amplifier 127 (max meaningful slowness) — 255
        // exceeds the signed-byte range the client expects and some clients wrap
        // 255 → -1 (a speed boost) instead of slowness 255.
        if (target != null && !target.isDead()) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, freezeTicks, 127, false, false, false));
            target.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, freezeTicks, 5, false, false, false));
        }

        // 2. Flash burst
        impactPoint.getWorld().spawnParticle(Particle.FLASH, impactPoint, 1, 0, 0, 0, 0);
        impactPoint.getWorld().spawnParticle(Particle.END_ROD, impactPoint, (int)(8 * intensity), 0.3, 0.3, 0.3, 0.1);
        impactPoint.getWorld().spawnParticle(Particle.FIREWORK, impactPoint, (int)(5 * intensity), 0.4, 0.4, 0.4, 0.15);

        // 3. Impact sound (layered for weight)
        impactPoint.getWorld().playSound(impactPoint, Sound.ENTITY_GENERIC_EXPLODE, 1.5f * (float)intensity, 0.5f);
        impactPoint.getWorld().playSound(impactPoint, Sound.BLOCK_ANVIL_LAND, 1.2f * (float)intensity, 0.6f);
        impactPoint.getWorld().playSound(impactPoint, Sound.ENTITY_PLAYER_ATTACK_STRONG, 1.5f * (float)intensity, 0.4f);

        // 4. Screen shake for all nearby players
        if (plugin.getScreenShakeSystem() != null) {
            plugin.getScreenShakeSystem().shakeNearby(impactPoint, 15.0 * intensity, intensity);
        }

        // 5. Aftermath — lingering smoke + dust for 1 second (20 ticks)
        new BukkitRunnable() {
            int tick = 0;
            @Override public void run() {
                if (tick >= 20 || impactPoint.getWorld() == null) { cancel(); return; }
                // Smoke rises
                impactPoint.getWorld().spawnParticle(Particle.LARGE_SMOKE, impactPoint, 2, 0.5, 0.3, 0.5, 0.02);
                // Dust settles
                if (tick % 3 == 0) {
                    impactPoint.getWorld().spawnParticle(Particle.CLOUD, impactPoint, 1, 0.8, 0.1, 0.8, 0.01);
                }
                tick++;
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    /**
     * Convenience: trigger an impact frame with normal intensity.
     */
    public void trigger(@NotNull Location impactPoint, @Nullable LivingEntity target) {
        trigger(impactPoint, target, 1.0);
    }
}
