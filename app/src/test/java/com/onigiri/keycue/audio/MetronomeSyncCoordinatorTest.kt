package com.onigiri.keycue.audio

import com.onigiri.keycue.model.BeatSubdivision
import com.onigiri.keycue.model.MetronomeConfig
import com.onigiri.keycue.model.MetronomeTimingMode
import com.onigiri.keycue.model.SongData
import com.onigiri.keycue.model.SongFormat
import com.onigiri.keycue.model.timing.SongTimingMetadata
import com.onigiri.keycue.playback.PlaybackState
import com.onigiri.keycue.song.midi.RawTempoEvent
import com.onigiri.keycue.song.midi.RawTimeSignatureEvent
import com.onigiri.keycue.song.midi.TempoMap
import com.onigiri.keycue.song.midi.TimeSignatureMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [MetronomeSyncCoordinator] の非同期タイムライン解決、ジョブキャンセル、
 * 世代管理、および即時ミュートの直接検証テスト。
 */
class MetronomeSyncCoordinatorTest {

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

    private lateinit var testScope: CoroutineScope
    private lateinit var soundPlayer: FakeSoundPlayer
    private lateinit var scheduler: MetronomeScheduler
    private lateinit var coordinator: MetronomeSyncCoordinator

    private var currentPositionMs: Long = 0L
    private var isPlaying: Boolean = true
    private var timelineAppliedCount = 0

    @Before
    fun setUp() {
        testScope = CoroutineScope(Dispatchers.Default)
        soundPlayer = FakeSoundPlayer()
        currentPositionMs = 0L
        isPlaying = true
        timelineAppliedCount = 0

        scheduler = MetronomeScheduler(
            timeProvider = { currentPositionMs },
            speedProvider = { 1.0f },
            isPlayingProvider = { isPlaying },
            durationProvider = { 60_000L },
            loopBoundsProvider = { null to null },
            soundPlayer = soundPlayer,
            scope = testScope,
            autoStartTicker = false
        )

        coordinator = MetronomeSyncCoordinator(
            scope = testScope,
            scheduler = scheduler,
            defaultDispatcher = Dispatchers.Default,
            mainDispatcher = Dispatchers.Default, // 単体テスト環境のためDefaultディスパッチャを使用
            onTimelineApplied = {
                timelineAppliedCount++
            }
        )
    }

    @After
    fun tearDown() {
        coordinator.cancel()
        scheduler.release()
        testScope.cancel()
    }

    private fun createSong(title: String, bpm: Double): SongData {
        val usPerQuarter = (60_000_000.0 / bpm).toLong()
        val ppqn = 480
        val tempoMap = TempoMap(ppqn, listOf(RawTempoEvent(0L, usPerQuarter)))
        val tsMap = TimeSignatureMap(listOf(RawTimeSignatureEvent(0L, 4, 2, 24, 8)))
        val metadata = SongTimingMetadata.Midi(
            tempoMap = tempoMap,
            timeSignatureMap = tsMap,
            ppqn = ppqn,
            hasExplicitTempo = true,
            hasExplicitTimeSignature = true
        )
        return SongData(
            title = title,
            durationMs = 60_000L,
            events = emptyList(),
            timingMetadata = metadata
        )
    }

    // 1. 楽曲の連続変更時に先行非同期ジョブが確実にキャンセルされることの直接検証
    @Test
    fun testJobCancellation_onRapidSongChange() = runBlocking {
        val songA = createSong("SongA", 100.0)
        val songB = createSong("SongB", 200.0)
        val config = MetronomeConfig(enabled = true, timingMode = MetronomeTimingMode.AUTO)

        // 曲Aロード開始
        coordinator.onSongChanged(songA, config)
        val jobA = coordinator.activeJob
        assertNotNull("ジョブAが起動されていること", jobA)

        // 直後に曲Bへ切り替え
        coordinator.onSongChanged(songB, config)
        val jobB = coordinator.activeJob
        assertNotNull("ジョブBが起動されていること", jobB)

        // 先行するジョブAが実際にキャンセルされていることの直接検証
        assertTrue("先行するジョブAがJob.cancel()されていること", jobA!!.isCancelled)

        // ジョブBの完了を待機
        jobB!!.join()

        // 曲Bのテンポ (200 BPM) が適用されており、曲A (100 BPM) で上書きされていないこと
        assertTrue("解決完了後にisReadyがtrueになること", scheduler.isReady)
        assertEquals(200.0, scheduler.currentBpm(0L), 0.001)
    }

