package com.onigiri.keycue.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [NormalizedPoint] および [FitProfile] の単体テスト。
 *
 * 正規化座標から実ピクセル座標への変換、境界値バリデーション、
 * およびデフォルトプロファイル生成の整合性を検証します。
 */
class FitProfileTest {

    @Test
    fun normalizedPoint_toPixel_calculatesCorrectCoordinates() {
        val point = NormalizedPoint(x = 0.5f, y = 0.8f)
        val (px, py) = point.toPixel(width = 1000, height = 2000)

        assertEquals(500f, px, 0.001f)
        assertEquals(1600f, py, 0.001f)
    }

    @Test
    fun normalizedPoint_rejectsOutOfBounds() {
        assertThrows(IllegalArgumentException::class.java) {
            NormalizedPoint(x = -0.1f, y = 0.5f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            NormalizedPoint(x = 1.1f, y = 0.5f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            NormalizedPoint(x = 0.5f, y = -0.01f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            NormalizedPoint(x = 0.5f, y = 1.05f)
        }
    }

    @Test
    fun fitProfile_defaultTestProfile_hasExpectedKeyCount() {
        val profile = FitProfile.createDefaultTestProfile(landscape = true)

        assertEquals(15, profile.keyCenters.size)
        assertTrue(profile.landscape)
        assertEquals(0.04f, profile.keyRadiusRatio, 0.001f)
    }

    @Test
    fun fitProfile_defaultTestProfile_matchesBaseGridCoordinates() {
        val profile = FitProfile.createDefaultTestProfile()

        val expectedXs = floatArrayOf(0.20f, 0.35f, 0.50f, 0.65f, 0.80f)
        val expectedYs = floatArrayOf(0.65f, 0.75f, 0.85f)

        var index = 0
        for (row in 0..2) {
            for (col in 0..4) {
                val key = profile.keyCenters[index]
                assertEquals("Key $index X mismatch", expectedXs[col], key.x, 0.001f)
                assertEquals("Key $index Y mismatch", expectedYs[row], key.y, 0.001f)
                index++
            }
        }

        // 先頭キー(0: 左上)、中央キー(7: 中央)、最終キー(14: 右下) の検証
        assertEquals(0.20f, profile.keyCenters[0].x, 0.001f)
        assertEquals(0.65f, profile.keyCenters[0].y, 0.001f)

        assertEquals(0.50f, profile.keyCenters[7].x, 0.001f)
        assertEquals(0.75f, profile.keyCenters[7].y, 0.001f)

        assertEquals(0.80f, profile.keyCenters[14].x, 0.001f)
        assertEquals(0.85f, profile.keyCenters[14].y, 0.001f)
    }

    @Test
    fun fitProfile_rejectsEmptyKeyCenters() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            FitProfile(keyCenters = emptyList())
        }
        assertTrue(ex.message?.contains("empty") == true)
    }

    @Test
    fun fitProfile_orientationConversion_adaptsToPortraitAndLandscape() {
        val landscapeProfile = FitProfile.createDefaultTestProfile(landscape = true)
        val portraitProfile = FitProfile.createDefaultTestProfile(landscape = false)

        assertTrue(landscapeProfile.landscape)
        assertFalse(portraitProfile.landscape)

        // Landscape解像度: 2400 x 1080
        val lWidth = 2400
        val lHeight = 1080
        val (lKey0X, lKey0Y) = landscapeProfile.keyCenters[0].toPixel(lWidth, lHeight)
        assertEquals(480f, lKey0X, 0.001f) // 2400 * 0.20
        assertEquals(702f, lKey0Y, 0.001f) // 1080 * 0.65

        // Portrait解像度: 1080 x 2400
        val pWidth = 1080
        val pHeight = 2400
        val (pKey0X, pKey0Y) = portraitProfile.keyCenters[0].toPixel(pWidth, pHeight)
        assertEquals(216f, pKey0X, 0.001f) // 1080 * 0.20
        assertEquals(1560f, pKey0Y, 0.001f) // 2400 * 0.65
    }
}
