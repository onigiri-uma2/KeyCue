package com.onigiri.keycue.model

import com.onigiri.keycue.profile.GameProfileRegistry

/**
 * キー配置情報を保持する不変データモデル。
 *
 * 画面上の各キー中心位置（0.0〜1.0の正規化座標）および基準半径比率を保持し、
 * 異なる解像度やアスペクト比の端末間で一貫した演奏ガイド表示・当たり判定基準を提供します。
 *
 * @param keyCenters 各キーそれぞれの正規化中心座標（空であってはならない）
 * @param keyRadiusRatio 画面短辺に対するキー円の基準半径比率（デフォルト: 0.04f）
 * @param landscape 想定される画面向きが横向き（Landscape）かどうか
 */
data class FitProfile(
    val keyCenters: List<NormalizedPoint>,
    val keyRadiusRatio: Float = 0.04f,
    val landscape: Boolean = true
) {
    init {
        require(keyCenters.isNotEmpty()) {
            "FitProfile must not have empty keyCenters"
        }
        require(keyRadiusRatio > 0f) {
            "keyRadiusRatio must be positive, but was $keyRadiusRatio"
        }
    }

    companion object {

        /**
         * 開発・テストおよび初期状態用の標準的な格子FitProfileを生成する。
         *
         * 基準座標および半径比率は [com.onigiri.keycue.profile.SkyProfile] の定義を参照します。
         *
         * @param landscape 画面向き
         * @return 初期テスト用FitProfile
         */
        fun createDefaultTestProfile(landscape: Boolean = true): FitProfile {
            val profile = GameProfileRegistry.current
            return FitProfile(
                keyCenters = profile.baseKeyCenters.toList(),
                keyRadiusRatio = profile.baseKeyRadiusRatio,
                landscape = landscape
            )
        }
    }
}
