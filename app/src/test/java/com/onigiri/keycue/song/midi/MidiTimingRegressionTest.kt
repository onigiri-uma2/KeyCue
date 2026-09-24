package com.onigiri.keycue.song.midi

import com.onigiri.keycue.model.MidiMappingMode
import com.onigiri.keycue.model.MidiMappingSettings
import com.onigiri.keycue.model.timing.SongTimingMetadata
import com.onigiri.keycue.model.timing.TimeSignature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

/**
 * MIDI解析・楽曲タイミングメタデータ（テンポ・拍子）の回帰テスト群。
 *
 * 【検証要件】
 * 1. TempoイベントがないMIDI -> 標準120 BPM
 * 2. Time SignatureイベントがないMIDI -> 標準4/4
 * 3. tick 0 に 120 BPM を明示指定
 * 4. 曲中で 120 -> 150 BPM への変更
 * 5. 複数回のテンポ変更 (120 -> 150 -> 200 -> 80)
 * 6. 曲中で 4/4 -> 3/4 への拍子変更
 * 7. 6/8 拍子の解析 (numerator=6, denominator=8)
 * 8. Format 1 で複数トラックに分散したメタイベントの tick 順統合
 * 9. 同一 tick の競合メタイベントにおける決定的な優先度（トラック0優先）
 * 10. 不正なテンポ値への安全な対応
 * 11. 不正な拍子値への安全な対応
 * 12. MIDI再マッピング後も timingMetadata が維持されること
 * 13. 既存ノートの timeMs がテンポ解析によって改変されないこと
 * Format 0 と Format 1 の両方で動作を検証。
 */
class MidiTimingRegressionTest {

    private val parser = MidiParser()

    // 1. TempoイベントがないMIDI -> 標準120 BPM
    @Test
    fun testNoTempoEvent_usesStandard120Bpm() {
        val track = TrackHelper()
            .noteOn(delta = 0, note = 60, velocity = 100)
            .noteOn(delta = 480, note = 62, velocity = 100)
            .endOfTrack(delta = 0)

        val smf = SmfHelper(format = 0, ppqn = 480).addTrack(track).build()
        val song = parser.parse(smf, "no_tempo.mid")

        val timing = song.timingMetadata as? SongTimingMetadata.Midi
        assertNotNull(timing)
        assertEquals(480, timing!!.ppqn)
        assertEquals(120.0, timing.primaryBpm, 0.001)
        assertFalse(timing.hasExplicitTempo)
        // tick 480 = 500ms
        assertEquals(500L, timing.tempoMap.tickToMs(480L))
    }

    // 2. Time SignatureイベントがないMIDI -> 標準4/4
    @Test
    fun testNoTimeSignatureEvent_usesStandard4_4() {
        val track = TrackHelper()
            .noteOn(delta = 0, note = 60, velocity = 100)
            .endOfTrack(delta = 480)

        val smf = SmfHelper(format = 0, ppqn = 480).addTrack(track).build()
        val song = parser.parse(smf, "no_ts.mid")

        val timing = song.timingMetadata as? SongTimingMetadata.Midi
        assertNotNull(timing)
        assertEquals(TimeSignature(4, 4), timing!!.primaryTimeSignature)
        assertFalse(timing.hasExplicitTimeSignature)
    }

    // 3. tick 0 に 120 BPM を明示指定 (500000 us/quarter)
    @Test
    fun testExplicit120BpmAtTick0() {
        val track = TrackHelper()
            .setTempo(delta = 0, usPerQuarter = 500_000L) // 120 BPM
            .noteOn(delta = 480, note = 60, velocity = 100)
            .endOfTrack(delta = 0)

        val smf = SmfHelper(format = 0, ppqn = 480).addTrack(track).build()
        val song = parser.parse(smf, "explicit_120.mid")

        val timing = song.timingMetadata as? SongTimingMetadata.Midi
        assertNotNull(timing)
        assertTrue(timing!!.hasExplicitTempo)
        assertEquals(120.0, timing.primaryBpm, 0.001)
        assertEquals(500L, timing.tempoMap.tickToMs(480L))
    }

