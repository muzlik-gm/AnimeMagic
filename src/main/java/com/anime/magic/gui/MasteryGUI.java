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
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * <b>Mastery Tree GUI</b> — a 54-slot visual spell tree per school.
 *
 * <p>Layout (per school, 54 slots = 6 rows × 9 columns):
 * <ul>
 *   <li>Row 0 (slots 0-8): Title banner with school name</li>
 *   <li>Row 1 (slots 9-17): T1 novice spells (column 0 reserved for left edge)</li>
 *   <li>Row 2 (slots 18-26): Connector glass</li>
 *   <li>Row 3 (slots 27-35): T2 advanced spells</li>
 *   <li>Row 4 (slots 36-44): Connector glass</li>
 *   <li>Row 5 (slots 45-53): T3 ultimate spells + filter tabs + close</li>
 * </ul></p>
 *
 * <p>Tier is derived from requiredLevel: T1 (1-9), T2 (10-19), T3 (20+).</p>
 */
public final class MasteryGUI implements InventoryHolder {
    private static final int ROW_SIZE = 9;
    private final AnimeMagicPlugin plugin;
    private final Player player;
    private Spell.SchoolId currentSchool = Spell.SchoolId.NARUTO;
    private Inventory inventory;
    private final Map<Integer, String> slotToSpellId = new HashMap<>();

    public MasteryGUI(AnimeMagicPlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    public void open() {
        open(Spell.SchoolId.NARUTO);
    }

    public void open(Spell.SchoolId school) {
        this.currentSchool = school;
        this.inventory = Bukkit.createInventory(this, 54,
                "§d§lMastery §8» " + schoolName(school));
        slotToSpellId.clear();

        // ── Row 0: school filter tabs (0-3) + banner decoration (4-8) ───────
        inventory.setItem(0, makeFilterTab(Spell.SchoolId.NARUTO, "§6§lNaruto", 1002));
        inventory.setItem(1, makeFilterTab(Spell.SchoolId.TENSURA, "§5§lTensura", 1003));
        inventory.setItem(2, makeFilterTab(Spell.SchoolId.MUSHOKU, "§3§lMushoku", 1004));
        inventory.setItem(3, makeFilterTab(Spell.SchoolId.ONEPIECE, "§b§lOne Piece", 1005));
        Material bannerMat = switch (school) {
            case NARUTO -> Material.ORANGE_STAINED_GLASS_PANE;
            case TENSURA -> Material.PURPLE_STAINED_GLASS_PANE;
            case MUSHOKU -> Material.CYAN_STAINED_GLASS_PANE;
            case ONEPIECE -> Material.BLUE_STAINED_GLASS_PANE;
        };
        for (int i = 4; i < 9; i++) {
            ItemStack b = new ItemStack(bannerMat);
            ItemMeta bm = b.getItemMeta();
            if (bm != null) { bm.setDisplayName("§d§l" + schoolName(school) + " §7Mastery"); b.setItemMeta(bm); }
            inventory.setItem(i, b);
        }

        // ── Rows 1-5: spell tree organized by tier ─────────────────────────
        // T1: row 1, slots 10-16 (7 slots, col 0 and col 8 are border)
        // T2: row 3, slots 28-34 (7 slots)
        // T3: row 5, slots 46-52 (7 slots, col 0 = border, col 8 = close)
        List<Spell> spells = new ArrayList<>();
        for (Spell s : plugin.getSpellRegistry().all()) {
            if (s.school() == school && s.id().contains(":")) spells.add(s);
        }
        spells.sort(Comparator.comparingInt(Spell::requiredLevel));

        int n = spells.size();
        int tier1End = Math.max(1, n / 3);
        int tier2End = Math.max(tier1End + 1, (2 * n) / 3);

        for (int i = 0; i < n; i++) {
            Spell spell = spells.get(i);
            String tier;
            int slot;
            if (i < tier1End) {
                tier = "§a[T1 Novice]";
                slot = 10 + (i % 7);
            } else if (i < tier2End) {
                tier = "§e[T2 Advanced]";
                slot = 28 + ((i - tier1End) % 7);
            } else {
                tier = "§c[T3 Ultimate]";
                slot = 46 + ((i - tier2End) % 7);
            }
            if (slot >= 54) continue;
            inventory.setItem(slot, makeSpellIcon(spell, tier));
            slotToSpellId.put(slot, spell.id());
        }

        // ── Left edge (col 0) + right edge (col 8) + connector rows ────────
        // Col 0: slots 9, 18, 27, 36, 45 — left border
        // Col 8: slots 17, 26, 35, 44 — right border
        // Connector rows: 18-26 (row 2) and 36-44 (row 4)
        for (int slot : new int[]{9, 18, 27, 36, 45, 17, 26, 35, 44}) {
            if (!slotToSpellId.containsKey(slot)) inventory.setItem(slot, makeConnector());
        }
        for (int i = 18; i < 27; i++) {
            if (!slotToSpellId.containsKey(i)) inventory.setItem(i, makeConnector());
        }
        for (int i = 36; i < 45; i++) {
            if (!slotToSpellId.containsKey(i)) inventory.setItem(i, makeConnector());
        }

        // ── Row 5 col 8: close button (slot 53) ────────────────────────────
        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta cm = close.getItemMeta();
        if (cm != null) { cm.setDisplayName("§c§lClose"); close.setItemMeta(cm); }
        inventory.setItem(53, close);

        player.openInventory(inventory);
    }

    private ItemStack makeBanner(Spell.SchoolId school) {
        Material mat = switch (school) {
            case NARUTO -> Material.ORANGE_STAINED_GLASS_PANE;
            case TENSURA -> Material.PURPLE_STAINED_GLASS_PANE;
            case MUSHOKU -> Material.CYAN_STAINED_GLASS_PANE;
            case ONEPIECE -> Material.BLUE_STAINED_GLASS_PANE;
        };
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) { meta.setDisplayName("§d§l" + schoolName(school) + " §7Mastery"); item.setItemMeta(meta); }
        return item;
    }

