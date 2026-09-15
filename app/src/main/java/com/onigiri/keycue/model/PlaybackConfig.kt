package com.onigiri.keycue.model

/**
 * 演奏支援の再生設定を表すデータクラス。
 *
 * @param speed 再生速度比率 (1.0f = 100%)
 * @param leadTimeMs 先読み落下ノーツ表示時間（ミリ秒）
 * @param highlightTimeMs 対象キーの事前ハイライト時間（ミリ秒）
 * @param countdownMs 開始前カウントダウン時間（ミリ秒）
 */
data class PlaybackConfig(
    val speed: Float = 1.0f,
    val leadTimeMs: Long = 700L,
    val highlightTimeMs: Long = 500L,
    val countdownMs: Long = 3000L
) {
    fun normalized(): PlaybackConfig = copy(
        speed = speed.coerceIn(MIN_SPEED, MAX_SPEED),
        leadTimeMs = leadTimeMs.coerceIn(MIN_LEAD_TIME_MS, MAX_LEAD_TIME_MS),
        highlightTimeMs = highlightTimeMs.coerceIn(MIN_HIGHLIGHT_TIME_MS, MAX_HIGHLIGHT_TIME_MS),
        countdownMs = countdownMs.coerceIn(MIN_COUNTDOWN_MS, MAX_COUNTDOWN_MS)
    )

    companion object {
        const val MIN_SPEED = 0.25f
        const val MAX_SPEED = 2.0f
        const val MIN_LEAD_TIME_MS = 300L
        const val MAX_LEAD_TIME_MS = 2000L
        const val MIN_HIGHLIGHT_TIME_MS = 100L
        const val MAX_HIGHLIGHT_TIME_MS = 1000L
        const val MIN_COUNTDOWN_MS = 0L
        const val MAX_COUNTDOWN_MS = 5000L
    }
}
