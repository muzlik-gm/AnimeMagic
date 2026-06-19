package com.anime.magic.controls;

import com.anime.magic.AnimeMagicPlugin;
import com.anime.magic.api.CastingService;
import com.anime.magic.api.Spell;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * <b>Hotbar Control v3 — Complete Rewrite</b>
 *
 * <p>Handles BOTH left-click and right-click, in BOTH air and on blocks.
 * Gives players actual hotbar items with CustomModelData textures so they
 * can SEE which spell is in each slot.</p>
 *
 * <p>Triggers:
 * <ul>
 *   <li>Right-click (air or block) → cast normal spell bound to slot</li>
 *   <li>Left-click (air or block) → cast normal spell bound to slot (alternative)</li>
 *   <li>Sneak + right-click → cast sneak-variant spell</li>
 *   <li>Sneak + left-click → cast sneak-variant spell (alternative)</li>
 * </ul></p>
 */
public final class HotbarControl implements ControlScheme {
    private final AnimeMagicPlugin plugin;

    public HotbarControl(AnimeMagicPlugin plugin) { this.plugin = plugin; }

    @Override public @NotNull String id() { return "hotbar"; }
    @Override public @NotNull String displayName() { return "Hotbar Binding"; }
    @Override public @NotNull String description() {
        return "Left or right-click to cast. Sneak+click for variant. Works in air and on blocks.";
    }

    @Override
    public void onInteract(@NotNull Player player, @NotNull PlayerInteractEvent e) {
        // Accept ALL click types — left, right, air, block
        Action action = e.getAction();
        if (action == Action.PHYSICAL) return; // don't trigger on pressure plates

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

        // ALWAYS cancel the event to prevent vanilla item interactions
        e.setCancelled(true);

        // Cast the spell
        CastingService cs = new CastingService(plugin);
        CastingService.Result result = cs.cast(player, spell);
        if (result == CastingService.Result.SUCCESS) {
            plugin.getMessages().send(player, "spell.cast", "%spell%", spell.displayName());
        }
    }

    /**
     * Give the player actual hotbar items representing their bound spells.
     * Called on join and when switching schools.
     */
    public void giveHotbarItems(Player player) {
        if (plugin.getDefaultBindings() == null) return;
        var activeSchool = plugin.getDefaultBindings().activeSchool(player.getUniqueId());
        if (activeSchool == null) return;

        var loadout = plugin.getDefaultBindings().loadoutFor(activeSchool);
        if (loadout == null) return;

        for (int i = 0; i < 9; i++) {
            String spellId = loadout.slotNormal()[i];
            if (spellId == null) continue;

            Spell spell = plugin.getSpellRegistry().get(spellId);
            if (spell == null) continue;

            // Create a hotbar item with the spell's icon
            ItemStack item = createSpellItem(spell);
            player.getInventory().setItem(i, item);
        }
    }

    /**
     * Create a hotbar item for a spell. Uses the spell's icon material + CustomModelData
     * so the resource pack texture is applied.
     */
    private ItemStack createSpellItem(Spell spell) {
        Material mat;
        try { mat = Material.valueOf(spell.icon().material); }
        catch (IllegalArgumentException e) { mat = Material.PAPER; }

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(spell.displayName());
            if (spell.icon().customModelData != 0) {
                meta.setCustomModelData(spell.icon().customModelData);
            }
            // Add lore with spell info
            java.util.List<String> lore = new java.util.ArrayList<>();
            lore.add("§7" + spell.school().name());
            lore.add("§bMana: §e" + spell.manaCost());
            lore.add("§bCooldown: §e" + (spell.cooldownMs() / 1000) + "s");
            lore.add("");
            lore.add("§a» Click to cast «");
            lore.add("§d» Sneak+Click for variant «");
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }
}
