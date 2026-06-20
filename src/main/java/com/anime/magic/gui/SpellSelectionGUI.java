package com.anime.magic.gui;

import com.anime.magic.AnimeMagicPlugin;
import com.anime.magic.api.Spell;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * The main spellbook GUI — a school-grouped, multi-page inventory view of all
 * spells the player can cast.
 *
 * <p><b>Layout (54 slots = 6 rows × 9):</b>
 * <pre>
 *   Row 0 (0-8):   [All][Nar][Ten][Mus][One][B][B][B][B]   filter tabs + border
 *   Row 1 (9-17):  [N-ICON][S][S][S][S][S][S][S][B]        school icon + 7 Naruto spells
 *   Row 2 (18-26): [T-ICON][S][S][S][S][S][S][S][B]        school icon + 7 Tensura spells
 *   Row 3 (27-35): [M-ICON][S][S][S][S][S][S][S][B]        school icon + 7 Mushoku spells
 *   Row 4 (36-44): [O-ICON][S][S][S][S][S][S][S][B]        school icon + 7 OnePiece spells
 *   Row 5 (45-53): [P][B][B][B][C][B][B][B][N]              prev + border + close + next
 * </pre>
 *
 * <p>Each school row starts with the school's icon (column 0), followed by up
 * to 7 spell icons. When a school filter is active, only that school's row is
 * populated and the other 3 rows show decoration. Pagination advances
 * per-school (page 2 shows spells 8-14 of each school).</p>
 *
 * <p>Clicking a spell icon attempts a cast via the central CastingService.</p>
 */
public final class SpellSelectionGUI implements InventoryHolder {
    /** Spells per school per page (7 columns × 4 school rows = 28 total). */
    public static final int SPELLS_PER_SCHOOL_PER_PAGE = 7;

    private final AnimeMagicPlugin plugin;
    private final GUIManager guiManager;
    private final Player player;
    private Inventory inventory;
    private int currentPage = 0;
    private Spell.SchoolId currentFilter = null;
    private Map<Spell.SchoolId, List<Spell>> cachedBySchool = new LinkedHashMap<>();
    private int cachedTotalPages = 1;

    /** School display order (matches the filter tab order). */
    private static final List<Spell.SchoolId> SCHOOL_ORDER = List.of(
            Spell.SchoolId.NARUTO, Spell.SchoolId.TENSURA,
            Spell.SchoolId.MUSHOKU, Spell.SchoolId.ONEPIECE);

    /** Row start slot for each school's spell row. */
    private static final Map<Spell.SchoolId, Integer> SCHOOL_ROW_START = Map.of(
            Spell.SchoolId.NARUTO, 9,
            Spell.SchoolId.TENSURA, 18,
            Spell.SchoolId.MUSHOKU, 27,
            Spell.SchoolId.ONEPIECE, 36);

    /** CustomModelData for each school's icon (paper overrides). */
    private static final Map<Spell.SchoolId, Integer> SCHOOL_ICON_CMD = Map.of(
            Spell.SchoolId.NARUTO, 1002,
            Spell.SchoolId.TENSURA, 1003,
            Spell.SchoolId.MUSHOKU, 1004,
            Spell.SchoolId.ONEPIECE, 1005);

    public SpellSelectionGUI(AnimeMagicPlugin plugin, GUIManager guiManager, Player player) {
        this.plugin = plugin; this.guiManager = guiManager; this.player = player;
    }

    public void openPage(int page, @Nullable Spell.SchoolId filter, @Nullable Spell.SchoolId previousFilter) {
        // Always overwrite currentFilter — passing null means "show all schools".
        currentFilter = filter;
        cacheSpells();
        if (page < 0) page = 0;
        if (page >= cachedTotalPages) page = Math.max(0, cachedTotalPages - 1);
        currentPage = page;

        int rows = plugin.getConfig().getInt("gui.spellbook-rows", 6);
        int size = rows * 9;
        String title = plugin.getMessages().format("gui.spellbook.title",
                "%page%", String.valueOf(currentPage + 1), "%pages%", String.valueOf(cachedTotalPages));
        this.inventory = Bukkit.createInventory(this, size, title);

        decorateBorder(size);
        placeFilters();
        placeSchoolIcons();
        placeSpells();
        placeNavigation(cachedTotalPages);
        player.openInventory(inventory);
    }

