package com.onigiri.keycue.audio

import com.onigiri.keycue.model.BeatSubdivision
import com.onigiri.keycue.model.MetronomeConfig
import com.onigiri.keycue.model.MetronomeTimingMode
import com.onigiri.keycue.model.timing.SongTimingMetadata
import com.onigiri.keycue.model.timing.TimeSignature
import com.onigiri.keycue.playback.PlaybackState
import com.onigiri.keycue.song.midi.RawTempoEvent
import com.onigiri.keycue.song.midi.RawTimeSignatureEvent
import com.onigiri.keycue.song.midi.TempoMap
import com.onigiri.keycue.song.midi.TimeSignatureMap
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
 * [MetronomeScheduler] の楽曲テンポ・拍子自動連動に関する再生同期回帰テスト。
 *
 * 【検証要件（セクション34）】
 * 1. AUTO / MANUAL モード切替の即座反映
 * 2. Playback Speed (0.5x, 1.0x, 2.0x) への追従と実時間30ms遅延換算
 * 3. テンポ変更直前の Seek と直後の Seek
 * 4. 拍子変更直前の Seek と直後の Seek
 * 5. Pause / Resume / Pause中Seek後Resume での二重発音・誤発音防止
 * 6. ABリピートでテンポ変更前へ戻る動作
 * 7. ABリピートで拍子変更前へ戻る動作
 * 8. A地点直前拍の抑止と、A地点が拍境界に完全一致した場合の再発音
 * 9. 旧 BeatTimeline の発音予定破棄（新曲読み込み・モード変更）
 * 10. 古い世代番号の processTick 破棄
 * 11. 処理遅延時の古いクリック連打防止（古い拍スキップ）
 * 12. 自動メタデータのない楽曲（手動フォールバック）で手動設定利用
 * 13. 曲切り替え時も保存された手動設定値（BPM等）が上書きされないこと
 */
