package com.miearn.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LearningContentPolicyTest {
    @Test
    fun missingChineseUsesStableLearningPlaceholder() {
        assertEquals("暂缺释义", LearningContentPolicy.displayChinese("   "))
        assertEquals("夹具", LearningContentPolicy.displayChinese(" 夹具 "))
    }

    @Test
    fun chineseChoiceRequiresActualTranslation() {
        assertFalse(LearningContentPolicy.hasChineseTranslation(""))
        assertFalse(LearningContentPolicy.hasChineseTranslation("  "))
        assertTrue(LearningContentPolicy.hasChineseTranslation("夹具"))
    }

    @Test
    fun fillBlankRequiresExampleContainingPrimaryEnglish() {
        assertFalse(LearningContentPolicy.canFillBlank("", "fixture"))
        assertFalse(LearningContentPolicy.canFillBlank("Inspect the jig.", "fixture"))
        assertTrue(LearningContentPolicy.canFillBlank("Inspect the Fixture.", "fixture"))
    }
}
