package com.miearn.app.data.seed

import com.miearn.app.data.local.WordEntity
import org.json.JSONObject

data class ContentSeed(
    val contentVersion: String,
    val words: List<WordEntity>,
)

object SeedJsonParser {
    fun parse(json: String): ContentSeed {
        try {
            val root = JSONObject(json)
            val version = root.getString("contentVersion").requireValue("contentVersion")
            val array = root.getJSONArray("words")
            val words = buildList(array.length()) {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    add(
                        WordEntity(
                            id = item.required("id"),
                            category = item.required("category"),
                            categoryLabel = item.required("categoryLabel"),
                            sourceIndex = item.getInt("sourceIndex"),
                            kind = item.required("kind"),
                            section = item.optString("section"),
                            english = item.required("english"),
                            primaryEnglish = item.required("primaryEnglish"),
                            phonetic = item.required("phonetic"),
                            chinese = item.required("chinese"),
                            note = item.optString("note"),
                            exampleEn = item.required("exampleEn"),
                            exampleZh = item.required("exampleZh"),
                            audioText = item.required("audioText"),
                            audioAsset = item.required("audioAsset"),
                        ),
                    )
                }
            }
            return ContentSeed(version, words)
        } catch (error: Exception) {
            throw IllegalArgumentException("Invalid MIearn seed JSON", error)
        }
    }

    private fun JSONObject.required(key: String): String = getString(key).requireValue(key)

    private fun String.requireValue(key: String): String =
        trim().takeIf { it.isNotEmpty() }
            ?: throw IllegalArgumentException("Missing required field: $key")
}