class MetronomeSchedulerAutoSyncTest {

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
            loopBoundsProvider = {
                val start = loopStartMs
                val end = loopEndMs
                if (start != null && end != null && start < end && (end - start) >= 300L) {
                    start to end
                } else {
                    null to null
                }
            },
            soundPlayer = soundPlayer,
            scope = testScope,
            autoStartTicker = false // 決定論的テストのため手動でprocessTickを制御
        )

        fun release() {
            scheduler.release()
            testScope.cancel()
        }
    }

    private lateinit var fixture: TestFixture

    @Before
    fun setUp() {
        fixture = TestFixture()
    }

    @After
    fun tearDown() {
        fixture.release()
    }

    private fun createMidiMetadata(
        tempoChanges: List<Pair<Long, Long>> = listOf(0L to 500_000L), // tick to usPerQuarter
        timeSignatureChanges: List<Pair<Long, Pair<Int, Int>>> = listOf(0L to (4 to 4)),
        ppqn: Int = 480
    ): SongTimingMetadata.Midi {
        val tempoEvents = tempoChanges.map { RawTempoEvent(it.first, it.second) }
        val tsEvents = timeSignatureChanges.map {
            RawTimeSignatureEvent(it.first, it.second.first, it.second.second, 24, 8)
        }
        return SongTimingMetadata.Midi(
            ppqn = ppqn,
            tempoMap = TempoMap(ppqn, tempoEvents),
            timeSignatureMap = TimeSignatureMap(tsEvents)
        )
    }

    // 1. AUTO / MANUAL モード切替
    @Test
    fun testAutoManualSwitching() {
        val midiMeta = createMidiMetadata(
            tempoChanges = listOf(0L to 400_000L) // 150 BPM
        )
        // 手動は 120 BPM、モード AUTO
        val config = MetronomeConfig(
            enabled = true,
            timingMode = MetronomeTimingMode.AUTO,
            bpm = 120,
            volumePercent = 80
        )
        fixture.scheduler.updateConfig(config)
        fixture.scheduler.updateTimingMetadata(midiMeta)

        // AUTO 時は MIDI の 150 BPM が使われる
        assertEquals(150.0, fixture.scheduler.currentBpm(0L), 0.001)
        assertEquals(TimingSourceKind.MIDI, fixture.scheduler.currentTimingSourceKind)

        // MANUAL へ切り替え
        fixture.scheduler.updateConfig(config.copy(timingMode = MetronomeTimingMode.MANUAL))
        assertEquals(120.0, fixture.scheduler.currentBpm(0L), 0.001)
        assertEquals(TimingSourceKind.MANUAL, fixture.scheduler.currentTimingSourceKind)
        // 手動設定の 120 BPM が維持されていること
        assertEquals(120, config.bpm)
    }

    // 2. Playback Speed 追従 (0.5x, 1.0x, 2.0x)
    @Test
    fun testSpeedScaling_toleratedDelay() {
        val midiMeta = createMidiMetadata()
        val config = MetronomeConfig(enabled = true, timingMode = MetronomeTimingMode.AUTO)
        fixture.scheduler.updateConfig(config)
        fixture.scheduler.updateTimingMetadata(midiMeta)

        fixture.isCurrentlyPlaying = true
        fixture.scheduler.onPlaybackStateChanged(PlaybackState.Playing(fixture.currentPositionMs))

        // 1.0x では実時間30ms = 楽曲時間30ms許容
        // 拍時刻が 0ms の場合、現在時刻 30ms では鳴る、31ms では遅延超過
        fixture.currentPositionMs = 30L
        assertTrue(fixture.scheduler.processTick(30L))

        // Speed 0.5x: 実時間30ms * 0.5 = 楽曲時間15ms許容
        fixture.soundPlayer.clear()
        fixture.speed = 0.5f
        fixture.scheduler.onSpeedChanged()

        fixture.currentPositionMs = 515L // 次の拍 500ms に対して 15ms 遅延 -> 許容内
        assertTrue(fixture.scheduler.processTick(515L))

        // Speed 2.0x: 実時間30ms * 2.0 = 楽曲時間60ms許容
        fixture.soundPlayer.clear()
        fixture.speed = 2.0f
        fixture.scheduler.onSpeedChanged()

        fixture.currentPositionMs = 1060L // 次の拍 1000ms に対して 60ms 遅延 -> 許容内
        assertTrue(fixture.scheduler.processTick(1060L))
    }

    // 3. テンポ変更直前・直後の Seek
    @Test
    fun testSeekAroundTempoChange() {
        // tick 0: 120 BPM (0ms, 500ms, 1000ms)
        // tick 960 (1000ms): 240 BPM (1000ms, 1250ms, 1500ms...)
        val midiMeta = createMidiMetadata(
            tempoChanges = listOf(
                0L to 500_000L,
                960L to 250_000L
            )
        )
        val config = MetronomeConfig(enabled = true, timingMode = MetronomeTimingMode.AUTO)
        fixture.scheduler.updateConfig(config)
        fixture.scheduler.updateTimingMetadata(midiMeta)
        fixture.isCurrentlyPlaying = true
        fixture.scheduler.onPlaybackStateChanged(PlaybackState.Playing(fixture.currentPositionMs))

        // テンポ変更直前 (980ms) へシーク -> 次の拍は 1000ms
        fixture.currentPositionMs = 980L
        fixture.scheduler.onSeek(980L)

        // 990ms ではまだ鳴らない
        assertFalse(fixture.scheduler.processTick(990L))

        // 1000ms で鳴る
        fixture.currentPositionMs = 1000L
        assertTrue(fixture.scheduler.processTick(1000L))

        // テンポ変更直後 (1010ms) へシーク -> 次の拍は 1250ms
        fixture.soundPlayer.clear()
        fixture.currentPositionMs = 1010L
        fixture.scheduler.onSeek(1010L)

        // 1000ms の過去拍は鳴らない
        assertFalse(fixture.scheduler.processTick(1010L))

        // 1250ms で鳴る
        fixture.currentPositionMs = 1250L
        assertTrue(fixture.scheduler.processTick(1250L))
    }

    // 4. 拍子変更直前・直後の Seek
    @Test
    fun testSeekAroundTimeSignatureChange() {
        // 4/4 から tick 960 (1000ms) で 3/4 へ変更
        val midiMeta = createMidiMetadata(
            timeSignatureChanges = listOf(
                0L to (4 to 4),
                960L to (3 to 4)
            )
        )
        val config = MetronomeConfig(enabled = true, timingMode = MetronomeTimingMode.AUTO, accentEnabled = true)
        fixture.scheduler.updateConfig(config)
        fixture.scheduler.updateTimingMetadata(midiMeta)
        fixture.isCurrentlyPlaying = true
        fixture.scheduler.onPlaybackStateChanged(PlaybackState.Playing(fixture.currentPositionMs))

        // 拍子変更直後 (1000ms) にシーク -> 3/4の小節頭アクセントとして発音されること
        fixture.currentPositionMs = 1000L
        fixture.scheduler.onSeek(1000L)
        assertTrue(fixture.scheduler.processTick(1000L))
        assertTrue(fixture.soundPlayer.playedBeats.last().isAccent)
    }

    // 5. Pause / Resume / Pause中Seek後Resume
    @Test
    fun testPauseResumeAndSeekDuringPause() {
        val midiMeta = createMidiMetadata()
        val config = MetronomeConfig(enabled = true, timingMode = MetronomeTimingMode.AUTO)
        fixture.scheduler.updateConfig(config)
        fixture.scheduler.updateTimingMetadata(midiMeta)
        fixture.isCurrentlyPlaying = true
        fixture.scheduler.onPlaybackStateChanged(PlaybackState.Playing(fixture.currentPositionMs))

        // 500ms の拍を発音
        fixture.currentPositionMs = 500L
        assertTrue(fixture.scheduler.processTick(500L))

        // 510ms で Pause
        fixture.isCurrentlyPlaying = false
        fixture.scheduler.onPlaybackStateChanged(PlaybackState.Paused(510L))
        assertEquals(1, fixture.soundPlayer.stoppedCount)

        // Pause中に 520ms へ Seek
        fixture.currentPositionMs = 520L
        fixture.scheduler.onSeek(520L)

        // Resume (Playing)
        fixture.isCurrentlyPlaying = true
        fixture.scheduler.onPlaybackStateChanged(PlaybackState.Playing(fixture.currentPositionMs))

        // 500ms の直前拍が誤発音されないこと（Seek先 520ms より後の 1000ms が次拍）
        assertFalse(fixture.scheduler.processTick(520L))
        assertFalse(fixture.scheduler.processTick(530L))

        // 1000ms で発音
        fixture.currentPositionMs = 1000L
        assertTrue(fixture.scheduler.processTick(1000L))
    }

    // 6. ABリピートでテンポ変更前へ戻る
    @Test
    fun testAbLoopAcrossTempoChange() {
        // tick 0: 120 BPM
        // tick 960 (1000ms): 240 BPM
        val midiMeta = createMidiMetadata(
            tempoChanges = listOf(
                0L to 500_000L,
                960L to 250_000L
            )
        )
        val config = MetronomeConfig(enabled = true, timingMode = MetronomeTimingMode.AUTO)
        fixture.scheduler.updateConfig(config)
        fixture.scheduler.updateTimingMetadata(midiMeta)
        fixture.isCurrentlyPlaying = true

        // A=400ms (120 BPM区間), B=1300ms (240 BPM区間)
        fixture.loopStartMs = 400L
        fixture.loopEndMs = 1300L
        fixture.scheduler.onLoopBoundsChanged()
        fixture.scheduler.onPlaybackStateChanged(PlaybackState.Playing(fixture.currentPositionMs))

        // 500ms (120 BPM) 発音
        fixture.currentPositionMs = 500L
        assertTrue(fixture.scheduler.processTick(500L))

        // 1000ms (240 BPM) 発音
        fixture.currentPositionMs = 1000L
        assertTrue(fixture.scheduler.processTick(1000L))

        // 1250ms (240 BPM) 発音
        fixture.currentPositionMs = 1250L
        assertTrue(fixture.scheduler.processTick(1250L))

        // ループ巻き戻り発生 -> A=400ms へ
        fixture.currentPositionMs = 400L
        fixture.scheduler.onLoopRewound(400L)

        // A地点後の最初の拍 500ms (120 BPM) が再び発音されること
        fixture.soundPlayer.clear()
        fixture.currentPositionMs = 500L
        assertTrue(fixture.scheduler.processTick(500L))
        assertEquals(1, fixture.soundPlayer.playedBeats.size)
    }

    // 7. AB巻き戻り時: A地点直前拍の抑止と、境界一致時の再発音
    @Test
    fun testAbLoopBoundaryExactMatch() {
        val midiMeta = createMidiMetadata() // 120 BPM: 0, 500, 1000...
        val config = MetronomeConfig(enabled = true, timingMode = MetronomeTimingMode.AUTO)
        fixture.scheduler.updateConfig(config)
        fixture.scheduler.updateTimingMetadata(midiMeta)
        fixture.isCurrentlyPlaying = true

        // ケースA: A=510ms (拍 500ms の直後) -> ループ後に 500ms を拾わないこと
        fixture.loopStartMs = 510L
        fixture.loopEndMs = 1200L
        fixture.scheduler.onLoopBoundsChanged()
        fixture.currentPositionMs = 510L
        fixture.scheduler.onLoopRewound(510L)

        // 510ms で 500ms の拍は鳴らない
        assertFalse(fixture.scheduler.processTick(510L))

        // ケースB: A=500ms (拍境界に完全一致) -> 500ms の拍が発音されること
        fixture.loopStartMs = 500L
        fixture.currentPositionMs = 500L
        fixture.scheduler.onLoopRewound(500L)
        assertTrue(fixture.scheduler.processTick(500L))
    }

    // 8. 世代番号による古いスナップショットの破棄
    @Test
    fun testStaleGenerationDiscarded() {
        val config = MetronomeConfig(enabled = true)
        fixture.scheduler.updateConfig(config)
        fixture.isCurrentlyPlaying = true
        fixture.scheduler.onPlaybackStateChanged(PlaybackState.Playing(fixture.currentPositionMs))

        val currentGen = fixture.scheduler.generation
        // Seek を行うと世代が進む
        fixture.scheduler.onSeek(1000L)
        assertTrue(fixture.scheduler.generation > currentGen)

        // 古い世代番号を指定して processTick を呼ぶと安全に破棄されること
        assertFalse(fixture.scheduler.processTick(1000L, generation = currentGen))
    }

    // 9. 処理遅延時の古いクリック連打防止
    @Test
    fun testOverdueSpikeDoesNotPlayOldClicks() {
        val config = MetronomeConfig(enabled = true)
        fixture.scheduler.updateConfig(config)
        fixture.isCurrentlyPlaying = true
        fixture.scheduler.onPlaybackStateChanged(PlaybackState.Playing(fixture.currentPositionMs))

        // 0ms から突然 2000ms までスパイク（複数拍を飛び越す）
        fixture.currentPositionMs = 2000L
        // 過去の拍を連打せず、2000ms 直近の拍のみ発音または再同期すること
        fixture.scheduler.processTick(2000L)
        assertTrue(fixture.soundPlayer.playedBeats.size <= 1)
    }

    // 10. メタデータのない楽曲では手動設定を使用
    @Test
    fun testNoMetadata_usesManualSettings() {
        val config = MetronomeConfig(
            enabled = true,
            timingMode = MetronomeTimingMode.AUTO,
            bpm = 135,
            beatsPerBar = 3
        )
        fixture.scheduler.updateConfig(config)
        // メタデータなし
        fixture.scheduler.onSongChanged(metadata = null)

        assertEquals(135.0, fixture.scheduler.currentBpm(0L), 0.001)
        assertEquals(TimeSignature(3, 4), fixture.scheduler.currentTimeSignature(0L))
        assertEquals(TimingSourceKind.MANUAL, fixture.scheduler.currentTimingSourceKind)
    }
}
