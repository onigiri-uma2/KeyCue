package com.onigiri.keycue.model

/**
 * 15キーのダイアトニック音階を生成するためのスケール種別。
 *
 * @param displayName UI表示名
 * @param intervals ルート音からの半音インターバル（15要素）
 */
enum class ScaleType(
    val displayName: String,
    val intervals: List<Int>
) {
    MAJOR(
        displayName = "Major",
        intervals = listOf(
            0, 2, 4, 5, 7, 9, 11,
            12, 14, 16, 17, 19, 21, 23, 24
        )
    ),
    NATURAL_MINOR(
        displayName = "Natural Minor",
        intervals = listOf(
            0, 2, 3, 5, 7, 8, 10,
            12, 14, 15, 17, 19, 20, 22, 24
        )
    )
}
