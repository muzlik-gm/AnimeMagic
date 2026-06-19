package com.anime.magic.listeners;

import com.anime.magic.AnimeMagicPlugin;
import com.anime.magic.api.CastingService;
import com.anime.magic.api.Spell;
import com.anime.magic.gui.SpellSelectionGUI;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/** Routes spellbook clicks to the appropriate actions (cast, filter, page nav, close). */
public final class GUIListener implements Listener {
    private final AnimeMagicPlugin plugin;

    public GUIListener(AnimeMagicPlugin plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() == null) return;
        if (!(e.getInventory().getHolder() instanceof SpellSelectionGUI gui)) return;

        e.setCancelled(true);
        ItemStack item = e.getCurrentItem();
        if (item == null || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String action = pdc.get(plugin.getGuiManager().actionKey(), PersistentDataType.STRING);
        if (action == null) return;

        switch (action) {
            case "close" -> {
                p.closeInventory();
                plugin.getGuiManager().close(p.getUniqueId());
            }
            case "next" -> gui.openPage(gui.currentPage() + 1, null, null);
            case "prev" -> gui.openPage(gui.currentPage() - 1, null, null);
            default -> {
                if (action.startsWith("filter:")) {
                    String f = action.substring("filter:".length());
                    Spell.SchoolId filter = f.equals("all") ? null : Spell.SchoolId.valueOf(f.toUpperCase());
                    gui.openPage(0, filter, null);
                } else if (action.startsWith("cast:")) {
                    String spellId = action.substring("cast:".length());
                    Spell spell = plugin.getSpellRegistry().get(spellId);
                    if (spell == null) return;
                    p.closeInventory();
                    plugin.getGuiManager().close(p.getUniqueId());
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        CastingService caster = new CastingService(plugin);
                        caster.cast(p, spell);
                    });
                }
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        plugin.getGuiManager().close(p.getUniqueId());
    }
}
