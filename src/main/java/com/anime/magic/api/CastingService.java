package com.anime.magic.api;

import com.anime.magic.AnimeMagicPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Single entry point for casting spells. Enforces permission, world blacklist,
 * global/spell cooldowns, mana, and fires SpellCastEvent. All other code (commands,
 * listeners, arena logic, item right-clicks) MUST go through this service.
 *
 * <p>Thread-safety: must be invoked from the main server thread only. An off-thread
 * call is logged and refused to prevent async world mutation.</p>
 */
public final class CastingService {
    private final AnimeMagicPlugin plugin;

    public CastingService(AnimeMagicPlugin plugin) { this.plugin = plugin; }

    public Result cast(@NotNull Player player, @NotNull Spell spell) {
        // Main-thread guard — calling Bukkit world API from async corrupts chunks.
        if (!Bukkit.isPrimaryThread()) {
            plugin.getLogger().warning("CastingService.cast() invoked off-thread by "
                    + player.getName() + " for spell " + spell.id() + " — refusing.");
            return Result.CANCELLED;
        }

        if (player.isDead()) return Result.CANCELLED;

        if (!player.hasPermission(spell.permission())) {
            plugin.getMessages().send(player, "no-permission");
            return Result.NO_PERMISSION;
        }
        // Case-insensitive disabled-world check
        String worldName = player.getWorld().getName();
        boolean disabled = plugin.getConfig().getStringList("general.disabled-worlds")
                .stream().anyMatch(w -> w != null && w.equalsIgnoreCase(worldName));
        if (disabled) {
            plugin.getMessages().send(player, "spell.disabled-world");
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
            long leftMs = spellCd - (now - lastSpell);
            long left = (leftMs + 999) / 1000; // ceil division so 1ms remaining shows as "1s"
            plugin.getMessages().send(player, "spell.cooldown",
                    "%spell%", spell.displayName(), "%left%", String.valueOf(left));
            return Result.SPELL_COOLDOWN;
        }

        // Mana check — skipped entirely when mana.enabled is false. The config
        // flag was previously ignored, so setting mana.enabled: false still
        // consumed mana. Now it's respected everywhere.
        boolean manaEnabled = plugin.getConfig().getBoolean("mana.enabled", true);
        if (manaEnabled && spell.manaCost() > 0
                && !plugin.getManaManager().hasEnough(id, spell.manaCost())) {
            plugin.getMessages().send(player, "mana.insufficient",
                    "%need%", String.valueOf(spell.manaCost()),
                    "%have%", String.valueOf(plugin.getManaManager().current(id)));
            return Result.NO_MANA;
        }

        SpellCastEvent event = new SpellCastEvent(player, spell);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return Result.CANCELLED;

        // Only consume mana when mana is enabled in config.
        if (manaEnabled && spell.manaCost() > 0) plugin.getManaManager().consume(id, spell.manaCost());

        // Always record the cooldown BEFORE cast() so an exception or a `return false`
        // cannot be used to spam-cast a spell. Mana is refunded on failure, but the
        // cooldown is preserved (matches MMO convention: failed casts go on cooldown).
        plugin.getManaManager().recordCast(id, spell.id(), now);

        boolean ok;
        try {
            ok = spell.cast(new Caster(plugin, player, spell));
        } catch (Throwable t) {
            // Never let a buggy Spell implementation crash the caller (listener, command).
            plugin.getLogger().log(Level.WARNING,
                    "Spell " + spell.id() + " threw during cast for " + player.getName(), t);
            // Refund mana — cooldown stays. Only when mana is enabled.
            if (manaEnabled && spell.manaCost() > 0) plugin.getManaManager().add(id, spell.manaCost());
            return Result.FAILED;
        }

        if (!ok) {
            // Spell reported failure — refund mana, keep cooldown.
            if (manaEnabled && spell.manaCost() > 0) plugin.getManaManager().add(id, spell.manaCost());
            return Result.ABORTED;
        }

        // Bump MRU for the spell wheel
        var wheel = plugin.getControlManager() != null ? plugin.getControlManager().get("wheel") : null;
        if (wheel instanceof com.anime.magic.controls.SpellWheelControl swc) {
            swc.recordUse(id, spell.id());
        }
        return Result.SUCCESS;
    }

    public enum Result {
        SUCCESS, NO_PERMISSION, DISABLED_WORLD, GLOBAL_COOLDOWN, SPELL_COOLDOWN,
        NO_MANA, CANCELLED, ABORTED, FAILED
    }
}
