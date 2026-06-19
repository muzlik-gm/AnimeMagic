package com.anime.magic.controls;

import com.anime.magic.AnimeMagicPlugin;
import com.anime.magic.api.CastingService;
import com.anime.magic.api.Spell;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.jetbrains.annotations.NotNull;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * <b>Double-Jump Cast Control</b> — cast a "mobility spell" by double-tapping jump.
 *
 * <p>Bind via {@code /bind doublejump <spell>}. When the player presses jump while
 * already airborne (i.e. attempts a double-jump), the bound spell fires and the
 * double-jump is cancelled (so the player keeps moving in their current direction).</p>
 *
 * <p>Designed for mobility/utility spells: Gomu Gomu Pistol (dash punch), Chidori
 * (lightning dash), Rasengan (leap strike), Magicule Blade (blink slash).</p>
 *
 * <p>Default cooldown: 3 seconds between triggers to prevent spam.</p>
 */
public final class DoubleJumpCastControl implements ControlScheme, Listener {
    private final AnimeMagicPlugin plugin;
    private final Map<UUID, String> bindings = new HashMap<>();
    private final Map<UUID, Long> lastTriggered = new HashMap<>();
    private static final long COOLDOWN_MS = 3000;

    public DoubleJumpCastControl(AnimeMagicPlugin plugin) { this.plugin = plugin; }

    @Override public @NotNull String id() { return "doublejump"; }
    @Override public @NotNull String displayName() { return "Double-Jump Cast"; }
    @Override public @NotNull String description() {
        return "Cast a bound mobility spell by attempting a double-jump in mid-air.";
    }

    public void bind(@NotNull UUID playerId, String spellId) {
        if (spellId == null) bindings.remove(playerId);
        else bindings.put(playerId, spellId);
    }

    public String bound(@NotNull UUID playerId) { return bindings.get(playerId); }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onToggleFlight(PlayerToggleFlightEvent e) {
        Player p = e.getPlayer();
        // Only fire on the "trying to start flying" event, which Minecraft fires
        // when a survival player tries to double-jump (since they can't actually fly).
        if (e.isFlying()) {
            String spellId = bindings.get(p.getUniqueId());
            if (spellId == null) return;
            // Cooldown check
            Long last = lastTriggered.get(p.getUniqueId());
            long now = System.currentTimeMillis();
            if (last != null && now - last < COOLDOWN_MS) return;

            Spell spell = plugin.getSpellRegistry().get(spellId);
            if (spell == null) return;

            e.setCancelled(true); // prevent actual flight toggle
            lastTriggered.put(p.getUniqueId(), now);

            CastingService cs = new CastingService(plugin);
            CastingService.Result result = cs.cast(p, spell);
            if (result == CastingService.Result.SUCCESS) {
                plugin.getMessages().send(p, "spell.cast", "%spell%", spell.displayName());
            }
        }
    }
}
