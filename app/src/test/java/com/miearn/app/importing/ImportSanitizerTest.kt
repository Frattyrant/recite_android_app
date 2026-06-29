package com.miearn.app.importing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportSanitizerTest {
    @Test fun normalizesAndValidatesEnglish() {
        assertEquals("flow drill screw", ImportSanitizer.normalizeEnglish("  Flow   Drill Screw "))
        assertTrue(ImportSanitizer.isValidEnglish("fixture; jig"))
        assertTrue(ImportSanitizer.isValidEnglish("end-of-arm tooling"))
        assertFalse(ImportSanitizer.isValidEnglish("夹具"))
    }

    @Test fun detectsCommonChineseAndEnglishHeaders() {
        assertEquals(ColumnRole.ENGLISH, ImportSanitizer.detectHeader("单词"))
        assertEquals(ColumnRole.CHINESE, ImportSanitizer.detectHeader("中文释义"))
        assertEquals(ColumnRole.EXAMPLE_ZH, ImportSanitizer.detectHeader("例句翻译"))
        assertEquals(ColumnRole.PHONETIC, ImportSanitizer.detectHeader("phonetic"))
    }

    @Test fun requiresManualMappingWhenHeaderHasNoUniqueEnglishColumn() {
        assertEquals(
            null,
            ImportSanitizer.autoMappingOrNull(listOf("中文", "备注")),
        )
        assertEquals(
            null,
            ImportSanitizer.autoMappingOrNull(listOf("英文", "word", "中文")),
        )
        assertEquals(
            ImportColumnMapping(
                mapOf(0 to ColumnRole.ENGLISH, 1 to ColumnRole.CHINESE),
            ),
            ImportSanitizer.autoMappingOrNull(listOf("英文", "中文")),
        )
    }
}
