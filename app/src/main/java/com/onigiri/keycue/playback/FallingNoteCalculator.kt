package com.onigiri.keycue.playback

import kotlin.math.abs

/**
 * 落下ノーツ（Falling Notes）の降下進行度、Y座標、キー行判定、ジャストタイミング判定を行う純粋計算オブジェクト。
 *
 * Android View や Canvas、システム時刻に直接依存せず、引数として渡された時間とパラメータのみを用いて
 * 決定論的に計算を行うため、高速かつJVM単体テストが容易です。
 */
object FallingNoteCalculator {

    /**
     * ジャストタイミング判定のデフォルト許容時間幅（ミリ秒、前後 ±80ms）。
     */
    const val DEFAULT_JUST_THRESHOLD_MS: Long = 80L

    /**
     * 画面短辺または画面高さに対するデフォルト落下開始距離比率。
     */
    const val DEFAULT_FALL_DISTANCE_RATIO: Float = 0.35f

    /**
     * 落下ノーツの描画を許容する最大進行度（1.0を超えた直後の余韻を持たせる）。
     */
    const val MAX_DRAWABLE_PROGRESS: Float = 1.05f

    /**
     * ノートイベントと現在時刻、先読み時間から進行度 (progress) を算出する。
     *
     * remainingTime = eventTimeMs - currentTimeMs
     * progress = 1.0 - remainingTime / leadTimeMs
     *
     * @param eventTimeMs ノートの打鍵目標時刻（ミリ秒）
     * @param currentTimeMs 現在の楽曲再生位置（ミリ秒）
     * @param leadTimeMs 先読み時間（ミリ秒、例: 700ms）
     * @return 0.0 (出現位置) 〜 1.0 (判定位置) の progress
     */
    fun calculateProgress(
        eventTimeMs: Long,
        currentTimeMs: Long,
        leadTimeMs: Long
    ): Float {
        if (leadTimeMs <= 0L) return 1.0f
        val remainingTime = eventTimeMs - currentTimeMs
        return 1.0f - (remainingTime.toFloat() / leadTimeMs.toFloat())
    }

    /**
     * 指定された progress のノーツが画面上に描画対象となるかを判定する。
     * 0未満（出現前）または1を大きく超えたノーツ（通過後）は除外する。
     */
    fun shouldDraw(progress: Float): Boolean {
        return progress in 0.0f..MAX_DRAWABLE_PROGRESS
    }

    /**
     * キー番号 (0..14) から行段（段）を取得する。
     * - 0..4  : 0 (上段 -> ○)
     * - 5..9  : 1 (中段 -> □)
     * - 10..14: 2 (下段 -> △)
     */
    fun getRow(key: Int): Int {
        return (key.coerceIn(0, 14)) / 5
    }

    /**
     * キー番号 (0..14) から列 (0..4) を取得する。
     * column = key % 5
     */
    fun getColumn(key: Int): Int {
        return (key.coerceIn(0, 14)) % 5
    }

    /**
     * 落下ノーツの現在Y座標（ピクセル）を線形補間 (lerp) で算出する。
     *
     * startY = targetY - fallDistancePx
     * currentY = lerp(startY, targetY, progress)
     *
     * @param targetY 対象キーの最終判定Y座標（ピクセル）
     * @param fallDistancePx 落下移動距離（ピクセル）
     * @param progress 進行度 (0.0: startY, 1.0: targetY)
     * @return 現在のY座標
     */
    fun calculateY(targetY: Float, fallDistancePx: Float, progress: Float): Float {
        val startY = targetY - fallDistancePx
        return startY + (targetY - startY) * progress
    }

    /**
     * 指定されたイベントが現在時刻においてキー事前ハイライト範囲内かを判定する。
     *
     * 例: highlightTimeMs = 300ms の場合、イベントまで300ms以内かつ通過直後(-50ms)までハイライトする。
     */
    fun isHighlighted(
        eventTimeMs: Long,
        currentTimeMs: Long,
        highlightTimeMs: Long
    ): Boolean {
        val delta = eventTimeMs - currentTimeMs
        return delta in -50L..highlightTimeMs
    }

    /**
     * 指定されたイベントが現在時刻においてジャストタイミング（リング表示）範囲内かを判定する。
     *
     * |eventTimeMs - currentTimeMs| <= justThresholdMs
     */
    fun isJustTiming(
        eventTimeMs: Long,
        currentTimeMs: Long,
        justThresholdMs: Long = DEFAULT_JUST_THRESHOLD_MS
    ): Boolean {
        val diff = abs(eventTimeMs - currentTimeMs)
        return diff <= justThresholdMs
    }
}
