package com.onigiri.keycue.overlay

import com.onigiri.keycue.model.PlaybackConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * [ControlOverlayView] における再生速度プリセットおよび増減計算ロジックの単体テスト。
 */
class ControlOverlaySpeedTest {

    @Test
    fun speedPresets_coversEntireConfiguredRange() {
        val presets = ControlOverlayView.SPEED_PRESETS

        // 最小値が PlaybackConfig.MIN_SPEED (0.25f) に完全に一致すること
        assertEquals(PlaybackConfig.MIN_SPEED, presets.first(), 0.001f)

        // 最大値が PlaybackConfig.MAX_SPEED (2.0f) に完全に一致すること
        assertEquals(PlaybackConfig.MAX_SPEED, presets.last(), 0.001f)

        // 昇順に並んでいること
        for (i in 0 until presets.size - 1) {
            assertTrue(
                "presets should be sorted ascending: ${presets[i]} < ${presets[i + 1]}",
                presets[i] < presets[i + 1]
            )
        }
    }

    @Test
    fun calculateNextSpeed_increasesThroughFullRangeToMax() {
        var speed = 1.00f

        val expectedSteps = listOf(1.25f, 1.50f, 1.75f, 2.00f)
        for (expected in expectedSteps) {
            speed = ControlOverlayView.calculateNextSpeed(speed, direction = +1)
            assertEquals(expected, speed, 0.001f)
        }

        // 上限 (2.00f) での +1 は 2.00f のまま維持されること
        val clamped = ControlOverlayView.calculateNextSpeed(speed, direction = +1)
        assertEquals(PlaybackConfig.MAX_SPEED, clamped, 0.001f)
    }

    @Test
    fun calculateNextSpeed_decreasesThroughFullRangeToMin() {
        var speed = 1.00f

        val expectedSteps = listOf(0.75f, 0.50f, 0.25f)
        for (expected in expectedSteps) {
            speed = ControlOverlayView.calculateNextSpeed(speed, direction = -1)
            assertEquals(expected, speed, 0.001f)
        }

        // 下限 (0.25f) での -1 は 0.25f のまま維持されること
        val clamped = ControlOverlayView.calculateNextSpeed(speed, direction = -1)
        assertEquals(PlaybackConfig.MIN_SPEED, clamped, 0.001f)
    }

    @Test
    fun calculateNextSpeed_snapsNonPresetValuesCorrectly() {
        // 0.85f (プリセット外) から +1 すると上位直近 (1.00f) へ
        val nextUp = ControlOverlayView.calculateNextSpeed(0.85f, direction = +1)
        assertEquals(1.00f, nextUp, 0.001f)

        // 0.85f (プリセット外) から -1 すると下位直近 (0.75f) へ
        val nextDown = ControlOverlayView.calculateNextSpeed(0.85f, direction = -1)
        assertEquals(0.75f, nextDown, 0.001f)

        // 下限未満 (例: 0.10f) から +1 すると 0.25f へ
        val fromBelow = ControlOverlayView.calculateNextSpeed(0.10f, direction = +1)
        assertEquals(0.25f, fromBelow, 0.001f)

        // 上限超過 (例: 2.50f) から -1 すると 2.00f へ
        val fromAbove = ControlOverlayView.calculateNextSpeed(2.50f, direction = -1)
        assertEquals(2.00f, fromAbove, 0.001f)
    }

    @Test
    fun generateSpeedPresets_adaptsToArbitraryMinMax() {
        val customPresets = ControlOverlayView.generateSpeedPresets(
            minSpeed = 0.50f,
            maxSpeed = 1.50f,
            step = 0.25f
        )
        val expected = listOf(0.50f, 0.75f, 1.00f, 1.25f, 1.50f)
        assertEquals(expected.size, customPresets.size)
        for (i in expected.indices) {
            assertEquals(expected[i], customPresets[i], 0.001f)
        }
    }
}
