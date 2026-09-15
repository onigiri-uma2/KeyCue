package com.onigiri.keycue.ui.fitting

import org.junit.Assert.assertEquals
import org.junit.Test

class FittingCoordinatesTest {
    @Test
    fun `drag delta accounts for image size and zoom`() {
        val (x, y) = normalizedDragDelta(100f, 50f, 1000f, 500f, 2f)
        assertEquals(0.05f, x, 0.0001f)
        assertEquals(0.05f, y, 0.0001f)
    }

    @Test
    fun `zero image dimension produces safe zero delta`() {
        val (x, y) = normalizedDragDelta(100f, 50f, 0f, 500f, 1f)
        assertEquals(0f, x, 0.0001f)
        assertEquals(0f, y, 0.0001f)
    }
}
