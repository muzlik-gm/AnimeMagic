package com.anime.magic.hud;

import com.anime.magic.AnimeMagicPlugin;
import com.anime.magic.api.Spell;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Persistent action-bar HUD — plain text only (no emoji/symbols).
 *
 * <p>Minecraft's vanilla action-bar API only accepts plain text. Item
 * textures/sprites CANNOT be embedded in the action bar via the Bukkit
 * API — that requires NMS packets or a client-side mod. This class uses
 * clean text formatting with color codes only.</p>
 *
 * <p>Layout: [School] Spell Name  |  Mana: cost/max  |  CD: Xs/Ready  |  Key: L-Click/Shift</p>
 */
public final class ActionBarTask extends BukkitRunnable {
    private final AnimeMagicPlugin plugin;

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

        // School name in brackets + spell name
        Spell primary = spell != null ? spell : sneak;
        sb.append(schoolTag(primary.school()));
        sb.append(" ").append(primary.displayName());

        // Mana cost
        if (manaEnabled && primary.manaCost() > 0) {
            int cur = plugin.getManaManager().current(id);
            int max = plugin.getManaManager().max(id);
            String costColor = cur >= primary.manaCost() ? "§b" : "§c";
            sb.append("  §7| ").append(costColor).append(primary.manaCost())
              .append("§7/").append("§b").append(max).append(" §7mana");
        }

        // Cooldown
        String cd = cooldownText(id, primary);
        sb.append("  §7| ").append(cd);

        // Keybind
        if (sneak != null && spell != null) {
            sb.append("  §7| §eL-Click §7/ §dShift: ").append(sneak.displayName());
        } else {
            sb.append("  §7| §eL-Click");
        }

        p.sendActionBar(sb.toString());
    }

    private String schoolTag(Spell.SchoolId school) {
        return switch (school) {
            case NARUTO  -> "§6[Naruto]";
            case TENSURA -> "§5[Tensura]";
            case MUSHOKU -> "§3[Mushoku]";
            case ONEPIECE -> "§b[OnePiece]";
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
                return "§cCD: " + secs + "s";
            }
        }
        return "§aReady";
    }
}
