package com.anime.magic.controls;

import com.anime.magic.AnimeMagicPlugin;
import com.anime.magic.api.CastingService;
import com.anime.magic.api.Spell;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Hotbar Control v6 — NO INVENTORY ITEMS.
 *
 * Abilities are bound to hotbar SLOT NUMBERS (0-8), not to items.
 * The player's hotbar stays as their normal inventory — we don't touch it.
 *
 * - Left-click casts the ability bound to the current slot number
 * - Switching slots shows the ability name in the action bar
 * - Sneak+click casts the variant ability
 * - No items are given, modified, or prevented from moving
 */
public final class HotbarControl implements ControlScheme {
    private final AnimeMagicPlugin plugin;

    public HotbarControl(AnimeMagicPlugin plugin) { this.plugin = plugin; }

    @Override public @NotNull String id() { return "hotbar"; }
    @Override public @NotNull String displayName() { return "Hotbar Binding"; }
    @Override public @NotNull String description() {
        return "Left-click casts ability bound to current slot. No inventory items.";
    }

    @Override
    public void onInteract(@NotNull Player player, @NotNull PlayerInteractEvent e) {
        Action action = e.getAction();
        if (action == Action.PHYSICAL) return;
        if (action != Action.LEFT_CLICK_AIR && action != Action.LEFT_CLICK_BLOCK
            && action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        int slot = player.getInventory().getHeldItemSlot();
        boolean sneaking = player.isSneaking();

        // Check sneak variant first
        String spellId = null;
        if (sneaking && plugin.getDefaultBindings() != null) {
            spellId = plugin.getDefaultBindings().sneakSpellFor(player.getUniqueId(), slot);
        }
        if (spellId == null) {
            spellId = plugin.getControlManager().boundSpell(player.getUniqueId(), slot);
        }
        if (spellId == null) return;

        Spell spell = plugin.getSpellRegistry().get(spellId);
        if (spell == null) return;

        // Do NOT cancel the event — let vanilla item interactions happen too
        // (player can still mine blocks with their pickaxe while having abilities bound)

        CastingService cs = new CastingService(plugin);
        CastingService.Result result = cs.cast(player, spell);
        if (result == CastingService.Result.SUCCESS) {
            plugin.getMessages().send(player, "spell.cast", "%spell%", spell.displayName());
        }
    }

    @Override
    public void onSlotChange(@NotNull Player player, @NotNull PlayerItemHeldEvent e) {
        int newSlot = e.getNewSlot();
        String spellId = plugin.getControlManager().boundSpell(player.getUniqueId(), newSlot);
        if (spellId == null) return;
        Spell spell = plugin.getSpellRegistry().get(spellId);
        if (spell == null) return;

        // Show ability name + mana cost in action bar
        String sneakId = plugin.getDefaultBindings() != null
                ? plugin.getDefaultBindings().sneakSpellFor(player.getUniqueId(), newSlot) : null;
        Spell sneak = sneakId != null ? plugin.getSpellRegistry().get(sneakId) : null;

        StringBuilder msg = new StringBuilder();
        msg.append("\u00a76Slot ").append(newSlot + 1).append(": \u00a7e").append(spell.displayName());
        msg.append(" \u00a77(").append(spell.manaCost()).append(" mana)");
        if (sneak != null) {
            msg.append(" \u00a7d+ [Sneak] ").append(sneak.displayName());
        }
        player.sendActionBar(msg.toString());
    }
}
