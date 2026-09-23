package com.onigiri.keycue.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [PlaybackConfig] の正規化ロジックおよび Note / Circle 先読み時間の完全独立化を検証する単体テスト。
 */
class PlaybackConfigTest {

    @Test
    fun normalizeLeadTimes_normalRangesWithinBounds_remainsUnchanged() {
        val (note, circle) = PlaybackConfig.normalizeLeadTimes(
            noteLeadTimeMs = 1000L,
            approachCircleLeadTimeMs = 500L
        )
        assertEquals(1000L, note)
        assertEquals(500L, circle)
    }

    @Test
    fun normalizeLeadTimes_circleGreaterThanNote_allowsCircleToExceedNote() {
        // Circle Lead Time > Note Lead Time が制限されずにそのまま許容されること
        val (note, circle) = PlaybackConfig.normalizeLeadTimes(
            noteLeadTimeMs = 300L,
            approachCircleLeadTimeMs = 2000L
        )
        assertEquals(300L, note)
        assertEquals(2000L, circle)
    }

    @Test
    fun normalizeLeadTimes_belowMinimum_clampsToMinimums() {
        val (note, circle) = PlaybackConfig.normalizeLeadTimes(
            noteLeadTimeMs = 0L,
            approachCircleLeadTimeMs = 50L
        )
        assertEquals(PlaybackConfig.MIN_NOTE_LEAD_TIME_MS, note) // 300L
        assertEquals(PlaybackConfig.MIN_APPROACH_CIRCLE_LEAD_TIME_MS, circle) // 100L
    }

    @Test
    fun normalizeLeadTimes_aboveMaximum_clampsToMaximums() {
        val (note, circle) = PlaybackConfig.normalizeLeadTimes(
            noteLeadTimeMs = 5000L,
            approachCircleLeadTimeMs = 5000L
        )
        assertEquals(PlaybackConfig.MAX_NOTE_LEAD_TIME_MS, note) // 2000L
        assertEquals(PlaybackConfig.MAX_APPROACH_CIRCLE_LEAD_TIME_MS, circle) // 2000L
    }

    @Test
    fun normalizeLeadTimes_exactBoundaries_verified() {
        // 最小境界値
        val (minNote, minCircle) = PlaybackConfig.normalizeLeadTimes(300L, 100L)
        assertEquals(300L, minNote)
        assertEquals(100L, minCircle)

        // 最大境界値
        val (maxNote, maxCircle) = PlaybackConfig.normalizeLeadTimes(2000L, 2000L)
        assertEquals(2000L, maxNote)
        assertEquals(2000L, maxCircle)
    }

    @Test
    fun normalize_bulkConfig_normalizesAllFieldsIndependently() {
        val config = PlaybackConfig.normalize(
            speed = 3.0f,
            noteLeadTimeMs = 400L,
            approachCircleLeadTimeMs = 1800L,
            countdownMs = 10000L
        )
        assertEquals(PlaybackConfig.MAX_SPEED, config.speed) // 2.0f
        assertEquals(400L, config.noteLeadTimeMs)
        assertEquals(1800L, config.approachCircleLeadTimeMs)
        assertEquals(PlaybackConfig.MAX_COUNTDOWN_MS, config.countdownMs) // 5000L
    }

    @Test
    fun normalized_extensionMethod_preservesIndependentValues() {
        val raw = PlaybackConfig(
            speed = 1.0f,
            noteLeadTimeMs = 500L,
            approachCircleLeadTimeMs = 1500L,
            countdownMs = 3000L
        )
        val normalized = raw.normalized()
        assertEquals(500L, normalized.noteLeadTimeMs)
        assertEquals(1500L, normalized.approachCircleLeadTimeMs)
    }

    @Test
    fun stepConstants_definedConsistently() {
        assertEquals(100L, PlaybackConfig.NOTE_LEAD_TIME_STEP_MS)
        assertEquals(50L, PlaybackConfig.APPROACH_CIRCLE_LEAD_TIME_STEP_MS)
    }

    @Test
    fun countdownPresets_definedConsistently() {
        assertEquals(listOf(0L, 1000L, 3000L, 5000L), PlaybackConfig.COUNTDOWN_PRESETS_MS)
    }
}
