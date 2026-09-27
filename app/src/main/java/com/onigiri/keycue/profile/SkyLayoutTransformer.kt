package com.onigiri.keycue.profile

import com.onigiri.keycue.model.FitProfile
import com.onigiri.keycue.model.NormalizedPoint

/**
 * 基準フィッティング ([com.onigiri.keycue.profile.SkyLayout.TOUCH_STANDARD]) から
 * 任意のレイアウトの描画用 [FitProfile] (activeFitProfile) を生成する座標変換エンジン。
 *
 * 【設計原則】
 * 1. **基準フィッティング不変性**:
 *    [SettingsRepository.fitProfile] (baseFitProfile) を唯一のアンカーとし、
 *    他レイアウト選択時は必ず baseFitProfile から直接再計算します。
 *    派生プロファイルから別レイアウトへの連続変換を行わないため、切り替えを繰り返しても誤差が一切蓄積しません。
 * 2. **バイリニア補間および外挿**:
 *    基準画像における TOUCH_STANDARD の4隅アンカー（Key 0, 4, 10, 14）に対する相対パラメータ (s, t) を算出し、
 *    ユーザーの実機画面上の4隅座標に対してバイリニア変換を適用します。
 *    PAD系などの格子外キー（s < 0, s > 1, t < 0, t > 1）も滑らかに外挿されます。
 * 3. **内部計算時の非Clamp**:
 *    中間変換計算中は 0.0〜1.0 への丸めを行わず、高精度の浮動小数点数演算を維持します。
 * 4. **レイアウト別微調整 (Adjustment)**:
 *    対象レイアウト全体の幾何中心を基準として、scaleX, scaleY による拡大縮小および
 *    offsetX, offsetY による並進移動を適用します。
 */
object SkyLayoutTransformer {

    private val stdTopLeft = SkyLayoutRegistry.STANDARD_TOP_LEFT
    private val stdTopRight = SkyLayoutRegistry.STANDARD_TOP_RIGHT
    private val stdBottomLeft = SkyLayoutRegistry.STANDARD_BOTTOM_LEFT

    private val deltaU = stdTopRight.x - stdTopLeft.x
    private val deltaV = stdBottomLeft.y - stdTopLeft.y

    /**
     * 基準 [baseFitProfile] から指定レイアウト [targetLayout] の描画用 [FitProfile] を生成する。
     *
     * @param baseFitProfile TOUCH_STANDARD で位置合わせされた基準プロファイル
     * @param targetLayout 変換先のレイアウト
     * @param adjustment レイアウト固有の位置・スケール微調整値
     * @return 変換後の描画用 [FitProfile] (activeFitProfile)
     */
    fun transform(
        baseFitProfile: FitProfile,
        targetLayout: SkyLayout,
        adjustment: SkyLayoutAdjustment = SkyLayoutAdjustment.DEFAULT
    ): FitProfile {
        require(baseFitProfile.keyCenters.size == 15) {
            "baseFitProfile must contain exactly 15 key centers, but got ${baseFitProfile.keyCenters.size}"
        }

        // TOUCH_STANDARD かつ 微調整なし の場合は、誤差ゼロで基準プロファイルをそのまま返す
        if (targetLayout == SkyLayout.TOUCH_STANDARD && adjustment.isDefault) {
            return baseFitProfile
        }

        // 基準プロファイルの4隅アンカー
        val pTL = baseFitProfile.keyCenters[0]   // Key 0: 左上
        val pTR = baseFitProfile.keyCenters[4]   // Key 4: 右上
        val pBL = baseFitProfile.keyCenters[10]  // Key 10: 左下
        val pBR = baseFitProfile.keyCenters[14]  // Key 14: 右下

        val preset = SkyLayoutRegistry.getPreset(targetLayout)

        // 1. 各キーのバイリニア補間・外挿座標の算出
        val rawPoints = ArrayList<Pair<Float, Float>>(15)
        for (norm in preset.keyCenters) {
            val s = (norm.x - stdTopLeft.x) / deltaU
            val t = (norm.y - stdTopLeft.y) / deltaV

            val x = (1.0f - t) * ((1.0f - s) * pTL.x + s * pTR.x) + t * ((1.0f - s) * pBL.x + s * pBR.x)
            val y = (1.0f - s) * ((1.0f - t) * pTL.y + t * pBL.y) + s * ((1.0f - t) * pTR.y + t * pBR.y)
            rawPoints.add(Pair(x, y))
        }

        // 2. レイアウト幾何中心の算出
        var sumX = 0.0f
        var sumY = 0.0f
        for (pt in rawPoints) {
            sumX += pt.first
            sumY += pt.second
        }
        val centerX = sumX / 15.0f
        val centerY = sumY / 15.0f

        // 3. 微調整 (scale & offset) の適用と最終的な NormalizedPoint 生成
        val safeAdj = SkyLayoutAdjustment.safe(
            offsetX = adjustment.offsetX,
            offsetY = adjustment.offsetY,
            scaleX = adjustment.scaleX,
            scaleY = adjustment.scaleY
        )

        val finalCenters = rawPoints.map { pt ->
            val scaledX = centerX + (pt.first - centerX) * safeAdj.scaleX + safeAdj.offsetX
            val scaledY = centerY + (pt.second - centerY) * safeAdj.scaleY + safeAdj.offsetY
            NormalizedPoint(
                x = scaledX.coerceIn(0.0f, 1.0f),
                y = scaledY.coerceIn(0.0f, 1.0f)
            )
        }

        return FitProfile(
            keyCenters = finalCenters,
            keyRadiusRatio = baseFitProfile.keyRadiusRatio,
            landscape = baseFitProfile.landscape
        )
    }
}
