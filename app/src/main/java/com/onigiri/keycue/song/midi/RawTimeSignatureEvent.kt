package com.onigiri.keycue.song.midi

import com.onigiri.keycue.model.timing.TimeSignature

/**
 * MIDIファイルから抽出された Time Signature メタイベント (0xFF 0x58)。
 *
 * @param tick 変更が発生した絶対tick
 * @param numerator 拍子の分子
 * @param denominator 拍子の分母（2^dd から計算された実際の値）
 * @param clocksPerClick メトロノームクリック間のMIDIクロック数 (cc)
 * @param notated32ndNotesPerQuarter 4分音符あたりの32分音符数 (bb)
 * @param trackIndex 抽出元のトラックインデックス（決定的なソート・コンダクタートラック優先用）
 * @param eventIndex トラック内の出現順序
 */
data class RawTimeSignatureEvent(
    val tick: Long,
    val numerator: Int,
    val denominator: Int,
    val clocksPerClick: Int = 24,
    val notated32ndNotesPerQuarter: Int = 8,
    val trackIndex: Int = 0,
    val eventIndex: Int = 0
) {
    /**
     * [TimeSignature] モデルへ変換する。
     */
    fun toTimeSignature(): TimeSignature = TimeSignature(numerator, denominator)
}
