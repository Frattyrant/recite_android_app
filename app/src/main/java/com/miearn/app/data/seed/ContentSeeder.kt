package com.miearn.app.data.seed

import android.content.Context
import androidx.room.withTransaction
import com.miearn.app.data.local.AppDatabase
import com.miearn.app.data.local.ContentMetadataEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ContentSeeder(
    private val context: Context,
    private val database: AppDatabase,
) {
    suspend fun ensureSeeded(): Int = withContext(Dispatchers.IO) {
        val json = context.assets.open(CONTENT_ASSET).bufferedReader().use { it.readText() }
        val seed = SeedJsonParser.parse(json)
        val installedVersion = database.metadataDao().get(CONTENT_VERSION_KEY)
        if (installedVersion == seed.contentVersion && database.wordDao().count() == seed.words.size) {
            return@withContext seed.words.size
        }
        database.withTransaction {
            seed.words.chunked(250).forEach { database.wordDao().upsertAll(it) }
            database.metadataDao().put(
                ContentMetadataEntity(CONTENT_VERSION_KEY, seed.contentVersion),
            )
        }
        seed.words.size
    }

    companion object {
        const val CONTENT_ASSET = "content/words_v1.json"
        const val CONTENT_VERSION_KEY = "content_version"
    }
}

