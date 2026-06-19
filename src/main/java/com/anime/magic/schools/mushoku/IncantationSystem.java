package com.anime.magic.schools.mushoku;

import com.anime.magic.AnimeMagicPlugin;
import com.anime.magic.api.Spell;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages multi-line incantations for Mushoku-school spells. When a player casts a
 * Mushoku spell with incantation lines defined, the spell calls begin() to enqueue
 * the player. The player must then type each line in chat (case-insensitive, trimmed).
 * On success the spell's onComplete callback runs on the main thread. On timeout or
 * wrong line, the cast is aborted with no mana refund.
 */
public final class IncantationSystem implements Listener {
    private final AnimeMagicPlugin plugin;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private static final long TIMEOUT_MS = 10_000;

    public IncantationSystem(AnimeMagicPlugin plugin) { this.plugin = plugin; }

    public boolean begin(@NotNull Player caster, @NotNull Spell spell, @NotNull Runnable onComplete) {
        if (sessions.containsKey(caster.getUniqueId())) return false;
        List<String> lines = spell.incantation();
        if (lines.isEmpty()) {
            onComplete.run();
            return true;
        }
        Session s = new Session(spell, lines, onComplete, System.currentTimeMillis() + TIMEOUT_MS);
        sessions.put(caster.getUniqueId(), s);
        plugin.getMessages().send(caster, "incantation.start");
        prompt(caster, s);
        new BukkitRunnable() {
            @Override public void run() {
                Session cur = sessions.get(caster.getUniqueId());
                if (cur == s && System.currentTimeMillis() > cur.deadline) {
                    sessions.remove(caster.getUniqueId());
                    plugin.getMessages().send(caster, "incantation.timeout");
                }
            }
        }.runTaskLater(plugin, TIMEOUT_MS / 50 + 1);
        return true;
    }

    private void prompt(Player p, Session s) {
        plugin.getMessages().send(p, "incantation.line-prompt", "%line%", s.lines.get(s.currentIdx));
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent e) {
        Session s = sessions.get(e.getPlayer().getUniqueId());
        if (s == null) return;
        e.setCancelled(true);
        String typed = e.getMessage().trim();
        String expected = s.lines.get(s.currentIdx).trim();
        if (typed.equalsIgnoreCase(expected)) {
            plugin.getMessages().send(e.getPlayer(), "incantation.line-correct");
            s.currentIdx++;
            if (s.currentIdx >= s.lines.size()) {
                sessions.remove(e.getPlayer().getUniqueId());
                plugin.getMessages().send(e.getPlayer(), "incantation.complete");
                Bukkit.getScheduler().runTask(plugin, s.onComplete);
            } else {
                prompt(e.getPlayer(), s);
            }
        } else {
            plugin.getMessages().send(e.getPlayer(), "incantation.line-wrong");
            prompt(e.getPlayer(), s);
        }
    }

    public @Nullable Session current(UUID id) { return sessions.get(id); }

    static final class Session {
        final Spell spell;
        final List<String> lines;
        final Runnable onComplete;
        final long deadline;
        int currentIdx = 0;
        Session(Spell spell, List<String> lines, Runnable onComplete, long deadline) {
            this.spell = spell; this.lines = lines; this.onComplete = onComplete; this.deadline = deadline;
        }
    }
}
