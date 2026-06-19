package com.anime.magic.schools.naruto;

import com.anime.magic.AnimeMagicPlugin;
import com.anime.magic.api.Caster;
import com.anime.magic.api.Spell;
import com.anime.magic.util.LocationUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * <b>Kage Bunshin no Jutsu</b> — Shadow Clone Technique.
 *
 * <p>Summons 3 hostile-looking zombies that follow the caster's target,
 * attack it, and after 15 seconds poof into smoke clouds.</p>
 */
public final class ShadowCloneJutsu implements Spell {

    private final AnimeMagicPlugin plugin;

    public ShadowCloneJutsu(AnimeMagicPlugin plugin) { this.plugin = plugin; }

    @Override public @NotNull String id() { return "naruto:shadow_clone"; }
    @Override public @NotNull String displayName() { return "§7Kage Bunshin §8» §fShadow Clones"; }
    @Override public @NotNull SchoolId school() { return SchoolId.NARUTO; }
    @Override public int manaCost() { return 50; }
    @Override public long cooldownMs() { return 12000; }
    @Override public int requiredLevel() { return 20; }
    @Override public @NotNull String description() {
        return "Summon three shadow clones to fight alongside you for 15 seconds.";
    }
    @Override public @NotNull SpellIcon icon() {
        return new SpellIcon("SOUL_SAND", 3004, "§7Shadow Clones");
    }

    @Override
    public boolean cast(@NotNull Caster caster) {
        Player p = caster.player();
        Location base = p.getLocation().add(p.getLocation().getDirection().multiply(2));
        List<Zombie> clones = new ArrayList<>();

        for (int i = 0; i < 3; i++) {
            Location spawn = base.clone().add(
                    (Math.random() - 0.5) * 2, 0, (Math.random() - 0.5) * 2);
            if (spawn.getWorld() == null) continue;
            Zombie z = (Zombie) spawn.getWorld().spawnEntity(spawn, EntityType.ZOMBIE);
            z.setBaby(false);
            z.getEquipment().setHelmet(new ItemStack(Material.PLAYER_HEAD));
            z.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 300, 2));
            z.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 300, 1));
            z.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 300, 0));
            spawn.getWorld().spawnParticle(Particle.LARGE_SMOKE, spawn, 30, 0.5, 1.0, 0.5, 0.05);
            LocationUtil.sound(spawn, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 0.7f);
            clones.add(z);
        }

        new BukkitRunnable() {
            @Override public void run() {
                for (Zombie z : clones) {
                    if (z.isValid() && !z.isDead()) {
                        Location loc = z.getLocation();
                        if (loc.getWorld() != null) {
                            loc.getWorld().spawnParticle(Particle.LARGE_SMOKE, loc, 25, 0.4, 0.8, 0.4, 0.05);
                            LocationUtil.sound(loc, Sound.ENTITY_ENDERMAN_TELEPORT, 0.7f, 0.5f);
                        }
                        z.remove();
                    }
                }
            }
        }.runTaskLater(plugin, 300L);

        return true;
    }
}
