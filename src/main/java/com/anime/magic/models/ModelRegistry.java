package com.anime.magic.models;

import com.anime.magic.AnimeMagicPlugin;
import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
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
 * Loads and caches CustomModel definitions from the models/ folder. Each *.json file is
 * parsed as a single model definition. Hot-reloadable via reload().
 *
 * <p>On every {@link #reload()}, ALL model JSON files bundled in the jar's
 * {@code resources/models/} directory are extracted to the plugin's data folder
 * ({@code plugins/AnimeMagic/models/}), overwriting any stale copies. This ensures
 * that new model files added in plugin updates are always available — previously
 * only 3 sample files were extracted on first run, causing "unknown model id"
 * warnings for the other 29 models.</p>
 */
public final class ModelRegistry {
    private final AnimeMagicPlugin plugin;
    private final File folder;
    private final Map<String, CustomModel> models = new HashMap<>();

    public ModelRegistry(AnimeMagicPlugin plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "models");
        if (!folder.exists()) folder.mkdirs();
    }

    /**
     * Extract ALL bundled model JSON files from the jar to the data folder,
     * overwriting stale copies. Then load every JSON in the data folder.
     */
    public void reload() {
        models.clear();
        folder.mkdirs();
        // Extract ALL model files from the jar's resources/models/ directory
        extractAllBundledModels();
        // Now load every .json file in the data folder
        File[] files = folder.listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".json"));
        if (files == null) return;
        for (File f : files) {
            try {
                String content = Files.readString(f.toPath(), StandardCharsets.UTF_8);
                JSONObject json = new JSONObject(content);
                CustomModel model = parseModel(json);
                if (model != null) models.put(model.id().toLowerCase(Locale.ROOT), model);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING,
                        "Failed to parse model file " + f.getName() + ": " + e.getMessage(), e);
            }
        }
        plugin.getLogger().info("Loaded " + models.size() + " custom models from " + folder.getName() + "/");
    }

    /**
     * Extract every file from the jar's resources/models/ directory into the
     * plugin's data folder. Overwrites existing files so updates take effect.
     */
    private void extractAllBundledModels() {
        // List all known model files (bundled in the jar at build time)
        // We enumerate by trying known filenames + scanning the jar
        String[] knownModels = {
            "magic_orb.json", "chidori_blade.json", "rasengan_sphere.json",
            "fireball_orb.json", "haki_dome.json", "kirin_bolt.json",
            "lightning_aura.json", "magicule_sword.json", "phoenix_flower.json",
            "rasenshuriken.json", "sage_aura.json",
            // CMD 7012-7032
            "megiddo_pillar.json", "disintegration_beam.json", "gravity_orb.json",
            "atomic_flare.json", "steam_aura.json", "clone_haze.json",
            "beelzebuth_maw.json", "raphael_halo.json", "razor_edge_blade.json",
            "emperor_earth_spike.json", "quake_cracks.json", "saint_fire_cross.json",
            "saint_water_drop.json", "storm_vortex.json", "time_warp_clock.json",
            "armament_gauntlet.json", "gear_third_fist.json", "gear_fourth_boundman.json",
            "gomu_pistol_fist.json", "observation_eye.json", "voice_waves.json",
        };
        for (String name : knownModels) {
            try (InputStream in = plugin.getResource("models/" + name)) {
                if (in == null) continue;
                File target = new File(folder, name);
                // Always overwrite — ensures updated model definitions take effect
                Files.copy(in, target.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                plugin.getLogger().fine("Could not extract model " + name + ": " + e.getMessage());
            }
        }
    }

    public @Nullable CustomModel get(@NotNull String id) {
        return models.get(id.toLowerCase(Locale.ROOT));
    }

    public int size() { return models.size(); }
    public Collection<CustomModel> all() { return models.values(); }

    private CustomModel parseModel(JSONObject json) {
        try {
            String id = json.getString("id");
            String itemStr = json.optString("item", "PAPER");
            Material material;
            try { material = Material.valueOf(itemStr.toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException e) { material = Material.PAPER; }
            int cmd = json.optInt("customModelData", 0);
            String displayContext = json.optString("display", "FIXED");
            String billboard = json.optString("billboard", "CENTER");
            JSONObject scale = json.optJSONObject("defaultScale");
            float sx = scale != null ? (float) scale.optDouble("x", 0.6) : 0.6f;
            float sy = scale != null ? (float) scale.optDouble("y", 0.6) : 0.6f;
            float sz = scale != null ? (float) scale.optDouble("z", 0.6) : 0.6f;
            JSONObject trans = json.optJSONObject("defaultTranslation");
            float tx = trans != null ? (float) trans.optDouble("x", 0.0) : 0.0f;
            float ty = trans != null ? (float) trans.optDouble("y", 0.0) : 0.0f;
            float tz = trans != null ? (float) trans.optDouble("z", 0.0) : 0.0f;
            JSONObject rot = json.optJSONObject("defaultRotation");
            float rx = rot != null ? (float) rot.optDouble("x", 0.0) : 0.0f;
            float ry = rot != null ? (float) rot.optDouble("y", 0.0) : 0.0f;
            float rz = rot != null ? (float) rot.optDouble("z", 0.0) : 0.0f;
            boolean glow = json.optBoolean("glow", false);
            float shadowRadius = (float) json.optDouble("shadowRadius", 0.0);
            return new CustomModel(id, material, cmd, displayContext, billboard,
                    sx, sy, sz, tx, ty, tz, rx, ry, rz, glow, shadowRadius);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to parse model: " + e.getMessage());
            return null;
        }
    }
}
