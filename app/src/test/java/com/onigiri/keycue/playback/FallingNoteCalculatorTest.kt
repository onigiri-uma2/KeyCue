package com.onigiri.keycue.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [FallingNoteCalculator] の単体テスト。
 *
 * 以下の計算・判定ロジックを検証します:
 * 1. 落下進行度（progress）の線形計算
 * 2. 先読み時間（leadTime）前の非表示制御
 * 3. イベント時刻における progress ≒ 1.0 の整合性
 * 4. キーインデックスから列（column = key % 5）および段（row: 上/中/下段）の特定
 * 5. 事前ハイライト時間範囲の判定
 * 6. ジャストタイミング判定（前後許容幅）
 */
class FallingNoteCalculatorTest {

    @Test
    fun test1_calculateProgress_linearProgression() {
        val eventTime = 1000L
        val leadTime = 700L

        // 1. 出現境界 (currentTime = 300ms -> remaining = 700ms -> progress = 0.0)
        val progressAtStart = FallingNoteCalculator.calculateProgress(eventTime, 300L, leadTime)
        assertEquals(0.0f, progressAtStart, 0.001f)

        // 2. 中間 (currentTime = 650ms -> remaining = 350ms -> progress = 0.5)
        val progressHalfway = FallingNoteCalculator.calculateProgress(eventTime, 650L, leadTime)
        assertEquals(0.5f, progressHalfway, 0.001f)

        // 3. ジャスト (currentTime = 1000ms -> remaining = 0ms -> progress = 1.0)
        val progressAtJust = FallingNoteCalculator.calculateProgress(eventTime, 1000L, leadTime)
        assertEquals(1.0f, progressAtJust, 0.001f)
    }

    @Test
    fun test2_shouldDraw_hiddenBeforeLeadTime_andAfterOvershoot() {
        // 出現前 (progress < 0.0)
        val progressBefore = -0.1f
        assertFalse(FallingNoteCalculator.shouldDraw(progressBefore))

        // 出現時 (progress = 0.0)
        assertTrue(FallingNoteCalculator.shouldDraw(0.0f))

        // 進行中 (progress = 0.5)
        assertTrue(FallingNoteCalculator.shouldDraw(0.5f))

        // ジャスト時 (progress = 1.0)
        assertTrue(FallingNoteCalculator.shouldDraw(1.0f))

        // ジャスト直後 (progress = 1.04) -> 表示許容
        assertTrue(FallingNoteCalculator.shouldDraw(1.04f))

        // 通過完了後 (progress = 1.15) -> 非表示
        assertFalse(FallingNoteCalculator.shouldDraw(1.15f))
    }

    @Test
    fun test3_progressAtEventTime_isApproximatelyOne() {
        val eventTimes = listOf(500L, 1234L, 99999L)
        val leadTime = 800L

        for (time in eventTimes) {
            val progress = FallingNoteCalculator.calculateProgress(
                eventTimeMs = time,
                currentTimeMs = time,
                leadTimeMs = leadTime
            )
            assertEquals(1.0f, progress, 0.0001f)
        }
    }

    @Test
    fun test4_column_isKeyModulo5() {
        for (key in 0..14) {
            val expectedCol = key % 5
            assertEquals("Key $key column check", expectedCol, FallingNoteCalculator.getColumn(key))
        }
    }

    @Test
    fun test5_row_determinesShapeStage() {
        // 上段 (Row 0 -> ○): key 0..4
        for (key in 0..4) {
            assertEquals("Key $key row check", 0, FallingNoteCalculator.getRow(key))
        }

        // 中段 (Row 1 -> □): key 5..9
        for (key in 5..9) {
            assertEquals("Key $key row check", 1, FallingNoteCalculator.getRow(key))
        }

        // 下段 (Row 2 -> △): key 10..14
        for (key in 10..14) {
            assertEquals("Key $key row check", 2, FallingNoteCalculator.getRow(key))
        }
    }

    @Test
    fun test6_isHighlighted_boundaries() {
        val eventTime = 1000L
        val highlightMs = 300L // 範囲: 700ms .. 1050ms

        // 700ms より前 (699ms) -> ハイライト外
        assertFalse(FallingNoteCalculator.isHighlighted(eventTime, 699L, highlightMs))

        // 700ms ちょうど -> ハイライト対象
        assertTrue(FallingNoteCalculator.isHighlighted(eventTime, 700L, highlightMs))

        // 850ms -> ハイライト対象
        assertTrue(FallingNoteCalculator.isHighlighted(eventTime, 850L, highlightMs))

        // 1000ms (ジャスト) -> ハイライト対象
        assertTrue(FallingNoteCalculator.isHighlighted(eventTime, 1000L, highlightMs))

        // 1050ms (通過直後余韻) -> ハイライト対象
        assertTrue(FallingNoteCalculator.isHighlighted(eventTime, 1050L, highlightMs))

        // 1051ms -> ハイライト終了
        assertFalse(FallingNoteCalculator.isHighlighted(eventTime, 1051L, highlightMs))
    }

    @Test
    fun test7_isJustTiming_thresholdRange() {
        val eventTime = 1000L
        val threshold = 80L // 範囲: 920ms .. 1080ms

        // 範囲外 (919ms)
        assertFalse(FallingNoteCalculator.isJustTiming(eventTime, 919L, threshold))

        // 範囲内 (920ms)
        assertTrue(FallingNoteCalculator.isJustTiming(eventTime, 920L, threshold))

        // ジャスト (1000ms)
        assertTrue(FallingNoteCalculator.isJustTiming(eventTime, 1000L, threshold))

        // 範囲内 (1080ms)
        assertTrue(FallingNoteCalculator.isJustTiming(eventTime, 1080L, threshold))

        // 範囲外 (1081ms)
        assertFalse(FallingNoteCalculator.isJustTiming(eventTime, 1081L, threshold))
    }

    @Test
    fun test_calculateY_linearInterpolation() {
        val targetY = 800f
        val fallDistance = 300f
        // startY = 800 - 300 = 500f

        // progress = 0.0 -> 500f
        assertEquals(500f, FallingNoteCalculator.calculateY(targetY, fallDistance, 0.0f), 0.01f)

        // progress = 0.5 -> 650f
        assertEquals(650f, FallingNoteCalculator.calculateY(targetY, fallDistance, 0.5f), 0.01f)

        // progress = 1.0 -> 800f
        assertEquals(800f, FallingNoteCalculator.calculateY(targetY, fallDistance, 1.0f), 0.01f)
    }
}
