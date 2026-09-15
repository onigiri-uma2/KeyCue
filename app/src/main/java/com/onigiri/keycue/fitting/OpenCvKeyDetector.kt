package com.onigiri.keycue.fitting

import android.graphics.Bitmap
import android.util.Log
import org.opencv.android.OpenCVLoader
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * OpenCVを用いたボタン候補点検出器の標準実装。
 *
 * OpenCVのネイティブ処理を本パッケージ内にカプセル化し、
 * 輪郭解析（円形度・サイズフィルタリング）とハフ変換円検出（HoughCircles）を
 * 組み合わせてスクリーンショットから演奏ボタン候補点を抽出します。
 */
class OpenCvKeyDetector(
    private val preprocessor: ImagePreprocessor = ImagePreprocessor()
) : KeyDetector {

    companion object {
        private const val TAG = "OpenCvKeyDetector"

        private var isInitialized = false

        /**
         * OpenCVライブラリの初期化を安全に行う。
         */
        @Synchronized
        fun ensureInitialized(): Boolean {
            if (isInitialized) return true
            isInitialized = try {
                if (OpenCVLoader.initLocal()) {
                    Log.d(TAG, "OpenCVLoader.initLocal() successful")
                    true
                } else if (OpenCVLoader.initDebug()) {
                    Log.d(TAG, "OpenCVLoader.initDebug() successful")
                    true
                } else {
                    Log.w(TAG, "OpenCV initialization failed")
                    false
                }
            } catch (e: Throwable) {
                Log.e(TAG, "OpenCV initialization error: ${e.message}", e)
                false
            }
            return isInitialized
        }
    }

    init {
        ensureInitialized()
    }

    override fun detect(image: Bitmap): List<DetectedPoint> {
        if (!ensureInitialized()) {
            Log.w(TAG, "OpenCV not initialized. Returning empty candidates.")
            return emptyList()
        }

        val candidates = mutableListOf<DetectedPoint>()
        val minDimension = min(image.width, image.height).toFloat()

        // 演奏キーの配置Y範囲フィルタ（画面短辺/長辺に関わらずSkyのキーボードは 8% 〜 72% 付近に位置する）
        // 画面最下部の地面にあるキャンドル群（Y > 72%）や画面上部UI（Y < 8%）を確実に排除
        val minY = image.height * 0.08f
        val maxY = image.height * 0.72f

        // ボタン半径の想定レンジ（画面短辺の 5.8% 〜 12%）
        // 実キー半径比率（約 0.075〜0.090）に適合させ、キー間の隙間やテクスチャの小円弧（4%以下）を徹底排除
        val minRadius = max(24f, minDimension * 0.058f)
        val maxRadius = minDimension * 0.12f
        val minArea = (PI * minRadius * minRadius * 0.40).toDouble()

        val preprocess = preprocessor.process(image)

        try {
            // --- 方式1: 輪郭抽出 (findContours) による円形・ボタン特徴解析 ---
            val contours = ArrayList<MatOfPoint>()
            val hierarchy = Mat()
            Imgproc.findContours(
                preprocess.binaryMat,
                contours,
                hierarchy,
                Imgproc.RETR_LIST,
                Imgproc.CHAIN_APPROX_SIMPLE
            )
            hierarchy.release()

            for (contour in contours) {
                val point2f = MatOfPoint2f(*contour.toArray())
                val area = Imgproc.contourArea(point2f)
                val perimeter = Imgproc.arcLength(point2f, true)

                if (area >= minArea && perimeter > 30.0) {
                    // 円形度: 4 * π * Area / Perimeter^2
                    // 完全な正円: 1.0, 角丸四角形: 0.6〜0.8, ダイアモンド(◇): 0.39〜0.55
                    val circularity = (4.0 * PI * area / (perimeter * perimeter)).toFloat()

                    val center = Point()
                    val radius = FloatArray(1)
                    Imgproc.minEnclosingCircle(point2f, center, radius)
                    val r = radius[0]

                    val rect = Imgproc.boundingRect(contour)
                    val aspectRatio = rect.width.toFloat() / max(1, rect.height).toFloat()

                    // ボタンらしさの判定条件:
                    // 1. Y座標がキーボード領域内 (8% 〜 72%)
                    // 2. 半径が想定範囲内 (画面短辺の 5.8%〜12%)
                    // 3. アスペクト比が正方形・円形に近い (0.70 〜 1.40)
                    // 4. 円形度が 0.35 以上 (正円○だけでなくダイアモンド◇にも対応)
                    if (center.y in minY..maxY &&
                        r in minRadius..maxRadius &&
                        aspectRatio in 0.70f..1.40f &&
                        circularity in 0.35f..1.25f
                    ) {
                        val confidence = (circularity.coerceIn(0f, 1f) * 0.5f + 0.5f)
                        candidates.add(
                            DetectedPoint(
                                x = center.x.toFloat(),
                                y = center.y.toFloat(),
                                radius = r,
                                confidence = confidence
                            )
                        )
                    }
                }
                contour.release()
                point2f.release()
            }

            // --- 方式2: HoughCircles による円形検出フォールバック / 補完 ---
            val circlesMat = Mat()
            try {
                Imgproc.HoughCircles(
                    preprocess.grayMat,
                    circlesMat,
                    Imgproc.HOUGH_GRADIENT,
                    1.5,
                    (minRadius * 1.6).toDouble(), // 検出円間の最小距離
                    100.0,                        // Canny 上位閾値
                    52.0,                         // 中心検出のアキュムレータ閾値（隙間ノイズ排除のため引き上げ）
                    minRadius.toInt(),
                    maxRadius.toInt()
                )

                if (!circlesMat.empty()) {
                    val numCircles = circlesMat.cols()
                    for (i in 0 until numCircles) {
                        val circleData = circlesMat.get(0, i) ?: continue
                        val cx = circleData[0].toFloat()
                        val cy = circleData[1].toFloat()
                        val cr = circleData[2].toFloat()

                        if (cy in minY..maxY && cr in minRadius..maxRadius) {
                            candidates.add(
                                DetectedPoint(
                                    x = cx,
                                    y = cy,
                                    radius = cr,
                                    confidence = 0.85f
                                )
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "HoughCircles detection error: ${e.message}")
            } finally {
                circlesMat.release()
            }

            // --- 3. 重複候補の統合（近接点のマージ） ---
            return mergeDuplicateCandidates(candidates, minRadius * 0.7f)

        } finally {
            preprocess.release()
        }
    }

    /**
     * 距離が近すぎる（重複している）候補点を平均座標に統合する。
     */
    private fun mergeDuplicateCandidates(
        points: List<DetectedPoint>,
        distanceThreshold: Float
    ): List<DetectedPoint> {
        val merged = mutableListOf<DetectedPoint>()

        for (p in points) {
            val existingIndex = merged.indexOfFirst { m ->
                val dx = m.x - p.x
                val dy = m.y - p.y
                sqrt(dx * dx + dy * dy) <= distanceThreshold
            }

            if (existingIndex >= 0) {
                // すでに近接する点が存在する場合は平均化
                val existing = merged[existingIndex]
                val newX = (existing.x + p.x) / 2.0f
                val newY = (existing.y + p.y) / 2.0f
                val newR = max(existing.radius, p.radius)
                val newConf = max(existing.confidence, p.confidence)
                merged[existingIndex] = DetectedPoint(newX, newY, newR, newConf)
            } else {
                merged.add(p)
            }
        }

        return merged
    }
}
