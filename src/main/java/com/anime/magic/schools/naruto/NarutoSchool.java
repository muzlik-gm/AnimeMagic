package com.anime.magic.schools.naruto;

import com.anime.magic.AnimeMagicPlugin;
import com.anime.magic.api.Spell;
import com.anime.magic.api.SpellRegistry;
import com.anime.magic.api.SpellSchool;
import org.jetbrains.annotations.NotNull;

/**
 * Naruto school — Chakra nature transformations and signature jutsu.
 *
 * <p>Spells (9 total):</p>
 * <ul>
 *   <li>{@link FireballJutsu} — Katon: Gōkakyū no Jutsu</li>
 *   <li>{@link ChidoriSpell} — Raiton: Chidori</li>
 *   <li>{@link RasenganSpell} — Spiraling Sphere</li>
 *   <li>{@link ShadowCloneJutsu} — Kage Bunshin no Jutsu</li>
 *   <li>{@link PhoenixFlowerSpell} — Katon: Hōsenka no Jutsu (barrage)</li>
 *   <li>{@link RasenshurikenSpell} — Fūton: Rasenshuriken (throwable)</li>
 *   <li>{@link KirinSpell} — Raiton: Kirin (lightning strike)</li>
 *   <li>{@link SageModeSpell} — Sennin Mōdo (transformation)</li>
 *   <li>{@link SixPathsSpell} — Rikudō Sennin Mōdo (ultimate transformation)</li>
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
        // v2 Ultimates
        registry.register(new PhoenixFlowerSpell(plugin));
        registry.register(new RasenshurikenSpell(plugin));
        registry.register(new KirinSpell(plugin));
        registry.register(new SageModeSpell(plugin));
        registry.register(new SixPathsSpell(plugin));
    }
}
