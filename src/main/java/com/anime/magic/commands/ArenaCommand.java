package com.anime.magic.commands;

import com.anime.magic.AnimeMagicPlugin;
import com.anime.magic.minigame.MagicArena;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import java.util.*;
import java.util.stream.Collectors;

/** Arena command: /arena <create|delete|join|leave|start|stop|list|info> [name] */
public final class ArenaCommand implements CommandExecutor, TabCompleter {
    private final AnimeMagicPlugin plugin;
    private static final List<String> SUBS = Arrays.asList("create", "delete", "join", "leave", "start", "stop", "list", "info");

    public ArenaCommand(AnimeMagicPlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                              @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) { plugin.getMessages().sendNoPrefix(sender, "help.arena"); return true; }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "create" -> {
                if (!sender.hasPermission("animemagic.arena.manage")) { plugin.getMessages().send(sender, "no-permission"); return true; }
                if (!(sender instanceof Player p)) { plugin.getMessages().send(sender, "player-only"); return true; }
                if (args.length < 2) { sender.sendMessage("§cUsage: /arena create <name>"); return true; }
                plugin.getArenaManager().create(args[1], p.getLocation(), p.getLocation());
                plugin.getMessages().send(sender, "arena.created", "%name%", args[1]);
            }
            case "delete" -> {
                if (!sender.hasPermission("animemagic.arena.manage")) { plugin.getMessages().send(sender, "no-permission"); return true; }
                if (args.length < 2) { sender.sendMessage("§cUsage: /arena delete <name>"); return true; }
                boolean ok = plugin.getArenaManager().delete(args[1]);
                if (ok) plugin.getMessages().send(sender, "arena.deleted", "%name%", args[1]);
                else plugin.getMessages().send(sender, "arena.no-exist", "%name%", args[1]);
            }
            case "join" -> {
                if (!(sender instanceof Player p)) { plugin.getMessages().send(sender, "player-only"); return true; }
                if (args.length < 2) { sender.sendMessage("§cUsage: /arena join <name>"); return true; }
                MagicArena a = plugin.getArenaManager().arena(args[1]);
                if (a == null) { plugin.getMessages().send(sender, "arena.no-exist", "%name%", args[1]); return true; }
                boolean ok = plugin.getArenaManager().join(args[1], p);
                if (!ok) plugin.getMessages().send(sender, "arena.full", "%name%", args[1]);
            }
            case "leave" -> {
                if (!(sender instanceof Player p)) { plugin.getMessages().send(sender, "player-only"); return true; }
                MagicArena a = plugin.getArenaManager().arenaOf(p.getUniqueId());
                if (a == null) { plugin.getMessages().send(sender, "arena.not-in"); return true; }
                plugin.getArenaManager().leave(p);
            }
            case "start" -> {
                if (!sender.hasPermission("animemagic.arena.manage")) { plugin.getMessages().send(sender, "no-permission"); return true; }
                if (args.length < 2) { sender.sendMessage("§cUsage: /arena start <name>"); return true; }
                MagicArena a = plugin.getArenaManager().arena(args[1]);
                if (a == null) { plugin.getMessages().send(sender, "arena.no-exist", "%name%", args[1]); return true; }
                a.start();
            }
            case "stop" -> {
                if (!sender.hasPermission("animemagic.arena.manage")) { plugin.getMessages().send(sender, "no-permission"); return true; }
                if (args.length < 2) { sender.sendMessage("§cUsage: /arena stop <name>"); return true; }
                MagicArena a = plugin.getArenaManager().arena(args[1]);
                if (a == null) { plugin.getMessages().send(sender, "arena.no-exist", "%name%", args[1]); return true; }
                a.stop();
            }
            case "list" -> {
                var arenas = plugin.getArenaManager().all();
                plugin.getMessages().send(sender, "arena.list.header", "%count%", String.valueOf(arenas.size()));
                for (MagicArena a : arenas) {
                    plugin.getMessages().sendNoPrefix(sender, "arena.list.entry",
                            "%name%", a.name(), "%state%", a.state().name(),
                            "%cur%", String.valueOf(a.playerCount()), "%max%", String.valueOf(a.maxPlayers()));
                }
            }
            case "info" -> {
                if (args.length < 2) { sender.sendMessage("§cUsage: /arena info <name>"); return true; }
                MagicArena a = plugin.getArenaManager().arena(args[1]);
                if (a == null) { plugin.getMessages().send(sender, "arena.no-exist", "%name%", args[1]); return true; }
                sender.sendMessage("§d=== §e" + a.name() + " §d===");
                sender.sendMessage("§7State: §e" + a.state());
                sender.sendMessage("§7Players: §e" + a.playerCount() + "/" + a.maxPlayers());
                sender.sendMessage("§7Spawn: §e" + (a.spawn() == null ? "(unset)" : a.spawn()));
            }
            default -> plugin.getMessages().sendNoPrefix(sender, "help.arena");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                       @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) return SUBS.stream().filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).collect(Collectors.toList());
        if (args.length == 2) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return plugin.getArenaManager().all().stream().map(MagicArena::name)
                    .filter(n -> n.startsWith(prefix)).collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}
