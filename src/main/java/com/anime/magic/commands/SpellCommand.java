package com.anime.magic.commands;

import com.anime.magic.AnimeMagicPlugin;
import com.anime.magic.api.CastingService;
import com.anime.magic.api.Spell;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import java.util.*;
import java.util.stream.Collectors;

/** Spell command: /spell <cast|learn|forget|list|info> [spell_id] */
public final class SpellCommand implements CommandExecutor, TabCompleter {
    private final AnimeMagicPlugin plugin;
    private static final List<String> SUBS = Arrays.asList("cast", "learn", "forget", "list", "info");

    public SpellCommand(AnimeMagicPlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                              @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player p)) { plugin.getMessages().send(sender, "player-only"); return true; }
        if (args.length == 0) { plugin.getMessages().sendNoPrefix(sender, "help.spell"); return true; }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "cast" -> {
                if (args.length < 2) { sender.sendMessage("§cUsage: /spell cast <id>"); return true; }
                Spell spell = plugin.getSpellRegistry().get(args[1]);
                if (spell == null) { plugin.getMessages().send(sender, "spell.unknown", "%id%", args[1]); return true; }
                CastingService cs = new CastingService(plugin);
                CastingService.Result result = cs.cast(p, spell);
                if (result == CastingService.Result.SUCCESS) {
                    plugin.getMessages().send(p, "spell.cast", "%spell%", spell.displayName());
                }
            }
            case "learn" -> {
                if (args.length < 2) { sender.sendMessage("§cUsage: /spell learn <id>"); return true; }
                Spell spell = plugin.getSpellRegistry().get(args[1]);
                if (spell == null) { plugin.getMessages().send(sender, "spell.unknown", "%id%", args[1]); return true; }
                plugin.getMessages().send(p, "spell.learned", "%spell%", spell.displayName());
            }
            case "forget" -> {
                if (args.length < 2) { sender.sendMessage("§cUsage: /spell forget <id>"); return true; }
                plugin.getMessages().send(p, "spell.forgotten", "%spell%", args[1]);
            }
            case "list" -> {
                List<Spell> known = plugin.getSpellRegistry().all().stream()
                        .filter(s -> s.id().contains(":"))
                        .sorted((a, b) -> a.id().compareTo(b.id()))
                        .collect(Collectors.toList());
                plugin.getMessages().send(p, "spell.list.header", "%count%", String.valueOf(known.size()));
                for (Spell s : known) {
                    plugin.getMessages().sendNoPrefix(p, "spell.list.entry",
                            "%id%", s.id(), "%school%", s.school().name(), "%name%", s.displayName());
                }
            }
            case "info" -> {
                if (args.length < 2) { sender.sendMessage("§cUsage: /spell info <id>"); return true; }
                Spell s = plugin.getSpellRegistry().get(args[1]);
                if (s == null) { plugin.getMessages().send(sender, "spell.unknown", "%id%", args[1]); return true; }
                plugin.getMessages().sendNoPrefix(p, "spell.info",
                        "%name%", s.displayName(), "%school%", s.school().name(),
                        "%mana%", String.valueOf(s.manaCost()),
                        "%cd%", String.valueOf(s.cooldownMs() / 1000), "%desc%", s.description());
            }
            default -> plugin.getMessages().sendNoPrefix(sender, "help.spell");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                       @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            return SUBS.stream().filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).collect(Collectors.toList());
        }
        if (args.length == 2) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return plugin.getSpellRegistry().all().stream()
                    .map(Spell::id).filter(id -> id.contains(":"))
                    .filter(id -> id.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}