    /** Group all registered spells by school, sorted by required level within each school. */
    private void cacheSpells() {
        cachedBySchool = new LinkedHashMap<>();
        for (Spell.SchoolId s : SCHOOL_ORDER) cachedBySchool.put(s, new ArrayList<>());
        for (Spell s : plugin.getSpellRegistry().all()) {
            if (!s.id().contains(":")) continue;
            List<Spell> list = cachedBySchool.get(s.school());
            if (list != null) list.add(s);
        }
        for (List<Spell> list : cachedBySchool.values()) {
            list.sort(Comparator.comparingInt(Spell::requiredLevel));
        }
        // Total pages = ceil(max school spell count / SPELLS_PER_SCHOOL_PER_PAGE)
        int maxSchoolSize = 0;
        for (List<Spell> list : cachedBySchool.values()) {
            if (currentFilter == null || list.isEmpty() || list.get(0).school() == currentFilter) {
                maxSchoolSize = Math.max(maxSchoolSize, list.size());
            }
        }
        cachedTotalPages = Math.max(1, (maxSchoolSize + SPELLS_PER_SCHOOL_PER_PAGE - 1) / SPELLS_PER_SCHOOL_PER_PAGE);
    }

    private void decorateBorder(int size) {
        GUIManager.TextureDef border = guiManager.texture("border-edge");
        ItemStack decor = border != null ? makeDecor(border) : decorGlass(Material.MAGENTA_STAINED_GLASS_PANE);
        // Top-right corners (slots 5-8), right edge per row (17, 26, 35, 44),
        // bottom row gaps (46, 47, 48, 50, 51, 52).
        for (int slot : new int[]{5, 6, 7, 8, 17, 26, 35, 44, 46, 47, 48, 50, 51, 52}) {
            if (slot < size) inventory.setItem(slot, decor);
        }
    }

    private void placeFilters() {
        Map<String, Spell.SchoolId> filters = new LinkedHashMap<>();
        filters.put("filter-all", null);
        filters.put("filter-naruto", Spell.SchoolId.NARUTO);
        filters.put("filter-tensura", Spell.SchoolId.TENSURA);
        filters.put("filter-mushoku", Spell.SchoolId.MUSHOKU);
        filters.put("filter-onepiece", Spell.SchoolId.ONEPIECE);
        List<Integer> slots = List.of(0, 1, 2, 3, 4);
        int i = 0;
        for (var e : filters.entrySet()) {
            if (i >= slots.size()) break;
            GUIManager.TextureDef def = guiManager.texture(e.getKey());
            if (def != null) {
                ItemStack item = make(def);
                // Highlight the active filter
                if (e.getValue() == currentFilter
                        || (e.getValue() == null && currentFilter == null)) {
                    ItemMeta m = item.getItemMeta();
                    if (m != null) {
                        m.setDisplayName("§a» " + def.display + " §a«");
                        item.setItemMeta(m);
                    }
                }
                setAction(item, "filter:" + (e.getValue() == null ? "all" : e.getValue().name().toLowerCase()));
                inventory.setItem(slots.get(i), item);
            } else {
                // Fallback: plain stained glass with school color
                inventory.setItem(slots.get(i), schoolFilterFallback(e.getValue()));
            }
            i++;
        }
    }

    /** Place the school icon at column 0 of each school's row. */
    private void placeSchoolIcons() {
        for (Spell.SchoolId school : SCHOOL_ORDER) {
            // Skip rows for schools that are filtered out
            if (currentFilter != null && school != currentFilter) continue;
            int rowStart = SCHOOL_ROW_START.get(school);
            ItemStack icon = schoolIcon(school);
            setAction(icon, "filter:" + school.name().toLowerCase());
            inventory.setItem(rowStart, icon);
        }
    }

    private ItemStack schoolIcon(Spell.SchoolId school) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(schoolColor(school) + "§l" + schoolName(school) + " School");
            meta.setCustomModelData(SCHOOL_ICON_CMD.get(school));
            List<String> lore = new ArrayList<>();
            lore.add("§7" + schoolCountLore(school));
            lore.add("§eClick to filter by this school");
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private String schoolCountLore(Spell.SchoolId school) {
        List<Spell> list = cachedBySchool.getOrDefault(school, Collections.emptyList());
        return list.size() + " spells available";
    }

    /** Place spell icons in each school's row (columns 1-7). */
    private void placeSpells() {
        int pageOffset = currentPage * SPELLS_PER_SCHOOL_PER_PAGE;
        for (Spell.SchoolId school : SCHOOL_ORDER) {
            if (currentFilter != null && school != currentFilter) continue;
            List<Spell> list = cachedBySchool.getOrDefault(school, Collections.emptyList());
            int rowStart = SCHOOL_ROW_START.get(school);
            for (int i = 0; i < SPELLS_PER_SCHOOL_PER_PAGE; i++) {
                int spellIdx = pageOffset + i;
                if (spellIdx >= list.size()) break;
                int slot = rowStart + 1 + i; // column 0 is the school icon
                inventory.setItem(slot, makeSpellIcon(list.get(spellIdx)));
            }
        }
    }

