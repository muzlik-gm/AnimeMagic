package com.anime.magic.mana;

import com.anime.magic.AnimeMagicPlugin;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;

/** Ticks mana regeneration once per second. */
public final class ManaRegenTask extends BukkitRunnable {
    private final AnimeMagicPlugin plugin;

    public ManaRegenTask(AnimeMagicPlugin plugin) { this.plugin = plugin; }

    @Override
    public void run() {
        double rate = plugin.getConfig().getDouble("mana.regen-per-second", 2.0);
        if (rate <= 0) return;
        Bukkit.getOnlinePlayers().forEach(p -> {
            var mm = plugin.getManaManager();
            int cur = mm.current(p.getUniqueId());
            int max = mm.max(p.getUniqueId());
            if (cur < max) mm.add(p.getUniqueId(), (int) Math.ceil(rate));
        });
    }
}
