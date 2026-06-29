package com.miearn.app.importing

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

class CsvVocabularyReader(private val maxRows: Int = 20_000) : VocabularyRowReader {
    override fun rows(input: InputStream): Sequence<RawVocabularyRow> =
        parse(decode(input.readBytes())).asSequence()

    private fun decode(bytes: ByteArray): String {
        if (
            bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() &&
            bytes[2] == 0xBF.toByte()
        ) {
            return String(bytes, 3, bytes.size - 3, StandardCharsets.UTF_8)
        }
        return try {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (_: CharacterCodingException) {
            String(bytes, Charset.forName("GB18030"))
        }
    }

    private fun parse(text: String): List<RawVocabularyRow> {
        val rows = mutableListOf<RawVocabularyRow>()
        val cells = mutableListOf<String>()
        val cell = StringBuilder()
        var inQuotes = false
        var index = 0
        var logicalRow = 1

        fun finishCell() {
            cells += cell.toString()
            cell.setLength(0)
        }

        fun finishRow() {
            finishCell()
            if (cells.any { it.isNotBlank() }) {
                if (rows.size >= maxRows) throw ImportLimitException(maxRows)
                rows += RawVocabularyRow(logicalRow, cells.toList())
            }
            cells.clear()
            logicalRow++
        }

        while (index < text.length) {
            val ch = text[index]
            if (inQuotes) {
                when {
                    ch == '"' && index + 1 < text.length && text[index + 1] == '"' -> {
                        cell.append('"')
                        index += 2
                    }
                    ch == '"' -> {
                        inQuotes = false
                        index++
                    }
                    else -> {
                        cell.append(ch)
                        index++
                    }
                }
            } else {
                when (ch) {
                    '"' -> {
                        inQuotes = true
                        index++
                    }
                    ',' -> {
                        finishCell()
                        index++
                    }
                    '\r' -> {
                        finishRow()
                        index++
                        if (index < text.length && text[index] == '\n') index++
                    }
                    '\n' -> {
                        finishRow()
                        index++
                    }
                    else -> {
                        cell.append(ch)
                        index++
                    }
                }
            }
        }
        if (inQuotes) throw CorruptVocabularyFileException()
        if (cell.isNotEmpty() || cells.isNotEmpty()) finishRow()
        if (rows.isEmpty()) throw EmptyVocabularyFileException()
        return rows
    }
}
