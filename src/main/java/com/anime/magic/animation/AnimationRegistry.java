package com.anime.magic.animation;

import com.anime.magic.AnimeMagicPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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
 * Loads Blockbench-format .anim.json files from the animations/ folder. Each file may
 * contain multiple named animations. They are registered under both bare name AND
 * file-prefixed name (e.g. "orb_spin" and "orb_spin_animation.orb.spin").
 */
public final class AnimationRegistry {
    private final AnimeMagicPlugin plugin;
    private final File folder;
    private final Map<String, KeyframeAnimation> animations = new HashMap<>();

    public AnimationRegistry(AnimeMagicPlugin plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "animations");
        if (!folder.exists()) {
            folder.mkdirs();
            extractSample("orb_spin.anim.json");
            extractSample("cast_charge.anim.json");
            extractSample("slash_arc.anim.json");
        }
    }

    private void extractSample(String name) {
        try (var in = plugin.getResource("animations/" + name)) {
            if (in == null) return;
            File target = new File(folder, name);
            if (!target.exists()) Files.copy(in, target.toPath());
        } catch (IOException e) {
            plugin.getLogger().warning("Could not extract sample animation " + name + ": " + e.getMessage());
        }
    }

    public void reload() {
        animations.clear();
        if (!folder.exists()) return;
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
                        animations.put(animName.toLowerCase(Locale.ROOT), anim);
                        animations.put((fileBase + "_" + animName).toLowerCase(Locale.ROOT), anim);
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING,
                        "Failed to parse animation file " + f.getName() + ": " + e.getMessage(), e);
            }
        }
        plugin.getLogger().info("Loaded " + animations.size() + " animations from " + folder.getName() + "/");
    }

    public @Nullable KeyframeAnimation get(@NotNull String name) {
        return animations.get(name.toLowerCase(Locale.ROOT));
    }

    public int size() { return animations.size(); }
    public Collection<KeyframeAnimation> all() { return animations.values(); }
}
