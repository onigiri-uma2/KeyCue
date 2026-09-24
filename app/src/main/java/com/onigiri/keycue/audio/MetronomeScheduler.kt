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
 * 3. 処理遅延（スパイクやフレーム落ち）時に過去の拍をまとめて連打（追いかけ再生）せず、実時間換算の許容遅延を超えた拍は破棄して現在位置以降へ再同期する。
 * 4. 再生速度（speed）を考慮し、実時間での許容遅延（デフォルト30ms）を再生速度倍率で楽曲時間へ換算して判定する。
 * 5. 初回再生・Resume・Seek後を明示的に区別し、手動Seek後やResume時に直前拍が遅延許容で誤発音されるのを防止する。
 * 6. Seek世代番号（seekGeneration）を導入し、Tickerが位置取得してからロック取得するまでの間に発生した古い位置スナップショットを安全に破棄する。
 * 7. 排他制御（stateLock）を用いて制御スレッドと発音Tickerスレッドの競合を完全に直列化する。
 *
 * @param timeProvider 現在の楽曲再生位置（ミリ秒）を提供する関数
 * @param speedProvider 現在の再生速度比率を提供する関数
 * @param isPlayingProvider 現在再生中（Playing状態）かどうかを提供する関数
 * @param durationProvider 現在の楽曲の総演奏時間（ミリ秒）を提供する関数
 * @param loopBoundsProvider ABリピートの区間 (loopStartMs, loopEndMs) を提供する関数（有効なAB区間のみ）
 * @param soundPlayer クリック音を再生する音声プレイヤー
 * @param scope 非同期スケジューリングを実行するCoroutineScope
 * @param toleratedRealTimeDelayMs 実時間での処理遅延の許容上限ミリ秒（デフォルト30ms）。
 * @param autoStartTicker バックグラウンドTickerを自動起動するかどうか（テスト時はfalseで手動制御可能）
 */
