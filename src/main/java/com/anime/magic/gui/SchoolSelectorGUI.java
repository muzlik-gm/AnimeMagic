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
 * School Selector GUI — RPG class-selection style.
 *
 * <p>Layout (54 slots = 6 rows × 9):
 * <pre>
 *   Row 0: [B][B][B][B][B][B][B][B][B]   dark border
 *   Row 1: [B][.][.][NAR][.][.][B][B][B]   Naruto card (slot 20)
 *   Row 2: [B][TEN][.][.][.][TEN][.][B][B]   (spacers)
 *   Row 3: [B][.][MUS][.][ONE][.][B][B][B]   Mushoku (22) + OnePiece (24)
 *   Row 4: [B][.][.][.][.][.][B][B][B]
 *   Row 5: [B][B][B][B][X][B][B][B][B]   close (slot 49)
 * </pre>
 *
 * Actually simpler: 4 school cards in a 2×2 grid in the center, with
 * clean borders and a close button. RPG style.
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
                "§8» §d§lSelect Your School §8«");

        // Fill ALL slots with dark border first
        for (int i = 0; i < 54; i++) {
            inventory.setItem(i, decor(Material.BLACK_STAINED_GLASS_PANE, " "));
        }

        // Inner area border (lighter purple) — slots 11-15, 20-24, 29-33, 38-42
        for (int slot : new int[]{11, 12, 13, 14, 15, 20, 24, 29, 33, 38, 39, 40, 41, 42}) {
            inventory.setItem(slot, decor(Material.PURPLE_STAINED_GLASS_PANE, " "));
        }

        // 4 school cards in 2×2 grid (slots 21, 23, 30, 32)
        inventory.setItem(21, schoolCard(Spell.SchoolId.NARUTO, "§6§lNaruto",
                "§7Fire • Lightning • Rasengan", "§7Clones • Sage • Six Paths",
                "§e10 spells available", "", "§a» Click to equip «"));
        inventory.setItem(23, schoolCard(Spell.SchoolId.TENSURA, "§5§lTensura",
                "§7Blade • Gluttony • Razor Edge", "§7Disintegration • Dragon",
                "§e8 spells available", "", "§a» Click to equip «"));
        inventory.setItem(30, schoolCard(Spell.SchoolId.MUSHOKU, "§3§lMushoku Tensei",
                "§7Water • Fire • Earth", "§7Atomic • Gravity • Time",
                "§e8 spells available", "", "§a» Click to equip «"));
        inventory.setItem(32, schoolCard(Spell.SchoolId.ONEPIECE, "§b§lOne Piece",
                "§7Gomu • Haki • Conqueror", "§7Gear 2/3/4 • Voice",
                "§e8 spells available", "", "§a» Click to equip «"));

        // Info book (slot 13 — center top)
        inventory.setItem(13, info());

        // Close button (slot 49 — bottom center)
        inventory.setItem(49, close());

        player.openInventory(inventory);
    }

    private ItemStack schoolCard(Spell.SchoolId school, String name, String... lore) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setCustomModelData(schoolCmd(school));
            meta.setLore(List.of(lore));
            item.setItemMeta(meta);
        }
        return item;
    }

    private int schoolCmd(Spell.SchoolId school) {
        return switch (school) {
            case NARUTO -> 1002;
            case TENSURA -> 1003;
            case MUSHOKU -> 1004;
            case ONEPIECE -> 1005;
        };
    }

    private ItemStack info() {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§d§lHow It Works");
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
        if (meta != null) { meta.setDisplayName("§c§lClose"); item.setItemMeta(meta); }
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
            case 21 -> Spell.SchoolId.NARUTO;
            case 23 -> Spell.SchoolId.TENSURA;
            case 30 -> Spell.SchoolId.MUSHOKU;
            case 32 -> Spell.SchoolId.ONEPIECE;
            default -> null;
        };
    }

    public boolean isCloseButton(int slot) { return slot == 49; }
    public Player player() { return player; }
    @Override public @NotNull Inventory getInventory() { return inventory; }
}
