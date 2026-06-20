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
 * Persistent action-bar HUD with icon symbols.
 *
 * <p>Minecraft's vanilla action-bar API only accepts plain text — you cannot
 * embed real item sprites. We use Unicode symbols that render like icons
 * (most Minecraft fonts include them) to give the action bar an icon-led look
 * matching the user's reference image.</p>
 *
 * <p>Layout (left to right):
 * <ul>
 *   <li><b>School icon:</b> §6⚔ (Naruto) / §5✦ (Tensura) / §3❖ (Mushoku) / §b◈ (OnePiece)</li>
 *   <li><b>Spell name:</b> the spell's displayName() (school-colored)</li>
 *   <li><b>Mana icon:</b> §b◆ cost/max (suppressed when mana.enabled=false)</li>
 *   <li><b>Cooldown icon:</b> §a✓ Ready / §c⏲ 3s</li>
 *   <li><b>Keybind icon:</b> §e⟶ L-Click / §d⇧ Shift: Phoenix Flower</li>
 * </ul>
 * </p>
 *
 * <p>Example output:
 * <pre>
 *   §6⚔ §6Fireball Jutsu  §b◆ 25/100  §a✓ Ready  §e⟶ L-Click
 *   §6⚔ §6Fireball Jutsu  §b◆ 25/100  §c⏲ 3s  §e⟶ L-Click
 *   §5✦ §fMegiddo  §b◆ 150/1000  §c⏲ 45s  §e⟶ L-Click|§d⇧ Shift: True Dragon
 * </pre>
 * </p>
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

        // School icon (Unicode symbol) + spell name
        Spell primary = spell != null ? spell : sneak;
        sb.append(schoolIcon(primary.school()));
        sb.append(" ").append(primary.displayName());

        // Mana icon + cost/pool
        if (manaEnabled && primary.manaCost() > 0) {
            int cur = plugin.getManaManager().current(id);
            int max = plugin.getManaManager().max(id);
            String costColor = cur >= primary.manaCost() ? "§b" : "§c";
            sb.append("  ").append(costColor).append("◆")
              .append(" ").append(primary.manaCost())
              .append("§7/").append("§b").append(max);
        }

        // Cooldown icon
        String cd = cooldownText(id, primary);
        sb.append("  ").append(cd);

        // Keybind icon
        if (sneak != null && spell != null) {
            sb.append("  §e⟶ L-Click§8|§d⇧ Shift: ").append(sneak.displayName());
        } else {
            sb.append("  §e⟶ L-Click");
        }

        p.sendActionBar(sb.toString());
    }

    /** Unicode icon + color for each school. */
    private String schoolIcon(Spell.SchoolId school) {
        return switch (school) {
            case NARUTO  -> "§6⚔";   // orange sword
            case TENSURA -> "§5✦";   // purple star
            case MUSHOKU -> "§3❖";   // cyan diamond
            case ONEPIECE -> "§b◈";  // aqua lozenge
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
                return "§c⏲ " + secs + "s";
            }
        }
        return "§a✓ Ready";
    }
}
