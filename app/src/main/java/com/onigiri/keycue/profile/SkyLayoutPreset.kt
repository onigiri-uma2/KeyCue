package com.onigiri.keycue.profile

import com.onigiri.keycue.model.NormalizedPoint

/**
 * 基準画像（1024x460）に対する各レイアウトの15キー正規化中心座標プリセット。
 *
 * @param layout 対応するレイアウト種別
 * @param keyCenters 論理キー番号 0〜14 に対応する正規化中心座標リスト（要素数は必ず15）
 */
data class SkyLayoutPreset(
    val layout: SkyLayout,
    val keyCenters: List<NormalizedPoint>
) {
    init {
        require(keyCenters.size == 15) {
            "SkyLayoutPreset must contain exactly 15 key centers, but got ${keyCenters.size}"
        }
    }
}
