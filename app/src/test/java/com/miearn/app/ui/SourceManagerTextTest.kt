package com.miearn.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SourceManagerTextTest {
    @Test
    fun blankSourceNameHasActionableValidationMessage() {
        assertEquals("请输入词库名称", sourceNameValidationMessage("   "))
        assertNull(sourceNameValidationMessage("专业英语"))
    }
}
