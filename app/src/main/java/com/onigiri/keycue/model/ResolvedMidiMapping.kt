package com.onigiri.keycue.model

/**
 * 実際に解決・適用されたMIDIマッピング結果データクラス。
 *
 * @param root マッピングのRoot音
 * @param scale マッピングのスケール種別
 * @param baseOctave マッピングの開始オクターブ
 * @param midiNotes 各キーに対応するMIDIノート番号のリスト
 * @param mappedEventCount 各キーへ割り当てられたNote Onイベント数
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
     * 各ガイドキーに対応するキー配置音名の文字列表記リスト（例: ["C4", "D4", ...]）。
     * [midiNotes] 生成時の順序を保持して一度生成され、以後読み取り専用として扱うラベルList。
     * [guideLabels] の各要素はそのインデックスに対応するキー配置音の文字列表記であり、
     * `guideLabels[index] == PitchClass.formatMidiNote(midiNotes[index])` を保証する。
     */
    val guideLabels: List<String> = midiNotes.map { PitchClass.formatMidiNote(it) }

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
     * 指定されたMIDIノート番号がマッピングに含まれている場合、そのキーインデックスを返す。
     * 存在しない場合は null を返す。
     */
    fun keyOf(midiNote: Int): Int? {
        val index = midiNotes.indexOf(midiNote)
        return if (index != -1) index else null
    }

    /**
     * 各音それぞれの音名表記リスト（例: ["C4", "D4", "E4", ...]）を返す。
     * 事前生成済みの [guideLabels] を再利用し、重複計算を回避する。
     */
    fun getFormattedNoteNames(): List<String> = guideLabels
}
