package com.miearn.app.importing

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

data class DecodedText(
    val text: String,
    val charset: Charset,
    val hadBom: Boolean,
)

object TextDocumentDecoder {
    fun decode(bytes: ByteArray): DecodedText {
        when {
            bytes.startsWith(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())) ->
                return DecodedText(
                    text = decodeStrict(bytes, 3, StandardCharsets.UTF_8),
                    charset = StandardCharsets.UTF_8,
                    hadBom = true,
                )
            bytes.startsWith(byteArrayOf(0xFF.toByte(), 0xFE.toByte())) ->
                return DecodedText(
                    text = decodeStrict(bytes, 2, StandardCharsets.UTF_16LE),
                    charset = StandardCharsets.UTF_16LE,
                    hadBom = true,
                )
            bytes.startsWith(byteArrayOf(0xFE.toByte(), 0xFF.toByte())) ->
                return DecodedText(
                    text = decodeStrict(bytes, 2, StandardCharsets.UTF_16BE),
                    charset = StandardCharsets.UTF_16BE,
                    hadBom = true,
                )
        }

        // Some Windows/Excel exports omit the UTF-16 BOM. Detect the common
        // alternating-zero layout before trying UTF-8/GB18030, otherwise the
        // file can look like an unknown binary document.
        guessUtf16Charset(bytes)?.let { charset ->
            val text = runCatching { decodeStrict(bytes, 0, charset) }
                .getOrNull()
            if (text != null && text.isPlausibleText()) {
                return DecodedText(text, charset, false)
            }
        }

        try {
            return DecodedText(decodeStrict(bytes, 0, StandardCharsets.UTF_8), StandardCharsets.UTF_8, false)
        } catch (_: CharacterCodingException) {
            return try {
                val gb18030 = Charset.forName("GB18030")
                DecodedText(decodeStrict(bytes, 0, gb18030), gb18030, false)
            } catch (_: CharacterCodingException) {
                throw UnsupportedVocabularyFileException(
                    message = "无法解码文本，请使用 UTF-8、UTF-16 或 GB18030 编码后重试",
                    code = ImportFailureCode.UNSUPPORTED_TEXT_ENCODING,
                    recoveryHint = "请在编辑器或 Excel 中选择 UTF-8 编码另存文件。",
                )
            }
        }
    }

    private fun decodeStrict(bytes: ByteArray, offset: Int, charset: Charset): String =
        charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes, offset, bytes.size - offset))
            .toString()

    internal fun looksLikeUtf16WithoutBom(bytes: ByteArray): Boolean =
        guessUtf16Charset(bytes) != null

    private fun guessUtf16Charset(bytes: ByteArray): Charset? {
        if (bytes.size < 8 || bytes.size % 2 != 0) return null
        val pairs = bytes.size / 2
        val evenZeroes = (0 until bytes.size step 2).count { bytes[it] == 0.toByte() }
        val oddZeroes = (1 until bytes.size step 2).count { bytes[it] == 0.toByte() }
        val threshold = maxOf(2, pairs / 5)
        return when {
            oddZeroes >= threshold && oddZeroes > evenZeroes * 2 -> StandardCharsets.UTF_16LE
            evenZeroes >= threshold && evenZeroes > oddZeroes * 2 -> StandardCharsets.UTF_16BE
            else -> null
        }
    }

    private fun String.isPlausibleText(): Boolean =
        isNotEmpty() && none { character ->
            character == '\u0000' ||
                (Character.isISOControl(character) && character !in "\r\n\t")
        }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
        size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }
}
