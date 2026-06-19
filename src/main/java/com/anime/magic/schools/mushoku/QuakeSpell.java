package com.anime.magic.schools.mushoku;

import com.anime.magic.AnimeMagicPlugin;
import com.anime.magic.api.Caster;
import com.anime.magic.api.Spell;
import com.anime.magic.effects.RingBurst;
import com.anime.magic.effects.SphereAnimation;
import com.anime.magic.util.LocationUtil;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;
import java.util.List;

/**
 * <b>Emperor-class Earth Quake</b>
 *
 * <p>Slams both hands into the ground, causing a massive earthquake that damages and
 * launches all entities within 20 blocks. The ground cracks in expanding rings.</p>
 */
public final class QuakeSpell implements Spell {
    private final AnimeMagicPlugin plugin;

    public QuakeSpell(AnimeMagicPlugin plugin) { this.plugin = plugin; }

    @Override public @NotNull String id() { return "mushoku:quake"; }
    @Override public @NotNull String displayName() { return "§6§l§k||§r §6§lEmperor-class: Quake §6§l§k||§r"; }
    @Override public @NotNull SchoolId school() { return SchoolId.MUSHOKU; }
    @Override public int manaCost() { return 130; }
    @Override public long cooldownMs() { return 40000; }
    @Override public int requiredLevel() { return 50; }
    @Override public @NotNull String description() {
        return "Slam the ground. 35 damage + launch all entities within 20 blocks upward. 5 expanding crack rings.";
    }
    @Override public @NotNull SpellIcon icon() { return new SpellIcon("STONE", 5003, "§6Quake"); }

    @Override public @NotNull List<String> incantation() {
        return List.of(
                "Earth, tremble at my command",
                "Rise up and shatter the ground",
                "Cast my enemies to the sky",
                "Emperor-class Quake."
        );
    }

    @Override
    public boolean cast(@NotNull Caster caster) {
        Player p = caster.player();
        Location center = p.getLocation();

        // 5 expanding crack rings over 2 seconds
        for (int i = 0; i < 5; i++) {
            final int ringIdx = i;
            new BukkitRunnable() {
                @Override public void run() {
                    double radius = 4.0 + ringIdx * 4.0;
                    plugin.getParticleEngine().play(new RingBurst(plugin, p, center, Particle.DUST, 20, radius, 80));
                    plugin.getParticleEngine().play(new SphereAnimation(plugin, p, center, Particle.DUST, 8, 0.5, ringIdx * 2.0 + 2.0, 40));
                    LocationUtil.sound(center, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.5f - ringIdx * 0.1f, 0.4f + ringIdx * 0.05f);
                    LocationUtil.sound(center, Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 1.0f, 0.5f + ringIdx * 0.1f);

                    double dmg = 35.0 * plugin.getConfig().getDouble("schools.mushoku.damage-multiplier", 1.0);
                    for (LivingEntity e : LocationUtil.nearbyLiving(center, radius, p.getUniqueId())) {
                        e.damage(dmg, p);
                        e.setVelocity(e.getVelocity().setY(2.0)); // launch up
                    }
                }
            }.runTaskLater(plugin, i * 8L);
        }
        return true;
    }
}