    // 4. 曲中で 120 -> 150 BPM (tick 0: 500_000us, tick 960: 400_000us)
    @Test
    fun testTempoChange_120_to_150() {
        val track = TrackHelper()
            .setTempo(delta = 0, usPerQuarter = 500_000L)   // 120 BPM
            .setTempo(delta = 960, usPerQuarter = 400_000L) // tick 960: 150 BPM
            .noteOn(delta = 480, note = 60, velocity = 100) // tick 1440
            .endOfTrack(delta = 0)

        val smf = SmfHelper(format = 0, ppqn = 480).addTrack(track).build()
        val song = parser.parse(smf, "tempo_change.mid")

        val timing = song.timingMetadata as? SongTimingMetadata.Midi
        assertNotNull(timing)
        // tick 0: 120 BPM (1000ms for 960 ticks)
        assertEquals(0L, timing!!.tempoMap.tickToMs(0L))
        assertEquals(500L, timing.tempoMap.tickToMs(480L))
        assertEquals(1000L, timing.tempoMap.tickToMs(960L))

        // tick 960以降: 150 BPM (400ms per 480 ticks)
        // tick 1440 = 1000ms + 400ms = 1400ms
        assertEquals(1400L, timing.tempoMap.tickToMs(1440L))
        assertEquals(150.0, timing.tempoMap.bpmAtMs(1200L), 0.001)
    }

    // 5. 複数回のテンポ変更 (120 -> 150 -> 200 -> 80)
    @Test
    fun testMultipleTempoChanges() {
        val track = TrackHelper()
            .setTempo(delta = 0, usPerQuarter = 500_000L)    // 120 BPM
            .setTempo(delta = 480, usPerQuarter = 400_000L)  // tick 480: 150 BPM
            .setTempo(delta = 480, usPerQuarter = 300_000L)  // tick 960: 200 BPM
            .setTempo(delta = 480, usPerQuarter = 750_000L)  // tick 1440: 80 BPM
            .endOfTrack(delta = 480)

        val smf = SmfHelper(format = 0, ppqn = 480).addTrack(track).build()
        val song = parser.parse(smf, "multi_tempo.mid")

        val tm = (song.timingMetadata as SongTimingMetadata.Midi).tempoMap
        assertEquals(0L, tm.tickToMs(0L))
        assertEquals(500L, tm.tickToMs(480L)) // 120 BPM: 500ms
        assertEquals(900L, tm.tickToMs(960L)) // 150 BPM: +400ms = 900ms
        assertEquals(1200L, tm.tickToMs(1440L)) // 200 BPM: +300ms = 1200ms
        assertEquals(1950L, tm.tickToMs(1920L)) // 80 BPM: +750ms = 1950ms
    }

    // 6. 曲中で 4/4 -> 3/4
    @Test
    fun testTimeSignatureChange_4_4_to_3_4() {
        val track = TrackHelper()
            .timeSignature(delta = 0, nn = 4, dd = 2)   // 4/4
            .timeSignature(delta = 1920, nn = 3, dd = 2) // tick 1920: 3/4
            .endOfTrack(delta = 0)

        val smf = SmfHelper(format = 0, ppqn = 480).addTrack(track).build()
        val song = parser.parse(smf, "ts_change.mid")

        val tsMap = (song.timingMetadata as SongTimingMetadata.Midi).timeSignatureMap
        assertTrue(tsMap.hasExplicitTimeSignature)
        assertEquals(TimeSignature(4, 4), tsMap.timeSignatureAtTick(0L))
        assertEquals(TimeSignature(4, 4), tsMap.timeSignatureAtTick(1919L))
        assertEquals(TimeSignature(3, 4), tsMap.timeSignatureAtTick(1920L))
        assertEquals(TimeSignature(3, 4), tsMap.timeSignatureAtTick(3000L))
    }

    // 7. 6/8 の解析
    @Test
    fun testTimeSignature_6_8() {
        val track = TrackHelper()
            .timeSignature(delta = 0, nn = 6, dd = 3) // 6/8
            .endOfTrack(delta = 0)

        val smf = SmfHelper(format = 0, ppqn = 480).addTrack(track).build()
        val song = parser.parse(smf, "six_eight.mid")

        val tsMap = (song.timingMetadata as SongTimingMetadata.Midi).timeSignatureMap
        assertEquals(TimeSignature(6, 8), tsMap.firstTimeSignature)
        assertEquals(6, tsMap.firstTimeSignature.numerator)
        assertEquals(8, tsMap.firstTimeSignature.denominator)
        assertEquals("6/8", tsMap.firstTimeSignature.displayString)
    }

