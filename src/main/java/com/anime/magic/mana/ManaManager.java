package com.anime.magic.mana;

import com.anime.magic.AnimeMagicPlugin;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks per-player mana, cooldown timestamps, and learned spells. Backed by mana.yml.
 * Boss bar shown when mana.show-bossbar=true and player is online.
 */
public final class ManaManager {
    private final AnimeMagicPlugin plugin;
    private final File file;
    private final YamlConfiguration yaml;

    private final Map<UUID, Integer> current = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> maxBonus = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Long>> spellCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> globalCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, BossBar> bossBars = new ConcurrentHashMap<>();

    public ManaManager(AnimeMagicPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "mana.yml");
        if (!file.exists()) {
            try { file.createNewFile(); } catch (IOException ignored) {}
        }
        this.yaml = YamlConfiguration.loadConfiguration(file);
    }

    public void loadAll() {
        for (String key : yaml.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                current.put(id, yaml.getInt(key + ".current", plugin.getConfig().getInt("mana.base-max")));
                maxBonus.put(id, yaml.getInt(key + ".maxBonus", 0));
            } catch (IllegalArgumentException ignored) {}
        }
    }

    public void saveAll() {
        for (var e : current.entrySet()) {
            yaml.set(e.getKey() + ".current", e.getValue());
            yaml.set(e.getKey() + ".maxBonus", maxBonus.getOrDefault(e.getKey(), 0));
        }
        try { yaml.save(file); }
        catch (IOException ex) { plugin.getLogger().warning("Could not save mana.yml: " + ex.getMessage()); }
    }

    public void load(UUID id) {
        if (current.containsKey(id)) return;
        int base = plugin.getConfig().getInt("mana.base-max");
        current.put(id, yaml.getInt(id.toString() + ".current", base));
        maxBonus.put(id, yaml.getInt(id.toString() + ".maxBonus", 0));
    }

    public int current(@NotNull UUID id) {
        load(id);
        return current.getOrDefault(id, plugin.getConfig().getInt("mana.base-max"));
    }

    public int max(@NotNull UUID id) {
        load(id);
        int base = plugin.getConfig().getInt("mana.base-max");
        int bonus = maxBonus.getOrDefault(id, permissionBonus(id));
        return base + bonus;
    }

    public boolean hasEnough(@NotNull UUID id, int amount) { return current(id) >= amount; }

    public void consume(@NotNull UUID id, int amount) {
        load(id);
        current.put(id, Math.max(0, current(id) - amount));
        updateBossBar(id);
    }

    public void add(@NotNull UUID id, int amount) {
        load(id);
        int m = max(id);
        current.put(id, Math.min(m, current(id) + amount));
        updateBossBar(id);
    }

    public void set(@NotNull UUID id, int value) {
        load(id);
        int m = max(id);
        current.put(id, Math.max(0, Math.min(m, value)));
        updateBossBar(id);
    }

    public void setMaxBonus(@NotNull UUID id, int bonus) { maxBonus.put(id, Math.max(0, bonus)); }

    public int permissionBonus(@NotNull UUID id) {
        Player p = Bukkit.getPlayer(id);
        if (p == null) return 0;
        var section = plugin.getConfig().getConfigurationSection("mana.permission-bonus");
        if (section == null) return 0;
        int total = 0;
        for (String perm : section.getKeys(false)) {
            if (p.hasPermission(perm)) total = Math.max(total, section.getInt(perm, 0));
        }
        return total;
    }

    public @Nullable Long lastGlobalCast(@NotNull UUID id) { return globalCooldowns.get(id); }

    public @Nullable Long lastSpellCast(@NotNull UUID id, @NotNull String spellId) {
        Map<String, Long> m = spellCooldowns.get(id);
        return m == null ? null : m.get(spellId.toLowerCase());
    }

    public void recordCast(@NotNull UUID id, @NotNull String spellId, long timestamp) {
        globalCooldowns.put(id, timestamp);
        spellCooldowns.computeIfAbsent(id, k -> new HashMap<>()).put(spellId.toLowerCase(), timestamp);
    }

    public void showBossBar(@NotNull Player player) {
        if (!plugin.getConfig().getBoolean("mana.show-bossbar", true)) return;
        BossBar bar = bossBars.get(player.getUniqueId());
        if (bar == null) {
            BarColor color;
            try { color = BarColor.valueOf(plugin.getConfig().getString("mana.bossbar-color", "PURPLE")); }
            catch (IllegalArgumentException e) { color = BarColor.PURPLE; }
            BarStyle style;
            try { style = BarStyle.valueOf(plugin.getConfig().getString("mana.bossbar-style", "SEGMENTED_20")); }
            catch (IllegalArgumentException e) { style = BarStyle.SEGMENTED_20; }
            bar = Bukkit.createBossBar(
                    plugin.getMessages().format("mana.bossbar-title",
                            "%current%", String.valueOf(current(player.getUniqueId())),
                            "%max%", String.valueOf(max(player.getUniqueId()))),
                    color, style);
            bar.addPlayer(player);
            bossBars.put(player.getUniqueId(), bar);
        }
        updateBossBar(player.getUniqueId());
        bar.setVisible(true);
    }

    public void hideBossBar(@NotNull UUID id) {
        BossBar bar = bossBars.remove(id);
        if (bar != null) bar.removeAll();
    }

    private void updateBossBar(@NotNull UUID id) {
        BossBar bar = bossBars.get(id);
        if (bar == null) return;
        int cur = current(id);
        int m = max(id);
        bar.setProgress(m == 0 ? 0 : Math.min(1.0, (double) cur / m));
        bar.setTitle(plugin.getMessages().format("mana.bossbar-title",
                "%current%", String.valueOf(cur), "%max%", String.valueOf(m)));
    }
}