    private ItemStack makeConnector() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) { meta.setDisplayName(" "); item.setItemMeta(meta); }
        return item;
    }

    private ItemStack makeSpellIcon(Spell spell, String tier) {
        Material mat;
        try { mat = Material.valueOf(spell.icon().material); }
        catch (IllegalArgumentException e) { mat = Material.PAPER; }
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(spell.displayName());
            if (spell.icon().customModelData != 0) meta.setCustomModelData(spell.icon().customModelData);
            // PDC key so spellAt(slot) can resolve reliably even after a rename
            meta.getPersistentDataContainer().set(
                    new org.bukkit.NamespacedKey(plugin, "mastery_spell_id"),
                    PersistentDataType.STRING, spell.id());
            List<String> lore = new ArrayList<>();
            lore.add(tier);
            lore.add("§7School: §f" + spell.school().name());
            lore.add("§bMana: §e" + spell.manaCost());
            lore.add("§bCooldown: §e" + (spell.cooldownMs() / 1000) + "s");
            lore.add("§bRequired Level: §e" + spell.requiredLevel());
            lore.add("");
            String desc = spell.description();
            int maxLine = 40;
            while (desc.length() > maxLine) {
                int cut = desc.lastIndexOf(' ', maxLine);
                if (cut < 0) cut = maxLine;
                lore.add("§7" + desc.substring(0, cut));
                desc = desc.substring(cut).trim();
            }
            lore.add("§7" + desc);
            lore.add("");
            lore.add("§a» Click to cast «");
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack makeFilterTab(Spell.SchoolId school, String name, int cmd) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setCustomModelData(cmd);
            meta.setLore(List.of("§eClick to view", "§8school:" + school.name().toLowerCase()));
            item.setItemMeta(meta);
        }
        return item;
    }

    private String schoolName(Spell.SchoolId s) {
        return switch (s) {
            case NARUTO -> "Naruto";
            case TENSURA -> "Tensura";
            case MUSHOKU -> "Mushoku Tensei";
            case ONEPIECE -> "One Piece";
        };
    }

    /** Resolve the spell at a clicked slot via PDC, falling back to slot map. */
    public @org.jetbrains.annotations.Nullable Spell spellAt(int slot) {
        ItemStack item = inventory.getItem(slot);
        if (item == null || !item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        // Preferred path: PDC key
        String id = meta.getPersistentDataContainer().get(
                new org.bukkit.NamespacedKey(plugin, "mastery_spell_id"),
                PersistentDataType.STRING);
        if (id != null) return plugin.getSpellRegistry().get(id);
        // Fallback: slot map (for backwards compat if PDC missing)
        String fallbackId = slotToSpellId.get(slot);
        if (fallbackId != null) return plugin.getSpellRegistry().get(fallbackId);
        return null;
    }

    public @org.jetbrains.annotations.Nullable Spell.SchoolId schoolFilterAt(int slot) {
        // Filter tabs are now in row 0 (slots 0-3), not row 5 (slots 45-48).
        return switch (slot) {
            case 0 -> Spell.SchoolId.NARUTO;
            case 1 -> Spell.SchoolId.TENSURA;
            case 2 -> Spell.SchoolId.MUSHOKU;
            case 3 -> Spell.SchoolId.ONEPIECE;
            default -> null;
        };
    }

    public boolean isCloseButton(int slot) { return slot == 53; }
    public Spell.SchoolId currentSchool() { return currentSchool; }
    public Player player() { return player; }
    @Override public @NotNull Inventory getInventory() { return inventory; }
}
