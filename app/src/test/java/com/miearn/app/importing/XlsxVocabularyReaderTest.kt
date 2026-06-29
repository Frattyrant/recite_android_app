package com.miearn.app.importing

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class XlsxVocabularyReaderTest {
    @Test
    fun readsFirstNonEmptySheetAndPreservesUnicodeCells() {
        val bytes = xlsx(
            "xl/workbook.xml" to """
                <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
                    xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                  <sheets>
                    <sheet name="空表" sheetId="1" r:id="rId1"/>
                    <sheet name="词库" sheetId="2" r:id="rId2"/>
                  </sheets>
                </workbook>
            """,
            "xl/_rels/workbook.xml.rels" to """
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Target="worksheets/sheet1.xml"/>
                  <Relationship Id="rId2" Target="worksheets/sheet2.xml"/>
                </Relationships>
            """,
            "xl/worksheets/sheet1.xml" to worksheet(""),
            "xl/worksheets/sheet2.xml" to worksheet(
                """
                <row r="1">
                  <c r="A1" t="inlineStr"><is><t>英文</t></is></c>
                  <c r="B1" t="inlineStr"><is><t>中文</t></is></c>
                </row>
                <row r="2">
                  <c r="A2" t="inlineStr"><is><t>fixture</t></is></c>
                  <c r="B2" t="inlineStr"><is><t>夹具</t></is></c>
                </row>
                """,
            ),
        )

        val rows = XlsxVocabularyReader()
            .rows(ByteArrayInputStream(bytes))
            .toList()

        assertEquals(listOf("英文", "中文"), rows[0].cells)
        assertEquals(listOf("fixture", "夹具"), rows[1].cells)
    }

    @Test
    fun readsSharedStringsAndPreservesMissingCells() {
        val bytes = xlsx(
            "xl/workbook.xml" to """
                <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
                    xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                  <sheets><sheet name="词库" sheetId="1" r:id="rId1"/></sheets>
                </workbook>
            """,
            "xl/_rels/workbook.xml.rels" to """
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Target="worksheets/sheet1.xml"/>
                </Relationships>
            """,
            "xl/sharedStrings.xml" to """
                <sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                  <si><t>fixture</t></si><si><t>夹具</t></si>
                </sst>
            """,
            "xl/worksheets/sheet1.xml" to worksheet(
                """
                <row r="3">
                  <c r="A3" t="s"><v>0</v></c>
                  <c r="C3" t="s"><v>1</v></c>
                </row>
                """,
            ),
        )

        val row = XlsxVocabularyReader().rows(ByteArrayInputStream(bytes)).single()

        assertEquals(3, row.rowIndex)
        assertEquals(listOf("fixture", "", "夹具"), row.cells)
    }

    @Test
    fun rejectsCorruptZip() {
        assertThrows(CorruptVocabularyFileException::class.java) {
            XlsxVocabularyReader()
                .rows(ByteArrayInputStream("PK not really xlsx".toByteArray()))
                .toList()
        }
    }

    private fun worksheet(rows: String): String =
        """<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>$rows</sheetData></worksheet>"""

    private fun xlsx(vararg entries: Pair<String, String>): ByteArray =
        ByteArrayOutputStream().use { output ->
            ZipOutputStream(output).use { zip ->
                entries.forEach { (name, content) ->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(content.trimIndent().toByteArray())
                    zip.closeEntry()
                }
            }
            output.toByteArray()
        }
}
