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
 * The main spellbook GUI — a multi-page, school-filterable inventory view of all
 * spells the player can cast. Clicking a spell icon attempts a cast via the central
 * CastingService.
 */
public final class SpellSelectionGUI implements InventoryHolder {
    public static final int PAGE_SIZE = 28;

    private final AnimeMagicPlugin plugin;
    private final GUIManager guiManager;
    private final Player player;
    private Inventory inventory;
    private int currentPage = 0;
    private Spell.SchoolId currentFilter = null;
    private List<Spell> cachedSpells = Collections.emptyList();

    public SpellSelectionGUI(AnimeMagicPlugin plugin, GUIManager guiManager, Player player) {
        this.plugin = plugin; this.guiManager = guiManager; this.player = player;
    }

    public void openPage(int page, @Nullable Spell.SchoolId filter, @Nullable Spell.SchoolId previousFilter) {
        if (filter != null) currentFilter = filter;
        cachedSpells = collectSpells();
        int pages = Math.max(1, (cachedSpells.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        if (page < 0) page = 0;
        if (page >= pages) page = pages - 1;
        currentPage = page;

        int rows = plugin.getConfig().getInt("gui.spellbook-rows", 6);
        int size = rows * 9;
        String title = plugin.getMessages().format("gui.spellbook.title",
                "%page%", String.valueOf(currentPage + 1), "%pages%", String.valueOf(pages));
        this.inventory = Bukkit.createInventory(this, size, title);

        decorateBorder(size);
        placeFilters();
        placeSpells();
        placeNavigation(pages);
        player.openInventory(inventory);
    }

    private List<Spell> collectSpells() {
        List<Spell> all = new ArrayList<>();
        for (Spell s : plugin.getSpellRegistry().all()) {
            if (s.id().contains(":")) all.add(s);
        }
        all.sort(Comparator.comparing(Spell::id));
        if (currentFilter == null) return all;
        all.removeIf(s -> s.school() != currentFilter);
        return all;
    }

    private void decorateBorder(int size) {
        ConfigurationSection layout = guiManager.layout();
        if (layout == null) return;
        GUIManager.TextureDef border = guiManager.texture("border-edge");
        if (border == null) return;
        List<Integer> slots = layout.getIntegerList("border-slots");
        for (int slot : slots) {
            if (slot >= 0 && slot < size) inventory.setItem(slot, makeDecor(border));
        }
    }

    private void placeFilters() {
        Map<String, Spell.SchoolId> filters = new LinkedHashMap<>();
        filters.put("filter-all", null);
        filters.put("filter-naruto", Spell.SchoolId.NARUTO);
        filters.put("filter-tensura", Spell.SchoolId.TENSURA);
        filters.put("filter-mushoku", Spell.SchoolId.MUSHOKU);
        filters.put("filter-onepiece", Spell.SchoolId.ONEPIECE);
        ConfigurationSection layout = guiManager.layout();
        List<Integer> slots = layout == null ? List.of(0,1,2,3,4) : layout.getIntegerList("filter-slots");
        int i = 0;
        for (var e : filters.entrySet()) {
            if (i >= slots.size()) break;
            GUIManager.TextureDef def = guiManager.texture(e.getKey());
            if (def == null) { i++; continue; }
            ItemStack item = make(def);
            setAction(item, "filter:" + (e.getValue() == null ? "all" : e.getValue().name().toLowerCase()));
            inventory.setItem(slots.get(i), item);
            i++;
        }
    }

    private void placeSpells() {
        ConfigurationSection layout = guiManager.layout();
        if (layout == null) return;
        List<Integer> allSlots = new ArrayList<>();
        int start = layout.getInt("spell-slots-start", 19);
        int end = layout.getInt("spell-slots-end", 44);
        List<Integer> skip = layout.getIntegerList("spell-slots-skip");
        for (int s = start; s <= end; s++) if (!skip.contains(s)) allSlots.add(s);
        int pageStart = currentPage * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE && pageStart + i < cachedSpells.size(); i++) {
            if (i >= allSlots.size()) break;
            inventory.setItem(allSlots.get(i), makeSpellIcon(cachedSpells.get(pageStart + i)));
        }
    }

    private void placeNavigation(int pages) {
        ConfigurationSection layout = guiManager.layout();
        int nextSlot = layout == null ? 53 : layout.getInt("nav-next", 53);
        int prevSlot = layout == null ? 45 : layout.getInt("nav-prev", 45);
        int closeSlot = layout == null ? 49 : layout.getInt("nav-close", 49);
        if (currentPage > 0) {
            ItemStack prev = make(guiManager.texture("prev-page"));
            setAction(prev, "prev");
            inventory.setItem(prevSlot, prev);
        }
        if (currentPage < pages - 1) {
            ItemStack next = make(guiManager.texture("next-page"));
            setAction(next, "next");
            inventory.setItem(nextSlot, next);
        }
        ItemStack close = make(guiManager.texture("close"));
        setAction(close, "close");
        inventory.setItem(closeSlot, close);
    }

    private ItemStack makeSpellIcon(Spell spell) {
        Material mat = safeMaterial(spell.icon().material);
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(spell.displayName());
            if (spell.icon().customModelData != 0) meta.setCustomModelData(spell.icon().customModelData);
            List<String> lore = new ArrayList<>();
            lore.add("§7" + spell.school().name().charAt(0) + spell.school().name().substring(1).toLowerCase());
            lore.add("§bMana: §e" + spell.manaCost());
            lore.add("§bCooldown: §e" + (spell.cooldownMs() / 1000) + "s");
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

    public Player player() { return player; }
    public int currentPage() { return currentPage; }
    public Spell.SchoolId currentFilter() { return currentFilter; }
    @Override public @NotNull Inventory getInventory() { return inventory; }
}
