package com.onigiri.keycue.profile

import com.onigiri.keycue.model.FitProfile
import com.onigiri.keycue.model.NormalizedPoint
import com.onigiri.keycue.playback.FallingNoteCalculator
import com.onigiri.keycue.song.midi.MidiKeyMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [SkyProfile] の仕様・整合性・回帰テスト。
 *
 * 循環参照を避け、リファクタリング前の固定期待値を用いて
 * 座標、キー数、アンカー、MIDIマッピング、行・列計算の完全一致を検証します。
 */
class SkyProfileTest {

    @Test
    fun `SkyProfile basic dimensions and anchors match Sky specifications`() {
        assertEquals(15, SkyProfile.keyCount)
        assertEquals(3, SkyProfile.rowCount)
        assertEquals(5, SkyProfile.columnCount)

        assertEquals(0, SkyProfile.topLeftKeyIndex)
        assertEquals(4, SkyProfile.topRightKeyIndex)
        assertEquals(10, SkyProfile.bottomLeftKeyIndex)
        assertEquals(14, SkyProfile.bottomRightKeyIndex)

        assertEquals(0.04f, SkyProfile.baseKeyRadiusRatio, 0.0001f)
    }

    @Test
    fun `SkyProfile baseKeyCenters matches fixed expected coordinates exactly`() {
        // リファクタリング前の既存コード (FitProfile.createDefaultTestProfile) で生成されていた基準座標リスト
        val expectedCenters = listOf(
            // Row 0 (上段)
            NormalizedPoint(0.20f, 0.65f),
            NormalizedPoint(0.35f, 0.65f),
            NormalizedPoint(0.50f, 0.65f),
            NormalizedPoint(0.65f, 0.65f),
            NormalizedPoint(0.80f, 0.65f),

            // Row 1 (中段)
            NormalizedPoint(0.20f, 0.75f),
            NormalizedPoint(0.35f, 0.75f),
            NormalizedPoint(0.50f, 0.75f),
            NormalizedPoint(0.65f, 0.75f),
            NormalizedPoint(0.80f, 0.75f),

            // Row 2 (下段)
            NormalizedPoint(0.20f, 0.85f),
            NormalizedPoint(0.35f, 0.85f),
            NormalizedPoint(0.50f, 0.85f),
            NormalizedPoint(0.65f, 0.85f),
            NormalizedPoint(0.80f, 0.85f)
        )

        // 1. 固定期待値と SkyProfile.baseKeyCenters の比較（非循環参照検証）
        assertEquals(expectedCenters.size, SkyProfile.baseKeyCenters.size)
        for (i in expectedCenters.indices) {
            assertEquals("Key $i X mismatch", expectedCenters[i].x, SkyProfile.baseKeyCenters[i].x, 0.0001f)
            assertEquals("Key $i Y mismatch", expectedCenters[i].y, SkyProfile.baseKeyCenters[i].y, 0.0001f)
        }

        // 2. SkyProfile.baseKeyCenters と FitProfile.createDefaultTestProfile().keyCenters の比較
        val defaultFitProfile = FitProfile.createDefaultTestProfile(landscape = true)
        assertEquals(SkyProfile.baseKeyCenters.size, defaultFitProfile.keyCenters.size)
        for (i in SkyProfile.baseKeyCenters.indices) {
            assertEquals("Default FitProfile Key $i X mismatch", SkyProfile.baseKeyCenters[i].x, defaultFitProfile.keyCenters[i].x, 0.0001f)
            assertEquals("Default FitProfile Key $i Y mismatch", SkyProfile.baseKeyCenters[i].y, defaultFitProfile.keyCenters[i].y, 0.0001f)
        }
    }

    @Test
    fun `SkyProfile defaultMidiNotes matches fixed expected notes exactly`() {
        // リファクタリング前の既存コード (MidiKeyMapper.DEFAULT_RESOLVED_MAPPING) で定義されていた基準MIDIノートリスト
        val expectedMidiNotes = listOf(
            60, 62, 64, 65, 67,
            69, 71, 72, 74, 76,
            77, 79, 81, 83, 84
        )

        // 1. 固定期待値と SkyProfile.defaultMidiNotes の比較（非循環参照検証）
        assertEquals(expectedMidiNotes, SkyProfile.defaultMidiNotes)

        // 2. SkyProfile.defaultMidiNotes と MidiKeyMapper.DEFAULT_RESOLVED_MAPPING.midiNotes の参照一致検証
        assertEquals(SkyProfile.defaultMidiNotes, MidiKeyMapper.DEFAULT_RESOLVED_MAPPING.midiNotes)
    }

    @Test
    fun `FitProfile KEY_COUNT legacy constant stays in sync with SkyProfile keyCount`() {
        assertEquals(SkyProfile.keyCount, FitProfile.KEY_COUNT)
    }

    @Test
    fun `getRow and getColumn match existing FallingNoteCalculator behavior for all valid keys`() {
        for (key in 0 until SkyProfile.keyCount) {
            val expectedRow = (key.coerceIn(0, 14)) / 5
            val expectedCol = (key.coerceIn(0, 14)) % 5

            assertEquals("Key $key row mismatch", expectedRow, SkyProfile.getRow(key))
            assertEquals("Key $key col mismatch", expectedCol, SkyProfile.getColumn(key))

            // FallingNoteCalculator からの委譲呼び出しの一致検証
            assertEquals("FallingNoteCalculator row mismatch for key $key", expectedRow, FallingNoteCalculator.getRow(key))
            assertEquals("FallingNoteCalculator col mismatch for key $key", expectedCol, FallingNoteCalculator.getColumn(key))
        }
    }

    @Test
    fun `getRow and getColumn handle out of bounds keys identically to legacy behavior`() {
        val outOfBoundsKeys = listOf(-100, -1, 15, 16, 50, 100)

        for (key in outOfBoundsKeys) {
            val legacyExpectedRow = (key.coerceIn(0, 14)) / 5
            val legacyExpectedCol = (key.coerceIn(0, 14)) % 5

            assertEquals("Out-of-bounds key $key row mismatch", legacyExpectedRow, SkyProfile.getRow(key))
            assertEquals("Out-of-bounds key $key col mismatch", legacyExpectedCol, SkyProfile.getColumn(key))

            assertEquals("FallingNoteCalculator out-of-bounds row mismatch for key $key", legacyExpectedRow, FallingNoteCalculator.getRow(key))
            assertEquals("FallingNoteCalculator out-of-bounds col mismatch for key $key", legacyExpectedCol, FallingNoteCalculator.getColumn(key))
        }
    }
}
