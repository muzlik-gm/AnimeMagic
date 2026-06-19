package com.anime.magic.api;

import org.jetbrains.annotations.NotNull;
import java.util.List;

/**
 * Contract every spell must implement. Spells are stateless descriptors — actual casting
 * happens via cast(Caster). Cooldowns and mana are enforced by the CastingService before
 * cast() is invoked, so implementations focus on visual + damage logic.
 */
public interface Spell {
    @NotNull String id();
    @NotNull String displayName();
    @NotNull SchoolId school();
    default int manaCost() { return 10; }
    default long cooldownMs() { return 1000; }
    default String permission() { return "animemagic.spell." + school().name().toLowerCase(); }
    default int requiredLevel() { return 0; }
    @NotNull String description();
    @NotNull SpellIcon icon();
    boolean cast(@NotNull Caster caster);
    default @NotNull List<String> incantation() { return List.of(); }

    enum SchoolId {
        NARUTO("naruto"), TENSURA("tensura"), MUSHOKU("mushoku"), ONEPIECE("onepiece");
        private final String configKey;
        SchoolId(String k) { this.configKey = k; }
        public String configKey() { return configKey; }
    }

    final class SpellIcon {
        public final String material;
        public final int customModelData;
        public final String display;
        public SpellIcon(String material, int customModelData, String display) {
            this.material = material; this.customModelData = customModelData; this.display = display;
        }
    }
}
