package com.onigiri.keycue.song.midi

import com.onigiri.keycue.model.MidiMappingMode
import com.onigiri.keycue.model.MidiMappingSettings
import com.onigiri.keycue.model.PitchClass
import com.onigiri.keycue.model.ResolvedMidiMapping
import com.onigiri.keycue.model.ScaleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [MidiKeyMapper] およびマッピング計算・AUTO評価アルゴリズムの単体テスト。
 */
class MidiKeyMapperTest {

    // -------------------------------------------------------------
    // 1. C Major octave 4
    // -------------------------------------------------------------
    @Test
    fun testCMajorOctave4Notes() {
        val notes = MidiKeyMapper.createMapping(PitchClass.C, ScaleType.MAJOR, 4)
        assertNotNull(notes)
        val expected = listOf(
            60, 62, 64, 65, 67, // C4, D4, E4, F4, G4
            69, 71, 72, 74, 76, // A4, B4, C5, D5, E5
            77, 79, 81, 83, 84  // F5, G5, A5, B5, C6
        )
        assertEquals(expected, notes)
    }

    // -------------------------------------------------------------
    // 2. D Major octave 4
    // -------------------------------------------------------------
    @Test
    fun testDMajorOctave4Notes() {
        val notes = MidiKeyMapper.createMapping(PitchClass.D, ScaleType.MAJOR, 4)
        assertNotNull(notes)
        // D4 = 62
        val expected = listOf(
            62, 64, 66, 67, 69, // D4, E4, F#4, G4, A4
            71, 73, 74, 76, 78, // B4, C#5, D5, E5, F#5
            79, 81, 83, 85, 86  // G5, A5, B5, C#6, D6
        )
        assertEquals(expected, notes)
    }

    // -------------------------------------------------------------
    // 3. E♭ Major
    // -------------------------------------------------------------
    @Test
    fun testEbMajorOctave4Notes() {
        val notes = MidiKeyMapper.createMapping(PitchClass.E_FLAT, ScaleType.MAJOR, 4)
        assertNotNull(notes)
        // Eb4 = 63
        // intervals: 0, 2, 4, 5, 7, 9, 11, 12, 14, 16, 17, 19, 21, 23, 24
        val expected = listOf(
            63, 65, 67, 68, 70, // Eb4, F4, G4, Ab4, Bb4
            72, 74, 75, 77, 79, // C5, D5, Eb5, F5, G5
            80, 82, 84, 86, 87  // Ab5, Bb5, C6, D6, Eb6
        )
        assertEquals(expected, notes)
    }

    // -------------------------------------------------------------
    // 4. A Natural Minor
    // -------------------------------------------------------------
    @Test
    fun testANaturalMinorOctave4Notes() {
        val notes = MidiKeyMapper.createMapping(PitchClass.A, ScaleType.NATURAL_MINOR, 4)
        assertNotNull(notes)
        // A4 = 69
        // intervals: 0, 2, 3, 5, 7, 8, 10, 12, 14, 15, 17, 19, 20, 22, 24
        val expected = listOf(
            69, 71, 72, 74, 76, // A4, B4, C5, D5, E5
            77, 79, 81, 83, 84, // F5, G5, A5, B5, C6
            86, 88, 89, 91, 93  // D6, E6, F6, G6, A6
        )
        assertEquals(expected, notes)
    }

    // -------------------------------------------------------------
    // 5. C Majorが15音生成される
    // -------------------------------------------------------------
    @Test
    fun testCreatesExactly15Notes() {
        val notes = MidiKeyMapper.createMapping(PitchClass.C, ScaleType.MAJOR, 4)
        assertNotNull(notes)
        assertEquals(15, notes!!.size)
    }

