package com.onigiri.keycue.model.timing

/**
 * 楽曲の拍子を表すデータクラス。
 *
 * 例: 4/4, 3/4, 6/8, 5/4, 7/8 など。
 *
 * @param numerator 拍子の分子（1小節内の基本拍数）
 * @param denominator 拍子の分母（基準となる音符の種類、2のべき乗: 2, 4, 8, 16 等）
 */
data class TimeSignature(
    val numerator: Int,
    val denominator: Int
) {
    init {
        require(numerator > 0) { "Numerator must be positive, but was $numerator" }
        require(denominator > 0 && (denominator and (denominator - 1)) == 0) {
            "Denominator must be a power of 2, but was $denominator"
        }
    }

    /**
     * 表示用文字列表現（例: "4/4", "6/8"）
     */
    val displayString: String
        get() = "$numerator/$denominator"

    companion object {
        /** MIDI標準およびポピュラー音楽のデフォルト拍子: 4/4 */
        val DEFAULT = TimeSignature(4, 4)
    }
}
