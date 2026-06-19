package com.anime.magic.commands;

import com.anime.magic.AnimeMagicPlugin;
import com.anime.magic.api.Spell;
import com.anime.magic.controls.DefaultBindings;
import com.anime.magic.gui.MasteryGUI;
import com.anime.magic.gui.SchoolSelectorGUI;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * School command — open the school selector GUI, switch schools, or open the mastery tree.
 *   /school                        — open School Selector GUI
 *   /school <naruto|tensura|...>   — switch to that school's loadout
 *   /school mastery                — open the Mastery Tree GUI
 */
public final class SchoolCommand implements CommandExecutor, TabCompleter {
    private final AnimeMagicPlugin plugin;
    private static final List<String> SUBS = Arrays.asList(
            "naruto", "tensura", "mushoku", "onepiece", "mastery", "help");

    public SchoolCommand(AnimeMagicPlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                              @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player p)) {
            plugin.getMessages().send(sender, "player-only");
            return true;
        }
        if (args.length == 0) {
            // Open school selector GUI
            new SchoolSelectorGUI(plugin, p).open();
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "help" -> {
                sender.sendMessage("§d=== §e/school §d===");
                sender.sendMessage("§e/school §7- Open the school selector GUI");
                sender.sendMessage("§e/school <name> §7- Switch to that school's loadout");
                sender.sendMessage("§e/school mastery §7- Open the mastery tree GUI");
                sender.sendMessage("§7Schools: §6naruto §5tensura §3mushoku §bonepiece");
            }
            case "mastery" -> new MasteryGUI(plugin, p).open();
            case "naruto", "tensura", "mushoku", "onepiece" -> {
                Spell.SchoolId school = Spell.SchoolId.valueOf(args[0].toUpperCase(Locale.ROOT));
                plugin.getDefaultBindings().applyLoadout(p, school);
                String nameKey = "school." + school.configKey() + ".name";
                plugin.getMessages().send(p, "school.switched",
                        "%school%", plugin.getMessages().raw(nameKey));
            }
            default -> sender.sendMessage("§cUnknown subcommand. Try §e/school help");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                       @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            return SUBS.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}
