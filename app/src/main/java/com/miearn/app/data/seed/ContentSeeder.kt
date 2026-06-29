package com.miearn.app.data.seed

import android.content.Context
import androidx.room.withTransaction
import com.miearn.app.data.local.AppDatabase
import com.miearn.app.data.local.ContentMetadataEntity
import com.miearn.app.data.local.SourceEntity
import com.miearn.app.data.local.SourceType
import com.miearn.app.data.local.WordSourceCrossRef
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
        database.withTransaction {
            if (
                installedVersion != seed.contentVersion ||
                database.wordDao().builtInCount() != seed.words.size
            ) {
                seed.words.chunked(250).forEach { database.wordDao().upsertAll(it) }
                database.metadataDao().put(ContentMetadataEntity(CONTENT_VERSION_KEY, seed.contentVersion))
            }
            val grouped = seed.words.groupBy { it.category }
            database.sourceDao().upsertAll(
                grouped.map { (sourceId, words) ->
                    SourceEntity(
                        sourceId = sourceId,
                        displayName = words.first().categoryLabel,
                        type = SourceType.BUILTIN.name,
                        wordCount = words.size,
                    )
                },
            )
            grouped.forEach { (sourceId, words) ->
                database.sourceDao().upsertLinks(
                    words.map { WordSourceCrossRef(sourceId, it.id, it.sourceIndex) },
                )
            }
        }
        seed.words.size
    }

    companion object {
        const val CONTENT_ASSET = "content/words_v1.json"
        const val CONTENT_VERSION_KEY = "content_version"
    }
}
