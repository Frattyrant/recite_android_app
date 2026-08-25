package com.miearn.app.importing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class VocabularyFileDetectorTest {
    @Test
    fun detectsUtf8CsvFromContentEvenWhenNameAndMimeAreWrong() {
        val detected = VocabularyFileDetector.detect(
            fileName = "download.bin",
            mimeType = "application/octet-stream",
            prefix = "word,translation\nfixture,夹具".toByteArray(),
        )

        assertEquals(VocabularyFileType.CSV, detected.type)
    }

    @Test
    fun rejectsOleCompoundWorkbookWithActionableLegacyXlsMessage() {
        val error = assertThrows(UnsupportedVocabularyFileException::class.java) {
            VocabularyFileDetector.detect(
                fileName = "legacy.xls",
                mimeType = "application/vnd.ms-excel",
                prefix = byteArrayOf(
                    0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(),
                    0xA1.toByte(), 0xB1.toByte(), 0x1A, 0xE1.toByte(),
                ),
            )
        }

        assertEquals("暂不支持旧版 .xls 文件，请另存为 .xlsx 或 CSV 后重试", error.message)
    }

    @Test
    fun rejectsOrdinaryZipEvenWhenNamedAsWorkbook() {
        val zip = ByteArrayOutputStream().also { output ->
            ZipOutputStream(output).use { archive ->
                archive.putNextEntry(ZipEntry("notes.txt"))
                archive.write("not a workbook".toByteArray())
                archive.closeEntry()
            }
        }.toByteArray()

        val error = assertThrows(UnsupportedVocabularyFileException::class.java) {
            VocabularyFileDetector.detect("notes.xlsx", null, zip)
        }

        assertEquals(ImportFailureCode.UNSUPPORTED_ZIP, error.code)
    }

    @Test
    fun detectsUtf16TextWithoutBomBeforeBinaryCheck() {
        val detected = VocabularyFileDetector.detect(
            fileName = "words.txt",
            mimeType = "text/plain",
            prefix = "fixture\t夹具".toByteArray(StandardCharsets.UTF_16LE),
        )

        assertEquals(VocabularyFileType.TXT, detected.type)
    }

    @Test
    fun detectsUtf16TextWithoutBomWhenFilenameAndMimeAreOpaque() {
        val detected = VocabularyFileDetector.detect(
            fileName = "download.bin",
            mimeType = "application/octet-stream",
            prefix = "fixture\t夹具".toByteArray(StandardCharsets.UTF_16LE),
        )

        assertEquals(VocabularyFileType.TSV, detected.type)
    }
}
