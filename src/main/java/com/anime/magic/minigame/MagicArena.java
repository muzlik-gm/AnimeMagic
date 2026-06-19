package com.anime.magic.minigame;

import com.anime.magic.AnimeMagicPlugin;
import com.anime.magic.effects.SphereAnimation;
import com.anime.magic.util.LocationUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * A single magical-duel arena instance. Tracks state, players, and the active match
 * timer. State transitions are driven by ArenaManager via start()/stop().
 */
public final class MagicArena {
    private final AnimeMagicPlugin plugin;
    private final ArenaManager manager;
    private final String name;
    private Location spawn;
    private Location lobby;
    private GameState state = GameState.IDLE;
    private final Set<UUID> players = new LinkedHashSet<>();
    private final Map<UUID, Location> previousLocations = new HashMap<>();
    private final Map<UUID, Integer> kills = new HashMap<>();
    private int matchSecondsLeft;
    private int suddenDeathSecondsLeft;
    private boolean suddenDeath;
    private BukkitRunnable ticker;

    public MagicArena(AnimeMagicPlugin plugin, ArenaManager manager, String name, Location spawn, Location lobby) {
        this.plugin = plugin; this.manager = manager; this.name = name;
        this.spawn = spawn; this.lobby = lobby;
    }

    public String name() { return name; }
    public Location spawn() { return spawn; }
    public Location lobby() { return lobby; }
    public GameState state() { return state; }
    public Set<UUID> players() { return Collections.unmodifiableSet(players); }
    public int playerCount() { return players.size(); }
    public int maxPlayers() { return plugin.getConfig().getInt("arena.max-players", 8); }
    public int minPlayers() { return plugin.getConfig().getInt("arena.min-players", 2); }

    public void setSpawn(Location spawn) { this.spawn = spawn; }
    public void setLobby(Location lobby) { this.lobby = lobby; }

    public boolean join(@NotNull Player p) {
        if (state != GameState.IDLE && state != GameState.COUNTDOWN) return false;
        if (players.size() >= maxPlayers()) return false;
        if (manager.arenaOf(p.getUniqueId()) != null) return false;
        players.add(p.getUniqueId());
        previousLocations.put(p.getUniqueId(), p.getLocation());
        if (lobby != null) p.teleport(lobby);
        broadcast("arena.joined", "%name%", name, "%cur%", String.valueOf(players.size()),
                "%max%", String.valueOf(maxPlayers()));
        if (players.size() >= minPlayers() && state == GameState.IDLE) startCountdown();
        return true;
    }

    public void leave(@NotNull Player p) {
        if (!players.remove(p.getUniqueId())) return;
        Location prev = previousLocations.remove(p.getUniqueId());
        if (plugin.getConfig().getBoolean("arena.teleport-back", true) && prev != null) p.teleport(prev);
        kills.remove(p.getUniqueId());
        broadcast("arena.left");
        if (players.size() < minPlayers() && state == GameState.COUNTDOWN) {
            state = GameState.IDLE;
            if (ticker != null) ticker.cancel();
            broadcast("arena.not-enough");
        }
    }

    public void startCountdown() {
        state = GameState.COUNTDOWN;
        int countdown = 10;
        new BukkitRunnable() {
            int left = countdown;
            @Override public void run() {
                if (players.size() < minPlayers()) { state = GameState.IDLE; cancel(); return; }
                if (left <= 0) { start(); cancel(); return; }
                broadcast("arena.starting", "%name%", name, "%sec%", String.valueOf(left));
                left--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    public void start() {
        state = GameState.RUNNING;
        matchSecondsLeft = plugin.getConfig().getInt("arena.match-duration-seconds", 300);
        suddenDeathSecondsLeft = plugin.getConfig().getInt("arena.sudden-death-seconds", 60);
        suddenDeath = false;
        for (UUID id : players) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && spawn != null) {
                p.teleport(spawn);
                plugin.getManaManager().set(id, plugin.getManaManager().max(id));
            }
            kills.put(id, 0);
        }
        broadcast("arena.started");
        runTicker();
    }

    public void stop() {
        if (ticker != null) ticker.cancel();
        state = GameState.ENDED;
        end(null);
    }

    private void runTicker() {
        ticker = new BukkitRunnable() {
            @Override public void run() {
                if (state != GameState.RUNNING) { cancel(); return; }
                matchSecondsLeft--;
                if (matchSecondsLeft <= 0) {
                    if (!suddenDeath) {
                        suddenDeath = true;
                        broadcast("§c=== SUDDEN DEATH ===");
                        matchSecondsLeft = suddenDeathSecondsLeft;
                        for (UUID id : players) {
                            Player p = Bukkit.getPlayer(id);
                            if (p != null) p.addPotionEffect(
                                    new PotionEffect(PotionEffectType.STRENGTH, 99999, 1));
                        }
                    } else {
                        end(null);
                        cancel();
                        return;
                    }
                }
                long alive = players.stream().filter(id -> {
                    Player p = Bukkit.getPlayer(id);
                    return p != null && !p.isDead() && p.getHealth() > 0;
                }).count();
                if (alive <= 1) {
                    UUID winner = players.stream()
                            .filter(id -> { Player p = Bukkit.getPlayer(id); return p != null && p.getHealth() > 0; })
                            .findFirst().orElse(null);
                    end(winner);
                    cancel();
                }
            }
        };
        ticker.runTaskTimer(plugin, 20L, 20L);
    }

    private void end(@Nullable UUID winner) {
        state = GameState.ENDED;
        if (ticker != null) ticker.cancel();
        if (spawn != null) {
            plugin.getParticleEngine().play(new SphereAnimation(plugin,
                    winner == null ? null : Bukkit.getPlayer(winner),
                    spawn, Particle.END_ROD, 30, 1.0, 8.0, 100));
        }
        LocationUtil.sound(spawn, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.5f, 1.0f);

        if (winner != null) {
            Player w = Bukkit.getPlayer(winner);
            String name = w != null ? w.getName() : "Unknown";
            broadcast("arena.ended", "%name%", this.name, "%winner%", name);
            String cmd = plugin.getConfig().getString("arena.reward-command", "");
            if (!cmd.isEmpty() && w != null) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("%player%", w.getName()));
            }
        } else {
            broadcast("arena.draw", "%name%", this.name);
        }

        for (UUID id : players) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) continue;
            p.removePotionEffect(PotionEffectType.STRENGTH);
            Location prev = previousLocations.get(id);
            if (plugin.getConfig().getBoolean("arena.teleport-back", true) && prev != null) p.teleport(prev);
        }
        previousLocations.clear();
        players.clear();
        kills.clear();
        state = GameState.IDLE;
        manager.save();
    }

    public void recordKill(UUID killer) { kills.merge(killer, 1, Integer::sum); }
    public int kills(UUID id) { return kills.getOrDefault(id, 0); }

    void broadcast(String key, Object... kv) {
        for (UUID id : players) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) plugin.getMessages().send(p, key, kv);
        }
    }
}