class MetronomeScheduler(
    private val timeProvider: () -> Long,
    private val speedProvider: () -> Float,
    private val isPlayingProvider: () -> Boolean,
    private val durationProvider: () -> Long,
    private val loopBoundsProvider: () -> Pair<Long?, Long?>,
    private val soundPlayer: MetronomeSoundPlayer,
    private val scope: CoroutineScope,
    val toleratedRealTimeDelayMs: Long = DEFAULT_TOLERATED_REAL_TIME_DELAY_MS,
    autoStartTicker: Boolean = true
) {
    companion object {
        /** 実時間での処理遅延のデフォルト許容時間（ミリ秒） */
        const val DEFAULT_TOLERATED_REAL_TIME_DELAY_MS = 30L

        /** 後方互換用定数 */
        const val DEFAULT_TOLERATED_DELAY_MS = DEFAULT_TOLERATED_REAL_TIME_DELAY_MS

        /** 時間逆行（ABリピート巻き戻り等）を検出する閾値（ミリ秒） */
        private const val REWIND_DETECT_THRESHOLD_MS = 50L
    }

    /** 互換用プロパティ */
    val toleratedDelayMs: Long get() = toleratedRealTimeDelayMs

    private val stateLock = Any()

    private var currentConfig: MetronomeConfig = MetronomeConfig()
    private var currentTimingMetadata: com.onigiri.keycue.model.timing.SongTimingMetadata? = null
    private var timeline: BeatTimeline = ManualBeatTimeline(currentConfig)
    private val resyncChannel = Channel<Unit>(Channel.CONFLATED)
    private var schedulerJob: Job? = null

    // 内部状態（stateLockで保護）
    private var lastPlayedBeatIndex: Long = -1L
    private var nextClickIndex: Long = 0L
    private var lastObservedPositionMs: Long = 0L

    // 再生セッション状態の明示的フラグ（初回再生・Resume・Seek後を分離）
    private var hasStartedInitialPlay = false
    private var isPendingSeek = false

    // Seek世代番号（古い位置スナップショットの破棄用）
    private var currentGeneration: Long = 0L

    // タイムライン準備状態（新曲非同期構築中はfalseとなり即時ミュートを保証）
    private var isTimelineReady: Boolean = true

    /** 現在タイムラインが発音可能な準備状態にあるかどうか */
    val isReady: Boolean get() = synchronized(stateLock) { isTimelineReady }

    init {
        if (autoStartTicker) {
            startSchedulerJob()
        }
    }

    private fun rebuildTimelineLocked(): BeatTimeline {
        return MetronomeTimingResolver.resolveTimeline(
            config = currentConfig,
            timingMetadata = currentTimingMetadata,
            durationMs = durationProvider()
        )
    }

    /**
     * 楽曲のタイミングメタデータ（MIDI Set Tempo/Time Signature、Sky Studio BPM等）を更新する。
     */
    fun updateTimingMetadata(metadata: com.onigiri.keycue.model.timing.SongTimingMetadata?) {
        synchronized(stateLock) {
            currentTimingMetadata = metadata
            if (isTimelineReady) {
                timeline = rebuildTimelineLocked()
                val currentPos = timeProvider()
                val nextIdx = timeline.nextBeatIndexAtOrAfter(currentPos)
                nextClickIndex = nextIdx
                lastPlayedBeatIndex = nextIdx - 1
                lastObservedPositionMs = currentPos
                currentGeneration++
                notifyResync()
            }
        }
    }

    /**
     * テストまたはカスタム制御用にタイムラインを直接差し替える。
     */
    fun updateTimeline(newTimeline: BeatTimeline) {
        synchronized(stateLock) {
            timeline = newTimeline
            isTimelineReady = true
            val currentPos = timeProvider()
            val nextIdx = timeline.nextBeatIndexAtOrAfter(currentPos)
            nextClickIndex = nextIdx
            lastPlayedBeatIndex = nextIdx - 1
            lastObservedPositionMs = currentPos
            currentGeneration++
            notifyResync()
        }
    }

    /**
     * 現在位置におけるBPMを取得する。
     */
    fun currentBpm(positionMs: Long = timeProvider()): Double = synchronized(stateLock) {
        timeline.bpmAt(positionMs)
    }

    /**
     * 現在位置における拍子を取得する。
     */
    fun currentTimeSignature(positionMs: Long = timeProvider()): com.onigiri.keycue.model.timing.TimeSignature = synchronized(stateLock) {
        timeline.timeSignatureAt(positionMs)
    }

    /** 現在のタイミング情報源種別 */
    val currentTimingSourceKind: TimingSourceKind get() = synchronized(stateLock) {
        timeline.sourceKind
    }

    /** BPMが楽曲から自動取得されたものかどうか */
    val isBpmAuto: Boolean get() = synchronized(stateLock) {
        timeline.isBpmAuto
    }

    /** 拍子が楽曲から自動取得されたものかどうか */
    val isTimeSignatureAuto: Boolean get() = synchronized(stateLock) {
        timeline.isTimeSignatureAuto
    }

    /**
     * メトロノーム設定を更新する。
     *
     * 有効状態やBPM・拍子・分割・オフセットの変更時は、拍グリッドを再同期します。
     * 音量変更のみの場合は拍グリッドのリセットを行わず、音量のみ即時適用します。
     */
    fun updateConfig(newConfig: MetronomeConfig) {
        val normalized = newConfig.normalized()
        synchronized(stateLock) {
            val oldConfig = currentConfig
            currentConfig = normalized

            if (!oldConfig.enabled && normalized.enabled) {
                // OFF -> ON: 現在位置より後の拍から開始
                // ただし、新曲非同期構築中 (!isTimelineReady) の場合は勝手に解除せず、構築完了を待つ
                if (isTimelineReady) {
                    timeline = rebuildTimelineLocked()
                    val currentPos = timeProvider()
                    val nextIdx = timeline.nextBeatIndexAtOrAfter(currentPos)
                    nextClickIndex = nextIdx
                    // 直前拍の再発音・誤発音を防止（nextIdxが0なら-1L、1以上ならその直前インデックス）
                    lastPlayedBeatIndex = nextIdx - 1
                    lastObservedPositionMs = currentPos
                    currentGeneration++
                    notifyResync()
                }
            } else if (oldConfig.enabled && !normalized.enabled) {
                // ON -> OFF: 以後の発音を即座に停止
                soundPlayer.stop()
                currentGeneration++
                notifyResync()
            } else if (normalized.enabled) {
                // パラメータ変更の確認（モード, BPM, 拍子, 分割, オフセット等）
                val gridChanged = oldConfig.timingMode != normalized.timingMode ||
                        oldConfig.bpm != normalized.bpm ||
                        oldConfig.beatsPerBar != normalized.beatsPerBar ||
                        oldConfig.subdivision != normalized.subdivision ||
                        oldConfig.beatOffsetMs != normalized.beatOffsetMs ||
                        oldConfig.accentEnabled != normalized.accentEnabled

                if (gridChanged && isTimelineReady) {
                    timeline = rebuildTimelineLocked()
                    val currentPos = timeProvider()
                    val nextIdx = timeline.nextBeatIndexAtOrAfter(currentPos)
                    nextClickIndex = nextIdx
                    lastPlayedBeatIndex = nextIdx - 1
                    lastObservedPositionMs = currentPos
                    currentGeneration++
                    notifyResync()
                }
            }
        }
    }

    /**
     * [PlaybackState] の変化（状態種別の遷移）を通知する。
     */
    fun onPlaybackStateChanged(state: PlaybackState) {
        synchronized(stateLock) {
            when (state) {
                is PlaybackState.Playing -> {
                    val currentPos = timeProvider()
                    val speed = speedProvider().coerceAtLeast(0.01f)
                    val toleratedSongDelayMs = (toleratedRealTimeDelayMs * speed).toLong()

                    if (isPendingSeek) {
                        // 手動Seek直後のPlaying通知: Seek先以降の拍から再開（遅延許容で直前拍を拾わない）
                        isPendingSeek = false
                        hasStartedInitialPlay = true
                        nextClickIndex = timeline.nextBeatIndexAtOrAfter(currentPos)
                        lastPlayedBeatIndex = nextClickIndex - 1
                    } else if (!hasStartedInitialPlay) {
                        // 初期開始時のみ: 第1拍を取りこぼさないよう許容遅延を加味
                        hasStartedInitialPlay = true
                        nextClickIndex = timeline.candidateBeatIndex(
                            positionMs = currentPos,
                            toleratedDelayMs = toleratedSongDelayMs
                        )
                        lastPlayedBeatIndex = nextClickIndex - 1
                    } else {
                        // 通常のResume: Pause直前の拍を再発音しないよう、現在位置以降の拍から開始
                        nextClickIndex = timeline.nextBeatIndexAtOrAfter(currentPos)
                        lastPlayedBeatIndex = nextClickIndex - 1
                    }
                    lastObservedPositionMs = currentPos
                    currentGeneration++
                    notifyResync()
                }
                is PlaybackState.Stopped -> {
                    soundPlayer.stop()
                    hasStartedInitialPlay = false
                    isPendingSeek = false
                    lastPlayedBeatIndex = -1L
                    nextClickIndex = 0L
                    lastObservedPositionMs = 0L
                    currentGeneration++
                    notifyResync()
                }
                is PlaybackState.Paused, is PlaybackState.Finished, is PlaybackState.CountingDown -> {
                    soundPlayer.stop()
                    currentGeneration++
                    notifyResync()
                }
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
        synchronized(stateLock) {
            val safePos = targetPositionMs.coerceAtLeast(0L)
            val nextIdx = timeline.nextBeatIndexAtOrAfter(safePos)
            nextClickIndex = nextIdx
            lastPlayedBeatIndex = nextIdx - 1
            lastObservedPositionMs = safePos
            isPendingSeek = true
            currentGeneration++
            notifyResync()
        }
    }

    /**
     * 再生速度（Speed）の変更を通知する。
     */
    fun onSpeedChanged() {
        synchronized(stateLock) {
            currentGeneration++
            notifyResync()
        }
    }

    /**
     * ABリピート区間（有効状態）の変更を通知する。
     */
    fun onLoopBoundsChanged() {
        synchronized(stateLock) {
            currentGeneration++
            notifyResync()
        }
    }

    /** 現在のスケジューラ世代番号（テストおよび検証用） */
    val generation: Long get() = synchronized(stateLock) { currentGeneration }

    /**
     * ABリピート巻き戻り発生を明示的に通知する。
     *
     * A地点以降の拍のみを発音対象とするため、遅延許容を適用せず現在位置以降の拍から再開します。
     * （A地点が拍境界に完全一致する場合は、その拍を含めて発音可能）
     */
    fun onLoopRewound(rewindPositionMs: Long) {
        synchronized(stateLock) {
            val nextIdx = timeline.nextBeatIndexAtOrAfter(rewindPositionMs)
            nextClickIndex = nextIdx
            lastPlayedBeatIndex = nextIdx - 1
            lastObservedPositionMs = rewindPositionMs
            currentGeneration++
            notifyResync()
        }
    }

    /**
     * 楽曲変更の開始時に呼び出し、発音を停止して古い曲のクリック予定を破棄する。
     * バックグラウンドでのタイムライン構築が完了するまでの間、安全なミュート状態を維持します。
     */
    fun prepareForSongChange(metadata: com.onigiri.keycue.model.timing.SongTimingMetadata? = null) {
        synchronized(stateLock) {
            soundPlayer.stop()
            currentTimingMetadata = metadata
            timeline = ManualBeatTimeline(currentConfig)
            isTimelineReady = false // 構築完了まで発音を停止（即時ミュート保証）
            hasStartedInitialPlay = false
            isPendingSeek = false
            lastPlayedBeatIndex = -1L
            nextClickIndex = 0L
            lastObservedPositionMs = 0L
            currentGeneration++
            notifyResync()
        }
    }

    /**
     * バックグラウンドスレッド等で事前に構築された [newTimeline] をSchedulerへ適用する。
     */
    fun applyResolvedTimeline(
        newTimeline: BeatTimeline,
        metadata: com.onigiri.keycue.model.timing.SongTimingMetadata? = null
    ) {
        synchronized(stateLock) {
            currentTimingMetadata = metadata
            timeline = newTimeline
            isTimelineReady = true // 準備完了
            val currentPos = timeProvider()
            val nextIdx = timeline.nextBeatIndexAtOrAfter(currentPos)
            nextClickIndex = nextIdx
            lastPlayedBeatIndex = nextIdx - 1
            lastObservedPositionMs = currentPos
            currentGeneration++
            notifyResync()
        }
    }

    /**
     * 楽曲変更時（新曲読み込み、Recent切替等）の初期化を行う（同期版・後方互換用）。
     */
    fun onSongChanged(metadata: com.onigiri.keycue.model.timing.SongTimingMetadata? = null) {
        synchronized(stateLock) {
            soundPlayer.stop()
            currentTimingMetadata = metadata
            timeline = rebuildTimelineLocked()
            isTimelineReady = true
            hasStartedInitialPlay = false
            isPendingSeek = false
            lastPlayedBeatIndex = -1L
            nextClickIndex = 0L
            lastObservedPositionMs = 0L
            currentGeneration++
            notifyResync()
        }
    }

    private fun notifyResync() {
        resyncChannel.trySend(Unit)
    }

    /**
     * 指定された現在楽曲位置 [currentPos] における発音判定と状態更新を1ステップ分同期的に処理する。
     *
     * [generation] を指定した場合、現在の世代番号と一致しない場合は古いスナップショットとして処理を破棄します。
     *
     * @return このステップで発音が行われた場合は true、待機またはスキップの場合は false
     */
    fun processTick(currentPos: Long, generation: Long? = null): Boolean {
        synchronized(stateLock) {
            if (generation != null && generation != currentGeneration) {
                // Seekや状態遷移により世代が進んでいる場合、古い位置スナップショットを破棄
                return false
            }

            val isPlaying = isPlayingProvider()
            val config = currentConfig
            val duration = durationProvider()

            // タイムライン構築中 (!isTimelineReady) や停止中は一切発音しない（即時ミュート保証）
            if (!isPlaying || !config.enabled || duration <= 0L || !isTimelineReady) {
                return false
            }

            val speed = speedProvider().coerceAtLeast(0.01f)
            val toleratedSongDelayMs = (toleratedRealTimeDelayMs * speed).toLong()

            val (_, loopEnd) = loopBoundsProvider()

            // 1. 時間巻き戻り検知（ABリピート巻き戻り等のバックアップ検出）
            if (currentPos < lastObservedPositionMs - REWIND_DETECT_THRESHOLD_MS) {
                val nextIdx = timeline.nextBeatIndexAtOrAfter(currentPos)
                nextClickIndex = nextIdx
                lastPlayedBeatIndex = nextIdx - 1
            }
            lastObservedPositionMs = currentPos

            // 2. 次のクリック予定拍を計算
            var beat = timeline.beatForIndex(nextClickIndex)

            // 3. 処理遅延チェック: 予定時刻が現在位置より大幅に遅れている場合は未来へ再同期（古い拍スキップ）
            if (beat != null) {
                val overdueMs = currentPos - beat.timeMs
                if (overdueMs > toleratedSongDelayMs) {
                    nextClickIndex = timeline.candidateBeatIndex(
                        positionMs = currentPos,
                        toleratedDelayMs = toleratedSongDelayMs
                    )
                    beat = timeline.beatForIndex(nextClickIndex)
                }
            }

            if (beat == null) {
                return false
            }

            // 4. 有効なABリピートのB地点以降は発音禁止
            if (loopEnd != null && beat.timeMs >= loopEnd) {
                return false
            }

            // 5. 楽曲長を超えている場合は発音しない
            if (beat.timeMs >= duration) {
                return false
            }

            // 6. 到達判定 & 発音処理
            val delayMs = currentPos - beat.timeMs
            val isWithinDelay = delayMs in 0L..toleratedSongDelayMs
            val notYetPlayed = (lastPlayedBeatIndex != beat.index)
            val notPastLoopEnd = (loopEnd == null || beat.timeMs < loopEnd)

            return if (isWithinDelay && notYetPlayed && notPastLoopEnd) {
                soundPlayer.playBeat(beat.isAccent, config.volumePercent)
                lastPlayedBeatIndex = beat.index
                nextClickIndex = beat.index + 1
                true
            } else if (delayMs > toleratedSongDelayMs) {
                nextClickIndex = timeline.candidateBeatIndex(
                    positionMs = currentPos,
                    toleratedDelayMs = toleratedSongDelayMs
                )
                false
            } else {
                false
            }
        }
    }

    private fun startSchedulerJob() {
        if (schedulerJob != null) return
        schedulerJob = scope.launch(Dispatchers.Default) {
            while (isActive) {
                val shouldWait = synchronized(stateLock) {
                    !isPlayingProvider() || !currentConfig.enabled || durationProvider() <= 0L || !isTimelineReady
                }

                // 再生中でない、メトロノーム無効、楽曲が存在しない、またはタイムライン構築中の場合はシグナル待機
                if (shouldWait) {
                    resyncChannel.receive()
                    continue
                }

                // ロック内で現在位置と現在世代番号をアトミックに取得
                val (currentPos, gen, speed, loopBounds, songDuration) = synchronized(stateLock) {
                    val pos = timeProvider()
                    val gen = currentGeneration
                    val spd = speedProvider().coerceAtLeast(0.01f)
                    val bounds = loopBoundsProvider()
                    val dur = durationProvider()
                    SchedulerContext(pos, gen, spd, bounds, dur)
                }
                val (_, loopEnd) = loopBounds

                // 現在時刻で発音処理を評価（古い世代の場合は安全にスキップされる）
                val played = processTick(currentPos, gen)
                if (played) {
                    continue
                }

                val beat = synchronized(stateLock) {
                    timeline.beatForIndex(nextClickIndex)
                }

                if (beat == null) {
                    withTimeoutOrNull(100L) { resyncChannel.receive() }
                    continue
                }

                if (loopEnd != null && beat.timeMs >= loopEnd) {
                    withTimeoutOrNull(50L) { resyncChannel.receive() }
                    continue
                }
                if (beat.timeMs >= songDuration) {
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
                    val (freshPos, freshGen) = synchronized(stateLock) {
                        timeProvider() to currentGeneration
                    }
                    processTick(freshPos, freshGen)
                }
            }
        }
    }

    private data class SchedulerContext(
        val currentPos: Long,
        val generation: Long,
        val speed: Float,
        val loopBounds: Pair<Long?, Long?>,
        val songDuration: Long
    )

    /**
     * スケジューラを停止する。
     */
    fun stop() {
        synchronized(stateLock) {
            soundPlayer.stop()
            currentGeneration++
            notifyResync()
        }
    }

    /**
     * スケジューラと音声プレイヤーのリソースを解放する。
     */
    fun release() {
        synchronized(stateLock) {
            stop()
            schedulerJob?.cancel()
            schedulerJob = null
            soundPlayer.release()
        }
    }
}
