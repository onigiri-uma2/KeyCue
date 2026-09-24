package com.onigiri.keycue.audio

import com.onigiri.keycue.model.BeatSubdivision
import com.onigiri.keycue.model.MetronomeConfig
import kotlin.math.ceil
import kotlin.math.round

/**
 * 楽曲時間軸におけるメトロノーム拍グリッド・クリック時刻を算出する計算クラス。
 *
 * Android SDKに一切依存しない純粋関数として実装されており、BPM・拍子・分割・オフセットに基づく
 * 正確な拍時刻およびアクセント判定をミリ秒単位で提供します。
 */
object MetronomeBeatCalculator {

    /**
     * 1拍あたりの基本間隔（4分音符の間隔、ミリ秒）を算出する。
     */
    fun calculateQuarterIntervalMs(bpm: Int): Double {
        val safeBpm = bpm.coerceIn(MetronomeConfig.MIN_BPM, MetronomeConfig.MAX_BPM)
        return 60_000.0 / safeBpm
    }

    /**
     * 設定に応じたクリック間隔（ミリ秒）を算出する。
     */
    fun calculateClickIntervalMs(config: MetronomeConfig): Double {
        val quarterInterval = calculateQuarterIntervalMs(config.bpm)
        return when (config.subdivision) {
            BeatSubdivision.QUARTER -> quarterInterval
            BeatSubdivision.EIGHTH -> quarterInterval / 2.0
        }
    }

    /**
     * 1小節あたりのクリック総数を算出する。
     */
    fun calculateClicksPerBar(config: MetronomeConfig): Int {
        val subdivisionFactor = when (config.subdivision) {
            BeatSubdivision.QUARTER -> 1
            BeatSubdivision.EIGHTH -> 2
        }
        return config.beatsPerBar * subdivisionFactor
    }

    /**
     * 指定されたクリックインデックス [clickIndex] に対する発音予定情報 [MetronomeBeat] を算出する。
     *
     * 各クリック時刻はインデックスから直接計算（clickTimeMs = beatOffsetMs + clickIndex * clickIntervalMs）され、
     * 前回の時刻に丸め済み数値を加算し続けることによる誤差累積を防止します。
     *
     * @param config メトロノーム設定
     * @param clickIndex クリックインデックス（0以上の整数）
     */
    fun calculateBeat(config: MetronomeConfig, clickIndex: Long): MetronomeBeat {
        require(clickIndex >= 0L) { "clickIndex must be non-negative: $clickIndex" }
        val clickIntervalMs = calculateClickIntervalMs(config)
        val rawTimeMs = config.beatOffsetMs + clickIndex * clickIntervalMs
        val timeMs = round(rawTimeMs).toLong()

        val clicksPerBar = calculateClicksPerBar(config)
        val isAccent = config.accentEnabled && (clicksPerBar > 0) && (clickIndex % clicksPerBar == 0L)

        return MetronomeBeat(
            index = clickIndex,
            timeMs = timeMs,
            isAccent = isAccent
        )
    }

    /**
     * 指定された楽曲再生位置 [currentPositionMs] において、
     * 遅延許容時間 [toleratedDelayMs] を加味して次に発音対象とすべき最小の [clickIndex] を算出する。
     *
     * 予定時刻がすでに過去であっても、経過時間が [toleratedDelayMs] 以内であれば
     * その拍が候補として返されます。それを超えて遅延している古い拍はスキップされます。
     * また、予定時刻が負（< 0ms）となる拍は除外されます。
     *
     * @param config メトロノーム設定
     * @param currentPositionMs 現在の楽曲再生位置（ミリ秒）
     * @param toleratedDelayMs 許容遅延時間（ミリ秒、デフォルト30ms）
     */
    fun findCandidateClickIndex(
        config: MetronomeConfig,
        currentPositionMs: Long,
        toleratedDelayMs: Long = 30L
    ): Long {
        val clickIntervalMs = calculateClickIntervalMs(config)
        // 許容遅延を加味した有効下限楽曲時刻
        val thresholdMs = currentPositionMs - toleratedDelayMs
        // thresholdMs <= beatOffsetMs + index * clickIntervalMs
        // index >= (thresholdMs - beatOffsetMs) / clickIntervalMs
        val rawIndex = (thresholdMs - config.beatOffsetMs) / clickIntervalMs
        var candidateIndex = ceil(rawIndex).toLong().coerceAtLeast(0L)

        // 負の予定時刻の拍（timeMs < 0）は通常再生では鳴らさないためスキップ
        while (true) {
            val beat = calculateBeat(config, candidateIndex)
            if (beat.timeMs >= 0L) {
                break
            }
            candidateIndex++
        }

        return candidateIndex
    }

    /**
     * 手動シーク時など、指定された楽曲位置 [targetPositionMs] 以降（遅延許容なし、厳密に未来または現在）の
     * 最初のクリックインデックスを算出する。
     */
    fun findNextClickIndexFromPosition(
        config: MetronomeConfig,
        targetPositionMs: Long
    ): Long {
        val clickIntervalMs = calculateClickIntervalMs(config)
        // targetPositionMs <= beatOffsetMs + index * clickIntervalMs
        // index >= (targetPositionMs - beatOffsetMs) / clickIntervalMs
        val rawIndex = (targetPositionMs - config.beatOffsetMs) / clickIntervalMs
        var candidateIndex = ceil(rawIndex).toLong().coerceAtLeast(0L)

        while (true) {
            val beat = calculateBeat(config, candidateIndex)
            if (beat.timeMs >= targetPositionMs && beat.timeMs >= 0L) {
                break
            }
            candidateIndex++
        }

        return candidateIndex
    }
}
