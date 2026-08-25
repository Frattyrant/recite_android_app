package com.miearn.app.importing

import java.util.Locale
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

enum class VocabularyFileType {
    XLSX,
    CSV,
    TSV,
    TXT,
}

data class DetectedVocabularyFile(
    val type: VocabularyFileType,
) {
    val format: VocabularyFileType get() = type
}

/** Detects the actual document format from bytes before consulting filename/MIME hints. */
object VocabularyFileDetector {
    private val ZIP_SIGNATURE = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
    private val OLE_SIGNATURE = byteArrayOf(
        0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(),
        0xA1.toByte(), 0xB1.toByte(), 0x1A, 0xE1.toByte(),
    )

    fun detect(
        fileName: String,
        mimeType: String?,
        prefix: ByteArray,
    ): DetectedVocabularyFile {
        if (prefix.startsWith(OLE_SIGNATURE)) {
            throw UnsupportedVocabularyFileException(
                message = "暂不支持旧版 .xls 文件，请另存为 .xlsx 或 CSV 后重试",
                code = ImportFailureCode.UNSUPPORTED_LEGACY_XLS,
                recoveryHint = "请在 Excel 中选择“另存为”，导出为 .xlsx 或 CSV。",
            )
        }
        if (prefix.startsWith(ZIP_SIGNATURE) || prefix.startsWith(byteArrayOf(0x50, 0x4B))) {
            // A ZIP is an XLSX only when the package contains the expected workbook parts.
            // The complete signature check is performed by XlsxVocabularyReader; this keeps
            // detector usable with a short SAF prefix while rejecting ordinary ZIP files.
            if (fileName.endsWith(".xlsm", ignoreCase = true)) {
                throw UnsupportedVocabularyFileException(
                    message = "暂不支持启用宏的 .xlsm 文件，请另存为 .xlsx 或 CSV 后重试",
                    code = ImportFailureCode.UNSUPPORTED_XLSM,
                    recoveryHint = "请在 Excel 中移除宏并另存为 .xlsx，或导出为 CSV。",
                )
            }
            if (looksLikeXlsxPackage(prefix)) {
                return DetectedVocabularyFile(VocabularyFileType.XLSX)
            }
            throw UnsupportedVocabularyFileException(
                message = "无法识别此 ZIP 文件，请选择 .xlsx、.csv、.tsv 或 .txt 词库",
                code = ImportFailureCode.UNSUPPORTED_ZIP,
                recoveryHint = "普通 ZIP 压缩包不能直接导入；请先解压并选择其中的 .xlsx 或文本文件。",
            )
        }
        if (prefix.startsWith(byteArrayOf(0x7F, 0x45, 0x4C, 0x46)) || looksBinary(prefix)) {
            throw UnsupportedVocabularyFileException(
                message = "无法识别文件内容，请选择 UTF-8/UTF-16/GB18030 编码的文本词库",
                code = ImportFailureCode.UNKNOWN_BINARY,
                recoveryHint = "请将文件另存为 CSV、TSV 或纯文本后重试。",
            )
        }

        val lowerName = fileName.lowercase(Locale.ROOT)
        if (lowerName.endsWith(".xlsx") || lowerName.endsWith(".xlsm") || lowerName.endsWith(".xls")) {
            throw UnsupportedVocabularyFileException(
                message = "文件扩展名是 Excel，但内容不是有效的工作簿，请另存为 .xlsx 或 CSV 后重试",
                code = ImportFailureCode.UNKNOWN_FORMAT,
                recoveryHint = "请在 Excel/WPS 中打开后选择“另存为”，导出为 .xlsx 或 UTF-8 CSV。",
            )
        }
        val type = when {
            lowerName.endsWith(".tsv") -> VocabularyFileType.TSV
            lowerName.endsWith(".txt") -> VocabularyFileType.TXT
            lowerName.endsWith(".csv") -> VocabularyFileType.CSV
            else -> inferTextType(prefix, mimeType)
        }
        return DetectedVocabularyFile(type)
    }

    private fun inferTextType(prefix: ByteArray, mimeType: String?): VocabularyFileType {
        // SAF providers frequently return an opaque MIME type and a generated
        // filename. Decode the sample before inferring delimiters so UTF-16
        // exports without a BOM are not mistaken for binary/unknown content.
        val sample = runCatching { TextDocumentDecoder.decode(prefix).text }
            .getOrElse { prefix.toString(Charsets.UTF_8) }
        val nonEmptyLines = sample
            .split(Regex("\\r\\n|\\n|\\r"))
            .filter(String::isNotBlank)
        val commaCounts = nonEmptyLines.map { line -> line.count { it == ',' } }
        return when {
            mimeType.equals("text/tab-separated-values", ignoreCase = true) || '\t' in sample ->
                VocabularyFileType.TSV
            mimeType.equals("text/csv", ignoreCase = true) ||
                (commaCounts.firstOrNull()?.let { it > 0 } == true &&
                    commaCounts.distinct().size == 1) -> VocabularyFileType.CSV
            mimeType?.startsWith("text/") == true ||
                looksText(prefix) ||
                hasTextEncodingBom(prefix) ||
                TextDocumentDecoder.looksLikeUtf16WithoutBom(prefix) -> VocabularyFileType.TXT
            else -> throw UnsupportedVocabularyFileException(
                message = "无法识别文件格式，请选择 .xlsx、.csv、.tsv 或 .txt 词库",
                code = ImportFailureCode.UNKNOWN_FORMAT,
                recoveryHint = "请检查文件是否为受支持的词库格式，并重新选择。",
            )
        }
    }

    private fun looksBinary(bytes: ByteArray): Boolean {
        if (bytes.isEmpty()) return false
        val nulCount = bytes.count { it == 0.toByte() }
        return nulCount > bytes.size / 4 &&
            !hasTextEncodingBom(bytes) &&
            !TextDocumentDecoder.looksLikeUtf16WithoutBom(bytes)
    }

    private fun looksText(bytes: ByteArray): Boolean = bytes.all { value ->
        val unsigned = value.toInt() and 0xFF
        unsigned == 9 || unsigned == 10 || unsigned == 13 || unsigned in 32..126 || unsigned >= 0x80
    }

    private fun hasTextEncodingBom(bytes: ByteArray): Boolean =
        bytes.startsWith(byteArrayOf(0xFF.toByte(), 0xFE.toByte())) ||
            bytes.startsWith(byteArrayOf(0xFE.toByte(), 0xFF.toByte())) ||
            bytes.startsWith(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
        size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

    private fun looksLikeXlsxPackage(bytes: ByteArray): Boolean = runCatching {
        var hasWorkbook = false
        var hasWorksheet = false
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory) {
                    val normalized = entry.name.removePrefix("/")
                    hasWorkbook = hasWorkbook || normalized == "xl/workbook.xml"
                    hasWorksheet = hasWorksheet || normalized.startsWith("xl/worksheets/")
                }
                zip.closeEntry()
            }
        }
        hasWorkbook && hasWorksheet
    }.getOrDefault(false)
}