    // -------------------------------------------------------------
    // 6. key 0..14へ正しく変換
    // -------------------------------------------------------------
    @Test
    fun testMapNoteToKey0To14() {
        val notes = MidiKeyMapper.createMapping(PitchClass.C, ScaleType.MAJOR, 4)!!
        val mapping = ResolvedMidiMapping(
            root = PitchClass.C,
            scale = ScaleType.MAJOR,
            baseOctave = 4,
            midiNotes = notes,
            mappedEventCount = 0,
            totalEventCount = 0
        )
        for (i in 0..14) {
            val midiNote = notes[i]
            assertEquals("Note $midiNote should map to key $i", i, mapping.keyOf(midiNote))
            assertEquals(i, MidiKeyMapper.mapNote(midiNote, mapping))
        }
    }

    // -------------------------------------------------------------
    // 7. scale外noteがnull
    // -------------------------------------------------------------
    @Test
    fun testOutOfScaleNotesMapToNull() {
        val notes = MidiKeyMapper.createMapping(PitchClass.C, ScaleType.MAJOR, 4)!!
        val mapping = ResolvedMidiMapping(
            root = PitchClass.C,
            scale = ScaleType.MAJOR,
            baseOctave = 4,
            midiNotes = notes,
            mappedEventCount = 0,
            totalEventCount = 0
        )
        assertNull(mapping.keyOf(61)) // C#4
        assertNull(mapping.keyOf(63)) // D#4
        assertNull(mapping.keyOf(66)) // F#4
        assertNull(mapping.keyOf(59)) // B3 (オクターブ外)
        assertNull(mapping.keyOf(85)) // C#6 (オクターブ外)
    }

    // -------------------------------------------------------------
    // 8. octave計算 C4 = 60
    // -------------------------------------------------------------
    @Test
    fun testOctaveCalculationC4Is60() {
        assertEquals(60, PitchClass.C.toMidiNote(4))
        assertEquals(62, PitchClass.D.toMidiNote(4))
        assertEquals(69, PitchClass.A.toMidiNote(4))
        assertEquals(12, PitchClass.C.toMidiNote(0))
        assertEquals(0, PitchClass.C.toMidiNote(-1))
    }

    // -------------------------------------------------------------
    // 9. note 0..127境界
    // -------------------------------------------------------------
    @Test
    fun testMidiNoteBoundaries() {
        // オクターブ高すぎて127を超える場合 -> null
        val tooHigh = MidiKeyMapper.createMapping(PitchClass.B, ScaleType.MAJOR, 9)
        assertNull(tooHigh)

        // オクターブ低すぎて0未満になる場合 -> null
        val tooLow = MidiKeyMapper.createMapping(PitchClass.C, ScaleType.MAJOR, -2)
        assertNull(tooLow)

        // 正常境界内
        val valid = MidiKeyMapper.createMapping(PitchClass.C, ScaleType.MAJOR, 2)
        assertNotNull(valid)
        assertTrue(valid!!.all { it in 0..127 })
    }

    // -------------------------------------------------------------
    // 10. AUTO: C Majorが最適なMIDI
    // -------------------------------------------------------------
    @Test
    fun testAutoSelectsCMajorWhenOptimal() {
        // C4, E4, G4, C5 (60, 64, 67, 72)
        val events = listOf(
            RawMidiNoteEvent(0L, 60, 80, 0),
            RawMidiNoteEvent(100L, 64, 80, 0),
            RawMidiNoteEvent(200L, 67, 80, 0),
            RawMidiNoteEvent(300L, 72, 80, 0)
        )
        val settings = MidiMappingSettings(mode = MidiMappingMode.AUTO)
        val resolved = MidiKeyMapper.resolveMapping(events, settings)

        assertEquals(PitchClass.C, resolved.root)
        assertEquals(ScaleType.MAJOR, resolved.scale)
        assertEquals(4, resolved.baseOctave)
        assertEquals(4, resolved.mappedEventCount)
        assertEquals(4, resolved.totalEventCount)
    }

