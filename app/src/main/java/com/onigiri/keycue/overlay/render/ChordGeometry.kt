package com.onigiri.keycue.overlay.render

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 幾何計算用の汎用座標点（Android framework 非依存）。
 */
data class GeometryPoint(
    val x: Float,
    val y: Float
)

/**
 * キーインデックス付きの幾何計算用座標点。
 */
data class ChordPoint(
    val key: Int,
    val x: Float,
    val y: Float
) {
    fun toGeometryPoint(): GeometryPoint = GeometryPoint(x, y)
}

/**
 * Chord Link 用のエッジ情報（2つのキーおよび座標を接続）。
 */
data class ChordEdge(
    val fromKey: Int,
    val toKey: Int,
    val fromPoint: GeometryPoint,
    val toPoint: GeometryPoint
) {
    /**
     * テスト比較および決定的な識別用の正規化キーペア (minKey, maxKey)。
     */
    val normalizedKeyRange: Pair<Int, Int>
        get() = if (fromKey <= toKey) Pair(fromKey, toKey) else Pair(toKey, fromKey)
}

/**
 * 和音リンクおよび和音ハローの幾何ロジックを集約する純粋幾何ユーティリティ。
 *
 * Android framework 型（PointF, Path 等）に依存せず、ローカル JVM Unit Test で
 * 完全に検証可能な決定論的アルゴリズムを提供します。
 */
object ChordGeometry {

    /**
     * 外周サンプル点生成用のデフォルト 8 方向ラジアン角 (0°, 45°, 90°, 135°, 180°, 225°, 270°, 315°)。
     */
    val DEFAULT_SUPPORT_ANGLES = floatArrayOf(
        0f,
        (Math.PI / 4).toFloat(),
        (Math.PI / 2).toFloat(),
        (3 * Math.PI / 4).toFloat(),
        Math.PI.toFloat(),
        (5 * Math.PI / 4).toFloat(),
        (3 * Math.PI / 2).toFloat(),
        (7 * Math.PI / 4).toFloat()
    )

    /**
     * Prim法による Minimum Spanning Tree（最小全域木）を構築する。
     *
     * - 開始頂点: [points] 内で [ChordPoint.key] が最小の点（入力順に依存せず決定的）
     * - tie-break: 1. ユークリッド距離昇順, 2. min(fromKey, toKey) 昇順, 3. max(fromKey, toKey) 昇順
     * - N点に対して厳密に N-1 本のエッジを返す（N < 2 の場合は空リスト）
     *
     * @param points 接続対象となる点一覧
     * @return 接続エッジ一覧
     */
    fun buildMinimumSpanningTree(points: List<ChordPoint>): List<ChordEdge> {
        if (points.size < 2) return emptyList()

        // 1. 重複キーの排除（同一キーが複数回渡された場合は先頭を採用）
        val uniquePoints = points.distinctBy { it.key }
        if (uniquePoints.size < 2) return emptyList()

        val n = uniquePoints.size
        val visited = BooleanArray(n)
        val edges = ArrayList<ChordEdge>(n - 1)

        // 開始頂点を key が最小の点に固定して入力順序に依存しない決定性を担保
        var startIdx = 0
        var minKey = uniquePoints[0].key
        for (i in 1 until n) {
            if (uniquePoints[i].key < minKey) {
                minKey = uniquePoints[i].key
                startIdx = i
            }
        }

        visited[startIdx] = true
        var visitedCount = 1

        while (visitedCount < n) {
            var bestU = -1
            var bestV = -1
            var bestDistSq = Float.MAX_VALUE
            var bestMinKey = Int.MAX_VALUE
            var bestMaxKey = Int.MAX_VALUE

            for (u in 0 until n) {
                if (!visited[u]) continue
                val pu = uniquePoints[u]

                for (v in 0 until n) {
                    if (visited[v]) continue
                    val pv = uniquePoints[v]

                    val dx = pu.x - pv.x
                    val dy = pu.y - pv.y
                    val distSq = dx * dx + dy * dy

                    val k1 = if (pu.key <= pv.key) pu.key else pv.key
                    val k2 = if (pu.key <= pv.key) pv.key else pu.key

                    // 1. 距離最小
                    // 2. minKey 最小
                    // 3. maxKey 最小
                    val isBetter = when {
                        distSq < bestDistSq - 1e-6f -> true
                        distSq > bestDistSq + 1e-6f -> false
                        k1 < bestMinKey -> true
                        k1 > bestMinKey -> false
                        else -> k2 < bestMaxKey
                    }

                    if (isBetter) {
                        bestDistSq = distSq
                        bestMinKey = k1
                        bestMaxKey = k2
                        bestU = u
                        bestV = v
                    }
                }
            }

            if (bestV == -1) break

            visited[bestV] = true
            visitedCount++

            val fromP = uniquePoints[bestU]
            val toP = uniquePoints[bestV]
            edges.add(
                ChordEdge(
                    fromKey = fromP.key,
                    toKey = toP.key,
                    fromPoint = fromP.toGeometryPoint(),
                    toPoint = toP.toGeometryPoint()
                )
            )
        }

        return edges
    }

