package com.onigiri.keycue.playback

import com.onigiri.keycue.model.NoteEvent
import com.onigiri.keycue.model.SongData
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PlaybackEngineLoopTest {

    private class TestTimeProvider(var currentTime: Long = 1000L) : TimeProvider {
        override fun elapsedRealtime(): Long = currentTime
        fun advance(deltaMs: Long) {
            currentTime += deltaMs
        }
    }

    private lateinit var testTimeProvider: TestTimeProvider
    private lateinit var clock: PlaybackClock
    private lateinit var engine: PlaybackEngine

    private val sampleSong = SongData(
        title = "Loop Test Song",
        durationMs = 10_000L,
        events = listOf(
            NoteEvent(timeMs = 1000L, key = 0),
            NoteEvent(timeMs = 5000L, key = 3)
        )
    )

    @Before
    fun setUp() {
        testTimeProvider = TestTimeProvider(1000L)
        clock = PlaybackClock(timeProvider = testTimeProvider)
        engine = PlaybackEngine(clock = clock)
        engine.setSong(sampleSong)
    }

    @After
    fun tearDown() {
        engine.release()
    }

    @Test
    fun isLoopValid_boundaryCases() {
        // 初期状態: 両方 null
        assertFalse(engine.isLoopValid())

        // Aのみ設定
        engine.setLoopStart(1000L)
        assertFalse(engine.isLoopValid())

        // Bを設定、A == B
        engine.setLoopEnd(1000L)
        assertFalse(engine.isLoopValid())

        // A > B
        engine.setLoopEnd(500L)
        assertFalse(engine.isLoopValid())

        // B - A = 299ms (MIN_LOOP_DURATION_MS 300ms 未満)
        engine.setLoopStart(1000L)
        engine.setLoopEnd(1299L)
        assertFalse(engine.isLoopValid())

        // B - A = 300ms (ちょうど MIN_LOOP_DURATION_MS)
        engine.setLoopEnd(1300L)
        assertTrue(engine.isLoopValid())

        // B - A > 300ms
        engine.setLoopEnd(2000L)
        assertTrue(engine.isLoopValid())
    }

    @Test
    fun shouldLoop_boundaryCases() {
        // ループ無効時は常に false
        engine.setLoopStart(1000L)
        assertFalse(engine.shouldLoop(3000L))

        // ループ有効時 (A = 1000L, B = 2000L)
        engine.setLoopEnd(2000L)
        assertTrue(engine.isLoopValid())

        // B 未満
        assertFalse(engine.shouldLoop(0L))
        assertFalse(engine.shouldLoop(1000L))
        assertFalse(engine.shouldLoop(1999L))

        // B 到達
        assertTrue(engine.shouldLoop(2000L))

        // B 超過
        assertTrue(engine.shouldLoop(2001L))
        assertTrue(engine.shouldLoop(5000L))
    }

    @Test
    fun getLoopTarget_returnsStartWhenTargetReached() {
        // ループ無効時: 常に null
        assertNull(engine.getLoopTarget(3000L))

        engine.setLoopStart(1500L)
        engine.setLoopEnd(2500L)

        // B 未満: null
        assertNull(engine.getLoopTarget(1500L))
        assertNull(engine.getLoopTarget(2499L))

        // B 到達: loopStartMs (1500L)
        assertEquals(1500L, engine.getLoopTarget(2500L))
        assertEquals(1500L, engine.getLoopTarget(3000L))
    }

    @Test
    fun settersAreIndependent_updatingOneDoesNotClearOther() {
        engine.setLoopStart(1000L)
        assertEquals(1000L, engine.loopStartMs)
        assertNull(engine.loopEndMs)

        // B を設定しても A は保持
        engine.setLoopEnd(3000L)
        assertEquals(1000L, engine.loopStartMs)
        assertEquals(3000L, engine.loopEndMs)

        // A を再設定しても B は保持
        engine.setLoopStart(1500L)
        assertEquals(1500L, engine.loopStartMs)
        assertEquals(3000L, engine.loopEndMs)

        // B を再設定しても A は保持
        engine.setLoopEnd(4000L)
        assertEquals(1500L, engine.loopStartMs)
        assertEquals(4000L, engine.loopEndMs)
    }

    @Test
    fun clearLoop_resetsBothPoints() {
        engine.setLoopStart(1000L)
        engine.setLoopEnd(3000L)
        assertTrue(engine.isLoopValid())

        engine.clearLoop()
        assertNull(engine.loopStartMs)
        assertNull(engine.loopEndMs)
        assertFalse(engine.isLoopValid())
    }

    @Test
    fun setSong_clearsLoopSettings() {
        engine.setLoopStart(1000L)
        engine.setLoopEnd(3000L)
        assertTrue(engine.isLoopValid())

        val anotherSong = SongData(
            title = "Another Song",
            durationMs = 8000L,
            events = emptyList()
        )
        engine.setSong(anotherSong)

        assertNull(engine.loopStartMs)
        assertNull(engine.loopEndMs)
        assertFalse(engine.isLoopValid())
    }

    @Test
    fun safeWhenNoSongOrDurationZero() {
        val emptyEngine = PlaybackEngine(clock = clock)
        // 曲未読込時は無視される
        emptyEngine.setLoopStart(1000L)
        emptyEngine.setLoopEnd(2000L)
        assertNull(emptyEngine.loopStartMs)
        assertNull(emptyEngine.loopEndMs)
        emptyEngine.release()

        val zeroDurationSong = SongData(title = "Zero", durationMs = 0L, events = emptyList())
        val zeroEngine = PlaybackEngine(clock = clock)
        zeroEngine.setSong(zeroDurationSong)
        zeroEngine.setLoopStart(500L)
        zeroEngine.setLoopEnd(1000L)
        assertNull(zeroEngine.loopStartMs)
        assertNull(zeroEngine.loopEndMs)
        zeroEngine.release()
    }

    @Test
    fun stateTransitions_preserveLoopSettings() {
        engine.setLoopStart(2000L)
        engine.setLoopEnd(5000L)
        assertTrue(engine.isLoopValid())

        // Play -> Pause -> Resume
        engine.play(skipCountdown = true)
        assertEquals(2000L, engine.loopStartMs)
        assertEquals(5000L, engine.loopEndMs)

        engine.pause()
        assertEquals(2000L, engine.loopStartMs)
        assertEquals(5000L, engine.loopEndMs)

        engine.resume()
        assertEquals(2000L, engine.loopStartMs)
        assertEquals(5000L, engine.loopEndMs)

        // Restart
        engine.restart(skipCountdown = true)
        assertEquals(2000L, engine.loopStartMs)
        assertEquals(5000L, engine.loopEndMs)

        // Stop
        engine.stop()
        assertEquals(2000L, engine.loopStartMs)
        assertEquals(5000L, engine.loopEndMs)
    }

    @Test
    fun progressTicker_loopsBeforeFinished() = runBlocking {
        // A = 1000L, B = 2000L
        engine.setLoopStart(1000L)
        engine.setLoopEnd(2000L)

        engine.play(skipCountdown = true)
        assertEquals(0L, engine.getCurrentPositionMs())

        // 1500ms 経過 (B到達前)
        testTimeProvider.advance(1500L)
        delay(60L)
        assertEquals(1500L, engine.getCurrentPositionMs())
        assertTrue(engine.state.value is PlaybackState.Playing)

        // さらに 600ms 経過 -> total 2100ms (B = 2000L を超過)
        testTimeProvider.advance(600L)

        // B到達により A (1000L) にシークされるのを待機
        var retries = 0
        while (engine.getCurrentPositionMs() != 1000L && retries < 20) {
            delay(30L)
            retries++
        }

        assertEquals(1000L, engine.getCurrentPositionMs())
        assertTrue("ABループで再生が継続し、Finishedにはならない", engine.state.value is PlaybackState.Playing)
    }
}
