package com.onigiri.keycue.fitting

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/**
 * ボタン候補点検出の精度向上のためのOpenCV画像前処理を担当するクラス。
 *
 * 背景グラフィックやエフェクトノイズを低減し、円形キーの外枠を際立たせるための
 * 画像処理パイプラインを実行します:
 * 1. Grayscale 変換
 * 2. ガウシアンブラーによる高周波ノイズ低減
 * 3. コントラスト適応的強調 (CLAHE または Normalize)
 * 4. 適応的2値化またはエッジ検出 (Canny)
 * 5. モルフォロジー演算 (Closing: 小さな隙間の穴埋め)
 */
class ImagePreprocessor {

    /**
     * 前処理結果を保持するデータクラス。
     * デバッグ表示用のBitmap生成メソッドも含む。
     */
    data class PreprocessResult(
        val grayMat: Mat,
        val binaryMat: Mat,
        val edgeMat: Mat
    ) {
        /**
         * 2値化画像のデバッグ用Bitmapを生成する。
         */
        fun createBinaryBitmap(): Bitmap {
            val bmp = Bitmap.createBitmap(binaryMat.cols(), binaryMat.rows(), Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(binaryMat, bmp)
            return bmp
        }

        /**
         * エッジ検出画像のデバッグ用Bitmapを生成する。
         */
        fun createEdgeBitmap(): Bitmap {
            val bmp = Bitmap.createBitmap(edgeMat.cols(), edgeMat.rows(), Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(edgeMat, bmp)
            return bmp
        }

        /**
         * Matリソースを解放する。
         */
        fun release() {
            grayMat.release()
            binaryMat.release()
            edgeMat.release()
        }
    }

    /**
     * 入力ビットマップに対して前処理を実行し、[PreprocessResult] を返す。
     * 呼び出し側は使用後に [PreprocessResult.release] を呼ぶ必要がある。
     */
    fun process(bitmap: Bitmap): PreprocessResult {
        val srcMat = Mat(bitmap.height, bitmap.width, CvType.CV_8UC4)
        Utils.bitmapToMat(bitmap, srcMat)

        val grayMat = Mat()
        val blurredMat = Mat()
        val binaryMat = Mat()
        val edgeMat = Mat()

        try {
            // 1. Grayscale変換 (RGBA -> GRAY)
            Imgproc.cvtColor(srcMat, grayMat, Imgproc.COLOR_RGBA2GRAY)

            // 2. ガウシアンブラーでノイズ低減 (カーネルサイズ 5x5)
            Imgproc.GaussianBlur(grayMat, blurredMat, Size(5.0, 5.0), 1.5)

            // 3. 適応的2値化 (Adaptive Thresholding)
            // ボタンの輪郭やダイアモンド/円形パターンを安定して浮き彫りにする
            Imgproc.adaptiveThreshold(
                blurredMat,
                binaryMat,
                255.0,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                Imgproc.THRESH_BINARY,
                15,
                2.0
            )

            // 4. モルフォロジー演算 (Closing: 小さな切れ目を接続)
            val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(3.0, 3.0))
            Imgproc.morphologyEx(binaryMat, binaryMat, Imgproc.MORPH_CLOSE, kernel)
            kernel.release()

            // 5. Cannyエッジ検出
            Imgproc.Canny(blurredMat, edgeMat, 50.0, 150.0)

            return PreprocessResult(
                grayMat = grayMat,
                binaryMat = binaryMat,
                edgeMat = edgeMat
            )
        } finally {
            srcMat.release()
            blurredMat.release()
        }
    }
}
