package com.onigiri.keycue.profile

/**
 * レイアウト別の位置オフセットおよび拡大縮小微調整パラメータ。
 *
 * 端末の解像度・アスペクト比・Sky側のUIスケール差による微小なズレを吸収するために
 * 各レイアウト（[SkyLayout]）ごとに独立して保持・保存されます。
 *
 * @param offsetX 水平方向の正規化オフセット (-0.5f 〜 +0.5f)
 * @param offsetY 垂直方向の正規化オフセット (-0.5f 〜 +0.5f)
 * @param scaleX 水平方向のスケール倍率 (0.5f 〜 2.0f)
 * @param scaleY 垂直方向のスケール倍率 (0.5f 〜 2.0f)
 */
data class SkyLayoutAdjustment(
    val offsetX: Float = DEFAULT_OFFSET_X,
    val offsetY: Float = DEFAULT_OFFSET_Y,
    val scaleX: Float = DEFAULT_SCALE_X,
    val scaleY: Float = DEFAULT_SCALE_Y
) {
    companion object {
        const val DEFAULT_OFFSET_X: Float = 0.0f
        const val DEFAULT_OFFSET_Y: Float = 0.0f
        const val DEFAULT_SCALE_X: Float = 1.0f
        const val DEFAULT_SCALE_Y: Float = 1.0f

        const val MIN_OFFSET: Float = -0.5f
        const val MAX_OFFSET: Float = 0.5f
        const val MIN_SCALE: Float = 0.5f
        const val MAX_SCALE: Float = 2.0f

        /** 初期デフォルト調整インスタンス */
        val DEFAULT = SkyLayoutAdjustment()

        /**
         * 範囲外の不正値を安全な範囲に丸めた [SkyLayoutAdjustment] を生成する。
         */
        fun safe(
            offsetX: Float = DEFAULT_OFFSET_X,
            offsetY: Float = DEFAULT_OFFSET_Y,
            scaleX: Float = DEFAULT_SCALE_X,
            scaleY: Float = DEFAULT_SCALE_Y
        ): SkyLayoutAdjustment {
            return SkyLayoutAdjustment(
                offsetX = if (offsetX.isNaN()) DEFAULT_OFFSET_X else offsetX.coerceIn(MIN_OFFSET, MAX_OFFSET),
                offsetY = if (offsetY.isNaN()) DEFAULT_OFFSET_Y else offsetY.coerceIn(MIN_OFFSET, MAX_OFFSET),
                scaleX = if (scaleX.isNaN()) DEFAULT_SCALE_X else scaleX.coerceIn(MIN_SCALE, MAX_SCALE),
                scaleY = if (scaleY.isNaN()) DEFAULT_SCALE_Y else scaleY.coerceIn(MIN_SCALE, MAX_SCALE)
            )
        }
    }

    /** デフォルト（補正なし）状態かどうか */
    val isDefault: Boolean
        get() = offsetX == DEFAULT_OFFSET_X &&
                offsetY == DEFAULT_OFFSET_Y &&
                scaleX == DEFAULT_SCALE_X &&
                scaleY == DEFAULT_SCALE_Y
}
