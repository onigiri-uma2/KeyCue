package com.onigiri.keycue.song

import android.net.FakeUri
import com.onigiri.keycue.model.SongFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream

class SongLoaderTest {

    private lateinit var songLoader: SongLoader

    @Before
    fun setUp() {
        songLoader = SongLoader()
    }

    // -------------------------------------------------------------
    // ケース1: songNotes が2048バイト以内（通常のSky Studio JSON）
    // -------------------------------------------------------------
    @Test
    fun loadSong_skyStudioJson_within2048Bytes_succeeds() {
        val json = """
            [
              {
                "name": "NormalSong",
                "songNotes": [
                  {"time": 500, "key": "1Key7"}
                ]
              }
            ]
        """.trimIndent()
        val uri = FakeUri("content://keycue/NormalSong.json")
        val bytes = json.toByteArray(Charsets.UTF_8)

        val result = songLoader.loadFromBytes(
            bytes = bytes,
            displayName = "NormalSong.json",
            mimeType = "application/json",
            uri = uri
        )

        assertTrue("Expected Success but got $result", result is SongLoadResult.Success)
        val success = result as SongLoadResult.Success
        assertEquals(SongFormat.SKY_STUDIO_JSON, success.metadata.format)
        assertEquals("NormalSong", success.songData.title)
        assertEquals(1, success.songData.events.size)
        assertEquals(500L, success.songData.events[0].timeMs)
        assertEquals(7, success.songData.events[0].key)
    }

    // -------------------------------------------------------------
    // ケース2: songNotes が2048バイトより後ろ
    // -------------------------------------------------------------
    @Test
    fun loadSong_skyStudioJson_over2048Bytes_succeeds() {
        val dummyNotes = (1..300).joinToString(",") { "[$it, 100, \"1\"]" }
        val json = """
            [
              {
                "name": "LargeSong",
                "notes": [$dummyNotes],
                "songNotes": [
                  {"time": 518, "key": "1Key7"},
                  {"time": 714, "key": "1Key8"}
                ]
              }
            ]
        """.trimIndent()
        assertTrue("songNotes position must be > 2048", json.indexOf("\"songNotes\"") > 2048)

        val uri = FakeUri("content://keycue/LargeSong.txt")
        val bytes = json.toByteArray(Charsets.UTF_8)

        val result = songLoader.loadFromBytes(
            bytes = bytes,
            displayName = "LargeSong.txt",
            mimeType = "text/plain",
            uri = uri
        )

        assertTrue("Expected Success but got $result", result is SongLoadResult.Success)
        val success = result as SongLoadResult.Success
        assertEquals(SongFormat.SKY_STUDIO_JSON, success.metadata.format)
        assertEquals("LargeSong", success.songData.title)
        assertEquals(2, success.songData.events.size)
        assertEquals(518L, success.songData.events[0].timeMs)
        assertEquals(7, success.songData.events[0].key)
        assertEquals(714L, success.songData.events[1].timeMs)
        assertEquals(8, success.songData.events[1].key)
    }

    // -------------------------------------------------------------
    // ケース3: songNotes が8192バイト以降
    // -------------------------------------------------------------
    @Test
    fun loadSong_skyStudioJson_over8192Bytes_succeeds() {
        val dummyNotes = (1..1000).joinToString(",") { "[$it, 100, \"1\"]" }
        val json = """
            [
              {
                "name": "HugeSong",
                "notes": [$dummyNotes],
                "songNotes": [
                  {"time": 1200, "key": "1Key0"}
                ]
              }
            ]
        """.trimIndent()
        assertTrue("songNotes position must be > 8192", json.indexOf("\"songNotes\"") > 8192)

        val uri = FakeUri("content://keycue/HugeSong.json")
        val bytes = json.toByteArray(Charsets.UTF_8)

        val result = songLoader.loadFromBytes(
            bytes = bytes,
            displayName = "HugeSong.json",
            mimeType = "application/json",
            uri = uri
        )

        assertTrue("Expected Success but got $result", result is SongLoadResult.Success)
        val success = result as SongLoadResult.Success
        assertEquals(SongFormat.SKY_STUDIO_JSON, success.metadata.format)
        assertEquals("HugeSong", success.songData.title)
        assertEquals(1, success.songData.events.size)
        assertEquals(1200L, success.songData.events[0].timeMs)
        assertEquals(0, success.songData.events[0].key)
    }

    // -------------------------------------------------------------
    // ケース4: .txt 拡張子の中身がSky Studio JSON
    // -------------------------------------------------------------
    @Test
    fun loadSong_txtExtension_withSkyStudioJson_succeeds() {
        val json = """
            [
              {
                "name": "TxtSheet",
                "songNotes": [
                  {"time": 300, "key": "1Key3"}
                ]
              }
            ]
        """.trimIndent()
        val uri = FakeUri("content://keycue/sheet.txt")
        val bytes = json.toByteArray(Charsets.UTF_8)

        val result = songLoader.loadFromBytes(
            bytes = bytes,
            displayName = "sheet.txt",
            mimeType = "text/plain",
            uri = uri
        )

        assertTrue("Expected Success but got $result", result is SongLoadResult.Success)
        val success = result as SongLoadResult.Success
        assertEquals(SongFormat.SKY_STUDIO_JSON, success.metadata.format)
        assertEquals("TxtSheet", success.songData.title)
    }

