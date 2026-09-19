package com.onigiri.keycue.profile

import com.onigiri.keycue.model.NormalizedPoint

/**
 * 『Sky 星を紡ぐ子どもたち』専用のキーボードプロファイル。
 *
 * 3段×5列 = 15キーの楽器UI仕様、基準座標、4隅アンカー、およびデフォルトMIDIノートを集約します。
 */
object SkyProfile : GameProfile {
    override val keyCount: Int = 15
    override val rowCount: Int = 3
    override val columnCount: Int = 5

    override val topLeftKeyIndex: Int = 0
    override val topRightKeyIndex: Int = 4
    override val bottomLeftKeyIndex: Int = 10
    override val bottomRightKeyIndex: Int = 14

    override val baseKeyCenters: List<NormalizedPoint> = listOf(
        // 上段 (Row 0: Key 0..4)
        NormalizedPoint(0.20f, 0.65f),
        NormalizedPoint(0.35f, 0.65f),
        NormalizedPoint(0.50f, 0.65f),
        NormalizedPoint(0.65f, 0.65f),
        NormalizedPoint(0.80f, 0.65f),

        // 中段 (Row 1: Key 5..9)
        NormalizedPoint(0.20f, 0.75f),
        NormalizedPoint(0.35f, 0.75f),
        NormalizedPoint(0.50f, 0.75f),
        NormalizedPoint(0.65f, 0.75f),
        NormalizedPoint(0.80f, 0.75f),

        // 下段 (Row 2: Key 10..14)
        NormalizedPoint(0.20f, 0.85f),
        NormalizedPoint(0.35f, 0.85f),
        NormalizedPoint(0.50f, 0.85f),
        NormalizedPoint(0.65f, 0.85f),
        NormalizedPoint(0.80f, 0.85f)
    )

    override val baseKeyRadiusRatio: Float = 0.04f

    override fun getRow(keyIndex: Int): Int {
        return (keyIndex.coerceIn(0, keyCount - 1)) / columnCount
    }

    override fun getColumn(keyIndex: Int): Int {
        return (keyIndex.coerceIn(0, keyCount - 1)) % columnCount
    }

    override val defaultMidiNotes: List<Int> = listOf(
        60, 62, 64, 65, 67,
        69, 71, 72, 74, 76,
        77, 79, 81, 83, 84
    )
}
