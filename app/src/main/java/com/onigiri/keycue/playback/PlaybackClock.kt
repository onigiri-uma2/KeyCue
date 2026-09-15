package com.onigiri.keycue.playback

import android.os.SystemClock

/**
 * 実時間取得の抽象化インターフェース。
 * 単体テスト時に実時間待ちを避けるために利用する。
 */
fun interface TimeProvider {
    /**
     * 単調増加時間（ミリ秒）を取得する。
     */
    fun elapsedRealtime(): Long
}

/**
 * Android標準の単調増加時計 [SystemClock.elapsedRealtime] を利用する実装。
 */
object SystemTimeProvider : TimeProvider {
    override fun elapsedRealtime(): Long = SystemClock.elapsedRealtime()
}

/**
 * 再生時間を管理する単調増加クロック。
 *
 * 概念式:
 * currentSongPosition = baseSongPosition + (elapsedRealtime - baseRealtime) * speed
 *
 * @param timeProvider 単調増加時間を提供するプロバイダ（デフォルト: [SystemTimeProvider]）
 */
class PlaybackClock(
    private val timeProvider: TimeProvider = SystemTimeProvider
) {
    /**
     * 再生速度比率 (1.0f = 100%)
     */
    var speed: Float = 1.0f
        private set

    /**
     * 一時停止中フラグ
     */
    var isPaused: Boolean = false
        private set

    /**
     * 停止状態フラグ
     */
    var isStopped: Boolean = true
        private set

    private var baseRealtime: Long = 0L
    private var baseSongPosition: Long = 0L

    /**
     * 指定位置（ミリ秒）から再生を開始する。
     *
     * @param startPositionMs 再生開始位置。カウントダウン先読み時は負の値（例: -3000ms）を指定可能。
     * @param allowNegative trueの場合、0未満の開始位置をそのまま許容する。
     */
    fun play(startPositionMs: Long = 0L, allowNegative: Boolean = false) {
        baseRealtime = timeProvider.elapsedRealtime()
        baseSongPosition = if (allowNegative) startPositionMs else startPositionMs.coerceAtLeast(0L)
        isPaused = false
        isStopped = false
    }

    /**
     * 再生を一時停止する。
     * 現在位置を固定し、再開時に時間が飛ばないようにする。
     */
    fun pause() {
        if (isStopped || isPaused) return
        baseSongPosition = getCurrentPositionMs(allowNegative = true)
        baseRealtime = timeProvider.elapsedRealtime()
        isPaused = true
    }

    /**
     * 一時停止から再生を再開する。
     */
    fun resume() {
        if (isStopped || !isPaused) return
        baseRealtime = timeProvider.elapsedRealtime()
        isPaused = false
    }

    /**
     * 再生を停止し、位置を先頭(0ms)にリセットする。
     */
    fun stop() {
        baseSongPosition = 0L
        baseRealtime = 0L
        isPaused = false
        isStopped = true
    }

    /**
     * 指定された楽曲位置（ミリ秒）へ移動する。
     */
    fun seekTo(positionMs: Long) {
        val newPos = positionMs.coerceAtLeast(0L)
        baseSongPosition = newPos
        baseRealtime = timeProvider.elapsedRealtime()
    }

    /**
     * 再生速度を変更する。
     * 走行中に変更された場合でも現在位置がジャンプしないよう、変更直前の位置を基準に再設定する。
     */
    fun setSpeed(newSpeed: Float) {
        require(newSpeed > 0f) { "Speed must be positive, got: $newSpeed" }
        if (!isStopped && !isPaused) {
            baseSongPosition = getCurrentPositionMs(allowNegative = true)
            baseRealtime = timeProvider.elapsedRealtime()
        }
        speed = newSpeed
    }

    /**
     * 現在の楽曲位置（ミリ秒）を取得する。
     *
     * @param durationMs 楽曲の総演奏時間。指定された場合は上限が durationMs に丸められる。
     * @param allowNegative 負の値（カウントダウン中など）を許容するかどうか。
     */
    fun getCurrentPositionMs(durationMs: Long? = null, allowNegative: Boolean = false): Long {
        val pos = when {
            isStopped -> 0L
            isPaused -> baseSongPosition
            else -> {
                val elapsed = timeProvider.elapsedRealtime() - baseRealtime
                baseSongPosition + (elapsed * speed).toLong()
            }
        }
        val lowerBound = if (allowNegative) Long.MIN_VALUE else 0L
        return if (durationMs != null) {
            pos.coerceIn(lowerBound, durationMs)
        } else {
            pos.coerceAtLeast(lowerBound)
        }
    }
}
