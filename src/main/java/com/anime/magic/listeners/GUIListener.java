package com.anime.magic.listeners;

import com.anime.magic.AnimeMagicPlugin;
import com.anime.magic.api.CastingService;
import com.anime.magic.api.Spell;
import com.anime.magic.gui.MasteryGUI;
import com.anime.magic.gui.SchoolSelectorGUI;
import com.anime.magic.gui.SpellSelectionGUI;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Routes clicks for ALL custom GUIs:
 * - SpellSelectionGUI (spellbook) — cast, filter, page nav, close
 * - SchoolSelectorGUI (/school) — select school loadout, close
 * - MasteryGUI (/school mastery) — cast spell, switch school tab, close
 *
 * ALL clicks in ALL custom GUIs are cancelled to prevent item theft/movement.
 */
public final class GUIListener implements Listener {
    private final AnimeMagicPlugin plugin;

    public GUIListener(AnimeMagicPlugin plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;

        Object holder = e.getInventory().getHolder();

        // ─── SpellSelectionGUI (spellbook) ───────────────────────────────
        if (holder instanceof SpellSelectionGUI gui) {
            e.setCancelled(true);
            if (e.getClickedInventory() == null || !e.getClickedInventory().equals(gui.getInventory())) return;
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
            return;
        }

        // ─── SchoolSelectorGUI (/school) ─────────────────────────────────
        if (holder instanceof SchoolSelectorGUI gui) {
            e.setCancelled(true);
            if (e.getClickedInventory() == null || !e.getClickedInventory().equals(gui.getInventory())) return;

            int slot = e.getRawSlot();
            Spell.SchoolId school = gui.schoolAt(slot);
            if (school != null) {
                // Apply the school's default loadout
                plugin.getDefaultBindings().applyLoadout(p, school);
                String nameKey = "school." + school.configKey() + ".name";
                plugin.getMessages().send(p, "school.switched",
                        "%school%", plugin.getMessages().raw(nameKey));
                p.closeInventory();
                return;
            }
            // Close button (slot 22)
            if (slot == 22) {
                p.closeInventory();
                return;
            }
            return;
        }

        // ─── MasteryGUI (/school mastery) ────────────────────────────────
        if (holder instanceof MasteryGUI gui) {
            e.setCancelled(true);
            if (e.getClickedInventory() == null || !e.getClickedInventory().equals(gui.getInventory())) return;

            int slot = e.getRawSlot();

            // Close button (slot 53)
            if (gui.isCloseButton(slot)) {
                p.closeInventory();
                return;
            }

            // School filter tab (slots 45-48)
            Spell.SchoolId filterSchool = gui.schoolFilterAt(slot);
            if (filterSchool != null) {
                gui.open(filterSchool);
                return;
            }

            // Spell icon — cast it
            Spell spell = gui.spellAt(slot);
            if (spell != null) {
                p.closeInventory();
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    CastingService caster = new CastingService(plugin);
                    caster.cast(p, spell);
                });
                return;
            }
            return;
        }
    }

    // Also cancel dragging in all custom GUIs
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent e) {
        Object holder = e.getInventory().getHolder();
        if (holder instanceof SpellSelectionGUI
                || holder instanceof SchoolSelectorGUI
                || holder instanceof MasteryGUI) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        plugin.getGuiManager().close(p.getUniqueId());
    }
}
