package com.onigiri.keycue.audio

import com.onigiri.keycue.model.MetronomeConfig
import com.onigiri.keycue.model.SongData
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 楽曲データおよびメトロノーム設定の変更に伴うタイムライン再生成を統括するコーディネーター。
 *
 * 主な責務:
 * 1. **非同期解決の一元化**: 新曲読み込み時だけでなく、通常稼働中の設定変更（BPM、拍子、拍分割等）も
 *    [defaultDispatcher] (Dispatchers.Default) 上で非同期に計算し、メインスレッドのUIブロックや音声処理の停滞を防ぎます。
 * 2. **ジョブのキャンセルと世代管理**: 新たな楽曲または設定変更が到着した際、先行する未完了ジョブを [Job.cancel] し、
 *    世代番号 ([requestGeneration]) により古い結果が適用される競合（レースコンディション）を完全に防止します。
 * 3. **即時ミュートの保証**: 楽曲変更時は非同期解決完了まで [MetronomeScheduler.prepareForSongChange] を通じて
 *    即時ミュート状態を維持します。通常稼働中の設定変更時は再生を中断せず、解決完了時に滑らかに新タイムラインへ切り替えます。
 */
class MetronomeSyncCoordinator(
    private val scope: CoroutineScope,
    private val scheduler: MetronomeScheduler,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main,
    private val onTimelineApplied: (() -> Unit)? = null
) {
    private var currentSong: SongData? = null
    private var currentConfig: MetronomeConfig? = null
    private var activeResolutionJob: Job? = null
    private var requestGeneration: Long = 0L

    /** 現在実行中の非同期解決ジョブ（テストおよび検証用） */
    val activeJob: Job? get() = activeResolutionJob

    /** 現在のタイムライン要求世代番号（テストおよび検証用） */
    val generation: Long get() = requestGeneration

    /** 現在保持している楽曲データ */
    val song: SongData? get() = currentSong

    /** 現在保持しているメトロノーム設定 */
    val config: MetronomeConfig? get() = currentConfig

    /**
     * 楽曲変更（新曲ロード、ファイル切替等）を通知する。
     *
     * @param newSong 新しい楽曲データ（null の場合は楽曲なし状態）
     * @param config 現在のメトロノーム設定
     */
    fun onSongChanged(newSong: SongData?, config: MetronomeConfig) {
        currentSong = newSong
        currentConfig = config

        // 先行ジョブがあれば即座にキャンセルして世代番号を進める
        activeResolutionJob?.cancel()
        val generation = ++requestGeneration

        if (newSong == null) {
            scheduler.stop()
            scheduler.updateConfig(config, rebuildTimeline = false)
            scheduler.applyResolvedTimeline(ManualBeatTimeline(config))
            onTimelineApplied?.invoke()
            return
        }

        // 新曲タイムライン構築完了まで即時ミュート（安全状態）へリセット
        scheduler.prepareForSongChange(newSong.timingMetadata)
        scheduler.updateConfig(config, rebuildTimeline = false)

        if (!config.enabled) {
            // メトロノームが無効な場合は非同期構築を行わず手動タイムラインで初期化
            scheduler.applyResolvedTimeline(ManualBeatTimeline(config), newSong.timingMetadata)
            onTimelineApplied?.invoke()
            return
        }

        launchResolution(newSong, config, generation)
    }

    /**
     * メトロノーム設定変更（モード、BPM、拍子、分割、音量等）を通知する。
     *
     * 音量変更のみの場合は重いタイムライン再生成を行わず即時適用します。
     * グリッド変更（BPM、拍子、分割等）の場合は非同期で新タイムラインを解決し、完了後に切り替えます。
     *
     * @param newConfig 新しいメトロノーム設定
     */
    fun onConfigChanged(newConfig: MetronomeConfig) {
        val oldConfig = currentConfig
        currentConfig = newConfig
        val song = currentSong

        // 音量やOFF時の即時停止はスケジューラへ即時適用
        scheduler.updateConfig(newConfig, rebuildTimeline = false)

        if (!newConfig.enabled) {
            // OFFへ変更された場合は進行中の非同期解決をキャンセル
            activeResolutionJob?.cancel()
            requestGeneration++
            return
        }

        val isGridChanged = oldConfig == null ||
                oldConfig.timingMode != newConfig.timingMode ||
                oldConfig.bpm != newConfig.bpm ||
                oldConfig.beatsPerBar != newConfig.beatsPerBar ||
                oldConfig.subdivision != newConfig.subdivision ||
                oldConfig.beatOffsetMs != newConfig.beatOffsetMs ||
                oldConfig.accentEnabled != newConfig.accentEnabled ||
                !oldConfig.enabled // OFF -> ON

        if (!isGridChanged) {
            // 音量変更のみ等の場合、タイムライン再構築は不要
            return
        }

        if (song == null) {
            // 楽曲なし時は手動タイムラインを即座に適用
            scheduler.applyResolvedTimeline(ManualBeatTimeline(newConfig))
            onTimelineApplied?.invoke()
            return
        }

        // 先行ジョブをキャンセルし、新しい世代番号で非同期解決を開始
        activeResolutionJob?.cancel()
        val generation = ++requestGeneration
        launchResolution(song, newConfig, generation)
    }

    private fun launchResolution(song: SongData, config: MetronomeConfig, generation: Long) {
        val metadata = song.timingMetadata
        val durationMs = song.durationMs

        activeResolutionJob = scope.launch(defaultDispatcher) {
            val resolvedTimeline = MetronomeTimingResolver.resolveTimeline(
                config = config,
                timingMetadata = metadata,
                durationMs = durationMs
            )
            withContext(mainDispatcher) {
                if (generation == requestGeneration) {
                    scheduler.applyResolvedTimeline(resolvedTimeline, metadata)
                    onTimelineApplied?.invoke()
                }
            }
        }
    }

    /**
     * コーディネーターのリソースを解放し、実行中のジョブをキャンセルする。
     */
    fun cancel() {
        activeResolutionJob?.cancel()
        activeResolutionJob = null
    }
}
