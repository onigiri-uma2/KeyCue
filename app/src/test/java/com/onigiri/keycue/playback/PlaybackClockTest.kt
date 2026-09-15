package com.onigiri.keycue.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackClockTest {

    private class MockTimeProvider(var currentTime: Long = 0L) : TimeProvider {
        override fun elapsedRealtime(): Long = currentTime
    }

    @Test
    fun `speed change during playback does not cause position to jump`() {
        val timeProvider = MockTimeProvider(1000L)
        val clock = PlaybackClock(timeProvider)

        clock.play(0L)
        // 1秒経過 (speed 1.0) -> pos = 1000ms
        timeProvider.currentTime += 1000L
        assertEquals(1000L, clock.getCurrentPositionMs())

        // 速度を 0.5x に変更
        clock.setSpeed(0.5f)
        // 変更直後の位置はジャンプせず 1000ms のまま
        assertEquals(1000L, clock.getCurrentPositionMs())

        // さらに 1秒経過 (speed 0.5) -> +500ms -> pos = 1500ms
        timeProvider.currentTime += 1000L
        assertEquals(1500L, clock.getCurrentPositionMs())

        // 速度を 1.5x に変更
        clock.setSpeed(1.5f)
        assertEquals(1500L, clock.getCurrentPositionMs())

        // さらに 2秒経過 (speed 1.5) -> +3000ms -> pos = 4500ms
        timeProvider.currentTime += 2000L
        assertEquals(4500L, clock.getCurrentPositionMs())
    }

    @Test
    fun `speed change during pause takes effect upon resume`() {
        val timeProvider = MockTimeProvider(1000L)
        val clock = PlaybackClock(timeProvider)

        clock.play(0L)
        timeProvider.currentTime += 2000L // 2000ms
        clock.pause()
        assertEquals(2000L, clock.getCurrentPositionMs())

        // Pause中に速度を 2.0x に変更
        clock.setSpeed(2.0f)
        timeProvider.currentTime += 5000L // Pause中に時間が経過しても位置は固定
        assertEquals(2000L, clock.getCurrentPositionMs())

        // 再開
        clock.resume()
        assertEquals(2000L, clock.getCurrentPositionMs())

        // 1秒経過 (speed 2.0) -> +2000ms -> 4000ms
        timeProvider.currentTime += 1000L
        assertEquals(4000L, clock.getCurrentPositionMs())
    }

    @Test
    fun `seekTo moves to exact position and clamps to duration`() {
        val timeProvider = MockTimeProvider(1000L)
        val clock = PlaybackClock(timeProvider)

        clock.play(0L)
        clock.seekTo(5000L)
        assertEquals(5000L, clock.getCurrentPositionMs())

        // duration クランプ検証
        val durationMs = 10000L
        assertEquals(5000L, clock.getCurrentPositionMs(durationMs))

        clock.seekTo(15000L)
        assertEquals(10000L, clock.getCurrentPositionMs(durationMs))

        // 0未満のシーク
        clock.seekTo(-500L)
        assertEquals(0L, clock.getCurrentPositionMs(durationMs))
    }

    @Test
    fun `negative start position is allowed when allowNegative is true for countdown`() {
        val timeProvider = MockTimeProvider(1000L)
        val clock = PlaybackClock(timeProvider)

        clock.play(startPositionMs = -3000L, allowNegative = true)
        assertEquals(-3000L, clock.getCurrentPositionMs(allowNegative = true))

        timeProvider.currentTime += 1500L // 1.5秒経過
        assertEquals(-1500L, clock.getCurrentPositionMs(allowNegative = true))

        timeProvider.currentTime += 1500L // さらに1.5秒経過
        assertEquals(0L, clock.getCurrentPositionMs(allowNegative = true))
    }
}
