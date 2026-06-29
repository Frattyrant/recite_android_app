package com.miearn.app.importing

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ImportMappingCodecTest {
    @Test
    fun mappingHeadersAndPreviewRoundTripWithoutLosingUnicode() {
        val mapping = ImportColumnMapping(
            mapOf(
                0 to ColumnRole.ENGLISH,
                1 to ColumnRole.CHINESE,
                2 to ColumnRole.IGNORE,
            ),
        )
        val preview = listOf(
            RawVocabularyRow(2, listOf("fixture", "夹具", "")),
            RawVocabularyRow(3, listOf("jig", "治具", "重点")),
        )

        assertEquals(mapping, ImportMappingCodec.decodeMapping(ImportMappingCodec.encode(mapping)))
        assertEquals(
            listOf("英文", "中文", "备注"),
            ImportMappingCodec.decodeHeaders(
                ImportMappingCodec.encodeHeaders(listOf("英文", "中文", "备注")),
            ),
        )
        assertEquals(
            preview,
            ImportMappingCodec.decodePreview(ImportMappingCodec.encodePreview(preview)),
        )
    }
}
