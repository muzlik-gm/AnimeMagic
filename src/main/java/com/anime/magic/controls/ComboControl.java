package com.anime.magic.controls;

import com.anime.magic.AnimeMagicPlugin;
import com.anime.magic.api.CastingService;
import com.anime.magic.api.Spell;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.jetbrains.annotations.NotNull;
import java.util.*;

/**
 * Combo (Hand-Seal) Control — bind a sequence of left/right clicks to a spell.
 * Inspired by Naruto hand seals — the player taps out a sequence like L-R-L
 * (left click, right click, left click) within a 1.5-second window to trigger
 * the bound spell.
 */
public final class ComboControl implements ControlScheme {
    private final AnimeMagicPlugin plugin;
    private final Map<UUID, ComboState> active = new HashMap<>();
    private static final long WINDOW_MS = 1500;

    public ComboControl(AnimeMagicPlugin plugin) { this.plugin = plugin; }

    @Override public @NotNull String id() { return "combo"; }
    @Override public @NotNull String displayName() { return "Hand-Seal Combo"; }
    @Override public @NotNull String description() {
        return "Tap L/R click sequences (hand seals) within 1.5s to cast bound spells.";
    }

    public void bind(@NotNull UUID playerId, @NotNull String sequence, String spellId) {
        Map<String, String> map = plugin.getControlManager().state(playerId, "combo");
        if (map == null) map = new HashMap<>();
        if (spellId == null) map.remove(sequence.toUpperCase());
        else map.put(sequence.toUpperCase(), spellId);
        plugin.getControlManager().state(playerId, "combo", map);
    }

    public Map<String, String> bindings(@NotNull UUID playerId) {
        Map<String, String> map = plugin.getControlManager().state(playerId, "combo");
        return map == null ? Collections.emptyMap() : Collections.unmodifiableMap(map);
    }

    @Override
    public void onInteract(@NotNull Player player, @NotNull PlayerInteractEvent e) {
        Action a = e.getAction();
        char code;
        if (a == Action.LEFT_CLICK_AIR || a == Action.LEFT_CLICK_BLOCK) code = 'L';
        else if (a == Action.RIGHT_CLICK_AIR || a == Action.RIGHT_CLICK_BLOCK) code = 'R';
        else return;

        UUID id = player.getUniqueId();
        Map<String, String> bindings = bindings(id);
        if (bindings.isEmpty()) return;

        long now = System.currentTimeMillis();
        ComboState state = active.get(id);
        if (state == null || now - state.lastClick > WINDOW_MS) {
            state = new ComboState();
            active.put(id, state);
        }
        state.sequence.append(code);
        state.lastClick = now;
        String current = state.sequence.toString();

        player.sendActionBar(plugin.getMessages().format("controls.combo.progress",
                "%combo%", current));

        if (bindings.containsKey(current)) {
            String spellId = bindings.get(current);
            Spell spell = plugin.getSpellRegistry().get(spellId);
            active.remove(id);
            if (spell == null) return;
            e.setCancelled(true);
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                CastingService cs = new CastingService(plugin);
                CastingService.Result result = cs.cast(player, spell);
                if (result == CastingService.Result.SUCCESS) {
                    plugin.getMessages().send(player, "spell.cast", "%spell%", spell.displayName());
                }
            });
            return;
        }

        boolean prefixPossible = false;
        for (String seq : bindings.keySet()) {
            if (seq.startsWith(current)) { prefixPossible = true; break; }
        }
        if (!prefixPossible) {
            active.remove(id);
            player.sendActionBar(plugin.getMessages().raw("controls.combo.reset"));
        }
    }

    @Override
    public void tick(@NotNull Player player) {
        ComboState state = active.get(player.getUniqueId());
        if (state == null) return;
        if (System.currentTimeMillis() - state.lastClick > WINDOW_MS) {
            active.remove(player.getUniqueId());
            player.sendActionBar(plugin.getMessages().raw("controls.combo.timeout"));
        }
    }

    private static final class ComboState {
        final StringBuilder sequence = new StringBuilder();
        long lastClick = System.currentTimeMillis();
    }
}
