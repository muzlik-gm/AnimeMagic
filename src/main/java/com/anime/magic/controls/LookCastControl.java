package com.anime.magic.controls;

import com.anime.magic.AnimeMagicPlugin;
import com.anime.magic.api.CastingService;
import com.anime.magic.api.Spell;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * <b>Look-Cast Control</b> — cast a "look spell" by holding the sneak key for 1.5 seconds
 * while looking at a target. The cast fires automatically when the channel completes.
 *
 * <p>Bind via {@code /bind look <spell>}. Useful for ultimate-type spells where the
 * player shouldn't have to fumble with hotbar slots in combat.</p>
 *
 * <p>State machine per player:
 * <ol>
 *   <li>Player starts sneaking → timer begins</li>
 *   <li>Each tick, check if player is still sneaking and looking at a target</li>
 *   <li>After 1.5s (30 ticks) of continuous sneak+look → cast fires</li>
 *   <li>If player stops sneaking or looks away → reset</li>
 * </ol></p>
 *
 * <p>Action bar shows channel progress: "Channeling [████░░░░░░] 1.0s / 1.5s"</p>
 */
public final class LookCastControl implements ControlScheme {
    private final AnimeMagicPlugin plugin;
    private final java.util.Map<UUID, String> lookBindings = new java.util.HashMap<>();
    private final java.util.Map<UUID, Integer> channelTicks = new java.util.HashMap<>();
    private static final int CHANNEL_REQUIRED = 30; // 1.5s at 20 TPS
    private static final double LOOK_RANGE = 30.0;

    public LookCastControl(AnimeMagicPlugin plugin) { this.plugin = plugin; }

    @Override public @NotNull String id() { return "look"; }
    @Override public @NotNull String displayName() { return "Look-Cast"; }
    @Override public @NotNull String description() {
        return "Hold sneak + look at a target for 1.5s to cast a bound ultimate spell.";
    }

    public void bind(@NotNull UUID playerId, String spellId) {
        if (spellId == null) lookBindings.remove(playerId);
        else lookBindings.put(playerId, spellId);
    }

    public String bound(@NotNull UUID playerId) { return lookBindings.get(playerId); }

    @Override
    public void tick(@NotNull Player player) {
        String spellId = lookBindings.get(player.getUniqueId());
        if (spellId == null) return;
        Spell spell = plugin.getSpellRegistry().get(spellId);
        if (spell == null) return;

        if (!player.isSneaking()) {
            channelTicks.remove(player.getUniqueId());
            return;
        }
        // Must be looking at a target
        var caster = new com.anime.magic.api.Caster(plugin, player, spell);
        if (caster.targetEntity(LOOK_RANGE) == null) {
            channelTicks.remove(player.getUniqueId());
            player.sendActionBar("§7» §cNo target — look at an entity to channel");
            return;
        }
        int ticks = channelTicks.merge(player.getUniqueId(), 1, Integer::sum);
        double progress = (double) ticks / CHANNEL_REQUIRED;
        // Progress bar
        int filled = (int) (progress * 10);
        StringBuilder bar = new StringBuilder("§d");
        for (int i = 0; i < 10; i++) bar.append(i < filled ? "█" : "§8░§d");
        double secs = ticks / 20.0;
        player.sendActionBar("§7» §dChanneling " + bar + " §e" + String.format("%.1f", secs) + "s / 1.5s");

        if (ticks >= CHANNEL_REQUIRED) {
            channelTicks.remove(player.getUniqueId());
            CastingService cs = new CastingService(plugin);
            CastingService.Result result = cs.cast(player, spell);
            if (result == CastingService.Result.SUCCESS) {
                plugin.getMessages().send(player, "spell.cast", "%spell%", spell.displayName());
            }
        }
    }
}
