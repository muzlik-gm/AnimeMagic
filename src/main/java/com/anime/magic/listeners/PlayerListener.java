package com.anime.magic.listeners;

import com.anime.magic.AnimeMagicPlugin;
import org.bukkit.Bukkit;
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
        int bonus = plugin.getManaManager().permissionBonus(p.getUniqueId());
        plugin.getManaManager().setMaxBonus(p.getUniqueId(), bonus);
        // Apply default loadout on first join (binds abilities to slot NUMBERS, no items)
        if (plugin.getDefaultBindings() != null) {
            plugin.getDefaultBindings().applyDefaultOnFirstJoin(p);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        plugin.getManaManager().hideBossBar(p.getUniqueId());
        plugin.getParticleEngine().cancelAll(p.getUniqueId());
        plugin.getGuiManager().close(p.getUniqueId());
        if (plugin.getControlManager() != null) {
            var cbc = plugin.getControlManager().get("castbar");
            if (cbc instanceof com.anime.magic.controls.CastBarControl bar) {
                bar.cancel(p.getUniqueId(), true);
            }
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            plugin.getManaManager().saveAll();
            if (plugin.getControlManager() != null) plugin.getControlManager().save();
        });
    }
}
