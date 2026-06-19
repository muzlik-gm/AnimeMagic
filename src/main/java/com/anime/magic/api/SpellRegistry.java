package com.anime.magic.api;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Registry of all loaded spells keyed by id. Plugin populates on enable by asking each
 * SpellSchool to register its spells. Lookups are case-insensitive and tolerate both
 * "school:id" qualified form and bare id.
 */
public final class SpellRegistry {
    private final Map<String, Spell> spells = new LinkedHashMap<>();

    public void register(@NotNull Spell spell) {
        spells.put(spell.id().toLowerCase(), spell);
        String bare = bareId(spell.id());
        if (!bare.isEmpty()) spells.putIfAbsent(bare.toLowerCase(), spell);
    }

    public void clear() { spells.clear(); }
    public int size() { return spells.size(); }

    public @org.jetbrains.annotations.Nullable Spell get(@NotNull String id) {
        return id == null ? null : spells.get(id.toLowerCase());
    }

    public Collection<Spell> all() { return spells.values(); }

    public Stream<Spell> bySchool(Spell.SchoolId school) {
        return spells.values().stream().filter(s -> s.school() == school).distinct();
    }

    public static String bareId(String id) {
        int i = id.indexOf(':');
        return i < 0 ? id : id.substring(i + 1);
    }

    public static String qualify(Spell.SchoolId school, String id) {
        return id.contains(":") ? id : school.name().toLowerCase() + ":" + id;
    }
}
