package com.anime.magic.controls;

import com.anime.magic.AnimeMagicPlugin;
import com.anime.magic.api.CastingService;
import com.anime.magic.api.Spell;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Hotbar Control — bind spells to hotbar slots 1-9, then right-click with the
 * corresponding item to cast. Bind via /bind hotbar <slot> <spell>.
 */
public final class HotbarControl implements ControlScheme {
    private final AnimeMagicPlugin plugin;

    public HotbarControl(AnimeMagicPlugin plugin) { this.plugin = plugin; }

    @Override public @NotNull String id() { return "hotbar"; }
    @Override public @NotNull String displayName() { return "Hotbar Binding"; }
    @Override public @NotNull String description() {
        return "Bind spells to hotbar slots; right-click the bound item to cast.";
    }

    @Override
    public void onInteract(@NotNull Player player, @NotNull org.bukkit.event.player.PlayerInteractEvent e) {
        switch (e.getAction()) {
            case RIGHT_CLICK_AIR, RIGHT_CLICK_BLOCK -> {}
            default -> { return; }
        }
        int slot = player.getInventory().getHeldItemSlot();
        String spellId = plugin.getControlManager().boundSpell(player.getUniqueId(), slot);
        if (spellId == null) return;
        Spell spell = plugin.getSpellRegistry().get(spellId);
        if (spell == null) return;
        e.setCancelled(true);
        CastingService cs = new CastingService(plugin);
        CastingService.Result result = cs.cast(player, spell);
        if (result == CastingService.Result.SUCCESS) {
            plugin.getMessages().send(player, "spell.cast", "%spell%", spell.displayName());
        }
    }

    @Override
    public void onSlotChange(@NotNull Player player, @NotNull PlayerItemHeldEvent e) {
        plugin.getControlManager().selectedSlot(player.getUniqueId(), e.getNewSlot());
        String bound = plugin.getControlManager().boundSpell(player.getUniqueId(), e.getNewSlot());
        if (bound != null) {
            Spell s = plugin.getSpellRegistry().get(bound);
            if (s != null) {
                player.sendActionBar(plugin.getMessages().format("controls.hotbar.equipped",
                        "%spell%", s.displayName()));
            }
        }
    }
}
