package com.onigiri.keycue.audio

import com.onigiri.keycue.model.BeatSubdivision
import com.onigiri.keycue.model.MetronomeConfig
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
 * [MetronomeScheduler] の再生制御・時間同期・遅延制御に関する単体テスト。
 *
 * 外部ライブラリや非同期スリープに依存せず、決定論的な [MetronomeScheduler.processTick] を用いて
 * 厳密かつ高速に検証します。
 */
class MetronomeSchedulerTest {

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
        var durationMs: Long = 60_000L
        var loopStartMs: Long? = null
        var loopEndMs: Long? = null

        val soundPlayer = FakeSoundPlayer()

        val scheduler = MetronomeScheduler(
            timeProvider = { currentPositionMs },
            speedProvider = { speed },
            isPlayingProvider = { isCurrentlyPlaying },
            durationProvider = { durationMs },
            loopBoundsProvider = { Pair(loopStartMs, loopEndMs) },
            soundPlayer = soundPlayer,
            scope = testScope,
            toleratedDelayMs = 30L
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
    fun `初期状態(enabled=false)では無音`() {
        f.currentPositionMs = 0L
        f.setPlaying(true)
        val played = f.scheduler.processTick(f.currentPositionMs)

        assertFalse(played)
        assertEquals("enabled=false なので発音されない", 0, f.soundPlayer.playedBeats.size)
    }

    @Test
    fun `Stopped, Paused, CountingDown では発音しない`() {
        f.scheduler.updateConfig(MetronomeConfig(enabled = true, bpm = 120))

        // Stopped
        f.isCurrentlyPlaying = false
        f.scheduler.onPlaybackStateChanged(PlaybackState.Stopped)
        assertFalse(f.scheduler.processTick(0L))
        assertEquals(0, f.soundPlayer.playedBeats.size)

        // CountingDown
        f.scheduler.onPlaybackStateChanged(PlaybackState.CountingDown(remainingMs = 2000L, countNumber = 2))
        assertFalse(f.scheduler.processTick(0L))
        assertEquals(0, f.soundPlayer.playedBeats.size)

        // Paused
        f.scheduler.onPlaybackStateChanged(PlaybackState.Paused(positionMs = 500L))
        assertFalse(f.scheduler.processTick(500L))
        assertEquals(0, f.soundPlayer.playedBeats.size)
    }

    @Test
    fun `Playing時のみBPM120の一定間隔で発音される`() {
        f.scheduler.updateConfig(MetronomeConfig(enabled = true, bpm = 120)) // 500ms間隔

        f.currentPositionMs = 0L
        f.setPlaying(true)

        // 0ms時点: 第1拍（アクセント音）
        val played0 = f.scheduler.processTick(0L)
        assertTrue("0msで発音", played0)
        assertEquals(1, f.soundPlayer.playedBeats.size)
        assertTrue("第1拍はアクセント", f.soundPlayer.playedBeats[0].isAccent)

        // 500ms時点
        f.currentPositionMs = 500L
        val played500 = f.scheduler.processTick(500L)
        assertTrue("500msで発音", played500)
        assertEquals(2, f.soundPlayer.playedBeats.size)
        assertFalse("第2拍は通常音", f.soundPlayer.playedBeats[1].isAccent)

        // 1000ms時点
        f.currentPositionMs = 1000L
        val played1000 = f.scheduler.processTick(1000L)
        assertTrue("1000msで発音", played1000)
        assertEquals(3, f.soundPlayer.playedBeats.size)
    }

    @Test
    fun `同一拍の二重発音なし`() {
        f.scheduler.updateConfig(MetronomeConfig(enabled = true, bpm = 120))

        f.currentPositionMs = 0L
        f.setPlaying(true)
        assertTrue(f.scheduler.processTick(0L))
        assertEquals(1, f.soundPlayer.playedBeats.size)

        // 同じ0ms位置にとどまった状態で再評価しても発音しない
        assertFalse(f.scheduler.processTick(0L))
        assertEquals(1, f.soundPlayer.playedBeats.size)

        // 100ms位置（拍未到達）でも発音しない
        f.currentPositionMs = 100L
        assertFalse(f.scheduler.processTick(100L))
        assertEquals(1, f.soundPlayer.playedBeats.size)
    }

    @Test
    fun `Pauseで停止しResumeで直前の拍を再発音しない`() {
        f.scheduler.updateConfig(MetronomeConfig(enabled = true, bpm = 120))

        f.currentPositionMs = 0L
        f.setPlaying(true)
        f.scheduler.processTick(0L)
        assertEquals(1, f.soundPlayer.playedBeats.size)

        // 500ms拍発音
        f.currentPositionMs = 500L
        f.scheduler.processTick(500L)
        assertEquals(2, f.soundPlayer.playedBeats.size)

        // 510msでPause
        f.currentPositionMs = 510L
        f.isCurrentlyPlaying = false
        f.scheduler.onPlaybackStateChanged(PlaybackState.Paused(510L))
        assertTrue("Pauseでstopが呼ばれた", f.soundPlayer.stoppedCount > 0)

        // Resume: 現在位置 510ms から再開
        f.isCurrentlyPlaying = true
        f.scheduler.onPlaybackStateChanged(PlaybackState.Playing(510L))

        // 510ms時点で評価しても直前の500ms拍は再発音されない
        assertFalse(f.scheduler.processTick(510L))
        assertEquals("直前の500ms拍は再発音されない", 2, f.soundPlayer.playedBeats.size)

        // 1000msに到達したら第3拍が発音される
        f.currentPositionMs = 1000L
        assertTrue(f.scheduler.processTick(1000L))
        assertEquals(3, f.soundPlayer.playedBeats.size)
    }

    @Test
    fun `前方Seekおよび後方Seekで旧クリック予定を破棄し再同期`() {
        f.scheduler.updateConfig(MetronomeConfig(enabled = true, bpm = 120)) // 0, 500, 1000, 1500...

        f.currentPositionMs = 0L
        f.setPlaying(true)
        f.scheduler.processTick(0L)
        assertEquals(1, f.soundPlayer.playedBeats.size) // 0ms拍

        // 1. 前方Seek: 0ms -> 10,200ms
        f.currentPositionMs = 10_200L
        f.scheduler.onSeek(10_200L)

        // 10,200ms時点では拍未到達（次は10,500ms）
        assertFalse(f.scheduler.processTick(10_200L))
        assertEquals("Seek前の古い拍は連打されない", 1, f.soundPlayer.playedBeats.size)

        // 10,500msに到達
        f.currentPositionMs = 10_500L
        assertTrue(f.scheduler.processTick(10_500L))
        assertEquals(2, f.soundPlayer.playedBeats.size)

        // 2. 後方Seek: 10,500ms -> 2,100ms
        f.currentPositionMs = 2_100L
        f.scheduler.onSeek(2_100L)

        // 2,100ms時点（次は2,500ms）
        assertFalse(f.scheduler.processTick(2_100L))
        assertEquals(2, f.soundPlayer.playedBeats.size)

        // 2,500msに到達
        f.currentPositionMs = 2_500L
        assertTrue(f.scheduler.processTick(2_500L))
        assertEquals(3, f.soundPlayer.playedBeats.size)
    }

    @Test
    fun `処理遅延後に過去のクリックを連打せずスキップする`() {
        f.scheduler.updateConfig(MetronomeConfig(enabled = true, bpm = 120)) // 0, 500, 1000, 1500...

        f.currentPositionMs = 0L
        f.setPlaying(true)
        f.scheduler.processTick(0L)
        assertEquals(1, f.soundPlayer.playedBeats.size) // 0ms拍

        // 端末負荷により 500ms, 1000ms を大きく飛び越えて一気に 1600ms に到達したとする
        f.currentPositionMs = 1600L
        // 1600ms時点では 1500ms から 100ms 経過（> 30ms許容遅延）のため 1500ms もスキップされ発音しない
        assertFalse(f.scheduler.processTick(1600L))
        assertEquals("古い拍は連打されない", 1, f.soundPlayer.playedBeats.size)

        // 次の 2000ms に到達
        f.currentPositionMs = 2000L
        assertTrue(f.scheduler.processTick(2000L))
        assertEquals(2, f.soundPlayer.playedBeats.size)
    }

    @Test
    fun `ABリピート区間内で正しく循環しB地点以降は発音しない`() {
        f.scheduler.updateConfig(MetronomeConfig(enabled = true, bpm = 120)) // 0, 500, 1000, 1500, 2000...
        // A = 750ms, B = 1750ms
        f.loopStartMs = 750L
        f.loopEndMs = 1750L

        f.currentPositionMs = 750L
        f.setPlaying(true)
        assertFalse(f.scheduler.processTick(750L))
        assertEquals(0, f.soundPlayer.playedBeats.size)

        // 1000ms
        f.currentPositionMs = 1000L
        assertTrue(f.scheduler.processTick(1000L))
        assertEquals(1, f.soundPlayer.playedBeats.size)

        // 1500ms
        f.currentPositionMs = 1500L
        assertTrue(f.scheduler.processTick(1500L))
        assertEquals(2, f.soundPlayer.playedBeats.size)

        // B地点 1750ms 到達 -> ループにより A地点 750ms へ巻き戻り
        f.currentPositionMs = 750L
        f.scheduler.onLoopRewound(750L)

        // B地点以降（例: 2000ms）は発音されていない
        assertEquals(2, f.soundPlayer.playedBeats.size)

        // 再び 1000ms
        f.currentPositionMs = 1000L
        assertTrue(f.scheduler.processTick(1000L))
        assertEquals("ABループ後に1000ms拍が再び発音される", 3, f.soundPlayer.playedBeats.size)
    }

    @Test
    fun `ON・OFF連打でも不整合が発生しない`() {
        f.currentPositionMs = 100L
        f.setPlaying(true)

        // ON/OFF を連続トグル
        repeat(5) {
            f.scheduler.updateConfig(MetronomeConfig(enabled = true, bpm = 120))
            f.scheduler.updateConfig(MetronomeConfig(enabled = false, bpm = 120))
        }
        assertFalse(f.scheduler.processTick(100L))
        assertEquals(0, f.soundPlayer.playedBeats.size)

        // 最後に ON
        f.scheduler.updateConfig(MetronomeConfig(enabled = true, bpm = 120))

        // 次の拍 500ms
        f.currentPositionMs = 500L
        assertTrue(f.scheduler.processTick(500L))
        assertEquals(1, f.soundPlayer.playedBeats.size)
    }

    @Test
    fun `曲切替(onSongChanged)で旧曲の音声予定がクリアされる`() {
        f.scheduler.updateConfig(MetronomeConfig(enabled = true, bpm = 120))

        f.currentPositionMs = 0L
        f.setPlaying(true)
        f.scheduler.processTick(0L)
        assertEquals(1, f.soundPlayer.playedBeats.size)

        // 新曲に切り替え
        f.isCurrentlyPlaying = false
        f.scheduler.onSongChanged()
        assertTrue("stopが呼ばれた", f.soundPlayer.stoppedCount > 0)
    }
}
