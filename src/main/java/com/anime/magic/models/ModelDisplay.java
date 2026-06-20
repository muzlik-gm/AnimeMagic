package com.anime.magic.models;

import com.anime.magic.AnimeMagicPlugin;
import com.anime.magic.animation.AnimationPlayer;
import com.anime.magic.animation.KeyframeAnimation;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.UUID;

/**
 * Wraps an ItemDisplay entity that renders a CustomModel. Supports parenting (follows a
 * player), animation (attach a KeyframeAnimation via playAnimation), manual transforms
 * (setTransform), and lifecycle (auto-removed after lifetimeTicks, or 0 for manual).
 *
 * Requires Paper 1.19.4+.
 */
public final class ModelDisplay {
    private final AnimeMagicPlugin plugin;
    private final ItemDisplay entity;
    private final UUID ownerId;
    private final int lifetimeTicks;

    private AnimationPlayer animation;
    private BukkitRunnable follower;
    private BukkitRunnable ticker;
    private int age;

    private float curScaleX, curScaleY, curScaleZ;
    private float curTransX, curTransY, curTransZ;
    private float curRotX, curRotY, curRotZ;

    public ModelDisplay(@NotNull AnimeMagicPlugin plugin, @NotNull CustomModel model,
                        @NotNull Location spawn, @Nullable UUID ownerId, int lifetimeTicks) {
        this.plugin = plugin;
        this.ownerId = ownerId;
        this.lifetimeTicks = lifetimeTicks;

        this.curScaleX = model.scaleX(); this.curScaleY = model.scaleY(); this.curScaleZ = model.scaleZ();
        this.curTransX = model.transX(); this.curTransY = model.transY(); this.curTransZ = model.transZ();
        this.curRotX = model.rotX();     this.curRotY = model.rotY();     this.curRotZ = model.rotZ();

        if (spawn.getWorld() == null) throw new IllegalArgumentException("spawn must have a world");
        this.entity = spawn.getWorld().spawn(spawn, ItemDisplay.class, display -> {
            display.setItemStack(buildItemStack(model));
            display.setPersistent(false);
            applyDisplayProps(display, model);
            display.setTransformation(buildTransform());
            display.setInterpolationDuration(2);
            display.setInterpolationDelay(0);
        });
        entity.getPersistentDataContainer().set(
                new NamespacedKey(plugin, "model_id"), PersistentDataType.STRING, model.id());

        startTicker();
    }

    private ItemStack buildItemStack(CustomModel model) {
        ItemStack item = new ItemStack(model.material());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (model.customModelData() != 0) meta.setCustomModelData(model.customModelData());
            if (model.glow()) meta.setEnchantmentGlintOverride(true);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void applyDisplayProps(ItemDisplay display, CustomModel model) {
        try {
            Display.Billboard billboard = Display.Billboard.valueOf(model.billboard().toUpperCase());
            display.setBillboard(billboard);
        } catch (IllegalArgumentException ignored) {}
        if (model.shadowRadius() > 0) {
            try { display.setShadowRadius(model.shadowRadius()); } catch (Throwable ignored) {}
        }
        if (model.glow()) {
            try { display.setGlowColorOverride(Color.WHITE); } catch (Throwable ignored) {}
        }
        try { display.setBrightness(new Display.Brightness(15, 15)); } catch (Throwable ignored) {}
    }

    private Transformation buildTransform() {
        Vector3f translation = new Vector3f(curTransX, curTransY, curTransZ);
        Quaternionf leftRot = new Quaternionf().rotateZYX(
                (float) Math.toRadians(curRotZ),
                (float) Math.toRadians(curRotY),
                (float) Math.toRadians(curRotX));
        Vector3f scale = new Vector3f(curScaleX, curScaleY, curScaleZ);
        return new Transformation(translation, leftRot, scale, new Quaternionf());
    }

    private void startTicker() {
        this.ticker = new BukkitRunnable() {
            @Override public void run() {
                if (entity == null || entity.isDead()) { cancel(); return; }
                age++;
                if (animation != null && !animation.isFinished()) animation.tick(ModelDisplay.this);
                if (lifetimeTicks > 0 && age >= lifetimeTicks) {
                    remove();
                    cancel();
                }
            }
        };
        ticker.runTaskTimer(plugin, 1L, 1L);
    }

    /** Make this display follow the player each tick (offset by `offset`).
     *  The model's orientation is LOCKED — it does NOT rotate with the
     *  player's view. Only the position follows. */
    public void followPlayer(@NotNull UUID playerId, @NotNull Vector offset) {
        if (follower != null) follower.cancel();
        follower = new BukkitRunnable() {
            @Override public void run() {
                var p = plugin.getServer().getPlayer(playerId);
                if (p == null || !p.isOnline()) { cancel(); return; }
                if (entity == null || entity.isDead()) { cancel(); return; }
                // Teleport to the player's position + offset, but with
                // yaw=0 and pitch=0 so the model orientation stays FIXED.
                // Previously this used p.getLocation() which carried the
                // player's yaw/pitch, causing the model to rotate when
                // the player looked up/down/left/right.
                Location loc = p.getLocation().add(offset);
                loc.setYaw(0f);
                loc.setPitch(0f);
                entity.teleport(loc);
            }
        };
        follower.runTaskTimer(plugin, 0L, 1L);
    }

    public void playAnimation(@NotNull KeyframeAnimation anim) {
        this.animation = new AnimationPlayer(anim);
    }

    public void stopAnimation() { this.animation = null; }

    public void setTransform(float tx, float ty, float tz,
                             float rx, float ry, float rz,
                             float sx, float sy, float sz) {
        this.curTransX = tx; this.curTransY = ty; this.curTransZ = tz;
        this.curRotX = rx;   this.curRotY = ry;   this.curRotZ = rz;
        this.curScaleX = sx; this.curScaleY = sy; this.curScaleZ = sz;
        entity.setTransformation(buildTransform());
    }

    public void teleport(Location loc) { entity.teleport(loc); }
    public void remove() {
        if (follower != null) follower.cancel();
        if (ticker != null) ticker.cancel();
        if (entity != null && !entity.isDead()) entity.remove();
    }

    public ItemDisplay entity() { return entity; }
    public boolean isDead() { return entity == null || entity.isDead(); }
    public UUID ownerId() { return ownerId; }

    // Package-private mutators used by AnimationPlayer

    public void applyInterpolated(float tx, float ty, float tz,
                                  float rx, float ry, float rz,
                                  float sx, float sy, float sz) {
        this.curTransX = tx; this.curTransY = ty; this.curTransZ = tz;
        this.curRotX = rx;   this.curRotY = ry;   this.curRotZ = rz;
        this.curScaleX = sx; this.curScaleY = sy; this.curScaleZ = sz;
        entity.setTransformation(buildTransform());
    }

    public float[] currentTransform() {
        return new float[] {
                curTransX, curTransY, curTransZ,
                curRotX, curRotY, curRotZ,
                curScaleX, curScaleY, curScaleZ
        };
    }
}
