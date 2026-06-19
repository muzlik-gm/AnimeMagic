package com.anime.magic;

import com.anime.magic.api.Spell;
import com.anime.magic.api.SpellRegistry;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SpellRegistryTest {
    @Test void bareIdStripsPrefix() {
        assertEquals("fireball", SpellRegistry.bareId("naruto:fireball"));
        assertEquals("fireball", SpellRegistry.bareId("fireball"));
    }
    @Test void qualifyAddsPrefix() {
        assertEquals("naruto:fireball", SpellRegistry.qualify(Spell.SchoolId.NARUTO, "fireball"));
        assertEquals("naruto:fireball", SpellRegistry.qualify(Spell.SchoolId.NARUTO, "naruto:fireball"));
    }
}
