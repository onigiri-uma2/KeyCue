package com.onigiri.keycue.overlay.render

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import com.onigiri.keycue.playback.FallingNoteCalculator
import com.onigiri.keycue.playback.GuideFrame

/**
 * 和音・同時押しキーの視覚的グルーピング（Chord Link および Chord Halo）の描画を担当するレンダラー。
 *
 * - **Chord Link**: 同一時刻のキー間を最小全域木（MST）で結ぶリンク線（N点に対し N-1 本）
 * - **Chord Halo**: 同一時刻のキー/ノート群の外側を薄く囲む Stroke 枠線（Convex Hull + margin）
 *
 * GameProfile の 15 キーや 3x5 配置をハードコードせず、渡された座標リストにのみ依存します。
 * また、毎フレームの onDraw 経路における不要なアロケーションを抑えるため、
 * Paint, Path, scratch buffer を再利用します。
 */
class ChordVisualRenderer(context: Context) {

    private val density: Float = context.resources.displayMetrics.density

    // 再利用 Paint
    private val linkPaint = Paint().apply {
        style = Paint.Style.STROKE
        isAntiAlias = true
        strokeWidth = 2.0f * density
        strokeCap = Paint.Cap.ROUND
    }

    private val haloPaint = Paint().apply {
        style = Paint.Style.STROKE
        isAntiAlias = true
        strokeWidth = 1.5f * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    // 再利用 Path
    private val haloPath = Path()

    // 再利用 scratch buffer
    private val tempChordPoints = ArrayList<ChordPoint>()
    private val tempCenterPoints = ArrayList<GeometryPoint>()

    companion object {
        // Chord Link のアルファ値（控えめかつ視認性のある半透明ライン）
        private const val LINK_ALPHA = 150
        // Chord Halo のアルファ値（Chord Link より目立たない薄い外周線）
        private const val HALO_ALPHA = 90
        /** Falling Notes OFF 時に直近和音を表示する先行時間（ミリ秒、既存挙動を維持） */
        const val DEFAULT_CHORD_VISIBILITY_LEAD_TIME_MS = 200L
    }

    /**
     * 和音リンクおよび和音ハローを描画する。
     *
     * 描画レイヤー順序に従い、ノートや Approach Circle の背面に描画します。
     * 遠い未来の ChordGroup から先に描画し、直近が前面に来るよう timeMs 降順で描画します。
     */
    fun drawChords(
        canvas: Canvas,
        frame: GuideFrame,
        keyPixelCenters: List<PointF>,
        keyRadiusPx: Float,
        guideColor: Int,
        showChordLinks: Boolean,
        showChordHalos: Boolean,
        showFallingNotes: Boolean,
        viewHeight: Int,
        chordVisibilityLeadTimeMs: Long = DEFAULT_CHORD_VISIBILITY_LEAD_TIME_MS
    ) {
        if (!showChordLinks && !showChordHalos) return
        if (frame.chordGroups.isEmpty()) return

        // ガイド色から RGB を抽出し、Chord 専用の alpha を適用
        val gr = Color.red(guideColor)
        val gg = Color.green(guideColor)
        val gb = Color.blue(guideColor)

        if (showChordLinks) {
            linkPaint.color = Color.argb(LINK_ALPHA, gr, gg, gb)
        }
        if (showChordHalos) {
            haloPaint.color = Color.argb(HALO_ALPHA, gr, gg, gb)
        }

        val fallDistancePx = viewHeight * FallingNoteCalculator.DEFAULT_FALL_DISTANCE_RATIO
        val haloMarginPx = 4.0f * density

        // frame.chordGroups は NoteScheduler 側で timeMs 降順（遠い未来 -> 直近）にソート済み。
        // そのまま順次描画することで、遠い未来が背面に、直近が最前面に重なる。
        for (chord in frame.chordGroups) {
            // Falling Notes OFF 時は画面ノイズを防ぐため、chordVisibilityLeadTimeMs 範囲内（直近）のみを描画
            if (!showFallingNotes) {
                val remainingTime = chord.timeMs - frame.currentTimeMs
                if (remainingTime > chordVisibilityLeadTimeMs) continue
            }

            tempChordPoints.clear()
            tempCenterPoints.clear()

            val visualRadius: Float

            if (showFallingNotes) {
                // Falling Notes ON: 落下中ノートの現在座標に追従
                val fallingProgress = FallingNoteCalculator.calculateProgress(
                    eventTimeMs = chord.timeMs,
                    currentTimeMs = frame.currentTimeMs,
                    noteLeadTimeMs = frame.noteLeadTimeMs
                )
                if (!FallingNoteCalculator.shouldDraw(fallingProgress)) continue

                // 落下ノートの半径
                visualRadius = keyRadiusPx * 0.65f + haloMarginPx

                for (key in chord.keys) {
                    if (key !in keyPixelCenters.indices) continue
                    val target = keyPixelCenters[key]
                    val currentX = target.x
                    val currentY = FallingNoteCalculator.calculateY(target.y, fallDistancePx, fallingProgress)

                    tempChordPoints.add(ChordPoint(key = key, x = currentX, y = currentY))
                    tempCenterPoints.add(GeometryPoint(x = currentX, y = currentY))
                }
            } else {
                // Falling Notes OFF: FitProfile のキー中心座標に固定
                visualRadius = keyRadiusPx + haloMarginPx

                for (key in chord.keys) {
                    if (key !in keyPixelCenters.indices) continue
                    val center = keyPixelCenters[key]

                    tempChordPoints.add(ChordPoint(key = key, x = center.x, y = center.y))
                    tempCenterPoints.add(GeometryPoint(x = center.x, y = center.y))
                }
            }

            // 描画可能な有効座標が2点以上ある場合のみ描画
            if (tempChordPoints.size < 2) continue

            // 1. Chord Halo 描画（最背面）
            if (showChordHalos) {
                val supportPoints = ChordGeometry.generateSupportPoints(tempCenterPoints, visualRadius)
                val hull = ChordGeometry.buildConvexHull(supportPoints)
                if (hull.size >= 2) {
                    haloPath.reset()
                    haloPath.moveTo(hull[0].x, hull[0].y)
                    for (i in 1 until hull.size) {
                        haloPath.lineTo(hull[i].x, hull[i].y)
                    }
                    haloPath.close()
                    canvas.drawPath(haloPath, haloPaint)
                }
            }

            // 2. Chord Link 描画（Halo の上、Circle/Falling Note の下）
            if (showChordLinks) {
                val edges = ChordGeometry.buildMinimumSpanningTree(tempChordPoints)
                for (edge in edges) {
                    canvas.drawLine(
                        edge.fromPoint.x,
                        edge.fromPoint.y,
                        edge.toPoint.x,
                        edge.toPoint.y,
                        linkPaint
                    )
                }
            }
        }
    }
}