    // 8. Format 1: 複数トラックに分散したメタイベントの統合
    @Test
    fun testFormat1_multiTrackMetaEvents() {
        // Track 0: Conductor Track (Tempo, Time Signature)
        val conductor = TrackHelper()
            .setTempo(delta = 0, usPerQuarter = 500_000L) // 120 BPM
            .timeSignature(delta = 0, nn = 4, dd = 2)
            .setTempo(delta = 960, usPerQuarter = 250_000L) // tick 960: 240 BPM
            .endOfTrack(delta = 0)

        // Track 1: Notes and mid-song TS change
        val musicTrack = TrackHelper()
            .noteOn(delta = 0, note = 60, velocity = 100)
            .timeSignature(delta = 960, nn = 3, dd = 2) // tick 960: 3/4
            .noteOn(delta = 480, note = 62, velocity = 100) // tick 1440
            .endOfTrack(delta = 0)

        val smf = SmfHelper(format = 1, ppqn = 480)
            .addTrack(conductor)
            .addTrack(musicTrack)
            .build()

        val song = parser.parse(smf, "format1_multitrack.mid")
        val timing = song.timingMetadata as SongTimingMetadata.Midi

        assertEquals(120.0, timing.tempoMap.bpmAtMs(500L), 0.001)
        assertEquals(240.0, timing.tempoMap.bpmAtMs(1100L), 0.001)
        assertEquals(TimeSignature(4, 4), timing.timeSignatureMap.timeSignatureAtTick(0L))
        assertEquals(TimeSignature(3, 4), timing.timeSignatureMap.timeSignatureAtTick(960L))
    }

    // 9. 同一tickの競合イベント (Format 1: トラック0優先)
    @Test
    fun testConflictingEventsAtSameTick_prefersTrack0() {
        // Track 0: tick 0 -> 120 BPM
        val track0 = TrackHelper()
            .setTempo(delta = 0, usPerQuarter = 500_000L)
            .endOfTrack(delta = 0)

        // Track 1: tick 0 -> 150 BPM
        val track1 = TrackHelper()
            .setTempo(delta = 0, usPerQuarter = 400_000L)
            .endOfTrack(delta = 0)

        val smf = SmfHelper(format = 1, ppqn = 480)
            .addTrack(track0)
            .addTrack(track1)
            .build()

        val song = parser.parse(smf, "conflict.mid")
        val timing = song.timingMetadata as SongTimingMetadata.Midi
        // トラック0の120 BPMが優先採用されること
        assertEquals(120.0, timing.primaryBpm, 0.001)
    }

    // 10. 不正なテンポ値 (usPerQuarter <= 0) への安全な対応
    @Test
    fun testInvalidTempoValue_handledSafely() {
        val track = TrackHelper()
            .setTempo(delta = 0, usPerQuarter = 0L) // 不正なテンポ
            .noteOn(delta = 480, note = 60, velocity = 100)
            .endOfTrack(delta = 0)

        val smf = SmfHelper(format = 0, ppqn = 480).addTrack(track).build()
        val song = parser.parse(smf, "invalid_tempo.mid")

        val timing = song.timingMetadata as SongTimingMetadata.Midi
        // 0は無視されてデフォルトの120 BPM (500000us) が適用されること
        assertEquals(120.0, timing.primaryBpm, 0.001)
    }

    // 11. 不正な拍子値 (dd >= 8 等) への安全な対応
    @Test
    fun testInvalidTimeSignatureValue_handledSafely() {
        val track = TrackHelper()
            .timeSignature(delta = 0, nn = 0, dd = 10) // 不正な分子0、過大な分母2^10
            .endOfTrack(delta = 0)

        val smf = SmfHelper(format = 0, ppqn = 480).addTrack(track).build()
        val song = parser.parse(smf, "invalid_ts.mid")

        val timing = song.timingMetadata as SongTimingMetadata.Midi
        // 不正値は安全に破棄され、デフォルトの4/4が適用されること
        assertEquals(TimeSignature(4, 4), timing.primaryTimeSignature)
    }

