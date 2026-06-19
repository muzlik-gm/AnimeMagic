package com.anime.magic;

import com.anime.magic.animation.AnimationPlayer;
import com.anime.magic.animation.KeyframeAnimation;
import com.anime.magic.models.ModelDisplay;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AnimationPlayerTest {
    @Test void holdsFirstKeyframeAtTimeZero() {
        KeyframeAnimation anim = makeSimpleAnim();
        ModelDisplay mock = Mockito.mock(ModelDisplay.class);
        AnimationPlayer player = new AnimationPlayer(anim);
        player.tick(mock);
        Mockito.verify(mock).applyInterpolated(0f, 0f, 0f, 0f, 0f, 0f, 1f, 1f, 1f);
    }
    @Test void interpolatesMidwayCorrectly() {
        KeyframeAnimation anim = makeSimpleAnim();
        ModelDisplay mock = Mockito.mock(ModelDisplay.class);
        AnimationPlayer player = new AnimationPlayer(anim);
        for (int i = 0; i < 11; i++) player.tick(mock);
        Mockito.verify(mock, Mockito.atLeastOnce()).applyInterpolated(0f, 0f, 0f, 0f, 180f, 0f, 1.5f, 1.5f, 1.5f);
    }
    @Test void finishesAtLastKeyframe() {
        KeyframeAnimation anim = makeSimpleAnim();
        ModelDisplay mock = Mockito.mock(ModelDisplay.class);
        AnimationPlayer player = new AnimationPlayer(anim);
        for (int i = 0; i < 25; i++) player.tick(mock);
        assertTrue(player.isFinished());
    }
    private KeyframeAnimation makeSimpleAnim() {
        JSONObject json = new JSONObject("""
            {
              "length": 1.0,
              "loop": false,
              "bones": {
                "root": {
                  "rotation": { "0.0": [0,0,0], "1.0": [0,360,0] },
                  "scale":    { "0.0": [1,1,1], "1.0": [2,2,2] }
                }
              }
            }""");
        return KeyframeAnimation.parse("test", json);
    }
}
