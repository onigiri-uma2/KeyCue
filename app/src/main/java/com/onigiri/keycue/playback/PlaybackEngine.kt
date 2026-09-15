package com.onigiri.keycue.playback

import com.onigiri.keycue.model.SongData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 演奏支援の再生制御エンジン。
 *
 * ViewやCompose UIを参照せず、[PlaybackClock] を用いて再生・一時停止・シーク・速度調整・カウントダウン等の
 * 状態遷移を管理する。
 *
 * @param clock 再生時間管理クロック
 * @param externalScope 非同期処理用のCoroutineScope（nullの場合は内部Scopeを生成）
 */
class PlaybackEngine(
    val clock: PlaybackClock = PlaybackClock(),
    externalScope: CoroutineScope? = null
) {
    private val scope: CoroutineScope = externalScope ?: CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val isInternalScope = externalScope == null

    private val _state = MutableStateFlow<PlaybackState>(PlaybackState.Stopped)
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    var songData: SongData? = null
        private set

    /**
     * 再生開始前カウントダウン時間（ミリ秒）。デフォルト3秒。
     */
    var countdownMs: Long = 3000L

    /** ユーザーが設定した再生速度（カウントダウン終了後に適用） */
    var targetPlaybackSpeed: Float = 1.0f
        private set

    private var countdownJob: Job? = null
    private var progressTickerJob: Job? = null

    /**
     * 再生対象の楽曲データを設定する。
     */
    fun setSong(song: SongData) {
        stop()
        songData = song
    }

    /**
     * 再生を開始する。
     *
     * - Paused状態の場合は一時停止から復帰（resume）する。
     * - StoppedまたはFinished状態の場合、[skipCountdown] が false ならカウントダウン後に再生開始、
     *   true なら直ちに再生開始する。
     *
     * @param skipCountdown カウントダウンを省略して即時再生するかどうか（テスト時等）
     */
    fun play(skipCountdown: Boolean = false) {
        val song = songData ?: return

        when (val current = _state.value) {
            is PlaybackState.Paused -> {
                resume()
            }
            is PlaybackState.Playing -> {
                // すでに再生中
            }
            is PlaybackState.CountingDown -> {
                // カウントダウン中
            }
            is PlaybackState.Stopped, is PlaybackState.Finished -> {
                if (skipCountdown || countdownMs <= 0L) {
                    startPlayback(startPositionMs = 0L)
                } else {
                    startCountdown()
                }
            }
        }
    }

    /**
     * カウントダウンを開始する。
     *
     * 【重要設計】曲開始前（負の仮想時間）も設定された再生速度 (speed) に従ってクロックを進めます。
     * これにより、曲冒頭（0ms付近）の落下ノーツがカウントダウン中から再生速度通りのスピードで落下し、
     * 仮想時間0ms（カウント終了の瞬間）に正確にジャストタイミングとなります。
     */
    private fun startCountdown() {
        countdownJob?.cancel()
        progressTickerJob?.cancel()

        val speed = targetPlaybackSpeed
        // カウントダウン時間と再生速度から逆算した負の開始時刻（ms）
        val virtualStartMs = -(countdownMs * speed).toLong()

        clock.setSpeed(speed)
        clock.play(startPositionMs = virtualStartMs, allowNegative = true)

        countdownJob = scope.launch {
            while (true) {
                val currentPos = clock.getCurrentPositionMs(allowNegative = true)
                // 0msに到達した時点でカウントダウン完了とし、本再生へ移行
                if (currentPos >= 0L) {
                    break
                }
                // 実時間ベースの残り時間を算出 (-currentPos / speed)
                val remainingRealMs = if (speed > 0f) (-currentPos / speed).toLong().coerceAtLeast(0L) else 0L
                val sec = ((remainingRealMs + 999L) / 1000L).toInt().coerceAtLeast(1)
                _state.value = PlaybackState.CountingDown(remainingMs = remainingRealMs, countNumber = sec)
                delay(16L) // 約60fpsで状態更新し、滑らかな落下ノーツ描画を担保
            }
            startPlayback(startPositionMs = 0L)
        }
    }

    /**
     * 指定位置から実再生を開始する。
     */
    private fun startPlayback(startPositionMs: Long) {
        countdownJob?.cancel()
        clock.setSpeed(targetPlaybackSpeed)
        clock.play(startPositionMs)
        _state.value = PlaybackState.Playing(startPositionMs)
        startProgressTicker()
    }

    /**
     * 再生を一時停止する。
     */
    fun pause() {
        if (_state.value !is PlaybackState.Playing) return
        countdownJob?.cancel()
        clock.pause()
        val pos = clock.getCurrentPositionMs(songData?.durationMs)
        _state.value = PlaybackState.Paused(pos)
        progressTickerJob?.cancel()
    }

    /**
     * 一時停止から再生を再開する。
     */
    fun resume() {
        if (_state.value !is PlaybackState.Paused) return
        countdownJob?.cancel()
        clock.resume()
        val pos = clock.getCurrentPositionMs(songData?.durationMs)
        _state.value = PlaybackState.Playing(pos)
        startProgressTicker()
    }

    /**
     * 再生を停止し、初期状態(Stopped)へ遷移する。
     */
    fun stop() {
        countdownJob?.cancel()
        progressTickerJob?.cancel()
        clock.stop()
        _state.value = PlaybackState.Stopped
    }

    /**
     * 先頭から再度再生を開始する。
     * Pause中・Playing中・Finished状態からでも同じ挙動。
     */
    fun restart(skipCountdown: Boolean = false) {
        stop()
        play(skipCountdown = skipCountdown)
    }

    /**
     * 指定位置（ミリ秒）へシークする。
     */
    fun seekTo(positionMs: Long) {
        val duration = songData?.durationMs ?: Long.MAX_VALUE
        val targetPos = positionMs.coerceIn(0L, duration)
        clock.seekTo(targetPos)

        when (_state.value) {
            is PlaybackState.Playing -> {
                _state.value = PlaybackState.Playing(targetPos)
            }
            is PlaybackState.Paused, is PlaybackState.Finished -> {
                _state.value = PlaybackState.Paused(targetPos)
            }
            is PlaybackState.Stopped -> {
                // Stoppedの場合は位置のみ変更
            }
            is PlaybackState.CountingDown -> {
                // カウントダウン中のシークは一旦停止
                stop()
            }
        }
    }

    /**
     * 現在位置から指定時間（ミリ秒）巻き戻す。
     * デフォルトは10秒 (10,000ms)。
     */
    fun seekBack(deltaMs: Long = 10_000L) {
        val current = getCurrentPositionMs()
        seekTo((current - deltaMs).coerceAtLeast(0L))
    }

    /**
     * 現在位置から指定時間（ミリ秒）早送りする。
     * デフォルトは10秒 (10,000ms)。
     */
    fun seekForward(deltaMs: Long = 10_000L) {
        val current = getCurrentPositionMs()
        val duration = songData?.durationMs ?: Long.MAX_VALUE
        seekTo((current + deltaMs).coerceAtMost(duration))
    }

    /**
     * 再生速度を変更する。
     */
    fun setSpeed(speed: Float) {
        targetPlaybackSpeed = speed
        clock.setSpeed(speed)
    }

    /**
     * 現在の楽曲位置（ミリ秒）を取得する。
     *
     * @param allowNegative カウントダウン中の負の仮想時間を許容するかどうか
     */
    fun getCurrentPositionMs(allowNegative: Boolean = false): Long {
        return clock.getCurrentPositionMs(songData?.durationMs, allowNegative = allowNegative)
    }

    /**
     * 再生中の位置同期と終了判定を行うバックグラウンド定期処理。
     */
    private fun startProgressTicker() {
        progressTickerJob?.cancel()
        progressTickerJob = scope.launch {
            val duration = songData?.durationMs ?: 0L
            while (_state.value is PlaybackState.Playing) {
                val currentPos = clock.getCurrentPositionMs(duration)
                if (duration > 0L && currentPos >= duration) {
                    clock.pause()
                    _state.value = PlaybackState.Finished(duration)
                    break
                }
                _state.value = PlaybackState.Playing(currentPos)
                delay(30L) // 約33fpsで状態を定期通知
            }
        }
    }

    /**
     * エンジンが使用するリソースを解放する。
     */
    fun release() {
        stop()
        if (isInternalScope) {
            scope.cancel()
        }
    }
}
