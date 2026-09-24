package com.onigiri.keycue.audio

import com.onigiri.keycue.model.MetronomeConfig
import com.onigiri.keycue.playback.PlaybackState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 楽曲時間軸に厳密同期してメトロノームの発音タイミングを管理・制御するスケジューラ。
 *
 * 【設計原則】
 * 1. PlaybackClock / PlaybackEngine が提供する楽曲時間軸を絶対基準とする（delay等の累積時間を楽曲時刻とみなさない）。
 * 2. 描画フレームレートやUI更新周期（100ms）に依存せず、専用Coroutine内でミリ秒単位の到達判定を行う。
 * 3. 処理遅延（スパイクやフレーム落ち）時に過去の拍をまとめて連打（追いかけ再生）せず、許容遅延を超えた拍は破棄して現在位置以降へ再同期する。
 * 4. Pause / Resume / Seek / ABリピート / 速度変更 / 設定変更において、拍の二重発音や発音漏れを防止する。
 *
 * @param timeProvider 現在の楽曲再生位置（ミリ秒）を提供する関数
 * @param speedProvider 現在の再生速度比率を提供する関数
 * @param isPlayingProvider 現在再生中（Playing状態）かどうかを提供する関数
 * @param durationProvider 現在の楽曲の総演奏時間（ミリ秒）を提供する関数
 * @param loopBoundsProvider ABリピートの区間 (loopStartMs, loopEndMs) を提供する関数
 * @param soundPlayer クリック音を再生する音声プレイヤー
 * @param scope 非同期スケジューリングを実行するCoroutineScope
 * @param toleratedDelayMs 処理遅延の許容上限ミリ秒（デフォルト30ms）。これを超えて遅れた拍はスキップされる。
 */
