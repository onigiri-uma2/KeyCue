package com.onigiri.keycue.model

/**
 * 15キーの配置情報を保持する不変データモデル。
 *
 * 画面上の各キー中心位置（0.0〜1.0の正規化座標）および基準半径比率を保持し、
 * 異なる解像度やアスペクト比の端末間で一貫した演奏ガイド表示・当たり判定基準を提供します。
 *
 * @param keyCenters 15キーそれぞれの正規化中心座標（要素数は必ず15）
 * @param keyRadiusRatio 画面短辺に対するキー円の基準半径比率（デフォルト: 0.04f）
 * @param landscape 想定される画面向きが横向き（Landscape）かどうか
 */
data class FitProfile(
    val keyCenters: List<NormalizedPoint>,
    val keyRadiusRatio: Float = 0.04f,
    val landscape: Boolean = true
) {
    init {
        require(keyCenters.size == KEY_COUNT) {
            "FitProfile must contain exactly $KEY_COUNT key centers, but got ${keyCenters.size}"
        }
        require(keyRadiusRatio > 0f) {
            "keyRadiusRatio must be positive, but was $keyRadiusRatio"
        }
    }

    companion object {
        const val KEY_COUNT = 15

        /**
         * 開発・テストおよび初期状態用の標準的な5列×3段格子FitProfileを生成する。
         *
         * 横方向比率 (X): 20%, 35%, 50%, 65%, 80%
         * 縦方向比率 (Y): 65%, 75%, 85%
         *
         * 内部キー番号の配置:
         * 0  1  2  3  4  (Row 0: 上段)
         * 5  6  7  8  9  (Row 1: 中段)
         * 10 11 12 13 14 (Row 2: 下段)
         *
         * @param landscape 画面向き
         * @return 15キーの初期テスト用FitProfile
         */
        fun createDefaultTestProfile(landscape: Boolean = true): FitProfile {
            val colXs = floatArrayOf(0.20f, 0.35f, 0.50f, 0.65f, 0.80f)
            val rowYs = floatArrayOf(0.65f, 0.75f, 0.85f)

            val centers = ArrayList<NormalizedPoint>(KEY_COUNT)
            for (y in rowYs) {
                for (x in colXs) {
                    centers.add(NormalizedPoint(x = x, y = y))
                }
            }

            return FitProfile(
                keyCenters = centers,
                keyRadiusRatio = 0.04f,
                landscape = landscape
            )
        }
    }
}
