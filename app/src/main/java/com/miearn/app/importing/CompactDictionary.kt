package com.miearn.app.importing

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import java.io.File
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class DictionaryEntry(
    val word: String,
    val phonetic: String,
    val translation: String,
    val exchange: String,
)

fun interface DictionaryLookup {
    suspend fun lookup(word: String): DictionaryEntry?
}

class CompactDictionary(context: Context) : DictionaryLookup {
    private val appContext = context.applicationContext
    private val openMutex = Mutex()
    @Volatile private var openedDatabase: SQLiteDatabase? = null

    override suspend fun lookup(word: String): DictionaryEntry? = withContext(Dispatchers.IO) {
        runCatching {
            val database = database()
            database.query(
                "entry",
                arrayOf("word", "phonetic", "translation", "exchange"),
                "word = ? COLLATE NOCASE",
                arrayOf(ImportSanitizer.normalizeEnglish(word)),
                null,
                null,
                null,
                "1",
            ).use { cursor ->
                if (!cursor.moveToFirst()) return@runCatching null
                DictionaryEntry(
                    word = cursor.getString(0).orEmpty(),
                    phonetic = cursor.getString(1).orEmpty(),
                    translation = cursor.getString(2).orEmpty(),
                    exchange = cursor.getString(3).orEmpty(),
                )
            }
        }.onFailure {
            Log.w(LOG_TAG, "Offline dictionary lookup unavailable", it)
        }.getOrNull()
    }

    private suspend fun database(): SQLiteDatabase =
        openedDatabase ?: openMutex.withLock {
            openedDatabase ?: SQLiteDatabase.openDatabase(
                installIfNeeded().absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY,
            ).also { openedDatabase = it }
        }

    private fun installIfNeeded(): File {
        val metadata = appContext.assets.open(MANIFEST_PATH).bufferedReader().use {
            JSONObject(it.readText())
        }
        val expectedDatabaseHash = metadata.getString("databaseSha256")
        val expectedGzipHash = metadata.getString("gzipSha256")
        val directory = File(appContext.noBackupFilesDir, "dictionaries").apply { mkdirs() }
        val target = File(directory, DATABASE_NAME)
        if (target.isFile && sha256(target) == expectedDatabaseHash) return target

        val temporaryGzip = File(directory, "$DATABASE_NAME.gz.tmp")
        val temporaryDatabase = File(directory, "$DATABASE_NAME.tmp")
        try {
            appContext.assets.open(ASSET_PATH).use { input ->
                temporaryGzip.outputStream().buffered().use(input::copyTo)
            }
            check(sha256(temporaryGzip) == expectedGzipHash) {
                "ECDICT gzip asset hash mismatch"
            }
            GZIPInputStream(temporaryGzip.inputStream().buffered()).use { input ->
                temporaryDatabase.outputStream().buffered().use(input::copyTo)
            }
            check(sha256(temporaryDatabase) == expectedDatabaseHash) {
                "ECDICT database hash mismatch"
            }
            if (!temporaryDatabase.renameTo(target)) {
                temporaryDatabase.copyTo(target, overwrite = true)
                temporaryDatabase.delete()
            }
            return target
        } finally {
            temporaryGzip.delete()
            temporaryDatabase.delete()
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val ASSET_PATH = "dictionaries/ecdict_compact.db.gz.bin"
        const val MANIFEST_PATH = "dictionaries/ecdict_compact_manifest.json"
        private const val DATABASE_NAME = "ecdict_compact.db"
        private const val LOG_TAG = "CompactDictionary"
    }
}
