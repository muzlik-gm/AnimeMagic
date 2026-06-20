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
 * <p>Summons 3 "shadow clone" zombies that target the caster's current target
 * (NOT the caster — fixed in audit pass: vanilla zombie AI defaults to the
 * nearest player, which is the caster). Clones are non-persistent (despawn on
 * chunk unload), wear a player-head helmet for thematic effect, and poof into
 * smoke clouds after 15 seconds.</p>
 */
public final class ShadowCloneJutsu implements Spell {

    private final AnimeMagicPlugin plugin;

    public ShadowCloneJutsu(AnimeMagicPlugin plugin) { this.plugin = plugin; }

    @Override public @NotNull String id() { return "naruto:shadow_clone"; }
    @Override public @NotNull String displayName() { return "§7ᴋage ʙunshin §8» §fꜱhadow ᴄlones"; }
    @Override public @NotNull SchoolId school() { return SchoolId.NARUTO; }
    @Override public int manaCost() { return 50; }
    @Override public long cooldownMs() { return 12000; }
    @Override public int requiredLevel() { return 20; }
    @Override public @NotNull String description() {
        return "ꜱummon three shadow clones to fight alongside you for 15 seconds.";
    }
    @Override public @NotNull SpellIcon icon() {
        return new SpellIcon("SOUL_SAND", 3004, "§7Shadow Clones");
    }

    @Override
    public boolean cast(@NotNull Caster caster) {
        Player p = caster.player();
        // Spawn the 3D clone_haze model at the caster's location.
        com.anime.magic.util.SpellEffects.spawnAnimated(plugin, p,
                "clone_haze", "animation.clone_haze.poof",
                p.getEyeLocation().add(p.getLocation().getDirection().multiply(1.5)).clone(), 60, null);
        // Resolve the caster's target ONCE so all clones attack the same enemy.
        LivingEntity target = caster.targetEntity(30);
        Location base = p.getLocation().add(p.getLocation().getDirection().multiply(2));
        List<Zombie> clones = new ArrayList<>();

        for (int i = 0; i < 3; i++) {
            Location spawn = base.clone().add(
                    (Math.random() - 0.5) * 2, 0, (Math.random() - 0.5) * 2);
            if (spawn.getWorld() == null) continue;
            Zombie z = (Zombie) spawn.getWorld().spawnEntity(spawn, EntityType.ZOMBIE);
            // Critical: zombies default to attacking the nearest player (the caster).
            // Set the target explicitly so clones fight for the caster.
            if (target != null) {
                try { z.setTarget(target); } catch (Throwable ignored) {}
            }
            z.setBaby(false);
            // Non-persistent: don't save to chunk data on server crash.
            try { z.setPersistent(false); } catch (Throwable ignored) {}
            z.getEquipment().setHelmet(new ItemStack(Material.PLAYER_HEAD));
            z.getEquipment().setHelmetDropChance(0f);
            z.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 300, 2));
            z.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 300, 1));
            z.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 300, 0));
            try { spawn.getWorld().spawnParticle(Particle.LARGE_SMOKE, spawn, 1, 0.5, 1.0, 0.5, 0.05); } catch (Throwable ignored) {}
            LocationUtil.sound(spawn, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 0.7f);
            clones.add(z);
        }

        // Re-target poller: keep clones locked on the caster's CURRENT target
        // for the lifetime of the spell. Self-terminates if the caster logs out.
        new BukkitRunnable() {
            @Override public void run() {
                if (!p.isOnline()) { cancel(); return; }
                LivingEntity current = new Caster(plugin, p, ShadowCloneJutsu.this).targetEntity(30);
                if (current == null) return;
                for (Zombie z : clones) {
                    if (z.isValid() && !z.isDead()) {
                        try { z.setTarget(current); } catch (Throwable ignored) {}
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);

        // Lifetime expiry: poof all clones into smoke after 15s.
        new BukkitRunnable() {
            @Override public void run() {
                if (!p.isOnline()) { cancel(); return; }
                for (Zombie z : clones) {
                    if (z.isValid() && !z.isDead()) {
                        Location loc = z.getLocation();
                        if (loc.getWorld() != null) {
                            try { loc.getWorld().spawnParticle(Particle.LARGE_SMOKE, loc, 1, 0.4, 0.8, 0.4, 0.05); } catch (Throwable ignored) {}
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
