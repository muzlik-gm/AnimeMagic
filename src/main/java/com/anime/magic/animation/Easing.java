package com.anime.magic.animation;

/**
 * Easing functions used by keyframe interpolation. Mirrors Blockbench's animation editor
 * options. Each easing is a pure function f(t) -> t' where t is linear progress 0->1.
 */
public enum Easing {
    LINEAR,
    EASE_IN_SINE, EASE_OUT_SINE, EASE_IN_OUT_SINE,
    EASE_IN_QUAD, EASE_OUT_QUAD, EASE_IN_OUT_QUAD,
    EASE_IN_CUBIC, EASE_OUT_CUBIC, EASE_IN_OUT_CUBIC,
    EASE_IN_EXPO, EASE_OUT_EXPO, EASE_IN_OUT_EXPO,
    EASE_IN_BACK, EASE_OUT_BACK, EASE_IN_OUT_BACK,
    EASE_IN_ELASTIC, EASE_OUT_ELASTIC, EASE_IN_OUT_ELASTIC,
    EASE_IN_BOUNCE, EASE_OUT_BOUNCE, EASE_IN_OUT_BOUNCE;

    public double apply(double t) {
        if (t <= 0) return 0;
        if (t >= 1) return 1;
        return switch (this) {
            case LINEAR -> t;
            case EASE_IN_SINE -> 1 - Math.cos((t * Math.PI) / 2);
            case EASE_OUT_SINE -> Math.sin((t * Math.PI) / 2);
            case EASE_IN_OUT_SINE -> -(Math.cos(Math.PI * t) - 1) / 2;
            case EASE_IN_QUAD -> t * t;
            case EASE_OUT_QUAD -> 1 - (1 - t) * (1 - t);
            case EASE_IN_OUT_QUAD -> t < 0.5 ? 2 * t * t : 1 - Math.pow(-2 * t + 2, 2) / 2;
            case EASE_IN_CUBIC -> t * t * t;
            case EASE_OUT_CUBIC -> 1 - Math.pow(1 - t, 3);
            case EASE_IN_OUT_CUBIC -> t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
            case EASE_IN_EXPO -> t == 0 ? 0 : Math.pow(2, 10 * t - 10);
            case EASE_OUT_EXPO -> t == 1 ? 1 : 1 - Math.pow(2, -10 * t);
            case EASE_IN_OUT_EXPO -> t == 0 ? 0 : t == 1 ? 1
                    : t < 0.5 ? Math.pow(2, 20 * t - 10) / 2
                    : (2 - Math.pow(2, -20 * t + 10)) / 2;
            case EASE_IN_BACK -> {
                double c1 = 1.70158, c3 = c1 + 1;
                yield c3 * t * t * t - c1 * t * t;
            }
            case EASE_OUT_BACK -> {
                double c1 = 1.70158, c3 = c1 + 1;
                yield 1 + c3 * Math.pow(t - 1, 3) + c1 * Math.pow(t - 1, 2);
            }
            case EASE_IN_OUT_BACK -> {
                double c1 = 1.70158, c2 = c1 * 1.525;
                yield t < 0.5
                        ? (Math.pow(2 * t, 2) * ((c2 + 1) * 2 * t - c2)) / 2
                        : (Math.pow(2 * t - 2, 2) * ((c2 + 1) * (t * 2 - 2) + c2) + 2) / 2;
            }
            case EASE_IN_ELASTIC -> t == 0 ? 0 : t == 1 ? 1
                    : -Math.pow(2, 10 * t - 10) * Math.sin((t * 10 - 10.75) * (2 * Math.PI) / 3);
            case EASE_OUT_ELASTIC -> t == 0 ? 0 : t == 1 ? 1
                    : Math.pow(2, -10 * t) * Math.sin((t * 10 - 0.75) * (2 * Math.PI) / 3) + 1;
            case EASE_IN_OUT_ELASTIC -> t == 0 ? 0 : t == 1 ? 1
                    : t < 0.5
                        ? -(Math.pow(2, 20 * t - 10) * Math.sin((20 * t - 11.125) * (2 * Math.PI) / 4.5)) / 2
                        : (Math.pow(2, -20 * t + 10) * Math.sin((20 * t - 11.125) * (2 * Math.PI) / 4.5)) / 2 + 1;
            case EASE_IN_BOUNCE -> 1 - EASE_OUT_BOUNCE.apply(1 - t);
            case EASE_OUT_BOUNCE -> {
                double n1 = 7.5625, d1 = 2.75;
                if (t < 1 / d1) yield n1 * t * t;
                else if (t < 2 / d1) { double tt = t - 1.5 / d1; yield n1 * tt * tt + 0.75; }
                else if (t < 2.5 / d1) { double tt = t - 2.25 / d1; yield n1 * tt * tt + 0.9375; }
                else { double tt = t - 2.625 / d1; yield n1 * tt * tt + 0.984375; }
            }
            case EASE_IN_OUT_BOUNCE -> t < 0.5
                    ? (1 - EASE_OUT_BOUNCE.apply(1 - 2 * t)) / 2
                    : (1 + EASE_OUT_BOUNCE.apply(2 * t - 1)) / 2;
        };
    }

    public static Easing fromString(String s) {
        if (s == null || s.isEmpty()) return LINEAR;
        String norm = s.replaceAll("([a-z])([A-Z])", "$1_$2").replace("-", "_").toUpperCase();
        try { return Easing.valueOf(norm); }
        catch (IllegalArgumentException e) { return LINEAR; }
    }
}
