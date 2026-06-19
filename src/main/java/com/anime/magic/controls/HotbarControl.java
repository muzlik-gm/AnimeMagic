package com.anime.magic.controls;

import com.anime.magic.AnimeMagicPlugin;
import com.anime.magic.api.CastingService;
import com.anime.magic.api.Spell;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.jetbrains.annotations.NotNull;

/**
 * <b>Hotbar Control v2</b> — bind spells to hotbar slots with sneak-modifier support.
 *
 * <p>Right-click a hotbar item → casts the slot's normal spell.
 * Sneak + right-click → casts the slot's sneak-variant spell (if bound).</p>
 *
 * <p>Default loadouts per school are applied via {@link DefaultBindings#applyLoadout}.
 * Use {@code /school <naruto|tensura|mushoku|onepiece>} to swap loadouts.</p>
 */
public final class HotbarControl implements ControlScheme {
    private final AnimeMagicPlugin plugin;

    public HotbarControl(AnimeMagicPlugin plugin) { this.plugin = plugin; }

    @Override public @NotNull String id() { return "hotbar"; }
    @Override public @NotNull String displayName() { return "Hotbar Binding"; }
    @Override public @NotNull String description() {
        return "Right-click a hotbar item to cast; sneak+right-click for the variant spell.";
    }

    @Override
    public void onInteract(@NotNull Player player, @NotNull org.bukkit.event.player.PlayerInteractEvent e) {
        switch (e.getAction()) {
            case RIGHT_CLICK_AIR, RIGHT_CLICK_BLOCK -> {}
            default -> { return; }
        }
        int slot = player.getInventory().getHeldItemSlot();
        boolean sneaking = player.isSneaking();

        // Sneak variant takes precedence
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
        e.setCancelled(true);

        // Visual feedback on the action bar before cast
        player.sendActionBar(plugin.getMessages().format("controls.hotbar.casting",
                "%spell%", spell.displayName(),
                "%mode%", sneaking ? "§d[Sneak]" : "§a[Normal]"));

        CastingService cs = new CastingService(plugin);
        CastingService.Result result = cs.cast(player, spell);
        if (result == CastingService.Result.SUCCESS) {
            plugin.getMessages().send(player, "spell.cast", "%spell%", spell.displayName());
        }
    }

    @Override
    public void onSlotChange(@NotNull Player player, @NotNull PlayerItemHeldEvent e) {
        plugin.getControlManager().selectedSlot(player.getUniqueId(), e.getNewSlot());
        if (!plugin.getConfig().getBoolean("controls.hotbar.show-equipped-message", true)) return;
        String normal = plugin.getControlManager().boundSpell(player.getUniqueId(), e.getNewSlot());
        String sneak = plugin.getDefaultBindings() != null
                ? plugin.getDefaultBindings().sneakSpellFor(player.getUniqueId(), e.getNewSlot())
                : null;
        if (normal != null) {
            Spell s = plugin.getSpellRegistry().get(normal);
            Spell ss = sneak != null ? plugin.getSpellRegistry().get(sneak) : null;
            String msg = "§7» §a" + (s != null ? s.displayName() : normal);
            if (ss != null) msg += " §7+ §d[sneak] " + ss.displayName();
            player.sendActionBar(msg);
        }
    }
}
