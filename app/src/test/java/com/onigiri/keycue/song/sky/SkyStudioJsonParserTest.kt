package com.onigiri.keycue.song.sky

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream

class SkyStudioJsonParserTest {

    private lateinit var parser: SkyStudioJsonParser

    @Before
    fun setUp() {
        parser = SkyStudioJsonParser()
    }

    @Test
    fun parse_singleNote_returnsCorrectSongData() {
        val json = """
            [
              {
                "name": "SingleNoteSong",
                "bpm": 120,
                "songNotes": [
                  {"time": 500, "key": "1Key7"}
                ]
              }
            ]
        """.trimIndent()

        val songData = parser.parse(json, "fallback.json")
        assertEquals("SingleNoteSong", songData.title)
        assertEquals(500L, songData.durationMs)
        assertEquals(1, songData.events.size)
        assertEquals(500L, songData.events[0].timeMs)
        assertEquals(7, songData.events[0].key)
    }

    @Test
    fun parse_singleObjectRoot_supported() {
        val json = """
            {
              "name": "DirectObjectSong",
              "songNotes": [
                {"time": 1000, "key": "A1"}
              ]
            }
        """.trimIndent()

        val songData = parser.parse(json, "fallback.json")
        assertEquals("DirectObjectSong", songData.title)
        assertEquals(1000L, songData.durationMs)
        assertEquals(1, songData.events.size)
        assertEquals(0, songData.events[0].key) // A1 -> 0
    }

    @Test
    fun parse_chords_preservesAllNoteEvents() {
        // 同一時刻 2000ms に 3つのキー (和音)
        val json = """
            [
              {
                "name": "ChordSong",
                "songNotes": [
                  {"time": 2000, "key": "1Key0"},
                  {"time": 2000, "key": "1Key4"},
                  {"time": 2000, "key": "1Key7"}
                ]
              }
            ]
        """.trimIndent()

        val songData = parser.parse(json, "fallback.json")
        assertEquals(3, songData.events.size)
        assertEquals(2000L, songData.events[0].timeMs)
        assertEquals(0, songData.events[0].key)
        assertEquals(2000L, songData.events[1].timeMs)
        assertEquals(4, songData.events[1].key)
        assertEquals(2000L, songData.events[2].timeMs)
        assertEquals(7, songData.events[2].key)
    }

    @Test
    fun parse_multiTrackAndDeduplication_mergesAndDeduplicates() {
        // 異なるトラックのノート、および同一キー・同一時刻の重複ノート
        val json = """
            [
              {
                "name": "MultiTrackSong",
                "songNotes": [
                  {"time": 1000, "key": "1Key7"},
                  {"time": 1000, "key": "2Key7"},
                  {"time": 1000, "key": "2Key9"},
                  {"time": 1500, "key": "1Key11"}
                ]
              }
            ]
        """.trimIndent()

        val songData = parser.parse(json, "fallback.json")
        // 重複した 2Key7 は除外され、合計3ノートになる
        assertEquals(3, songData.events.size)
        assertEquals(1000L, songData.events[0].timeMs)
        assertEquals(7, songData.events[0].key)
        assertEquals(1000L, songData.events[1].timeMs)
        assertEquals(9, songData.events[1].key)
        assertEquals(1500L, songData.events[2].timeMs)
        assertEquals(11, songData.events[2].key)
    }

    @Test
    fun parse_timeCalculationAndDuration_correct() {
        val json = """
            [
              {
                "name": "TimeTest",
                "songNotes": [
                  {"time": 600, "key": "1Key7"},
                  {"time": 1200, "key": "1Key8"},
                  {"time": 3600, "key": "1Key9"}
                ]
              }
            ]
        """.trimIndent()

        val songData = parser.parse(json, "fallback.json")
        assertEquals(3600L, songData.durationMs)
        assertEquals(3, songData.events.size)
        assertEquals(600L, songData.events[0].timeMs)
        assertEquals(1200L, songData.events[1].timeMs)
        assertEquals(3600L, songData.events[2].timeMs)
    }

    @Test
    fun parse_missingSongName_usesDefaultTitle() {
        val json = """
            [
              {
                "name": "",
                "songNotes": [
                  {"time": 500, "key": "1Key7"}
                ]
              }
            ]
        """.trimIndent()

        val songData = parser.parse(json, "DefaultName.json")
        assertEquals("DefaultName.json", songData.title)
    }

    @Test
    fun parse_outOfBoundsOrUnknownKeys_areSkipped() {
        val json = """
            [
              {
                "name": "InvalidKeys",
                "songNotes": [
                  {"time": 500, "key": "1Key7"},
                  {"time": 600, "key": "1Key99"},
                  {"time": 700, "key": "unknown"},
                  {"time": 800, "key": "1Key8"}
                ]
              }
            ]
        """.trimIndent()

        val songData = parser.parse(json, "fallback.json")
        assertEquals(2, songData.events.size)
        assertEquals(7, songData.events[0].key)
        assertEquals(8, songData.events[1].key)
    }

    @Test
    fun parse_encryptedJson_throwsEncryptedSkyStudioException() {
        val json = """
            [
              {
                "name": "EncryptedSong",
                "isEncrypted": true,
                "songNotes": []
              }
            ]
        """.trimIndent()

        val ex = assertThrows(EncryptedSkyStudioException::class.java) {
            parser.parse(json, "fallback.json")
        }
        assertTrue(ex.message!!.contains("暗号化されているため読み込めません"))
    }

