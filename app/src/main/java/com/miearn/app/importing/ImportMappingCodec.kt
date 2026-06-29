package com.miearn.app.importing

import org.json.JSONArray
import org.json.JSONObject

object ImportMappingCodec {
    fun encode(mapping: ImportColumnMapping): String = JSONObject().apply {
        mapping.byIndex.toSortedMap().forEach { (index, role) ->
            put(index.toString(), role.name)
        }
    }.toString()

    fun decodeMapping(json: String): ImportColumnMapping {
        val value = JSONObject(json)
        val mapping = value.keys().asSequence().associate { key ->
            key.toInt() to ColumnRole.valueOf(value.getString(key))
        }
        return ImportColumnMapping(mapping)
    }

    fun encodeHeaders(headers: List<String>): String = JSONArray(headers).toString()

    fun decodeHeaders(json: String): List<String> {
        val value = JSONArray(json)
        return List(value.length(), value::getString)
    }

    fun encodePreview(rows: List<RawVocabularyRow>): String = JSONArray().apply {
        rows.forEach { row ->
            put(
                JSONObject()
                    .put("rowIndex", row.rowIndex)
                    .put("cells", JSONArray(row.cells)),
            )
        }
    }.toString()

    fun decodePreview(json: String): List<RawVocabularyRow> {
        val value = JSONArray(json)
        return List(value.length()) { index ->
            val row = value.getJSONObject(index)
            val cells = row.getJSONArray("cells")
            RawVocabularyRow(
                rowIndex = row.getInt("rowIndex"),
                cells = List(cells.length(), cells::getString),
            )
        }
    }
}
