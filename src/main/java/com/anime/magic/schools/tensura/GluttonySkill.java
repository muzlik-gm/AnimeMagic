package com.anime.magic.schools.tensura;

import com.anime.magic.AnimeMagicPlugin;
import com.anime.magic.api.Caster;
import com.anime.magic.api.Spell;
import com.anime.magic.effects.HelixEffect;
import com.anime.magic.effects.SphereAnimation;
import com.anime.magic.models.ModelDisplay;
import com.anime.magic.util.LocationUtil;
import com.anime.magic.util.SpellEffects;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

/**
 * <b>Unique Skill: Gluttony (Predator)</b> — Devour a target's life essence.
 *
 * <p>Channels for 2 seconds, visualized by a 3D {@code magic_orb} model playing
 * the {@code cast_charge} animation hovering above the target's head. If the
 * target remains within 6 blocks, absorbs 25% of their max health as bonus max
 * mana for the caster and drains 8 HP from the target. The drain is visualized
 * by a sphere of dark particles collapsing from the target onto the caster, then
 * a helix of regeneration particles around the caster.</p>
 */
public final class GluttonySkill implements Spell {

    private final AnimeMagicPlugin plugin;

    public GluttonySkill(AnimeMagicPlugin plugin) { this.plugin = plugin; }

    @Override public @NotNull String id() { return "tensura:gluttony"; }
    @Override public @NotNull String displayName() { return "§5Unique Skill §8» §dGluttony"; }
    @Override public @NotNull Spell.SchoolId school() { return Spell.SchoolId.TENSURA; }
    @Override public int manaCost() { return 60; }
    @Override public long cooldownMs() { return 15000; }
    @Override public int requiredLevel() { return 25; }
    @Override public @NotNull String description() {
        return "Drains 8 HP from a nearby target, granting you bonus max mana and Strength II for 15s.";
    }
    @Override public @NotNull Spell.SpellIcon icon() {
        return new SpellIcon("BLACK_DYE", 4002, "§dGluttony");
    }

    @Override
    public boolean cast(@NotNull Caster caster) {
        Player p = caster.player();
        LivingEntity target = caster.targetEntity(8.0);
        if (target == null) return false;

        // --- Spawn the orb model above the target with the cast_charge animation ---
        Location targetHead = target.getEyeLocation().add(0, 1.5, 0);
        ModelDisplay drainOrb = SpellEffects.spawnAnimated(plugin, p,
                "magic_orb", "cast_charge", targetHead, 40, null);

        // 2-second channel
        new BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                if (ticks++ > 40) {
                    complete(p, target, drainOrb);
                    cancel();
                    return;
                }
                // Channeling particles — line of dark dust from target to caster
                Location from = target.getEyeLocation();
                Location to = p.getEyeLocation();
                if (from.getWorld() == null) return;
                for (double d = 0; d <= 1; d += 0.1) {
                    Location loc = from.clone().add(to.toVector().subtract(from.toVector()).multiply(d));
                    from.getWorld().spawnParticle(Particle.SQUID_INK, loc, 1, 0.1, 0.1, 0.1, 0.0);
                }
                if (ticks % 10 == 0) {
                    LocationUtil.sound(from, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.6f, 0.6f);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);

        return true;
    }

    private void complete(Player caster, LivingEntity target, ModelDisplay drainOrb) {
        if (drainOrb != null) drainOrb.remove();

        // Collapse sphere on target
        plugin.getParticleEngine().play(
                new SphereAnimation(plugin, caster, target.getEyeLocation(),
                        Particle.SQUID_INK, 15, 3.0, 0.3, 60));

        // Drain damage
        double dmg = 8.0 * plugin.getConfig().getDouble("schools.tensura.damage-multiplier", 1.0);
        target.damage(dmg, caster);

        // Heal caster
        double newHealth = Math.min(caster.getMaxHealth(), caster.getHealth() + 6.0);
        caster.setHealth(newHealth);

        // Buffs
        caster.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 300, 1));
        caster.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 1));

        // Helix of regen around caster
        plugin.getParticleEngine().play(
                new HelixEffect(plugin, caster, Particle.HEART,
                        30, 0.9, 0.5, 6, 0.4));

        LocationUtil.sound(caster.getLocation(), Sound.ENTITY_PLAYER_BURP, 1.0f, 0.6f);
    }
}
