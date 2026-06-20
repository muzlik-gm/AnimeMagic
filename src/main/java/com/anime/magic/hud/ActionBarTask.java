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
 * Persistent action-bar HUD — shows a clean, minimal display of the ability
 * bound to the player's currently selected hotbar slot.
 *
 * <p>Design (kept intentionally simple):
 * <ul>
 *   <li><b>Ready:</b>      {@code §e❯ §fFireball Jutsu}</li>
 *   <li><b>Cooldown:</b>   {@code §e❯ §fFireball Jutsu §7(§c3s§7)}</li>
 *   <li><b>No mana:</b>    {@code §e❯ §fFireball Jutsu §7(§cNo Mana§7)}</li>
 *   <li><b>No binding:</b> nothing (frees the action bar for chat/other plugins)</li>
 * </ul>
 * </p>
 *
 * <p>When mana is disabled in config ({@code mana.enabled: false}), the "No Mana"
 * indicator is suppressed — spells always cast.</p>
 *
 * <p>Transient prompts (combo progress, look-cast channeling, cast bars) take
 * precedence — they send their own action-bar messages which overwrite this HUD
 * for the duration. The next tick of this task restores the HUD.</p>
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

        if (spellId == null) {
            // Slot has no binding — show nothing (or the empty prompt if enabled).
            if (plugin.getConfig().getBoolean("gui.actionbar.show-when-empty", false)) {
                p.sendActionBar(plugin.getMessages().format("actionbar.empty",
                        "%slot%", String.valueOf(slot + 1)));
            }
            return;
        }

        Spell spell = plugin.getSpellRegistry().get(spellId);
        if (spell == null) return;

        UUID id = p.getUniqueId();
        boolean manaEnabled = plugin.getConfig().getBoolean("mana.enabled", true);

        StringBuilder sb = new StringBuilder();
        // Chevron prefix + spell name (school-colored via displayName())
        sb.append("§e❯ §r").append(spell.displayName());

        // Status indicator on the right
        String status = statusText(id, spell, manaEnabled);
        if (status != null) {
            sb.append(" §7").append(status);
        }

        p.sendActionBar(sb.toString());
    }

    private @Nullable String statusText(@NotNull UUID id, @NotNull Spell spell, boolean manaEnabled) {
        // Cooldown takes priority
        Long lastCast = plugin.getManaManager().lastSpellCast(id, spell.id());
        long now = System.currentTimeMillis();
        long cdMs = (long) (spell.cooldownMs() * plugin.getConfig().getDouble(
                "schools." + spell.school().configKey() + ".cooldown-multiplier", 1.0));
        if (lastCast != null) {
            long remaining = cdMs - (now - lastCast);
            if (remaining > 0) {
                long secs = (remaining + 999) / 1000;
                return "(§c" + secs + "s§7)";
            }
        }
        // No cooldown — check mana
        if (manaEnabled && spell.manaCost() > 0) {
            int cur = plugin.getManaManager().current(id);
            if (cur < spell.manaCost()) {
                return "(§cNo Mana§7)";
            }
        }
        // Ready — no extra text (keeps the bar clean)
        return null;
    }
}
