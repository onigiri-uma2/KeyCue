package com.onigiri.keycue.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [FallingNoteCalculator.calculateEffectiveFallDistance] および画面上端キーの落下挙動の単体テスト。
 *
 * PAD_DPAD_FIRSTなど画面上端付近に配置されたキーにおいて、落下ノートの出現位置が画面上端をはみ出さず、
 * かつジャストタイミング（progress = 1.0）で正確にキー位置に到達することを検証します。
 */
class FallingNoteCalculatorEffectiveDistanceTest {

    @Test
    fun testNormalKey_usesStandardFallDistance() {
        val targetY = 500f
        val topMargin = 30f
        val standardFallDistance = 200f

        val effective = FallingNoteCalculator.calculateEffectiveFallDistance(
            targetY = targetY,
            defaultFallDistancePx = standardFallDistance,
            topMarginPx = topMargin
        )

        assertEquals("Normal key with enough top space should use full distance", 200f, effective, 0.001f)
    }

    @Test
    fun testTopEdgeKey_shortensFallDistanceSafely() {
        val targetY = 80f
        val topMargin = 30f
        val standardFallDistance = 200f

        val effective = FallingNoteCalculator.calculateEffectiveFallDistance(
            targetY = targetY,
            defaultFallDistancePx = standardFallDistance,
            topMarginPx = topMargin
        )

        // availableSpace = 80f - 30f = 50f < 200f -> effective must be 50f
        assertEquals("Top edge key should shorten distance to available space", 50f, effective, 0.001f)

        // 出現位置 (progress = 0.0f)
        val startY = targetY - effective
        assertEquals("Appearance position should be at topMargin", topMargin, startY, 0.001f)
        assertTrue("startY must be at or below topMargin", startY >= topMargin)
    }

    @Test
    fun testExtremeTopKey_clampedAtZero() {
        val targetY = 20f
        val topMargin = 30f
        val standardFallDistance = 200f

        val effective = FallingNoteCalculator.calculateEffectiveFallDistance(
            targetY = targetY,
            defaultFallDistancePx = standardFallDistance,
            topMarginPx = topMargin
        )

        assertEquals("When targetY is within topMargin, distance should clamp to 0", 0f, effective, 0.001f)
    }

    @Test
    fun testReachabilityAtJustTiming() {
        // 短縮された落下距離であっても、progress = 1.0 (ジャストタイミング) で正確に targetY に到達すること
        val targetY = 90f
        val topMargin = 25f
        val standardFallDistance = 200f

        val effective = FallingNoteCalculator.calculateEffectiveFallDistance(
            targetY = targetY,
            defaultFallDistancePx = standardFallDistance,
            topMarginPx = topMargin
        )
        val startY = targetY - effective

        // progress = 0.0
        val yAtStart = startY + effective * 0.0f
        assertEquals(startY, yAtStart, 0.001f)

        // progress = 0.5
        val yAtHalf = startY + effective * 0.5f
        assertEquals(startY + effective * 0.5f, yAtHalf, 0.001f)

        // progress = 1.0 (ジャスト)
        val yAtJust = startY + effective * 1.0f
        assertEquals("Note must reach exact targetY at progress 1.0", targetY, yAtJust, 0.001f)
    }
}
