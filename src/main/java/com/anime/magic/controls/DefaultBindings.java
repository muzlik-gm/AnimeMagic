package com.anime.magic.controls;

import com.anime.magic.AnimeMagicPlugin;
import com.anime.magic.api.Spell;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Auto-binds spells to hotbar slots per school. Each school has a default loadout:
 *
 * <p><b>Naruto</b> (orange-themed) — slots 1-4:
 * <ul><li>1: naruto:fireball (Katon)</li><li>2: naruto:chidori (Raiton)</li>
 * <li>3: naruto:rasengan (sphere)</li><li>4: naruto:shadow_clone (clones)</li></ul></p>
 *
 * <p><b>Tensura</b> (purple-themed) — slots 5-7:
 * <ul><li>5: tensura:magicule_blade</li><li>6: tensura:gluttony</li>
 * <li>7: tensura:razor_edge</li></ul></p>
 *
 * <p><b>Mushoku</b> (blue-themed) — slots 6-8 (sneak-variants):
 * <ul><li>6+sneak: mushoku:saint_water</li><li>7+sneak: mushoku:saint_fire</li>
 * <li>8+sneak: mushoku:emperor_earth</li></ul></p>
 *
 * <p><b>One Piece</b> (red-themed) — slots 1-3 (replaces Naruto when OnePiece is selected):
 * <ul><li>1: onepiece:gomu_pistol</li><li>2: onepiece:armament_haki</li>
 * <li>3: onepiece:conquerors_haki</li></ul></p>
 *
 * <p>The /school command switches the active loadout, rebinding the hotbar.</p>
 */
public final class DefaultBindings {

    /** A complete school loadout: 9 slots, each with a normal spell and an optional sneak-variant spell. */
    public record Loadout(Spell.SchoolId school, String[] slotNormal, String[] slotSneak) {
        public Loadout(Spell.SchoolId school) {
            this(school, new String[9], new String[9]);
        }
    }

    private final AnimeMagicPlugin plugin;
    private final Map<Spell.SchoolId, Loadout> loadouts = new HashMap<>();
    private final Map<UUID, Spell.SchoolId> activeSchool = new HashMap<>();

    public DefaultBindings(AnimeMagicPlugin plugin) {
        this.plugin = plugin;
        buildLoadouts();
    }

    private void buildLoadouts() {
        // NARUTO — slots 0-3
        Loadout naruto = new Loadout(Spell.SchoolId.NARUTO);
        naruto.slotNormal[0] = "naruto:fireball";
        naruto.slotNormal[1] = "naruto:chidori";
        naruto.slotNormal[2] = "naruto:rasengan";
        naruto.slotNormal[3] = "naruto:shadow_clone";
        // Sneak variants on same slots = Mushoku support spells
        naruto.slotSneak[0] = "mushoku:saint_fire";
        naruto.slotSneak[1] = "mushoku:saint_water";
        naruto.slotSneak[2] = "mushoku:emperor_earth";
        loadouts.put(Spell.SchoolId.NARUTO, naruto);

        // TENSURA — slots 0-2 (blade spells)
        Loadout tensura = new Loadout(Spell.SchoolId.TENSURA);
        tensura.slotNormal[0] = "tensura:magicule_blade";
        tensura.slotNormal[1] = "tensura:razor_edge";
        tensura.slotNormal[2] = "tensura:gluttony";
        tensura.slotSneak[0] = "tensura:razor_edge";
        tensura.slotSneak[1] = "tensura:gluttony";
        tensura.slotSneak[2] = "tensura:magicule_blade";
        loadouts.put(Spell.SchoolId.TENSURA, tensura);

        // ONE PIECE — slots 0-2
        Loadout onepiece = new Loadout(Spell.SchoolId.ONEPIECE);
        onepiece.slotNormal[0] = "onepiece:gomu_pistol";
        onepiece.slotNormal[1] = "onepiece:armament_haki";
        onepiece.slotNormal[2] = "onepiece:conquerors_haki";
        onepiece.slotSneak[0] = "onepiece:armament_haki";
        onepiece.slotSneak[1] = "onepiece:conquerors_haki";
        onepiece.slotSneak[2] = "onepiece:gomu_pistol";
        loadouts.put(Spell.SchoolId.ONEPIECE, onepiece);

        // MUSHOKU — slots 0-2 (incantation spells)
        Loadout mushoku = new Loadout(Spell.SchoolId.MUSHOKU);
        mushoku.slotNormal[0] = "mushoku:saint_water";
        mushoku.slotNormal[1] = "mushoku:saint_fire";
        mushoku.slotNormal[2] = "mushoku:emperor_earth";
        mushoku.slotSneak[0] = "mushoku:saint_fire";
        mushoku.slotSneak[1] = "mushoku:saint_water";
        mushoku.slotSneak[2] = "mushoku:emperor_earth";
        loadouts.put(Spell.SchoolId.MUSHOKU, mushoku);
    }

    /** Apply a school's default loadout to the player's hotbar. */
    public void applyLoadout(@NotNull Player player, @NotNull Spell.SchoolId school) {
        Loadout loadout = loadouts.get(school);
        if (loadout == null) return;
        // Clear existing bindings first
        plugin.getControlManager().clearBindings(player.getUniqueId());
        // Bind normal slots (stored under "hotbar:<slot>" state)
        Map<String, String> slotMap = new HashMap<>();
        for (int i = 0; i < 9; i++) {
            if (loadout.slotNormal[i] != null) {
                plugin.getControlManager().bindHotbar(player.getUniqueId(), i, loadout.slotNormal[i]);
            }
        }
        // Store sneak-variant map in control state for HotbarControl to read
        Map<String, String> sneakMap = new HashMap<>();
        for (int i = 0; i < 9; i++) {
            if (loadout.slotSneak[i] != null) {
                sneakMap.put(String.valueOf(i), loadout.slotSneak[i]);
            }
        }
        plugin.getControlManager().state(player.getUniqueId(), "hotbar_sneak", sneakMap);
        activeSchool.put(player.getUniqueId(), school);
        plugin.getControlManager().save();
    }

    public @org.jetbrains.annotations.Nullable Spell.SchoolId activeSchool(@NotNull UUID playerId) {
        return activeSchool.get(playerId);
    }

    public @org.jetbrains.annotations.Nullable String sneakSpellFor(@NotNull UUID playerId, int slot) {
        Map<String, String> sneakMap = plugin.getControlManager().state(playerId, "hotbar_sneak");
        return sneakMap == null ? null : sneakMap.get(String.valueOf(slot));
    }

    public Loadout loadoutFor(@NotNull Spell.SchoolId school) { return loadouts.get(school); }

    /** Apply the Naruto loadout as the default on first join. */
    public void applyDefaultOnFirstJoin(@NotNull Player player) {
        if (activeSchool.containsKey(player.getUniqueId())) return;
        // If player already has any bindings, leave them alone
        if (!plugin.getControlManager().bindings(player.getUniqueId()).isEmpty()) return;
        applyLoadout(player, Spell.SchoolId.NARUTO);
        plugin.getMessages().send(player, "school.default-applied",
                "%school%", plugin.getMessages().raw("school.naruto.name"));
    }
}
