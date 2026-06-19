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

import java.util.*;

/**
 * <b>Mastery Tree GUI</b> — a 54-slot visual spell tree per school.
 *
 * <p>Layout (per school):
 * <ul>
 *   <li>Row 0: Title banner with school name</li>
 *   <li>Row 1-5: Spell tree with branches (T1 novice spells on row 1,
 *       T2 advanced on row 3, T3 ultimate on row 5)</li>
 *   <li>Row 6: Filter buttons to switch school view + back button</li>
 * </ul></p>
 *
 * <p>Each spell icon shows:
 * <ul>
 *   <li>Spell name (color-coded by school)</li>
 *   <li>Mana cost and cooldown</li>
 *   <li>"Tier" indicator (T1/T2/T3)</li>
 *   <li>Click to cast (if learned)</li>
 * </ul></p>
 *
 * <p>Tier is derived from requiredLevel: T1 (1-9), T2 (10-19), T3 (20+).</p>
 */
public final class MasteryGUI implements InventoryHolder {
    private final AnimeMagicPlugin plugin;
    private final Player player;
    private Spell.SchoolId currentSchool = Spell.SchoolId.NARUTO;
    private Inventory inventory;

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
        // Row 0: title banner
        for (int i = 0; i < 9; i++) {
            inventory.setItem(i, makeBanner(school));
        }
        // Rows 1-5: spell tree (organize spells by tier)
        List<Spell> spells = new ArrayList<>();
        for (Spell s : plugin.getSpellRegistry().all()) {
            if (s.school() == school && s.id().contains(":")) spells.add(s);
        }
        spells.sort(Comparator.comparingInt(Spell::requiredLevel));

        // Assign tiers: T1 = first third, T2 = middle third, T3 = last third
        int n = spells.size();
        int tier1End = Math.max(1, n / 3);
        int tier2End = Math.max(tier1End + 1, (2 * n) / 3);

        // Place T1 on row 1 (slots 9-17), T2 on row 3 (slots 27-35), T3 on row 5 (slots 45-53)
        int t1Slot = 10, t2Slot = 28, t3Slot = 46;
        int t1Count = 0, t2Count = 0, t3Count = 0;
        for (int i = 0; i < n; i++) {
            Spell spell = spells.get(i);
            String tier;
            int slot;
            if (i < tier1End) {
                tier = "§a[T1 Novice]";
                slot = t1Slot + t1Count;
                t1Count++;
                // Skip column 9 and 17 (keep within row 1)
                if ((slot - 9) % 9 == 8 && slot < 17) slot++;
            } else if (i < tier2End) {
                tier = "§e[T2 Advanced]";
                slot = t2Slot + t2Count;
                t2Count++;
            } else {
                tier = "§c[T3 Ultimate]";
                slot = t3Slot + t3Count;
                t3Count++;
            }
            if (slot >= 54) continue;
            inventory.setItem(slot, makeSpellIcon(spell, tier));
        }

        // Row 2 and 4: connector glass
        for (int i = 18; i < 27; i++) inventory.setItem(i, makeConnector());
        for (int i = 36; i < 45; i++) inventory.setItem(i, makeConnector());

        // Row 6: school filter tabs + close
        inventory.setItem(45, makeFilterTab(Spell.SchoolId.NARUTO, "§6§lNaruto", 1002));
        inventory.setItem(46, makeFilterTab(Spell.SchoolId.TENSURA, "§5§lTensura", 1003));
        inventory.setItem(47, makeFilterTab(Spell.SchoolId.MUSHOKU, "§3§lMushoku", 1004));
        inventory.setItem(48, makeFilterTab(Spell.SchoolId.ONEPIECE, "§b§lOne Piece", 1005));
        // spacer
        for (int i = 49; i < 53; i++) inventory.setItem(i, makeConnector());
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
            List<String> lore = new ArrayList<>();
            lore.add(tier);
            lore.add("§7School: §f" + spell.school().name());
            lore.add("§bMana: §e" + spell.manaCost());
            lore.add("§bCooldown: §e" + (spell.cooldownMs() / 1000) + "s");
            lore.add("§bRequired Level: §e" + spell.requiredLevel());
            lore.add("");
            // Wrap description
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

    public @org.jetbrains.annotations.Nullable Spell spellAt(int slot) {
        ItemStack item = inventory.getItem(slot);
        if (item == null || !item.hasItemMeta()) return null;
        // Find the spell by matching the clicked item's display name against registered spells
        String name = item.getItemMeta().getDisplayName();
        for (Spell s : plugin.getSpellRegistry().all()) {
            if (s.id().contains(":") && s.school() == currentSchool
                    && s.displayName().equals(name)) return s;
        }
        return null;
    }

    public @org.jetbrains.annotations.Nullable Spell.SchoolId schoolFilterAt(int slot) {
        return switch (slot) {
            case 45 -> Spell.SchoolId.NARUTO;
            case 46 -> Spell.SchoolId.TENSURA;
            case 47 -> Spell.SchoolId.MUSHOKU;
            case 48 -> Spell.SchoolId.ONEPIECE;
            default -> null;
        };
    }

    public boolean isCloseButton(int slot) { return slot == 53; }
    public Spell.SchoolId currentSchool() { return currentSchool; }
    public Player player() { return player; }
    @Override public @NotNull Inventory getInventory() { return inventory; }
}
