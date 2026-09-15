package com.onigiri.keycue.overlay

import com.onigiri.keycue.fitting.Corner
import com.onigiri.keycue.model.NormalizedPoint
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * [FittingCornerController] の単体テスト。
 *
 * 矩形連動モード（TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT）および
 * 4点個別調整モード（他Cornerが動かないこと）を検証する。
 */
class FittingCornerControllerTest {

    private lateinit var controller: FittingCornerController

    @Before
    fun setUp() {
        controller = FittingCornerController(
            initialTopLeft = NormalizedPoint(0.20f, 0.60f),
            initialTopRight = NormalizedPoint(0.80f, 0.60f),
            initialBottomLeft = NormalizedPoint(0.20f, 0.90f),
            initialBottomRight = NormalizedPoint(0.80f, 0.90f),
            isIndividualMode = false
        )
    }

    @Test
    fun moveCornerDelta_linkedMode_topLeft_movesBottomLeftX_and_topRightY() {
        // TOP_LEFT を dx=+0.05f, dy=+0.04f 移動
        controller.moveCornerDelta(Corner.TOP_LEFT, 0.05f, 0.04f)

        // TOP_LEFT は (0.25, 0.64)
        assertEquals(0.25f, controller.topLeft.x, 0.001f)
        assertEquals(0.64f, controller.topLeft.y, 0.001f)

        // BOTTOM_LEFT は X が連動して 0.25、Y は元の 0.90 のまま
        assertEquals(0.25f, controller.bottomLeft.x, 0.001f)
        assertEquals(0.90f, controller.bottomLeft.y, 0.001f)

        // TOP_RIGHT は X は元の 0.80、Y が連動して 0.64
        assertEquals(0.80f, controller.topRight.x, 0.001f)
        assertEquals(0.64f, controller.topRight.y, 0.001f)

        // BOTTOM_RIGHT は変わらず (0.80, 0.90)
        assertEquals(0.80f, controller.bottomRight.x, 0.001f)
        assertEquals(0.90f, controller.bottomRight.y, 0.001f)
    }

    @Test
    fun moveCornerDelta_linkedMode_topRight_movesBottomRightX_and_topLeftY() {
        // TOP_RIGHT を dx=-0.05f, dy=-0.02f 移動
        controller.moveCornerDelta(Corner.TOP_RIGHT, -0.05f, -0.02f)

        // TOP_RIGHT は (0.75, 0.58)
        assertEquals(0.75f, controller.topRight.x, 0.001f)
        assertEquals(0.58f, controller.topRight.y, 0.001f)

        // BOTTOM_RIGHT は X が連動して 0.75、Y は元の 0.90 のまま
        assertEquals(0.75f, controller.bottomRight.x, 0.001f)
        assertEquals(0.90f, controller.bottomRight.y, 0.001f)

        // TOP_LEFT は X は元の 0.20、Y が連動して 0.58
        assertEquals(0.20f, controller.topLeft.x, 0.001f)
        assertEquals(0.58f, controller.topLeft.y, 0.001f)

        // BOTTOM_LEFT は変わらず (0.20, 0.90)
        assertEquals(0.20f, controller.bottomLeft.x, 0.001f)
        assertEquals(0.90f, controller.bottomLeft.y, 0.001f)
    }

    @Test
    fun moveCornerDelta_linkedMode_bottomLeft_movesTopLeftX_and_bottomRightY() {
        // BOTTOM_LEFT を dx=+0.02f, dy=-0.05f 移動
        controller.moveCornerDelta(Corner.BOTTOM_LEFT, 0.02f, -0.05f)

        // BOTTOM_LEFT は (0.22, 0.85)
        assertEquals(0.22f, controller.bottomLeft.x, 0.001f)
        assertEquals(0.85f, controller.bottomLeft.y, 0.001f)

        // TOP_LEFT は X が連動して 0.22、Y は元の 0.60
        assertEquals(0.22f, controller.topLeft.x, 0.001f)
        assertEquals(0.60f, controller.topLeft.y, 0.001f)

        // BOTTOM_RIGHT は X は元の 0.80、Y が連動して 0.85
        assertEquals(0.80f, controller.bottomRight.x, 0.001f)
        assertEquals(0.85f, controller.bottomRight.y, 0.001f)

        // TOP_RIGHT は変わらず (0.80, 0.60)
        assertEquals(0.80f, controller.topRight.x, 0.001f)
        assertEquals(0.60f, controller.topRight.y, 0.001f)
    }

    @Test
    fun moveCornerDelta_linkedMode_bottomRight_movesTopRightX_and_bottomLeftY() {
        // BOTTOM_RIGHT を dx=+0.04f, dy=+0.03f 移動
        controller.moveCornerDelta(Corner.BOTTOM_RIGHT, 0.04f, 0.03f)

        // BOTTOM_RIGHT は (0.84, 0.93)
        assertEquals(0.84f, controller.bottomRight.x, 0.001f)
        assertEquals(0.93f, controller.bottomRight.y, 0.001f)

        // TOP_RIGHT は X が連動して 0.84、Y は元の 0.60
        assertEquals(0.84f, controller.topRight.x, 0.001f)
        assertEquals(0.60f, controller.topRight.y, 0.001f)

        // BOTTOM_LEFT は X は元の 0.20、Y が連動して 0.93
        assertEquals(0.20f, controller.bottomLeft.x, 0.001f)
        assertEquals(0.93f, controller.bottomLeft.y, 0.001f)

        // TOP_LEFT は変わらず (0.20, 0.60)
        assertEquals(0.20f, controller.topLeft.x, 0.001f)
        assertEquals(0.60f, controller.topLeft.y, 0.001f)
    }

    @Test
    fun moveCornerDelta_individualMode_onlySelectedCornerMoves() {
        controller.isIndividualMode = true

        // TOP_LEFT のみ移動
        controller.moveCornerDelta(Corner.TOP_LEFT, 0.05f, 0.05f)

        // TOP_LEFT のみ変化
        assertEquals(0.25f, controller.topLeft.x, 0.001f)
        assertEquals(0.65f, controller.topLeft.y, 0.001f)

        // 他の3角は一切動かない
        assertEquals(0.80f, controller.topRight.x, 0.001f)
        assertEquals(0.60f, controller.topRight.y, 0.001f)

        assertEquals(0.20f, controller.bottomLeft.x, 0.001f)
        assertEquals(0.90f, controller.bottomLeft.y, 0.001f)

        assertEquals(0.80f, controller.bottomRight.x, 0.001f)
        assertEquals(0.90f, controller.bottomRight.y, 0.001f)
    }
}
