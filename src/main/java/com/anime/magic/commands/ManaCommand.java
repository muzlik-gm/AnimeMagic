package com.anime.magic.commands;

import com.anime.magic.AnimeMagicPlugin;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import java.util.*;
import java.util.stream.Collectors;

/** Mana command: /mana <show|set|add|remove> [player] [amount] */
public final class ManaCommand implements CommandExecutor, TabCompleter {
    private final AnimeMagicPlugin plugin;
    private static final List<String> SUBS = Arrays.asList("show", "set", "add", "remove");

    public ManaCommand(AnimeMagicPlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                              @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) { sender.sendMessage("§cUsage: /mana <show|set|add|remove> [player] [amount]"); return true; }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "show" -> {
                Player target;
                if (args.length >= 2) {
                    target = Bukkit.getPlayer(args[1]);
                    if (target == null) { sender.sendMessage("§cPlayer not found: " + args[1]); return true; }
                } else {
                    if (!(sender instanceof Player p)) { plugin.getMessages().send(sender, "player-only"); return true; }
                    target = p;
                }
                if (target.getUniqueId().equals(sender instanceof Player p ? p.getUniqueId() : null)) {
                    plugin.getMessages().send(sender, "mana.show.self",
                            "%current%", String.valueOf(plugin.getManaManager().current(target.getUniqueId())),
                            "%max%", String.valueOf(plugin.getManaManager().max(target.getUniqueId())));
                } else {
                    plugin.getMessages().send(sender, "mana.show.other",
                            "%player%", target.getName(),
                            "%current%", String.valueOf(plugin.getManaManager().current(target.getUniqueId())),
                            "%max%", String.valueOf(plugin.getManaManager().max(target.getUniqueId())));
                }
            }
            case "set", "add", "remove" -> {
                if (!sender.hasPermission("animemagic.admin")) { plugin.getMessages().send(sender, "no-permission"); return true; }
                if (args.length < 3) { sender.sendMessage("§cUsage: /mana " + args[0] + " <player> <amount>"); return true; }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) { sender.sendMessage("§cPlayer not found: " + args[1]); return true; }
                int amount;
                try { amount = Integer.parseInt(args[2]); }
                catch (NumberFormatException e) { sender.sendMessage("§cAmount must be a number."); return true; }
                switch (args[0].toLowerCase(Locale.ROOT)) {
                    case "set" -> { plugin.getManaManager().set(target.getUniqueId(), amount);
                        plugin.getMessages().send(sender, "mana.set", "%player%", target.getName(), "%value%", String.valueOf(amount)); }
                    case "add" -> { plugin.getManaManager().add(target.getUniqueId(), amount);
                        plugin.getMessages().send(sender, "mana.add", "%player%", target.getName(), "%amount%", String.valueOf(amount)); }
                    case "remove" -> { plugin.getManaManager().consume(target.getUniqueId(), amount);
                        plugin.getMessages().send(sender, "mana.remove", "%player%", target.getName(), "%amount%", String.valueOf(amount)); }
                }
            }
            default -> sender.sendMessage("§cUsage: /mana <show|set|add|remove> [player] [amount]");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                       @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) return SUBS.stream().filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).collect(Collectors.toList());
        if (args.length == 2) return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))).collect(Collectors.toList());
        if (args.length == 3) return List.of("10", "50", "100");
        return new ArrayList<>();
    }
}
