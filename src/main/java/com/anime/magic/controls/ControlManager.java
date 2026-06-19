package com.anime.magic.controls;

import com.anime.magic.AnimeMagicPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Central manager for all control schemes. Owns the per-player bindings table and
 * the per-scheme per-player state map. Bindings are persisted to bindings.yml.
 */
public final class ControlManager {
    private final AnimeMagicPlugin plugin;
    private final Map<String, ControlScheme> schemes = new LinkedHashMap<>();
    private final Map<UUID, Map<Integer, String>> hotbarBindings = new HashMap<>();
    private final Map<UUID, Map<String, Object>> perPlayerState = new HashMap<>();
    private final Map<UUID, Integer> selectedSlot = new HashMap<>();

    public ControlManager(AnimeMagicPlugin plugin) { this.plugin = plugin; }

    public void register(@NotNull ControlScheme scheme) { schemes.put(scheme.id(), scheme); }
    public @Nullable ControlScheme get(@NotNull String id) { return schemes.get(id); }
    public Collection<ControlScheme> all() { return schemes.values(); }

    public void bindHotbar(@NotNull UUID playerId, int slot, @Nullable String spellId) {
        if (slot < 0 || slot > 8) throw new IllegalArgumentException("slot must be 0-8");
        Map<Integer, String> bindings = hotbarBindings.computeIfAbsent(playerId, k -> new HashMap<>());
        if (spellId == null) bindings.remove(slot);
        else bindings.put(slot, spellId);
    }

    public @Nullable String boundSpell(@NotNull UUID playerId, int slot) {
        Map<Integer, String> bindings = hotbarBindings.get(playerId);
        return bindings == null ? null : bindings.get(slot);
    }

    public @NotNull Map<Integer, String> bindings(@NotNull UUID playerId) {
        return hotbarBindings.getOrDefault(playerId, new HashMap<>());
    }

    public void clearBindings(@NotNull UUID playerId) {
        hotbarBindings.remove(playerId);
        perPlayerState.remove(playerId);
        selectedSlot.remove(playerId);
    }

    @SuppressWarnings("unchecked")
    public <T> @Nullable T state(@NotNull UUID playerId, @NotNull String schemeId) {
        Map<String, Object> states = perPlayerState.get(playerId);
        if (states == null) return null;
        return (T) states.get(schemeId);
    }

    public void state(@NotNull UUID playerId, @NotNull String schemeId, @Nullable Object value) {
        perPlayerState.computeIfAbsent(playerId, k -> new HashMap<>()).put(schemeId, value);
    }

    public int selectedSlot(@NotNull UUID playerId) { return selectedSlot.getOrDefault(playerId, -1); }
    public void selectedSlot(@NotNull UUID playerId, int slot) { selectedSlot.put(playerId, slot); }

    public void onInteract(Player player, org.bukkit.event.player.PlayerInteractEvent e) {
        for (ControlScheme s : schemes.values()) s.onInteract(player, e);
    }
    public void onSlotChange(Player player, org.bukkit.event.player.PlayerItemHeldEvent e) {
        for (ControlScheme s : schemes.values()) s.onSlotChange(player, e);
    }
    public void onSneak(Player player, org.bukkit.event.player.PlayerToggleSneakEvent e) {
        for (ControlScheme s : schemes.values()) s.onSneak(player, e);
    }

    public void tickAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            for (ControlScheme s : schemes.values()) {
                try { s.tick(p); }
                catch (Exception ex) {
                    plugin.getLogger().warning("Control scheme " + s.id()
                            + " threw on tick for " + p.getName() + ": " + ex.getMessage());
                }
            }
        }
    }

    public void load() {
        var file = new File(plugin.getDataFolder(), "bindings.yml");
        if (!file.exists()) return;
        var yaml = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
        for (String key : yaml.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                var section = yaml.getConfigurationSection(key);
                if (section == null) continue;
                Map<Integer, String> bindings = new HashMap<>();
                for (String slotKey : section.getKeys(false)) {
                    try { bindings.put(Integer.parseInt(slotKey), section.getString(slotKey)); }
                    catch (NumberFormatException ignored) {}
                }
                hotbarBindings.put(id, bindings);
            } catch (IllegalArgumentException ignored) {}
        }
    }

    public void save() {
        var file = new File(plugin.getDataFolder(), "bindings.yml");
        var yaml = new org.bukkit.configuration.file.YamlConfiguration();
        for (var entry : hotbarBindings.entrySet()) {
            for (var b : entry.getValue().entrySet()) {
                yaml.set(entry.getKey().toString() + "." + b.getKey(), b.getValue());
            }
        }
        try { yaml.save(file); }
        catch (IOException e) { plugin.getLogger().warning("Could not save bindings.yml: " + e.getMessage()); }
    }
}
