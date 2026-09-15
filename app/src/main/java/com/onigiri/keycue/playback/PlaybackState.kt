package com.onigiri.keycue.playback

/**
 * 演奏支援の再生状態を表す sealed interface。
 */
sealed interface PlaybackState {
    /**
     * 停止状態。
     */
    data object Stopped : PlaybackState

    /**
     * 再生開始前のカウントダウン状態。
     *
     * @param remainingMs 残りカウントダウン時間（ミリ秒）
     * @param countNumber 表示用カウント番号 (3, 2, 1 等)
     */
    data class CountingDown(
        val remainingMs: Long,
        val countNumber: Int
    ) : PlaybackState

    /**
     * 演奏再生中状態。
     *
     * @param positionMs 現在の楽曲位置（ミリ秒）
     */
    data class Playing(
        val positionMs: Long
    ) : PlaybackState

    /**
     * 一時停止状態。
     *
     * @param positionMs 一時停止位置（ミリ秒）
     */
    data class Paused(
        val positionMs: Long
    ) : PlaybackState

    /**
     * 再生完了状態。
     *
     * @param positionMs 終了位置（ミリ秒）
     */
    data class Finished(
        val positionMs: Long
    ) : PlaybackState
}

/**
 * 現在の状態に応じた楽曲再生位置（ミリ秒）を取得する。
 */
val PlaybackState.currentPositionMs: Long
    get() = when (this) {
        is PlaybackState.Stopped -> 0L
        is PlaybackState.CountingDown -> 0L
        is PlaybackState.Playing -> positionMs
        is PlaybackState.Paused -> positionMs
        is PlaybackState.Finished -> positionMs
    }

/**
 * 現在再生中（Playing状態）かどうかを判定する。
 */
val PlaybackState.isPlaying: Boolean
    get() = this is PlaybackState.Playing
