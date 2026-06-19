package com.anime.magic.animation;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Blockbench-style keyframe animation. Parsed from JSON in the Bedrock animation format.
 * Since Java Edition ItemDisplay has a single transformation (no bone hierarchy),
 * all bones' values are summed together at runtime.
 */
public final class KeyframeAnimation {
    private final String name;
    private final double lengthSeconds;
    private final boolean loop;
    private final List<ChannelKeyframes> rotation;
    private final List<ChannelKeyframes> position;
    private final List<ChannelKeyframes> scale;

    public KeyframeAnimation(@NotNull String name, double lengthSeconds, boolean loop,
                             @NotNull List<ChannelKeyframes> rotation,
                             @NotNull List<ChannelKeyframes> position,
                             @NotNull List<ChannelKeyframes> scale) {
        this.name = name;
        this.lengthSeconds = lengthSeconds;
        this.loop = loop;
        this.rotation = rotation;
        this.position = position;
        this.scale = scale;
    }

    public @NotNull String name() { return name; }
    public double lengthSeconds() { return lengthSeconds; }
    public int lengthTicks() { return (int) Math.ceil(lengthSeconds * 20); }
    public boolean loop() { return loop; }
    public @NotNull List<ChannelKeyframes> rotation() { return rotation; }
    public @NotNull List<ChannelKeyframes> position() { return position; }
    public @NotNull List<ChannelKeyframes> scale() { return scale; }

    public static @Nullable KeyframeAnimation parse(@NotNull String animName, @NotNull JSONObject animJson) {
        double length = animJson.optDouble("length", 1.0);
        boolean loop = animJson.optBoolean("loop", false);
        JSONObject bones = animJson.optJSONObject("bones");
        if (bones == null) {
            return new KeyframeAnimation(animName, length, loop, List.of(), List.of(), List.of());
        }
        Map<Double, double[]> rotMerge = new HashMap<>();
        Map<Double, double[]> posMerge = new HashMap<>();
        Map<Double, double[]> scaleMerge = new HashMap<>();
        Map<Double, Easing> rotEase = new HashMap<>();
        Map<Double, Easing> posEase = new HashMap<>();
        Map<Double, Easing> scaleEase = new HashMap<>();

        for (String boneName : bones.keySet()) {
            JSONObject bone = bones.optJSONObject(boneName);
            if (bone == null) continue;
            mergeChannel(bone.optJSONObject("rotation"), rotMerge, rotEase);
            mergeChannel(bone.optJSONObject("position"), posMerge, posEase);
            mergeChannel(bone.optJSONObject("scale"), scaleMerge, scaleEase);
        }

        return new KeyframeAnimation(animName, length, loop,
                toList(rotMerge, rotEase),
                toList(posMerge, posEase),
                toList(scaleMerge, scaleEase));
    }

    private static void mergeChannel(JSONObject channel, Map<Double, double[]> values, Map<Double, Easing> easings) {
        if (channel == null) return;
        for (String key : channel.keySet()) {
            double time;
            try { time = Double.parseDouble(key); }
            catch (NumberFormatException e) { continue; }
            Object val = channel.get(key);
            double[] vec;
            Easing ease = Easing.LINEAR;
            if (val instanceof JSONArray arr) {
                vec = jsonArrayToDouble(arr);
            } else if (val instanceof JSONObject obj) {
                Object v = obj.opt("vector");
                if (v instanceof JSONArray arr) vec = jsonArrayToDouble(arr);
                else vec = new double[]{0, 0, 0};
                ease = Easing.fromString(obj.optString("easing", "linear"));
            } else if (val instanceof Number n) {
                vec = new double[]{n.doubleValue(), n.doubleValue(), n.doubleValue()};
            } else continue;
            double[] existing = values.get(time);
            if (existing == null) values.put(time, vec.clone());
            else for (int i = 0; i < 3; i++) existing[i] += vec[i];
            easings.putIfAbsent(time, ease);
        }
    }

    private static double[] jsonArrayToDouble(JSONArray arr) {
        double[] out = new double[3];
        for (int i = 0; i < 3 && i < arr.length(); i++) out[i] = arr.optDouble(i, 0.0);
        return out;
    }

    private static List<ChannelKeyframes> toList(Map<Double, double[]> values, Map<Double, Easing> easings) {
        List<ChannelKeyframes> list = new ArrayList<>();
        values.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> list.add(new ChannelKeyframes(
                        e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[2],
                        easings.getOrDefault(e.getKey(), Easing.LINEAR))));
        return list;
    }

    public record ChannelKeyframes(double time, double x, double y, double z, Easing easing) {}
}
