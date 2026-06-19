package com.anime.magic.controls;
import org.bukkit.entity.Player;

import com.anime.magic.AnimeMagicPlugin;
import com.anime.magic.api.Spell;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Auto-binds spells to hotbar slots per school. Each school ONLY has its OWN spells.
 */
public final class DefaultBindings {

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
        // NARUTO — slots 0-4, ONLY Naruto spells
        Loadout naruto = new Loadout(Spell.SchoolId.NARUTO);
        naruto.slotNormal[0] = "naruto:fireball";       naruto.slotSneak[0] = "naruto:phoenix_flower";
        naruto.slotNormal[1] = "naruto:chidori";         naruto.slotSneak[1] = "naruto:kirin";
        naruto.slotNormal[2] = "naruto:rasengan";        naruto.slotSneak[2] = "naruto:rasenshuriken";
        naruto.slotNormal[3] = "naruto:shadow_clone";    naruto.slotSneak[3] = "naruto:sage_mode";
        naruto.slotNormal[4] = "naruto:sage_mode";       naruto.slotSneak[4] = "naruto:six_paths";
        loadouts.put(Spell.SchoolId.NARUTO, naruto);

        // TENSURA — slots 0-4, ONLY Tensura spells
        Loadout tensura = new Loadout(Spell.SchoolId.TENSURA);
        tensura.slotNormal[0] = "tensura:magicule_blade";  tensura.slotSneak[0] = "tensura:razor_edge";
        tensura.slotNormal[1] = "tensura:razor_edge";      tensura.slotSneak[1] = "tensura:gluttony";
        tensura.slotNormal[2] = "tensura:gluttony";        tensura.slotSneak[2] = "tensura:beelzebuth";
        tensura.slotNormal[3] = "tensura:disintegration";  tensura.slotSneak[3] = "tensura:megiddo";
        tensura.slotNormal[4] = "tensura:raphael";         tensura.slotSneak[4] = "tensura:true_dragon";
        loadouts.put(Spell.SchoolId.TENSURA, tensura);

        // MUSHOKU — slots 0-4, ONLY Mushoku spells
        Loadout mushoku = new Loadout(Spell.SchoolId.MUSHOKU);
        mushoku.slotNormal[0] = "mushoku:saint_water";     mushoku.slotSneak[0] = "mushoku:storm";
        mushoku.slotNormal[1] = "mushoku:saint_fire";      mushoku.slotSneak[1] = "mushoku:atomic_flare";
        mushoku.slotNormal[2] = "mushoku:emperor_earth";   mushoku.slotSneak[2] = "mushoku:quake";
        mushoku.slotNormal[3] = "mushoku:gravity";         mushoku.slotSneak[3] = "mushoku:time_warp";
        mushoku.slotNormal[4] = "mushoku:atomic_flare";    mushoku.slotSneak[4] = "mushoku:time_warp";
        loadouts.put(Spell.SchoolId.MUSHOKU, mushoku);

        // ONE PIECE — slots 0-4, ONLY One Piece spells
        Loadout onepiece = new Loadout(Spell.SchoolId.ONEPIECE);
        onepiece.slotNormal[0] = "onepiece:gomu_pistol";        onepiece.slotSneak[0] = "onepiece:gear_second";
        onepiece.slotNormal[1] = "onepiece:armament_haki";      onepiece.slotSneak[1] = "onepiece:gear_third";
        onepiece.slotNormal[2] = "onepiece:conquerors_haki";    onepiece.slotSneak[2] = "onepiece:gear_fourth";
        onepiece.slotNormal[3] = "onepiece:observation_haki";   onepiece.slotSneak[3] = "onepiece:voice_of_all_things";
        onepiece.slotNormal[4] = "onepiece:gear_second";        onepiece.slotSneak[4] = "onepiece:voice_of_all_things";
        loadouts.put(Spell.SchoolId.ONEPIECE, onepiece);
    }

    public void applyLoadout(@NotNull Player player, @NotNull Spell.SchoolId school) {
        Loadout loadout = loadouts.get(school);
        if (loadout == null) return;
        plugin.getControlManager().clearBindings(player.getUniqueId());
        // Bind normal slots
        for (int i = 0; i < 9; i++) {
            if (loadout.slotNormal[i] != null) {
                plugin.getControlManager().bindHotbar(player.getUniqueId(), i, loadout.slotNormal[i]);
            }
        }
        // Store sneak-variant map
        Map<String, String> sneakMap = new HashMap<>();
        for (int i = 0; i < 9; i++) {
            if (loadout.slotSneak[i] != null) {
                sneakMap.put(String.valueOf(i), loadout.slotSneak[i]);
            }
        }
        plugin.getControlManager().state(player.getUniqueId(), "hotbar_sneak", sneakMap);
        activeSchool.put(player.getUniqueId(), school);
    }

    public @org.jetbrains.annotations.Nullable Spell.SchoolId activeSchool(@NotNull UUID playerId) {
        return activeSchool.get(playerId);
    }

    public @org.jetbrains.annotations.Nullable String sneakSpellFor(@NotNull UUID playerId, int slot) {
        Map<String, String> sneakMap = plugin.getControlManager().state(playerId, "hotbar_sneak");
        return sneakMap == null ? null : sneakMap.get(String.valueOf(slot));
    }

    public Loadout loadoutFor(@NotNull Spell.SchoolId school) { return loadouts.get(school); }

    public void applyDefaultOnFirstJoin(@NotNull Player player) {
        if (activeSchool.containsKey(player.getUniqueId())) return;
        if (!plugin.getControlManager().bindings(player.getUniqueId()).isEmpty()) return;
        applyLoadout(player, Spell.SchoolId.NARUTO);
    }
}