    @Test
    fun parse_notSkyStudioJson_throwsInvalidSkyStudioJsonException() {
        // songNotes が存在しない通常のJSON
        val json = """
            {
              "version": 1,
              "settings": {
                "darkMode": true
              }
            }
        """.trimIndent()

        assertThrows(InvalidSkyStudioJsonException::class.java) {
            parser.parse(json, "fallback.json")
        }
    }

    @Test
    fun parse_malformedJson_throwsInvalidSkyStudioJsonException() {
        val brokenJson = "{ invalid json content ... "

        assertThrows(InvalidSkyStudioJsonException::class.java) {
            parser.parse(brokenJson, "fallback.json")
        }
    }

    @Test
    fun parse_realFixtureTwinkleStar_succeeds() {
        val fixture = """
            [{"name":"Twinkle_twinkle_little_star9","author":"Unknown","transcribedBy":"Unknown","isComposed":true,"bpm":200,"bitsPerPage":16,"pitchLevel":0,"isEncrypted":false,"songNotes":[{"time":600,"key":"1Key7"},{"time":1200,"key":"1Key7"},{"time":1500,"key":"1Key11"},{"time":2100,"key":"1Key11"},{"time":2400,"key":"1Key12"},{"time":3000,"key":"1Key12"},{"time":3300,"key":"1Key11"},{"time":4200,"key":"1Key10"},{"time":4800,"key":"1Key10"},{"time":5100,"key":"1Key9"},{"time":5700,"key":"1Key9"},{"time":6300,"key":"1Key8"},{"time":6600,"key":"1Key8"},{"time":6900,"key":"1Key7"}]}]
        """.trimIndent()

        val songData = parser.parse(ByteArrayInputStream(fixture.toByteArray(Charsets.UTF_8)), "twinkle.json")
        assertEquals("Twinkle_twinkle_little_star9", songData.title)
        assertEquals(6900L, songData.durationMs)
        assertEquals(14, songData.events.size)
        // ド ド ソ ソ ラ ラ ソ
        assertEquals(7, songData.events[0].key)
        assertEquals(7, songData.events[1].key)
        assertEquals(11, songData.events[2].key)
        assertEquals(11, songData.events[3].key)
        assertEquals(12, songData.events[4].key)
        assertEquals(12, songData.events[5].key)
        assertEquals(11, songData.events[6].key)
    }

    @Test
    fun parse_realFixtureCanon_succeeds() {
        val fixture = """
            [{"name":"Example_Canon.C","author":"","arrangedBy":"","transcribedBy":"","permission":"","bpm":320,"bitsPerPage":16,"pitchLevel":0,"songNotes":[{"time":2992,"key":"2Key7"},{"time":2992,"key":"2Key9"},{"time":2992,"key":"1Key11"},{"time":3366,"key":"1Key9"},{"time":28424,"key":"1Key7"},{"time":28424,"key":"1Key9"},{"time":28424,"key":"1Key11"},{"time":28424,"key":"1Key14"}]}]
        """.trimIndent()

        val songData = parser.parse(fixture, "canon.json")
        assertEquals("Example_Canon.C", songData.title)
        assertEquals(28424L, songData.durationMs)
        assertEquals(8, songData.events.size)

        // 最初の和音 (time=2992: keys 7, 9, 11)
        assertEquals(2992L, songData.events[0].timeMs)
        assertEquals(7, songData.events[0].key)
        assertEquals(2992L, songData.events[1].timeMs)
        assertEquals(9, songData.events[1].key)
        assertEquals(2992L, songData.events[2].timeMs)
        assertEquals(11, songData.events[2].key)

        // 最後の4和音 (time=28424: keys 7, 9, 11, 14)
        val last4 = songData.events.takeLast(4)
        assertEquals(listOf(28424L, 28424L, 28424L, 28424L), last4.map { it.timeMs })
        assertEquals(listOf(7, 9, 11, 14), last4.map { it.key })
    }

    @Test
    fun parse_utf16LeWithBom_succeeds() {
        val json = """[{"name":"Utf16Song","songNotes":[{"time":500,"key":"1Key7"}]}]"""
        val rawBytes = json.toByteArray(java.nio.charset.StandardCharsets.UTF_16LE)
        val bomBytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + rawBytes

        val songData = parser.parse(ByteArrayInputStream(bomBytes), "utf16.json")
        assertEquals("Utf16Song", songData.title)
        assertEquals(1, songData.events.size)
        assertEquals(7, songData.events[0].key)
    }

    @Test
    fun parse_utf8WithBom_succeeds() {
        val json = """[{"name":"Utf8BomSong","songNotes":[{"time":300,"key":"A1"}]}]"""
        val rawBytes = json.toByteArray(Charsets.UTF_8)
        val bomBytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + rawBytes

        val songData = parser.parse(ByteArrayInputStream(bomBytes), "utf8bom.json")
        assertEquals("Utf8BomSong", songData.title)
        assertEquals(1, songData.events.size)
        assertEquals(0, songData.events[0].key)
    }
}
