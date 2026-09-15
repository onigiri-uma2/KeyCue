package com.onigiri.keycue.model

/**
 * 実際に解決・適用されたMIDIマッピング結果データクラス。
 *
 * @param root マッピングのRoot音
 * @param scale マッピングのスケール種別
 * @param baseOctave マッピングの開始オクターブ
 * @param midiNotes 15キー（0..14）に対応するMIDIノート番号のリスト
 * @param mappedEventCount 15キーへ割り当てられたNote Onイベント数
 * @param totalEventCount 評価対象となった総Note Onイベント数（Percussion除外）
 */
data class ResolvedMidiMapping(
    val root: PitchClass,
    val scale: ScaleType,
    val baseOctave: Int,
    val midiNotes: List<Int>,
    val mappedEventCount: Int,
    val totalEventCount: Int
) {
    /**
     * マッピング成功割合（0.0f〜100.0f）。
     * totalEventCount が 0 の場合は安全に 0.0f を返す。
     */
    val mappedPercentage: Float
        get() = if (totalEventCount > 0) {
            (mappedEventCount.toFloat() / totalEventCount) * 100f
        } else {
            0.0f
        }

    /**
     * 指定されたMIDIノート番号がマッピングに含まれている場合、そのキーインデックス (0..14) を返す。
     * 存在しない場合は null を返す。
     */
    fun keyOf(midiNote: Int): Int? {
        val index = midiNotes.indexOf(midiNote)
        return if (index != -1) index else null
    }

    /**
     * 15音それぞれの音名表記リスト（例: ["C4", "D4", "E4", ...]）を返す。
     */
    fun getFormattedNoteNames(): List<String> {
        return midiNotes.map { PitchClass.formatMidiNote(it) }
    }
}
