package com.onigiri.keycue.overlay

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [ControlOverlayView.calculateSnapTargetX] のスナップ計算単体テスト。
 */
class ControlOverlaySnapTest {

    @Test
    fun `画面中央より左にある場合は左端0かつLEFTにスナップする`() {
        val screenWidth = 1080
        val viewWidth = 150
        val currentX = 100

        val (targetX, edge) = ControlOverlayView.calculateSnapTargetX(
            currentX = currentX,
            viewWidth = viewWidth,
            screenWidth = screenWidth
        )

        assertEquals(0, targetX)
        assertEquals(SnapEdge.LEFT, edge)
    }

    @Test
    fun `画面中央より右にある場合は右端maxXかつRIGHTにスナップする`() {
        val screenWidth = 1080
        val viewWidth = 150
        val maxX = screenWidth - viewWidth // 930
        val currentX = 800

        val (targetX, edge) = ControlOverlayView.calculateSnapTargetX(
            currentX = currentX,
            viewWidth = viewWidth,
            screenWidth = screenWidth
        )

        assertEquals(maxX, targetX)
        assertEquals(SnapEdge.RIGHT, edge)
    }

    @Test
    fun `バブルの中心が画面中央ちょうどにある場合はLEFTにスナップする`() {
        // screenWidth = 1000, viewWidth = 100, screenCenterX = 500
        // centerX = currentX + 50 = 500 => currentX = 450
        val screenWidth = 1000
        val viewWidth = 100
        val currentX = 450

        val (targetX, edge) = ControlOverlayView.calculateSnapTargetX(
            currentX = currentX,
            viewWidth = viewWidth,
            screenWidth = screenWidth
        )

        assertEquals(0, targetX)
        assertEquals(SnapEdge.LEFT, edge)
    }

    @Test
    fun `バブルの中心が画面中央をわずかに超えた場合はRIGHTにスナップする`() {
        // currentX = 451 => centerX = 501 > 500
        val screenWidth = 1000
        val viewWidth = 100
        val maxX = screenWidth - viewWidth // 900
        val currentX = 451

        val (targetX, edge) = ControlOverlayView.calculateSnapTargetX(
            currentX = currentX,
            viewWidth = viewWidth,
            screenWidth = screenWidth
        )

        assertEquals(maxX, targetX)
        assertEquals(SnapEdge.RIGHT, edge)
    }

    @Test
    fun `すでに左端にある場合は左端0かつLEFTを維持する`() {
        val (targetX, edge) = ControlOverlayView.calculateSnapTargetX(
            currentX = 0,
            viewWidth = 150,
            screenWidth = 1080
        )

        assertEquals(0, targetX)
        assertEquals(SnapEdge.LEFT, edge)
    }

    @Test
    fun `すでに右端にある場合は右端maxXかつRIGHTを維持する`() {
        val screenWidth = 1080
        val viewWidth = 150
        val maxX = screenWidth - viewWidth

        val (targetX, edge) = ControlOverlayView.calculateSnapTargetX(
            currentX = maxX,
            viewWidth = viewWidth,
            screenWidth = screenWidth
        )

        assertEquals(maxX, targetX)
        assertEquals(SnapEdge.RIGHT, edge)
    }
}
