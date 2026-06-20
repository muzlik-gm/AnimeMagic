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
 * Persistent action-bar HUD — shows the ability bound to the player's currently
 * selected hotbar slot, plus mana cost, cooldown, and keybind hints. Updates
 * every {@code gui.actionbar.update-ticks} ticks (default 10 = 2 Hz).
 *
 * <p>The HUD only appears when the selected slot has a bound spell. Slots with
 * no binding show nothing (so the action bar is free for other plugins / chat
 * notifications) unless {@code gui.actionbar.show-when-empty} is true.</p>
 *
 * <p>Layout example (with a Fireball Jutsu bound to slot 1):
 * <pre>
 *   ❯ [1] §6Fireball Jutsu   ◆ §b25§7/§b100   ⏲ §aReady   [§eL-Click§7]
 * </pre>
 * With a cooldown active:
 * <pre>
 *   ❯ [1] §6Fireball Jutsu   ◆ §b25§7/§b100   ⏲ §c3s   [§eL-Click§7]
 * </pre>
 * With a sneak-variant on the same slot:
 * <pre>
 *   ❯ [1] §6Fireball Jutsu   ◆ §b25§7/§b100   ⏲ §aReady   [§eL-Click§7|§dShift: Phoenix Flower§7]
 * </pre>
 * </p>
 *
 * <p>The combo-control progress and look-cast channel progress intentionally
 * take precedence: while a combo is being entered or a channel is in progress,
 * those controls send their own action-bar messages, which overwrite this HUD
 * for the duration. Once they finish, the next tick of this task restores the
 * HUD. This is the intended behavior — the HUD is "ambient" and yields to
 * transient prompts.</p>
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
                // Never let a buggy player state crash the repeating task.
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
            // Slot has no binding. Optionally show an "empty" prompt.
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
        int cur = plugin.getManaManager().current(id);
        int max = plugin.getManaManager().max(id);

        // ── Build the HUD line ───────────────────────────────────────────────
        StringBuilder sb = new StringBuilder();

        // Selected-slot marker + slot number (e.g. "❯ [1] ")
        sb.append(plugin.getMessages().format("actionbar.slot-marker",
                "%slot%", String.valueOf(slot + 1)));

        // Spell name (school-colored)
        if (spell != null) {
            sb.append(spell.displayName());
        } else if (sneak != null) {
            // Slot has only a sneak binding — show it as the primary.
            sb.append(sneak.displayName());
        }

        // Mana cost + pool (◆ 25/100)
        Spell shown = spell != null ? spell : sneak;
        int cost = shown.manaCost();
        if (cost > 0) {
            // Red if not enough mana, blue if affordable
            String costColor = cur >= cost ? "§b" : "§c";
            sb.append("  ").append(plugin.getMessages().format("actionbar.mana",
                    "%cost%", String.valueOf(cost),
                    "%current%", String.valueOf(cur),
                    "%max%", String.valueOf(max),
                    "%cost_color%", costColor));
        }

        // Cooldown (⏲ Ready / ⏲ 3s)
        String cdText = cooldownText(id, shown);
        sb.append("  ").append(cdText);

        // Keybind hint ([L-Click] or [L-Click|Shift: Phoenix Flower])
        String keybind = keybindText(spell, sneak);
        sb.append("  ").append(keybind);

        p.sendActionBar(sb.toString());
    }

    private @NotNull String cooldownText(@NotNull UUID id, @NotNull Spell spell) {
        Long lastCast = plugin.getManaManager().lastSpellCast(id, spell.id());
        if (lastCast == null) {
            return plugin.getMessages().format("actionbar.cd-ready");
        }
        long now = System.currentTimeMillis();
        long cdMs = (long) (spell.cooldownMs() * plugin.getConfig().getDouble(
                "schools." + spell.school().configKey() + ".cooldown-multiplier", 1.0));
        long remaining = cdMs - (now - lastCast);
        if (remaining <= 0) {
            return plugin.getMessages().format("actionbar.cd-ready");
        }
        long secs = (remaining + 999) / 1000;
        return plugin.getMessages().format("actionbar.cd-active",
                "%secs%", String.valueOf(secs));
    }

    private @NotNull String keybindText(@Nullable Spell spell, @Nullable Spell sneak) {
        if (sneak == null) {
            return plugin.getMessages().raw("actionbar.keybind-simple");
        }
        // Has a sneak-variant — show both triggers
        return plugin.getMessages().format("actionbar.keybind-with-sneak",
                "%sneak%", sneak.displayName());
    }
}