    // -------------------------------------------------------------
    // ケース5: .json 拡張子の中身がSky Studio JSON
    // -------------------------------------------------------------
    @Test
    fun loadSong_jsonExtension_withSkyStudioJson_succeeds() {
        val json = """
            [
              {
                "name": "JsonSheet",
                "songNotes": [
                  {"time": 400, "key": "1Key5"}
                ]
              }
            ]
        """.trimIndent()
        val uri = FakeUri("content://keycue/sheet.json")
        val bytes = json.toByteArray(Charsets.UTF_8)

        val result = songLoader.loadFromBytes(
            bytes = bytes,
            displayName = "sheet.json",
            mimeType = "application/json",
            uri = uri
        )

        assertTrue("Expected Success but got $result", result is SongLoadResult.Success)
        val success = result as SongLoadResult.Success
        assertEquals(SongFormat.SKY_STUDIO_JSON, success.metadata.format)
        assertEquals("JsonSheet", success.songData.title)
    }

    // -------------------------------------------------------------
    // ケース6: 無関係なJSON
    // -------------------------------------------------------------
    @Test
    fun loadSong_unrelatedJson_returnsUnsupportedFormat() {
        val unrelatedJson = """
            {
              "foo": "bar",
              "version": 1
            }
        """.trimIndent()
        val uri = FakeUri("content://keycue/config.json")
        val bytes = unrelatedJson.toByteArray(Charsets.UTF_8)

        val result = songLoader.loadFromBytes(
            bytes = bytes,
            displayName = "config.json",
            mimeType = "application/json",
            uri = uri
        )

        assertTrue("Expected UnsupportedFormat but got $result", result is SongLoadResult.Failure.UnsupportedFormat)
    }

    @Test
    fun loadSong_jsonMentioningSongNotesInValue_returnsUnsupportedFormat() {
        val fakeKeyJson = """
            {
              "description": "This text mentions \"songNotes\" as a value, not a key"
            }
        """.trimIndent()
        val uri = FakeUri("content://keycue/document.json")
        val bytes = fakeKeyJson.toByteArray(Charsets.UTF_8)

        val result = songLoader.loadFromBytes(
            bytes = bytes,
            displayName = "document.json",
            mimeType = "application/json",
            uri = uri
        )

        assertTrue("Expected UnsupportedFormat but got $result", result is SongLoadResult.Failure.UnsupportedFormat)
    }

    @Test
    fun loadSong_jsonMentioningSongNotesColonInValue_returnsUnsupportedFormat() {
        val fakeKeyColonJson = """
            {
              "description": "example: \"songNotes\": []"
            }
        """.trimIndent()
        val uri = FakeUri("content://keycue/fake_notes.json")
        val bytes = fakeKeyColonJson.toByteArray(Charsets.UTF_8)

        val result = songLoader.loadFromBytes(
            bytes = bytes,
            displayName = "fake_notes.json",
            mimeType = "application/json",
            uri = uri
        )

        assertTrue("Expected UnsupportedFormat but got $result", result is SongLoadResult.Failure.UnsupportedFormat)
    }

    // -------------------------------------------------------------
    // ケース7: 壊れたJSON（"songNotes": キーは存在するが構文が壊れている）
    // -------------------------------------------------------------
    @Test
    fun loadSong_malformedJsonWithSongNotesKey_returnsInvalidSkyStudioJson() {
        val brokenJson = """
            [
              {
                "songNotes": [
                  {"time": 100, "key": "1Key0"
        """.trimIndent()
        val uri = FakeUri("content://keycue/broken.json")
        val bytes = brokenJson.toByteArray(Charsets.UTF_8)

        val result = songLoader.loadFromBytes(
            bytes = bytes,
            displayName = "broken.json",
            mimeType = "application/json",
            uri = uri
        )

        assertTrue("Expected InvalidSkyStudioJson but got $result", result is SongLoadResult.Failure.InvalidSkyStudioJson)
    }

    // -------------------------------------------------------------
    // ケース8: MIDIへの回帰がないこと
    // -------------------------------------------------------------
    @Test
    fun loadSong_midiFile_succeeds() {
        val midiBytes = createMinimalMidiBytes()
        val uri = FakeUri("content://keycue/song.mid")

        val result = songLoader.loadFromBytes(
            bytes = midiBytes,
            displayName = "song.mid",
            mimeType = "audio/midi",
            uri = uri
        )

        assertTrue("Expected Success but got $result", result is SongLoadResult.Success)
        val success = result as SongLoadResult.Success
        assertEquals(SongFormat.MIDI, success.metadata.format)
    }

    @Test
    fun loadSong_midiFileWithoutExtension_succeedsViaMThdHeader() {
        val midiBytes = createMinimalMidiBytes()
        val uri = FakeUri("content://keycue/raw_audio")

        val result = songLoader.loadFromBytes(
            bytes = midiBytes,
            displayName = "raw_audio",
            mimeType = "application/octet-stream",
            uri = uri
        )

        assertTrue("Expected Success but got $result", result is SongLoadResult.Success)
        val success = result as SongLoadResult.Success
        assertEquals(SongFormat.MIDI, success.metadata.format)
    }

    private fun createMinimalMidiBytes(): ByteArray {
        val track = byteArrayOf(
            0x00, 0xFF.toByte(), 0x2F, 0x00 // Delta 0, End of Track
        )
        val out = ByteArrayOutputStream()
        out.write("MThd".toByteArray(Charsets.US_ASCII))
        out.write(byteArrayOf(0x00, 0x00, 0x00, 0x06)) // header length 6
        out.write(byteArrayOf(0x00, 0x00)) // format 0
        out.write(byteArrayOf(0x00, 0x01)) // 1 track
        out.write(byteArrayOf(0x01, 0xE0.toByte())) // ppqn 480
        out.write("MTrk".toByteArray(Charsets.US_ASCII))
        out.write(byteArrayOf(0x00, 0x00, 0x00, track.size.toByte()))
        out.write(track)
        return out.toByteArray()
    }
}
