package com.anime.magic.hud;

import com.anime.magic.AnimeMagicPlugin;
import com.anime.magic.api.Spell;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;

/**
 * Persistent action-bar HUD with custom texture icons via PUA Unicode font.
 *
 * <p>Uses Private Use Area (PUA) codepoints (U+E001–U+E057) that are
 * mapped to custom texture sprites in the resource pack's font/default.json.
 * When the resource pack is applied, these codepoints render as actual
 * spell/school icons in the actionbar — real textures, not emoji.</p>
 *
 * <p>Layout:
 * <pre>
 *   [school_icon] Spell Name  [mana_icon] cost/max  [cd_icon] Ready/3s  [L-Click]
 * </pre>
 * </p>
 */
public final class ActionBarTask extends BukkitRunnable {
    private final AnimeMagicPlugin plugin;

    // PUA codepoints for icons (must match generate_font_atlas.py)
    private static final String ICON_NARUTO   = "\uE001";
    private static final String ICON_TENSURA  = "\uE002";
    private static final String ICON_MUSHOKU  = "\uE003";
    private static final String ICON_ONEPIECE = "\uE004";
    private static final String ICON_READY    = "\uE010";
    private static final String ICON_COOLDOWN = "\uE011";
    private static final String ICON_MANA     = "\uE012";

    public ActionBarTask(AnimeMagicPlugin plugin) { this.plugin = plugin; }

    @Override
    public void run() {
        if (!plugin.getConfig().getBoolean("gui.actionbar.enabled", true)) return;
        for (Player p : Bukkit.getOnlinePlayers()) {
            try { sendHud(p); }
            catch (Exception ex) {
                plugin.getLogger().warning("ActionBar HUD failed for "
                        + p.getName() + ": " + ex.getMessage());
            }
        }
    }

    private void sendHud(@NotNull Player p) {
        int slot = p.getInventory().getHeldItemSlot();
        String spellId = plugin.getControlManager().boundSpell(p.getUniqueId(), slot);
        String sneakId = plugin.getDefaultBindings() != null
                ? plugin.getDefaultBindings().sneakSpellFor(p.getUniqueId(), slot) : null;

        if (spellId == null && sneakId == null) {
            if (plugin.getConfig().getBoolean("gui.actionbar.show-when-empty", false)) {
                p.sendActionBar(plugin.getMessages().format("actionbar.empty",
                        "%slot%", String.valueOf(slot + 1)));
            }
            return;
        }

        Spell spell = spellId != null ? plugin.getSpellRegistry().get(spellId) : null;
        Spell sneak = sneakId != null ? plugin.getSpellRegistry().get(sneakId) : null;
        if (spell == null && sneak == null) return;

        UUID id = p.getUniqueId();
        boolean manaEnabled = plugin.getConfig().getBoolean("mana.enabled", true);

        StringBuilder sb = new StringBuilder();

        // School icon (PUA texture) + spell name
        Spell primary = spell != null ? spell : sneak;
        sb.append(schoolIcon(primary.school()));
        sb.append(" ").append(primary.displayName());

        // Mana icon (PUA texture) + cost/pool
        if (manaEnabled && primary.manaCost() > 0) {
            int cur = plugin.getManaManager().current(id);
            int max = plugin.getManaManager().max(id);
            String costColor = cur >= primary.manaCost() ? "§b" : "§c";
            sb.append("  ").append(ICON_MANA).append(" ").append(costColor)
              .append(primary.manaCost()).append("§7/§b").append(max);
        }

        // Cooldown icon (PUA texture)
        String cd = cooldownText(id, primary);
        sb.append("  ").append(cd);

        // Keybind text
        if (sneak != null && spell != null) {
            sb.append("  §7| §eL-Click §7/ §dShift: ").append(sneak.displayName());
        } else {
            sb.append("  §7| §eL-Click");
        }

        p.sendActionBar(sb.toString());
    }

    private String schoolIcon(Spell.SchoolId school) {
        return switch (school) {
            case NARUTO  -> "§6" + ICON_NARUTO;
            case TENSURA -> "§5" + ICON_TENSURA;
            case MUSHOKU -> "§3" + ICON_MUSHOKU;
            case ONEPIECE -> "§b" + ICON_ONEPIECE;
        };
    }

    private @NotNull String cooldownText(@NotNull UUID id, @NotNull Spell spell) {
        Long lastCast = plugin.getManaManager().lastSpellCast(id, spell.id());
        long now = System.currentTimeMillis();
        long cdMs = (long) (spell.cooldownMs() * plugin.getConfig().getDouble(
                "schools." + spell.school().configKey() + ".cooldown-multiplier", 1.0));
        if (lastCast != null) {
            long remaining = cdMs - (now - lastCast);
            if (remaining > 0) {
                long secs = (remaining + 999) / 1000;
                return ICON_COOLDOWN + " §c" + secs + "s";
            }
        }
        return ICON_READY + " §aReady";
    }
}
