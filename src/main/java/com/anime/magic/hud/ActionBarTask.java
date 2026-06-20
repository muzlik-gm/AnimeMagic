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
 * <p>Each spell has its OWN unique icon (not just a school icon). The icons
 * are mapped via Private Use Area codepoints in the resource pack's font
 * atlas. When the resource pack is applied, these render as actual textures.</p>
 */
public final class ActionBarTask extends BukkitRunnable {
    private final AnimeMagicPlugin plugin;

    // Status icons
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

        // Spell-specific icon (PUA texture) + spell name
        Spell primary = spell != null ? spell : sneak;
        sb.append(spellIcon(primary.id()));
        sb.append(" ").append(primary.displayName());

        // Mana icon + cost/pool
        if (manaEnabled && primary.manaCost() > 0) {
            int cur = plugin.getManaManager().current(id);
            int max = plugin.getManaManager().max(id);
            String costColor = cur >= primary.manaCost() ? "§b" : "§c";
            sb.append("  ").append(ICON_MANA).append(" ").append(costColor)
              .append(primary.manaCost()).append("§7/§b").append(max);
        }

        // Cooldown icon
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

    /** Get the spell-specific PUA icon codepoint based on the spell's full id. */
    private String spellIcon(String spellId) {
        if (spellId == null) return "";
        return switch (spellId) {
            // Naruto (U+E020–E028)
            case "naruto:fireball"       -> "\uE020";
            case "naruto:chidori"        -> "\uE021";
            case "naruto:rasengan"       -> "\uE022";
            case "naruto:rasenshuriken"  -> "\uE023";
            case "naruto:phoenix_flower" -> "\uE024";
            case "naruto:kirin"          -> "\uE025";
            case "naruto:sage_mode"      -> "\uE026";
            case "naruto:shadow_clone"   -> "\uE027";
            case "naruto:six_paths"      -> "\uE028";
            // Tensura (U+E030–E037)
            case "tensura:magicule_blade"  -> "\uE030";
            case "tensura:gluttony"        -> "\uE031";
            case "tensura:razor_edge"      -> "\uE032";
            case "tensura:beelzebuth"      -> "\uE033";
            case "tensura:disintegration"  -> "\uE034";
            case "tensura:megiddo"         -> "\uE035";
            case "tensura:raphael"         -> "\uE036";
            case "tensura:true_dragon"     -> "\uE037";
            // Mushoku (U+E040–E047)
            case "mushoku:saint_water"   -> "\uE040";
            case "mushoku:saint_fire"    -> "\uE041";
            case "mushoku:emperor_earth" -> "\uE042";
            case "mushoku:storm"         -> "\uE043";
            case "mushoku:atomic_flare"  -> "\uE044";
            case "mushoku:gravity"       -> "\uE045";
            case "mushoku:quake"         -> "\uE046";
            case "mushoku:time_warp"     -> "\uE047";
            // One Piece (U+E050–E057)
            case "onepiece:gomu_pistol"       -> "\uE050";
            case "onepiece:conquerors_haki"   -> "\uE051";
            case "onepiece:armament_haki"     -> "\uE052";
            case "onepiece:observation_haki"  -> "\uE053";
            case "onepiece:gear_second"       -> "\uE054";
            case "onepiece:gear_third"        -> "\uE055";
            case "onepiece:gear_fourth"       -> "\uE056";
            case "onepiece:voice_of_all_things" -> "\uE057";
            // Fallback: school icon
            default -> "\uE001"; // generic
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
