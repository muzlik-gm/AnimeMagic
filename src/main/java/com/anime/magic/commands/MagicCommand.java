package com.anime.magic.commands;

import com.anime.magic.AnimeMagicPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import java.util.*;
import java.util.stream.Collectors;

/** Master command: /magic <reload|info|give|version> [args...] */
public final class MagicCommand implements CommandExecutor, TabCompleter {
    private final AnimeMagicPlugin plugin;
    private static final List<String> SUBCOMMANDS = Arrays.asList("help", "reload", "info", "give", "version");

    public MagicCommand(AnimeMagicPlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                              @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) { sendHelp(sender, label); return true; }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "help" -> sendHelp(sender, label);
            case "reload" -> {
                if (!sender.hasPermission("animemagic.admin")) {
                    plugin.getMessages().send(sender, "no-permission");
                    return true;
                }
                plugin.reload(sender);
            }
            case "info" -> {
                sender.sendMessage("§d=== §eAnimeMagic §d===");
                sender.sendMessage("§7Version: §e" + plugin.getDescription().getVersion());
                sender.sendMessage("§7Server: §e" + plugin.getVersionManager());
                sender.sendMessage("§7Spells loaded: §e" + plugin.getSpellRegistry().size());
                sender.sendMessage("§7Models loaded: §e" + plugin.getModelRegistry().size());
                sender.sendMessage("§7Animations loaded: §e" + plugin.getAnimationRegistry().size());
                sender.sendMessage("§7Arenas: §e" + plugin.getArenaManager().all().size());
            }
            case "version" -> sender.sendMessage("§e" + plugin.getDescription().getVersion());
            case "give" -> {
                if (!sender.hasPermission("animemagic.admin")) {
                    plugin.getMessages().send(sender, "no-permission");
                    return true;
                }
                if (args.length < 3) { sender.sendMessage("§cUsage: /magic give <player> <spell_id>"); return true; }
                var target = plugin.getServer().getPlayer(args[1]);
                if (target == null) { sender.sendMessage("§cPlayer not found: " + args[1]); return true; }
                var spell = plugin.getSpellRegistry().get(args[2]);
                if (spell == null) { plugin.getMessages().send(sender, "spell.unknown", "%id%", args[2]); return true; }
                plugin.getMessages().send(target, "spell.learned", "%spell%", spell.displayName());
                sender.sendMessage("§aForce-taught §e" + spell.displayName() + " §ato §e" + target.getName());
            }
            default -> sendHelp(sender, label);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                       @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            return SUBCOMMANDS.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            return plugin.getServer().getOnlinePlayers().stream()
                    .map(p -> p.getName())
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            return plugin.getSpellRegistry().all().stream()
                    .map(s -> s.id())
                    .filter(id -> id.toLowerCase(Locale.ROOT).startsWith(args[2].toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }

    private void sendHelp(CommandSender sender, String label) {
        plugin.getMessages().sendNoPrefix(sender, "help.header");
        plugin.getMessages().sendNoPrefix(sender, "help.magic");
    }
}
