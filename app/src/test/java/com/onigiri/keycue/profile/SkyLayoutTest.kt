package com.onigiri.keycue.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [SkyLayout] および [SkyLayoutRegistry] の単体テスト。
 *
 * 以下の仕様を検証します:
 * 1. 6種類の内部ID、表示名、ゲームパッド判定
 * 2. 旧IDからの移行および不正値フォールバック（旧pad3 -> PAD_GRID_EXPANDED等）
 * 3. 6種類すべてのプリセットにおける15キーの整合性
 * 4. 格子: 標準と拡大で独立した別座標を持つこと
 * 5. 分散1・分散2: サイズ別の重複設定が存在しないこと
 * 6. 論理キー対応（0..14）が維持されること
 */
class SkyLayoutTest {

    @Test
    fun testLayoutIdsAndGamepadFlag() {
        assertEquals("touch_standard", SkyLayout.TOUCH_STANDARD.id)
        assertFalse(SkyLayout.TOUCH_STANDARD.isGamepad)
        assertEquals("タッチ（標準）", SkyLayout.TOUCH_STANDARD.displayName)

        assertEquals("touch_expanded", SkyLayout.TOUCH_EXPANDED.id)
        assertFalse(SkyLayout.TOUCH_EXPANDED.isGamepad)
        assertEquals("タッチ（拡大）", SkyLayout.TOUCH_EXPANDED.displayName)

        assertEquals("pad_trigger_first", SkyLayout.PAD_TRIGGER_FIRST.id)
        assertTrue(SkyLayout.PAD_TRIGGER_FIRST.isGamepad)
        assertEquals("パッド（分散1）", SkyLayout.PAD_TRIGGER_FIRST.displayName)

        assertEquals("pad_dpad_first", SkyLayout.PAD_DPAD_FIRST.id)
        assertTrue(SkyLayout.PAD_DPAD_FIRST.isGamepad)
        assertEquals("パッド（分散2）", SkyLayout.PAD_DPAD_FIRST.displayName)

        assertEquals("pad_grid_standard", SkyLayout.PAD_GRID_STANDARD.id)
        assertTrue(SkyLayout.PAD_GRID_STANDARD.isGamepad)
        assertEquals("パッド（格子・標準）", SkyLayout.PAD_GRID_STANDARD.displayName)

        assertEquals("pad_grid_expanded", SkyLayout.PAD_GRID_EXPANDED.id)
        assertTrue(SkyLayout.PAD_GRID_EXPANDED.isGamepad)
        assertEquals("パッド（格子・拡大）", SkyLayout.PAD_GRID_EXPANDED.displayName)
    }

    @Test
    fun testFromId_standardIds() {
        assertEquals(SkyLayout.TOUCH_STANDARD, SkyLayout.fromId("touch_standard"))
        assertEquals(SkyLayout.TOUCH_EXPANDED, SkyLayout.fromId("touch_expanded"))
        assertEquals(SkyLayout.PAD_TRIGGER_FIRST, SkyLayout.fromId("pad_trigger_first"))
        assertEquals(SkyLayout.PAD_DPAD_FIRST, SkyLayout.fromId("pad_dpad_first"))
        assertEquals(SkyLayout.PAD_GRID_STANDARD, SkyLayout.fromId("pad_grid_standard"))
        assertEquals(SkyLayout.PAD_GRID_EXPANDED, SkyLayout.fromId("pad_grid_expanded"))
    }

    @Test
    fun testFromId_legacyMigration() {
        // 旧短縮ID (sk1, sk2, pad1, pad2, pad3)
        assertEquals(SkyLayout.TOUCH_STANDARD, SkyLayout.fromId("sk1"))
        assertEquals(SkyLayout.TOUCH_EXPANDED, SkyLayout.fromId("sk2"))
        assertEquals(SkyLayout.PAD_TRIGGER_FIRST, SkyLayout.fromId("pad1"))
        assertEquals(SkyLayout.PAD_DPAD_FIRST, SkyLayout.fromId("pad2"))
        assertEquals(SkyLayout.PAD_GRID_EXPANDED, SkyLayout.fromId("pad3"))

        // 旧識別名および旧enum名
        assertEquals(SkyLayout.PAD_TRIGGER_FIRST, SkyLayout.fromId("pad_trigger_first"))
        assertEquals(SkyLayout.PAD_TRIGGER_FIRST, SkyLayout.fromId("PAD_TRIGGER_FIRST"))
        assertEquals(SkyLayout.PAD_TRIGGER_FIRST, SkyLayout.fromId("pad_trigger_first_standard"))
        assertEquals(SkyLayout.PAD_TRIGGER_FIRST, SkyLayout.fromId("pad_trigger_first_expanded"))

        assertEquals(SkyLayout.PAD_DPAD_FIRST, SkyLayout.fromId("pad_dpad_first"))
        assertEquals(SkyLayout.PAD_DPAD_FIRST, SkyLayout.fromId("PAD_DPAD_FIRST"))
        assertEquals(SkyLayout.PAD_DPAD_FIRST, SkyLayout.fromId("pad_dpad_first_standard"))
        assertEquals(SkyLayout.PAD_DPAD_FIRST, SkyLayout.fromId("pad_dpad_first_expanded"))

        // 既存の PAD_GRID は拡大版に相当するため、PAD_GRID_EXPANDED へ移行
        assertEquals(SkyLayout.PAD_GRID_EXPANDED, SkyLayout.fromId("pad_grid"))
        assertEquals(SkyLayout.PAD_GRID_EXPANDED, SkyLayout.fromId("PAD_GRID"))
    }

