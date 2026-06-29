package com.miearn.app.importing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportFilePlannerTest {
    @Test
    fun recognizedHeaderWithoutEnglishRequestsManualMapping() {
        val rows = listOf(
            RawVocabularyRow(1, listOf("中文", "备注")),
            RawVocabularyRow(2, listOf("夹具", "重点")),
        )

        val plan = ImportFilePlanner.plan(rows)

        assertTrue(plan is ImportFilePlan.MappingRequired)
        plan as ImportFilePlan.MappingRequired
        assertEquals(listOf("中文", "备注"), plan.headers)
        assertEquals(rows.drop(1), plan.preview)
    }

    @Test
    fun headerlessFileUsesFirstColumnAndKeepsFirstRow() {
        val rows = listOf(
            RawVocabularyRow(1, listOf("fixture", "夹具")),
            RawVocabularyRow(2, listOf("jig", "治具")),
        )

        val plan = ImportFilePlanner.plan(rows) as ImportFilePlan.Ready

        assertEquals(
            ImportColumnMapping(mapOf(0 to ColumnRole.ENGLISH)),
            plan.mapping,
        )
        assertEquals(rows, plan.dataRows)
    }

    @Test
    fun suppliedMappingTreatsFirstRowAsHeader() {
        val rows = listOf(
            RawVocabularyRow(1, listOf("术语", "解释")),
            RawVocabularyRow(2, listOf("fixture", "夹具")),
        )
        val mapping = ImportColumnMapping(
            mapOf(0 to ColumnRole.ENGLISH, 1 to ColumnRole.CHINESE),
        )

        val plan = ImportFilePlanner.plan(rows, mapping) as ImportFilePlan.Ready

        assertEquals(mapping, plan.mapping)
        assertEquals(rows.drop(1), plan.dataRows)
    }
}
