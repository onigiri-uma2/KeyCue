package com.onigiri.keycue.model

/**
 * 演奏支援の再生設定を表すデータクラス。
 *
 * @param speed 再生速度比率 (1.0f = 100%)
 * @param noteLeadTimeMs 落下ノート（Falling Notes）の先読み表示時間（ミリ秒）
 * @param approachCircleLeadTimeMs タイミングサークル（Approach Circle）の表示・縮小開始時間（ミリ秒）
 * @param countdownMs 開始前カウントダウン時間（ミリ秒）
 */
data class PlaybackConfig(
    val speed: Float = 1.0f,
    val noteLeadTimeMs: Long = DEFAULT_NOTE_LEAD_TIME_MS,
    val approachCircleLeadTimeMs: Long = DEFAULT_APPROACH_CIRCLE_LEAD_TIME_MS,
    val countdownMs: Long = 3000L
) {
    /**
     * 設定値を正規化した新しいインスタンスを返す。
     */
    fun normalized(): PlaybackConfig = normalize(
        speed = speed,
        noteLeadTimeMs = noteLeadTimeMs,
        approachCircleLeadTimeMs = approachCircleLeadTimeMs,
        countdownMs = countdownMs
    )

    companion object {
        const val DEFAULT_NOTE_LEAD_TIME_MS = 300L
        const val DEFAULT_APPROACH_CIRCLE_LEAD_TIME_MS = 200L

        const val MIN_SPEED = 0.25f
        const val MAX_SPEED = 2.0f
        const val MIN_NOTE_LEAD_TIME_MS = 300L
        const val MAX_NOTE_LEAD_TIME_MS = 2000L
        const val MIN_APPROACH_CIRCLE_LEAD_TIME_MS = 100L
        const val MAX_APPROACH_CIRCLE_LEAD_TIME_MS = 2000L
        const val MIN_COUNTDOWN_MS = 0L
        const val MAX_COUNTDOWN_MS = 5000L

        const val NOTE_LEAD_TIME_STEP_MS = 100L
        const val APPROACH_CIRCLE_LEAD_TIME_STEP_MS = 50L

        /**
         * 再生設定値の正規化を一元管理する。
         *
         * 範囲制約:
         * - MIN_SPEED <= speed <= MAX_SPEED
         * - MIN_NOTE_LEAD_TIME_MS <= noteLeadTimeMs <= MAX_NOTE_LEAD_TIME_MS
         * - MIN_APPROACH_CIRCLE_LEAD_TIME_MS <= approachCircleLeadTimeMs <= MAX_APPROACH_CIRCLE_LEAD_TIME_MS
         * - MIN_COUNTDOWN_MS <= countdownMs <= MAX_COUNTDOWN_MS
         */
        /**
         * ノート先読み時間とタイミングサークル先読み時間の正規化ペアを算出する。
         * Note Lead Time と Approach Circle Lead Time は互いに干渉せず、完全に独立して正規化される。
         */
        fun normalizeLeadTimes(
            noteLeadTimeMs: Long,
            approachCircleLeadTimeMs: Long
        ): Pair<Long, Long> {
            val clampedNote = noteLeadTimeMs.coerceIn(MIN_NOTE_LEAD_TIME_MS, MAX_NOTE_LEAD_TIME_MS)
            val clampedCircle = approachCircleLeadTimeMs.coerceIn(MIN_APPROACH_CIRCLE_LEAD_TIME_MS, MAX_APPROACH_CIRCLE_LEAD_TIME_MS)
            return Pair(clampedNote, clampedCircle)
        }

        fun normalize(
            speed: Float = 1.0f,
            noteLeadTimeMs: Long = DEFAULT_NOTE_LEAD_TIME_MS,
            approachCircleLeadTimeMs: Long = DEFAULT_APPROACH_CIRCLE_LEAD_TIME_MS,
            countdownMs: Long = 3000L
        ): PlaybackConfig {
            val clampedSpeed = speed.coerceIn(MIN_SPEED, MAX_SPEED)
            val (clampedNote, clampedCircle) = normalizeLeadTimes(noteLeadTimeMs, approachCircleLeadTimeMs)
            val clampedCountdown = countdownMs.coerceIn(MIN_COUNTDOWN_MS, MAX_COUNTDOWN_MS)
            return PlaybackConfig(
                speed = clampedSpeed,
                noteLeadTimeMs = clampedNote,
                approachCircleLeadTimeMs = clampedCircle,
                countdownMs = clampedCountdown
            )
        }
    }
}
