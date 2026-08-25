package com.miearn.app.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreakMotionPolicyTest {
    @Test
    fun animationRequiresStartedScreenAndEnabledSystemScale() {
        assertTrue(StreakMotionPolicy.shouldAnimate(true, 1f))
        assertFalse(StreakMotionPolicy.shouldAnimate(false, 1f))
        assertFalse(StreakMotionPolicy.shouldAnimate(true, 0f))
        assertFalse(StreakMotionPolicy.shouldAnimate(true, -1f))
    }
}
