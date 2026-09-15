package com.onigiri.keycue.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeFormatterTest {

    @Test
    fun `formatDuration formats zero and small seconds correctly`() {
        assertEquals("00:00", TimeFormatter.formatDuration(0L))
        assertEquals("00:05", TimeFormatter.formatDuration(5_000L))
        assertEquals("00:59", TimeFormatter.formatDuration(59_000L))
    }

    @Test
    fun `formatDuration formats minutes and seconds under one hour`() {
        assertEquals("01:24", TimeFormatter.formatDuration(84_000L))
        assertEquals("03:42", TimeFormatter.formatDuration(222_000L))
        assertEquals("59:59", TimeFormatter.formatDuration(3599_000L))
    }

    @Test
    fun `formatDuration formats hours correctly when exceeds one hour`() {
        assertEquals("01:00:00", TimeFormatter.formatDuration(3600_000L))
        assertEquals("01:05:30", TimeFormatter.formatDuration(3930_000L))
        assertEquals("02:15:00", TimeFormatter.formatDuration(8100_000L))
    }

    @Test
    fun `formatDuration clamps negative values to 00 00`() {
        assertEquals("00:00", TimeFormatter.formatDuration(-1000L))
        assertEquals("00:00", TimeFormatter.formatDuration(-99999L))
    }

    @Test
    fun `formatDurationPair formats standard minutes pair`() {
        val result = TimeFormatter.formatDurationPair(84_000L, 222_000L)
        assertEquals("01:24 / 03:42", result)
    }

    @Test
    fun `formatDurationPair forces hours when total is one hour or more`() {
        val result = TimeFormatter.formatDurationPair(84_000L, 3930_000L)
        assertEquals("00:01:24 / 01:05:30", result)
    }
}