    // 2. 通常稼働中の連続設定変更（BPM変更等）時に先行ジョブが確実にキャンセルされること
    @Test
    fun testJobCancellation_onRapidConfigChange() = runBlocking {
        val song = createSong("Song", 120.0)
        val initialConfig = MetronomeConfig(enabled = true, timingMode = MetronomeTimingMode.AUTO)

        // 初期曲ロードを完了させる
        coordinator.onSongChanged(song, initialConfig)
        coordinator.activeJob?.join()
        assertTrue(scheduler.isReady)
        assertEquals(120.0, scheduler.currentBpm(0L), 0.001)

        // 通常稼働中に MANUAL 130 BPM への変更を通知
        val manualConfig1 = initialConfig.copy(timingMode = MetronomeTimingMode.MANUAL, bpm = 130)
        coordinator.onConfigChanged(manualConfig1)
        val job1 = coordinator.activeJob
        assertNotNull("設定変更ジョブ1が起動されていること", job1)

        // 完了前に続けて MANUAL 170 BPM への変更を通知
        val manualConfig2 = initialConfig.copy(timingMode = MetronomeTimingMode.MANUAL, bpm = 170)
        coordinator.onConfigChanged(manualConfig2)
        val job2 = coordinator.activeJob
        assertNotNull("設定変更ジョブ2が起動されていること", job2)

        // ジョブ1が実際にキャンセルされていること
        assertTrue("先行する設定変更ジョブ1がキャンセルされていること", job1!!.isCancelled)

        job2!!.join()

        // 最新の設定 (170 BPM) が適用されていること
        assertEquals(170.0, scheduler.currentBpm(0L), 0.001)
        assertEquals(TimingSourceKind.MANUAL, scheduler.currentTimingSourceKind)
    }

    // 3. 非同期解決中の即時ミュート保証と解決完了後の復帰
    @Test
    fun testImmediateMuteDuringAsyncSongResolution() = runBlocking {
        val song = createSong("Song", 150.0)
        val config = MetronomeConfig(enabled = true, timingMode = MetronomeTimingMode.AUTO)

        coordinator.onSongChanged(song, config)
        val job = coordinator.activeJob
        assertNotNull(job)

        // 解決完了前は scheduler.isReady が false であり、processTick が発音しないこと
        assertFalse("非同期解決中はisReadyがfalseであること（即時ミュート）", scheduler.isReady)
        currentPositionMs = 0L
        assertFalse("非同期解決中はprocessTickがfalseを返すこと", scheduler.processTick(0L))
        assertEquals(0, soundPlayer.playedBeats.size)

        // 解決完了を待機
        job!!.join()

        // 完了後は isReady が true になり、発音可能になること
        assertTrue("解決完了後はisReadyがtrueになること", scheduler.isReady)
        scheduler.onPlaybackStateChanged(PlaybackState.Playing(0L))
        assertTrue("解決完了後はprocessTickがtrueを返すこと", scheduler.processTick(0L))
        assertEquals(1, soundPlayer.playedBeats.size)
    }

    // 4. 音量変更のみでは重い再解決ジョブを起動しないことの検証
    @Test
    fun testVolumeOnlyChangeDoesNotTriggerResolutionJob() = runBlocking {
        val song = createSong("Song", 120.0)
        val config = MetronomeConfig(enabled = true, timingMode = MetronomeTimingMode.AUTO, volumePercent = 70)

        coordinator.onSongChanged(song, config)
        coordinator.activeJob?.join()
        val generationAfterSong = coordinator.generation

        // 音量のみを変更 (70% -> 95%)
        val newVolumeConfig = config.copy(volumePercent = 95)
        coordinator.onConfigChanged(newVolumeConfig)

        // 音量変更のみの場合は世代番号が進まず、重い再解決ジョブも起動されないこと
        assertEquals("音量変更のみでは世代番号が進まないこと", generationAfterSong, coordinator.generation)

        // スケジューラの現在の音量は即時更新されていること
        scheduler.onPlaybackStateChanged(PlaybackState.Playing(0L))
        scheduler.processTick(0L)
        assertEquals(95, soundPlayer.playedBeats.last().volume)
    }

    // 5. 新曲構築中に設定変更（AUTO -> MANUAL）が割り込んだ場合の先行ジョブキャンセルと最新適用
    @Test
    fun testConfigChangeDuringSongResolution_cancelsStaleAndResolvesLatest() = runBlocking {
        val song = createSong("Song", 240.0)
        val autoConfig = MetronomeConfig(enabled = true, timingMode = MetronomeTimingMode.AUTO)

        // 新曲のAUTO解決開始
        coordinator.onSongChanged(song, autoConfig)
        val autoJob = coordinator.activeJob
        assertNotNull(autoJob)

        // 解決中にユーザーが MANUAL (BPM=110) に変更
        val manualConfig = autoConfig.copy(timingMode = MetronomeTimingMode.MANUAL, bpm = 110)
        coordinator.onConfigChanged(manualConfig)
        val manualJob = coordinator.activeJob
        assertNotNull(manualJob)

        // AUTOのジョブがキャンセルされていること
        assertTrue("先行するAUTOジョブがキャンセルされていること", autoJob!!.isCancelled)

        manualJob!!.join()

        // 最終的にMANUAL 110 BPMが適用されること
        assertTrue(scheduler.isReady)
        assertEquals(110.0, scheduler.currentBpm(0L), 0.001)
        assertEquals(TimingSourceKind.MANUAL, scheduler.currentTimingSourceKind)
    }
}
