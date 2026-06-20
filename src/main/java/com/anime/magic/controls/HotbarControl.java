package com.anime.magic.controls;

import com.anime.magic.AnimeMagicPlugin;
import com.anime.magic.api.CastingService;
import com.anime.magic.api.Spell;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Hotbar Control v7 — fixes left-click in air.
 *
 * Bukkit's LEFT_CLICK_AIR only fires when holding an item. When the hand is
 * empty, it never fires. We use PlayerAnimationEvent (fires on every arm
 * swing = every left-click) as the primary trigger, plus PlayerInteractEvent
 * as backup for when the player IS holding an item.
 *
 * Abilities are bound to slot NUMBERS, not items. No inventory items given.
 */
public final class HotbarControl implements ControlScheme {
    private final AnimeMagicPlugin plugin;

    public HotbarControl(AnimeMagicPlugin plugin) { this.plugin = plugin; }

    @Override public @NotNull String id() { return "hotbar"; }
    @Override public @NotNull String displayName() { return "Hotbar Binding"; }
    @Override public @NotNull String description() {
        return "Left-click (arm swing) casts ability. Works with empty hands.";
    }

    /**
     * Called from PlayerAnimationEvent — fires on EVERY left-click (arm swing),
     * regardless of whether the player is holding an item or pointing at a block.
     * This is the PRIMARY trigger for ability casting.
     */
    public void onArmSwing(@NotNull Player player) {
        tryCast(player, player.isSneaking());
    }

    /**
     * Called from PlayerInteractEvent — backup trigger for when the player
     * IS holding an item and left/right-clicks. We handle RIGHT_CLICK only here
     * since left-clicks are handled by onArmSwing.
     */
    @Override
    public void onInteract(@NotNull Player player, @NotNull PlayerInteractEvent e) {
        Action action = e.getAction();
        if (action == Action.PHYSICAL) return;
        // Only handle RIGHT clicks here — left clicks are handled by onArmSwing
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        // Don't cancel — let vanilla right-click work too (eating, placing blocks, etc.)
        tryCast(player, player.isSneaking());
    }

    private void tryCast(Player player, boolean sneaking) {
        int slot = player.getInventory().getHeldItemSlot();

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

        CastingService cs = new CastingService(plugin);
        CastingService.Result result = cs.cast(player, spell);
        if (result == CastingService.Result.SUCCESS) {
            plugin.getMessages().send(player, "spell.cast", "%spell%", spell.displayName());
        }
    }

    @Override
    public void onSlotChange(@NotNull Player player, @NotNull PlayerItemHeldEvent e) {
        // The persistent ActionBarTask now shows the selected ability HUD
        // continuously, so we don't need to send a one-shot action-bar message
        // on slot change. We just track the slot for other controls that
        // might want it (e.g. combo tracking).
        int newSlot = e.getNewSlot();
        plugin.getControlManager().selectedSlot(player.getUniqueId(), newSlot);
    }
}
