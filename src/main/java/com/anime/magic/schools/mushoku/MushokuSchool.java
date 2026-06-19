package com.anime.magic.schools.mushoku;

import com.anime.magic.AnimeMagicPlugin;
import com.anime.magic.api.Spell;
import com.anime.magic.api.SpellRegistry;
import com.anime.magic.api.SpellSchool;
import org.jetbrains.annotations.NotNull;

/**
 * Mushoku Tensei school — Incantation-based Saint & Emperor tier magic.
 * When schools.mushoku.require-chant is true, the caster must type each incantation
 * line in chat before the spell fires (handled by IncantationSystem).
 */
public final class MushokuSchool implements SpellSchool {
    private final AnimeMagicPlugin plugin;
    private IncantationSystem incantation;

    public MushokuSchool(@NotNull AnimeMagicPlugin plugin) { this.plugin = plugin; }

    @Override public String displayName() { return plugin.getMessages().raw("school.mushoku.name"); }
    @Override public Spell.SchoolId id() { return Spell.SchoolId.MUSHOKU; }
    @Override public AnimeMagicPlugin plugin() { return plugin; }

    @Override
    public void register(SpellRegistry registry) {
        if (plugin.getConfig().getBoolean("schools.mushoku.require-chant", true)) {
            this.incantation = new IncantationSystem(plugin);
            plugin.getServer().getPluginManager().registerEvents(incantation, plugin);
        }
        registry.register(new SaintWaterSpell(plugin, incantation));
        registry.register(new SaintFireSpell(plugin, incantation));
        registry.register(new EmperorEarthSpell(plugin, incantation));
        // v2 Ultimates
        registry.register(new StormSpell(plugin));
        registry.register(new AtomicFlareSpell(plugin));
        registry.register(new GravitySpell(plugin));
        registry.register(new QuakeSpell(plugin));
        registry.register(new TimeWarpSpell(plugin));
    }
}
