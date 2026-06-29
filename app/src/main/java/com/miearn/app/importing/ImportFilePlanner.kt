package com.miearn.app.importing

sealed interface ImportFilePlan {
    val headers: List<String>
    val preview: List<RawVocabularyRow>

    data class MappingRequired(
        override val headers: List<String>,
        override val preview: List<RawVocabularyRow>,
    ) : ImportFilePlan

    data class Ready(
        override val headers: List<String>,
        override val preview: List<RawVocabularyRow>,
        val mapping: ImportColumnMapping,
        val dataRows: List<RawVocabularyRow>,
    ) : ImportFilePlan
}

object ImportFilePlanner {
    fun plan(
        rows: List<RawVocabularyRow>,
        suppliedMapping: ImportColumnMapping? = null,
    ): ImportFilePlan {
        val first = rows.firstOrNull() ?: throw EmptyVocabularyFileException()
        val detectedRoles = first.cells.map(ImportSanitizer::detectHeader)
        val hasHeader = detectedRoles.any { it != ColumnRole.IGNORE }
        val headers = if (hasHeader || suppliedMapping != null) {
            first.cells
        } else {
            List(first.cells.size) { "第 ${it + 1} 列" }
        }
        val dataRows = if (hasHeader || suppliedMapping != null) rows.drop(1) else rows
        val preview = dataRows.take(3)

        if (suppliedMapping != null) {
            return ImportFilePlan.Ready(headers, preview, suppliedMapping, dataRows)
        }
        if (!hasHeader) {
            return ImportFilePlan.Ready(
                headers,
                preview,
                ImportSanitizer.headerlessMapping(first.cells.size),
                dataRows,
            )
        }
        val automatic = ImportSanitizer.autoMappingOrNull(headers)
            ?: return ImportFilePlan.MappingRequired(headers, preview)
        return ImportFilePlan.Ready(headers, preview, automatic, dataRows)
    }
}
