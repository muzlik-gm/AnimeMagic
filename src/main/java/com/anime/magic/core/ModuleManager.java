package com.anime.magic.core;

import com.anime.magic.AnimeMagicPlugin;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Central registry for plugin sub-modules. Tracks modules so onDisable() can shut them
 * down in reverse order.
 */
public final class ModuleManager {
    private final AnimeMagicPlugin plugin;
    private final Map<String, Module> modules = new LinkedHashMap<>();

    public ModuleManager(AnimeMagicPlugin plugin) { this.plugin = Objects.requireNonNull(plugin); }

    public void register(String id, Module module) {
        modules.putIfAbsent(id, module);
        module.onEnable(plugin);
    }

    public Module get(String id) { return modules.get(id); }

    public void shutdownAll() {
        var reversed = new java.util.ArrayList<>(modules.keySet());
        java.util.Collections.reverse(reversed);
        for (String k : reversed) modules.get(k).onDisable(plugin);
        modules.clear();
    }

    public interface Module {
        default void onEnable(AnimeMagicPlugin plugin) {}
        default void onDisable(AnimeMagicPlugin plugin) {}
    }
}
