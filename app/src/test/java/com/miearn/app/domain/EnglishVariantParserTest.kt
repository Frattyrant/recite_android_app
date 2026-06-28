package com.miearn.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class EnglishVariantParserTest {
    @Test
    fun splitsOnlyAsciiAndFullWidthSemicolons() {
        assertEquals(
            listOf("fixture", "jig", "checking fixture"),
            EnglishVariantParser.parse(" fixture；jig; checking fixture "),
        )
        assertEquals(
            listOf("support, pad/net"),
            EnglishVariantParser.parse("support, pad/net"),
        )
    }

    @Test
    fun removesEmptySegmentsAndPreservesOrder() {
        assertEquals(
            listOf("first", "second"),
            EnglishVariantParser.parse("； first ;; second ；"),
        )
    }
}