    // 12. MIDI再マッピング後も timingMetadata が維持されること
    @Test
    fun testReapplyMapping_preservesTimingMetadata() {
        val track = TrackHelper()
            .setTempo(delta = 0, usPerQuarter = 400_000L) // 150 BPM
            .timeSignature(delta = 0, nn = 3, dd = 2)     // 3/4
            .noteOn(delta = 480, note = 60, velocity = 100)
            .endOfTrack(delta = 0)

        val smf = SmfHelper(format = 0, ppqn = 480).addTrack(track).build()
        val extracted = parser.extractRawEvents(smf)

        val mapping = MidiKeyMapper.resolveMapping(extracted.rawEvents, MidiMappingSettings(mode = MidiMappingMode.AUTO))
        val song = parser.parseWithResolvedMapping(extracted, "song.mid", mapping)

        val timing = song.timingMetadata as? SongTimingMetadata.Midi
        assertNotNull(timing)
        assertEquals(150.0, timing!!.primaryBpm, 0.001)
        assertEquals(TimeSignature(3, 4), timing.primaryTimeSignature)
        assertEquals(480, timing.ppqn)
    }

    // 13. 既存ノートの timeMs がテンポ解析によって改変されないこと
    @Test
    fun testNoteTimeMs_matchesTempoMapExactly() {
        val track = TrackHelper()
            .setTempo(delta = 0, usPerQuarter = 500_000L)   // tick 0: 120 BPM
            .noteOn(delta = 240, note = 60, velocity = 100) // tick 240 (delta 240) -> 250ms
            .setTempo(delta = 240, usPerQuarter = 250_000L) // tick 480 (delta 240): 240 BPMへ
            .noteOn(delta = 240, note = 62, velocity = 100) // tick 720 (delta 240): tick 480(500ms) + 240tick(125ms) = 625ms
            .endOfTrack(delta = 0)

        val smf = SmfHelper(format = 0, ppqn = 480).addTrack(track).build()
        val song = parser.parse(smf, "notes_timing.mid")

        assertEquals(2, song.events.size)
        assertEquals(250L, song.events[0].timeMs)
        assertEquals(625L, song.events[1].timeMs)
    }

    // -------------------------------------------------------------
    // テスト用ヘルパークラス
    // -------------------------------------------------------------
    private class TrackHelper {
        private val stream = ByteArrayOutputStream()

        fun setTempo(delta: Long, usPerQuarter: Long): TrackHelper {
            writeVlq(delta)
            stream.write(0xFF)
            stream.write(0x51)
            stream.write(0x03)
            stream.write(((usPerQuarter shr 16) and 0xFF).toInt())
            stream.write(((usPerQuarter shr 8) and 0xFF).toInt())
            stream.write((usPerQuarter and 0xFF).toInt())
            return this
        }

        fun timeSignature(delta: Long, nn: Int, dd: Int, cc: Int = 24, bb: Int = 8): TrackHelper {
            writeVlq(delta)
            stream.write(0xFF)
            stream.write(0x58)
            stream.write(0x04)
            stream.write(nn and 0xFF)
            stream.write(dd and 0xFF)
            stream.write(cc and 0xFF)
            stream.write(bb and 0xFF)
            return this
        }

        fun noteOn(delta: Long, channel: Int = 0, note: Int, velocity: Int): TrackHelper {
            writeVlq(delta)
            stream.write(0x90 or (channel and 0x0F))
            stream.write(note and 0x7F)
            stream.write(velocity and 0x7F)
            return this
        }

        fun endOfTrack(delta: Long = 0): TrackHelper {
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

    private class SmfHelper(private val format: Int, private val ppqn: Int) {
        private val tracks = mutableListOf<ByteArray>()

        fun addTrack(track: TrackHelper): SmfHelper {
            tracks.add(track.toBytes())
            return this
        }

        fun build(): ByteArray {
            val out = ByteArrayOutputStream()
            out.write("MThd".toByteArray(Charsets.US_ASCII))
            out.writeInt(6)
            out.writeShort(format)
            out.writeShort(tracks.size)
            out.writeShort(ppqn)

            for (track in tracks) {
                out.write("MTrk".toByteArray(Charsets.US_ASCII))
                out.writeInt(track.size)
                out.write(track)
            }
            return out.toByteArray()
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
    }
}
