package com.onigiri.keycue.song

import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

/**
 * ファイルのバイト列から文字エンコーディング（UTF-8, UTF-16LE, UTF-16BE, BOMの有無）を自動判定してデコードするヘルパー。
 */
object TextEncodingHelper {

    /**
     * バイト配列からBOMおよび文字エンコーディングを自動検出して文字列へデコードする。
     */
    fun decodeText(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""

        // 1. UTF-8 BOM (EF BB BF)
        if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() &&
            bytes[2] == 0xBF.toByte()
        ) {
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8).removePrefix("\uFEFF")
        }

        // 2. UTF-16LE BOM (FF FE)
        if (bytes.size >= 2 &&
            bytes[0] == 0xFF.toByte() &&
            bytes[1] == 0xFE.toByte()
        ) {
            return String(bytes, 2, bytes.size - 2, StandardCharsets.UTF_16LE).removePrefix("\uFEFF")
        }

        // 3. UTF-16BE BOM (FE FF)
        if (bytes.size >= 2 &&
            bytes[0] == 0xFE.toByte() &&
            bytes[1] == 0xFF.toByte()
        ) {
            return String(bytes, 2, bytes.size - 2, StandardCharsets.UTF_16BE).removePrefix("\uFEFF")
        }

        // 4. BOMなし UTF-16LE の推定 (2バイト目と4バイト目が0x00など)
        if (bytes.size >= 4 && bytes[1] == 0x00.toByte() && bytes[3] == 0x00.toByte()) {
            return String(bytes, 0, bytes.size, StandardCharsets.UTF_16LE).removePrefix("\uFEFF")
        }

        // 5. BOMなし UTF-16BE の推定 (1バイト目と3バイト目が0x00など)
        if (bytes.size >= 4 && bytes[0] == 0x00.toByte() && bytes[2] == 0x00.toByte()) {
            return String(bytes, 0, bytes.size, StandardCharsets.UTF_16BE).removePrefix("\uFEFF")
        }

        // 6. デフォルト UTF-8
        return String(bytes, 0, bytes.size, Charsets.UTF_8).removePrefix("\uFEFF")
    }

    /**
     * バイト配列の先頭部分からプレビュー文字列を取得する。
     * UTF-16の場合は2バイト境界を維持する。
     */
    fun decodePreview(bytes: ByteArray, maxBytes: Int = 2048): String {
        if (bytes.isEmpty()) return ""
        val length = minOf(bytes.size, maxBytes)
        // UTF-16LE/BEの場合、奇数バイト長で切れると不正文字になるため偶数に揃える
        val adjustedLength = if (isLikelyUtf16(bytes) && (length % 2 != 0)) {
            length - 1
        } else {
            length
        }
        val slice = bytes.copyOfRange(0, adjustedLength)
        return decodeText(slice)
    }

    private fun isLikelyUtf16(bytes: ByteArray): Boolean {
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) return true
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) return true
        if (bytes.size >= 4 && bytes[1] == 0x00.toByte() && bytes[3] == 0x00.toByte()) return true
        if (bytes.size >= 4 && bytes[0] == 0x00.toByte() && bytes[2] == 0x00.toByte()) return true
        return false
    }
}
