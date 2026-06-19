package com.anime.magic.animation;

import com.anime.magic.models.ModelDisplay;
import org.jetbrains.annotations.NotNull;
import java.util.List;

/**
 * Plays a KeyframeAnimation on a ModelDisplay by interpolating per-channel keyframes
 * every tick and writing the resulting transform back to the display via applyInterpolated.
 */
public final class AnimationPlayer {
    private final KeyframeAnimation anim;
    private int tick;
    private boolean finished;

    public AnimationPlayer(@NotNull KeyframeAnimation anim) {
        this.anim = anim;
        this.tick = 0;
    }

    public void tick(@NotNull ModelDisplay display) {
        if (finished) return;
        int total = anim.lengthTicks();
        if (total <= 0) { finished = true; return; }

        double timeSec = tick / 20.0;
        if (timeSec >= anim.lengthSeconds()) {
            if (anim.loop()) { tick = 0; timeSec = 0; }
            else { timeSec = anim.lengthSeconds(); finished = true; }
        }

        float[] rot = sample(anim.rotation(), timeSec, new float[]{0, 0, 0});
        float[] pos = sample(anim.position(), timeSec, new float[]{0, 0, 0});
        float[] scl = sample(anim.scale(), timeSec, new float[]{1, 1, 1});

        display.applyInterpolated(pos[0], pos[1], pos[2], rot[0], rot[1], rot[2], scl[0], scl[1], scl[2]);
        tick++;
    }

    private float[] sample(List<KeyframeAnimation.ChannelKeyframes> keys, double time, float[] fallback) {
        if (keys.isEmpty()) return fallback;
        if (time <= keys.get(0).time()) return toFloat(keys.get(0));
        KeyframeAnimation.ChannelKeyframes last = keys.get(keys.size() - 1);
        if (time >= last.time()) return toFloat(last);
        for (int i = 0; i < keys.size() - 1; i++) {
            var a = keys.get(i);
            var b = keys.get(i + 1);
            if (time >= a.time() && time <= b.time()) {
                double span = b.time() - a.time();
                double t = span <= 0 ? 0 : (time - a.time()) / span;
                double eased = a.easing().apply(t);
                return new float[] {
                        lerp(a.x(), b.x(), eased),
                        lerp(a.y(), b.y(), eased),
                        lerp(a.z(), b.z(), eased)
                };
            }
        }
        return fallback;
    }

    private static float[] toFloat(KeyframeAnimation.ChannelKeyframes k) {
        return new float[] { (float) k.x(), (float) k.y(), (float) k.z() };
    }

    private static float lerp(double a, double b, double t) { return (float) (a + (b - a) * t); }

    public boolean isFinished() { return finished; }
    public int currentTick() { return tick; }
    public @NotNull KeyframeAnimation animation() { return anim; }
}
