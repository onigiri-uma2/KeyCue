package com.onigiri.keycue.audio

import com.onigiri.keycue.model.BeatSubdivision
import com.onigiri.keycue.model.MetronomeConfig
import com.onigiri.keycue.model.MetronomeTimingMode
import com.onigiri.keycue.model.SongData
import com.onigiri.keycue.playback.PlaybackClock
import com.onigiri.keycue.playback.PlaybackEngine
import com.onigiri.keycue.playback.PlaybackState
import com.onigiri.keycue.playback.TimeProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 設定画面復帰時における再生一時停止、メトロノーム発音停止、状態保持、再開のテスト。
 */
class MetronomeSettingsPauseTest {

    private class TestTimeProvider(var currentTime: Long = 1000L) : TimeProvider {
        override fun elapsedRealtime(): Long = currentTime
        fun advance(deltaMs: Long) {
            currentTime += deltaMs
        }
    }

    private class FakeSoundPlayer : MetronomeSoundPlayer {
        data class PlayedBeat(val isAccent: Boolean, val volume: Int)

        val playedBeats = mutableListOf<PlayedBeat>()
        var stoppedCount = 0

        override fun playBeat(isAccent: Boolean, volumePercent: Int) {
            playedBeats.add(PlayedBeat(isAccent, volumePercent))
        }

        override fun stop() {
            stoppedCount++
        }

        override fun release() {}
    }

    private lateinit var testTimeProvider: TestTimeProvider
    private lateinit var clock: PlaybackClock
    private lateinit var engine: PlaybackEngine
    private lateinit var soundPlayer: FakeSoundPlayer
    private lateinit var scheduler: MetronomeScheduler
    private val testScope = CoroutineScope(Dispatchers.Default)
    private var playbackSpeed: Float = 1.0f

    private val sampleSong = SongData(
        title = "Test Song",
        durationMs = 10_000L,
        events = emptyList()
    )

    private var metronomeConfig = MetronomeConfig(
        enabled = true,
        timingMode = MetronomeTimingMode.MANUAL,
        bpm = 120,
        beatsPerBar = 4,
        subdivision = BeatSubdivision.QUARTER,
        volumePercent = 80
    )

    @Before
    fun setUp() {
        testTimeProvider = TestTimeProvider(1000L)
        clock = PlaybackClock(timeProvider = testTimeProvider)
        engine = PlaybackEngine(clock = clock)
        engine.setSong(sampleSong)
        soundPlayer = FakeSoundPlayer()

        scheduler = MetronomeScheduler(
            timeProvider = { engine.getCurrentPositionMs(allowNegative = true) },
            speedProvider = { playbackSpeed },
            isPlayingProvider = { engine.state.value is PlaybackState.Playing },
            durationProvider = { 10_000L },
            loopBoundsProvider = { Pair(engine.loopStartMs, engine.loopEndMs) },
            soundPlayer = soundPlayer,
            scope = testScope,
            toleratedRealTimeDelayMs = 30L,
            autoStartTicker = false
        )

        val timeline = MetronomeTimingResolver.resolveTimeline(metronomeConfig, timingMetadata = null, durationMs = 10_000L)
        scheduler.updateConfig(metronomeConfig)
        scheduler.applyResolvedTimeline(timeline)
    }

    @After
    fun tearDown() {
        scheduler.release()
        engine.release()
        testScope.cancel()
    }

    /**
     * OverlayService.pausePlaybackForSettings() と同等のロジックをテスト
     */
    private fun pausePlaybackForSettings() {
        when (engine.state.value) {
            is PlaybackState.Playing -> engine.pause()
            is PlaybackState.CountingDown -> engine.stop()
            else -> {}
        }
        scheduler.pauseForOpeningSettings()
    }

