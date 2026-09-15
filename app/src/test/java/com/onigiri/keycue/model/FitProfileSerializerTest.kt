package com.onigiri.keycue.model

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
        assertEquals(FitProfile.KEY_COUNT, restored.keyCenters.size)

        for (i in 0 until FitProfile.KEY_COUNT) {
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
}
