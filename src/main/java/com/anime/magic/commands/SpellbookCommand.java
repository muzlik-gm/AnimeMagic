package com.anime.magic.commands;

import com.anime.magic.AnimeMagicPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/** One-liner command that opens the custom spellbook GUI. */
public final class SpellbookCommand implements CommandExecutor {
    private final AnimeMagicPlugin plugin;

    public SpellbookCommand(AnimeMagicPlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                              @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player p)) { plugin.getMessages().send(sender, "player-only"); return true; }
        plugin.getGuiManager().openSpellbook(p);
        return true;
    }
}