    @Test
    fun playing_whenOpeningSettings_pausesPlaybackAndStopsMetronome() = runBlocking {
        engine.play(skipCountdown = true)
        scheduler.onPlaybackStateChanged(engine.state.value)

        // 0ms 発音
        scheduler.processTick(engine.getCurrentPositionMs())
        assertEquals(1, soundPlayer.playedBeats.size)

        // 500ms 経過
        testTimeProvider.advance(500L)
        scheduler.processTick(engine.getCurrentPositionMs())
        assertEquals(2, soundPlayer.playedBeats.size)
        assertEquals(500L, engine.getCurrentPositionMs())

        // 設定画面を開く
        pausePlaybackForSettings()

        // 状態検証: PlaybackEngine は Paused になり、位置(500ms)を保持
        assertTrue(engine.state.value is PlaybackState.Paused)
        assertEquals(500L, engine.getCurrentPositionMs())
        assertTrue(metronomeConfig.enabled) // enabled は変更されない
        assertTrue(scheduler.isSettingsActive)
        assertTrue(soundPlayer.stoppedCount >= 1)

        // 設定画面内で時間が進んだり tick が呼ばれても発音しない
        testTimeProvider.advance(500L)
        scheduler.processTick(engine.getCurrentPositionMs())
        assertEquals(2, soundPlayer.playedBeats.size)

        // 設定画面内で evaluateFinalBeat が呼ばれても発音しない
        assertFalse(scheduler.evaluateFinalBeat(1000L))
        assertEquals(2, soundPlayer.playedBeats.size)
    }

    @Test
    fun openingSettings_isIdempotent() = runBlocking {
        engine.play(skipCountdown = true)
        testTimeProvider.advance(300L)

        // 1回目の呼び出し
        pausePlaybackForSettings()
        assertEquals(300L, engine.getCurrentPositionMs())
        assertTrue(scheduler.isSettingsActive)

        // 2回目の呼び出し（MainActivity.onStart / setOverlayVisibleForSettings(false) と openMainActivity の重複）
        pausePlaybackForSettings()
        assertEquals(300L, engine.getCurrentPositionMs())
        assertTrue(engine.state.value is PlaybackState.Paused)
        assertTrue(scheduler.isSettingsActive)
    }

    @Test
    fun returningFromSettings_doesNotAutoResume_butCanResumeOnPlayButton() = runBlocking {
        engine.play(skipCountdown = true)
        testTimeProvider.advance(500L)
        scheduler.processTick(engine.getCurrentPositionMs())
        val playedBeforeSettings = soundPlayer.playedBeats.size

        pausePlaybackForSettings()
        assertTrue(engine.state.value is PlaybackState.Paused)

        // 設定画面からオーバーレイに戻る (setOverlayVisibleForSettings(true))
        scheduler.onSettingsClosed()
        assertFalse(scheduler.isSettingsActive)

        // 自動再開はしないため、エンジンは Paused のまま
        assertTrue(engine.state.value is PlaybackState.Paused)
        assertEquals(500L, engine.getCurrentPositionMs())

        // 再生ボタンを押して再開
        engine.resume()
        scheduler.onPlaybackStateChanged(engine.state.value)
        assertTrue(engine.state.value is PlaybackState.Playing)
        assertEquals(500L, engine.getCurrentPositionMs())

        // 次の拍（1000ms）へ進めて発音確認
        testTimeProvider.advance(500L) // 現在位置 1000ms
        scheduler.processTick(engine.getCurrentPositionMs())
        assertEquals(playedBeforeSettings + 1, soundPlayer.playedBeats.size)
    }

    @Test
    fun abRepeat_isPreservedAcrossSettingsScreen() = runBlocking {
        // ABリピートを設定 [1000, 3000]
        engine.setLoopStart(1000L)
        engine.setLoopEnd(3000L)

        engine.play(skipCountdown = true)
        engine.seekTo(1500L)

        pausePlaybackForSettings()

        // ABリピート設定が保持されていることを確認
        assertEquals(1000L, engine.loopStartMs)
        assertEquals(3000L, engine.loopEndMs)
        assertEquals(1500L, engine.getCurrentPositionMs())

        scheduler.onSettingsClosed()
        assertEquals(1000L, engine.loopStartMs)
        assertEquals(3000L, engine.loopEndMs)
    }

    @Test
    fun countingDown_whenOpeningSettings_stopsCountdown() = runBlocking {
        engine.countdownMs = 1000L
        engine.play(skipCountdown = false)
        delay(50L)
        assertTrue(engine.state.value is PlaybackState.CountingDown)

        pausePlaybackForSettings()

        // カウントダウンが安全に停止
        assertEquals(PlaybackState.Stopped, engine.state.value)
        assertTrue(scheduler.isSettingsActive)
    }
}
