package com.anime.magic.api;

import com.anime.magic.AnimeMagicPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import java.util.UUID;

/**
 * Single entry point for casting spells. Enforces permission, world blacklist,
 * global/spell cooldowns, mana, and fires SpellCastEvent. All other code (commands,
 * listeners, arena logic, item right-clicks) MUST go through this service.
 */
public final class CastingService {
    private final AnimeMagicPlugin plugin;

    public CastingService(AnimeMagicPlugin plugin) { this.plugin = plugin; }

    public Result cast(@NotNull Player player, @NotNull Spell spell) {
        if (!player.hasPermission(spell.permission())) {
            plugin.getMessages().send(player, "no-permission");
            return Result.NO_PERMISSION;
        }
        if (plugin.getConfig().getStringList("general.disabled-worlds").contains(player.getWorld().getName())) {
            return Result.DISABLED_WORLD;
        }
        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();

        Long lastGlobal = plugin.getManaManager().lastGlobalCast(id);
        long globalCd = plugin.getConfig().getLong("general.global-cast-cooldown-ms", 250L);
        if (lastGlobal != null && now - lastGlobal < globalCd) return Result.GLOBAL_COOLDOWN;

        Long lastSpell = plugin.getManaManager().lastSpellCast(id, spell.id());
        long spellCd = (long) (spell.cooldownMs() * plugin.getConfig().getDouble(
                "schools." + spell.school().configKey() + ".cooldown-multiplier", 1.0));
        if (lastSpell != null && now - lastSpell < spellCd) {
            long left = (spellCd - (now - lastSpell)) / 1000;
            plugin.getMessages().send(player, "spell.cooldown",
                    "%spell%", spell.displayName(), "%left%", String.valueOf(left + 1));
            return Result.SPELL_COOLDOWN;
        }

        if (spell.manaCost() > 0 && !plugin.getManaManager().hasEnough(id, spell.manaCost())) {
            plugin.getMessages().send(player, "mana.insufficient",
                    "%need%", String.valueOf(spell.manaCost()),
                    "%have%", String.valueOf(plugin.getManaManager().current(id)));
            return Result.NO_MANA;
        }

        SpellCastEvent event = new SpellCastEvent(player, spell);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return Result.CANCELLED;

        if (spell.manaCost() > 0) plugin.getManaManager().consume(id, spell.manaCost());

        boolean ok = spell.cast(new Caster(plugin, player, spell));
        if (!ok && spell.manaCost() > 0) {
            plugin.getManaManager().add(id, spell.manaCost());
            return Result.ABORTED;
        }

        plugin.getManaManager().recordCast(id, spell.id(), now);
        // Bump MRU for the spell wheel
        var wheel = plugin.getControlManager() != null ? plugin.getControlManager().get("wheel") : null;
        if (wheel instanceof com.anime.magic.controls.SpellWheelControl swc) {
            swc.recordUse(id, spell.id());
        }
        return Result.SUCCESS;
    }

    public enum Result {
        SUCCESS, NO_PERMISSION, DISABLED_WORLD, GLOBAL_COOLDOWN, SPELL_COOLDOWN,
        NO_MANA, CANCELLED, ABORTED
    }
}
