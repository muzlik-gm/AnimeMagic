package com.anime.magic.gui;

import com.anime.magic.AnimeMagicPlugin;
import com.anime.magic.api.Spell;
import com.anime.magic.api.SpellRegistry;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * <b>School Selector GUI</b> — pick your active school to swap the entire hotbar loadout.
 *
 * <pre>
 *  ┌─────────────────────────────────────────────────┐
 *  │                                                 │
 *  │            [ Konoha Leaf ]                      │
 *  │            (Naruto School)                      │
 *  │                                                 │
 *  │   [ Slime ]      [ Magic Circle ]    [ Skull ]  │
 *  │   (Tensura)      (Mushoku)          (OnePiece)  │
 *  │                                                 │
 *  │              [ Close ]                          │
 *  └─────────────────────────────────────────────────┘
 * </pre>
 *
 * <p>Click a school icon → calls {@link com.anime.magic.controls.DefaultBindings#applyLoadout}
 * and closes the GUI. The hotbar immediately rebinds to that school's default spells.</p>
 */
public final class SchoolSelectorGUI implements InventoryHolder {
    private final AnimeMagicPlugin plugin;
    private final Player player;
    private Inventory inventory;

    public SchoolSelectorGUI(AnimeMagicPlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    public void open() {
        this.inventory = Bukkit.createInventory(this, 27,
                "§d§lSelect Your School");
        // Title row (slot 4)
        inventory.setItem(4, makeInfo());
        // Schools (slots 10-13)
        inventory.setItem(10, makeSchoolIcon(Spell.SchoolId.NARUTO,
                Material.PAPER, 1002, "§6§lNaruto",
                "§7Fire Style, Lightning, Rasengan, Clones",
                "§eClick to equip Naruto loadout"));
        inventory.setItem(11, makeSchoolIcon(Spell.SchoolId.TENSURA,
                Material.PAPER, 1003, "§5§lTensura",
                "§7Magicule Blade, Gluttony, Razor Edge",
                "§eClick to equip Tensura loadout"));
        inventory.setItem(12, makeSchoolIcon(Spell.SchoolId.MUSHOKU,
                Material.PAPER, 1004, "§3§lMushoku Tensei",
                "§7Saint-class + Emperor-class incantations",
                "§eClick to equip Mushoku loadout"));
        inventory.setItem(13, makeSchoolIcon(Spell.SchoolId.ONEPIECE,
                Material.PAPER, 1005, "§b§lOne Piece",
                "§7Haki, Gomu Gomu, Conqueror's Will",
                "§eClick to equip One Piece loadout"));

        // Spacer rows filled with glass
        for (int i : new int[]{0,1,2,3,5,6,7,8,9,14,15,16,17,18,19,20,21,22,23,24,25,26}) {
            inventory.setItem(i, makeGlass());
        }
        // Close button (slot 22)
        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta cm = close.getItemMeta();
        if (cm != null) { cm.setDisplayName("§c§lClose"); close.setItemMeta(cm); }
        inventory.setItem(22, close);

        player.openInventory(inventory);
    }

    private ItemStack makeInfo() {
        ItemStack item = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§d§lChoose Your Magic School");
            meta.setLore(List.of(
                    "§7Each school has a unique playstyle",
                    "§7and a default hotbar loadout.",
                    "",
                    "§eClick an icon below to equip."
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack makeSchoolIcon(Spell.SchoolId school, Material mat, int cmd,
                                      String name, String desc, String click) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setCustomModelData(cmd);
            meta.setLore(List.of("", desc, "", click,
                    "§8school:" + school.name().toLowerCase()));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack makeGlass() {
        ItemStack item = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) { meta.setDisplayName(" "); item.setItemMeta(meta); }
        return item;
    }

    public @org.jetbrains.annotations.Nullable Spell.SchoolId schoolAt(int slot) {
        return switch (slot) {
            case 10 -> Spell.SchoolId.NARUTO;
            case 11 -> Spell.SchoolId.TENSURA;
            case 12 -> Spell.SchoolId.MUSHOKU;
            case 13 -> Spell.SchoolId.ONEPIECE;
            default -> null;
        };
    }

    public Player player() { return player; }
    @Override public @NotNull Inventory getInventory() { return inventory; }
}
