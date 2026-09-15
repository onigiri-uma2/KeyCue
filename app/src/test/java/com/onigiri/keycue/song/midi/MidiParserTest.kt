package com.onigiri.keycue.song.midi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class MidiParserTest {

    // -------------------------------------------------------------
    // 1. Variable Length Quantity (VLQ)
    // -------------------------------------------------------------
    @Test
    fun testVariableLengthQuantity() {
        // 0x00 -> 0
        assertEquals(0L, MidiParser.MidiByteReader(byteArrayOf(0x00)).readVlq())
        // 0x40 -> 64
        assertEquals(64L, MidiParser.MidiByteReader(byteArrayOf(0x40)).readVlq())
        // 0x7F -> 127
        assertEquals(127L, MidiParser.MidiByteReader(byteArrayOf(0x7F.toByte())).readVlq())
        // 0x81, 0x00 -> 128
        assertEquals(128L, MidiParser.MidiByteReader(byteArrayOf(0x81.toByte(), 0x00)).readVlq())
        // 0xC0, 0x00 -> 8192
        assertEquals(8192L, MidiParser.MidiByteReader(byteArrayOf(0xC0.toByte(), 0x00)).readVlq())
        // 0xFF, 0x7F -> 16383
        assertEquals(16383L, MidiParser.MidiByteReader(byteArrayOf(0xFF.toByte(), 0x7F)).readVlq())
        // 0x81, 0x80, 0x00 -> 16384
        assertEquals(16384L, MidiParser.MidiByteReader(byteArrayOf(0x81.toByte(), 0x80.toByte(), 0x00)).readVlq())
        // 0xFF, 0xFF, 0x7F -> 2097151
        assertEquals(2097151L, MidiParser.MidiByteReader(byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0x7F)).readVlq())
        // 0x81, 0x80, 0x80, 0x00 -> 2097152
        assertEquals(2097152L, MidiParser.MidiByteReader(byteArrayOf(0x81.toByte(), 0x80.toByte(), 0x80.toByte(), 0x00)).readVlq())
        // 0xFF, 0xFF, 0xFF, 0x7F -> 268435455 (0x0FFFFFFF)
        assertEquals(268435455L, MidiParser.MidiByteReader(byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x7F)).readVlq())
    }

    // -------------------------------------------------------------
    // 2. Note On 解析
    // -------------------------------------------------------------
    @Test
    fun testNoteOnParsing() {
        val ppqn = 480
        val track = TrackBuilder()
            .noteOn(delta = 0, note = 60, velocity = 100) // C4 -> key 0
            .noteOn(delta = 480, note = 62, velocity = 80) // D4 -> key 1
            .endOfTrack(delta = 0)

        val midiBytes = SmfBuilder(format = 0, ppqn = ppqn)
            .addTrack(track)
            .build()

        val parser = MidiParser()
        val songData = parser.parse(midiBytes, "test.mid")

        assertEquals("test.mid", songData.title)
        assertEquals(2, songData.events.size)

        // 120 BPM: 480 tick = 500 ms
        assertEquals(0L, songData.events[0].timeMs)
        assertEquals(0, songData.events[0].key)

        assertEquals(500L, songData.events[1].timeMs)
        assertEquals(1, songData.events[1].key)
    }

    // -------------------------------------------------------------
    // 3. velocity=0 の Note On (Note Off として扱われNoteEvent非生成)
    // -------------------------------------------------------------
    @Test
    fun testNoteOnWithVelocityZeroIsTreatedAsNoteOff() {
        val track = TrackBuilder()
            .noteOn(delta = 0, note = 60, velocity = 100) // Note On -> 有効
            .noteOn(delta = 240, note = 60, velocity = 0)  // Note On with vel=0 -> Note Off扱い（無視）
            .noteOff(delta = 240, note = 60, velocity = 64) // 通常のNote Off -> 無視
            .endOfTrack(delta = 0)

        val midiBytes = SmfBuilder(format = 0, ppqn = 480)
            .addTrack(track)
            .build()

        val parser = MidiParser()
        val songData = parser.parse(midiBytes, "test.mid")

        assertEquals(1, songData.events.size)
        assertEquals(0L, songData.events[0].timeMs)
        assertEquals(0, songData.events[0].key)
    }

    // -------------------------------------------------------------
    // 4. Tempo 120 BPM (Set Tempo未指定時のデフォルト動作)
    // -------------------------------------------------------------
    @Test
    fun testDefaultTempo120Bpm() {
        val ppqn = 480
        val track = TrackBuilder()
            .noteOn(delta = 0, note = 60, velocity = 90)     // tick 0 -> 0 ms
            .noteOn(delta = 480, note = 62, velocity = 90)   // tick 480 (1拍) -> 500 ms
            .noteOn(delta = 960, note = 64, velocity = 90)   // tick 1440 (3拍) -> 1500 ms
            .endOfTrack(delta = 480)                          // tick 1920 (4拍) -> 2000 ms

        val midiBytes = SmfBuilder(format = 0, ppqn = ppqn)
            .addTrack(track)
            .build()

        val parser = MidiParser()
        val songData = parser.parse(midiBytes, "test.mid")

        assertEquals(3, songData.events.size)
        assertEquals(0L, songData.events[0].timeMs)
        assertEquals(500L, songData.events[1].timeMs)
        assertEquals(1500L, songData.events[2].timeMs)
        assertEquals(2000L, songData.durationMs)
    }

    // -------------------------------------------------------------
    // 5. Tempo変更を含む tick → ms 変換
    // -------------------------------------------------------------
    @Test
    fun testTempoChangesTickToMs() {
        // PPQN = 480
        // 初期テンポ: 120 BPM (500,000 us/quarter)
        // tick 0: Note On 60 (0ms)
        // tick 480: Tempo変更 -> 60 BPM (1,000,000 us/quarter)
        // tick 480: Note On 62 (500ms)
        // tick 960: Note On 64 (500ms + 1000ms = 1500ms)
        val ppqn = 480
        val track = TrackBuilder()
            .noteOn(delta = 0, note = 60, velocity = 90)
            .setTempo(delta = 480, usPerQuarter = 1_000_000L) // 60 BPMへ減速
            .noteOn(delta = 0, note = 62, velocity = 90)
            .noteOn(delta = 480, note = 64, velocity = 90) // tick 960
            .endOfTrack(delta = 0)

        val midiBytes = SmfBuilder(format = 0, ppqn = ppqn)
            .addTrack(track)
            .build()

        val parser = MidiParser()
        val songData = parser.parse(midiBytes, "tempo_change.mid")

        assertEquals(3, songData.events.size)
        assertEquals(0L, songData.events[0].timeMs)
        assertEquals(500L, songData.events[1].timeMs)
        assertEquals(1500L, songData.events[2].timeMs)
        assertEquals(1500L, songData.durationMs)
    }

    // -------------------------------------------------------------
    // 6. Format 0 (単一トラック)
    // -------------------------------------------------------------
    @Test
    fun testFormat0Parsing() {
        val ppqn = 480
        val track = TrackBuilder()
            .setTempo(delta = 0, usPerQuarter = 500_000L)
            .noteOn(delta = 0, note = 60, velocity = 80)
            .noteOn(delta = 480, note = 72, velocity = 80)
            .endOfTrack(delta = 240)

        val midiBytes = SmfBuilder(format = 0, ppqn = ppqn)
            .addTrack(track)
            .build()

        val parser = MidiParser()
        val songData = parser.parse(midiBytes, "format0.mid")

        assertEquals(2, songData.events.size)
        assertEquals(0, songData.events[0].key) // 60 -> 0
        assertEquals(7, songData.events[1].key) // 72 -> 7
        assertEquals(750L, songData.durationMs)
    }

    // -------------------------------------------------------------
    // 7. 複数Trackの Format 1
    // -------------------------------------------------------------
    @Test
    fun testFormat1MultipleTracks() {
        val ppqn = 480
        // Track 0: テンポ情報
        val tempoTrack = TrackBuilder()
            .setTempo(delta = 0, usPerQuarter = 500_000L) // 120 BPM
            .endOfTrack(delta = 960)

        // Track 1: メロディ
        val track1 = TrackBuilder()
            .noteOn(delta = 0, note = 60, velocity = 100) // tick 0 -> 0ms
            .noteOn(delta = 480, note = 64, velocity = 100) // tick 480 -> 500ms
            .endOfTrack(delta = 480)

        // Track 2: 伴奏
        val track2 = TrackBuilder()
            .noteOn(delta = 240, note = 67, velocity = 90) // tick 240 -> 250ms
            .noteOn(delta = 480, note = 72, velocity = 90) // tick 720 -> 750ms
            .endOfTrack(delta = 240)

        val midiBytes = SmfBuilder(format = 1, ppqn = ppqn)
            .addTrack(tempoTrack)
            .addTrack(track1)
            .addTrack(track2)
            .build()

        val parser = MidiParser()
        val songData = parser.parse(midiBytes, "format1.mid")

        assertEquals(4, songData.events.size)
        // 昇順にソートされていること
        assertEquals(0L, songData.events[0].timeMs)
        assertEquals(0, songData.events[0].key) // 60 -> key 0

        assertEquals(250L, songData.events[1].timeMs)
        assertEquals(4, songData.events[1].key) // 67 -> key 4

        assertEquals(500L, songData.events[2].timeMs)
        assertEquals(2, songData.events[2].key) // 64 -> key 2

        assertEquals(750L, songData.events[3].timeMs)
        assertEquals(7, songData.events[3].key) // 72 -> key 7

        assertEquals(960L * 500L / 480L, songData.durationMs) // 1000ms
    }

    // -------------------------------------------------------------
    // 8. MidiKeyMapper
    // -------------------------------------------------------------
    @Test
    fun testMidiKeyMapper() {
        val mapper = MidiKeyMapper()

        val expected = mapOf(
            60 to 0,  // C4
            62 to 1,  // D4
            64 to 2,  // E4
            65 to 3,  // F4
            67 to 4,  // G4
            69 to 5,  // A4
            71 to 6,  // B4
            72 to 7,  // C5
            74 to 8,  // D5
            76 to 9,  // E5
            77 to 10, // F5
            79 to 11, // G5
            81 to 12, // A5
            83 to 13, // B5
            84 to 14  // C6
        )

        for ((midiNote, expectedKey) in expected) {
            assertEquals("Note $midiNote should map to key $expectedKey", expectedKey, mapper.map(midiNote))
        }

        // 黒鍵やオクターブ外は null
        assertNull(mapper.map(61)) // C#4
        assertNull(mapper.map(63)) // D#4
        assertNull(mapper.map(59)) // B3
        assertNull(mapper.map(85)) // C#6
        assertNull(mapper.map(36)) // C2

        // 移調 (transpose = +2) のテスト
        val transposedMapper = MidiKeyMapper(transpose = 2)
        assertEquals(0, transposedMapper.map(58)) // 58 + 2 = 60 -> key 0
        assertEquals(1, transposedMapper.map(60)) // 60 + 2 = 62 -> key 1
        assertNull(transposedMapper.map(59))      // 59 + 2 = 61 (C#4) -> null

        // オクターブシフト (octaveShift = -1) のテスト
        val octaveShiftedMapper = MidiKeyMapper(octaveShift = -1)
        assertEquals(0, octaveShiftedMapper.map(72)) // 72 - 12 = 60 -> key 0
    }

    // -------------------------------------------------------------
    // 9. 同時刻の和音
    // -------------------------------------------------------------
    @Test
    fun testChordsAtSameTickPreserved() {
        val track = TrackBuilder()
            // 同時刻 tick 0 で 3音同時に Note On (ド・ミ・ソ: 60, 64, 67)
            .noteOn(delta = 0, note = 60, velocity = 90)
            .noteOn(delta = 0, note = 64, velocity = 90)
            .noteOn(delta = 0, note = 67, velocity = 90)
            .endOfTrack(delta = 480)

        val midiBytes = SmfBuilder(format = 0, ppqn = 480)
            .addTrack(track)
            .build()

        val parser = MidiParser()
        val songData = parser.parse(midiBytes, "chord.mid")

        assertEquals(3, songData.events.size)
        // 3音すべてが timeMs=0 を保持
        assertEquals(0L, songData.events[0].timeMs)
        assertEquals(0, songData.events[0].key) // C4

        assertEquals(0L, songData.events[1].timeMs)
        assertEquals(2, songData.events[1].key) // E4

        assertEquals(0L, songData.events[2].timeMs)
        assertEquals(4, songData.events[2].key) // G4
    }

    // -------------------------------------------------------------
    // 10. 範囲外ノートが無視されること
    // -------------------------------------------------------------
    @Test
    fun testOutOfRangeNotesIgnored() {
        val track = TrackBuilder()
            .noteOn(delta = 0, note = 36, velocity = 90)   // C2 (範囲外 -> 無視)
            .noteOn(delta = 0, note = 61, velocity = 90)   // C#4 (半音外 -> 無視)
            .noteOn(delta = 0, note = 60, velocity = 90)   // C4 (範囲内 -> key 0)
            .noteOn(delta = 240, note = 85, velocity = 90) // C#6 (範囲外 -> 無視)
            .noteOn(delta = 0, note = 84, velocity = 90)   // C6 (範囲内 -> key 14)
            .endOfTrack(delta = 240)

        val midiBytes = SmfBuilder(format = 0, ppqn = 480)
            .addTrack(track)
            .build()

        val parser = MidiParser()
        val songData = parser.parse(midiBytes, "out_of_range.mid")

        // 範囲内の 60 と 84 の2音のみ抽出される
        assertEquals(2, songData.events.size)
        assertEquals(0, songData.events[0].key)
        assertEquals(14, songData.events[1].key)
    }

    // -------------------------------------------------------------
    // Running Status の動作検証
    // -------------------------------------------------------------
    @Test
    fun testRunningStatus() {
        val trackBytes = ByteArrayOutputStream().apply {
            // delta=0, status=0x90, note=60, vel=100
            write(0x00) // delta
            write(0x90) // Note On channel 0
            write(60)
            write(100)

            // delta=0, running status (ステータス省略), note=64, vel=90
            write(0x00) // delta
            write(64)
            write(90)

            // delta=0, End of Track (0xFF 0x2F 0x00)
            write(0x00)
            write(0xFF)
            write(0x2F)
            write(0x00)
        }.toByteArray()

        val smfBytes = ByteArrayOutputStream().apply {
            // MThd
            write("MThd".toByteArray(Charsets.US_ASCII))
            writeInt(6)
            writeShort(0) // format 0
            writeShort(1) // 1 track
            writeShort(480) // ppqn

            // MTrk
            write("MTrk".toByteArray(Charsets.US_ASCII))
            writeInt(trackBytes.size)
            write(trackBytes)
        }.toByteArray()

        val parser = MidiParser()
        val songData = parser.parse(smfBytes, "running_status.mid")

        assertEquals(2, songData.events.size)
        assertEquals(0, songData.events[0].key)
        assertEquals(2, songData.events[1].key)
    }

    // -------------------------------------------------------------
    // エラーハンドリングのテスト
    // -------------------------------------------------------------
    @Test
    fun testInvalidMidiHeaderThrowsInvalidMidiException() {
        val corruptedBytes = "NOT_A_MIDI_FILE".toByteArray()
        assertThrows(InvalidMidiException::class.java) {
            MidiParser().parse(corruptedBytes, "corrupt.mid")
        }
    }

    @Test
    fun testUnsupportedFormatThrowsUnsupportedMidiException() {
        val format2Bytes = SmfBuilder(format = 2, ppqn = 480)
            .addTrack(TrackBuilder().endOfTrack(0))
            .build()

        assertThrows(UnsupportedMidiException::class.java) {
            MidiParser().parse(format2Bytes, "format2.mid")
        }
    }

    // -------------------------------------------------------------
    // テスト用 SMF バイナリビルダー
    // -------------------------------------------------------------
    private class TrackBuilder {
        private val stream = ByteArrayOutputStream()

        fun setTempo(delta: Long, usPerQuarter: Long): TrackBuilder {
            writeVlq(delta)
            stream.write(0xFF)
            stream.write(0x51)
            stream.write(0x03)
            stream.write(((usPerQuarter shr 16) and 0xFF).toInt())
            stream.write(((usPerQuarter shr 8) and 0xFF).toInt())
            stream.write((usPerQuarter and 0xFF).toInt())
            return this
        }

        fun noteOn(delta: Long, channel: Int = 0, note: Int, velocity: Int): TrackBuilder {
            writeVlq(delta)
            stream.write(0x90 or (channel and 0x0F))
            stream.write(note and 0x7F)
            stream.write(velocity and 0x7F)
            return this
        }

        fun noteOff(delta: Long, channel: Int = 0, note: Int, velocity: Int = 64): TrackBuilder {
            writeVlq(delta)
            stream.write(0x80 or (channel and 0x0F))
            stream.write(note and 0x7F)
            stream.write(velocity and 0x7F)
            return this
        }

        fun endOfTrack(delta: Long = 0): TrackBuilder {
            writeVlq(delta)
            stream.write(0xFF)
            stream.write(0x2F)
            stream.write(0x00)
            return this
        }

        fun toBytes(): ByteArray = stream.toByteArray()

        private fun writeVlq(value: Long) {
            var v = value
            val buffer = ByteArray(5)
            var count = 0
            buffer[count++] = (v and 0x7F).toByte()
            v = v ushr 7
            while (v > 0) {
                buffer[count++] = ((v and 0x7F) or 0x80).toByte()
                v = v ushr 7
            }
            for (i in count - 1 downTo 0) {
                stream.write(buffer[i].toInt() and 0xFF)
            }
        }
    }

    private class SmfBuilder(
        private val format: Int,
        private val ppqn: Int
    ) {
        private val tracks = mutableListOf<ByteArray>()

        fun addTrack(track: TrackBuilder): SmfBuilder {
            tracks.add(track.toBytes())
            return this
        }

        fun build(): ByteArray {
            val out = ByteArrayOutputStream()

            // Header Chunk (MThd)
            out.write("MThd".toByteArray(Charsets.US_ASCII))
            out.writeInt(6) // length
            out.writeShort(format)
            out.writeShort(tracks.size)
            out.writeShort(ppqn)

            // Track Chunks (MTrk)
            for (trackBytes in tracks) {
                out.write("MTrk".toByteArray(Charsets.US_ASCII))
                out.writeInt(trackBytes.size)
                out.write(trackBytes)
            }

            return out.toByteArray()
        }
    }
}

private fun ByteArrayOutputStream.writeShort(value: Int) {
    write((value shr 8) and 0xFF)
    write(value and 0xFF)
}

private fun ByteArrayOutputStream.writeInt(value: Int) {
    write((value shr 24) and 0xFF)
    write((value shr 16) and 0xFF)
    write((value shr 8) and 0xFF)
    write(value and 0xFF)
}
