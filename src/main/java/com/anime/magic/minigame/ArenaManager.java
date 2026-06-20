package com.anime.magic.minigame;

import com.anime.magic.AnimeMagicPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns all MagicArena instances, persists them to arenas.yml, and tracks which arena
 * each online player is currently in (for routing death/quit events back to the arena).
 */
public final class ArenaManager implements Listener {
    private final AnimeMagicPlugin plugin;
    private final File file;
    private final YamlConfiguration yaml;
    private final Map<String, MagicArena> arenas = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerToArena = new ConcurrentHashMap<>();

    public ArenaManager(AnimeMagicPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "arenas.yml");
        if (!file.exists()) { try { file.createNewFile(); } catch (IOException ignored) {} }
        this.yaml = YamlConfiguration.loadConfiguration(file);
    }

    public void load() {
        for (String key : yaml.getKeys(false)) {
            ConfigurationSection s = yaml.getConfigurationSection(key);
            if (s == null) continue;
            Location spawn = deserializeLoc(s.getString("spawn"));
            Location lobby = deserializeLoc(s.getString("lobby"));
            arenas.put(key, new MagicArena(plugin, this, key, spawn, lobby));
        }
    }

    public void save() {
        for (var e : arenas.entrySet()) {
            MagicArena a = e.getValue();
            yaml.set(e.getKey() + ".spawn", serializeLoc(a.spawn()));
            yaml.set(e.getKey() + ".lobby", serializeLoc(a.lobby()));
        }
        try { yaml.save(file); }
        catch (IOException ex) { plugin.getLogger().warning("Could not save arenas.yml: " + ex.getMessage()); }
    }

    public @Nullable MagicArena arena(@NotNull String name) { return arenas.get(name.toLowerCase()); }
    public @Nullable MagicArena arenaOf(@NotNull UUID playerId) {
        String name = playerToArena.get(playerId);
        return name == null ? null : arenas.get(name);
    }
    public Collection<MagicArena> all() { return arenas.values(); }

    public MagicArena create(@NotNull String name, @NotNull Location spawn, @Nullable Location lobby) {
        MagicArena arena = new MagicArena(plugin, this, name.toLowerCase(), spawn, lobby);
        arenas.put(name.toLowerCase(), arena);
        save();
        return arena;
    }

    public boolean delete(@NotNull String name) {
        MagicArena removed = arenas.remove(name.toLowerCase());
        if (removed == null) return false;
        // Stop the arena (teleports players back, strips potion effects, cancels tickers)
        // before removing it. Otherwise players are stranded at the arena spawn with
        // no way to /arena leave (arenaOf returns null after removal).
        removed.stop();
        // Drop any stragglers from the playerToArena index.
        playerToArena.entrySet().removeIf(e -> e.getValue().equals(name.toLowerCase()));
        yaml.set(name.toLowerCase(), null);
        save();
        return true;
    }

    public boolean join(@NotNull String name, @NotNull Player p) {
        MagicArena a = arena(name);
        if (a == null) return false;
        boolean ok = a.join(p);
        if (ok) playerToArena.put(p.getUniqueId(), name.toLowerCase());
        return ok;
    }

    public void leave(@NotNull Player p) {
        MagicArena a = arenaOf(p.getUniqueId());
        if (a != null) {
            a.leave(p);
            playerToArena.remove(p.getUniqueId());
        }
    }

    public void stopAll() {
        for (MagicArena a : arenas.values()) a.stop();
        playerToArena.clear();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent e) {
        MagicArena a = arenaOf(e.getEntity().getUniqueId());
        if (a == null || a.state() != GameState.RUNNING) return;
        // Verify the victim is also in this arena — avoids counting cross-world damage.
        if (!a.players().contains(e.getEntity().getUniqueId())) return;
        // Mark the victim as eliminated so the alive-count check excludes them
        // even after they respawn (default isDead() returns false post-respawn).
        a.eliminate(e.getEntity().getUniqueId());
        Player killer = e.getEntity().getKiller();
        if (killer != null && a.players().contains(killer.getUniqueId())) {
            a.recordKill(killer.getUniqueId());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        MagicArena a = arenaOf(e.getPlayer().getUniqueId());
        if (a != null) {
            a.leave(e.getPlayer());
            playerToArena.remove(e.getPlayer().getUniqueId());
        }
    }

    /**
     * Called from PlayerListener.onJoin to teleport a player back to their saved
     * pre-arena location if they disconnected mid-match. Without this, they would
     * relog at the arena spawn (stranded).
     */
    public boolean tryRestore(@NotNull Player p) {
        for (MagicArena a : arenas.values()) {
            Location prev = a.previousLocation(p.getUniqueId());
            if (prev != null) {
                a.consumePreviousLocation(p.getUniqueId());
                if (plugin.getConfig().getBoolean("arena.teleport-back", true)) {
                    p.teleport(prev);
                }
                return true;
            }
        }
        return false;
    }

    private static String serializeLoc(Location l) {
        if (l == null || l.getWorld() == null) return null;
        return l.getWorld().getName() + "," + l.getX() + "," + l.getY() + "," + l.getZ()
                + "," + l.getYaw() + "," + l.getPitch();
    }

    private static Location deserializeLoc(String s) {
        if (s == null || s.isEmpty()) return null;
        String[] parts = s.split(",");
        if (parts.length < 6) return null;
        var world = Bukkit.getWorld(parts[0]);
        if (world == null) return null;
        return new Location(world,
                Double.parseDouble(parts[1]), Double.parseDouble(parts[2]), Double.parseDouble(parts[3]),
                Float.parseFloat(parts[4]), Float.parseFloat(parts[5]));
    }
}
