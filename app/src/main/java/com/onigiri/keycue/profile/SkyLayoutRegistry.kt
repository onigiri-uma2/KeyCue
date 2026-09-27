package com.onigiri.keycue.profile

import com.onigiri.keycue.model.NormalizedPoint

/**
 * Sky の 5 種類の演奏ボタンレイアウトの固定座標プリセットを管理するレジストリ。
 *
 * 基準スクリーンショット（sk1, sk2, pad1, pad2, pad3: 1024×460）から
 * 高精度に測定・正規化されたキー中心座標（0.0f〜1.0f）を提供します。
 * 全レイアウトで論理キー番号 0〜14 の音階順（低音→高音）と1対1で対応付けられています。
 */
object SkyLayoutRegistry {

    /** 基準画像における TOUCH_STANDARD の 4 隅アンカー正規化座標 */
    val STANDARD_TOP_LEFT = NormalizedPoint(0.313086f, 0.226957f)     // Key 0
    val STANDARD_TOP_RIGHT = NormalizedPoint(0.685156f, 0.226957f)    // Key 4
    val STANDARD_BOTTOM_LEFT = NormalizedPoint(0.313086f, 0.626087f)  // Key 10
    val STANDARD_BOTTOM_RIGHT = NormalizedPoint(0.685156f, 0.626087f) // Key 14

