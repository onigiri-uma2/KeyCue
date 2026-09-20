package com.onigiri.keycue.song

import com.onigiri.keycue.model.SongFormat
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class SongFormatDetectorTest {

    private lateinit var detector: SongFormatDetector

    @Before
    fun setUp() {
        detector = SongFormatDetector()
    }

    @Test
    fun detect_byExtension_returnsMidi() {
        // 小文字
        assertEquals(SongFormat.MIDI, detector.detect("canon.mid", null))
        assertEquals(SongFormat.MIDI, detector.detect("test_song.midi", null))

        // 大文字
        assertEquals(SongFormat.MIDI, detector.detect("CANON.MID", null))
        assertEquals(SongFormat.MIDI, detector.detect("SONG.MIDI", null))

        // パスや複数ドット
        assertEquals(SongFormat.MIDI, detector.detect("my.song.v1.mid", null))
    }

    @Test
    fun detect_byMimeType_returnsMidi() {
        // 拡張子なしまたは不明でもMIME typeから判別
        assertEquals(SongFormat.MIDI, detector.detect("unknown_file", "audio/midi"))
        assertEquals(SongFormat.MIDI, detector.detect("song", "audio/x-midi"))
        assertEquals(SongFormat.MIDI, detector.detect(null, "audio/sp-midi"))
        assertEquals(SongFormat.MIDI, detector.detect(null, "application/x-midi"))
    }

    @Test
    fun detect_byExtensionWithGenericMimeType_returnsMidi() {
        // Androidで一般的な application/octet-stream や */* の場合
        assertEquals(SongFormat.MIDI, detector.detect("canon.mid", "application/octet-stream"))
        assertEquals(SongFormat.MIDI, detector.detect("canon.mid", "*/*"))
    }

    @Test
    fun detect_skyStudioJson_byExtensionAndMime() {
        assertEquals(SongFormat.SKY_STUDIO_JSON, detector.detect("canon.json", null))
        assertEquals(SongFormat.SKY_STUDIO_JSON, detector.detect("SONG.JSON", null))
        assertEquals(SongFormat.SKY_STUDIO_JSON, detector.detect("unknown", "application/json"))
        assertEquals(SongFormat.SKY_STUDIO_JSON, detector.detect(null, "text/json"))
    }

    @Test
    fun detect_skyStudioJson_withContentPreview() {
        val skyJsonPreview = """[{"name":"Song","songNotes":[{"time":100,"key":"1Key0"}]}]"""
        assertEquals(SongFormat.SKY_STUDIO_JSON, detector.detect("song.json", "application/json", skyJsonPreview))
        // 拡張子不明でもプレビュー内容でSky Studioと判定
        assertEquals(SongFormat.SKY_STUDIO_JSON, detector.detect("file", null, skyJsonPreview))

        // 無関係な通常のJSONプレビュー -> UNKNOWN
        val unrelatedJson = """{"setting": true, "theme": "dark"}"""
        assertEquals(SongFormat.UNKNOWN, detector.detect("config.json", "application/json", unrelatedJson))

        // .txt で Sky Studio JSON 内容がある場合 -> SKY_STUDIO_JSON
        assertEquals(SongFormat.SKY_STUDIO_JSON, detector.detect("sheet.txt", "text/plain", skyJsonPreview))

        // .txt で通常テキストの場合 -> UNKNOWN
        assertEquals(SongFormat.UNKNOWN, detector.detect("memo.txt", "text/plain", "通常のテキストメモ"))
    }

    @Test
    fun isSkyStudioJson_checksCorrectly() {
        // 1. 通常のsongNotesキー
        org.junit.Assert.assertTrue(detector.isSkyStudioJson("""{"songNotes": []}"""))

        // 2. 空白を含むキー
        org.junit.Assert.assertTrue(detector.isSkyStudioJson("""{"songNotes"   : []}"""))

        // 配列形式およびBOM付き
        org.junit.Assert.assertTrue(detector.isSkyStudioJson("""[{"songNotes": []}]"""))
        org.junit.Assert.assertTrue(detector.isSkyStudioJson("\uFEFF" + """[{"songNotes": []}]"""))

        // 3. 文字列値としてsongNotesを含む
        org.junit.Assert.assertFalse(detector.isSkyStudioJson("""{"description": "songNotes"}"""))

        // 4. 今回の重要な回帰テスト: 文字列値の中に "songNotes": が含まれる場合
        org.junit.Assert.assertFalse(detector.isSkyStudioJson("""{"description": "example: \"songNotes\": []"}"""))

        // 5. 類似キー
        org.junit.Assert.assertFalse(detector.isSkyStudioJson("""{"songNotesExample": []}"""))

        // 6. songNotesキーを持つ壊れたJSON (構文途切れでもSky Studio候補としてはtrue)
        org.junit.Assert.assertTrue(detector.isSkyStudioJson("[\n  {\n    \"songNotes\": [\n"))

        // 無関係なJSON（songNotesなし）
        org.junit.Assert.assertFalse(detector.isSkyStudioJson("""{"settings": {}}"""))

        // JSON形式ではないプレーンテキストに "songNotes": が含まれるケース
        org.junit.Assert.assertFalse(detector.isSkyStudioJson("""Notes header: "songNotes": []"""))

        // 空文字
        org.junit.Assert.assertFalse(detector.isSkyStudioJson(""))
    }

    @Test
    fun detect_skyStudioJson_withLargePadding_detectsCorrectly() {
        // 2048バイト超（約3000バイトのダミーnotes配列が先行）
        val padding2048 = (1..300).joinToString(",") { "[$it, 100, \"1\"]" }
        val contentOver2048 = """[{"name":"LargeSong","notes":[$padding2048],"songNotes":[{"time":100,"key":"1Key0"}]}]"""
        org.junit.Assert.assertTrue(contentOver2048.indexOf("\"songNotes\"") > 2048)
        assertEquals(SongFormat.SKY_STUDIO_JSON, detector.detect("song.txt", "text/plain", contentOver2048))
        assertEquals(SongFormat.SKY_STUDIO_JSON, detector.detect("song.json", "application/json", contentOver2048))

        // 8192バイト超（約10000バイトのダミーnotes配列が先行）
        val padding8192 = (1..1000).joinToString(",") { "[$it, 100, \"1\"]" }
        val contentOver8192 = """[{"name":"ExtraLargeSong","notes":[$padding8192],"songNotes":[{"time":100,"key":"1Key0"}]}]"""
        org.junit.Assert.assertTrue(contentOver8192.indexOf("\"songNotes\"") > 8192)
        assertEquals(SongFormat.SKY_STUDIO_JSON, detector.detect("song.txt", "text/plain", contentOver8192))
        assertEquals(SongFormat.SKY_STUDIO_JSON, detector.detect("song.json", "application/json", contentOver8192))
    }

    @Test
    fun detect_unknownFormat_returnsUnknown() {
        assertEquals(SongFormat.UNKNOWN, detector.detect("song.mp3", "audio/mpeg"))
        assertEquals(SongFormat.UNKNOWN, detector.detect("document.pdf", "application/pdf"))
        assertEquals(SongFormat.UNKNOWN, detector.detect("text.txt", "text/plain"))
        assertEquals(SongFormat.UNKNOWN, detector.detect(null, null))
        assertEquals(SongFormat.UNKNOWN, detector.detect("", ""))
    }
}
