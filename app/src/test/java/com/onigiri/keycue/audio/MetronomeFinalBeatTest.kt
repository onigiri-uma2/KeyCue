package com.onigiri.keycue.audio

import com.onigiri.keycue.model.BeatSubdivision
import com.onigiri.keycue.model.MetronomeConfig
import com.onigiri.keycue.model.MetronomeTimingMode
import com.onigiri.keycue.playback.PlaybackState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 楽曲終了境界における最終拍の発音取りこぼし防止および重複防止のテスト。
 */
class MetronomeFinalBeatTest {

    private class FakeSoundPlayer : MetronomeSoundPlayer {
        data class PlayedBeat(val isAccent: Boolean, val volume: Int)

        val playedBeats = mutableListOf<PlayedBeat>()
        var stoppedCount = 0
        var releasedCount = 0

        override fun playBeat(isAccent: Boolean, volumePercent: Int) {
            playedBeats.add(PlayedBeat(isAccent, volumePercent))
        }

        override fun stop() {
            stoppedCount++
        }

        override fun release() {
            releasedCount++
        }

        fun clear() {
            playedBeats.clear()
            stoppedCount = 0
        }
    }

    private class TestFixture {
        val testScope = CoroutineScope(Dispatchers.Default)
        var currentPositionMs: Long = 0L
        var speed: Float = 1.0f
        var isCurrentlyPlaying: Boolean = false
        var durationMs: Long = 1000L
        var loopStartMs: Long? = null
        var loopEndMs: Long? = null

        val soundPlayer = FakeSoundPlayer()

        val scheduler = MetronomeScheduler(
            timeProvider = { currentPositionMs },
            speedProvider = { speed },
            isPlayingProvider = { isCurrentlyPlaying },
            durationProvider = { durationMs },
            loopBoundsProvider = {
                val start = loopStartMs
                val end = loopEndMs
                if (start != null && end != null && start < end && (end - start >= 300L)) {
                    Pair(start, end)
                } else {
                    Pair(null, null)
                }
            },
            soundPlayer = soundPlayer,
            scope = testScope,
            toleratedRealTimeDelayMs = 30L,
            autoStartTicker = false
        )

        fun setPlaying(playing: Boolean) {
            isCurrentlyPlaying = playing
            scheduler.onPlaybackStateChanged(
                if (playing) PlaybackState.Playing(currentPositionMs)
                else PlaybackState.Paused(currentPositionMs)
            )
        }

        fun cleanup() {
            scheduler.release()
            testScope.cancel()
        }
    }

    private lateinit var f: TestFixture

    @Before
    fun setUp() {
        f = TestFixture()
    }

    @After
    fun tearDown() {
        f.cleanup()
    }

    @Test
    fun finalBeat_onExactGrid_playsSuccessfullyOnTick() {
        // 120 BPM (500ms間隔), durationMs = 1000ms -> 0ms, 500ms, 1000ms に拍が存在
        f.durationMs = 1000L
        val config = MetronomeConfig(
            enabled = true,
            timingMode = MetronomeTimingMode.MANUAL,
            bpm = 120,
            beatsPerBar = 4,
            subdivision = BeatSubdivision.QUARTER,
            volumePercent = 80
        )
        val timeline = MetronomeTimingResolver.resolveTimeline(config, timingMetadata = null, durationMs = 1000L)
        f.scheduler.updateConfig(config)
        f.scheduler.applyResolvedTimeline(timeline)
        f.setPlaying(true)

        // 0ms
        f.currentPositionMs = 0L
        f.scheduler.processTick(f.currentPositionMs)
        assertEquals(1, f.soundPlayer.playedBeats.size)

        // 500ms
        f.currentPositionMs = 500L
        f.scheduler.processTick(f.currentPositionMs)
        assertEquals(2, f.soundPlayer.playedBeats.size)

        // 1000ms (最終音符・楽曲終了時刻と一致)
        f.currentPositionMs = 1000L
        f.scheduler.processTick(f.currentPositionMs)
        assertEquals(3, f.soundPlayer.playedBeats.size)

        // evaluateFinalBeat を呼んでも既に発音済みなので重複発音しない
        val evaluated = f.scheduler.evaluateFinalBeat(1000L)
        assertFalse(evaluated)
        assertEquals(3, f.soundPlayer.playedBeats.size)
    }

