package com.onigiri.keycue.fitting

/**
 * 15キーグリッド（上段・中段・下段の3行×5列）の4隅（Anchor Corner）を表す列挙型。
 *
 * 画面上の配置調整（フィッティング）において、外枠の4点（左上、右上、左下、右下）を基準として
 * 内部の11キーをバイリニア補間するために使用されます。
 * - [TOP_LEFT]: Key 0 (左上キー)
 * - [TOP_RIGHT]: Key 4 (右上キー)
 * - [BOTTOM_LEFT]: Key 10 (左下キー)
 * - [BOTTOM_RIGHT]: Key 14 (右下キー)
 *
 * 単なる数値インデックスとの混同を防ぎ、型安全に4隅の選択・移動操作を扱います。
 */
enum class Corner(val keyIndex: Int, val label: String) {
    TOP_LEFT(0, "0:左上"),
    TOP_RIGHT(4, "4:右上"),
    BOTTOM_LEFT(10, "10:左下"),
    BOTTOM_RIGHT(14, "14:右下");

    companion object {
        fun fromKeyIndex(keyIndex: Int): Corner? = entries.firstOrNull { it.keyIndex == keyIndex }
    }
}
