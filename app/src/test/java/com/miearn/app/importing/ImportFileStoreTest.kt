package com.miearn.app.importing

import java.nio.file.Files
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportFileStoreTest {
    @Test
    fun successfulCopyPublishesOnlyCompleteInternalFile() {
        val directory = Files.createTempDirectory("miearn-import-store").toFile()
        try {
            val store = ImportFileStore(directory, maxBytes = 8)

            val result = store.copy("job-one", byteArrayOf(1, 2, 3).inputStream())

            assertEquals("job-one.source", result.name)
            assertArrayEquals(byteArrayOf(1, 2, 3), result.readBytes())
            assertEquals(listOf("job-one.source"), directory.list()?.sorted())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun oversizedCopyDeletesPartialAndFinalFiles() {
        val directory = Files.createTempDirectory("miearn-import-store").toFile()
        try {
            val store = ImportFileStore(directory, maxBytes = 3)

            val error = runCatching {
                store.copy("job-two", byteArrayOf(1, 2, 3, 4).inputStream())
            }.exceptionOrNull()

            assertTrue(error is VocabularyImportException)
            assertTrue(directory.listFiles().orEmpty().isEmpty())
            assertFalse(directory.resolve("job-two.partial").exists())
            assertFalse(directory.resolve("job-two.source").exists())
        } finally {
            directory.deleteRecursively()
        }
    }
}
