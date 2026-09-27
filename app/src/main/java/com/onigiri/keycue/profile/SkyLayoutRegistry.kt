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

    /** 基準画像における TOUCH_STANDARD の 4 隅アンカー正規化座標 (実測: 320.5px, 104.0px, 701.5px, 288.0px) */
    val STANDARD_TOP_LEFT = NormalizedPoint(0.312988f, 0.226087f)     // Key 0
    val STANDARD_TOP_RIGHT = NormalizedPoint(0.685059f, 0.226087f)    // Key 4
    val STANDARD_BOTTOM_LEFT = NormalizedPoint(0.312988f, 0.626087f)  // Key 10
    val STANDARD_BOTTOM_RIGHT = NormalizedPoint(0.685059f, 0.626087f) // Key 14

    private val presets: Map<SkyLayout, SkyLayoutPreset> = mapOf(
        SkyLayout.TOUCH_STANDARD to SkyLayoutPreset(
            layout = SkyLayout.TOUCH_STANDARD,
            keyCenters = listOf(
                // 上段 (Key 0..4: y=104.0px)
                NormalizedPoint(0.312988f, 0.226087f),
                NormalizedPoint(0.406250f, 0.226087f),
                NormalizedPoint(0.499023f, 0.226087f),
                NormalizedPoint(0.592285f, 0.226087f),
                NormalizedPoint(0.685059f, 0.226087f),
                // 中段 (Key 5..9: y=196.0px)
                NormalizedPoint(0.312988f, 0.426087f),
                NormalizedPoint(0.406250f, 0.426087f),
                NormalizedPoint(0.499023f, 0.426087f),
                NormalizedPoint(0.592285f, 0.426087f),
                NormalizedPoint(0.685059f, 0.426087f),
                // 下段 (Key 10..14: y=288.0px)
                NormalizedPoint(0.312988f, 0.626087f),
                NormalizedPoint(0.406250f, 0.626087f),
                NormalizedPoint(0.499023f, 0.626087f),
                NormalizedPoint(0.592285f, 0.626087f),
                NormalizedPoint(0.685059f, 0.626087f)
            )
        ),

        SkyLayout.TOUCH_EXPANDED to SkyLayoutPreset(
            layout = SkyLayout.TOUCH_EXPANDED,
            keyCenters = listOf(
                // 上段 (Key 0..4: y=115.5px)
                NormalizedPoint(0.266602f, 0.251087f),
                NormalizedPoint(0.383301f, 0.251087f),
                NormalizedPoint(0.499023f, 0.251087f),
                NormalizedPoint(0.616211f, 0.251087f),
                NormalizedPoint(0.731934f, 0.251087f),
                // 中段 (Key 5..9: y=230.5px)
                NormalizedPoint(0.266602f, 0.501087f),
                NormalizedPoint(0.383301f, 0.501087f),
                NormalizedPoint(0.499023f, 0.501087f),
                NormalizedPoint(0.616211f, 0.501087f),
                NormalizedPoint(0.731934f, 0.501087f),
                // 下段 (Key 10..14: y=345.5px)
                NormalizedPoint(0.266602f, 0.751087f),
                NormalizedPoint(0.383301f, 0.751087f),
                NormalizedPoint(0.499023f, 0.751087f),
                NormalizedPoint(0.616211f, 0.751087f),
                NormalizedPoint(0.731934f, 0.751087f)
            )
        ),

        SkyLayout.PAD_TRIGGER_FIRST to SkyLayoutPreset(
            layout = SkyLayout.PAD_TRIGGER_FIRST,
            keyCenters = listOf(
                NormalizedPoint(0.411136f, 0.819672f), // Key  0: LT (1115.0, 1000.0)
                NormalizedPoint(0.584440f, 0.819672f), // Key  1: RT (1585.0, 1000.0)
                NormalizedPoint(0.259277f, 0.714130f), // Key  2: D-Pad ↓ (265.5, 328.5)
                NormalizedPoint(0.734863f, 0.714130f), // Key  3: A (752.5, 328.5)
                NormalizedPoint(0.159668f, 0.606522f), // Key  4: D-Pad ← (163.5, 279.0)
                NormalizedPoint(0.634766f, 0.607609f), // Key  5: X (650.0, 279.5)
                NormalizedPoint(0.259277f, 0.500000f), // Key  6: D-Pad ↑ (265.5, 230.0)
                NormalizedPoint(0.734863f, 0.500000f), // Key  7: Y (752.5, 230.0)
                NormalizedPoint(0.359375f, 0.607609f), // Key  8: D-Pad → (368.0, 279.5)
                NormalizedPoint(0.834961f, 0.607609f), // Key  9: B (855.0, 279.5)
                NormalizedPoint(0.409180f, 0.394565f), // Key 10: LB (419.0, 181.5)
                NormalizedPoint(0.583984f, 0.394565f), // Key 11: RB (598.0, 181.5)
                NormalizedPoint(0.259277f, 0.288043f), // Key 12: 左Stick ← (265.5, 132.5)
                NormalizedPoint(0.734863f, 0.288043f), // Key 13: 右Stick ← (752.5, 132.5)
                NormalizedPoint(0.410029f, 0.181967f)  // Key 14: 左Stick → (1112.0, 222.0)
            )
        ),

        SkyLayout.PAD_DPAD_FIRST to SkyLayoutPreset(
            layout = SkyLayout.PAD_DPAD_FIRST,
            keyCenters = listOf(
                NormalizedPoint(0.396484f, 0.841304f), // Key  0: D-Pad ↓ (406.0, 387.0)
                NormalizedPoint(0.297566f, 0.733607f), // Key  1: D-Pad ← (807.0, 895.0)
                NormalizedPoint(0.396973f, 0.627174f), // Key  2: D-Pad ↑ (406.5, 288.5)
                NormalizedPoint(0.297566f, 0.522131f), // Key  3: 左Stick ↓ (807.0, 637.0)
                NormalizedPoint(0.196777f, 0.415217f), // Key  4: 左Stick ← (201.5, 191.0)
                NormalizedPoint(0.196777f, 0.204348f), // Key  5: LB (201.5,  94.0)
                NormalizedPoint(0.298673f, 0.096721f), // Key  6: LT (810.0, 118.0)
                NormalizedPoint(0.596680f, 0.841304f), // Key  7: 右Stick ↓ (611.0, 387.0)
                NormalizedPoint(0.697640f, 0.734426f), // Key  8: 右Stick → (1892.0, 896.0)
                NormalizedPoint(0.597168f, 0.627174f), // Key  9: 右Stick ↑ (611.5, 288.5)
                NormalizedPoint(0.697640f, 0.522951f), // Key 10: A (1892.0, 638.0)
                NormalizedPoint(0.797363f, 0.415217f), // Key 11: B (816.5, 191.0)
                NormalizedPoint(0.697640f, 0.309836f), // Key 12: Y (1892.0, 378.0)
                NormalizedPoint(0.797363f, 0.204348f), // Key 13: RB (816.5,  94.0)
                NormalizedPoint(0.696534f, 0.097541f)  // Key 14: RT (1889.0, 119.0)
            )
        ),

        SkyLayout.PAD_GRID_STANDARD to SkyLayoutPreset(
            layout = SkyLayout.PAD_GRID_STANDARD,
            keyCenters = listOf(
                // 上段 (Key 0..4: y=104.0px)
                NormalizedPoint(0.312988f, 0.226087f), // Key  0: LT
                NormalizedPoint(0.406250f, 0.226087f), // Key  1: RT
                NormalizedPoint(0.499023f, 0.226087f), // Key  2: D-Pad ↓
                NormalizedPoint(0.592285f, 0.226087f), // Key  3: A
                NormalizedPoint(0.685059f, 0.226087f), // Key  4: D-Pad ←
                // 中段 (Key 5..9: y=196.0px)
                NormalizedPoint(0.312988f, 0.426087f), // Key  5: X
                NormalizedPoint(0.406250f, 0.426087f), // Key  6: D-Pad ↑
                NormalizedPoint(0.499023f, 0.426087f), // Key  7: Y
                NormalizedPoint(0.592285f, 0.426087f), // Key  8: D-Pad →
                NormalizedPoint(0.685059f, 0.426087f), // Key  9: B
                // 下段 (Key 10..14: y=288.0px)
                NormalizedPoint(0.312988f, 0.626087f), // Key 10: LB
                NormalizedPoint(0.406250f, 0.626087f), // Key 11: RB
                NormalizedPoint(0.499023f, 0.626087f), // Key 12: 左Stick ←
                NormalizedPoint(0.592285f, 0.626087f), // Key 13: 右Stick ←
                NormalizedPoint(0.685059f, 0.626087f)  // Key 14: 左Stick →
            )
        ),

        SkyLayout.PAD_GRID_EXPANDED to SkyLayoutPreset(
            layout = SkyLayout.PAD_GRID_EXPANDED,
            keyCenters = listOf(
                // 上段 (Key 0..4: y=115.5px)
                NormalizedPoint(0.266602f, 0.251087f), // Key  0: LT
                NormalizedPoint(0.383301f, 0.251087f), // Key  1: RT
                NormalizedPoint(0.499023f, 0.251087f), // Key  2: D-Pad ↓
                NormalizedPoint(0.616211f, 0.251087f), // Key  3: A
                NormalizedPoint(0.731934f, 0.251087f), // Key  4: D-Pad ←
                // 中段 (Key 5..9: y=230.5px)
                NormalizedPoint(0.266602f, 0.501087f), // Key  5: X
                NormalizedPoint(0.383301f, 0.501087f), // Key  6: D-Pad ↑
                NormalizedPoint(0.499023f, 0.501087f), // Key  7: Y
                NormalizedPoint(0.616211f, 0.501087f), // Key  8: D-Pad →
                NormalizedPoint(0.731934f, 0.501087f), // Key  9: B
                // 下段 (Key 10..14: y=345.5px)
                NormalizedPoint(0.266602f, 0.751087f), // Key 10: LB
                NormalizedPoint(0.383301f, 0.751087f), // Key 11: RB
                NormalizedPoint(0.499023f, 0.751087f), // Key 12: 左Stick ←
                NormalizedPoint(0.616211f, 0.751087f), // Key 13: 右Stick ←
                NormalizedPoint(0.731934f, 0.751087f)  // Key 14: 左Stick →
            )
        )
    )

    /**
     * レイアウト固有の開発側既定キャリブレーション補正値。
     * 実機画像実測に基づき、外挿キーおよび拡大グリッドにおける微小な内側寄りを解消するための
     * 必要最小限の外側微拡大 (scaleX = 1.003f: +0.3%) を提供します。
     */
    private val tunings: Map<SkyLayout, SkyLayoutTuning> = mapOf(
        SkyLayout.TOUCH_STANDARD to SkyLayoutTuning.DEFAULT,
        SkyLayout.TOUCH_EXPANDED to SkyLayoutTuning(scaleX = 1.003f, scaleY = 1.000f),
        SkyLayout.PAD_TRIGGER_FIRST to SkyLayoutTuning(scaleX = 1.003f, scaleY = 1.000f),
        SkyLayout.PAD_DPAD_FIRST to SkyLayoutTuning(scaleX = 1.003f, scaleY = 1.000f),
        SkyLayout.PAD_GRID_STANDARD to SkyLayoutTuning(scaleX = 1.000f, scaleY = 1.000f),
        SkyLayout.PAD_GRID_EXPANDED to SkyLayoutTuning(scaleX = 1.003f, scaleY = 1.000f)
    )

    /**
     * 指定されたレイアウトの開発側既定キャリブレーション補正を取得する。
     */
    fun getTuning(layout: SkyLayout): SkyLayoutTuning {
        return tunings[layout] ?: SkyLayoutTuning.DEFAULT
    }

    /**
     * 指定されたレイアウトのプリセットを取得する。
     */
    fun getPreset(layout: SkyLayout): SkyLayoutPreset {
        return presets[layout] ?: presets.getValue(SkyLayout.TOUCH_STANDARD)
    }
}
