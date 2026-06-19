package com.anime.magic;

import com.anime.magic.util.MathUtil;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MathUtilTest {
    @Test void lerpAtExtremes() {
        assertEquals(0.0, MathUtil.lerp(0, 10, 0));
        assertEquals(10.0, MathUtil.lerp(0, 10, 1));
        assertEquals(5.0, MathUtil.lerp(0, 10, 0.5));
    }
    @Test void clampBoundsValues() {
        assertEquals(5, MathUtil.clamp(10, 0, 5));
        assertEquals(0, MathUtil.clamp(-1, 0, 5));
        assertEquals(3, MathUtil.clamp(3, 0, 5));
    }
    @Test void smoothstepAtBoundaries() {
        assertEquals(0.0, MathUtil.smoothstep(0));
        assertEquals(1.0, MathUtil.smoothstep(1));
        double mid = MathUtil.smoothstep(0.5);
        assertTrue(mid > 0.4 && mid < 0.6);
    }
    @Test void smootherstepIsMonotonic() {
        double prev = 0;
        for (double t = 0; t <= 1; t += 0.05) {
            double v = MathUtil.smootherstep(t);
            assertTrue(v >= prev - 1e-9);
            prev = v;
        }
        assertEquals(1.0, MathUtil.smootherstep(1), 1e-9);
    }
    @Test void wrapAngleInRange() {
        assertTrue(Math.abs(MathUtil.wrapAngle(Math.PI * 3)) <= Math.PI);
        assertTrue(Math.abs(MathUtil.wrapAngle(-Math.PI * 3)) <= Math.PI);
    }
    @Test void mapLinear() {
        assertEquals(0.0, MathUtil.map(0, 0, 10, 0, 100));
        assertEquals(100.0, MathUtil.map(10, 0, 10, 0, 100));
        assertEquals(50.0, MathUtil.map(5, 0, 10, 0, 100));
    }
    @Test void distanceSqMatchesFormula() {
        assertEquals(0, MathUtil.distanceSq(1, 2, 3, 1, 2, 3));
        assertEquals(9, MathUtil.distanceSq(0, 0, 0, 3, 0, 0));
        assertEquals(14, MathUtil.distanceSq(0, 0, 0, 1, 2, 3));
    }
}
