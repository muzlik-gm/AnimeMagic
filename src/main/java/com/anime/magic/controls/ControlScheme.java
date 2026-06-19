package com.anime.magic.controls;

import com.anime.magic.api.Spell;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Contract for a control scheme — a way the player can trigger spells outside of (or
 * in addition to) the standard /spell cast command. Each scheme implements the events
 * it cares about; ControlManager routes raw Bukkit events to every registered scheme.
 */
public interface ControlScheme {
    @NotNull String id();
    default void onInteract(@NotNull Player player, @NotNull PlayerInteractEvent event) {}
    default void onSlotChange(@NotNull Player player, @NotNull PlayerItemHeldEvent event) {}
    default void onSneak(@NotNull Player player, @NotNull PlayerToggleSneakEvent event) {}
    default void tick(@NotNull Player player) {}
    @NotNull String displayName();
    @NotNull String description();
}