    private val presets: Map<SkyLayout, SkyLayoutPreset> = mapOf(
        SkyLayout.TOUCH_STANDARD to SkyLayoutPreset(
            layout = SkyLayout.TOUCH_STANDARD,
            keyCenters = listOf(
                // 上段 (Key 0..4)
                NormalizedPoint(0.313086f, 0.226957f),
                NormalizedPoint(0.406641f, 0.226957f),
                NormalizedPoint(0.499805f, 0.226957f),
                NormalizedPoint(0.593555f, 0.226957f),
                NormalizedPoint(0.685156f, 0.226957f),
                // 中段 (Key 5..9)
                NormalizedPoint(0.313086f, 0.426522f),
                NormalizedPoint(0.406641f, 0.426522f),
                NormalizedPoint(0.499805f, 0.426522f),
                NormalizedPoint(0.593555f, 0.426522f),
                NormalizedPoint(0.685156f, 0.426522f),
                // 下段 (Key 10..14)
                NormalizedPoint(0.313086f, 0.626087f),
                NormalizedPoint(0.406641f, 0.626087f),
                NormalizedPoint(0.499805f, 0.626087f),
                NormalizedPoint(0.593555f, 0.626087f),
                NormalizedPoint(0.685156f, 0.626087f)
            )
        ),

        SkyLayout.TOUCH_EXPANDED to SkyLayoutPreset(
            layout = SkyLayout.TOUCH_EXPANDED,
            keyCenters = listOf(
                // 上段 (Key 0..4)
                NormalizedPoint(0.267090f, 0.251087f),
                NormalizedPoint(0.383301f, 0.251087f),
                NormalizedPoint(0.499512f, 0.251087f),
                NormalizedPoint(0.616699f, 0.251087f),
                NormalizedPoint(0.731934f, 0.251087f),
                // 中段 (Key 5..9)
                NormalizedPoint(0.267090f, 0.501087f),
                NormalizedPoint(0.383301f, 0.501087f),
                NormalizedPoint(0.499512f, 0.501087f),
                NormalizedPoint(0.616699f, 0.501087f),
                NormalizedPoint(0.731934f, 0.501087f),
                // 下段 (Key 10..14)
                NormalizedPoint(0.267090f, 0.751087f),
                NormalizedPoint(0.383301f, 0.751087f),
                NormalizedPoint(0.499512f, 0.751087f),
                NormalizedPoint(0.616699f, 0.751087f),
                NormalizedPoint(0.731934f, 0.751087f)
            )
        ),

        SkyLayout.PAD_TRIGGER_FIRST to SkyLayoutPreset(
            layout = SkyLayout.PAD_TRIGGER_FIRST,
            keyCenters = listOf(
                NormalizedPoint(0.406641f, 0.819783f), // Key 0: LT
                NormalizedPoint(0.585742f, 0.819783f), // Key 1: RT
                NormalizedPoint(0.259668f, 0.722609f), // Key 2: D-Pad ↓
                NormalizedPoint(0.735059f, 0.714783f), // Key 3: A
                NormalizedPoint(0.156445f, 0.605870f), // Key 4: D-Pad ←
                NormalizedPoint(0.634961f, 0.608478f), // Key 5: X
                NormalizedPoint(0.259277f, 0.489565f), // Key 6: D-Pad ↑
                NormalizedPoint(0.734570f, 0.500217f), // Key 7: Y
                NormalizedPoint(0.364844f, 0.609783f), // Key 8: D-Pad →
                NormalizedPoint(0.834766f, 0.608696f), // Key 9: B
                NormalizedPoint(0.409766f, 0.395652f), // Key 10: LB
                NormalizedPoint(0.583496f, 0.395870f), // Key 11: RB
                NormalizedPoint(0.259863f, 0.288696f), // Key 12: 左Stick ←
                NormalizedPoint(0.736133f, 0.289565f), // Key 13: 右Stick ←
                NormalizedPoint(0.409277f, 0.179565f)  // Key 14: 左Stick →
            )
        ),

        SkyLayout.PAD_DPAD_FIRST to SkyLayoutPreset(
            layout = SkyLayout.PAD_DPAD_FIRST,
            keyCenters = listOf(
                NormalizedPoint(0.395508f, 0.845652f), // Key 0: D-Pad ↓
                NormalizedPoint(0.291992f, 0.733696f), // Key 1: D-Pad ←
                NormalizedPoint(0.397461f, 0.621739f), // Key 2: D-Pad ↑
                NormalizedPoint(0.296875f, 0.523913f), // Key 3: 左Stick ↓
                NormalizedPoint(0.197266f, 0.415217f), // Key 4: 左Stick ←
                NormalizedPoint(0.197266f, 0.204348f), // Key 5: LB
                NormalizedPoint(0.297852f, 0.095652f), // Key 6: LT
                NormalizedPoint(0.597656f, 0.841304f), // Key 7: 右Stick ↓
                NormalizedPoint(0.698242f, 0.736957f), // Key 8: 右Stick →
                NormalizedPoint(0.597656f, 0.630435f), // Key 9: 右Stick ↑
                NormalizedPoint(0.697266f, 0.523913f), // Key 10: A
                NormalizedPoint(0.797852f, 0.417391f), // Key 11: B
                NormalizedPoint(0.697266f, 0.310870f), // Key 12: Y
                NormalizedPoint(0.797852f, 0.204348f), // Key 13: RB
                NormalizedPoint(0.695312f, 0.095652f)  // Key 14: RT
            )
        ),

        SkyLayout.PAD_GRID to SkyLayoutPreset(
            layout = SkyLayout.PAD_GRID,
            keyCenters = listOf(
                // 上段: LT, RT, ↓, A, ←
                NormalizedPoint(0.267090f, 0.251087f), // Key 0: LT
                NormalizedPoint(0.383301f, 0.251087f), // Key 1: RT
                NormalizedPoint(0.499512f, 0.251087f), // Key 2: D-Pad ↓
                NormalizedPoint(0.616699f, 0.251087f), // Key 3: A
                NormalizedPoint(0.731934f, 0.251087f), // Key 4: D-Pad ←
                // 中段: X, ↑, Y, →, B
                NormalizedPoint(0.267090f, 0.501087f), // Key 5: X
                NormalizedPoint(0.383301f, 0.501087f), // Key 6: D-Pad ↑
                NormalizedPoint(0.499512f, 0.501087f), // Key 7: Y
                NormalizedPoint(0.616699f, 0.501087f), // Key 8: D-Pad →
                NormalizedPoint(0.731934f, 0.501087f), // Key 9: B
                // 下段: LB, RB, 左Stick←, 右Stick←, 左Stick→
                NormalizedPoint(0.267090f, 0.751087f), // Key 10: LB
                NormalizedPoint(0.383301f, 0.751087f), // Key 11: RB
                NormalizedPoint(0.499512f, 0.751087f), // Key 12: 左Stick ←
                NormalizedPoint(0.616699f, 0.751087f), // Key 13: 右Stick ←
                NormalizedPoint(0.731934f, 0.751087f)  // Key 14: 左Stick →
            )
        )
    )

    /**
     * 指定されたレイアウトのプリセットを取得する。
     */
    fun getPreset(layout: SkyLayout): SkyLayoutPreset {
        return presets[layout] ?: presets.getValue(SkyLayout.TOUCH_STANDARD)
    }
}
