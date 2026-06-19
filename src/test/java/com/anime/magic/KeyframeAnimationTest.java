package com.anime.magic;

import com.anime.magic.animation.KeyframeAnimation;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KeyframeAnimationTest {
    @Test void parsesSimpleAnimation() {
        JSONObject json = new JSONObject("""
            {
              "loop": false, "length": 2.0,
              "bones": {
                "root": {
                  "rotation": { "0.0": [0,0,0], "1.0": [0,180,0], "2.0": [0,360,0] },
                  "scale":    { "0.0": [1,1,1], "2.0": [2,2,2] }
                }
              }
            }""");
        KeyframeAnimation anim = KeyframeAnimation.parse("animation.test", json);
        assertNotNull(anim);
        assertEquals("animation.test", anim.name());
        assertEquals(2.0, anim.lengthSeconds(), 1e-9);
        assertEquals(3, anim.rotation().size());
        assertEquals(2, anim.scale().size());
        assertEquals(40, anim.lengthTicks());
    }
    @Test void parsesEasingFromObjectForm() {
        JSONObject json = new JSONObject("""
            {
              "length": 1.0,
              "bones": {
                "root": {
                  "scale": {
                    "0.0": {"vector": [1,1,1], "easing": "easeOutCubic"},
                    "1.0": [2,2,2]
                  }
                }
              }
            }""");
        KeyframeAnimation anim = KeyframeAnimation.parse("test", json);
        assertEquals(com.anime.magic.animation.Easing.EASE_OUT_CUBIC, anim.scale().get(0).easing());
    }
    @Test void handlesAnimationWithNoBones() {
        JSONObject json = new JSONObject("""
            { "length": 1.5, "loop": true }""");
        KeyframeAnimation anim = KeyframeAnimation.parse("idle", json);
        assertNotNull(anim);
        assertTrue(anim.loop());
        assertTrue(anim.rotation().isEmpty());
    }
}
