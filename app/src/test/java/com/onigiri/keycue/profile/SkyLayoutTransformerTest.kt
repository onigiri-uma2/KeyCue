package com.onigiri.keycue.profile

import com.onigiri.keycue.model.FitProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [SkyLayoutTransformer] の単体テスト。
 *
 * 以下の仕様を検証します:
 * 1. 基準不変性: TOUCH_STANDARD は元の baseProfile と完全一致
 * 2. 拡大変換: TOUCH_EXPANDED では領域が適切に拡大
 * 3. PAD系外挿: PAD系レイアウトへの正常な座標マッピング
 * 4. baseProfile保護: 変換によって元の baseProfile は一切変更されない
 * 5. 誤差不蓄積: レイアウト切り替えを何回繰り返しても同一結果が得られる
 * 6. 微調整（Adjustment）の適用と独立性
 */
class SkyLayoutTransformerTest {

    private fun createStandardBaseProfile(): FitProfile {
        val standardPreset = SkyLayoutRegistry.getPreset(SkyLayout.TOUCH_STANDARD)
        return FitProfile(
            keyCenters = standardPreset.keyCenters,
            keyRadiusRatio = 0.04f,
            landscape = true
        )
    }

    @Test
    fun testIdentityTransform_touchStandard() {
        val base = createStandardBaseProfile()
        val transformed = SkyLayoutTransformer.transform(
            baseFitProfile = base,
            targetLayout = SkyLayout.TOUCH_STANDARD,
            adjustment = SkyLayoutAdjustment.DEFAULT
        )

        assertEquals(15, transformed.keyCenters.size)
        for (i in 0..14) {
            val basePt = base.keyCenters[i]
            val transPt = transformed.keyCenters[i]
            assertEquals("Button $i x must match base", basePt.x, transPt.x, 0.0001f)
            assertEquals("Button $i y must match base", basePt.y, transPt.y, 0.0001f)
        }
    }

    @Test
    fun testExpandedTransform_largerBoundingBox() {
        val base = createStandardBaseProfile()
        val transformed = SkyLayoutTransformer.transform(
            baseFitProfile = base,
            targetLayout = SkyLayout.TOUCH_EXPANDED,
            adjustment = SkyLayoutAdjustment.DEFAULT
        )

        val baseMinX = base.keyCenters.minOf { it.x }
        val baseMaxX = base.keyCenters.maxOf { it.x }
        val baseWidth = baseMaxX - baseMinX

        val transMinX = transformed.keyCenters.minOf { it.x }
        val transMaxX = transformed.keyCenters.maxOf { it.x }
        val transWidth = transMaxX - transMinX

        assertTrue("Expanded width ($transWidth) should be larger than base width ($baseWidth)", transWidth > baseWidth)
    }

    @Test
    fun testPadLayouts_allButtonsInValidRange() {
        val base = createStandardBaseProfile()
        for (padLayout in listOf(SkyLayout.PAD_TRIGGER_FIRST, SkyLayout.PAD_DPAD_FIRST, SkyLayout.PAD_GRID)) {
            val transformed = SkyLayoutTransformer.transform(
                baseFitProfile = base,
                targetLayout = padLayout,
                adjustment = SkyLayoutAdjustment.DEFAULT
            )

            assertEquals(15, transformed.keyCenters.size)
            for (i in 0..14) {
                val pt = transformed.keyCenters[i]
                assertTrue("Button $i x (${pt.x}) in 0.0..1.0 for $padLayout", pt.x in 0.0f..1.0f)
                assertTrue("Button $i y (${pt.y}) in 0.0..1.0 for $padLayout", pt.y in 0.0f..1.0f)
            }
        }
    }

    @Test
    fun testBaseProfileImmutability() {
        val base = createStandardBaseProfile()
        val originalSnapshot = base.keyCenters.map { it.x to it.y }

        // 複数回・全レイアウトへの変換を実行
        for (layout in SkyLayout.entries) {
            SkyLayoutTransformer.transform(base, layout, SkyLayoutAdjustment(0.02f, 0.02f, 1.05f, 0.95f))
        }

        // baseProfile が全く変わっていないことを確認
        for (i in 0..14) {
            assertEquals("Base x at $i must be unchanged", originalSnapshot[i].first, base.keyCenters[i].x, 0.000001f)
            assertEquals("Base y at $i must be unchanged", originalSnapshot[i].second, base.keyCenters[i].y, 0.000001f)
        }
    }

    @Test
    fun testNoDriftOnRepeatedSwitching() {
        val base = createStandardBaseProfile()

        // 1回目の変換
        val firstPadResult = SkyLayoutTransformer.transform(base, SkyLayout.PAD_TRIGGER_FIRST)

        // 他のレイアウトを何回も経由
        SkyLayoutTransformer.transform(base, SkyLayout.TOUCH_EXPANDED)
        SkyLayoutTransformer.transform(base, SkyLayout.PAD_DPAD_FIRST)
        SkyLayoutTransformer.transform(base, SkyLayout.TOUCH_STANDARD)
        SkyLayoutTransformer.transform(base, SkyLayout.PAD_GRID)

        // 再び PAD_TRIGGER_FIRST へ変換
        val secondPadResult = SkyLayoutTransformer.transform(base, SkyLayout.PAD_TRIGGER_FIRST)

        for (i in 0..14) {
            assertEquals(
                "Button $i coordinates must not drift after multiple switches",
                firstPadResult.keyCenters[i].x,
                secondPadResult.keyCenters[i].x,
                0.000001f
            )
            assertEquals(
                "Button $i coordinates must not drift after multiple switches",
                firstPadResult.keyCenters[i].y,
                secondPadResult.keyCenters[i].y,
                0.000001f
            )
        }
    }

    @Test
    fun testAdjustmentApplication() {
        val base = createStandardBaseProfile()
        val unadjusted = SkyLayoutTransformer.transform(base, SkyLayout.PAD_TRIGGER_FIRST, SkyLayoutAdjustment.DEFAULT)

        val offsetX = 0.05f
        val offsetY = -0.03f
        val adjusted = SkyLayoutTransformer.transform(
            base,
            SkyLayout.PAD_TRIGGER_FIRST,
            SkyLayoutAdjustment(offsetX = offsetX, offsetY = offsetY, scaleX = 1.0f, scaleY = 1.0f)
        )

        for (i in 0..14) {
            val uPt = unadjusted.keyCenters[i]
            val aPt = adjusted.keyCenters[i]
            assertEquals("Shift in X must match offsetX", uPt.x + offsetX, aPt.x, 0.001f)
            assertEquals("Shift in Y must match offsetY", uPt.y + offsetY, aPt.y, 0.001f)
        }
    }
}
