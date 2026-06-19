package com.anime.magic.models;

import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;

/**
 * Describes a custom 3D model — a Minecraft item rendered as an ItemDisplay entity with
 * a CustomModelData tag pointing to a resource-pack item model.
 *
 * Model definitions are loaded from plugins/AnimeMagic/models/*.json files.
 */
public final class CustomModel {
    private final String id;
    private final Material material;
    private final int customModelData;
    private final String displayContext;
    private final String billboard;
    private final float scaleX, scaleY, scaleZ;
    private final float transX, transY, transZ;
    private final float rotX, rotY, rotZ;
    private final boolean glow;
    private final float shadowRadius;

    public CustomModel(@NotNull String id, @NotNull Material material, int customModelData,
                       @NotNull String displayContext, @NotNull String billboard,
                       float sx, float sy, float sz, float tx, float ty, float tz,
                       float rx, float ry, float rz, boolean glow, float shadowRadius) {
        this.id = id; this.material = material; this.customModelData = customModelData;
        this.displayContext = displayContext; this.billboard = billboard;
        this.scaleX = sx; this.scaleY = sy; this.scaleZ = sz;
        this.transX = tx; this.transY = ty; this.transZ = tz;
        this.rotX = rx; this.rotY = ry; this.rotZ = rz;
        this.glow = glow; this.shadowRadius = shadowRadius;
    }

    public @NotNull String id() { return id; }
    public @NotNull Material material() { return material; }
    public int customModelData() { return customModelData; }
    public @NotNull String displayContext() { return displayContext; }
    public @NotNull String billboard() { return billboard; }
    public float scaleX() { return scaleX; }
    public float scaleY() { return scaleY; }
    public float scaleZ() { return scaleZ; }
    public float transX() { return transX; }
    public float transY() { return transY; }
    public float transZ() { return transZ; }
    public float rotX() { return rotX; }
    public float rotY() { return rotY; }
    public float rotZ() { return rotZ; }
    public boolean glow() { return glow; }
    public float shadowRadius() { return shadowRadius; }
}