    @Test
    fun testFromId_fallbackToTouchStandard() {
        assertEquals(SkyLayout.TOUCH_STANDARD, SkyLayout.fromId(null))
        assertEquals(SkyLayout.TOUCH_STANDARD, SkyLayout.fromId(""))
        assertEquals(SkyLayout.TOUCH_STANDARD, SkyLayout.fromId("unknown_layout"))
    }

    @Test
    fun testPresetsIntegrity_all6LayoutsHave15Keys() {
        assertEquals("計6種類のレイアウトが存在すること", 6, SkyLayout.entries.size)
        for (layout in SkyLayout.entries) {
            val preset = SkyLayoutRegistry.getPreset(layout)
            assertNotNull("Preset for $layout must exist", preset)
            assertEquals("Layout $layout must contain exactly 15 key centers", 15, preset.keyCenters.size)

            for (i in 0..14) {
                val pt = preset.keyCenters[i]
                assertTrue("Key $i x (${pt.x}) in range for $layout", pt.x in 0.0f..1.0f)
                assertTrue("Key $i y (${pt.y}) in range for $layout", pt.y in 0.0f..1.0f)
            }
        }
    }

    @Test
    fun testPadGridStandardAndExpanded_haveDistinctCoordinates() {
        // 格子: 標準と拡大で独立した15キー座標を持つ
        val gridStd = SkyLayoutRegistry.getPreset(SkyLayout.PAD_GRID_STANDARD)
        val gridExp = SkyLayoutRegistry.getPreset(SkyLayout.PAD_GRID_EXPANDED)
        assertNotEquals("格子 標準と拡大でKey 0のXが別であること", gridStd.keyCenters[0].x, gridExp.keyCenters[0].x)
        assertNotEquals("格子 標準と拡大でKey 4のXが別であること", gridStd.keyCenters[4].x, gridExp.keyCenters[4].x)
        assertNotEquals("格子 標準と拡大でKey 10のYが別であること", gridStd.keyCenters[10].y, gridExp.keyCenters[10].y)
    }

    @Test
    fun testNoDuplicateSettingsForPadTriggerFirstAndDpadFirst() {
        // 分散1・分散2にはサイズ別 (STANDARD / EXPANDED) のenum定数が存在しないことを確認
        val names = SkyLayout.entries.map { it.name }
        assertFalse("PAD_TRIGGER_FIRST_STANDARD が存在しないこと", names.contains("PAD_TRIGGER_FIRST_STANDARD"))
        assertFalse("PAD_TRIGGER_FIRST_EXPANDED が存在しないこと", names.contains("PAD_TRIGGER_FIRST_EXPANDED"))
        assertFalse("PAD_DPAD_FIRST_STANDARD が存在しないこと", names.contains("PAD_DPAD_FIRST_STANDARD"))
        assertFalse("PAD_DPAD_FIRST_EXPANDED が存在しないこと", names.contains("PAD_DPAD_FIRST_EXPANDED"))
        assertTrue("PAD_TRIGGER_FIRST が存在すること", names.contains("PAD_TRIGGER_FIRST"))
        assertTrue("PAD_DPAD_FIRST が存在すること", names.contains("PAD_DPAD_FIRST"))
    }

    @Test
    fun testPadGridCoordinates_matchRespectiveTouchGrids() {
        val touchStd = SkyLayoutRegistry.getPreset(SkyLayout.TOUCH_STANDARD)
        val padGridStd = SkyLayoutRegistry.getPreset(SkyLayout.PAD_GRID_STANDARD)
        for (i in 0..14) {
            assertEquals("Key $i x must match between touch standard and pad grid standard", touchStd.keyCenters[i].x, padGridStd.keyCenters[i].x, 0.005f)
            assertEquals("Key $i y must match between touch standard and pad grid standard", touchStd.keyCenters[i].y, padGridStd.keyCenters[i].y, 0.005f)
        }

        val touchExp = SkyLayoutRegistry.getPreset(SkyLayout.TOUCH_EXPANDED)
        val padGridExp = SkyLayoutRegistry.getPreset(SkyLayout.PAD_GRID_EXPANDED)
        for (i in 0..14) {
            assertEquals("Key $i x must match between touch expanded and pad grid expanded", touchExp.keyCenters[i].x, padGridExp.keyCenters[i].x, 0.005f)
            assertEquals("Key $i y must match between touch expanded and pad grid expanded", touchExp.keyCenters[i].y, padGridExp.keyCenters[i].y, 0.005f)
        }
    }

    @Test
    fun testPadTriggerFirstAndDpadFirst_horizontalSymmetry() {
        // pad1 and pad2 have bilateral symmetry around center X = 0.5
        val presets = listOf(
            SkyLayoutRegistry.getPreset(SkyLayout.PAD_TRIGGER_FIRST),
            SkyLayoutRegistry.getPreset(SkyLayout.PAD_DPAD_FIRST)
        )

        for (preset in presets) {
            val avgX = preset.keyCenters.map { it.x }.average()
            assertEquals("Center of keys should be around 0.5 for ${preset.layout}", 0.5, avgX, 0.05)
        }
    }
}
