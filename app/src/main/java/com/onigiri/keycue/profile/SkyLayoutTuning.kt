package com.onigiri.keycue.profile

/**
 * レイアウト固有の開発側既定キャリブレーション補正値。
 *
 * ユーザーが設定画面で調整・保存する [SkyLayoutAdjustment] とは責務が明確に分離されており、
 * 各レイアウトのプリセット幾何中心を基準として、実機レンダリングにおける
 * 外側への微拡大 (scaleX, scaleY) および全体オフセット (offsetX, offsetY) を規定します。
 */
data class SkyLayoutTuning(
    val scaleX: Float = 1.0f,
    val scaleY: Float = 1.0f,
    val offsetX: Float = 0.0f,
    val offsetY: Float = 0.0f
) {
    val isDefault: Boolean
        get() = scaleX == 1.0f && scaleY == 1.0f && offsetX == 0.0f && offsetY == 0.0f

    companion object {
        val DEFAULT = SkyLayoutTuning()
    }
}
