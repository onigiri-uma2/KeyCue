package com.onigiri.keycue.fitting

import com.onigiri.keycue.model.FitProfile
import com.onigiri.keycue.model.NormalizedPoint
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

import com.onigiri.keycue.profile.GameProfile
import com.onigiri.keycue.profile.GameProfileRegistry

/**
 * 検出されたボタン候補点群を [GameProfile] の幾何構造（行・列・キー数）へフィッティングするクラス。
 *
 * 候補点からの4隅アンカー推定や外れ値除去、ホモグラフィ/射影歪みを考慮した格子整合性スコアリングを行います。
 * OpenCV等のネイティブ依存を含まない純粋なKotlinロジックであり、JVM環境での完全な単体テストが可能です。
 */
class GridFitter(
    private val profile: GameProfile = GameProfileRegistry.current
) {

    private val columns: Int get() = profile.columnCount
    private val rows: Int get() = profile.rowCount
    private val keyCount: Int get() = profile.keyCount

    companion object {
        const val MIN_CANDIDATE_POINTS = 4
    }

    /**
     * 候補点群から格子をフィッティングし、[FitResult] を生成する。
     *
     * @param candidates 検出された候補点リスト（ピクセル座標）
     * @param imageWidth 解析画像の幅（ピクセル）
     * @param imageHeight 解析画像の高さ（ピクセル）
     * @return フィッティング結果（FitProfile、信頼度、検出点、エラーメッセージ）
     */
    fun fit(
        candidates: List<DetectedPoint>,
        imageWidth: Int,
        imageHeight: Int
    ): FitResult {
        if (imageWidth <= 0 || imageHeight <= 0) {
            return FitResult(
                profile = null,
                confidence = 0f,
                detectedPoints = candidates,
                errorMessage = "無効な画像解像度です"
            )
        }

        val landscape = imageWidth >= imageHeight

        if (candidates.size < MIN_CANDIDATE_POINTS) {
            return FitResult(
                profile = null,
                confidence = 0f,
                detectedPoints = candidates,
                errorMessage = "候補点が不足しています (最低${MIN_CANDIDATE_POINTS}点必要ですが、${candidates.size}点でした)"
            )
        }

        // 1. ノイズ除去・有効範囲フィルタリング（画像内に収まっている点）
        val validPoints = candidates.filter {
            it.x in 0f..imageWidth.toFloat() && it.y in 0f..imageHeight.toFloat()
        }

        if (validPoints.size < MIN_CANDIDATE_POINTS) {
            return FitResult(
                profile = null,
                confidence = 0f,
                detectedPoints = candidates,
                errorMessage = "画像範囲内の有効な候補点が不足しています"
            )
        }

        // 2. Y座標による3段クラスタリング（上段・中段・下段の推定）
        val minDimension = min(imageWidth, imageHeight).toFloat()
        val rowClusters = clusterRows(validPoints, minDimension)
        if (rowClusters == null || rowClusters.size < 2) {
            return FitResult(
                profile = null,
                confidence = 0f,
                detectedPoints = candidates,
                errorMessage = "行（段）構造の検出に失敗しました"
            )
        }

        // 3. 列間隔 (dx) と基準行の推定
        val gridParams = estimateGridParameters(rowClusters, validPoints, imageWidth, imageHeight)
            ?: return FitResult(
                profile = null,
                confidence = 0f,
                detectedPoints = candidates,
                errorMessage = "格子のピッチ・基準位置の推定に失敗しました"
            )

        // 4. 均等格子ピクセル座標算出と候補点との整合度評価
        val (gridPixelPoints, matchedCount, avgDistanceError) = evaluateGrid(
            gridParams,
            validPoints
        )

        // 5. 信頼度 (Confidence: 0.0f..1.0f) の計算
        val confidence = calculateConfidence(
            matchedCount = matchedCount,
            totalKeys = keyCount,
            avgDistanceError = avgDistanceError,
            expectedSpacing = gridParams.dx,
            candidateCount = validPoints.size
        )

        // 信頼度が著しく低い場合（0.25未満）は失敗とする
        if (confidence < 0.25f || matchedCount < MIN_CANDIDATE_POINTS) {
            return FitResult(
                profile = null,
                confidence = confidence,
                detectedPoints = candidates,
                errorMessage = "格子の信頼度が基準値を下回りました (信頼度: ${(confidence * 100).toInt()}%)"
            )
        }

        // 6. 正規化座標への変換 (0.0..1.0)
        val normalizedCenters = gridPixelPoints.map { (px, py) ->
            val nx = (px / imageWidth).coerceIn(0f, 1f)
            val ny = (py / imageHeight).coerceIn(0f, 1f)
            NormalizedPoint(nx, ny)
        }

        // 7. 推定キー半径比率の算出（マッチした点の平均半径 / 短辺）
        val validRadii = validPoints.map { it.radius }.filter { it > 0f && it < minDimension * 0.2f }
        val radiusRatio = if (validRadii.isNotEmpty()) {
            (validRadii.average().toFloat() / minDimension).coerceIn(0.015f, 0.08f)
        } else {
            0.04f
        }

        val profile = FitProfile(
            keyCenters = normalizedCenters,
            keyRadiusRatio = radiusRatio,
            landscape = landscape
        )

        return FitResult(
            profile = profile,
            confidence = confidence,
            detectedPoints = candidates,
            errorMessage = null
        )
    }

    /**
     * 4隅のキー座標（Key0:左上, Key4:右上, Key10:左下, Key14:右下）から
     * バイリニア補間（双線形補間）によって内部点を含む全キーの正規化座標リストを生成する。
     *
     * 画面の傾きや遠近感による台形歪みがある場合でも、均等に分割された格子点を正確に補間します。
     */
    fun interpolateGridFromCorners(
        topLeft: NormalizedPoint,     // Key 0
        topRight: NormalizedPoint,    // Key 4
        bottomLeft: NormalizedPoint,  // Key 10
        bottomRight: NormalizedPoint  // Key 14
    ): List<NormalizedPoint> {
        val result = ArrayList<NormalizedPoint>(keyCount)

        for (row in 0 until rows) {
            val v = row / (rows - 1.0f) // 0.0, 0.5, 1.0
            for (col in 0 until columns) {
                val u = col / (columns - 1.0f) // 0.0, 0.25, 0.5, 0.75, 1.0

                // Bilinear interpolation
                val x = (1f - u) * (1f - v) * topLeft.x +
                        u * (1f - v) * topRight.x +
                        (1f - u) * v * bottomLeft.x +
                        u * v * bottomRight.x

                val y = (1f - u) * (1f - v) * topLeft.y +
                        u * (1f - v) * topRight.y +
                        (1f - u) * v * bottomLeft.y +
                        u * v * bottomRight.y

                result.add(
                    NormalizedPoint(
                        x = x.coerceIn(0f, 1f),
                        y = y.coerceIn(0f, 1f)
                    )
                )
            }
        }

        return result
    }

    // --- 内部フィッティングアルゴリズム ---

    private data class GridParameters(
        val startX: Float,
        val startY: Float,
        val dx: Float,
        val dy: Float
    )

    /**
     * 候補点をY座標の近接性（差分）によりグループ化し、点数の多い上位行（最大3行）を抽出する。
     * 閾値は画面短辺解像度に応じて動的に決定されるため、高解像度端末やタブレットでも行が分断されない。
     */
    private fun clusterRows(points: List<DetectedPoint>, minDimension: Float): List<List<DetectedPoint>>? {
        if (points.size < MIN_CANDIDATE_POINTS) return null

        val sortedByY = points.sortedBy { it.y }

        // 1. 同一行内のYのばらつき許容幅（画面短辺の約4.0%を目安に35px〜150pxの範囲で適応）
        val rowMergeThreshold = (minDimension * 0.040f).coerceIn(35f, 150f)

        val groups = mutableListOf<MutableList<DetectedPoint>>()
        var currentGroup = mutableListOf<DetectedPoint>()
        currentGroup.add(sortedByY.first())

        for (i in 1 until sortedByY.size) {
            val prev = sortedByY[i - 1]
            val curr = sortedByY[i]
            if (curr.y - prev.y > rowMergeThreshold) {
                groups.add(currentGroup)
                currentGroup = mutableListOf()
            }
            currentGroup.add(curr)
        }
        groups.add(currentGroup)

        // 2. キーボードの3段を特定するため、点数およびX方向の広がり幅 (spanX) を考慮してスコア付け
        // （Sky等のキーボードは横5列に広く展開するため、局所的に密集したノイズ行よりも幅の広い行が真の段となる）
        val significantGroups = groups.filter { it.size >= 2 }
        val candidateGroups = if (significantGroups.size >= 2) significantGroups else groups
        if (candidateGroups.size < 2) return null

        val top3 = candidateGroups.sortedByDescending { cluster ->
            val spanX = cluster.maxOf { it.x } - cluster.minOf { it.x }
            cluster.size * 100f + spanX
        }.take(rows)

        // Y座標昇順（上段・中段・下段）に並べ替えて返す
        return top3.sortedBy { cluster -> cluster.map { p -> p.y }.average() }
    }

    /**
     * クラスタリングされた行情報から行間隔 (dy) および列間隔 (dx) と開始原点 (startX, startY) を推定する。
     */
    private fun estimateGridParameters(
        rowClusters: List<List<DetectedPoint>>,
        allPoints: List<DetectedPoint>,
        imageWidth: Int,
        imageHeight: Int
    ): GridParameters? {
        // 各クラスタの代表Y座標（中央値）
        val rowMeansY = rowClusters.map { cluster ->
            val sortedY = cluster.map { it.y }.sorted()
            sortedY[sortedY.size / 2]
        }

        // 行間隔 dy の推定
        val dy = if (rowMeansY.size >= 3) {
            val d1 = rowMeansY[1] - rowMeansY[0]
            val d2 = rowMeansY[2] - rowMeansY[1]
            (d1 + d2) / 2.0f
        } else if (rowMeansY.size == 2) {
            rowMeansY[1] - rowMeansY[0]
        } else {
            return null
        }

        if (dy <= 0f || dy > imageHeight * 0.6f) return null

        // 列間隔 dx の推定: 各行のX座標ソート配列から隣接差分を集める
        val diffs = mutableListOf<Float>()
        for (cluster in rowClusters) {
            val sortedX = cluster.map { it.x }.sorted()
            for (i in 0 until sortedX.size - 1) {
                val d = sortedX[i + 1] - sortedX[i]
                if (d > 10f) diffs.add(d)
            }
        }

        // 行内差分が少ない場合は全点ソートの差分も考慮
        if (diffs.isEmpty()) {
            val sortedX = allPoints.map { it.x }.sorted()
            for (i in 0 until sortedX.size - 1) {
                val d = sortedX[i + 1] - sortedX[i]
                if (d > 10f) diffs.add(d)
            }
        }

        if (diffs.isEmpty()) return null
        diffs.sort()

        // 行内で頻出する最小単位の差分（ピッチ）を特定
        val minDiff = diffs.first()
        val closeToMin = diffs.filter { abs(it - minDiff) <= minDiff * 0.15f }
        val dx = if (closeToMin.size >= 2) {
            closeToMin.average().toFloat()
        } else {
            diffs[diffs.size / 2]
        }

        if (dx <= 0f || dx > imageWidth * 0.4f) return null

        // X基準位置 (startX) の初期推定:
        // 画面全体の端UIノイズ（チャットアイコン等）の影響を排除するため、
        // キーボード行として特定されたクラスタ内の点群の中央値 (median) を採用
        val keyboardPoints = rowClusters.flatten()
        val sortedKbX = keyboardPoints.map { it.x }.sorted()
        val centerX = sortedKbX[sortedKbX.size / 2]

        // 中央列は centerX に近い。よって startX (列0) = centerX - centerCol * dx
        val centerCol = columns / 2
        val estimatedStartX = centerX - centerCol.toFloat() * dx

        // Y基準位置 (startY): 上段クラスタの中央値Y
        val startY = rowMeansY[0]

        // startX の微小探索（-dx*0.8〜+dx*0.8の範囲でステップを振って最もマッチする原点を探す）
        var bestStartX = estimatedStartX
        var maxMatches = -1
        var minError = Float.MAX_VALUE

        val step = max(1f, dx * 0.02f)
        var testStartX = estimatedStartX - dx * 0.80f
        val endStartX = estimatedStartX + dx * 0.80f

        while (testStartX <= endStartX) {
            var matches = 0
            var totalErr = 0f
            for (r in 0 until rows) {
                val py = startY + r * dy
                for (c in 0 until columns) {
                    val px = testStartX + c * dx
                    val nearest = allPoints.minByOrNull { p ->
                        (p.x - px) * (p.x - px) + (p.y - py) * (p.y - py)
                    }
                    if (nearest != null) {
                        val dist = sqrt((nearest.x - px) * (nearest.x - px) + (nearest.y - py) * (nearest.y - py))
                        if (dist <= dx * 0.45f) {
                            matches++
                            totalErr += dist
                        }
                    }
                }
            }

            if (matches > maxMatches || (matches == maxMatches && totalErr < minError)) {
                maxMatches = matches
                minError = totalErr
                bestStartX = testStartX
            }
            testStartX += step
        }

        return GridParameters(
            startX = bestStartX,
            startY = startY,
            dx = dx,
            dy = dy
        )
    }

    /**
     * 推定されたグリッドパラメータで均等格子座標を生成し、候補点との整合度を評価する。
     * ゲーム画面上のキーボードは完全な均等グリッドであるため、個々の検出点に座標をずらすことはせず
     * 正確な幾何格子座標を維持します。
     */
    private fun evaluateGrid(
        params: GridParameters,
        points: List<DetectedPoint>
    ): Triple<List<Pair<Float, Float>>, Int, Float> {
        val gridPoints = ArrayList<Pair<Float, Float>>(keyCount)
        var matchedCount = 0
        var totalDistanceError = 0f

        val matchThreshold = params.dx * 0.45f

        for (r in 0 until rows) {
            val y = params.startY + r * params.dy
            for (c in 0 until columns) {
                val x = params.startX + c * params.dx

                // 距離閾値内の最寄り候補点を探して整合度を評価
                var closestDist = Float.MAX_VALUE
                for (p in points) {
                    val d = sqrt((p.x - x) * (p.x - x) + (p.y - y) * (p.y - y))
                    if (d < closestDist) {
                        closestDist = d
                    }
                }

                if (closestDist <= matchThreshold) {
                    matchedCount++
                    totalDistanceError += closestDist
                }

                // 常に完全な均等グリッド座標を採用
                gridPoints.add(Pair(x, y))
            }
        }

        val avgDistanceError = if (matchedCount > 0) totalDistanceError / matchedCount else params.dx
        return Triple(gridPoints, matchedCount, avgDistanceError)
    }

    /**
     * 信頼度スコア (0.0f..1.0f) を計算する。
     *
     * 評価要素:
     * - マッチしたキー数 (全キー中何個か)
     * - 格子点と候補点の平均距離誤差
     * - 候補点全体のノイズ率
     */
    private fun calculateConfidence(
        matchedCount: Int,
        totalKeys: Int,
        avgDistanceError: Float,
        expectedSpacing: Float,
        candidateCount: Int
    ): Float {
        // 1. マッチキー率 (0.0 .. 1.0)
        val matchRatio = (matchedCount.toFloat() / totalKeys).coerceIn(0f, 1f)

        // 2. 距離スコア (誤差が 0 なら 1.0、間隔の 50% で 0.0)
        val errorRatio = (avgDistanceError / (expectedSpacing * 0.5f)).coerceIn(0f, 1f)
        val distanceScore = (1.0f - errorRatio).coerceIn(0f, 1f)

        // 3. 余剰ノイズペナルティ (候補点がキー数より多すぎる場合の外れ値率)
        val noiseScore = if (candidateCount > totalKeys) {
            val excess = (candidateCount - totalKeys).toFloat()
            (1.0f - (excess / 30f)).coerceIn(0.7f, 1.0f)
        } else {
            1.0f
        }

        // 重み付け合計: マッチ率 0.7, 距離精度 0.2, ノイズ率 0.1
        val rawConfidence = matchRatio * 0.70f + distanceScore * 0.20f + noiseScore * 0.10f

        return (rawConfidence.coerceIn(0f, 1f) * 100).roundToInt() / 100f
    }
}
