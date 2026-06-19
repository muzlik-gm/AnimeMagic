package com.anime.magic.commands;

import com.anime.magic.AnimeMagicPlugin;
import com.anime.magic.controls.ComboControl;
import com.anime.magic.controls.DoubleJumpCastControl;
import com.anime.magic.controls.LookCastControl;
import com.anime.magic.controls.SneakCastControl;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import java.util.*;
import java.util.stream.Collectors;

/** Bind command: maps a control scheme slot/sequence to a spell. */
public final class BindCommand implements CommandExecutor, TabCompleter {
    private final AnimeMagicPlugin plugin;
    private static final List<String> SUBS = Arrays.asList("hotbar", "sneak", "combo", "look", "doublejump", "list", "help");

    public BindCommand(AnimeMagicPlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                              @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player p)) { plugin.getMessages().send(sender, "player-only"); return true; }
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) { sendHelp(sender); return true; }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "hotbar" -> {
                if (args.length < 2) { sender.sendMessage("§cUsage: /bind hotbar <0-8> [spell_id]"); return true; }
                int slot;
                try { slot = Integer.parseInt(args[1]); }
                catch (NumberFormatException e) { sender.sendMessage("§cSlot must be a number 0-8."); return true; }
                if (slot < 0 || slot > 8) { sender.sendMessage("§cSlot must be 0-8."); return true; }
                String spellId = args.length >= 3 ? args[2] : null;
                if (spellId != null && plugin.getSpellRegistry().get(spellId) == null) {
                    plugin.getMessages().send(sender, "spell.unknown", "%id%", spellId); return true;
                }
                plugin.getControlManager().bindHotbar(p.getUniqueId(), slot, spellId);
                if (spellId == null) plugin.getMessages().send(p, "controls.bind.cleared", "%slot%", String.valueOf(slot));
                else {
                    var spell = plugin.getSpellRegistry().get(spellId);
                    plugin.getMessages().send(p, "controls.bind.hotbar.set",
                            "%slot%", String.valueOf(slot), "%spell%", spell.displayName());
                }
                plugin.getControlManager().save();
            }
            case "sneak" -> {
                var sc = (SneakCastControl) plugin.getControlManager().get("sneak");
                if (sc == null) { sender.sendMessage("§cSneak control not enabled."); return true; }
                String spellId = args.length >= 2 ? args[1] : null;
                if (spellId != null && plugin.getSpellRegistry().get(spellId) == null) {
                    plugin.getMessages().send(sender, "spell.unknown", "%id%", spellId); return true;
                }
                sc.bind(p.getUniqueId(), spellId);
                if (spellId == null) plugin.getMessages().send(p, "controls.bind.sneak.cleared");
                else {
                    var spell = plugin.getSpellRegistry().get(spellId);
                    plugin.getMessages().send(p, "controls.bind.sneak.set", "%spell%", spell.displayName());
                }
                plugin.getControlManager().save();
            }
            case "combo" -> {
                var cc = (ComboControl) plugin.getControlManager().get("combo");
                if (cc == null) { sender.sendMessage("§cCombo control not enabled."); return true; }
                if (args.length < 2) { sender.sendMessage("§cUsage: /bind combo <L/R sequence> [spell_id]"); return true; }
                String seq = args[1].toUpperCase(Locale.ROOT);
                if (!seq.matches("[LR]+")) { sender.sendMessage("§cSequence must be only L and R characters."); return true; }
                String spellId = args.length >= 3 ? args[2] : null;
                if (spellId != null && plugin.getSpellRegistry().get(spellId) == null) {
                    plugin.getMessages().send(sender, "spell.unknown", "%id%", spellId); return true;
                }
                cc.bind(p.getUniqueId(), seq, spellId);
                if (spellId == null) plugin.getMessages().send(p, "controls.bind.combo.cleared", "%seq%", seq);
                else {
                    var spell = plugin.getSpellRegistry().get(spellId);
                    plugin.getMessages().send(p, "controls.bind.combo.set", "%seq%", seq, "%spell%", spell.displayName());
                }
                plugin.getControlManager().save();
            }
            case "list" -> listBindings(p);
            case "look" -> {
                var lc = (LookCastControl) plugin.getControlManager().get("look");
                if (lc == null) { sender.sendMessage("§cLook-cast control not enabled."); return true; }
                String spellId = args.length >= 2 ? args[1] : null;
                if (spellId != null && plugin.getSpellRegistry().get(spellId) == null) {
                    plugin.getMessages().send(sender, "spell.unknown", "%id%", spellId); return true;
                }
                lc.bind(p.getUniqueId(), spellId);
                if (spellId == null) sender.sendMessage("§aCleared look-cast binding.");
                else {
                    var spell = plugin.getSpellRegistry().get(spellId);
                    sender.sendMessage("§aBound look-cast to §e" + spell.displayName());
                }
            }
            case "doublejump" -> {
                var djc = (DoubleJumpCastControl) plugin.getControlManager().get("doublejump");
                if (djc == null) { sender.sendMessage("§cDouble-jump control not enabled."); return true; }
                String spellId = args.length >= 2 ? args[1] : null;
                if (spellId != null && plugin.getSpellRegistry().get(spellId) == null) {
                    plugin.getMessages().send(sender, "spell.unknown", "%id%", spellId); return true;
                }
                djc.bind(p.getUniqueId(), spellId);
                if (spellId == null) sender.sendMessage("§aCleared double-jump binding.");
                else {
                    var spell = plugin.getSpellRegistry().get(spellId);
                    sender.sendMessage("§aBound double-jump to §e" + spell.displayName());
                }
            }
            default -> sendHelp(sender);
        }
        return true;
    }

    private void listBindings(Player p) {
        p.sendMessage("§d=== §eYour Bindings §d===");
        var hotbar = plugin.getControlManager().bindings(p.getUniqueId());
        if (!hotbar.isEmpty()) {
            p.sendMessage("§6Hotbar:");
            for (var e : hotbar.entrySet()) {
                var s = plugin.getSpellRegistry().get(e.getValue());
                p.sendMessage("  §7Slot §e" + e.getKey() + " §7-> §e" + (s != null ? s.displayName() : e.getValue()));
            }
        }
        var sc = (SneakCastControl) plugin.getControlManager().get("sneak");
        if (sc != null) {
            String s = sc.bound(p.getUniqueId());
            if (s != null) {
                var sp = plugin.getSpellRegistry().get(s);
                p.sendMessage("§6Sneak: §e" + (sp != null ? sp.displayName() : s));
            }
        }
        var cc = (ComboControl) plugin.getControlManager().get("combo");
        if (cc != null) {
            var combos = cc.bindings(p.getUniqueId());
            if (!combos.isEmpty()) {
                p.sendMessage("§6Combos:");
                for (var e : combos.entrySet()) {
                    var sp = plugin.getSpellRegistry().get(e.getValue());
                    p.sendMessage("  §e" + e.getKey() + " §7-> §e" + (sp != null ? sp.displayName() : e.getValue()));
                }
            }
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§d=== §e/bind §d===");
        sender.sendMessage("§e/bind hotbar <0-8> [spell_id] §7- bind/clear a hotbar slot");
        sender.sendMessage("§e/bind sneak [spell_id] §7- bind/clear sneak-cast");
        sender.sendMessage("§e/bind combo <L/R seq> [spell_id] §7- bind/clear a hand-seal combo");
        sender.sendMessage("§e/bind look [spell_id] §7- bind/clear look-cast (hold sneak + look at target 1.5s)");
        sender.sendMessage("§e/bind doublejump [spell_id] §7- bind/clear double-jump cast");
        sender.sendMessage("§e/bind list §7- show your bindings");
        sender.sendMessage("§7Example: §e/bind hotbar 1 naruto:fireball");
        sender.sendMessage("§7Example: §e/bind combo LRL naruto:chidori");
        sender.sendMessage("§7Example: §e/bind look onepiece:conquerors_haki");
        sender.sendMessage("§7Example: §e/bind doublejump onepiece:gomu_pistol");
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                       @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) return SUBS.stream().filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).collect(Collectors.toList());
        if (args.length >= 2 && args[0].equalsIgnoreCase("hotbar")) {
            if (args.length == 2) return List.of("0","1","2","3","4","5","6","7","8");
            if (args.length == 3) return spellSuggestions(args[2]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("sneak")) return spellSuggestions(args[1]);
        if (args.length == 2 && args[0].equalsIgnoreCase("look")) return spellSuggestions(args[1]);
        if (args.length == 2 && args[0].equalsIgnoreCase("doublejump")) return spellSuggestions(args[1]);
        if (args.length >= 2 && args[0].equalsIgnoreCase("combo")) {
            if (args.length == 2) return List.of("L", "R", "LR", "RL", "LLR", "LRL", "RLR", "RRL");
            if (args.length == 3) return spellSuggestions(args[2]);
        }
        return new ArrayList<>();
    }

    private List<String> spellSuggestions(String prefix) {
        return plugin.getSpellRegistry().all().stream().map(s -> s.id()).filter(id -> id.contains(":"))
                .filter(id -> id.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT)))
                .collect(Collectors.toList());
    }
}
