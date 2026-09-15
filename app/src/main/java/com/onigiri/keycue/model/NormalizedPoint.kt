package com.onigiri.keycue.model

/**
 * 画面上の位置を 0.0f 〜 1.0f の比率で表現する正規化座標データクラス。
 *
 * 端末の解像度やアスペクト比の違いに依存せず、画面サイズに応じた実ピクセル座標へ
 * 相互変換可能な共通位置表現として使用されます。
 *
 * @param x 画面左端を 0.0f、右端を 1.0f とする水平比率
 * @param y 画面上端を 0.0f、下端を 1.0f とする垂直比率
 */
data class NormalizedPoint(
    val x: Float,
    val y: Float
) {
    init {
        require(x in 0.0f..1.0f) { "x must be in 0.0f..1.0f, but was $x" }
        require(y in 0.0f..1.0f) { "y must be in 0.0f..1.0f, but was $y" }
    }

    /**
     * 指定された幅・高さに基づいて実ピクセル座標 (X, Y) を算出する。
     *
     * @param width 描画領域の幅（ピクセル）
     * @param height 描画領域の高さ（ピクセル）
     * @return 実ピクセル座標の Pair(X, Y)
     */
    fun toPixel(width: Int, height: Int): Pair<Float, Float> {
        return Pair(width * x, height * y)
    }

    fun pixelX(width: Int): Float = width * x

    fun pixelY(height: Int): Float = height * y
}
