package com.onigiri.keycue.fitting

import com.onigiri.keycue.model.FitProfile
import com.onigiri.keycue.model.NormalizedPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class GridFitterTest {

    private val fitter = GridFitter()

    // 理想的な15点のテスト用ピクセルグリッド生成ヘルパー
    // 画面サイズ: 1920 x 1080 (Landscape)
    private fun createIdealPoints(
        width: Int = 1920,
        height: Int = 1080,
        startX: Float = 400f,
        startY: Float = 600f,
        dx: Float = 250f,
        dy: Float = 150f,
        radius: Float = 45f
    ): List<DetectedPoint> {
        val points = mutableListOf<DetectedPoint>()
        for (r in 0 until 3) {
            val y = startY + r * dy
            for (c in 0 until 5) {
                val x = startX + c * dx
                points.add(DetectedPoint(x = x, y = y, radius = radius))
            }
        }
        return points
    }

    @Test
    fun `1 Perfect 15 points fitting test`() {
        val points = createIdealPoints()
        val result = fitter.fit(points, 1920, 1080)

        assertNotNull(result.profile)
        val profile = result.profile!!
        assertEquals(FitProfile.KEY_COUNT, profile.keyCenters.size)
        assertTrue("Landscape should be true", profile.landscape)
        assertTrue("Confidence should be high for ideal points (>= 0.90)", result.confidence >= 0.90f)

        // 順序の検証 (0..4: 上段, 5..9: 中段, 10..14: 下段)
        // 上段のY < 中段のY < 下段のY
        assertTrue(profile.keyCenters[0].y < profile.keyCenters[5].y)
        assertTrue(profile.keyCenters[5].y < profile.keyCenters[10].y)

        // 各段で左から右へXが増加
        for (r in 0 until 3) {
            val base = r * 5
            for (c in 0 until 4) {
                assertTrue(
                    "X must increase along column: row $r, col $c",
                    profile.keyCenters[base + c].x < profile.keyCenters[base + c + 1].x
                )
            }
        }
    }

    @Test
    fun `2 Missing points fitting test`() {
        // 15点中4点欠損（11点検出）
        // 欠損点: key 1, 5, 8, 14
        val fullPoints = createIdealPoints()
        val missingIndices = setOf(1, 5, 8, 14)
        val partialPoints = fullPoints.filterIndexed { index, _ -> index !in missingIndices }

        assertEquals(11, partialPoints.size)

        val result = fitter.fit(partialPoints, 1920, 1080)
        assertNotNull("Grid should be successfully estimated even with missing points", result.profile)
        assertEquals(FitProfile.KEY_COUNT, result.profile!!.keyCenters.size)
        assertTrue("Confidence should still be reasonably high (>= 0.60)", result.confidence >= 0.60f)

        // 欠損したキー位置も正しく補完されているか（元の理想位置と近接しているか）
        val fullProfile = fitter.fit(fullPoints, 1920, 1080).profile!!
        for (i in 0 until FitProfile.KEY_COUNT) {
            val estimated = result.profile!!.keyCenters[i]
            val ideal = fullProfile.keyCenters[i]
            assertEquals("Key $i X should match ideal", ideal.x, estimated.x, 0.02f)
            assertEquals("Key $i Y should match ideal", ideal.y, estimated.y, 0.02f)
        }
    }

    @Test
    fun `3 Noise points robustness test`() {
        val points = createIdealPoints().toMutableList()
        // ランダムな外れ値・背景ノイズを4点追加
        points.add(DetectedPoint(x = 100f, y = 200f, radius = 30f))
        points.add(DetectedPoint(x = 1700f, y = 150f, radius = 50f))
        points.add(DetectedPoint(x = 250f, y = 900f, radius = 40f))
        points.add(DetectedPoint(x = 1800f, y = 950f, radius = 35f))

        val result = fitter.fit(points, 1920, 1080)
        assertNotNull("Fitting should succeed despite background noise", result.profile)
        assertTrue("Confidence should still be high (>= 0.80)", result.confidence >= 0.80f)
    }

    @Test
    fun `4 Slightly shifted jittered grid test`() {
        val random = Random(42)
        val jitteredPoints = createIdealPoints().map {
            DetectedPoint(
                x = it.x + random.nextFloat() * 10f - 5f,
                y = it.y + random.nextFloat() * 10f - 5f,
                radius = it.radius
            )
        }

        val result = fitter.fit(jitteredPoints, 1920, 1080)
        assertNotNull("Fitting should handle slight jitter", result.profile)
        assertTrue(result.confidence >= 0.85f)
    }

    @Test
    fun `5 Horizontal scale difference test`() {
        // 横ピッチを大きくしたグリッド (dx = 300f)
        val widePoints = createIdealPoints(startX = 300f, dx = 300f)
        val result = fitter.fit(widePoints, 1920, 1080)

        assertNotNull("Fitting should adapt to wider horizontal spacing", result.profile)
        assertTrue(result.confidence >= 0.85f)
    }

    @Test
    fun `6 Vertical scale difference test`() {
        // 縦ピッチを狭くしたグリッド (dy = 100f)
        val narrowYPoints = createIdealPoints(startY = 700f, dy = 100f)
        val result = fitter.fit(narrowYPoints, 1920, 1080)

        assertNotNull("Fitting should adapt to different vertical spacing", result.profile)
        assertTrue(result.confidence >= 0.85f)
    }

    @Test
    fun `7 Insufficient candidate count test`() {
        // 3点のみ（最低必要数4点未満）
        val fewPoints = listOf(
            DetectedPoint(x = 100f, y = 100f),
            DetectedPoint(x = 200f, y = 100f),
            DetectedPoint(x = 300f, y = 100f)
        )

        val result = fitter.fit(fewPoints, 1920, 1080)
        assertNull("Fitting must fail when candidates < 4", result.profile)
        assertEquals(0f, result.confidence, 0.001f)
        assertNotNull(result.errorMessage)
    }

    @Test
    fun `8 Confidence calculation evaluation test`() {
        val perfect = fitter.fit(createIdealPoints(), 1920, 1080)
        val partial = fitter.fit(createIdealPoints().drop(5), 1920, 1080) // 10点

        assertTrue("Perfect grid confidence should be >= 0.90", perfect.confidence >= 0.90f)
        assertTrue("Partial grid confidence should be less than perfect", partial.confidence < perfect.confidence)
        assertTrue("Confidence must be in 0..1 range", perfect.confidence in 0f..1f)
    }

    @Test
    fun `9 Normalized coordinate conversion test`() {
        val result = fitter.fit(createIdealPoints(), 1920, 1080)
        assertNotNull(result.profile)

        for (point in result.profile!!.keyCenters) {
            assertTrue("Normalized X must be in 0..1: was ${point.x}", point.x in 0.0f..1.0f)
            assertTrue("Normalized Y must be in 0..1: was ${point.y}", point.y in 0.0f..1.0f)
        }
    }

    @Test
    fun `10 4-corner bilinear interpolation test`() {
        // 四隅を指定
        val topLeft = NormalizedPoint(0.20f, 0.60f)
        val topRight = NormalizedPoint(0.80f, 0.60f)
        val bottomLeft = NormalizedPoint(0.20f, 0.90f)
        val bottomRight = NormalizedPoint(0.80f, 0.90f)

        val interpolated = fitter.interpolateGridFromCorners(
            topLeft = topLeft,
            topRight = topRight,
            bottomLeft = bottomLeft,
            bottomRight = bottomRight
        )

        assertEquals(15, interpolated.size)

        // 四隅の点の一致
        assertEquals(topLeft.x, interpolated[0].x, 0.001f)
        assertEquals(topLeft.y, interpolated[0].y, 0.001f)

        assertEquals(topRight.x, interpolated[4].x, 0.001f)
        assertEquals(topRight.y, interpolated[4].y, 0.001f)

        assertEquals(bottomLeft.x, interpolated[10].x, 0.001f)
        assertEquals(bottomLeft.y, interpolated[10].y, 0.001f)

        assertEquals(bottomRight.x, interpolated[14].x, 0.001f)
        assertEquals(bottomRight.y, interpolated[14].y, 0.001f)

        // 中央の点 (Key 7: 列2, 行1) が中央値と一致
        assertEquals(0.50f, interpolated[7].x, 0.001f)
        assertEquals(0.75f, interpolated[7].y, 0.001f)

        // 台形（パース）の歪みテスト
        val trapezoidTopLeft = NormalizedPoint(0.30f, 0.60f)
        val trapezoidTopRight = NormalizedPoint(0.70f, 0.60f)
        val trapezoidBottomLeft = NormalizedPoint(0.20f, 0.90f)
        val trapezoidBottomRight = NormalizedPoint(0.80f, 0.90f)

        val trapezoidGrid = fitter.interpolateGridFromCorners(
            topLeft = trapezoidTopLeft,
            topRight = trapezoidTopRight,
            bottomLeft = trapezoidBottomLeft,
            bottomRight = trapezoidBottomRight
        )

        assertEquals(15, trapezoidGrid.size)
        // 中段の中央 (Key 7) の検証: ( (0.3+0.7)/2 + (0.2+0.8)/2 ) / 2 = 0.50
        assertEquals(0.50f, trapezoidGrid[7].x, 0.001f)
        assertEquals(0.75f, trapezoidGrid[7].y, 0.001f)
    }

    @Test
    fun `11 Large tablet high resolution fitting test`() {
        // 大画面タブレット (2560 x 1600)
        // 画面が大きい場合、ピクセル値も全体的にスケールアップする
        val tabletPoints = createIdealPoints(
            width = 2560,
            height = 1600,
            startX = 550f,
            startY = 900f,
            dx = 360f,
            dy = 220f,
            radius = 70f
        )

        val result = fitter.fit(tabletPoints, 2560, 1600)
        assertNotNull("Fitting should succeed on high-res tablet screens", result.profile)
        assertTrue("Confidence should be high on tablet ideal points", result.confidence >= 0.90f)

        // キー半径比率の妥当性 (70 / 1600 = 0.04375)
        assertEquals(70f / 1600f, result.profile!!.keyRadiusRatio, 0.01f)
    }

    @Test
    fun `12 Screen edge UI noise resilience test`() {
        // 画面左端にUIアイコン（チャットや戻るボタン等）が複数検出されてもキーボード中央がずれないこと
        val points = createIdealPoints(
            width = 2400,
            height = 1080,
            startX = 600f,
            startY = 500f,
            dx = 300f,
            dy = 180f
        ).toMutableList()

        // 画面左側にノイズを4点追加
        points.add(DetectedPoint(x = 50f, y = 100f, radius = 40f))
        points.add(DetectedPoint(x = 80f, y = 300f, radius = 40f))
        points.add(DetectedPoint(x = 120f, y = 700f, radius = 40f))
        points.add(DetectedPoint(x = 60f, y = 900f, radius = 40f))

        val result = fitter.fit(points, 2400, 1080)
        assertNotNull("Should fit correctly despite left-edge UI icons", result.profile)
        assertTrue("Confidence should remain high", result.confidence >= 0.85f)

        // 中央キー（Key 7: 列2, 行1）の正規化X座標が 0.50 (1200 / 2400) に近接していること
        val centerKey = result.profile!!.keyCenters[7]
        assertEquals(0.50f, centerKey.x, 0.02f)
    }
}
