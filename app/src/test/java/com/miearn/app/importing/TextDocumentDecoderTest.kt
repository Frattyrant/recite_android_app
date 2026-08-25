package com.miearn.app.importing

import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Test

class TextDocumentDecoderTest {
    @Test
    fun decodesUtf16LittleEndianBomWithoutLeavingBomInText() {
        val payload = "word\t中文\r\nfixture\t夹具".toByteArray(StandardCharsets.UTF_16LE)
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + payload

        val decoded = TextDocumentDecoder.decode(bytes)

        assertEquals("word\t中文\r\nfixture\t夹具", decoded.text)
        assertEquals(StandardCharsets.UTF_16LE, decoded.charset)
        assertEquals(true, decoded.hadBom)
    }

    @Test
    fun decodesUtf16BigEndianBom() {
        val payload = "fixture\t夹具".toByteArray(StandardCharsets.UTF_16BE)
        val bytes = byteArrayOf(0xFE.toByte(), 0xFF.toByte()) + payload

        assertEquals("fixture\t夹具", TextDocumentDecoder.decode(bytes).text)
    }

    @Test
    fun decodesUtf16LittleEndianWithoutBom() {
        val bytes = "fixture\t夹具".toByteArray(StandardCharsets.UTF_16LE)

        val decoded = TextDocumentDecoder.decode(bytes)

        assertEquals("fixture\t夹具", decoded.text)
        assertEquals(StandardCharsets.UTF_16LE, decoded.charset)
        assertEquals(false, decoded.hadBom)
    }

    @Test
    fun decodesUtf16BigEndianWithoutBom() {
        val bytes = "fixture\t夹具".toByteArray(StandardCharsets.UTF_16BE)

        val decoded = TextDocumentDecoder.decode(bytes)

        assertEquals("fixture\t夹具", decoded.text)
        assertEquals(StandardCharsets.UTF_16BE, decoded.charset)
        assertEquals(false, decoded.hadBom)
    }
}