    // -------------------------------------------------------------
    // 11. AUTO: D Majorが最適なMIDI
    // -------------------------------------------------------------
    @Test
    fun testAutoSelectsDMajorWhenOptimal() {
        // D4, F#4, A4, C#5 (62, 66, 69, 73)
        val events = listOf(
            RawMidiNoteEvent(0L, 62, 80, 0),
            RawMidiNoteEvent(100L, 66, 80, 0),
            RawMidiNoteEvent(200L, 69, 80, 0),
            RawMidiNoteEvent(300L, 73, 80, 0)
        )
        val settings = MidiMappingSettings(mode = MidiMappingMode.AUTO)
        val resolved = MidiKeyMapper.resolveMapping(events, settings)

        assertEquals(PitchClass.D, resolved.root)
        assertEquals(ScaleType.MAJOR, resolved.scale)
        assertEquals(4, resolved.baseOctave)
        assertEquals(4, resolved.mappedEventCount)
        assertEquals(4, resolved.totalEventCount)
    }

    // -------------------------------------------------------------
    // 12. AUTO: Minorが最適なMIDI
    // -------------------------------------------------------------
    @Test
    fun testAutoSelectsMinorWhenOptimal() {
        // D Minor (D4=62, F4=65, Bb4=70, D5=74, Bb5=82, D6=86)
        // Bb Major(58..82)には86が入らず、F Major(65..89)には62が入らないため、D Minorのみ満点(6)となる
        val events = listOf(
            RawMidiNoteEvent(0L, 62, 80, 0),
            RawMidiNoteEvent(100L, 65, 80, 0),
            RawMidiNoteEvent(200L, 70, 80, 0),
            RawMidiNoteEvent(300L, 74, 80, 0),
            RawMidiNoteEvent(400L, 82, 80, 0),
            RawMidiNoteEvent(500L, 86, 80, 0)
        )
        val settings = MidiMappingSettings(mode = MidiMappingMode.AUTO)
        val resolved = MidiKeyMapper.resolveMapping(events, settings)

        assertEquals(PitchClass.D, resolved.root)
        assertEquals(ScaleType.NATURAL_MINOR, resolved.scale)
        assertEquals(4, resolved.baseOctave)
        assertEquals(6, resolved.mappedEventCount)
        assertEquals(6, resolved.totalEventCount)
    }

    // -------------------------------------------------------------
    // 13. AUTO: base octave自動選択
    // -------------------------------------------------------------
    @Test
    fun testAutoSelectsCorrectBaseOctave() {
        // C5 (72), F5 (77), G5 (79), C6 (84), F6 (89), C7 (96)
        // F(77, 89)を含むためG Major(F#)は除外され、89や96を含むためC4 Major(60..84)も除外され、
        // C5 Major(72..96)のみが満点(6)となる
        val events = listOf(
            RawMidiNoteEvent(0L, 72, 80, 0),
            RawMidiNoteEvent(100L, 77, 80, 0),
            RawMidiNoteEvent(200L, 79, 80, 0),
            RawMidiNoteEvent(300L, 84, 80, 0),
            RawMidiNoteEvent(400L, 89, 80, 0),
            RawMidiNoteEvent(500L, 96, 80, 0)
        )
        val settings = MidiMappingSettings(mode = MidiMappingMode.AUTO)
        val resolved = MidiKeyMapper.resolveMapping(events, settings)

        // C5から始まるオクターブ5が選ばれる
        assertEquals(PitchClass.C, resolved.root)
        assertEquals(ScaleType.MAJOR, resolved.scale)
        assertEquals(5, resolved.baseOctave)
        assertEquals(6, resolved.mappedEventCount)
        assertEquals(6, resolved.totalEventCount)
    }

    // -------------------------------------------------------------
    // 14. AUTO: 一部範囲外noteがある場合
    // -------------------------------------------------------------
    @Test
    fun testAutoWithSomeOutOfRangeNotes() {
        // C4, E4, G4, そして非常に低いノート 36(C2)
        val events = listOf(
            RawMidiNoteEvent(0L, 60, 80, 0),
            RawMidiNoteEvent(100L, 64, 80, 0),
            RawMidiNoteEvent(200L, 67, 80, 0),
            RawMidiNoteEvent(300L, 36, 80, 0) // C Major Octave 4には入らない
        )
        val settings = MidiMappingSettings(mode = MidiMappingMode.AUTO)
        val resolved = MidiKeyMapper.resolveMapping(events, settings)

        assertEquals(PitchClass.C, resolved.root)
        assertEquals(4, resolved.baseOctave)
        assertEquals(3, resolved.mappedEventCount)
        assertEquals(4, resolved.totalEventCount)
        assertEquals(75.0f, resolved.mappedPercentage, 0.001f)
    }