    private void placeNavigation(int pages) {
        int prevSlot = 45, closeSlot = 49, nextSlot = 53;
        // Always place close button
        ItemStack close = make(guiManager.texture("close"));
        setAction(close, "close");
        inventory.setItem(closeSlot, close);
        // Prev button (only if not on page 1)
        if (currentPage > 0) {
            ItemStack prev = make(guiManager.texture("prev-page"));
            setAction(prev, "prev");
            inventory.setItem(prevSlot, prev);
        } else {
            inventory.setItem(prevSlot, decorGlass(Material.GRAY_STAINED_GLASS_PANE));
        }
        // Next button (only if not on last page)
        if (currentPage < pages - 1) {
            ItemStack next = make(guiManager.texture("next-page"));
            setAction(next, "next");
            inventory.setItem(nextSlot, next);
        } else {
            inventory.setItem(nextSlot, decorGlass(Material.GRAY_STAINED_GLASS_PANE));
        }
    }

    private ItemStack makeSpellIcon(Spell spell) {
        Material mat = safeMaterial(spell.icon().material);
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            // Display name = school-colored spell name (the icon IS the ability icon)
            meta.setDisplayName(spell.displayName());
            if (spell.icon().customModelData != 0) meta.setCustomModelData(spell.icon().customModelData);
            List<String> lore = new ArrayList<>();
            lore.add("§7School: " + schoolColor(spell.school()) + schoolName(spell.school()));
            boolean manaEnabled = plugin.getConfig().getBoolean("mana.enabled", true);
            if (manaEnabled && spell.manaCost() > 0) {
                lore.add("§bMana: §e" + spell.manaCost());
            }
            lore.add("§bCooldown: §e" + (spell.cooldownMs() / 1000) + "s");
            if (spell.requiredLevel() > 0) {
                lore.add("§bRequired Level: §e" + spell.requiredLevel());
            }
            lore.add("");
            if (plugin.getConfig().getBoolean("gui.show-lore", true)) {
                for (String line : spell.description().split("\n")) lore.add("§7" + line);
            }
            lore.add("");
            lore.add(plugin.getMessages().raw("gui.spellbook.cast"));
            meta.setLore(lore);
            meta.getPersistentDataContainer().set(guiManager.actionKey(), PersistentDataType.STRING, "cast:" + spell.id());
            meta.getPersistentDataContainer().set(guiManager.spellIdKey(), PersistentDataType.STRING, spell.id());
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack make(@Nullable GUIManager.TextureDef def) {
        if (def == null) return new ItemStack(Material.STONE);
        ItemStack item = new ItemStack(safeMaterial(def.material));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(def.display);
            if (def.customModelData != 0) meta.setCustomModelData(def.customModelData);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack makeDecor(GUIManager.TextureDef def) {
        ItemStack item = make(def);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) { meta.setDisplayName(" "); item.setItemMeta(meta); }
        return item;
    }

    private ItemStack decorGlass(Material mat) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) { meta.setDisplayName(" "); item.setItemMeta(meta); }
        return item;
    }

    private ItemStack schoolFilterFallback(@Nullable Spell.SchoolId school) {
        Material mat = school == null ? Material.WHITE_STAINED_GLASS_PANE : switch (school) {
            case NARUTO -> Material.ORANGE_STAINED_GLASS_PANE;
            case TENSURA -> Material.PURPLE_STAINED_GLASS_PANE;
            case MUSHOKU -> Material.CYAN_STAINED_GLASS_PANE;
            case ONEPIECE -> Material.BLUE_STAINED_GLASS_PANE;
        };
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(school == null ? "§fAll Schools" : schoolColor(school) + schoolName(school));
            item.setItemMeta(meta);
        }
        return item;
    }

    private void setAction(ItemStack item, String action) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(guiManager.actionKey(), PersistentDataType.STRING, action);
            item.setItemMeta(meta);
        }
    }

    private static Material safeMaterial(String name) {
        try { return Material.valueOf(name.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException e) { return Material.STONE; }
    }

    private static String schoolColor(Spell.SchoolId s) {
        return switch (s) {
            case NARUTO -> "§6";
            case TENSURA -> "§5";
            case MUSHOKU -> "§3";
            case ONEPIECE -> "§b";
        };
    }

    private static String schoolName(Spell.SchoolId s) {
        return switch (s) {
            case NARUTO -> "Naruto";
            case TENSURA -> "Tensura";
            case MUSHOKU -> "Mushoku Tensei";
            case ONEPIECE -> "One Piece";
        };
    }

    public Player player() { return player; }
    public int currentPage() { return currentPage; }
    public Spell.SchoolId currentFilter() { return currentFilter; }
    @Override public @NotNull Inventory getInventory() { return inventory; }
}
