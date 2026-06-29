package com.miearn.app.importing

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler

class XlsxVocabularyReader(private val maxRows: Int = 20_000) : VocabularyRowReader {
    override fun rows(input: InputStream): Sequence<RawVocabularyRow> {
        try {
            val parts = readPackage(input)
            val sharedStrings = parts["xl/sharedStrings.xml"]
                ?.let(::parseSharedStrings)
                .orEmpty()
            for (sheetPath in orderedSheetPaths(parts)) {
                val sheetBytes = parts[sheetPath] ?: continue
                val parsed = parseSheet(sheetBytes, sharedStrings)
                if (parsed.isNotEmpty()) return parsed.asSequence()
            }
        } catch (known: VocabularyImportException) {
            throw known
        } catch (error: Exception) {
            throw CorruptVocabularyFileException(error)
        }
        throw EmptyVocabularyFileException()
    }

    private fun readPackage(input: InputStream): Map<String, ByteArray> {
        val parts = linkedMapOf<String, ByteArray>()
        var inflatedBytes = 0L
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory && isRequiredPart(entry.name)) {
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = zip.read(buffer)
                        if (read < 0) break
                        inflatedBytes += read
                        if (inflatedBytes > MAX_INFLATED_BYTES) {
                            throw VocabularyImportException("XLSX 解压后内容过大")
                        }
                        output.write(buffer, 0, read)
                    }
                    parts[entry.name.removePrefix("/")] = output.toByteArray()
                }
                zip.closeEntry()
            }
        }
        if ("xl/workbook.xml" !in parts || parts.keys.none { it.startsWith("xl/worksheets/") }) {
            throw CorruptVocabularyFileException()
        }
        return parts
    }

    private fun isRequiredPart(name: String): Boolean {
        val normalized = name.removePrefix("/")
        return normalized == "xl/workbook.xml" ||
            normalized == "xl/_rels/workbook.xml.rels" ||
            normalized == "xl/sharedStrings.xml" ||
            normalized.startsWith("xl/worksheets/")
    }

    private fun orderedSheetPaths(parts: Map<String, ByteArray>): List<String> {
        val relationshipTargets = parts["xl/_rels/workbook.xml.rels"]
            ?.let(::parseRelationships)
            .orEmpty()
        val relationshipIds = parseWorkbookSheetIds(
            parts.getValue("xl/workbook.xml"),
        )
        val ordered = relationshipIds.mapNotNull(relationshipTargets::get)
        if (ordered.isNotEmpty()) return ordered
        return parts.keys
            .filter { it.startsWith("xl/worksheets/") && it.endsWith(".xml") }
            .sorted()
    }

    private fun parseRelationships(bytes: ByteArray): Map<String, String> {
        val result = linkedMapOf<String, String>()
        parseXml(bytes, object : DefaultHandler() {
            override fun startElement(
                uri: String?,
                localName: String?,
                qName: String?,
                attributes: Attributes,
            ) {
                if (elementName(localName, qName) != "Relationship") return
                val id = attributes.getValue("Id") ?: return
                val target = attributes.getValue("Target") ?: return
                if (target.contains("worksheets/")) {
                    result[id] = normalizeWorksheetTarget(target)
                }
            }
        })
        return result
    }

    private fun parseWorkbookSheetIds(bytes: ByteArray): List<String> {
        val result = mutableListOf<String>()
        parseXml(bytes, object : DefaultHandler() {
            override fun startElement(
                uri: String?,
                localName: String?,
                qName: String?,
                attributes: Attributes,
            ) {
                if (elementName(localName, qName) != "sheet") return
                (attributes.getValue("r:id") ?: attributes.getValue("id"))
                    ?.let(result::add)
            }
        })
        return result
    }

    private fun parseSharedStrings(bytes: ByteArray): List<String> {
        val result = mutableListOf<String>()
        val item = StringBuilder()
        val text = StringBuilder()
        var inItem = false
        var inText = false
        parseXml(bytes, object : DefaultHandler() {
            override fun startElement(
                uri: String?,
                localName: String?,
                qName: String?,
                attributes: Attributes?,
            ) {
                when (elementName(localName, qName)) {
                    "si" -> {
                        inItem = true
                        item.setLength(0)
                    }
                    "t" -> if (inItem) {
                        inText = true
                        text.setLength(0)
                    }
                }
            }

            override fun characters(ch: CharArray, start: Int, length: Int) {
                if (inText) text.append(ch, start, length)
            }

            override fun endElement(uri: String?, localName: String?, qName: String?) {
                when (elementName(localName, qName)) {
                    "t" -> if (inText) {
                        item.append(text)
                        inText = false
                    }
                    "si" -> {
                        result += item.toString()
                        inItem = false
                    }
                }
            }
        })
        return result
    }

    private fun parseSheet(
        bytes: ByteArray,
        sharedStrings: List<String>,
    ): List<RawVocabularyRow> {
        val result = mutableListOf<RawVocabularyRow>()
        var rowNumber = 0
        var rowCells = mutableListOf<String>()
        var cellColumn = 0
        var nextColumn = 0
        var cellType = ""
        var inValue = false
        var inInlineText = false
        val value = StringBuilder()

        parseXml(bytes, object : DefaultHandler() {
            override fun startElement(
                uri: String?,
                localName: String?,
                qName: String?,
                attributes: Attributes,
            ) {
                when (elementName(localName, qName)) {
                    "row" -> {
                        rowNumber = attributes.getValue("r")?.toIntOrNull() ?: result.size + 1
                        rowCells = mutableListOf()
                        nextColumn = 0
                    }
                    "c" -> {
                        cellColumn = attributes.getValue("r")
                            ?.let(::columnIndex)
                            ?: nextColumn
                        cellType = attributes.getValue("t").orEmpty()
                        value.setLength(0)
                    }
                    "v" -> {
                        inValue = true
                        value.setLength(0)
                    }
                    "t" -> if (cellType == "inlineStr") {
                        inInlineText = true
                        value.setLength(0)
                    }
                }
            }

            override fun characters(ch: CharArray, start: Int, length: Int) {
                if (inValue || inInlineText) value.append(ch, start, length)
            }

            override fun endElement(uri: String?, localName: String?, qName: String?) {
                when (elementName(localName, qName)) {
                    "v" -> inValue = false
                    "t" -> inInlineText = false
                    "c" -> {
                        while (rowCells.size <= cellColumn) rowCells += ""
                        rowCells[cellColumn] = when (cellType) {
                            "s" -> value.toString().toIntOrNull()
                                ?.let(sharedStrings::getOrNull)
                                .orEmpty()
                            else -> value.toString()
                        }
                        nextColumn = cellColumn + 1
                    }
                    "row" -> if (rowCells.any(String::isNotBlank)) {
                        result += RawVocabularyRow(rowNumber, rowCells.toList())
                        if (result.size > maxRows) throw ImportLimitException(maxRows)
                    }
                }
            }
        })
        return result
    }

    private fun parseXml(bytes: ByteArray, handler: DefaultHandler) {
        val prefix = bytes
            .take(512)
            .toByteArray()
            .toString(Charsets.UTF_8)
            .uppercase()
        if ("<!DOCTYPE" in prefix || "<!ENTITY" in prefix) {
            throw CorruptVocabularyFileException()
        }
        val factory = SAXParserFactory.newInstance().apply {
            isNamespaceAware = true
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        }
        factory.newSAXParser().parse(ByteArrayInputStream(bytes), handler)
    }

    private fun normalizeWorksheetTarget(target: String): String {
        val normalized = target.replace('\\', '/').removePrefix("/")
        return when {
            normalized.startsWith("xl/") -> normalized
            normalized.startsWith("worksheets/") -> "xl/$normalized"
            else -> "xl/${normalized.substringAfterLast("../")}"
        }
    }

    private fun columnIndex(cellReference: String): Int {
        var result = 0
        var letters = 0
        for (character in cellReference) {
            if (!character.isLetter()) break
            result = result * 26 + (character.uppercaseChar() - 'A' + 1)
            letters++
        }
        return if (letters == 0) 0 else result - 1
    }

    private fun elementName(localName: String?, qName: String?): String =
        localName?.takeIf(String::isNotEmpty) ?: qName.orEmpty().substringAfter(':')

    companion object {
        private const val MAX_INFLATED_BYTES = 64L * 1024 * 1024
    }
}
