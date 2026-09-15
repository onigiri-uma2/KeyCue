package com.onigiri.keycue.fitting

/**
 * スクリーンショット等の画像解析によって検出された演奏キーボタン候補の座標情報。
 *
 * @param x 解析画像内のX座標（ピクセル）
 * @param y 解析画像内のY座標（ピクセル）
 * @param radius 検出されたキー円の推定半径（ピクセル）
 * @param confidence 候補としての確信度 (0.0f..1.0f)
 */
data class DetectedPoint(
    val x: Float,
    val y: Float,
    val radius: Float = 0f,
    val confidence: Float = 1.0f
)
