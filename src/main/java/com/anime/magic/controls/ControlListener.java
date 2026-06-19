package com.anime.magic.controls;

import com.anime.magic.AnimeMagicPlugin;
import com.anime.magic.api.Spell;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;

/**
 * Routes ALL input events to ControlManager.
 *
 * CRITICAL: Uses PlayerAnimationEvent for left-click detection because
 * Bukkit's LEFT_CLICK_AIR only fires when holding an item. When the hand
 * is empty, PlayerAnimationEvent is the ONLY way to detect left-clicks.
 */
public final class ControlListener implements Listener {
    private final AnimeMagicPlugin plugin;

    public ControlListener(AnimeMagicPlugin plugin) { this.plugin = plugin; }

    /**
     * PlayerAnimationEvent fires on EVERY arm swing (left-click), regardless of:
     * - Whether the player is holding an item or has empty hands
     * - Whether they're pointing at a block or at nothing
     * - Whether they're in survival or creative
     *
     * This is the PRIMARY ability trigger.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onArmSwing(PlayerAnimationEvent e) {
        Player p = e.getPlayer();
        var hotbar = plugin.getControlManager().get("hotbar");
        if (hotbar instanceof HotbarControl hc) {
            hc.onArmSwing(p);
        }
    }

    /**
     * PlayerInteractEvent — handles RIGHT-click only (left-clicks handled by onArmSwing).
     * Also routes to other control schemes (SpellWheel, etc.).
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        plugin.getControlManager().onInteract(e.getPlayer(), e);
    }

    @EventHandler
    public void onSlot(PlayerItemHeldEvent e) {
        plugin.getControlManager().onSlotChange(e.getPlayer(), e);
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent e) {
        plugin.getControlManager().onSneak(e.getPlayer(), e);
    }

    @EventHandler
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        if (!plugin.getConfig().getBoolean("controls.castbar.interrupt-on-damage", true)) return;
        var cs = plugin.getControlManager().get("castbar");
        if (cs instanceof CastBarControl cbc) cbc.cancel(p.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!(e.getInventory().getHolder() instanceof SpellWheelGUI gui)) return;
        e.setCancelled(true);
        if (e.getClickedInventory() == null || !e.getClickedInventory().equals(gui.getInventory())) return;
        Spell spell = gui.spellAt(e.getSlot());
        if (spell == null) {
            if (e.getSlot() == SpellWheelGUI.CENTER_CANCEL) p.closeInventory();
            return;
        }
        var wc = (SpellWheelControl) plugin.getControlManager().get("wheel");
        if (wc != null) wc.onWheelClick(p, spell);
    }
}
