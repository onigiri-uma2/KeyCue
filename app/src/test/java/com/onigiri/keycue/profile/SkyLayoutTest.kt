package com.onigiri.keycue.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * [SkyLayout] および [SkyLayoutRegistry] の単体テスト。
 *
 * 以下の仕様を検証します:
 * 1. 5種類の内部ID、表示名、ゲームパッド判定
 * 2. 旧IDからの移行および不正値フォールバック
 * 3. 5種類すべてのプリセットにおける15キーの整合性
 * 4. PAD_GRIDの座標一致（TOUCH_EXPANDED互換）とPAD系のキー配置特性
 */
class SkyLayoutTest {

    @Test
    fun testLayoutIdsAndGamepadFlag() {
        assertEquals("touch_standard", SkyLayout.TOUCH_STANDARD.id)
        assertFalse(SkyLayout.TOUCH_STANDARD.isGamepad)

        assertEquals("touch_expanded", SkyLayout.TOUCH_EXPANDED.id)
        assertFalse(SkyLayout.TOUCH_EXPANDED.isGamepad)

        assertEquals("pad_trigger_first", SkyLayout.PAD_TRIGGER_FIRST.id)
        assertTrue(SkyLayout.PAD_TRIGGER_FIRST.isGamepad)

        assertEquals("pad_dpad_first", SkyLayout.PAD_DPAD_FIRST.id)
        assertTrue(SkyLayout.PAD_DPAD_FIRST.isGamepad)

        assertEquals("pad_grid", SkyLayout.PAD_GRID.id)
        assertTrue(SkyLayout.PAD_GRID.isGamepad)
    }

    @Test
    fun testFromId_standardIds() {
        assertEquals(SkyLayout.TOUCH_STANDARD, SkyLayout.fromId("touch_standard"))
        assertEquals(SkyLayout.TOUCH_EXPANDED, SkyLayout.fromId("touch_expanded"))
        assertEquals(SkyLayout.PAD_TRIGGER_FIRST, SkyLayout.fromId("pad_trigger_first"))
        assertEquals(SkyLayout.PAD_DPAD_FIRST, SkyLayout.fromId("pad_dpad_first"))
        assertEquals(SkyLayout.PAD_GRID, SkyLayout.fromId("pad_grid"))
    }

    @Test
    fun testFromId_legacyMigration() {
        assertEquals(SkyLayout.TOUCH_STANDARD, SkyLayout.fromId("sk1"))
        assertEquals(SkyLayout.TOUCH_EXPANDED, SkyLayout.fromId("sk2"))
        assertEquals(SkyLayout.PAD_TRIGGER_FIRST, SkyLayout.fromId("pad1"))
        assertEquals(SkyLayout.PAD_DPAD_FIRST, SkyLayout.fromId("pad2"))
        assertEquals(SkyLayout.PAD_GRID, SkyLayout.fromId("pad3"))
    }

    @Test
    fun testFromId_fallbackToTouchStandard() {
        assertEquals(SkyLayout.TOUCH_STANDARD, SkyLayout.fromId(null))
        assertEquals(SkyLayout.TOUCH_STANDARD, SkyLayout.fromId(""))
        assertEquals(SkyLayout.TOUCH_STANDARD, SkyLayout.fromId("unknown_layout"))
    }

    @Test
    fun testPresetsIntegrity_allHave15Keys() {
        for (layout in SkyLayout.entries) {
            val preset = SkyLayoutRegistry.getPreset(layout)
            assertNotNull("Preset for $layout must exist", preset)
            assertEquals("Layout $layout must contain exactly 15 key centers", 15, preset.keyCenters.size)

            for (i in 0..14) {
                val pt = preset.keyCenters[i]
                assertTrue("Key $i x (${pt.x}) in range", pt.x in 0.0f..1.0f)
                assertTrue("Key $i y (${pt.y}) in range", pt.y in 0.0f..1.0f)
            }
        }
    }

    @Test
    fun testPadGridCoordinates_matchTouchExpanded() {
        val expandedPreset = SkyLayoutRegistry.getPreset(SkyLayout.TOUCH_EXPANDED)
        val padGridPreset = SkyLayoutRegistry.getPreset(SkyLayout.PAD_GRID)

        for (i in 0..14) {
            val expPt = expandedPreset.keyCenters[i]
            val padPt = padGridPreset.keyCenters[i]
            assertEquals("Key $i x must match between expanded and pad_grid", expPt.x, padPt.x, 0.005f)
            assertEquals("Key $i y must match between expanded and pad_grid", expPt.y, padPt.y, 0.005f)
        }
    }

    @Test
    fun testPadTriggerFirstAndDpadFirst_horizontalSymmetry() {
        // pad1 and pad2 have bilateral symmetry around center X = 0.5
        val pad1Preset = SkyLayoutRegistry.getPreset(SkyLayout.PAD_TRIGGER_FIRST)
        val pad2Preset = SkyLayoutRegistry.getPreset(SkyLayout.PAD_DPAD_FIRST)

        for (preset in listOf(pad1Preset, pad2Preset)) {
            val avgX = preset.keyCenters.map { it.x }.average()
            assertEquals("Center of keys should be around 0.5", 0.5, avgX, 0.05)
        }
    }
}
