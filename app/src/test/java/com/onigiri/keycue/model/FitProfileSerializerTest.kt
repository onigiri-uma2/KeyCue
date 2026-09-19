package com.onigiri.keycue.model

import com.onigiri.keycue.profile.SkyProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FitProfileSerializerTest {

    @Test
    fun `serialize and deserialize roundtrip test`() {
        val original = FitProfile.createDefaultTestProfile(landscape = true)
        val json = FitProfileSerializer.toJson(original)

        assertNotNull(json)
        assertTrue(json.contains("\"landscape\":true"))
        assertTrue(json.contains("\"keyRadiusRatio\":0.04"))

        val restored = FitProfileSerializer.fromJson(json)
        assertNotNull(restored)
        assertEquals(original.landscape, restored!!.landscape)
        assertEquals(original.keyRadiusRatio, restored.keyRadiusRatio, 0.0001f)
        assertEquals(SkyProfile.keyCount, restored.keyCenters.size)

        for (i in 0 until SkyProfile.keyCount) {
            assertEquals(original.keyCenters[i].x, restored.keyCenters[i].x, 0.0001f)
            assertEquals(original.keyCenters[i].y, restored.keyCenters[i].y, 0.0001f)
        }
    }

    @Test
    fun `deserialize handles portrait orientation`() {
        val original = FitProfile.createDefaultTestProfile(landscape = false)
        val json = FitProfileSerializer.toJson(original)
        val restored = FitProfileSerializer.fromJson(json)

        assertNotNull(restored)
        assertEquals(false, restored!!.landscape)
    }

    @Test
    fun `deserialize returns null for invalid or incomplete json`() {
        assertNull(FitProfileSerializer.fromJson(null))
        assertNull(FitProfileSerializer.fromJson(""))
        assertNull(FitProfileSerializer.fromJson("{ invalid json }"))

        // キーが14個（1個不足）の場合
        val partialJson = "{\"landscape\":true,\"keyRadiusRatio\":0.04,\"keyCenters\":[{\"x\":0.1,\"y\":0.1}]}"
        assertNull(FitProfileSerializer.fromJson(partialJson))
    }

    @Test
    fun `deserialize handles fixed legacy released version json regression`() {
        // 既知の旧リリース版保存形式（SharedPreferences "fit_profile_json" に実際に保存されるフォーマット）
        val legacyReleasedJson = """
            {"landscape":true,"keyRadiusRatio":0.04,"keyCenters":[{"x":0.2,"y":0.65},{"x":0.35,"y":0.65},{"x":0.5,"y":0.65},{"x":0.65,"y":0.65},{"x":0.8,"y":0.65},{"x":0.2,"y":0.75},{"x":0.35,"y":0.75},{"x":0.5,"y":0.75},{"x":0.65,"y":0.75},{"x":0.8,"y":0.75},{"x":0.2,"y":0.85},{"x":0.35,"y":0.85},{"x":0.5,"y":0.85},{"x":0.65,"y":0.85},{"x":0.8,"y":0.85}]}
        """.trimIndent()

        val profile = FitProfileSerializer.fromJson(legacyReleasedJson)
        assertNotNull("Legacy JSON should deserialize successfully", profile)
        assertEquals(15, profile!!.keyCenters.size)
        assertTrue(profile.landscape)
        assertEquals(0.04f, profile.keyRadiusRatio, 0.0001f)

        // 代表キーの確認: 先頭(Key 0: 左上)、中央(Key 7)、末尾(Key 14: 右下)
        assertEquals(0.20f, profile.keyCenters[0].x, 0.0001f)
        assertEquals(0.65f, profile.keyCenters[0].y, 0.0001f)

        assertEquals(0.50f, profile.keyCenters[7].x, 0.0001f)
        assertEquals(0.75f, profile.keyCenters[7].y, 0.0001f)

        assertEquals(0.80f, profile.keyCenters[14].x, 0.0001f)
        assertEquals(0.85f, profile.keyCenters[14].y, 0.0001f)

        // ラウンドトリップ後の意味的一致検証
        val reSerialized = FitProfileSerializer.toJson(profile)
        val reDeserialized = FitProfileSerializer.fromJson(reSerialized)
        assertEquals(profile, reDeserialized)
    }

    @Test
    fun `deserialize handles custom user adjusted legacy json regression`() {
        // 既存ユーザーが端末画面に合わせて手動微調整した、デフォルト値と明確に異なる座標＋半径比率の旧JSONデータ
        // Key 0: (0.17, 0.61) ... Key 14: (0.84, 0.89), keyRadiusRatio: 0.053f
        val customAdjustedJson = """
            {"landscape":true,"keyRadiusRatio":0.053,"keyCenters":[{"x":0.17,"y":0.61},{"x":0.33,"y":0.62},{"x":0.49,"y":0.61},{"x":0.66,"y":0.63},{"x":0.82,"y":0.62},{"x":0.18,"y":0.74},{"x":0.34,"y":0.75},{"x":0.51,"y":0.74},{"x":0.67,"y":0.76},{"x":0.83,"y":0.75},{"x":0.19,"y":0.88},{"x":0.35,"y":0.89},{"x":0.52,"y":0.88},{"x":0.68,"y":0.90},{"x":0.84,"y":0.89}]}
        """.trimIndent()

        val profile = FitProfileSerializer.fromJson(customAdjustedJson)
        assertNotNull("Custom adjusted JSON should deserialize successfully", profile)
        assertEquals(SkyProfile.keyCount, profile!!.keyCenters.size)
        assertTrue(profile.landscape)
        assertEquals(0.053f, profile.keyRadiusRatio, 0.0001f)

        // 調整された四隅および中央座標がそのまま保持されること
        assertEquals(0.17f, profile.keyCenters[0].x, 0.0001f)
        assertEquals(0.61f, profile.keyCenters[0].y, 0.0001f)
        assertEquals(0.82f, profile.keyCenters[4].x, 0.0001f)
        assertEquals(0.62f, profile.keyCenters[4].y, 0.0001f)
        assertEquals(0.51f, profile.keyCenters[7].x, 0.0001f)
        assertEquals(0.74f, profile.keyCenters[7].y, 0.0001f)
        assertEquals(0.19f, profile.keyCenters[10].x, 0.0001f)
        assertEquals(0.88f, profile.keyCenters[10].y, 0.0001f)
        assertEquals(0.84f, profile.keyCenters[14].x, 0.0001f)
        assertEquals(0.89f, profile.keyCenters[14].y, 0.0001f)

        // 再シリアライズ・デシリアライズ後も完全一致
        val roundtrip = FitProfileSerializer.fromJson(FitProfileSerializer.toJson(profile))
        assertEquals(profile, roundtrip)
    }

    @Test
    fun `deserialize handles top-level unordered json properties`() {
        // keyCenters が先頭、landscape が末尾など、トップレベルプロパティの記述順序が異なる場合でも復元できること
        // （※各点の {"x":...,"y":...} 順や landscape=false 判定など旧保存形式の互換性を保証）
        val unorderedJson = """
            {
                "keyCenters": [
                    {"x":0.2,"y":0.65},{"x":0.35,"y":0.65},{"x":0.5,"y":0.65},{"x":0.65,"y":0.65},{"x":0.8,"y":0.65},
                    {"x":0.2,"y":0.75},{"x":0.35,"y":0.75},{"x":0.5,"y":0.75},{"x":0.65,"y":0.75},{"x":0.8,"y":0.75},
                    {"x":0.2,"y":0.85},{"x":0.35,"y":0.85},{"x":0.5,"y":0.85},{"x":0.65,"y":0.85},{"x":0.8,"y":0.85}
                ],
                "keyRadiusRatio":0.055,
                "landscape":false
            }
        """.trimIndent()

        val profile = FitProfileSerializer.fromJson(unorderedJson)
        assertNotNull(profile)
        assertEquals(SkyProfile.keyCount, profile!!.keyCenters.size)
        assertEquals(false, profile.landscape)
        assertEquals(0.055f, profile.keyRadiusRatio, 0.0001f)
        assertEquals(0.20f, profile.keyCenters[0].x, 0.0001f)
    }
}
