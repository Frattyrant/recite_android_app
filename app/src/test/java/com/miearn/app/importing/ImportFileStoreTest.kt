package com.miearn.app.importing

import java.nio.file.Files
import java.util.concurrent.CancellationException
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
            assertEquals(ImportFailureCode.FILE_TOO_LARGE, (error as VocabularyImportException).code)
            assertTrue(directory.listFiles().orEmpty().isEmpty())
            assertFalse(directory.resolve("job-two.partial").exists())
            assertFalse(directory.resolve("job-two.source").exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun cleanupRemovesPublishedAndInterruptedFiles() {
        val directory = Files.createTempDirectory("miearn-import-store").toFile()
        try {
            val store = ImportFileStore(directory)
            store.target("job-three").writeBytes(byteArrayOf(1))
            directory.resolve("job-three.partial").writeBytes(byteArrayOf(2))

            store.cleanup("job-three")

            assertFalse(store.target("job-three").exists())
            assertFalse(directory.resolve("job-three.partial").exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun checkpointCancellationCleansPartialCopy() {
        val directory = Files.createTempDirectory("miearn-import-store").toFile()
        try {
            val store = ImportFileStore(directory)
            val error = runCatching {
                store.copy("job-four", byteArrayOf(1, 2, 3).inputStream()) {
                    throw CancellationException("cancelled")
                }
            }.exceptionOrNull()

            assertTrue(error is CancellationException)
            assertTrue(directory.listFiles().orEmpty().isEmpty())
        } finally {
            directory.deleteRecursively()
        }
    }
}
