package com.anime.magic.gui;

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

import java.util.List;

/**
 * <b>School Selector GUI v2 — Professional Design</b>
 *
 * <pre>
 *  Row 0: ██████████████████████████████ (title banner)
 *  Row 1: ░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░ (spacer)
 *  Row 2: ░░░ [Naruto] [Tensura] [Mushoku] [OnePiece] ░░░ (school icons)
 *  Row 3: ░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░ (spacer)
 *  Row 4: ░░░░░░░░ [Info] ░░░░░░░░ [Close] ░░░ (info + close)
 *  Row 5: ██████████████████████████████ (bottom border)
 * </pre>
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
        this.inventory = Bukkit.createInventory(this, 54,
                "§8§l» §d§lSelect Your School §8§l«");

        // Row 0: Title banner (purple glass)
        for (int i = 0; i < 9; i++) {
            inventory.setItem(i, makeDecor(Material.PURPLE_STAINED_GLASS_PANE, "§d§lAnimeMagic"));
        }
        // Row 5: Bottom border (black glass)
        for (int i = 45; i < 54; i++) {
            inventory.setItem(i, makeDecor(Material.BLACK_STAINED_GLASS_PANE, " "));
        }

        // Row 2: School icons (slots 20-23)
        inventory.setItem(20, makeSchoolIcon(Spell.SchoolId.NARUTO, 1002,
                "§6§l⚔ NARUTO ⚔",
                "§7Fire Style • Lightning • Rasengan",
                "§7Shadow Clones • Sage Mode • Six Paths",
                "",
                "§e» Click to equip «"));
        inventory.setItem(21, makeSchoolIcon(Spell.SchoolId.TENSURA, 1003,
                "§5§l⚔ TENSURA ⚔",
                "§7Magicule Blade • Gluttony • Razor Edge",
                "§7Disintegration • Megiddo • True Dragon",
                "",
                "§e» Click to equip «"));
        inventory.setItem(22, makeSchoolIcon(Spell.SchoolId.MUSHOKU, 1004,
                "§3§l⚔ MUSHOKU ⚔",
                "§7Saint Water • Saint Fire • Emperor Earth",
                "§7Atomic Flare • Gravity • Time Warp",
                "",
                "§e» Click to equip «"));
        inventory.setItem(23, makeSchoolIcon(Spell.SchoolId.ONEPIECE, 1005,
                "§b§l⚔ ONE PIECE ⚔",
                "§7Gomu Gomu • Armament Haki • Conqueror's Haki",
                "§7Gear Second/Third/Fourth • Voice of All Things",
                "",
                "§e» Click to equip «"));

        // Row 4: Info (slot 22) + Close (slot 40)
        inventory.setItem(31, makeInfo());
        inventory.setItem(40, makeClose());

        // Fill remaining slots with dark glass
        int[] fillSlots = {9,10,11,12,13,14,15,16,17, 18,19,24,25,26, 27,28,29,30,32,33,34,35, 36,37,38,39,41,42,43,44};
        for (int slot : fillSlots) {
            inventory.setItem(slot, makeDecor(Material.GRAY_STAINED_GLASS_PANE, " "));
        }

        player.openInventory(inventory);
    }

    private ItemStack makeSchoolIcon(Spell.SchoolId school, int cmd, String name, String... lore) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setCustomModelData(cmd);
            meta.setLore(List.of(lore));
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES, org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack makeInfo() {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§d§lHow It Works");
            meta.setLore(List.of(
                    "§7Each school has unique spells.",
                    "§7Selecting a school gives you",
                    "§7a hotbar with pre-bound abilities.",
                    "",
                    "§eLeft/Right Click §7- Cast spell",
                    "§eSneak + Click §7- Cast variant",
                    "",
                    "§aSwitch schools anytime with §e/school"
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack makeClose() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§c§l✕ Close");
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack makeDecor(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) { meta.setDisplayName(name); item.setItemMeta(meta); }
        return item;
    }

    public @org.jetbrains.annotations.Nullable Spell.SchoolId schoolAt(int slot) {
        return switch (slot) {
            case 20 -> Spell.SchoolId.NARUTO;
            case 21 -> Spell.SchoolId.TENSURA;
            case 22 -> Spell.SchoolId.MUSHOKU;
            case 23 -> Spell.SchoolId.ONEPIECE;
            default -> null;
        };
    }

    public boolean isCloseButton(int slot) { return slot == 40; }
    public Player player() { return player; }
    @Override public @NotNull Inventory getInventory() { return inventory; }
}
