package com.onigiri.keycue.playback

import com.onigiri.keycue.model.NoteEvent
import com.onigiri.keycue.model.SongData
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PlaybackEngineTest {

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
        title = "Test Song",
        durationMs = 5000L,
        events = listOf(
            NoteEvent(timeMs = 1000L, key = 0),
            NoteEvent(timeMs = 3000L, key = 7)
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
    fun initialState_isStopped() {
        assertEquals(PlaybackState.Stopped, engine.state.value)
    }

    @Test
    fun play_skipCountdown_startsPlayingImmediately() = runBlocking {
        engine.play(skipCountdown = true)
        assertTrue(engine.state.value is PlaybackState.Playing)
        assertEquals(0L, (engine.state.value as PlaybackState.Playing).positionMs)
    }

    @Test
    fun pause_and_resume_transitionsStateProperly() = runBlocking {
        engine.play(skipCountdown = true)

        testTimeProvider.advance(500L)
        engine.pause()
        assertTrue(engine.state.value is PlaybackState.Paused)
        assertEquals(500L, (engine.state.value as PlaybackState.Paused).positionMs)

        // resume
        engine.resume()
        assertTrue(engine.state.value is PlaybackState.Playing)
        assertEquals(500L, (engine.state.value as PlaybackState.Playing).positionMs)
    }

    @Test
    fun stop_resetsStateToStopped() = runBlocking {
        engine.play(skipCountdown = true)

        testTimeProvider.advance(1000L)
        engine.stop()

        assertEquals(PlaybackState.Stopped, engine.state.value)
        assertEquals(0L, engine.getCurrentPositionMs())
    }

    @Test
    fun seekTo_updatesPositionAndMaintainsState() = runBlocking {
        engine.play(skipCountdown = true)

        engine.seekTo(2500L)
        assertTrue(engine.state.value is PlaybackState.Playing)
        assertEquals(2500L, (engine.state.value as PlaybackState.Playing).positionMs)
        assertEquals(2500L, engine.getCurrentPositionMs())

        engine.pause()
        engine.seekTo(4000L)
        assertTrue(engine.state.value is PlaybackState.Paused)
        assertEquals(4000L, (engine.state.value as PlaybackState.Paused).positionMs)
        assertEquals(4000L, engine.getCurrentPositionMs())
    }

    @Test
    fun speedChange_preservesCurrentPlaybackPosition() = runBlocking {
        engine.play(skipCountdown = true)
        testTimeProvider.advance(750L)

        engine.setSpeed(1.5f)

        assertEquals(750L, engine.getCurrentPositionMs())
        assertTrue(engine.state.value is PlaybackState.Playing)
    }

    @Test
    fun seekBack_rewindsProperly() = runBlocking {
        engine.play(skipCountdown = true)

        engine.seekTo(3000L)
        engine.seekBack(1000L) // 3000 - 1000 = 2000
        assertEquals(2000L, engine.getCurrentPositionMs())

        // 0ms未満にはならない
        engine.seekBack(5000L)
        assertEquals(0L, engine.getCurrentPositionMs())
    }

    @Test
    fun songFinish_transitionsToFinishedState() = runBlocking {
        engine.play(skipCountdown = true)

        // 楽曲のduration (5000L) を超過させる
        testTimeProvider.advance(5500L)

        // Tickerの更新を待機
        var retries = 0
        while (engine.state.value !is PlaybackState.Finished && retries < 20) {
            kotlinx.coroutines.delay(50L)
            retries++
        }

        assertTrue(engine.state.value is PlaybackState.Finished)
        val finishedState = engine.state.value as PlaybackState.Finished
        assertEquals(sampleSong.durationMs, finishedState.positionMs)
    }

    @Test
    fun countdown_startsCountingDown() = runBlocking {
        engine.countdownMs = 1000L
        engine.play(skipCountdown = false)

        kotlinx.coroutines.delay(50L)
        assertTrue(engine.state.value is PlaybackState.CountingDown)
        val cd = engine.state.value as PlaybackState.CountingDown
        assertEquals(1, cd.countNumber)
    }
}
