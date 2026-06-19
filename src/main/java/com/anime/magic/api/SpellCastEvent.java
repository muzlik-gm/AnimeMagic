package com.anime.magic.api;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/** Fired synchronously BEFORE a spell's cast() method. Cancel to prevent the cast. */
public class SpellCastEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player caster;
    private final Spell spell;
    private boolean cancelled;

    public SpellCastEvent(Player caster, Spell spell) { this.caster = caster; this.spell = spell; }

    public Player getCaster() { return caster; }
    public Spell getSpell() { return spell; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancel) { this.cancelled = cancel; }
    @NotNull @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
