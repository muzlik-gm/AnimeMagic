package com.anime.magic.controls;

import com.anime.magic.AnimeMagicPlugin;
import com.anime.magic.api.CastingService;
import com.anime.magic.api.Spell;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;
import java.util.*;

/**
 * Spell Wheel Control — when the player sneak + right-clicks (configurable), a small
 * inventory GUI opens showing their most-recently-used spells in a wheel layout.
 * Clicking a slot casts that spell. Auto-closes after 5 seconds.
 */
public final class SpellWheelControl implements ControlScheme {
    private final AnimeMagicPlugin plugin;
    private final Map<UUID, LinkedList<String>> mru = new HashMap<>();
    private static final int MRU_SIZE = 8;

    public SpellWheelControl(AnimeMagicPlugin plugin) { this.plugin = plugin; }

    @Override public @NotNull String id() { return "wheel"; }
    @Override public @NotNull String displayName() { return "Spell Wheel"; }
    @Override public @NotNull String description() {
        return "Sneak + right-click to open a wheel of your most-recently-used spells.";
    }

    @Override
    public void onInteract(@NotNull Player player, @NotNull org.bukkit.event.player.PlayerInteractEvent e) {
        if (!player.isSneaking()) return;
        if (e.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_AIR
                && e.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;
        e.setCancelled(true);
        openWheel(player);
    }

    public void openWheel(Player player) {
        List<Spell> recent = collectRecent(player);
        if (recent.isEmpty()) {
            for (Spell s : plugin.getSpellRegistry().all()) {
                if (s.id().contains(":")) recent.add(s);
            }
        }
        SpellWheelGUI gui = new SpellWheelGUI(plugin, player, recent);
        gui.open();
        new BukkitRunnable() {
            @Override public void run() {
                if (player.getOpenInventory().getTopInventory().getHolder() instanceof SpellWheelGUI) {
                    player.closeInventory();
                }
            }
        }.runTaskLater(plugin, 100L);
    }

    private List<Spell> collectRecent(Player player) {
        LinkedList<String> ids = mru.get(player.getUniqueId());
        List<Spell> out = new ArrayList<>();
        if (ids == null) return out;
        for (String id : ids) {
            Spell s = plugin.getSpellRegistry().get(id);
            if (s != null) out.add(s);
            if (out.size() >= 8) break;
        }
        return out;
    }

    public void recordUse(@NotNull UUID playerId, @NotNull String spellId) {
        LinkedList<String> list = mru.computeIfAbsent(playerId, k -> new LinkedList<>());
        list.remove(spellId);
        list.addFirst(spellId);
        while (list.size() > MRU_SIZE) list.removeLast();
    }

    public List<String> mru(@NotNull UUID playerId) {
        return Collections.unmodifiableList(mru.getOrDefault(playerId, new LinkedList<>()));
    }

    public void onWheelClick(Player player, Spell spell) {
        player.closeInventory();
        Bukkit.getScheduler().runTask(plugin, () -> {
            CastingService cs = new CastingService(plugin);
            CastingService.Result result = cs.cast(player, spell);
            if (result == CastingService.Result.SUCCESS) {
                recordUse(player.getUniqueId(), spell.id());
                plugin.getMessages().send(player, "spell.cast", "%spell%", spell.displayName());
            }
        });
    }
}
