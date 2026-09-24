package com.onigiri.keycue.model

/**
 * 音声メトロノームの再生設定を表す不変データモデル。
 *
 * 演奏中に一定の拍（4分音符 / 8分音符）を耳で確認し、テンポキープやリズムの練習を支援します。
 *
 * @param enabled メトロノーム音声再生が有効かどうか（初期値は必ず false）
 * @param bpm テンポ（BPM: 40〜240）
 * @param beatsPerBar 1小節あたりの拍数（3 または 4）
 * @param subdivision 1拍の分割単位（4分音符または8分音符）
 * @param accentEnabled 小節頭（第1拍）のアクセント音を有効にするかどうか
 * @param volumePercent クリック音量パーセント（0〜100%）
 * @param beatOffsetMs 楽曲時間軸に対する拍位置オフセット（-2000〜+2000ミリ秒）
 */
data class MetronomeConfig(
    val enabled: Boolean = false,
    val bpm: Int = DEFAULT_BPM,
    val beatsPerBar: Int = DEFAULT_BEATS_PER_BAR,
    val subdivision: BeatSubdivision = BeatSubdivision.QUARTER,
    val accentEnabled: Boolean = true,
    val volumePercent: Int = DEFAULT_VOLUME_PERCENT,
    val beatOffsetMs: Long = DEFAULT_BEAT_OFFSET_MS
) {
    /**
     * 設定値を安全な範囲に正規化した新しいインスタンスを返す。
     */
    fun normalized(): MetronomeConfig = normalize(
        enabled = enabled,
        bpm = bpm,
        beatsPerBar = beatsPerBar,
        subdivision = subdivision,
        accentEnabled = accentEnabled,
        volumePercent = volumePercent,
        beatOffsetMs = beatOffsetMs
    )

    companion object {
        const val MIN_BPM = 40
        const val MAX_BPM = 240
        const val DEFAULT_BPM = 120

        const val DEFAULT_BEATS_PER_BAR = 4
        val SUPPORTED_BEATS_PER_BAR = listOf(3, 4)

        const val MIN_VOLUME_PERCENT = 0
        const val MAX_VOLUME_PERCENT = 100
        const val DEFAULT_VOLUME_PERCENT = 30

        const val MIN_BEAT_OFFSET_MS = -2000L
        const val MAX_BEAT_OFFSET_MS = 2000L
        const val DEFAULT_BEAT_OFFSET_MS = 0L

        const val BPM_STEP = 1
        const val BEAT_OFFSET_STEP_MS = 10L

        /**
         * 設定値の正規化を一元管理する。
         *
         * 範囲制約:
         * - MIN_BPM <= bpm <= MAX_BPM
         * - beatsPerBar は 3 または 4（それ以外は4に正規化）
         * - MIN_VOLUME_PERCENT <= volumePercent <= MAX_VOLUME_PERCENT
         * - MIN_BEAT_OFFSET_MS <= beatOffsetMs <= MAX_BEAT_OFFSET_MS
         */
        fun normalize(
            enabled: Boolean = false,
            bpm: Int = DEFAULT_BPM,
            beatsPerBar: Int = DEFAULT_BEATS_PER_BAR,
            subdivision: BeatSubdivision = BeatSubdivision.QUARTER,
            accentEnabled: Boolean = true,
            volumePercent: Int = DEFAULT_VOLUME_PERCENT,
            beatOffsetMs: Long = DEFAULT_BEAT_OFFSET_MS
        ): MetronomeConfig {
            val clampedBpm = bpm.coerceIn(MIN_BPM, MAX_BPM)
            val normalizedBeatsPerBar = if (beatsPerBar in SUPPORTED_BEATS_PER_BAR) beatsPerBar else DEFAULT_BEATS_PER_BAR
            val clampedVolume = volumePercent.coerceIn(MIN_VOLUME_PERCENT, MAX_VOLUME_PERCENT)
            val clampedOffset = beatOffsetMs.coerceIn(MIN_BEAT_OFFSET_MS, MAX_BEAT_OFFSET_MS)

            return MetronomeConfig(
                enabled = enabled,
                bpm = clampedBpm,
                beatsPerBar = normalizedBeatsPerBar,
                subdivision = subdivision,
                accentEnabled = accentEnabled,
                volumePercent = clampedVolume,
                beatOffsetMs = clampedOffset
            )
        }
    }
}

/**
 * メトロノームの拍分割単位。
 */
enum class BeatSubdivision {
    /** 4分音符（1拍に1回クリック） */
    QUARTER,
    /** 8分音符（1拍に2回クリック） */
    EIGHTH
}
