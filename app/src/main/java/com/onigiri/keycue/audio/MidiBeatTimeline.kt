package com.onigiri.keycue.audio

import com.onigiri.keycue.model.BeatSubdivision
import com.onigiri.keycue.model.MetronomeConfig
import com.onigiri.keycue.model.timing.TimeSignature
import com.onigiri.keycue.song.midi.TempoMap
import com.onigiri.keycue.song.midi.TimeSignatureMap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * MIDIファイルのテンポ変更マップ（[TempoMap]）および拍子変更マップ（[TimeSignatureMap]）に
 * 基づく可変テンポ・可変拍子タイムライン。
 *
 * 【設計仕様】
 * 1. 拍グリッドはノート時刻から推測せず、PPQN・テンポ・拍子マップに基づきtick空間で計算。
 * 2. テンポ変更によってtick上の拍位置は不変とし、実時間msへの変換にのみTempoMapを適用。
 * 3. 拍子変更発生tickを新しい小節の開始基準（第1拍・アクセント）として扱う。
 * 4. 2/4, 3/4, 4/4, 5/4, 6/8, 5/8, 7/8 等の任意の分子・分母に正確に対応。
 * 5. 全拍をロード時に事前計算し、シーク時・巻き戻し時は二分探索（O(log N)）で高速特定。
 * 6. 予定拍時刻にのみ [beatOffsetMs] を適用し、原本のMIDIタイミングは不変。
 */
class MidiBeatTimeline(
    val ppqn: Int,
    val tempoMap: TempoMap,
    val timeSignatureMap: TimeSignatureMap,
    val subdivision: BeatSubdivision,
    val accentEnabled: Boolean,
    val beatOffsetMs: Long,
    val songDurationMs: Long,
    override val sourceKind: TimingSourceKind = if (tempoMap.hasExplicitTempo || timeSignatureMap.hasExplicitTimeSignature) TimingSourceKind.MIDI else TimingSourceKind.MIDI_DEFAULT,
    override val isBpmAuto: Boolean = tempoMap.hasExplicitTempo,
    override val isTimeSignatureAuto: Boolean = timeSignatureMap.hasExplicitTimeSignature
) : BeatTimeline {

    companion object {
        /** 安全上限拍数（極端に長いファイルでのメモリ保護） */
        private const val MAX_BEATS_LIMIT = 100_000
        /** 浮動小数点計算による小節終端との重複防止用イプシロン (tick) */
        private const val TICK_EPSILON = 1e-4
    }

    private val beats: List<MetronomeBeat> = buildBeats()

    private fun buildBeats(): List<MetronomeBeat> {
        val list = mutableListOf<MetronomeBeat>()
        val tsPoints = timeSignatureMap.points
        if (tsPoints.isEmpty()) return list

        val durationTick = tempoMap.msToTick(max(0L, songDurationMs))
        val maxTick = max(tempoMap.maxTick, durationTick) + (ppqn.toLong() * 16L)

        val stepTicks = when (subdivision) {
            BeatSubdivision.QUARTER -> ppqn.toDouble()
            BeatSubdivision.EIGHTH -> ppqn.toDouble() / 2.0
        }

        for (i in tsPoints.indices) {
            val currentTs = tsPoints[i]
            val nextTs = tsPoints.getOrNull(i + 1)
            val sectionStartTick = currentTs.tick
            val sectionEndTick = nextTs?.tick ?: maxTick

            if (sectionEndTick <= sectionStartTick) continue

            // 小節長（tick） = ppqn * 4 * numerator / denominator
            // 整数tickに収まらない拍子でも端数累積を防ぐためDouble精度で計算
            val measureTicks = ppqn.toDouble() * 4.0 * currentTs.timeSignature.numerator / currentTs.timeSignature.denominator
            if (measureTicks <= 0.0) continue

            // 4分音符グリッドと小節境界が一致しない場合（例: 5/8や7/8で4分音符クリック時）のクリック仕様:
            // 各小節は必ず小節開始境界（barStartTick）を小節頭（ダウンビート）として開始し、
            // 小節内で stepTicks ごとにクリックを配置する。小節終端を超えるクリックは破棄され、
            // 次の小節は正確な小節境界から新たにダウンビートで再開する（小節境界リセット仕様）。
            var measureIndex = 0
            while (list.size < MAX_BEATS_LIMIT) {
                val barStartTick = sectionStartTick.toDouble() + (measureIndex * measureTicks)
                if (barStartTick >= sectionEndTick) break

                val nextBarStartTick = sectionStartTick.toDouble() + ((measureIndex + 1) * measureTicks)
                val barEndTick = min(sectionEndTick.toDouble(), nextBarStartTick)

                // 小節頭 (ダウンビート)
                val downbeatTimeMs = Math.round(tempoMap.tickToMs(barStartTick)) + beatOffsetMs
                list.add(
                    MetronomeBeat(
                        index = list.size.toLong(),
                        timeMs = downbeatTimeMs,
                        isAccent = accentEnabled
                    )
                )

                // 小節内の後続クリック（小節境界との重複・浮動小数点誤差を TICK_EPSILON で安全に除外）
                var clickTick = barStartTick + stepTicks
                while (barEndTick - clickTick > TICK_EPSILON && list.size < MAX_BEATS_LIMIT) {
                    val clickTimeMs = Math.round(tempoMap.tickToMs(clickTick)) + beatOffsetMs
                    list.add(
                        MetronomeBeat(
                            index = list.size.toLong(),
                            timeMs = clickTimeMs,
                            isAccent = false
                        )
                    )
                    clickTick += stepTicks
                }

                measureIndex++
            }

            if (list.size >= MAX_BEATS_LIMIT) break
        }

        return list
    }

    override fun beatForIndex(index: Long): MetronomeBeat? {
        if (index < 0L || index >= beats.size) return null
        return beats[index.toInt()]
    }

    override fun nextBeatIndexAtOrAfter(positionMs: Long): Long {
        if (beats.isEmpty()) return 0L
        val pos = beats.binarySearchBy(positionMs) { it.timeMs }
        val index = if (pos >= 0) {
            var first = pos
            while (first > 0 && beats[first - 1].timeMs == positionMs) {
                first--
            }
            first
        } else {
            -pos - 1
        }
        return index.toLong()
    }

    override fun candidateBeatIndex(positionMs: Long, toleratedDelayMs: Long): Long {
        if (beats.isEmpty()) return 0L
        val thresholdMs = positionMs - toleratedDelayMs
        val targetMs = max(0L, thresholdMs)
        val pos = beats.binarySearchBy(targetMs) { it.timeMs }
        val index = if (pos >= 0) {
            var first = pos
            while (first > 0 && beats[first - 1].timeMs == targetMs) {
                first--
            }
            first
        } else {
            -pos - 1
        }
        return index.toLong()
    }

    override fun bpmAt(positionMs: Long): Double {
        return tempoMap.bpmAtMs(positionMs)
    }

    override fun timeSignatureAt(positionMs: Long): TimeSignature {
        return timeSignatureMap.timeSignatureAtMs(positionMs, tempoMap)
    }
}
