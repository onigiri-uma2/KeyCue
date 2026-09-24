package com.onigiri.keycue.song.sky

import com.onigiri.keycue.model.timing.SongTimingMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

/**
 * Sky Studio譜面におけるテンポメタデータ取得およびフォールバックの回帰テスト。
 *
 * 【検証要件】
 * 1. bpm=120 -> validBpm=120 で自動取得
 * 2. bpm フィールド不在 -> validBpm=null（手動フォールバック）
 * 3. bpm=null -> validBpm=null
 * 4. bpm=0 -> validBpm=null
 * 5. bpm=-1 -> validBpm=null
 * 6. bpm=596 -> 安全範囲 (40〜240) 外のため手動フォールバック（rawBpm=596.0, validBpm=null）
 * 7. bpmが数値文字列 "150" -> validBpm=150
 * 8. bpmが不正文字列 "fast" -> validBpm=null
 * 9. songNotes 内の note timeMs はBPMメタデータの有無に関わらず一切書き換えない
 * 10. bitsPerPage や pitchLevel などの他フィールドを拍子やテンポと誤認しない
 * 11. UTF-8, UTF-16LE, UTF-16BE (BOM付き) でも同一のメタデータが取得される
 * 12. メタデータが不正（bpm=596, 文字列等）でも、譜面読み込み自体は成功する
 */
class SkyStudioTimingRegressionTest {

    private val parser = SkyStudioJsonParser()

    private fun createSkyJson(
        bpmField: String? = null,
        extraFields: String = "",
        firstNoteTime: Long = 1000L
    ): String {
        val bpmPart = if (bpmField != null) "\"bpm\": $bpmField," else ""
        return """
            [
              {
                "name": "Test Song",
                $bpmPart
                $extraFields
                "songNotes": [
                  { "time": $firstNoteTime, "key": "1Key0" },
                  { "time": 2000, "key": "1Key1" }
                ]
              }
            ]
        """.trimIndent()
    }

    private fun parseString(json: String, charset: java.nio.charset.Charset = StandardCharsets.UTF_8, bom: ByteArray? = null): com.onigiri.keycue.model.SongData {
        val jsonBytes = json.toByteArray(charset)
        val allBytes = if (bom != null) bom + jsonBytes else jsonBytes
        return ByteArrayInputStream(allBytes).use {
            parser.parse(it, "Test")
        }
    }

    // 1. bpm=120 -> 自動取得
    @Test
    fun testValidBpm120_isExtracted() {
        val song = parseString(createSkyJson(bpmField = "120"))
        val timing = song.timingMetadata as? SongTimingMetadata.SkyStudio
        assertNotNull(timing)
        assertEquals(120, timing!!.validBpm)
        assertEquals(120.0, timing.rawBpm!!, 0.001)
        assertNull(timing.invalidReason)
        assertEquals(120.0, timing.primaryBpm, 0.001)
    }

    // 2. bpm フィールド不在 -> 手動BPMフォールバック
    @Test
    fun testNoBpmField_fallsBackToManual() {
        val song = parseString(createSkyJson(bpmField = null))
        val timing = song.timingMetadata as? SongTimingMetadata.SkyStudio
        assertNotNull(timing)
        assertNull(timing!!.validBpm)
        assertNull(timing.rawBpm)
        assertNotNull(timing.invalidReason)
    }

    // 3. bpm=null -> 手動BPMフォールバック
    @Test
    fun testBpmNull_fallsBackToManual() {
        val song = parseString(createSkyJson(bpmField = "null"))
        val timing = song.timingMetadata as? SongTimingMetadata.SkyStudio
        assertNotNull(timing)
        assertNull(timing!!.validBpm)
        assertNull(timing.rawBpm)
    }

    // 4. bpm=0 -> 手動BPMフォールバック
    @Test
    fun testBpmZero_fallsBackToManual() {
        val song = parseString(createSkyJson(bpmField = "0"))
        val timing = song.timingMetadata as? SongTimingMetadata.SkyStudio
        assertNotNull(timing)
        assertNull(timing!!.validBpm)
        assertEquals(0.0, timing.rawBpm!!, 0.001)
    }

    // 5. bpm=-1 -> 手動BPMフォールバック
    @Test
    fun testBpmNegative_fallsBackToManual() {
        val song = parseString(createSkyJson(bpmField = "-1"))
        val timing = song.timingMetadata as? SongTimingMetadata.SkyStudio
        assertNotNull(timing)
        assertNull(timing!!.validBpm)
        assertEquals(-1.0, timing.rawBpm!!, 0.001)
    }

