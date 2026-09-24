package com.onigiri.keycue.audio

import com.onigiri.keycue.model.MetronomeConfig
import com.onigiri.keycue.model.timing.TimeSignature

/**
 * ユーザーの手動設定（[MetronomeConfig]）に基づく固定テンポ・固定拍子のタイムライン。
 */
class ManualBeatTimeline(
    val config: MetronomeConfig
) : BeatTimeline {

    override fun beatForIndex(index: Long): MetronomeBeat? {
        if (index < 0L) return null
        return MetronomeBeatCalculator.calculateBeat(config, index)
    }

    override fun nextBeatIndexAtOrAfter(positionMs: Long): Long {
        return MetronomeBeatCalculator.findNextClickIndexFromPosition(config, positionMs)
    }

    override fun candidateBeatIndex(positionMs: Long, toleratedDelayMs: Long): Long {
        return MetronomeBeatCalculator.findCandidateClickIndex(config, positionMs, toleratedDelayMs)
    }

    override fun bpmAt(positionMs: Long): Double {
        return config.bpm.toDouble()
    }

    override fun timeSignatureAt(positionMs: Long): TimeSignature {
        return TimeSignature(config.beatsPerBar, 4)
    }

    override val sourceKind: TimingSourceKind
        get() = TimingSourceKind.MANUAL

    override val isBpmAuto: Boolean
        get() = false

    override val isTimeSignatureAuto: Boolean
        get() = false
}
