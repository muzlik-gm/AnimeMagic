package com.anime.magic.models;

import com.anime.magic.AnimeMagicPlugin;
import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
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
 */
public final class ModelRegistry {
    private final AnimeMagicPlugin plugin;
    private final File folder;
    private final Map<String, CustomModel> models = new HashMap<>();

    public ModelRegistry(AnimeMagicPlugin plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "models");
        if (!folder.exists()) {
            folder.mkdirs();
            copySampleIfPresent("magic_orb.json");
            copySampleIfPresent("chidori_blade.json");
            copySampleIfPresent("rasengan_sphere.json");
        }
    }

    private void copySampleIfPresent(String name) {
        try (var in = plugin.getResource("models/" + name)) {
            if (in == null) return;
            File target = new File(folder, name);
            if (!target.exists()) Files.copy(in, target.toPath());
        } catch (IOException e) {
            plugin.getLogger().warning("Could not extract sample model " + name + ": " + e.getMessage());
        }
    }

    public void reload() {
        models.clear();
        if (!folder.exists()) return;
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

    public @Nullable CustomModel get(@NotNull String id) {
        return models.get(id.toLowerCase(Locale.ROOT));
    }

    public Collection<CustomModel> all() { return models.values(); }
    public int size() { return models.size(); }

    private @Nullable CustomModel parseModel(JSONObject json) {
        String id = json.optString("id", null);
        if (id == null) return null;
        String matName = json.optString("item", "PAPER").toUpperCase(Locale.ROOT);
        Material material;
        try { material = Material.valueOf(matName); }
        catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Model " + id + " references unknown material " + matName + " — defaulting to PAPER");
            material = Material.PAPER;
        }
        int cmd = json.optInt("customModelData", 0);
        String display = json.optString("display", "FIXED");
        String billboard = json.optString("billboard", "CENTER");

        float[] scale = readVec3(json.opt("defaultScale"), 1f, 1f, 1f);
        float[] trans = readVec3(json.opt("defaultTranslation"), 0f, 0f, 0f);
        float[] rot = readVec3(json.opt("defaultRotation"), 0f, 0f, 0f);
        boolean glow = json.optBoolean("glow", false);
        float shadow = (float) json.optDouble("shadowRadius", 0.0);

        return new CustomModel(id, material, cmd, display, billboard,
                scale[0], scale[1], scale[2], trans[0], trans[1], trans[2],
                rot[0], rot[1], rot[2], glow, shadow);
    }

    private static float[] readVec3(Object obj, float dx, float dy, float dz) {
        if (obj == null) return new float[]{dx, dy, dz};
        if (obj instanceof JSONObject jo) {
            return new float[]{
                    (float) jo.optDouble("x", dx),
                    (float) jo.optDouble("y", dy),
                    (float) jo.optDouble("z", dz)
            };
        }
        if (obj instanceof JSONArray arr) {
            return new float[]{
                    (float) arr.optDouble(0, dx),
                    (float) arr.optDouble(1, dy),
                    (float) arr.optDouble(2, dz)
            };
        }
        return new float[]{dx, dy, dz};
    }
}
