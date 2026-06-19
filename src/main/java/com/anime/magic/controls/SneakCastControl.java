package com.anime.magic.controls;

import com.anime.magic.AnimeMagicPlugin;
import com.anime.magic.api.Spell;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.jetbrains.annotations.NotNull;
import java.util.UUID;

/**
 * Sneak-Cast Control — cast a "sneak spell" by sneaking while looking at a target.
 * Each player can bind ONE spell as their "sneak spell" via /bind sneak <spell>.
 */
public final class SneakCastControl implements ControlScheme {
    private final AnimeMagicPlugin plugin;
    private final java.util.Map<UUID, String> sneakBindings = new java.util.HashMap<>();

    public SneakCastControl(AnimeMagicPlugin plugin) { this.plugin = plugin; }

    @Override public @NotNull String id() { return "sneak"; }
    @Override public @NotNull String displayName() { return "Sneak Cast"; }
    @Override public @NotNull String description() {
        return "Bind a spell that fires when you sneak while looking at a target.";
    }

    public void bind(@NotNull UUID playerId, String spellId) {
        if (spellId == null) sneakBindings.remove(playerId);
        else sneakBindings.put(playerId, spellId);
    }

    public String bound(@NotNull UUID playerId) { return sneakBindings.get(playerId); }

    @Override
    public void onSneak(@NotNull Player player, @NotNull PlayerToggleSneakEvent e) {
        if (!e.isSneaking()) return;
        String spellId = sneakBindings.get(player.getUniqueId());
        if (spellId == null) return;
        Spell spell = plugin.getSpellRegistry().get(spellId);
        if (spell == null) return;
        double range = plugin.getConfig().getDouble("controls.sneak.target-range", 25.0);
        var caster = new com.anime.magic.api.Caster(plugin, player, spell);
        if (caster.targetEntity(range) == null) return;
        e.setCancelled(true);
        var cs = new com.anime.magic.api.CastingService(plugin);
        var result = cs.cast(player, spell);
        if (result == com.anime.magic.api.CastingService.Result.SUCCESS) {
            plugin.getMessages().send(player, "spell.cast", "%spell%", spell.displayName());
        }
    }
}
