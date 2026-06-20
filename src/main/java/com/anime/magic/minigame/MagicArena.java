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
 *
 * <p>Thread-safety: main-thread only. Both the countdown and match tickers are
 * tracked and cancelled on stop()/leave()/re-startCountdown() to prevent the
 * stale-runnable race where an old ticker fires {@code start()} after the arena
 * was supposed to stop.</p>
 */
public final class MagicArena {
    private final AnimeMagicPlugin plugin;
    private final ArenaManager manager;
    private final String name;
    private Location spawn;
    private Location lobby;
    private GameState state = GameState.IDLE;
    private final Set<UUID> players = new LinkedHashSet<>();
    /** Players who died during the current RUNNING match — excluded from alive count. */
    private final Set<UUID> eliminated = new HashSet<>();
    private final Map<UUID, Location> previousLocations = new HashMap<>();
    private final Map<UUID, Integer> kills = new HashMap<>();
    private int matchSecondsLeft;
    private int suddenDeathSecondsLeft;
    private boolean suddenDeath;
    private BukkitRunnable ticker;
    private BukkitRunnable countdownTask;

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
        eliminated.remove(p.getUniqueId());
        // Strip any potion effects granted by the arena (sudden-death Strength)
        // so leavers don't carry a permanent combat buff into the main world.
        stripArenaBuffs(p);
        Location prev = previousLocations.remove(p.getUniqueId());
        if (plugin.getConfig().getBoolean("arena.teleport-back", true) && prev != null) p.teleport(prev);
        kills.remove(p.getUniqueId());
        broadcast("arena.left");
        if (players.size() < minPlayers() && state == GameState.COUNTDOWN) {
            state = GameState.IDLE;
            if (countdownTask != null) { countdownTask.cancel(); countdownTask = null; }
            if (ticker != null) { ticker.cancel(); ticker = null; }
            broadcast("arena.not-enough");
        }
    }

    public void startCountdown() {
        // Defensively cancel any previous countdown task to prevent two countdowns
        // racing each other to call start().
        if (countdownTask != null) { countdownTask.cancel(); countdownTask = null; }
        state = GameState.COUNTDOWN;
        int countdown = 10;
        countdownTask = new BukkitRunnable() {
            int left = countdown;
            @Override public void run() {
                if (players.size() < minPlayers()) { state = GameState.IDLE; cancel(); countdownTask = null; return; }
                if (left <= 0) { start(); cancel(); countdownTask = null; return; }
                broadcast("arena.starting", "%name%", name, "%sec%", String.valueOf(left));
                left--;
            }
        };
        countdownTask.runTaskTimer(plugin, 0L, 20L);
    }

    public void start() {
        state = GameState.RUNNING;
        eliminated.clear();
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
        if (countdownTask != null) { countdownTask.cancel(); countdownTask = null; }
        if (ticker != null) { ticker.cancel(); ticker = null; }
        state = GameState.ENDED;
        end(null);
    }

    private void runTicker() {
        if (ticker != null) ticker.cancel();
        ticker = new BukkitRunnable() {
            @Override public void run() {
                if (state != GameState.RUNNING) { cancel(); ticker = null; return; }
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
                        ticker = null;
                        return;
                    }
                }
                long alive = players.stream().filter(id -> !eliminated.contains(id) && isAlive(id)).count();
                if (alive <= 1) {
                    UUID winner = players.stream()
                            .filter(id -> !eliminated.contains(id) && isAlive(id))
                            .findFirst().orElse(null);
                    end(winner);
                    cancel();
                    ticker = null;
                }
            }
        };
        ticker.runTaskTimer(plugin, 20L, 20L);
    }

    private boolean isAlive(UUID id) {
        Player p = Bukkit.getPlayer(id);
        return p != null && !p.isDead() && p.getHealth() > 0;
    }

    /** Mark a player as eliminated (called on PlayerDeathEvent by ArenaManager). */
    public void eliminate(@NotNull UUID id) {
        if (players.contains(id)) eliminated.add(id);
    }

    private void end(@Nullable UUID winner) {
        state = GameState.ENDED;
        if (ticker != null) { ticker.cancel(); ticker = null; }
        if (countdownTask != null) { countdownTask.cancel(); countdownTask = null; }
        if (spawn != null) {
            plugin.getParticleEngine().play(new SphereAnimation(plugin,
                    winner == null ? null : Bukkit.getPlayer(winner),
                    spawn, Particle.END_ROD, 30, 1.0, 8.0, 100));
            LocationUtil.sound(spawn, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.5f, 1.0f);
        }

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
            if (p == null) continue; // offline players keep their previousLocations (see below)
            stripArenaBuffs(p);
            Location prev = previousLocations.get(id);
            if (plugin.getConfig().getBoolean("arena.teleport-back", true) && prev != null) p.teleport(prev);
        }
        // Remove previousLocations only for online players we successfully teleported.
        // Offline players' entries are retained so ArenaManager can restore them on
        // next join via PlayerListener.onJoin → arenaManager.tryRestore(uuid).
        previousLocations.keySet().removeIf(id -> Bukkit.getPlayer(id) != null);
        players.clear();
        eliminated.clear();
        kills.clear();
        state = GameState.IDLE;
    }

    /** Strip all potion effects the arena might have granted. */
    private void stripArenaBuffs(Player p) {
        p.removePotionEffect(PotionEffectType.STRENGTH);
    }

    public void recordKill(UUID killer) { kills.merge(killer, 1, Integer::sum); }
    public int kills(UUID id) { return kills.getOrDefault(id, 0); }

    /** @return the saved pre-join location for a player who quit mid-match (for restore on rejoin). */
    public @Nullable Location previousLocation(@NotNull UUID id) { return previousLocations.get(id); }

    /** Consume the saved pre-join location (called by ArenaManager after restoring the player). */
    public void consumePreviousLocation(@NotNull UUID id) { previousLocations.remove(id); }

    void broadcast(String key, Object... kv) {
        for (UUID id : players) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) plugin.getMessages().send(p, key, kv);
        }
    }
}