    @Test
    fun finalBeat_evaluatedBeforeFinish_playsOnceIfNotYetPlayed() {
        // 120 BPM, durationMs = 1000ms. Tickerが990msまでしか進まず、1000msのtickを実行する前にFinishへ遷移した場合
        f.durationMs = 1000L
        val config = MetronomeConfig(
            enabled = true,
            timingMode = MetronomeTimingMode.MANUAL,
            bpm = 120,
            beatsPerBar = 4,
            subdivision = BeatSubdivision.QUARTER,
            volumePercent = 80
        )
        val timeline = MetronomeTimingResolver.resolveTimeline(config, timingMetadata = null, durationMs = 1000L)
        f.scheduler.updateConfig(config)
        f.scheduler.applyResolvedTimeline(timeline)
        f.setPlaying(true)

        // 0ms
        f.currentPositionMs = 0L
        f.scheduler.processTick(f.currentPositionMs)
        assertEquals(1, f.soundPlayer.playedBeats.size)

        // 500ms
        f.currentPositionMs = 500L
        f.scheduler.processTick(f.currentPositionMs)
        assertEquals(2, f.soundPlayer.playedBeats.size)

        // Tickerは990msまで進んだ
        f.currentPositionMs = 990L
        f.scheduler.processTick(f.currentPositionMs)
        assertEquals(2, f.soundPlayer.playedBeats.size)

        // PlaybackEngineが durationMs(1000L) に達して Finished に遷移する直前に evaluateFinalBeat(1000L) を呼び出す
        val evaluated = f.scheduler.evaluateFinalBeat(1000L)
        assertTrue(evaluated)
        assertEquals(3, f.soundPlayer.playedBeats.size)

        // 再度 evaluateFinalBeat を呼んでも重複しない
        val evaluatedAgain = f.scheduler.evaluateFinalBeat(1000L)
        assertFalse(evaluatedAgain)
        assertEquals(3, f.soundPlayer.playedBeats.size)
    }

    @Test
    fun finalBeat_offGrid_doesNotPlayExtraClick() {
        // durationMs = 750ms (拍グリッドは0ms, 500ms, 次は1000msだが楽曲は750msで終了)
        f.durationMs = 750L
        val config = MetronomeConfig(
            enabled = true,
            timingMode = MetronomeTimingMode.MANUAL,
            bpm = 120,
            beatsPerBar = 4,
            subdivision = BeatSubdivision.QUARTER,
            volumePercent = 80
        )
        val timeline = MetronomeTimingResolver.resolveTimeline(config, timingMetadata = null, durationMs = 750L)
        f.scheduler.updateConfig(config)
        f.scheduler.applyResolvedTimeline(timeline)
        f.setPlaying(true)

        // 0ms
        f.currentPositionMs = 0L
        f.scheduler.processTick(f.currentPositionMs)
        assertEquals(1, f.soundPlayer.playedBeats.size)

        // 500ms
        f.currentPositionMs = 500L
        f.scheduler.processTick(f.currentPositionMs)
        assertEquals(2, f.soundPlayer.playedBeats.size)

        // 750ms
        f.currentPositionMs = 750L
        f.scheduler.processTick(f.currentPositionMs)
        assertEquals(2, f.soundPlayer.playedBeats.size)

        // evaluateFinalBeat(750L) -> 750msに拍はないので発音されない
        val evaluated = f.scheduler.evaluateFinalBeat(750L)
        assertFalse(evaluated)
        assertEquals(2, f.soundPlayer.playedBeats.size)
    }

    @Test
    fun finishedState_doesNotPlayAnyMoreClicks() {
        f.durationMs = 1000L
        val config = MetronomeConfig(
            enabled = true,
            timingMode = MetronomeTimingMode.MANUAL,
            bpm = 120,
            beatsPerBar = 4,
            subdivision = BeatSubdivision.QUARTER,
            volumePercent = 80
        )
        val timeline = MetronomeTimingResolver.resolveTimeline(config, timingMetadata = null, durationMs = 1000L)
        f.scheduler.updateConfig(config)
        f.scheduler.applyResolvedTimeline(timeline)
        f.setPlaying(true)

        // 0ms, 500ms, 1000ms と進めて最終拍まで発音
        f.currentPositionMs = 0L
        f.scheduler.processTick(f.currentPositionMs)
        f.currentPositionMs = 500L
        f.scheduler.processTick(f.currentPositionMs)
        f.currentPositionMs = 1000L
        f.scheduler.processTick(f.currentPositionMs)
        assertEquals(3, f.soundPlayer.playedBeats.size)

        // Finished 状態へ遷移
        f.isCurrentlyPlaying = false
        f.scheduler.onPlaybackStateChanged(PlaybackState.Finished(1000L))

        // その後に tick や evaluateFinalBeat が呼ばれても発音しない
        f.scheduler.processTick(f.currentPositionMs)
        assertFalse(f.scheduler.evaluateFinalBeat(1000L))
        assertEquals(3, f.soundPlayer.playedBeats.size)
    }