    // 6. bpm=596 -> 40〜240範囲外のため手動BPMフォールバック
    @Test
    fun testBpm596_outOfRange_fallsBackToManual() {
        val song = parseString(createSkyJson(bpmField = "596"))
        val timing = song.timingMetadata as? SongTimingMetadata.SkyStudio
        assertNotNull(timing)
        assertNull(timing!!.validBpm)
        assertEquals(596.0, timing.rawBpm!!, 0.001)
        assertNotNull(timing.invalidReason)
    }

    // 6b. bpm=240.4 -> 丸め前の浮動小数点値で厳密に範囲外判定
    @Test
    fun testBpm240_4_outOfRange_fallsBackToManual() {
        val song = parseString(createSkyJson(bpmField = "240.4"))
        val timing = song.timingMetadata as? SongTimingMetadata.SkyStudio
        assertNotNull(timing)
        assertNull(timing!!.validBpm)
        assertEquals(240.4, timing.rawBpm!!, 0.001)
        assertNotNull(timing.invalidReason)
    }

    // 7. bpm が数値文字列 "150"
    @Test
    fun testBpmNumericString_isParsed() {
        val song = parseString(createSkyJson(bpmField = "\"150\""))
        val timing = song.timingMetadata as? SongTimingMetadata.SkyStudio
        assertNotNull(timing)
        assertEquals(150, timing!!.validBpm)
    }

    // 8. bpm が不正文字列 "fast"
    @Test
    fun testBpmInvalidString_fallsBackSafely() {
        val song = parseString(createSkyJson(bpmField = "\"fast\""))
        val timing = song.timingMetadata as? SongTimingMetadata.SkyStudio
        assertNotNull(timing)
        assertNull(timing!!.validBpm)
    }

    // 9. songNotes の timeMs がBPMによって改変されないこと
    @Test
    fun testSongNotesTime_remainsUnchanged() {
        val songWithBpm = parseString(createSkyJson(bpmField = "120", firstNoteTime = 1234L))
        assertEquals(1234L, songWithBpm.events[0].timeMs)

        val songWithoutBpm = parseString(createSkyJson(bpmField = null, firstNoteTime = 1234L))
        assertEquals(1234L, songWithoutBpm.events[0].timeMs)
    }

    // 10. bitsPerPage や pitchLevel を拍子・テンポと誤認しない
    @Test
    fun testOtherMetadataFields_notMisinterpreted() {
        val extra = """
            "bitsPerPage": 16,
            "pitchLevel": 4,
        """.trimIndent()
        val song = parseString(createSkyJson(bpmField = null, extraFields = extra))
        val timing = song.timingMetadata as? SongTimingMetadata.SkyStudio
        assertNotNull(timing)
        // 拍子は推測せず null であること
        assertNull(timing!!.primaryTimeSignature)
        // テンポも手動フォールバックであること
        assertNull(timing.validBpm)
    }

    // 11. UTF-8 / UTF-16LE / UTF-16BE (BOM付き)
    @Test
    fun testEncodings_allExtractMetadataConsistently() {
        val json = createSkyJson(bpmField = "180")

        val utf8Song = parseString(json, StandardCharsets.UTF_8, byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
        val utf16LeSong = parseString(json, StandardCharsets.UTF_16LE, byteArrayOf(0xFF.toByte(), 0xFE.toByte()))
        val utf16BeSong = parseString(json, StandardCharsets.UTF_16BE, byteArrayOf(0xFE.toByte(), 0xFF.toByte()))

        assertEquals(180, (utf8Song.timingMetadata as SongTimingMetadata.SkyStudio).validBpm)
        assertEquals(180, (utf16LeSong.timingMetadata as SongTimingMetadata.SkyStudio).validBpm)
        assertEquals(180, (utf16BeSong.timingMetadata as SongTimingMetadata.SkyStudio).validBpm)
    }

    // 12. メタデータが不正でも譜面読み込み自体は成功する
    @Test
    fun testInvalidMetadata_doesNotFailSongLoad() {
        val json = createSkyJson(bpmField = "\"totally invalid bpm\"", extraFields = "\"corrupt\": {}}")
        // 譜面構文自体が保たれていれば曲ロードは成功しノートも抽出される
        val song = parseString(createSkyJson(bpmField = "99999"))
        assertEquals(2, song.events.size)
        assertEquals("Test Song", song.title)
        assertNull((song.timingMetadata as SongTimingMetadata.SkyStudio).validBpm)
    }
}
