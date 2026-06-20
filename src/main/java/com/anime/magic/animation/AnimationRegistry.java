package com.anime.magic.animation;

import com.anime.magic.AnimeMagicPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

/**
 * Loads Blockbench-format .anim.json files from the animations/ folder. Each file may
 * contain multiple named animations. They are registered under both bare name AND
 * file-prefixed name (e.g. "orb_spin" and "orb_spin_animation.orb.spin").
 *
 * <p>On every {@link #reload()}, ALL animation JSON files bundled in the jar's
 * {@code resources/animations/} directory are extracted to the plugin's data folder,
 * overwriting stale copies. This ensures new animation files from updates are
 * always available.</p>
 */
public final class AnimationRegistry {
    private final AnimeMagicPlugin plugin;
    private final File folder;
    private final Map<String, KeyframeAnimation> animations = new HashMap<>();

    public AnimationRegistry(AnimeMagicPlugin plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "animations");
        if (!folder.exists()) folder.mkdirs();
    }

    public void reload() {
        animations.clear();
        folder.mkdirs();
        // Extract ALL bundled animation files from the jar
        extractAllBundledAnimations();
        // Load every .json file in the data folder
        File[] files = folder.listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".json"));
        if (files == null) return;
        for (File f : files) {
            try {
                String content = Files.readString(f.toPath(), StandardCharsets.UTF_8);
                JSONObject json = new JSONObject(content);
                JSONObject anims = json.optJSONObject("animations");
                if (anims == null) continue;
                String fileBase = f.getName().toLowerCase(Locale.ROOT)
                        .replace(".anim.json", "").replace(".json", "");
                for (String animName : anims.keySet()) {
                    JSONObject animJson = anims.optJSONObject(animName);
                    if (animJson == null) continue;
                    KeyframeAnimation anim = KeyframeAnimation.parse(animName, animJson);
                    if (anim != null) {
                        // Register under 3 keys for maximum lookup tolerance:
                        // 1. Bare animation name (e.g. "animation.slash.arc")
                        animations.put(animName.toLowerCase(Locale.ROOT), anim);
                        // 2. File-prefixed (e.g. "slash_arc_animation.slash.arc")
                        animations.put((fileBase + "_" + animName).toLowerCase(Locale.ROOT), anim);
                        // 3. Just the file base name (e.g. "slash_arc") — matches what
                        //    spells pass when they reference the animation by filename
                        animations.put(fileBase.toLowerCase(Locale.ROOT), anim);
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING,
                        "Failed to parse animation file " + f.getName() + ": " + e.getMessage(), e);
            }
        }
        plugin.getLogger().info("Loaded " + animations.size() + " animations from " + folder.getName() + "/");
    }

    /**
     * Extract every animation file from the jar's resources/animations/ directory
     * into the plugin's data folder. Overwrites existing files so updates take effect.
     */
    private void extractAllBundledAnimations() {
        String[] knownAnims = {
            "orb_spin.anim.json", "cast_charge.anim.json", "slash_arc.anim.json",
            "chidori_choreography.anim.json", "conquerors_choreography.anim.json",
            "fireball_charge_v2.anim.json", "haki_burst.anim.json",
            "lightning_strike.anim.json", "mushoku_ultimates.anim.json",
            "naruto_ultimates.anim.json", "rasengan_choreography.anim.json",
            "sword_draw.anim.json", "tensura_ultimates.anim.json",
            // New spell animations (CMD 7012-7032)
            "megiddo_pillar.anim.json", "disintegration_beam.anim.json",
            "gravity_orb.anim.json", "atomic_flare.anim.json", "steam_aura.anim.json",
            "clone_haze.anim.json", "beelzebuth_maw.anim.json", "raphael_halo.anim.json",
            "razor_edge_blade.anim.json", "emperor_earth_spike.anim.json",
            "quake_cracks.anim.json", "saint_fire_cross.anim.json",
            "saint_water_drop.anim.json", "storm_vortex.anim.json",
            "time_warp_clock.anim.json", "armament_gauntlet.anim.json",
            "gear_third_fist.anim.json", "gear_fourth_boundman.anim.json",
            "gomu_pistol_fist.anim.json", "observation_eye.anim.json",
            "voice_waves.anim.json",
            // Existing model animations (need re-extract for the upgrade script output)
            "fireball_orb.anim.json", "chidori_blade.anim.json",
            "rasengan_sphere.anim.json", "rasenshuriken.anim.json",
            "kirin_bolt.anim.json", "sage_aura.anim.json",
            "phoenix_flower.anim.json", "magicule_sword.anim.json",
            "haki_dome.anim.json", "magic_orb.anim.json", "lightning_aura.anim.json",
        };
        for (String name : knownAnims) {
            try (InputStream in = plugin.getResource("animations/" + name)) {
                if (in == null) continue;
                File target = new File(folder, name);
                Files.copy(in, target.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                plugin.getLogger().fine("Could not extract animation " + name + ": " + e.getMessage());
            }
        }
    }

    public @Nullable KeyframeAnimation get(@NotNull String name) {
        return animations.get(name.toLowerCase(Locale.ROOT));
    }

    public int size() { return animations.size(); }
    public Collection<KeyframeAnimation> all() { return animations.values(); }
}