    @Test
    fun pausedOrDisabled_doesNotPlayFinalBeat() {
        f.durationMs = 1000L
        val config = MetronomeConfig(
            enabled = false, // OFF
            timingMode = MetronomeTimingMode.MANUAL,
            bpm = 120,
            beatsPerBar = 4,
            subdivision = BeatSubdivision.QUARTER,
            volumePercent = 80
        )
        val timeline = MetronomeTimingResolver.resolveTimeline(config, timingMetadata = null, durationMs = 1000L)
        f.scheduler.updateConfig(config)
        f.scheduler.applyResolvedTimeline(timeline)
        f.setPlaying(true)

        // Metro OFFのときは evaluateFinalBeat も発音しない
        assertFalse(f.scheduler.evaluateFinalBeat(1000L))
        assertEquals(0, f.soundPlayer.playedBeats.size)

        // Metro ON だが Pause の場合
        val enabledConfig = config.copy(enabled = true)
        val enabledTimeline = MetronomeTimingResolver.resolveTimeline(enabledConfig, timingMetadata = null, durationMs = 1000L)
        f.scheduler.updateConfig(enabledConfig)
        f.scheduler.applyResolvedTimeline(enabledTimeline)
        f.setPlaying(false) // Paused

        assertFalse(f.scheduler.evaluateFinalBeat(1000L))
        assertEquals(0, f.soundPlayer.playedBeats.size)
    }

    @Test
    fun settingsActive_doesNotPlayFinalBeat() {
        f.durationMs = 1000L
        val config = MetronomeConfig(
            enabled = true,
            timingMode = MetronomeTimingMode.MANUAL,
            bpm = 120,
            beatsPerBar = 4,
            subdivision = BeatSubdivision.QUARTER,
            volumePercent = 80
        )
        val timeline = MetronomeTimingResolver.resolveTimeline(config, timingMetadata = null, durationMs = 1000L)
        f.scheduler.updateConfig(config)
        f.scheduler.applyResolvedTimeline(timeline)
        f.setPlaying(true)

        // 設定画面を開いた状態
        f.scheduler.pauseForOpeningSettings()
        assertTrue(f.scheduler.isSettingsActive)

        // 設定画面が開いている間は evaluateFinalBeat も processTick も発音不可
        f.currentPositionMs = 1000L
        f.scheduler.processTick(f.currentPositionMs)
        assertFalse(f.scheduler.evaluateFinalBeat(1000L))
        assertEquals(0, f.soundPlayer.playedBeats.size)

        // 設定画面を閉じた後
        f.scheduler.onSettingsClosed()
        assertFalse(f.scheduler.isSettingsActive)
    }

    @Test
    fun abRepeat_doesNotPlayPastLoopEnd() {
        // duration 2000ms, ABリピート [0, 800]
        f.durationMs = 2000L
        f.loopStartMs = 0L
        f.loopEndMs = 800L // 800ms 以降はリピート外
        val config = MetronomeConfig(
            enabled = true,
            timingMode = MetronomeTimingMode.MANUAL,
            bpm = 120,
            beatsPerBar = 4,
            subdivision = BeatSubdivision.QUARTER,
            volumePercent = 80
        )
        val timeline = MetronomeTimingResolver.resolveTimeline(config, timingMetadata = null, durationMs = 2000L)
        f.scheduler.updateConfig(config)
        f.scheduler.applyResolvedTimeline(timeline)
        f.setPlaying(true)

        // 0ms (発音)
        f.currentPositionMs = 0L
        f.scheduler.processTick(f.currentPositionMs)
        assertEquals(1, f.soundPlayer.playedBeats.size)

        // 500ms (発音)
        f.currentPositionMs = 500L
        f.scheduler.processTick(f.currentPositionMs)
        assertEquals(2, f.soundPlayer.playedBeats.size)

        // 1000ms は loopEndMs(800) を超えているため発音されない
        f.currentPositionMs = 1000L
        f.scheduler.processTick(f.currentPositionMs)
        assertEquals(2, f.soundPlayer.playedBeats.size)

        // evaluateFinalBeat(1000L) も loopEndMs を超えているため発音されない
        assertFalse(f.scheduler.evaluateFinalBeat(1000L))
        assertEquals(2, f.soundPlayer.playedBeats.size)
    }
}
