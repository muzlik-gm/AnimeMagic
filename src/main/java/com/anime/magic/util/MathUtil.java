package com.anime.magic.util;

/** Pure-math utilities. No Bukkit deps — fully unit-testable. */
public final class MathUtil {
    private MathUtil() {}

    public static double lerp(double a, double b, double t) { return a + (b - a) * t; }
    public static double clamp(double v, double min, double max) { return Math.max(min, Math.min(max, v)); }
    public static int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }

    public static double smoothstep(double t) { t = clamp(t, 0, 1); return t * t * (3 - 2 * t); }
    public static double smootherstep(double t) {
        t = clamp(t, 0, 1);
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    public static double wrapAngle(double rad) {
        while (rad > Math.PI) rad -= 2 * Math.PI;
        while (rad < -Math.PI) rad += 2 * Math.PI;
        return rad;
    }

    public static double map(double v, double inMin, double inMax, double outMin, double outMax) {
        double t = (v - inMin) / (inMax - inMin);
        return lerp(outMin, outMax, t);
    }

    public static double distanceSq(double x1, double y1, double z1, double x2, double y2, double z2) {
        double dx = x2 - x1, dy = y2 - y1, dz = z2 - z1;
        return dx * dx + dy * dy + dz * dz;
    }
}
