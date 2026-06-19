package com.anime.magic.api;

import com.anime.magic.AnimeMagicPlugin;

/** Base contract for a magic school (Naruto, Tensura, Mushoku, One Piece). */
public interface SpellSchool {
    String displayName();
    Spell.SchoolId id();
    void register(SpellRegistry registry);
    AnimeMagicPlugin plugin();
}
