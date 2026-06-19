package com.anime.magic.listeners;

import com.anime.magic.AnimeMagicPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

/** Combat hooks: arena-friendly-fire handling, boss-bar hide on death. */
public final class CombatListener implements Listener {
    private final AnimeMagicPlugin plugin;

    public CombatListener(AnimeMagicPlugin plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPvp(EntityDamageByEntityEvent e) {
        // Arena handles its own combat gating. Hook here for future team-fire logic.
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        plugin.getManaManager().hideBossBar(e.getEntity().getUniqueId());
    }
}
