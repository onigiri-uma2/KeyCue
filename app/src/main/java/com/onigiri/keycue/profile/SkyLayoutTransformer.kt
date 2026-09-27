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
     * 基準 [baseFitProfile] から指定レイアウト [targetLayout] に対する
     * 各キーの幾何変換後の生座標 (x, y) のリスト（15要素）を算出する。
     *
     * 【設計原則・非Clamp】
     * 0.0f〜1.0f への個別 clamp や自動全体縮小・マージン調整は一切行わず、
     * バイリニア補間/外挿および微調整（scale, offset）の幾何計算結果を生の浮動小数点数として返します。
     * これにより、微調整値の事前検証や画面外判定を正確に行うことができます。
     */
    fun computeTransformedRawPoints(
        baseFitProfile: FitProfile,
        targetLayout: SkyLayout,
        adjustment: SkyLayoutAdjustment = SkyLayoutAdjustment.DEFAULT
    ): List<Pair<Float, Float>> {
        require(baseFitProfile.keyCenters.size == 15) {
            "baseFitProfile must contain exactly 15 key centers, but got ${baseFitProfile.keyCenters.size}"
        }

        // TOUCH_STANDARD および PAD_GRID_STANDARD は標準15キー配置であるため、
        // 4隅からバイリニア再構成するのではなく、baseFitProfile の15キー座標を直接利用する。
        // これにより、中央キー等の個別位置情報が損なわれず100%保持される。
        // その後、各レイアウト固有のユーザー微調整 (adjustment) を適用する。
        if (targetLayout == SkyLayout.TOUCH_STANDARD || targetLayout == SkyLayout.PAD_GRID_STANDARD) {
            if (adjustment.isDefault) {
                return baseFitProfile.keyCenters.map { Pair(it.x, it.y) }
            }
            var sumX = 0.0f
            var sumY = 0.0f
            for (pt in baseFitProfile.keyCenters) {
                sumX += pt.x
                sumY += pt.y
            }
            val centerX = sumX / 15.0f
            val centerY = sumY / 15.0f

            val safeAdj = SkyLayoutAdjustment.safe(
                offsetX = adjustment.offsetX,
                offsetY = adjustment.offsetY,
                scaleX = adjustment.scaleX,
                scaleY = adjustment.scaleY
            )

            return baseFitProfile.keyCenters.map { pt ->
                val scaledX = centerX + (pt.x - centerX) * safeAdj.scaleX + safeAdj.offsetX
                val scaledY = centerY + (pt.y - centerY) * safeAdj.scaleY + safeAdj.offsetY
                Pair(scaledX, scaledY)
            }
        }

        // 基準プロファイルの4隅アンカー
        val pTL = baseFitProfile.keyCenters[0]   // Key 0: 左上
        val pTR = baseFitProfile.keyCenters[4]   // Key 4: 右上
        val pBL = baseFitProfile.keyCenters[10]  // Key 10: 左下
        val pBR = baseFitProfile.keyCenters[14]  // Key 14: 右下

        val preset = SkyLayoutRegistry.getPreset(targetLayout)
        val tuning = SkyLayoutRegistry.getTuning(targetLayout)

        // プリセットの幾何中心（開発側既定補正 tuning のスケーリング基準）
        var pSumX = 0.0f
        var pSumY = 0.0f
        for (pt in preset.keyCenters) {
            pSumX += pt.x
            pSumY += pt.y
        }
        val pCenterX = pSumX / 15.0f
        val pCenterY = pSumY / 15.0f

        // 1. 各キーの開発側既定補正適用およびバイリニア補間・外挿座標の算出
        val rawPoints = ArrayList<Pair<Float, Float>>(15)
        for (norm in preset.keyCenters) {
            // 開発側既定キャリブレーション補正 (外側微拡大 scaleX, scaleY および offset) の適用
            val tunedNormX = pCenterX + (norm.x - pCenterX) * tuning.scaleX + tuning.offsetX
            val tunedNormY = pCenterY + (norm.y - pCenterY) * tuning.scaleY + tuning.offsetY

            val s = (tunedNormX - stdTopLeft.x) / deltaU
            val t = (tunedNormY - stdTopLeft.y) / deltaV

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

        // 3. 微調整 (scale & offset) の適用
        val safeAdj = SkyLayoutAdjustment.safe(
            offsetX = adjustment.offsetX,
            offsetY = adjustment.offsetY,
            scaleX = adjustment.scaleX,
            scaleY = adjustment.scaleY
        )

        return rawPoints.map { pt ->
            val scaledX = centerX + (pt.first - centerX) * safeAdj.scaleX + safeAdj.offsetX
            val scaledY = centerY + (pt.second - centerY) * safeAdj.scaleY + safeAdj.offsetY
            Pair(scaledX, scaledY)
        }
    }

    /**
     * 指定された微調整値 [adjustment] を適用した際、すべてのキーが表示可能範囲 (0.0〜1.0) に
     * 収まっているかを検証する。
     *
     * 画面外に出るキーが存在する場合は false を返し、微調整の更新を採用せず直前の有効値を維持するために使用する。
     * 例外は絶対にスローせず、安全に真偽値を返します。
     */
    fun isAdjustmentValid(
        baseFitProfile: FitProfile,
        targetLayout: SkyLayout,
        adjustment: SkyLayoutAdjustment
    ): Boolean {
        if (baseFitProfile.keyCenters.size != 15) return false
        val rawPoints = computeTransformedRawPoints(baseFitProfile, targetLayout, adjustment)
        return rawPoints.all { (x, y) ->
            x in 0.0f..1.0f && y in 0.0f..1.0f
        }
    }

    /**
     * 基準 [baseFitProfile] から指定レイアウト [targetLayout] の描画用 [FitProfile] を生成する。
     *
     * 【安全性の担保】
     * 画面外のキーが存在する場合、個別clampや全体縮小を勝手に行わず、安全に null を返します。
     * 無効な描画プロファイルを安全なものとして流通させないための設計です。
     *
     * @return 変換後の描画用 [FitProfile]。画面外キーが存在する場合は null
     */
    fun transformOrNull(
        baseFitProfile: FitProfile,
        targetLayout: SkyLayout,
        adjustment: SkyLayoutAdjustment = SkyLayoutAdjustment.DEFAULT
    ): FitProfile? {
        if (baseFitProfile.keyCenters.size != 15) return null
        val rawPoints = computeTransformedRawPoints(baseFitProfile, targetLayout, adjustment)
        val allInBounds = rawPoints.all { (x, y) ->
            x in 0.0f..1.0f && y in 0.0f..1.0f
        }
        if (!allInBounds) {
            return null
        }

        val finalCenters = rawPoints.map { (x, y) ->
            NormalizedPoint(x = x, y = y)
        }

        return FitProfile(
            keyCenters = finalCenters,
            keyRadiusRatio = baseFitProfile.keyRadiusRatio,
            landscape = baseFitProfile.landscape
        )
    }

    /**
     * 基準 [baseFitProfile] から指定レイアウト [targetLayout] の描画用 [FitProfile] を生成する。
     *
     * 画面外のキーが存在する場合は [IllegalStateException] をスローします。
     * 呼び出し元が画面外を許容しない箇所で使用します。
     */
    fun transform(
        baseFitProfile: FitProfile,
        targetLayout: SkyLayout,
        adjustment: SkyLayoutAdjustment = SkyLayoutAdjustment.DEFAULT
    ): FitProfile {
        return transformOrNull(baseFitProfile, targetLayout, adjustment)
            ?: throw IllegalStateException("変換後の座標が表示可能範囲 (0.0〜1.0) を超えています。基準位置合わせを再確認してください。")
    }

    /**
     * [GuideOverlayView] と完全に同一のロジックでキーガイド円の描画ピクセル半径を算出する。
     *
     * @param viewWidth 描画領域の幅 (px)
     * @param viewHeight 描画領域の高さ (px)
     * @param guideRadiusRatio [com.onigiri.keycue.model.VisualConfig.guideRadiusRatio]
     * @param density 画面密度 (DisplayMetrics.density)
     */
    fun calculateKeyRadiusPx(
        viewWidth: Int,
        viewHeight: Int,
        guideRadiusRatio: Float,
        density: Float
    ): Float {
        val baseDimension = kotlin.math.min(viewWidth, viewHeight)
        return (baseDimension * guideRadiusRatio).coerceAtLeast(8.0f * density)
    }

    /**
     * 生座標リストに基づくレイアウト検証。
     *
     * - 画面外の逸脱 (x < 0, x > 1, y < 0, y > 1)
     * - ガイド円同士の重なり警告 (中心間距離 < 2 * 半径)
     * - 致命的な座標変換エラー・衝突 (中心間距離 < 半径)
     */
    fun validateRawPoints(
        rawPoints: List<Pair<Float, Float>>,
        viewWidth: Int,
        viewHeight: Int,
        guideRadiusRatio: Float,
        density: Float = 1.0f
    ): LayoutValidationResult {
        val radiusPx = calculateKeyRadiusPx(viewWidth, viewHeight, guideRadiusRatio, density)
        val pixelCenters = rawPoints.map { (x, y) ->
            Pair(x * viewWidth, y * viewHeight)
        }

        val outOfBoundsIndices = mutableListOf<Int>()
        for (i in rawPoints.indices) {
            val (x, y) = rawPoints[i]
            if (x < 0.0f || x > 1.0f || y < 0.0f || y > 1.0f) {
                outOfBoundsIndices.add(i)
            }
        }

        val overlapPairs = mutableListOf<Pair<Int, Int>>()
        val collisionPairs = mutableListOf<Pair<Int, Int>>()
        val doubleRadius = radiusPx * 2.0f

        for (i in 0 until pixelCenters.size) {
            val (x1, y1) = pixelCenters[i]
            for (j in i + 1 until pixelCenters.size) {
                val (x2, y2) = pixelCenters[j]
                val dx = x1 - x2
                val dy = y1 - y2
                val dist = kotlin.math.sqrt(dx * dx + dy * dy)

                if (dist < radiusPx) {
                    collisionPairs.add(Pair(i, j))
                } else if (dist < doubleRadius) {
                    overlapPairs.add(Pair(i, j))
                }
            }
        }

        val warnings = mutableListOf<String>()
        val errors = mutableListOf<String>()

        if (outOfBoundsIndices.isNotEmpty()) {
            errors.add("画面外に配置されているキーが存在します (キー番号: $outOfBoundsIndices)。基準位置合わせを再確認してください。")
        }
        if (collisionPairs.isNotEmpty()) {
            errors.add("ガイド円の半径未満に接近している重篤なキー重複が存在します: $collisionPairs")
        }
        if (overlapPairs.isNotEmpty()) {
            warnings.add("ガイド円同士が重なり合っているキーが存在します: $overlapPairs")
        }

        return LayoutValidationResult(
            isValid = outOfBoundsIndices.isEmpty() && collisionPairs.isEmpty(),
            hasOutOfBounds = outOfBoundsIndices.isNotEmpty(),
            outOfBoundsKeys = outOfBoundsIndices,
            overlapPairs = overlapPairs,
            collisionPairs = collisionPairs,
            warningMessages = warnings,
            errorMessages = errors
        )
    }

    /**
     * 基準プロファイルと対象レイアウト・微調整値に基づく完全レイアウト検証。
     */
    fun validateLayout(
        baseFitProfile: FitProfile,
        targetLayout: SkyLayout,
        adjustment: SkyLayoutAdjustment = SkyLayoutAdjustment.DEFAULT,
        viewWidth: Int,
        viewHeight: Int,
        guideRadiusRatio: Float,
        density: Float = 1.0f
    ): LayoutValidationResult {
        if (baseFitProfile.keyCenters.size != 15) {
            return LayoutValidationResult(
                isValid = false,
                hasOutOfBounds = true,
                outOfBoundsKeys = emptyList(),
                overlapPairs = emptyList(),
                collisionPairs = emptyList(),
                warningMessages = emptyList(),
                errorMessages = listOf("基準FitProfileのキー数が15個ではありません (${baseFitProfile.keyCenters.size}個)")
            )
        }
        val rawPoints = computeTransformedRawPoints(baseFitProfile, targetLayout, adjustment)
        return validateRawPoints(rawPoints, viewWidth, viewHeight, guideRadiusRatio, density)
    }

    /**
     * レイアウト変換後の描画プロファイルを、実際の画面ピクセル座標およびガイド円半径に基づいて詳細に検証する。
     */
    fun validateLayoutProfile(
        profile: FitProfile,
        viewWidth: Int,
        viewHeight: Int,
        guideRadiusRatio: Float,
        density: Float = 1.0f
    ): LayoutValidationResult {
        val rawPoints = profile.keyCenters.map { Pair(it.x, it.y) }
        return validateRawPoints(rawPoints, viewWidth, viewHeight, guideRadiusRatio, density)
    }
}

/**
 * レイアウト検証結果。
 */
data class LayoutValidationResult(
    val isValid: Boolean,
    val hasOutOfBounds: Boolean,
    val outOfBoundsKeys: List<Int>,
    val overlapPairs: List<Pair<Int, Int>>,
    val collisionPairs: List<Pair<Int, Int>>,
    val warningMessages: List<String>,
    val errorMessages: List<String>
)