class MetronomeScheduler(
    private val timeProvider: () -> Long,
    private val speedProvider: () -> Float,
    private val isPlayingProvider: () -> Boolean,
    private val durationProvider: () -> Long,
    private val loopBoundsProvider: () -> Pair<Long?, Long?>,
    private val soundPlayer: MetronomeSoundPlayer,
    private val scope: CoroutineScope,
    val toleratedDelayMs: Long = DEFAULT_TOLERATED_DELAY_MS
) {
    companion object {
        /** 処理遅延のデフォルト許容時間（ミリ秒） */
        const val DEFAULT_TOLERATED_DELAY_MS = 30L

        /** 時間逆行（ABリピート巻き戻り等）を検出する閾値（ミリ秒） */
        private const val REWIND_DETECT_THRESHOLD_MS = 150L
    }

    private var currentConfig: MetronomeConfig = MetronomeConfig()
    private val resyncChannel = Channel<Unit>(Channel.CONFLATED)
    private var schedulerJob: Job? = null

    // 内部状態
    private var lastPlayedBeatIndex: Long = -1L
    private var nextClickIndex: Long = 0L
    private var lastObservedPositionMs: Long = 0L

    init {
        startSchedulerJob()
    }

    /**
     * メトロノーム設定を更新する。
     *
     * 有効状態やBPM・拍子・分割・オフセットの変更時は、拍グリッドを再同期します。
     * 音量変更のみの場合は拍グリッドのリセットを行わず、音量のみ即時適用します。
     */
    fun updateConfig(newConfig: MetronomeConfig) {
        val normalized = newConfig.normalized()
        val oldConfig = currentConfig
        currentConfig = normalized

        if (!oldConfig.enabled && normalized.enabled) {
            // OFF -> ON: 現在位置より後の拍から開始
            val currentPos = timeProvider()
            nextClickIndex = MetronomeBeatCalculator.findNextClickIndexFromPosition(normalized, currentPos)
            lastPlayedBeatIndex = -1L
            lastObservedPositionMs = currentPos
            notifyResync()
        } else if (oldConfig.enabled && !normalized.enabled) {
            // ON -> OFF: 以後の発音を即座に停止
            soundPlayer.stop()
            notifyResync()
        } else if (normalized.enabled) {
            // パラメータ変更の確認（BPM, 拍子, 分割, オフセット等）
            val gridChanged = oldConfig.bpm != normalized.bpm ||
                    oldConfig.beatsPerBar != normalized.beatsPerBar ||
                    oldConfig.subdivision != normalized.subdivision ||
                    oldConfig.beatOffsetMs != normalized.beatOffsetMs ||
                    oldConfig.accentEnabled != normalized.accentEnabled

            if (gridChanged) {
                val currentPos = timeProvider()
                nextClickIndex = MetronomeBeatCalculator.findNextClickIndexFromPosition(normalized, currentPos)
                lastPlayedBeatIndex = -1L
                lastObservedPositionMs = currentPos
                notifyResync()
            }
        }
    }

    /**
     * [PlaybackState] の変化を通知する。
     */
    fun onPlaybackStateChanged(state: PlaybackState) {
        when (state) {
            is PlaybackState.Playing -> {
                val currentPos = timeProvider()
                // 再生開始・再開
                if (lastPlayedBeatIndex < 0L) {
                    // 初期開始（第1拍を取りこぼさないよう許容遅延を加味）
                    nextClickIndex = MetronomeBeatCalculator.findCandidateClickIndex(
                        config = currentConfig,
                        currentPositionMs = currentPos,
                        toleratedDelayMs = toleratedDelayMs
                    )
                } else {
                    // Resume時: Pause直前の拍を再発音しないよう、現在位置以降の拍から開始
                    nextClickIndex = MetronomeBeatCalculator.findNextClickIndexFromPosition(
                        config = currentConfig,
                        targetPositionMs = currentPos
                    )
                }
                lastObservedPositionMs = currentPos
                notifyResync()
            }
            is PlaybackState.Paused, is PlaybackState.Stopped, is PlaybackState.Finished, is PlaybackState.CountingDown -> {
                soundPlayer.stop()
                if (state is PlaybackState.Stopped) {
                    lastPlayedBeatIndex = -1L
                    nextClickIndex = 0L
                    lastObservedPositionMs = 0L
                }
                notifyResync()
            }
        }
    }

    /**
     * 手動シーク（ミニシークバー、±10秒、直接位置指定等）を通知する。
     *
     * シーク先より前の古い拍予定は破棄され、シーク先以降の拍から再同期します。
     *
     * @param targetPositionMs シーク先の楽曲位置（ミリ秒）
     */
    fun onSeek(targetPositionMs: Long) {
        val safePos = targetPositionMs.coerceAtLeast(0L)
        lastPlayedBeatIndex = -1L
        nextClickIndex = MetronomeBeatCalculator.findNextClickIndexFromPosition(currentConfig, safePos)
        lastObservedPositionMs = safePos
        notifyResync()
    }

    /**
     * 再生速度（Speed）の変更を通知する。
     */
    fun onSpeedChanged() {
        notifyResync()
    }

    /**
     * ABリピート設定の変更や巻き戻り発生を明示的に通知する。
     */
    fun onLoopRewound(rewindPositionMs: Long) {
        lastPlayedBeatIndex = -1L
        nextClickIndex = MetronomeBeatCalculator.findCandidateClickIndex(
            config = currentConfig,
            currentPositionMs = rewindPositionMs,
            toleratedDelayMs = toleratedDelayMs
        )
        lastObservedPositionMs = rewindPositionMs
        notifyResync()
    }

    /**
     * 楽曲変更時（新曲読み込み、Recent切替等）の初期化を行う。
     */
    fun onSongChanged() {
        soundPlayer.stop()
        lastPlayedBeatIndex = -1L
        nextClickIndex = 0L
        lastObservedPositionMs = 0L
        notifyResync()
    }

    private fun notifyResync() {
        resyncChannel.trySend(Unit)
    }

    /**
     * 指定された現在楽曲位置 [currentPos] における発音判定と状態更新を1ステップ分同期的に処理する。
     *
     * バックグラウンドループおよび単体テストから呼び出され、決定論的な動作を担保します。
     *
     * @return このステップで発音が行われた場合は true、待機またはスキップの場合は false
     */
    fun processTick(currentPos: Long): Boolean {
        val isPlaying = isPlayingProvider()
        val config = currentConfig
        val duration = durationProvider()

        if (!isPlaying || !config.enabled || duration <= 0L) {
            return false
        }

        val (_, loopEnd) = loopBoundsProvider()

        // 1. 時間巻き戻り検知（ABリピート巻き戻り等）
        if (currentPos < lastObservedPositionMs - REWIND_DETECT_THRESHOLD_MS) {
            lastPlayedBeatIndex = -1L
            nextClickIndex = MetronomeBeatCalculator.findCandidateClickIndex(
                config = config,
                currentPositionMs = currentPos,
                toleratedDelayMs = toleratedDelayMs
            )
        }
        lastObservedPositionMs = currentPos

        // 2. 次のクリック予定拍を計算
        var beat = MetronomeBeatCalculator.calculateBeat(config, nextClickIndex)

        // 3. 処理遅延チェック: 予定時刻が現在位置より大幅に遅れている場合は未来へ再同期（古い拍スキップ）
        val overdueMs = currentPos - beat.timeMs
        if (overdueMs > toleratedDelayMs) {
            nextClickIndex = MetronomeBeatCalculator.findCandidateClickIndex(
                config = config,
                currentPositionMs = currentPos,
                toleratedDelayMs = toleratedDelayMs
            )
            beat = MetronomeBeatCalculator.calculateBeat(config, nextClickIndex)
        }

        // 4. ABリピートのB地点以降は発音禁止
        if (loopEnd != null && beat.timeMs >= loopEnd) {
            return false
        }

        // 5. 楽曲長を超えている場合は発音しない
        if (beat.timeMs >= duration) {
            return false
        }

        // 6. 到達判定 & 発音処理
        val delayMs = currentPos - beat.timeMs
        val isWithinDelay = delayMs in 0L..toleratedDelayMs
        val notYetPlayed = (lastPlayedBeatIndex != beat.index)
        val notPastLoopEnd = (loopEnd == null || beat.timeMs < loopEnd)

        return if (isWithinDelay && notYetPlayed && notPastLoopEnd) {
            soundPlayer.playBeat(beat.isAccent, config.volumePercent)
            lastPlayedBeatIndex = beat.index
            nextClickIndex = beat.index + 1
            true
        } else if (delayMs > toleratedDelayMs) {
            nextClickIndex = MetronomeBeatCalculator.findCandidateClickIndex(
                config = config,
                currentPositionMs = currentPos,
                toleratedDelayMs = toleratedDelayMs
            )
            false
        } else {
            false
        }
    }

    private fun startSchedulerJob() {
        if (schedulerJob != null) return
        schedulerJob = scope.launch(Dispatchers.Default) {
            while (isActive) {
                val isPlaying = isPlayingProvider()
                val config = currentConfig
                val duration = durationProvider()

                // 再生中でない、メトロノーム無効、または楽曲が存在しない場合はシグナル待機
                if (!isPlaying || !config.enabled || duration <= 0L) {
                    resyncChannel.receive()
                    continue
                }

                val currentPos = timeProvider()
                val speed = speedProvider().coerceAtLeast(0.01f)
                val (_, loopEnd) = loopBoundsProvider()

                // 現在時刻で発音処理を評価
                val played = processTick(currentPos)
                if (played) {
                    continue
                }

                val beat = MetronomeBeatCalculator.calculateBeat(currentConfig, nextClickIndex)
                if (loopEnd != null && beat.timeMs >= loopEnd) {
                    withTimeoutOrNull(50L) { resyncChannel.receive() }
                    continue
                }
                if (beat.timeMs >= duration) {
                    withTimeoutOrNull(100L) { resyncChannel.receive() }
                    continue
                }

                val remainingSongMs = beat.timeMs - currentPos
                val remainingRealMs = (remainingSongMs / speed).toLong()

                if (remainingRealMs > 0L) {
                    withTimeoutOrNull(remainingRealMs) {
                        resyncChannel.receive()
                    }
                } else {
                    processTick(timeProvider())
                }
            }
        }
    }

    /**
     * スケジューラを停止する。
     */
    fun stop() {
        soundPlayer.stop()
        notifyResync()
    }

    /**
     * スケジューラと音声プレイヤーのリソースを解放する。
     */
    fun release() {
        stop()
        schedulerJob?.cancel()
        schedulerJob = null
        soundPlayer.release()
    }
}
