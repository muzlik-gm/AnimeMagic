package com.anime.magic.schools.onepiece;

import com.anime.magic.AnimeMagicPlugin;
import com.anime.magic.api.Spell;
import com.anime.magic.api.SpellRegistry;
import com.anime.magic.api.SpellSchool;
import org.jetbrains.annotations.NotNull;

/** One Piece school — Haki and Devil Fruit-inspired abilities. */
public final class OnePieceSchool implements SpellSchool {
    private final AnimeMagicPlugin plugin;

    public OnePieceSchool(@NotNull AnimeMagicPlugin plugin) { this.plugin = plugin; }

    @Override public String displayName() { return plugin.getMessages().raw("school.onepiece.name"); }
    @Override public Spell.SchoolId id() { return Spell.SchoolId.ONEPIECE; }
    @Override public AnimeMagicPlugin plugin() { return plugin; }

    @Override
    public void register(SpellRegistry registry) {
        registry.register(new ConquerorsHaki(plugin));
        registry.register(new ArmamentHaki(plugin));
        registry.register(new GomuGomuSkill(plugin));
    }
}
