package com.onigiri.keycue.overlay

import com.onigiri.keycue.audio.MetronomeScheduler
import com.onigiri.keycue.audio.MetronomeSoundPlayer
import com.onigiri.keycue.audio.MetronomeTimingResolver
import com.onigiri.keycue.data.InMemorySettingsRepository
import com.onigiri.keycue.model.BeatSubdivision
import com.onigiri.keycue.model.MetronomeConfig
import com.onigiri.keycue.model.MetronomeTimingMode
import com.onigiri.keycue.model.NoteEvent
import com.onigiri.keycue.model.SongData
import com.onigiri.keycue.model.timing.SongTimingMetadata
import com.onigiri.keycue.model.timing.TimeSignature
import com.onigiri.keycue.playback.PlaybackState
import com.onigiri.keycue.song.midi.RawTempoEvent
import com.onigiri.keycue.song.midi.TempoMap
import com.onigiri.keycue.song.midi.TimeSignatureMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Control Overlay のメトロノーム操作拡張・コールバック・本体設定同期、
 * および AUTO/MANUAL フォールバック・MIDIテンポ追従の単体テスト。
 */
class ControlOverlayMetronomeControlTest {

    private lateinit var settingsRepository: InMemorySettingsRepository
    private val testScope = CoroutineScope(Dispatchers.Default)

    @Before
    fun setUp() {
        settingsRepository = InMemorySettingsRepository()
    }

    @After
    fun tearDown() {
        testScope.cancel()
    }

    @Test
    fun callback_timingModeChange_updatesSettingsAtomically() = runBlocking {
        // 初期状態は AUTO
        val initialMode = settingsRepository.metronomeConfig.value.timingMode
        assertEquals(MetronomeTimingMode.AUTO, initialMode)

        // OverlayService 側のコールバック処理シミュレーション
        val onTimingModeChange: (MetronomeTimingMode) -> Unit = { mode ->
            runBlocking {
                settingsRepository.updateMetronomeConfig { it.copy(timingMode = mode) }
            }
        }

        onTimingModeChange(MetronomeTimingMode.MANUAL)
        assertEquals(MetronomeTimingMode.MANUAL, settingsRepository.metronomeConfig.value.timingMode)

        onTimingModeChange(MetronomeTimingMode.AUTO)
        assertEquals(MetronomeTimingMode.AUTO, settingsRepository.metronomeConfig.value.timingMode)
    }

    @Test
    fun callback_bpmChange_updatesSettingsAndClampsRange() = runBlocking {
        val onBpmChange: (Int) -> Unit = { bpm ->
            runBlocking {
                val clamped = bpm.coerceIn(40, 240)
                settingsRepository.updateMetronomeConfig { it.copy(bpm = clamped) }
            }
        }

        onBpmChange(140)
        assertEquals(140, settingsRepository.metronomeConfig.value.bpm)

        // 下限クランプ
        onBpmChange(20)
        assertEquals(40, settingsRepository.metronomeConfig.value.bpm)

        // 上限クランプ
        onBpmChange(300)
        assertEquals(240, settingsRepository.metronomeConfig.value.bpm)
    }

    @Test
    fun callback_beatsPerBarChange_updatesSettings() = runBlocking {
        val onBeatsPerBarChange: (Int) -> Unit = { beats ->
            runBlocking {
                settingsRepository.updateMetronomeConfig { it.copy(beatsPerBar = beats) }
            }
        }

        onBeatsPerBarChange(3)
        assertEquals(3, settingsRepository.metronomeConfig.value.beatsPerBar)

        onBeatsPerBarChange(4)
        assertEquals(4, settingsRepository.metronomeConfig.value.beatsPerBar)
    }

    @Test
    fun autoMode_withoutSongBpm_fallsBackToManualBpm() {
        // Sky Studio の楽曲など、BPMメタデータを持たない SongData (validBpm = null)
        val skySong = SongData(
            title = "Sky Song",
            durationMs = 10_000L,
            events = listOf(NoteEvent(0L, 0), NoteEvent(1000L, 1)),
            timingMetadata = SongTimingMetadata.SkyStudio(rawBpm = null, validBpm = null)
        )

        val config = MetronomeConfig(
            enabled = true,
            timingMode = MetronomeTimingMode.AUTO,
            bpm = 130, // 手動フォールバック値
            beatsPerBar = 4
        )

        // MetronomeTimingResolver によるタイムライン計算
        val timeline = MetronomeTimingResolver.resolveTimeline(config, skySong.timingMetadata, skySong.durationMs)

        // BPMメタデータがないため、手動設定の 130 BPM がフォールバックとして採用される
        assertEquals(130.0, timeline.bpmAt(0L), 0.001)
        assertFalse(timeline.isBpmAuto)

        // 表示テキストの検証（AUTOかつBPM手動フォールバック）
        val infoText = if (!timeline.isBpmAuto) {
            "AUTO（BPM手動値を使用）"
        } else {
            "AUTO ${timeline.bpmAt(0L).toInt()} BPM / ${timeline.timeSignatureAt(0L).displayString}"
        }
        assertEquals("AUTO（BPM手動値を使用）", infoText)
    }

