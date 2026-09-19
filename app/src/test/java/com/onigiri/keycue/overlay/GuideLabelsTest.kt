package com.onigiri.keycue.overlay

import com.onigiri.keycue.model.PitchClass
import com.onigiri.keycue.model.PlaybackConfig
import com.onigiri.keycue.model.PlaybackSession
import com.onigiri.keycue.model.ResolvedMidiMapping
import com.onigiri.keycue.model.ScaleType
import com.onigiri.keycue.model.SongData
import com.onigiri.keycue.model.SongFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * ガイドラベルの生成およびセッション解決純粋関数 [resolveGuideLabels] の単体テスト。
 * Android View / Canvas に依存せず、純粋ロジックとして検証する。
 */
class GuideLabelsTest {

    private fun createDummySong(title: String = "Test Song"): SongData = SongData(
        title = title,
        durationMs = 1000L,
        events = emptyList()
    )

    private fun createMapping(
        notes: List<Int>,
        root: PitchClass = PitchClass.C,
        scale: ScaleType = ScaleType.MAJOR,
        octave: Int = 4
    ): ResolvedMidiMapping = ResolvedMidiMapping(
        root = root,
        scale = scale,
        baseOctave = octave,
        midiNotes = notes,
        mappedEventCount = 10,
        totalEventCount = 10
    )

    @Test
    fun guideLabels_preservesOrderAndSizeOfMidiNotes() {
        // 大小順ではなく、ガイドインデックスの順序 (64, 60, 67) がそのまま保持されること
        val notes = listOf(64, 60, 67)
        val mapping = createMapping(notes)

        assertEquals(listOf("64", "60", "67"), mapping.guideLabels)
        assertEquals(notes.size, mapping.guideLabels.size)
    }

    @Test
    fun guideLabels_supportsArbitrarySizesWithoutConstraint() {
        // ResolvedMidiMapping に15件固定制約が追加されていないことを確認（1要素でも安全に生成可能）
        val notes = listOf(127)
        val mapping = createMapping(notes)

        assertEquals(listOf("127"), mapping.guideLabels)
        assertEquals(1, mapping.guideLabels.size)
    }

    @Test
    fun guideLabels_handlesThreeDigitMidiNotesCorrectly() {
        // 0..127 の範囲で3桁のノート番号が正常に文字列表記されること
        val notes = listOf(100, 120, 127)
        val mapping = createMapping(notes)

        assertEquals(listOf("100", "120", "127"), mapping.guideLabels)
    }

    @Test
    fun guideLabels_updatesReferenceOnNewMappingInstance() {
        // マッピング変更時、新インスタンスから新しいguideLabels参照が得られること
        val mapping1 = createMapping(listOf(60, 62, 64))
        val mapping2 = createMapping(listOf(72, 74, 76))

        assertEquals(listOf("60", "62", "64"), mapping1.guideLabels)
        assertEquals(listOf("72", "74", "76"), mapping2.guideLabels)
    }

    @Test
    fun resolveGuideLabels_returnsGuideLabelsForMidiSessionWithMapping() {
        val mapping = createMapping(listOf(48, 50, 52))
        val session = PlaybackSession(
            song = createDummySong(),
            config = PlaybackConfig(),
            format = SongFormat.MIDI,
            resolvedMidiMapping = mapping
        )

        val labels = resolveGuideLabels(session)
        // 同一参照が返されること（無駄なコピーを行わない）
        assertSame(mapping.guideLabels, labels)
        assertEquals(listOf("48", "50", "52"), labels)
    }

    @Test
    fun resolveGuideLabels_returnsNullForMidiSessionWithoutMapping() {
        val session = PlaybackSession(
            song = createDummySong(),
            config = PlaybackConfig(),
            format = SongFormat.MIDI,
            resolvedMidiMapping = null
        )

        assertNull(resolveGuideLabels(session))
    }

    @Test
    fun resolveGuideLabels_returnsNullForNonMidiSession() {
        val session = PlaybackSession(
            song = createDummySong(),
            config = PlaybackConfig(),
            format = SongFormat.SKY_STUDIO_JSON,
            resolvedMidiMapping = null
        )

        assertNull(resolveGuideLabels(session))
    }

    @Test
    fun resolveGuideLabels_returnsNullWhenSessionIsNull() {
        assertNull(resolveGuideLabels(null))
    }

    @Test
    fun resolveGuideLabels_prioritizesFormatOverResolvedMappingPresence() {
        // 何らかの理由で非MIDIセッションにresolvedMidiMappingが残っていても、format != MIDIならnullを返す
        val mapping = createMapping(listOf(60, 62, 64))
        val session = PlaybackSession(
            song = createDummySong(),
            config = PlaybackConfig(),
            format = SongFormat.SKY_STUDIO_JSON,
            resolvedMidiMapping = mapping
        )

        assertNull(resolveGuideLabels(session))
    }
}
