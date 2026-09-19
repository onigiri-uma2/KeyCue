package com.onigiri.keycue.fitting

import android.graphics.Bitmap

/**
 * スクリーンショット画像からゲーム内のキー候補点を抽出する検出器インターフェース。
 *
 * OpenCVハフ変換や輪郭抽出等、異なる検出アルゴリズムの差し替えおよびモックテストを可能にします。
 */
interface KeyDetector {
    /**
     * 指定されたビットマップ画像からボタン候補点を検出する。
     *
     * @param image 解析対象ビットマップ
     * @return 検出されたボタン候補点リスト（ピクセル座標系は入力ビットマップ基準）
     */
    fun detect(image: Bitmap): List<DetectedPoint>
}
