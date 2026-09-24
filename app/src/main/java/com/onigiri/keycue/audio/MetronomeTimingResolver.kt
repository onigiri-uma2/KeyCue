package com.onigiri.keycue.audio

import com.onigiri.keycue.model.MetronomeConfig
import com.onigiri.keycue.model.MetronomeTimingMode
import com.onigiri.keycue.model.timing.SongTimingMetadata
import com.onigiri.keycue.model.timing.TimeSignature

/**
 * [MetronomeConfig] のタイミングモード（AUTO / MANUAL）および楽曲の [SongTimingMetadata] から、
 * 最適な [BeatTimeline] を解決するリゾルバ。
 *
 * 【優先度ルール】
 * - MANUAL モード時: 楽曲情報に関わらず常に [ManualBeatTimeline] を使用。
 * - AUTO モード時:
 *   - MIDI: ファイル内の明示的テンポ・拍子 -> MIDI標準デフォルト（120BPM, 4/4）で [MidiBeatTimeline] を生成。
 *   - Sky Studio: 妥当なBPMメタデータ（40〜240）があればBPMを自動採用、拍子は手動設定を採用して [ConstantBeatTimeline] を生成。
 *     BPMメタデータがない/不正な場合は手動BPMへフォールバックし [ManualBeatTimeline] を生成。
 *   - メタデータなし/その他: [ManualBeatTimeline] へフォールバック。
 */
object MetronomeTimingResolver {

    fun resolveTimeline(
        config: MetronomeConfig,
        timingMetadata: SongTimingMetadata?,
        durationMs: Long
    ): BeatTimeline {
        val safeConfig = config.normalized()

        if (safeConfig.timingMode == MetronomeTimingMode.MANUAL || timingMetadata == null) {
            return ManualBeatTimeline(safeConfig)
        }

        return when (timingMetadata) {
            is SongTimingMetadata.Midi -> {
                MidiBeatTimeline(
                    ppqn = timingMetadata.ppqn,
                    tempoMap = timingMetadata.tempoMap,
                    timeSignatureMap = timingMetadata.timeSignatureMap,
                    subdivision = safeConfig.subdivision,
                    accentEnabled = safeConfig.accentEnabled,
                    beatOffsetMs = safeConfig.beatOffsetMs,
                    songDurationMs = durationMs
                )
            }
            is SongTimingMetadata.SkyStudio -> {
                val validBpm = timingMetadata.validBpm
                if (validBpm != null) {
                    ConstantBeatTimeline(
                        bpm = validBpm,
                        timeSignature = TimeSignature(safeConfig.beatsPerBar, 4),
                        subdivision = safeConfig.subdivision,
                        accentEnabled = safeConfig.accentEnabled,
                        beatOffsetMs = safeConfig.beatOffsetMs,
                        sourceKind = TimingSourceKind.SKY_STUDIO,
                        isBpmAuto = true,
                        isTimeSignatureAuto = false
                    )
                } else {
                    ManualBeatTimeline(safeConfig)
                }
            }
        }
    }
}