    /**
     * 各ノート中心点の周囲に、外周サンプル点（Support Points）を生成する。
     *
     * @param centers 各ノートの中心座標
     * @param radius 外周半径 (ノート視覚半径 + haloMarginPx)
     * @param angles サンプル点のラジアン角配列（デフォルト 8 方向）
     * @return 全中心点から展開された Support Points 一覧
     */
    fun generateSupportPoints(
        centers: List<GeometryPoint>,
        radius: Float,
        angles: FloatArray = DEFAULT_SUPPORT_ANGLES
    ): List<GeometryPoint> {
        if (centers.isEmpty() || radius <= 0f) return emptyList()

        val result = ArrayList<GeometryPoint>(centers.size * angles.size)
        for (c in centers) {
            for (angle in angles) {
                result.add(
                    GeometryPoint(
                        x = c.x + cos(angle) * radius,
                        y = c.y + sin(angle) * radius
                    )
                )
            }
        }
        return result
    }

    /**
     * アンドリューのモノトーン・チェーン（Monotone Chain）アルゴリズムにより、
     * 点集合の凸包（Convex Hull）の頂点列（反時計回り）を算出する。
     *
     * 同一直線上の点（collinear points）も安全に処理し、余分な同一頂点や不要な内部点を除外します。
     *
     * @param points 対象座標点のリスト
     * @return 凸包を構成する頂点列（N < 3 の場合は重複を除いた点）
     */
    fun buildConvexHull(points: List<GeometryPoint>): List<GeometryPoint> {
        if (points.size <= 1) return points

        // 重複座標を排除した上で X 昇順、同 X なら Y 昇順にソート
        val sorted = points.distinctBy { Pair(it.x, it.y) }
            .sortedWith(compareBy({ it.x }, { it.y }))

        if (sorted.size <= 2) return sorted

        val n = sorted.size
        val hull = ArrayList<GeometryPoint>(2 * n)

        // 下側凸包の構築
        for (p in sorted) {
            while (hull.size >= 2 && crossProduct(hull[hull.size - 2], hull[hull.size - 1], p) <= 1e-6f) {
                hull.removeAt(hull.size - 1)
            }
            hull.add(p)
        }

        // 上側凸包の構築
        val lowerSize = hull.size + 1
        for (i in n - 2 downTo 0) {
            val p = sorted[i]
            while (hull.size >= lowerSize && crossProduct(hull[hull.size - 2], hull[hull.size - 1], p) <= 1e-6f) {
                hull.removeAt(hull.size - 1)
            }
            hull.add(p)
        }

        // 始点と終点の重複を除去
        hull.removeAt(hull.size - 1)

        return hull
    }

    /**
     * ベクトル OA と OB の外積（2D cross product: OA x OB）を計算する。
     * 正: 反時計回り（左折）, 負: 時計回り（右折）, 0: 同一直線上 (collinear)
     */
    private fun crossProduct(o: GeometryPoint, a: GeometryPoint, b: GeometryPoint): Float {
        return (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x)
    }

    /**
     * 2点間のユークリッド距離を計算する。
     */
    fun distance(p1: GeometryPoint, p2: GeometryPoint): Float {
        val dx = p1.x - p2.x
        val dy = p1.y - p2.y
        return sqrt(dx * dx + dy * dy)
    }
}
