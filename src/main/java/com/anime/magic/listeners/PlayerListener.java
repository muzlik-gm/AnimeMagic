package com.anime.magic.listeners;

import com.anime.magic.AnimeMagicPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Player lifecycle: load mana on join, save on quit, cancel particle animations. */
public final class PlayerListener implements Listener {
    private final AnimeMagicPlugin plugin;

    public PlayerListener(AnimeMagicPlugin plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.LOW)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        plugin.getManaManager().load(p.getUniqueId());
        // Re-evaluate the player's max mana bonus from permissions and clamp
        // their current mana to the new max — so config changes to mana.base-max
        // (or to permission nodes) take effect immediately on (re)join without
        // requiring a server restart.
        plugin.getManaManager().recalculateMax(p.getUniqueId());
        // Apply default loadout on first join (binds abilities to slot NUMBERS, no items)
        if (plugin.getDefaultBindings() != null) {
            plugin.getDefaultBindings().applyDefaultOnFirstJoin(p);
        }
        // Restore a player who disconnected mid-arena-match back to their pre-join location.
        if (plugin.getArenaManager() != null) {
            plugin.getArenaManager().tryRestore(p);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        plugin.getManaManager().hideBossBar(p.getUniqueId());
        plugin.getManaManager().cleanup(p.getUniqueId());
        plugin.getParticleEngine().cancelAll(p.getUniqueId());
        plugin.getGuiManager().close(p.getUniqueId());
        if (plugin.getControlManager() != null) {
            var cbc = plugin.getControlManager().get("castbar");
            if (cbc instanceof com.anime.magic.controls.CastBarControl bar) {
                bar.cancel(p.getUniqueId(), true);
            }
        }
        // saveAll() builds a fresh snapshot (synchronized) — safe to call async.
        // controlManager.save() touches its own yaml field — keep on main thread
        // to be conservative (control bindings are small and quit is rare).
        plugin.getManaManager().saveAll();
        if (plugin.getControlManager() != null) plugin.getControlManager().save();
    }
}
