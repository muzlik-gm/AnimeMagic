package com.anime.magic;

import com.anime.magic.animation.Easing;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EasingTest {
    @Test void allEasingsStartAtZeroEndAtOne() {
        for (Easing e : Easing.values()) {
            assertEquals(0.0, e.apply(0), 1e-9, e.name() + " should be 0 at t=0");
            assertEquals(1.0, e.apply(1), 1e-9, e.name() + " should be 1 at t=1");
        }
    }
    @Test void linearIsIdentity() {
        assertEquals(0.25, Easing.LINEAR.apply(0.25), 1e-9);
        assertEquals(0.5, Easing.LINEAR.apply(0.5), 1e-9);
    }
    @Test void fromStringParsesKnownEasings() {
        assertEquals(Easing.LINEAR, Easing.fromString("linear"));
        assertEquals(Easing.EASE_IN_OUT_SINE, Easing.fromString("easeInOutSine"));
        assertEquals(Easing.EASE_OUT_BACK, Easing.fromString("ease-out-back"));
        assertEquals(Easing.LINEAR, Easing.fromString(""));
        assertEquals(Easing.LINEAR, Easing.fromString(null));
        assertEquals(Easing.LINEAR, Easing.fromString("not_a_real_easing"));
    }
}
