package com.anime.magic.api;

import com.anime.magic.AnimeMagicPlugin;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.UUID;

/**
 * Snapshot of the player casting a spell. Fresh per cast call; not thread-safe —
 * main thread only.
 */
public final class Caster {
    private final AnimeMagicPlugin plugin;
    private final Player player;
    private final Spell spell;

    public Caster(@NotNull AnimeMagicPlugin plugin, @NotNull Player player, @NotNull Spell spell) {
        this.plugin = plugin; this.player = player; this.spell = spell;
    }

    public @NotNull AnimeMagicPlugin plugin() { return plugin; }
    public @NotNull Player player() { return player; }
    public @NotNull UUID uuid() { return player.getUniqueId(); }
    public @NotNull Spell spell() { return spell; }
    public @NotNull Location eyeLocation() { return player.getEyeLocation(); }
    public @NotNull Location location() { return player.getLocation(); }

    public @Nullable LivingEntity targetEntity(double range) {
        var ray = player.getWorld().rayTraceEntities(
                player.getEyeLocation(), player.getLocation().getDirection(), range, 0.5,
                e -> e instanceof LivingEntity && !e.getUniqueId().equals(player.getUniqueId()));
        return ray == null ? null : (LivingEntity) ray.getHitEntity();
    }

    public void consumeMana(int amount) { plugin.getManaManager().consume(player.getUniqueId(), amount); }
    public void refundMana(int amount) { plugin.getManaManager().add(player.getUniqueId(), amount); }
}
