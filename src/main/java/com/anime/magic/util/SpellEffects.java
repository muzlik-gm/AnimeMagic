package com.anime.magic.util;

import com.anime.magic.AnimeMagicPlugin;
import com.anime.magic.animation.AnimationPlayer;
import com.anime.magic.animation.KeyframeAnimation;
import com.anime.magic.models.CustomModel;
import com.anime.magic.models.ModelDisplay;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Convenience façade for spells that want to spawn a {@link ModelDisplay} with an
 * attached {@link AnimationPlayer}. Hides the registry lookups and null-safety
 * checks so spell code stays a one-liner.
 *
 * <p>Typical usage from inside a {@link com.anime.magic.api.Spell#cast}:</p>
 * <pre>
 *   SpellEffects.spawnAnimated(plugin, caster.player(),
 *       "magic_orb",           // model id
 *       "orb_spin",             // animation id
 *       caster.eyeLocation(),   // spawn position
 *       200,                    // lifetime ticks
 *       new Vector(0, 1, 0));   // follow offset (or null for static)
 * </pre>
 */
public final class SpellEffects {

    private SpellEffects() {}

    /**
     * Spawn a custom model with an attached keyframe animation.
     *
     * @param plugin       the plugin instance
     * @param owner        the player owning this model (for cleanup on quit)
     * @param modelId      the model id (must exist in {@code models/*.json})
     * @param animationId  the animation id (must exist in {@code animations/*.anim.json}); null for no anim
     * @param spawn        where to spawn the display
     * @param lifetimeTicks how long to keep the display alive (0 = manual cleanup only)
     * @param followOffset if non-null, the display follows the owner with this offset every tick
     * @return the spawned {@link ModelDisplay}, or {@code null} if the model id is unknown
     */
    public static @Nullable ModelDisplay spawnAnimated(@NotNull AnimeMagicPlugin plugin,
                                                       @Nullable Player owner,
                                                       @NotNull String modelId,
                                                       @Nullable String animationId,
                                                       @NotNull Location spawn,
                                                       int lifetimeTicks,
                                                       @Nullable Vector followOffset) {
        // ItemDisplay entities require Paper 1.19.4+. On older versions
        // (1.8.x - 1.19.3), silently skip — the 3D model cannot render.
        if (!supportsItemDisplay(plugin)) {
            return null;
        }
        CustomModel model = plugin.getModelRegistry().get(modelId);
        if (model == null) {
            plugin.getLogger().warning("SpellEffects: unknown model id '" + modelId + "'");
            return null;
        }
        try {
            // Reset yaw/pitch on the spawn location so the model doesn't
            // inherit the player's view direction (was causing the model
            // to rotate when the player looked up/down/left/right).
            spawn.setYaw(0f);
            spawn.setPitch(0f);
            ModelDisplay display = new ModelDisplay(plugin, model, spawn,
                    owner != null ? owner.getUniqueId() : null, lifetimeTicks);

            if (animationId != null) {
                KeyframeAnimation anim = plugin.getAnimationRegistry().get(animationId);
                if (anim == null) {
                    plugin.getLogger().warning("SpellEffects: unknown animation id '" + animationId + "'");
                } else {
                    display.playAnimation(anim);
                }
            }

            if (followOffset != null && owner != null) {
                display.followPlayer(owner.getUniqueId(), followOffset);
            }

            return display;
        } catch (Throwable t) {
            // NoClassDefFoundError / NoSuchMethodError on old servers
            plugin.getLogger().fine("ItemDisplay not supported on this server version: " + t.getMessage());
            return null;
        }
    }

    /**
     * Check if the server supports ItemDisplay entities (Paper 1.19.4+).
     * Cached after first check.
     */
    private static Boolean itemDisplaySupported = null;
    private static boolean supportsItemDisplay(AnimeMagicPlugin plugin) {
        if (itemDisplaySupported != null) return itemDisplaySupported;
        try {
            Class.forName("org.bukkit.entity.ItemDisplay");
            itemDisplaySupported = true;
        } catch (ClassNotFoundException e) {
            plugin.getLogger().warning("ItemDisplay not available on this server version "
                    + "(requires Paper 1.19.4+). 3D spell models will not render. "
                    + "Use Paper 1.21+ for full 3D model support.");
            itemDisplaySupported = false;
        }
        return itemDisplaySupported;
    }

    /**
     * Spawn an animated model that plays once and self-removes when the animation finishes.
     * Useful for one-shot visual effects (slash arcs, charge bursts).
     */
    public static @Nullable ModelDisplay spawnOneShot(@NotNull AnimeMagicPlugin plugin,
                                                     @Nullable Player owner,
                                                     @NotNull String modelId,
                                                     @NotNull String animationId,
                                                     @NotNull Location spawn,
                                                     @Nullable Vector followOffset) {
        CustomModel model = plugin.getModelRegistry().get(modelId);
        if (model == null) return null;
        KeyframeAnimation anim = plugin.getAnimationRegistry().get(animationId);
        if (anim == null) {
            return spawnAnimated(plugin, owner, modelId, null, spawn, 40, followOffset);
        }
        // Estimate lifetime from animation length, plus a 5-tick buffer
        int lifetime = anim.lengthTicks() + 5;
        ModelDisplay display = spawnAnimated(plugin, owner, modelId, animationId, spawn, lifetime, followOffset);
        return display;
    }

    /**
     * Spawn an animated model that follows the player's hand (right-hand item position).
     * Uses a forward+down offset so the model appears in front of the player at chest height.
     */
    public static @Nullable ModelDisplay spawnInHand(@NotNull AnimeMagicPlugin plugin,
                                                     @NotNull Player owner,
                                                     @NotNull String modelId,
                                                     @Nullable String animationId,
                                                     int lifetimeTicks) {
        Location hand = owner.getEyeLocation().add(owner.getLocation().getDirection().multiply(0.8));
        // Slight downward offset so the model sits at hand height rather than eye height
        hand.add(0, -0.4, 0);
        return spawnAnimated(plugin, owner, modelId, animationId, hand, lifetimeTicks,
                new Vector(0, -0.4, 0).rotateAroundY(owner.getLocation().getYaw() * Math.PI / 180f));
    }
}
