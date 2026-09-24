package com.onigiri.keycue.audio

import com.onigiri.keycue.model.BeatSubdivision
import com.onigiri.keycue.model.MetronomeConfig
import com.onigiri.keycue.model.timing.TimeSignature
import kotlin.math.floor
import kotlin.math.round

/**
 * 固定BPM・固定拍子（Sky StudioのBPM自動取得＋手動拍子フォールバック等）に基づくタイムライン。
 *
 * @param bpm テンポ（40〜240）
 * @param timeSignature 拍子
 * @param subdivision クリック分割単位（4分音符/8分音符）
 * @param accentEnabled 小節頭アクセントを有効にするか
 * @param beatOffsetMs 拍位置オフセット（ミリ秒）
 * @param sourceKind 情報源種別（デフォルトは SKY_STUDIO）
 * @param isBpmAuto BPMが自動取得されたものか
 * @param isTimeSignatureAuto 拍子が自動取得されたものか
 */
class ConstantBeatTimeline(
    val bpm: Int,
    val timeSignature: TimeSignature,
    val subdivision: BeatSubdivision,
    val accentEnabled: Boolean,
    val beatOffsetMs: Long,
    override val sourceKind: TimingSourceKind = TimingSourceKind.SKY_STUDIO,
    override val isBpmAuto: Boolean = true,
    override val isTimeSignatureAuto: Boolean = false
) : BeatTimeline {

    // 拍計算用キャッシュ
    private val quarterIntervalMs: Double = 60_000.0 / bpm.coerceIn(MetronomeConfig.MIN_BPM, MetronomeConfig.MAX_BPM)
    private val clickIntervalMs: Double = when (subdivision) {
        BeatSubdivision.QUARTER -> quarterIntervalMs
        BeatSubdivision.EIGHTH -> quarterIntervalMs / 2.0
    }
    private val clicksPerMeasure: Int = when (subdivision) {
        BeatSubdivision.QUARTER -> timeSignature.numerator
        BeatSubdivision.EIGHTH -> (timeSignature.numerator * 8) / timeSignature.denominator
    }.coerceAtLeast(1)

    override fun beatForIndex(index: Long): MetronomeBeat? {
        if (index < 0L) return null
        val rawTimeMs = beatOffsetMs + index * clickIntervalMs
        val timeMs = round(rawTimeMs).toLong()
        val isAccent = accentEnabled && (clicksPerMeasure > 0) && (index % clicksPerMeasure == 0L)
        return MetronomeBeat(
            index = index,
            timeMs = timeMs,
            isAccent = isAccent
        )
    }

    override fun nextBeatIndexAtOrAfter(positionMs: Long): Long {
        val rawIndex = (positionMs - beatOffsetMs) / clickIntervalMs
        var candidateIndex = (floor(rawIndex).toLong() - 2L).coerceAtLeast(0L)
        while (true) {
            val beat = beatForIndex(candidateIndex) ?: return candidateIndex
            if (beat.timeMs >= positionMs && beat.timeMs >= 0L) {
                return candidateIndex
            }
            candidateIndex++
        }
    }

    override fun candidateBeatIndex(positionMs: Long, toleratedDelayMs: Long): Long {
        val thresholdMs = positionMs - toleratedDelayMs
        val rawIndex = (thresholdMs - beatOffsetMs) / clickIntervalMs
        var candidateIndex = (floor(rawIndex).toLong() - 2L).coerceAtLeast(0L)
        while (true) {
            val beat = beatForIndex(candidateIndex) ?: return candidateIndex
            if (beat.timeMs >= thresholdMs && beat.timeMs >= 0L) {
                return candidateIndex
            }
            candidateIndex++
        }
    }

    override fun bpmAt(positionMs: Long): Double = bpm.toDouble()

    override fun timeSignatureAt(positionMs: Long): TimeSignature = timeSignature
}