    // -------------------------------------------------------------
    // 15. AUTO: 同点時に結果が決定的 (Deterministic Tie-Break)
    // -------------------------------------------------------------
    @Test
    fun testDeterministicTieBreak() {
        // 空のイベントの場合でも決定論的に同じ結果を返す
        val emptyEvents = emptyList<RawMidiNoteEvent>()
        val resolved1 = MidiKeyMapper.resolveMapping(emptyEvents, MidiMappingSettings(mode = MidiMappingMode.AUTO))
        val resolved2 = MidiKeyMapper.resolveMapping(emptyEvents, MidiMappingSettings(mode = MidiMappingMode.AUTO))

        assertEquals(resolved1.root, resolved2.root)
        assertEquals(resolved1.scale, resolved2.scale)
        assertEquals(resolved1.baseOctave, resolved2.baseOctave)
        assertEquals(PitchClass.C, resolved1.root)
        assertEquals(ScaleType.MAJOR, resolved1.scale)
        assertEquals(4, resolved1.baseOctave)
    }

    // -------------------------------------------------------------
    // 16. mappedEventCount & 17. totalEventCount
    // -------------------------------------------------------------
    @Test
    fun testMappedAndTotalEventCount() {
        val events = listOf(
            RawMidiNoteEvent(0L, 60, 80, 0), // C4: match
            RawMidiNoteEvent(100L, 62, 80, 0), // D4: match
            RawMidiNoteEvent(200L, 63, 80, 0)  // Eb4: not in C Major
        )
        val settings = MidiMappingSettings(
            mode = MidiMappingMode.MANUAL,
            manualRoot = PitchClass.C,
            manualScale = ScaleType.MAJOR,
            manualBaseOctave = 4
        )
        val resolved = MidiKeyMapper.resolveMapping(events, settings)

        assertEquals(2, resolved.mappedEventCount)
        assertEquals(3, resolved.totalEventCount)
        assertEquals(66.666f, resolved.mappedPercentage, 0.01f)
    }

    // -------------------------------------------------------------
    // Percussion Channel (チャネル9) の除外テスト (AUTO & MANUAL)
    // -------------------------------------------------------------
    @Test
    fun testPercussionChannelExcludedFromStats() {
        val events = listOf(
            RawMidiNoteEvent(0L, 60, 80, 0), // C4: 演奏音
            RawMidiNoteEvent(50L, 60, 80, 9), // ドラム (チャネル9)
            RawMidiNoteEvent(100L, 38, 80, 9)  // スネア (チャネル9)
        )
        val autoResolved = MidiKeyMapper.resolveMapping(events, MidiMappingSettings(mode = MidiMappingMode.AUTO))
        assertEquals(1, autoResolved.totalEventCount)
        assertEquals(1, autoResolved.mappedEventCount)

        val manualResolved = MidiKeyMapper.resolveMapping(
            events,
            MidiMappingSettings(
                mode = MidiMappingMode.MANUAL,
                manualRoot = PitchClass.C,
                manualScale = ScaleType.MAJOR,
                manualBaseOctave = 4
            )
        )
        assertEquals(1, manualResolved.totalEventCount)
        assertEquals(1, manualResolved.mappedEventCount)
    }

    // -------------------------------------------------------------
    // totalEventCount = 0 のときの mappedPercentage の安全性
    // -------------------------------------------------------------
    @Test
    fun testMappedPercentageZeroSafe() {
        val mapping = ResolvedMidiMapping(
            root = PitchClass.C,
            scale = ScaleType.MAJOR,
            baseOctave = 4,
            midiNotes = listOf(60),
            mappedEventCount = 0,
            totalEventCount = 0
        )
        assertEquals(0.0f, mapping.mappedPercentage, 0.0001f)
    }
}
