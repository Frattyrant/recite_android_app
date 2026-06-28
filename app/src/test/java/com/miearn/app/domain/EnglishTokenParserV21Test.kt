package com.miearn.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class EnglishTokenParserV21Test {
    @Test
    fun splitsSemicolonsSlashesBackslashesAndWhitespace() {
        assertEquals(
            listOf("fixture", "jig", "support", "clamp", "block", "NC", "block"),
            EnglishVariantParser.parse(
                " fixture；jig / support\\clamp block; NC block ",
            ),
        )
    }

    @Test
    fun keepsCommaJoinedTextAndDropsEmptySegments() {
        assertEquals(
            listOf("support,", "pad"),
            EnglishVariantParser.parse(" / support,   pad \\\\ "),
        )
    }

    @Test
    fun blankInputProducesNoPlayableToken() {
        assertEquals(emptyList<String>(), EnglishVariantParser.parse("  / \\ ； "))
    }
}
