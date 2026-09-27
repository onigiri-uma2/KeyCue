package com.onigiri.keycue.profile

import com.onigiri.keycue.model.NormalizedPoint

/**
 * Sky の 6 種類の演奏ボタンレイアウトの固定座標プリセットを管理するレジストリ。
 *
 * 基準スクリーンショット（1024×460）から
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
                NormalizedPoint(0.408203f, 0.817391f), // Key  0: LT
                NormalizedPoint(0.583984f, 0.819565f), // Key  1: RT
                NormalizedPoint(0.259766f, 0.716304f), // Key  2: D-Pad ↓
                NormalizedPoint(0.735352f, 0.715217f), // Key  3: A
                NormalizedPoint(0.156250f, 0.606522f), // Key  4: D-Pad ←
                NormalizedPoint(0.634766f, 0.606522f), // Key  5: X
                NormalizedPoint(0.258789f, 0.491304f), // Key  6: D-Pad ↑
                NormalizedPoint(0.735352f, 0.500000f), // Key  7: Y
                NormalizedPoint(0.364258f, 0.608696f), // Key  8: D-Pad →
                NormalizedPoint(0.834961f, 0.606522f), // Key  9: B
                NormalizedPoint(0.410156f, 0.394565f), // Key 10: LB
                NormalizedPoint(0.583984f, 0.395652f), // Key 11: RB
                NormalizedPoint(0.259766f, 0.289130f), // Key 12: 左Stick ←
                NormalizedPoint(0.736328f, 0.289130f), // Key 13: 右Stick ←
                NormalizedPoint(0.410156f, 0.176087f)  // Key 14: 左Stick →
            )
        ),

        SkyLayout.PAD_DPAD_FIRST to SkyLayoutPreset(
            layout = SkyLayout.PAD_DPAD_FIRST,
            keyCenters = listOf(
                NormalizedPoint(0.395508f, 0.845652f), // Key  0: D-Pad ↓
                NormalizedPoint(0.293945f, 0.732609f), // Key  1: D-Pad ←
                NormalizedPoint(0.397461f, 0.628261f), // Key  2: D-Pad ↑
                NormalizedPoint(0.296875f, 0.523913f), // Key  3: 左Stick ↓
                NormalizedPoint(0.197266f, 0.415217f), // Key  4: 左Stick ←
                NormalizedPoint(0.197266f, 0.202174f), // Key  5: LB
                NormalizedPoint(0.297852f, 0.095652f), // Key  6: LT
                NormalizedPoint(0.597656f, 0.841304f), // Key  7: 右Stick ↓
                NormalizedPoint(0.698242f, 0.736957f), // Key  8: 右Stick →
                NormalizedPoint(0.597656f, 0.628261f), // Key  9: 右Stick ↑
                NormalizedPoint(0.697266f, 0.521739f), // Key 10: A
                NormalizedPoint(0.797852f, 0.417391f), // Key 11: B
                NormalizedPoint(0.697266f, 0.308696f), // Key 12: Y
                NormalizedPoint(0.797852f, 0.204348f), // Key 13: RB
                NormalizedPoint(0.695312f, 0.095652f)  // Key 14: RT
            )
        ),

        SkyLayout.PAD_GRID_STANDARD to SkyLayoutPreset(
            layout = SkyLayout.PAD_GRID_STANDARD,
            keyCenters = listOf(
                // 上段: LT, RT, ↓, A, ←
                NormalizedPoint(0.312500f, 0.226087f), // Key  0: LT
                NormalizedPoint(0.406250f, 0.226087f), // Key  1: RT
                NormalizedPoint(0.500000f, 0.227174f), // Key  2: D-Pad ↓
                NormalizedPoint(0.592773f, 0.226087f), // Key  3: A
                NormalizedPoint(0.685547f, 0.227174f), // Key  4: D-Pad ←
                // 中段: X, ↑, Y, →, B
                NormalizedPoint(0.313477f, 0.426087f), // Key  5: X
                NormalizedPoint(0.406250f, 0.426087f), // Key  6: D-Pad ↑
                NormalizedPoint(0.499023f, 0.426087f), // Key  7: Y
                NormalizedPoint(0.592773f, 0.426087f), // Key  8: D-Pad →
                NormalizedPoint(0.685547f, 0.426087f), // Key  9: B
                // 下段: LB, RB, 左Stick←, 右Stick←, 左Stick→
                NormalizedPoint(0.313477f, 0.626087f), // Key 10: LB
                NormalizedPoint(0.406250f, 0.626087f), // Key 11: RB
                NormalizedPoint(0.500000f, 0.626087f), // Key 12: 左Stick ←
                NormalizedPoint(0.592773f, 0.626087f), // Key 13: 右Stick ←
                NormalizedPoint(0.684570f, 0.626087f)  // Key 14: 左Stick →
            )
        ),

        SkyLayout.PAD_GRID_EXPANDED to SkyLayoutPreset(
            layout = SkyLayout.PAD_GRID_EXPANDED,
            keyCenters = listOf(
                // 上段: LT, RT, ↓, A, ←
                NormalizedPoint(0.266602f, 0.250000f), // Key  0: LT
                NormalizedPoint(0.382812f, 0.250000f), // Key  1: RT
                NormalizedPoint(0.499023f, 0.250000f), // Key  2: D-Pad ↓
                NormalizedPoint(0.616211f, 0.250000f), // Key  3: A
                NormalizedPoint(0.731445f, 0.250000f), // Key  4: D-Pad ←
                // 中段: X, ↑, Y, →, B
                NormalizedPoint(0.266602f, 0.500000f), // Key  5: X
                NormalizedPoint(0.382812f, 0.500000f), // Key  6: D-Pad ↑
                NormalizedPoint(0.499023f, 0.500000f), // Key  7: Y
                NormalizedPoint(0.616211f, 0.500000f), // Key  8: D-Pad →
                NormalizedPoint(0.731445f, 0.500000f), // Key  9: B
                // 下段: LB, RB, 左Stick←, 右Stick←, 左Stick→
                NormalizedPoint(0.266602f, 0.750000f), // Key 10: LB
                NormalizedPoint(0.382812f, 0.750000f), // Key 11: RB
                NormalizedPoint(0.499023f, 0.750000f), // Key 12: 左Stick ←
                NormalizedPoint(0.616211f, 0.750000f), // Key 13: 右Stick ←
                NormalizedPoint(0.731445f, 0.750000f)  // Key 14: 左Stick →
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
