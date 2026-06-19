package com.anime.magic.schools.naruto;

import com.anime.magic.AnimeMagicPlugin;
import com.anime.magic.api.Spell;
import com.anime.magic.api.SpellRegistry;
import com.anime.magic.api.SpellSchool;
import org.jetbrains.annotations.NotNull;

/**
 * Naruto school — Chakra nature transformations and signature jutsu.
 *
 * <p>Spells:</p>
 * <ul>
 *   <li>{@link FireballJutsu} — Fire Style: Fireball Jutsu (with 3D orb model + charge animation)</li>
 *   <li>{@link ChidoriSpell} — Lightning Style: Chidori (with 3D blade model + slash animation)</li>
 *   <li>{@link RasenganSpell} — Spiraling sphere melee (with 3D orb model + spin animation)</li>
 *   <li>{@link ShadowCloneJutsu} — Summons temporary combat clones</li>
 * </ul>
 */
public final class NarutoSchool implements SpellSchool {

    private final AnimeMagicPlugin plugin;

    public NarutoSchool(@NotNull AnimeMagicPlugin plugin) {
        this.plugin = plugin;
    }

    @Override public String displayName() { return plugin.getMessages().raw("school.naruto.name"); }
    @Override public Spell.SchoolId id() { return Spell.SchoolId.NARUTO; }
    @Override public AnimeMagicPlugin plugin() { return plugin; }

    @Override
    public void register(SpellRegistry registry) {
        registry.register(new FireballJutsu(plugin));
        registry.register(new ChidoriSpell(plugin));
        registry.register(new RasenganSpell(plugin));
        registry.register(new ShadowCloneJutsu(plugin));
    }
}
