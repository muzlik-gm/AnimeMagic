package com.anime.magic.schools.tensura;

import com.anime.magic.AnimeMagicPlugin;
import com.anime.magic.api.Spell;
import com.anime.magic.api.SpellRegistry;
import com.anime.magic.api.SpellSchool;
import org.jetbrains.annotations.NotNull;

/** Tensura school — Magicule-based skills. */
public final class TensuraSchool implements SpellSchool {
    private final AnimeMagicPlugin plugin;

    public TensuraSchool(@NotNull AnimeMagicPlugin plugin) { this.plugin = plugin; }

    @Override public String displayName() { return plugin.getMessages().raw("school.tensura.name"); }
    @Override public Spell.SchoolId id() { return Spell.SchoolId.TENSURA; }
    @Override public AnimeMagicPlugin plugin() { return plugin; }

    @Override
    public void register(SpellRegistry registry) {
        registry.register(new MagiculeBlade(plugin));
        registry.register(new GluttonySkill(plugin));
        registry.register(new RazorEdgeSkill(plugin));
    }
}
