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

/**
 * Hotbar Control v4 — LEFT CLICK ONLY by default. Works in air and on blocks.
 * Gives players actual hotbar items with CustomModelData textures.
 */
public final class HotbarControl implements ControlScheme {
    private final AnimeMagicPlugin plugin;

    public HotbarControl(AnimeMagicPlugin plugin) { this.plugin = plugin; }

    @Override public @NotNull String id() { return "hotbar"; }
    @Override public @NotNull String displayName() { return "Hotbar Binding"; }
    @Override public @NotNull String description() {
        return "Left-click to cast. Sneak+left-click for variant. Works in air and on blocks.";
    }

    @Override
    public void onInteract(@NotNull Player player, @NotNull PlayerInteractEvent e) {
        Action action = e.getAction();
        // LEFT CLICK only (both air and block). Also accept right-click as backup.
        if (action != Action.LEFT_CLICK_AIR && action != Action.LEFT_CLICK_BLOCK
            && action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        int slot = player.getInventory().getHeldItemSlot();
        boolean sneaking = player.isSneaking();

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

        CastingService cs = new CastingService(plugin);
        CastingService.Result result = cs.cast(player, spell);
        if (result == CastingService.Result.SUCCESS) {
            plugin.getMessages().send(player, "spell.cast", "%spell%", spell.displayName());
        }
    }

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

            ItemStack item = createSpellItem(spell, i);
            player.getInventory().setItem(i, item);
        }
    }

    private ItemStack createSpellItem(Spell spell, int slot) {
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
            java.util.List<String> lore = new java.util.ArrayList<>();
            lore.add("§7" + spell.school().name());
            lore.add("§bMana: §e" + spell.manaCost());
            lore.add("§bCD: §e" + (spell.cooldownMs() / 1000) + "s");
            lore.add("");
            lore.add("§aLeft-Click to cast");
            lore.add("§dSneak+Click for variant");
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }
}
