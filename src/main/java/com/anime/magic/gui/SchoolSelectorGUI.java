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
 * School Selector GUI — clean professional design. No ugly symbols.
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
                "§8» §dSelect Your School §8«");

        // Borders
        for (int i = 0; i < 9; i++) inventory.setItem(i, decor(Material.PURPLE_STAINED_GLASS_PANE, " "));
        for (int i = 45; i < 54; i++) inventory.setItem(i, decor(Material.BLACK_STAINED_GLASS_PANE, " "));
        for (int i : new int[]{9,18,27,36, 17,26,35,44}) inventory.setItem(i, decor(Material.PURPLE_STAINED_GLASS_PANE, " "));

        // School icons (row 2, slots 20-23)
        inventory.setItem(20, schoolIcon(Spell.SchoolId.NARUTO, 1002,
                "§6Naruto", "§7Fire • Lightning • Rasengan", "§7Clones • Sage • Six Paths", "", "§eClick to equip"));
        inventory.setItem(21, schoolIcon(Spell.SchoolId.TENSURA, 1003,
                "§5Tensura", "§7Blade • Gluttony • Razor Edge", "§7Disintegration • Dragon", "", "§eClick to equip"));
        inventory.setItem(22, schoolIcon(Spell.SchoolId.MUSHOKU, 1004,
                "§3Mushoku Tensei", "§7Water • Fire • Earth", "§7Atomic • Gravity • Time", "", "§eClick to equip"));
        inventory.setItem(23, schoolIcon(Spell.SchoolId.ONEPIECE, 1005,
                "§bOne Piece", "§7Gomu • Haki • Conqueror", "§7Gear 2/3/4 • Voice", "", "§eClick to equip"));

        // Info (slot 31)
        inventory.setItem(31, info());
        // Close (slot 40)
        inventory.setItem(40, close());

        // Fill empties
        for (int i : new int[]{10,11,12,13,14,15,16, 19,24,25, 28,29,30,32,33,34, 37,38,39,41,42,43}) {
            inventory.setItem(i, decor(Material.GRAY_STAINED_GLASS_PANE, " "));
        }

        player.openInventory(inventory);
    }

    private ItemStack schoolIcon(Spell.SchoolId school, int cmd, String name, String... lore) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setCustomModelData(cmd);
            meta.setLore(List.of(lore));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack info() {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§dHow It Works");
            meta.setLore(List.of(
                "§7Each school has unique spells.",
                "§7Click a school to equip its loadout.",
                "",
                "§eLeft-Click §7- Cast spell",
                "§eSneak+Click §7- Cast variant",
                "",
                "§aSwitch anytime: §e/school"
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack close() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) { meta.setDisplayName("§cClose"); item.setItemMeta(meta); }
        return item;
    }

    private ItemStack decor(Material mat, String name) {
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
