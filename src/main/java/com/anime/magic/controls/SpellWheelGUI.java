package com.anime.magic.controls;

import com.anime.magic.AnimeMagicPlugin;
import com.anime.magic.api.Spell;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A visual "spell wheel" GUI rendered as a 3x3 inventory with the center slot being
 * the cancel button and the 8 surrounding slots holding the player's most-recently-used
 * spells.
 */
public final class SpellWheelGUI implements InventoryHolder {
    public static final int SIZE = 9;
    public static final int CENTER_CANCEL = 4;
    public static final int[] WHEEL_SLOTS = {0, 1, 2, 3, 5, 6, 7, 8};

    private final AnimeMagicPlugin plugin;
    private final Player player;
    private final List<Spell> spells;
    private Inventory inventory;

    public SpellWheelGUI(AnimeMagicPlugin plugin, Player player, List<Spell> spells) {
        this.plugin = plugin;
        this.player = player;
        this.spells = spells.size() > 8 ? new ArrayList<>(spells.subList(0, 8)) : new ArrayList<>(spells);
    }

    public void open() {
        this.inventory = Bukkit.createInventory(this, SIZE,
                plugin.getMessages().raw("controls.wheel.title"));
        ItemStack cancel = new ItemStack(Material.BARRIER);
        ItemMeta cm = cancel.getItemMeta();
        if (cm != null) { cm.setDisplayName("§cClose"); cancel.setItemMeta(cm); }
        inventory.setItem(CENTER_CANCEL, cancel);
        for (int i = 0; i < WHEEL_SLOTS.length && i < spells.size(); i++) {
            inventory.setItem(WHEEL_SLOTS[i], makeIcon(spells.get(i)));
        }
        player.openInventory(inventory);
    }

    private ItemStack makeIcon(Spell spell) {
        Material mat;
        try { mat = Material.valueOf(spell.icon().material); }
        catch (IllegalArgumentException e) { mat = Material.PAPER; }
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(spell.displayName());
            if (spell.icon().customModelData != 0) meta.setCustomModelData(spell.icon().customModelData);
            List<String> lore = new ArrayList<>();
            lore.add("§7" + spell.school().name());
            lore.add("§bMana: §e" + spell.manaCost());
            lore.add("");
            lore.add(plugin.getMessages().raw("controls.wheel.click-to-cast"));
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    public Spell spellAt(int slot) {
        for (int i = 0; i < WHEEL_SLOTS.length; i++) {
            if (WHEEL_SLOTS[i] == slot && i < spells.size()) return spells.get(i);
        }
        return null;
    }

    public Player player() { return player; }
    @Override public @NotNull Inventory getInventory() { return inventory; }
}
