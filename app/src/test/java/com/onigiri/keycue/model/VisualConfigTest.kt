package com.onigiri.keycue.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class VisualConfigTest {

    @Test
    fun `default values match specifications`() {
        val config = VisualConfig()
        assertEquals(2.0f, config.chordStrokeWidthDp, 0.001f)
        assertEquals(60, config.chordStrokeAlphaPercent)
        assertEquals(10, config.chordHaloFillAlphaPercent)
    }

    @Test
    fun `safe clamps invalid chordStrokeWidthDp`() {
        // 下限未満 (-1.0f) -> MIN (0.5f)
        val clampedMin = VisualConfig.safe(chordStrokeWidthDp = -1.0f)
        assertEquals(0.5f, clampedMin.chordStrokeWidthDp, 0.001f)

        // 上限超え (100.0f) -> MAX (6.0f)
        val clampedMax = VisualConfig.safe(chordStrokeWidthDp = 100.0f)
        assertEquals(6.0f, clampedMax.chordStrokeWidthDp, 0.001f)

        // 正常値 (2.5f) -> 2.5f 維持
        val normal = VisualConfig.safe(chordStrokeWidthDp = 2.5f)
        assertEquals(2.5f, normal.chordStrokeWidthDp, 0.001f)
    }

    @Test
    fun `safe clamps invalid chordStrokeAlphaPercent`() {
        // 下限未満 (0%) -> MIN (20%)
        val clampedMin = VisualConfig.safe(chordStrokeAlphaPercent = 0)
        assertEquals(20, clampedMin.chordStrokeAlphaPercent)

        // 上限超え (200%) -> MAX (100%)
        val clampedMax = VisualConfig.safe(chordStrokeAlphaPercent = 200)
        assertEquals(100, clampedMax.chordStrokeAlphaPercent)

        // 正常値 (80%) -> 80% 維持
        val normal = VisualConfig.safe(chordStrokeAlphaPercent = 80)
        assertEquals(80, normal.chordStrokeAlphaPercent)
    }

    @Test
    fun `safe clamps invalid chordHaloFillAlphaPercent`() {
        // 0% -> MIN (10%)
        assertEquals(10, VisualConfig.safe(chordHaloFillAlphaPercent = 0).chordHaloFillAlphaPercent)

        // 5% -> MIN (10%)
        assertEquals(10, VisualConfig.safe(chordHaloFillAlphaPercent = 5).chordHaloFillAlphaPercent)

        // 10% -> 10% 維持
        assertEquals(10, VisualConfig.safe(chordHaloFillAlphaPercent = 10).chordHaloFillAlphaPercent)

        // 25% -> 25% 維持
        assertEquals(25, VisualConfig.safe(chordHaloFillAlphaPercent = 25).chordHaloFillAlphaPercent)

        // 50% -> 50% 維持
        assertEquals(50, VisualConfig.safe(chordHaloFillAlphaPercent = 50).chordHaloFillAlphaPercent)

        // 100% -> MAX (50%)
        assertEquals(50, VisualConfig.safe(chordHaloFillAlphaPercent = 100).chordHaloFillAlphaPercent)
    }

    @Test
    fun `constructor throws on invalid values`() {
        assertThrows(IllegalArgumentException::class.java) {
            VisualConfig(chordStrokeWidthDp = 0.4f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            VisualConfig(chordStrokeWidthDp = 6.1f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            VisualConfig(chordStrokeAlphaPercent = 19)
        }
        assertThrows(IllegalArgumentException::class.java) {
            VisualConfig(chordStrokeAlphaPercent = 101)
        }
        assertThrows(IllegalArgumentException::class.java) {
            VisualConfig(chordHaloFillAlphaPercent = 9)
        }
        assertThrows(IllegalArgumentException::class.java) {
            VisualConfig(chordHaloFillAlphaPercent = 51)
        }
    }
}

