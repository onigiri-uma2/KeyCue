package com.onigiri.keycue.audio

import com.onigiri.keycue.model.BeatSubdivision
import com.onigiri.keycue.model.MetronomeConfig
import com.onigiri.keycue.model.MetronomeTimingMode
import com.onigiri.keycue.model.SongData
import com.onigiri.keycue.model.timing.SongTimingMetadata
import com.onigiri.keycue.playback.PlaybackState
import com.onigiri.keycue.song.midi.RawTempoEvent
import com.onigiri.keycue.song.midi.RawTimeSignatureEvent
import com.onigiri.keycue.song.midi.TempoMap
import com.onigiri.keycue.song.midi.TimeSignatureMap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.coroutines.CoroutineContext

/**
 * [MetronomeSyncCoordinator] の非同期タイムライン解決、ジョブキャンセル、
 * 世代管理、および即時ミュートの決定論的直接検証テスト。
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

    /**
     * テスト内で非同期ジョブの実行順序・タイミングを決定論的に制御するディスパッチャ。
     * [executeAll] が呼ばれるまでタスクの実行を保留します。
     */
    private class ControllableDispatcher : CoroutineDispatcher() {
        private val queue = ArrayDeque<Runnable>()

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            synchronized(queue) {
                queue.add(block)
            }
        }

        fun executeNext(): Boolean {
            val task = synchronized(queue) {
                if (queue.isNotEmpty()) queue.removeFirst() else null
            } ?: return false
            task.run()
            return true
        }

        fun executeAll() {
            while (executeNext()) {
                // 保留タスクを順次実行
            }
        }
    }

    private lateinit var testDispatcher: ControllableDispatcher
    private lateinit var testScope: CoroutineScope
    private lateinit var soundPlayer: FakeSoundPlayer
    private lateinit var scheduler: MetronomeScheduler
    private lateinit var coordinator: MetronomeSyncCoordinator

    private var currentPositionMs: Long = 0L
    private var isPlaying: Boolean = true
    private var timelineAppliedCount = 0

    @Before
    fun setUp() {
        testDispatcher = ControllableDispatcher()
        testScope = CoroutineScope(testDispatcher)
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
            defaultDispatcher = testDispatcher,
            mainDispatcher = testDispatcher,
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
        // 4/4 拍子 (第3引数は実際の分母)
        val tsMap = TimeSignatureMap(listOf(RawTimeSignatureEvent(0L, 4, 4, 24, 8)))
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
        assertTrue("ジョブAが実行中であること", jobA!!.isActive)

        // 直後に曲Bへ切り替え
        coordinator.onSongChanged(songB, config)
        val jobB = coordinator.activeJob
        assertNotNull("ジョブBが起動されていること", jobB)

        // 先行するジョブAが決定論的にキャンセルされていることの直接検証
        assertTrue("先行するジョブAがJob.cancel()されていること", jobA.isCancelled)

        // ディスパッチャの保留タスクを実行
        testDispatcher.executeAll()

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
        testDispatcher.executeAll()
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

        // ジョブ1が決定論的にキャンセルされていること
        assertTrue("先行する設定変更ジョブ1がキャンセルされていること", job1!!.isCancelled)

        testDispatcher.executeAll()

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

        // 解決完了前は scheduler.isReady が false であり、processTick が発音しないこと（決定論的に保証）
        assertFalse("非同期解決中はisReadyがfalseであること（即時ミュート）", scheduler.isReady)
        currentPositionMs = 0L
        assertFalse("非同期解決中はprocessTickがfalseを返すこと", scheduler.processTick(0L))
        assertEquals(0, soundPlayer.playedBeats.size)

        // 解決処理を実行
        testDispatcher.executeAll()

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
        testDispatcher.executeAll()
        val generationAfterSong = coordinator.generation

        // 音量のみを変更 (70% -> 95%)
        val newVolumeConfig = config.copy(volumePercent = 95)
        coordinator.onConfigChanged(newVolumeConfig)

        // 音量変更のみの場合は世代番号が進まないこと
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

        testDispatcher.executeAll()

        // 最終的にMANUAL 110 BPMが適用されること
        assertTrue(scheduler.isReady)
        assertEquals(110.0, scheduler.currentBpm(0L), 0.001)
        assertEquals(TimingSourceKind.MANUAL, scheduler.currentTimingSourceKind)
    }

    // 6. OFF -> ON 遷移時に新タイムライン解決完了まで古いタイムラインが発音抑止されることの検証
    @Test
    fun testImmediateMuteOnEnableUntilResolutionCompletes() = runBlocking {
        val song = createSong("Song", 120.0)
        val offConfig = MetronomeConfig(enabled = false, timingMode = MetronomeTimingMode.AUTO)

        // メトロノーム OFF で楽曲を読み込み完了させる
        coordinator.onSongChanged(song, offConfig)
        testDispatcher.executeAll()

        // ユーザーが OFF -> ON に切り替え
        val onConfig = offConfig.copy(enabled = true)
        coordinator.onConfigChanged(onConfig)

        // 新タイムライン解決完了前は明示的に即時ミュート（isReady == false）状態であること
        assertFalse("OFF->ON時に解決完了前はisReadyがfalseであること", scheduler.isReady)

        // 再生中であっても processTick は一切発音しないこと
        scheduler.onPlaybackStateChanged(PlaybackState.Playing(0L))
        currentPositionMs = 0L
        assertFalse("旧タイムラインで発音されないこと", scheduler.processTick(0L))
        assertEquals(0, soundPlayer.playedBeats.size)

        // 解決完了を実行
        testDispatcher.executeAll()

        // 解決完了後に初めて isReady == true となり発音可能になること
        assertTrue("解決完了後にisReadyがtrueになること", scheduler.isReady)
        assertTrue("新タイムラインで発音されること", scheduler.processTick(0L))
        assertEquals(1, soundPlayer.playedBeats.size)
    }
}
