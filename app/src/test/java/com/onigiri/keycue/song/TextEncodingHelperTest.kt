package com.onigiri.keycue.song

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

class TextEncodingHelperTest {

    @Test
    fun decodeText_utf8_decodesCorrectly() {
        val original = """{"name":"Test"}"""
        val bytes = original.toByteArray(Charsets.UTF_8)
        assertEquals(original, TextEncodingHelper.decodeText(bytes))
    }

    @Test
    fun decodeText_utf8WithBom_removesBomAndDecodes() {
        val original = """{"name":"Test"}"""
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + original.toByteArray(Charsets.UTF_8)
        assertEquals(original, TextEncodingHelper.decodeText(bytes))
    }

    @Test
    fun decodeText_utf16LeWithBom_removesBomAndDecodes() {
        val original = """{"name":"Test"}"""
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + original.toByteArray(StandardCharsets.UTF_16LE)
        assertEquals(original, TextEncodingHelper.decodeText(bytes))
    }

    @Test
    fun decodeText_utf16BeWithBom_removesBomAndDecodes() {
        val original = """{"name":"Test"}"""
        val bytes = byteArrayOf(0xFE.toByte(), 0xFF.toByte()) + original.toByteArray(StandardCharsets.UTF_16BE)
        assertEquals(original, TextEncodingHelper.decodeText(bytes))
    }

    @Test
    fun decodeText_utf16LeWithoutBom_autoDetectsAndDecodes() {
        val original = """[{"name":"Twinkle"}]"""
        val bytes = original.toByteArray(StandardCharsets.UTF_16LE)
        assertEquals(original, TextEncodingHelper.decodeText(bytes))
    }

    @Test
    fun decodePreview_utf16Le_preservesCorrectCharacters() {
        val original = """[{"name":"Song","songNotes":[{"time":100,"key":"1Key0"}]}]"""
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + original.toByteArray(StandardCharsets.UTF_16LE)
        val preview = TextEncodingHelper.decodePreview(bytes, 64)
        assertTrue(preview.startsWith("""[{"name":"Song""""))
    }
}
