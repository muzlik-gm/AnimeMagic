package com.anime.magic.gui;

import com.anime.magic.AnimeMagicPlugin;
import com.anime.magic.api.Spell;
import com.anime.magic.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

/**
 * Coordinates all custom GUIs. Owns the shared texture-map configuration and the
 * action-id PDC key used to identify clicks across all GUIs.
 */
public final class GUIManager {
    private final AnimeMagicPlugin plugin;
    private final File textureFile;
    private YamlConfiguration textures;
    private final Map<UUID, GUIState> openStates = new HashMap<>();
    private final NamespacedKey actionKey;
    private final NamespacedKey spellIdKey;

    public static final String GUI_ID_SPELLBOOK = "spellbook";

    public GUIManager(AnimeMagicPlugin plugin) {
        this.plugin = plugin;
        this.textureFile = new File(plugin.getDataFolder(), "gui_textures.yml");
        this.actionKey = new NamespacedKey(plugin, "gui_action");
        this.spellIdKey = new NamespacedKey(plugin, "gui_spell_id");
    }

    public void load() {
        if (!textureFile.exists()) plugin.saveResource("gui_textures.yml", false);
        this.textures = YamlConfiguration.loadConfiguration(textureFile);
    }

    public void reload() { load(); }

    public @Nullable TextureDef texture(@NotNull String key) {
        ConfigurationSection s = textures.getConfigurationSection("textures." + key);
        if (s == null) return null;
        return new TextureDef(s.getString("material", "STONE"),
                s.getInt("model", 0),
                TextUtil.color(s.getString("display", "")));
    }

    public @Nullable ConfigurationSection layout() { return textures.getConfigurationSection("layout"); }

    public NamespacedKey actionKey() { return actionKey; }
    public NamespacedKey spellIdKey() { return spellIdKey; }

    public void openSpellbook(Player player) {
        SpellSelectionGUI gui = new SpellSelectionGUI(plugin, this, player);
        gui.openPage(0, Spell.SchoolId.NARUTO, null);
        openStates.put(player.getUniqueId(), new GUIState(GUI_ID_SPELLBOOK, gui));
    }

    public @Nullable GUIState state(UUID id) { return openStates.get(id); }
    public void close(UUID id) { openStates.remove(id); }

    public void closeAll() {
        for (UUID id : new HashSet<>(openStates.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.closeInventory();
        }
        openStates.clear();
    }

    public static final class GUIState {
        public final String guiId;
        public final SpellSelectionGUI gui;
        public GUIState(String guiId, SpellSelectionGUI gui) { this.guiId = guiId; this.gui = gui; }
    }

    public static final class TextureDef {
        public final String material;
        public final int customModelData;
        public final String display;
        public TextureDef(String material, int customModelData, String display) {
            this.material = material; this.customModelData = customModelData; this.display = display;
        }
    }
}
