package com.onigiri.keycue.overlay.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ChordGeometryTest {

    @Test
    fun buildMinimumSpanningTree_edgeCountIsNMinusOne() {
        // 2点 -> 1 edge
        val p2 = listOf(
            ChordPoint(0, 100f, 100f),
            ChordPoint(1, 200f, 100f)
        )
        val edges2 = ChordGeometry.buildMinimumSpanningTree(p2)
        assertEquals(1, edges2.size)

        // 3点 -> 2 edges
        val p3 = listOf(
            ChordPoint(0, 100f, 100f),
            ChordPoint(1, 200f, 100f),
            ChordPoint(2, 300f, 100f)
        )
        val edges3 = ChordGeometry.buildMinimumSpanningTree(p3)
        assertEquals(2, edges3.size)

        // 4点 -> 3 edges
        val p4 = listOf(
            ChordPoint(0, 100f, 100f),
            ChordPoint(1, 200f, 100f),
            ChordPoint(2, 100f, 200f),
            ChordPoint(3, 200f, 200f)
        )
        val edges4 = ChordGeometry.buildMinimumSpanningTree(p4)
        assertEquals(3, edges4.size)
    }

    @Test
    fun buildMinimumSpanningTree_insufficientPoints_returnsEmpty() {
        assertEquals(emptyList<ChordEdge>(), ChordGeometry.buildMinimumSpanningTree(emptyList()))
        assertEquals(emptyList<ChordEdge>(), ChordGeometry.buildMinimumSpanningTree(listOf(ChordPoint(0, 100f, 100f))))
    }

    @Test
    fun buildMinimumSpanningTree_orderIndependence_producesSameNormalizedEdges() {
        val a = ChordPoint(0, 100f, 100f)
        val b = ChordPoint(1, 300f, 100f)
        val c = ChordPoint(2, 200f, 150f)
        val d = ChordPoint(3, 200f, 300f)

        val order1 = listOf(a, b, c, d)
        val order2 = listOf(c, a, d, b)
        val order3 = listOf(d, c, b, a)

        val edges1 = ChordGeometry.buildMinimumSpanningTree(order1).map { it.normalizedKeyRange }.toSet()
        val edges2 = ChordGeometry.buildMinimumSpanningTree(order2).map { it.normalizedKeyRange }.toSet()
        val edges3 = ChordGeometry.buildMinimumSpanningTree(order3).map { it.normalizedKeyRange }.toSet()

        assertEquals(3, edges1.size)
        assertEquals(edges1, edges2)
        assertEquals(edges1, edges3)
    }

    @Test
    fun buildMinimumSpanningTree_equidistantTieBreak_isDeterministic() {
        // 正三角形の3点（全エッジ長が同一）
        val p0 = ChordPoint(0, 100f, 0f)
        val p1 = ChordPoint(1, 0f, 173.2f)
        val p2 = ChordPoint(2, 200f, 173.2f)

        val edges = ChordGeometry.buildMinimumSpanningTree(listOf(p0, p1, p2))
        assertEquals(2, edges.size)

        // 開始点は key=0。tie-break により決定的にエッジが選ばれる
        val normalized = edges.map { it.normalizedKeyRange }.toSet()
        // key 0 から key 1, key 2 の両方に接続（または決定的な2本）
        assertTrue(normalized.contains(Pair(0, 1)))
        assertTrue(normalized.contains(Pair(0, 2)))
    }

    @Test
    fun generateSupportPoints_expandsEightPointsPerCenter() {
        val centers = listOf(GeometryPoint(100f, 100f), GeometryPoint(200f, 200f))
        val radius = 20f

        val points = ChordGeometry.generateSupportPoints(centers, radius)
        assertEquals(16, points.size) // 2 centers * 8 angles

        // 全ての点が中心から radius の距離にあること
        for (i in 0 until 8) {
            val dist = ChordGeometry.distance(centers[0], points[i])
            assertEquals(20f, dist, 0.01f)
        }
        for (i in 8 until 16) {
            val dist = ChordGeometry.distance(centers[1], points[i])
            assertEquals(20f, dist, 0.01f)
        }
    }

    @Test
    fun buildConvexHull_excludesInteriorPoints() {
        // 四角形の4頂点 + 内部に1点
        val points = listOf(
            GeometryPoint(0f, 0f),
            GeometryPoint(100f, 0f),
            GeometryPoint(100f, 100f),
            GeometryPoint(0f, 100f),
            GeometryPoint(50f, 50f) // 内部点
        )

        val hull = ChordGeometry.buildConvexHull(points)
        assertEquals(4, hull.size)

        val hullSet = hull.map { Pair(it.x.toInt(), it.y.toInt()) }.toSet()
        val expected = setOf(Pair(0, 0), Pair(100, 0), Pair(100, 100), Pair(0, 100))
        assertEquals(expected, hullSet)
    }

    @Test
    fun buildConvexHull_collinearPoints_handledSafelyWithoutCrash() {
        // 同一直線上の点列
        val points = listOf(
            GeometryPoint(0f, 0f),
            GeometryPoint(10f, 0f),
            GeometryPoint(20f, 0f),
            GeometryPoint(30f, 0f)
        )

        val hull = ChordGeometry.buildConvexHull(points)
        // 両端点 (0,0) と (30,0) が残ること
        assertEquals(2, hull.size)
        assertEquals(0f, hull[0].x, 0.01f)
        assertEquals(30f, hull[1].x, 0.01f)
    }

    @Test
    fun buildConvexHull_withSupportPoints_enclosesCenters() {
        // 2点和音（100, 100）と（300, 100）
        val c1 = GeometryPoint(100f, 100f)
        val c2 = GeometryPoint(300f, 100f)
        val radius = 30f

        val supportPoints = ChordGeometry.generateSupportPoints(listOf(c1, c2), radius)
        val hull = ChordGeometry.buildConvexHull(supportPoints)

        assertTrue(hull.size >= 4)

        // 凸包の X, Y 範囲が中心点より確実に外側（margin分広い）こと
        val minX = hull.minOf { it.x }
        val maxX = hull.maxOf { it.x }
        val minY = hull.minOf { it.y }
        val maxY = hull.maxOf { it.y }

        assertTrue("minX ($minX) should be <= 70", minX <= 70.01f)
        assertTrue("maxX ($maxX) should be >= 330", maxX >= 329.99f)
        assertTrue("minY ($minY) should be <= 70", minY <= 70.01f)
        assertTrue("maxY ($maxY) should be >= 130", maxY >= 129.99f)
    }
}