    @Test
    fun autoMode_skySong_manualBeatsPerBarIsFunctional() {
        // Sky Studio 楽曲（BPM 120 自動取得）で拍子を手動で 3/4 または 4/4 に変更した場合
        val skySong = SongData(
            title = "Sky Song Waltz",
            durationMs = 6000L,
            events = listOf(NoteEvent(0L, 0)),
            timingMetadata = SongTimingMetadata.SkyStudio(rawBpm = 120.0, validBpm = 120)
        )

        // 3/4 拍子
        val config34 = MetronomeConfig(
            enabled = true,
            timingMode = MetronomeTimingMode.AUTO,
            bpm = 120,
            beatsPerBar = 3
        )
        val timeline34 = MetronomeTimingResolver.resolveTimeline(config34, skySong.timingMetadata, skySong.durationMs)
        assertEquals(3, timeline34.timeSignatureAt(0L).numerator)
        assertFalse(timeline34.isTimeSignatureAuto) // 拍子は手動設定

        // 拍のアクセント検証 (0拍目がアクセント、3拍目が次のアクセント)
        assertTrue(timeline34.beatForIndex(0L)!!.isAccent)
        assertFalse(timeline34.beatForIndex(1L)!!.isAccent)
        assertFalse(timeline34.beatForIndex(2L)!!.isAccent)
        assertTrue(timeline34.beatForIndex(3L)!!.isAccent)

        // 4/4 拍子へ切り替え
        val config44 = config34.copy(beatsPerBar = 4)
        val timeline44 = MetronomeTimingResolver.resolveTimeline(config44, skySong.timingMetadata, skySong.durationMs)
        assertEquals(4, timeline44.timeSignatureAt(0L).numerator)
        assertTrue(timeline44.beatForIndex(0L)!!.isAccent)
        assertFalse(timeline44.beatForIndex(1L)!!.isAccent)
        assertFalse(timeline44.beatForIndex(2L)!!.isAccent)
        assertFalse(timeline44.beatForIndex(3L)!!.isAccent)
        assertTrue(timeline44.beatForIndex(4L)!!.isAccent)
    }

    @Test
    fun midiTempoChange_andPlaybackSpeedTracking_preserved() {
        class MockSoundPlayer : MetronomeSoundPlayer {
            val playedBeats = mutableListOf<Boolean>()
            override fun playBeat(isAccent: Boolean, volumePercent: Int) {
                playedBeats.add(isAccent)
            }
            override fun stop() {}
            override fun release() {}
        }

        val soundPlayer = MockSoundPlayer()
        var currentPositionMs = 0L
        var playbackSpeed = 1.0f

        val scheduler = MetronomeScheduler(
            timeProvider = { currentPositionMs },
            speedProvider = { playbackSpeed },
            isPlayingProvider = { true },
            durationProvider = { 10_000L },
            loopBoundsProvider = { Pair(null, null) },
            soundPlayer = soundPlayer,
            scope = testScope,
            toleratedRealTimeDelayMs = 30L,
            autoStartTicker = false
        )

        // MIDI楽曲（120BPM: 500,000 us/quarter）
        val ppqn = 480
        val tempoMap = TempoMap(ppqn, listOf(RawTempoEvent(0L, 500_000L)))
        val tsMap = TimeSignatureMap(emptyList()) // 4/4
        val midiMetadata = SongTimingMetadata.Midi(
            ppqn = ppqn,
            tempoMap = tempoMap,
            timeSignatureMap = tsMap
        )

        val midiSong = SongData(
            title = "Midi Song",
            durationMs = 10_000L,
            events = listOf(NoteEvent(0L, 0)),
            timingMetadata = midiMetadata
        )

        val config = MetronomeConfig(
            enabled = true,
            timingMode = MetronomeTimingMode.AUTO,
            bpm = 90 // 手動値（AUTOではMIDIの120が優先される）
        )

        val timeline = MetronomeTimingResolver.resolveTimeline(config, midiSong.timingMetadata, midiSong.durationMs)
        assertEquals(120.0, timeline.bpmAt(0L), 0.001)
        assertTrue(timeline.isBpmAuto)

        scheduler.updateConfig(config)
        scheduler.applyResolvedTimeline(timeline, midiSong.timingMetadata)
        scheduler.onPlaybackStateChanged(PlaybackState.Playing(0L))

        // 速度 1.5x の場合でも、再生位置（ミリ秒）に基づいて正しく発音判定される
        playbackSpeed = 1.5f
        currentPositionMs = 0L
        scheduler.processTick(currentPositionMs)
        assertEquals(1, soundPlayer.playedBeats.size)

        // 120 BPM = 500ms間隔
        currentPositionMs = 500L
        scheduler.processTick(currentPositionMs)
        assertEquals(2, soundPlayer.playedBeats.size)

        scheduler.release()
    }
}
