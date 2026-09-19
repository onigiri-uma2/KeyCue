package com.onigiri.keycue.overlay.render

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import com.onigiri.keycue.playback.FallingNoteCalculator
import com.onigiri.keycue.playback.GuideFrame
import kotlin.math.roundToInt

/**
 * 和音・同時押しキーの視覚的グルーピング（Chord Link および Chord Halo）の描画を担当するレンダラー。
 *
 * - **Chord Link**: 同一時刻のキー間を最小全域木（MST）で結ぶリンク線（N点に対し N-1 本）
 * - **Chord Halo**: 同一時刻のキー/ノート群を薄い内部面と外周枠線で囲むグルーピング表現（Convex Hull + margin）
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
        strokeCap = Paint.Cap.ROUND
    }

    private val haloPaint = Paint().apply {
        style = Paint.Style.STROKE
        isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val haloFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    init {
        updateStyle(
            strokeWidthDp = com.onigiri.keycue.model.VisualConfig.DEFAULT_CHORD_STROKE_WIDTH_DP,
            strokeAlphaPercent = com.onigiri.keycue.model.VisualConfig.DEFAULT_CHORD_STROKE_ALPHA_PERCENT,
            haloFillAlphaPercent = com.onigiri.keycue.model.VisualConfig.DEFAULT_CHORD_HALO_FILL_ALPHA_PERCENT,
            guideColor = com.onigiri.keycue.model.VisualConfig.DEFAULT_GUIDE_COLOR
        )
    }

    // 和音幾何キャッシュ (OFF用 と ON用 を完全分離)
    private val staticGeometryCache = HashMap<List<Int>, CachedChordGeometry>()
    private val fallingGeometryCache = HashMap<List<Int>, CachedChordGeometry>()

    /**
     * キャッシュされた和音幾何情報（基準キー座標における Convex Hull Path および MST 描画線）。
     */
    class CachedChordGeometry(
        val haloPath: Path,
        val linkLines: FloatArray
    )

    /**
     * 幾何キャッシュを一括破棄する。
     * キー座標や半径、レイアウトが変更された際に呼び出します。
     * （線幅や濃さのスタイル変更時には呼び出さないこと）
     */
    fun clearCache() {
        staticGeometryCache.clear()
        fallingGeometryCache.clear()
        cacheHitCount = 0L
        cacheMissCount = 0L
    }

    /**
     * 和音リンク・ハローの描画スタイル（線幅、アルファ、基本色）を更新する。
     * 設定変更時や初期化時のみ呼び出し、Paintへ事前反映することで
     * onDraw Hot Path 内での計算・代入オーバーヘッドを排除します。
     */
    fun updateStyle(
        strokeWidthDp: Float,
        strokeAlphaPercent: Int,
        haloFillAlphaPercent: Int,
        guideColor: Int
    ) {
        val widthPx = strokeWidthDpToPx(strokeWidthDp, density)
        val strokeAlpha = alphaPercentToAlpha(strokeAlphaPercent)
        val fillAlpha = alphaPercentToAlpha(haloFillAlphaPercent)

        linkPaint.strokeWidth = widthPx
        haloPaint.strokeWidth = widthPx

        val gr = Color.red(guideColor)
        val gg = Color.green(guideColor)
        val gb = Color.blue(guideColor)

        linkPaint.color = Color.argb(strokeAlpha, gr, gg, gb)
        haloPaint.color = Color.argb(strokeAlpha, gr, gg, gb)
        haloFillPaint.color = Color.argb(fillAlpha, gr, gg, gb)
    }

    companion object {
        /**
         * dp単位の線幅をdensityに基づいてpxへ変換する純粋関数。
         */
        fun strokeWidthDpToPx(strokeWidthDp: Float, density: Float): Float = strokeWidthDp * density

        /**
         * パーセント表記(10..100)の濃さを0..255のアルファ値へ変換する純粋関数。
         */
        fun alphaPercentToAlpha(alphaPercent: Int): Int =
            (alphaPercent * 255f / 100f).roundToInt().coerceIn(0, 255)

        /**
         * Falling Notes OFF 時において、和音（Chord Link / Chord Halo）が未来側表示開始範囲にあるかを判定する。
         *
         * Falling Notes OFF 時は、Chord Link / Halo はキー上に表示する直近の和音ガイドとなるため、
         * タイミングサークル（Approach Circle）と同じ approachCircleLeadTimeMs を表示開始時間の基準として使用する。
         * Approach Circle 自体の表示 ON/OFF には依存しない。
         *
         * この関数は未来側の表示開始境界のみを判定し、打鍵後の表示終了仕様は変更しない。
         *
         * @param chordTimeMs 和音の打鍵目標時刻（ミリ秒）
         * @param currentTimeMs 現在の楽曲再生位置（ミリ秒）
         * @param approachCircleLeadTimeMs タイミングサークル先読み時間（ミリ秒）
         * @return 未来側表示開始範囲内であれば true
         */
        fun isChordVisibleWhenFallingNotesOff(
            chordTimeMs: Long,
            currentTimeMs: Long,
            approachCircleLeadTimeMs: Long
        ): Boolean {
            val remainingTimeMs = chordTimeMs - currentTimeMs
            return remainingTimeMs <= approachCircleLeadTimeMs
        }
    }

    // キャッシュヒット・ミスカウンター（DEBUG/テスト確認用）
    internal var cacheHitCount: Long = 0L
        private set
    internal var cacheMissCount: Long = 0L
        private set

    /**
     * 指定されたキー構成と幾何モードに対応する幾何キャッシュを取得、未生成なら生成してキャッシュする。
     *
     * 【Hot Path 最適化】
     * 渡される [keys] は [com.onigiri.keycue.playback.ChordGroup] の契約により、
     * すでに昇順・重複なし・immutable な canonical form であることが保証されています。
     * 毎フレームの distinct() や sorted() は行わず、そのままキャッシュキーとして利用します。
     */
    private fun getOrCreateGeometry(
        keys: List<Int>,
        isFalling: Boolean,
        keyPixelCenters: List<PointF>,
        keyRadiusPx: Float,
        haloMarginPx: Float
    ): CachedChordGeometry? {
        val cache = if (isFalling) fallingGeometryCache else staticGeometryCache
        val existing = cache[keys]
        if (existing != null) {
            cacheHitCount++
            return existing
        }
        cacheMissCount++

        val validPoints = ArrayList<ChordPoint>()
        val validCenters = ArrayList<GeometryPoint>()
        for (k in keys) {
            if (k in keyPixelCenters.indices) {
                val center = keyPixelCenters[k]
                validPoints.add(ChordPoint(key = k, x = center.x, y = center.y))
                validCenters.add(GeometryPoint(x = center.x, y = center.y))
            }
        }
        if (validPoints.size < 2) return null

        val visualRadius = if (isFalling) {
            keyRadiusPx * 0.65f + haloMarginPx
        } else {
            keyRadiusPx + haloMarginPx
        }

        // 1. Chord Halo 用 Convex Hull Path 構築
        val supportPoints = ChordGeometry.generateSupportPoints(validCenters, visualRadius)
        val hull = ChordGeometry.buildConvexHull(supportPoints)
        val path = Path()
        if (hull.size >= 2) {
            path.moveTo(hull[0].x, hull[0].y)
            for (i in 1 until hull.size) {
                path.lineTo(hull[i].x, hull[i].y)
            }
            path.close()
        }

        // 2. Chord Link 用 MST 線分配列 (drawLines 用 FloatArray) 構築
        val edges = ChordGeometry.buildMinimumSpanningTree(validPoints)
        val lines = FloatArray(edges.size * 4)
        var idx = 0
        for (edge in edges) {
            lines[idx++] = edge.fromPoint.x
            lines[idx++] = edge.fromPoint.y
            lines[idx++] = edge.toPoint.x
            lines[idx++] = edge.toPoint.y
        }

        val geometry = CachedChordGeometry(haloPath = path, linkLines = lines)
        cache[keys] = geometry
        return geometry
    }

    /**
     * 和音リンクおよび和音ハローを描画する。
     *
     * 描画レイヤー順序に従い、ノートや Approach Circle の背面に描画します。
     * 遠い未来の ChordGroup から先に描画し、直近が前面に来るよう timeMs 降順で描画します。
     * Paintの色・線幅・alphaは [updateStyle] で事前設定済みのため、Hot Path内での色計算は行いません。
     */
    fun drawChords(
        canvas: Canvas,
        frame: GuideFrame,
        keyPixelCenters: List<PointF>,
        keyRadiusPx: Float,
        showChordLinks: Boolean,
        showChordHalos: Boolean,
        showFallingNotes: Boolean,
        viewHeight: Int
    ) {
        if (!showChordLinks && !showChordHalos) return
        if (frame.chordGroups.isEmpty()) return

        val fallDistancePx = viewHeight * FallingNoteCalculator.DEFAULT_FALL_DISTANCE_RATIO
        val haloMarginPx = 4.0f * density

        // frame.chordGroups は NoteScheduler 側で timeMs 降順（遠い未来 -> 直近）にソート済み。
        // そのまま順次描画することで、遠い未来が背面に、直近が最前面に重なる。
        for (chord in frame.chordGroups) {
            // Falling Notes OFF 時はキー上の直近和音ガイドとして表示するため、
            // タイミングサークルと共通の時間基準 approachCircleLeadTimeMs を使用する。
            if (!showFallingNotes) {
                if (!isChordVisibleWhenFallingNotesOff(
                        chordTimeMs = chord.timeMs,
                        currentTimeMs = frame.currentTimeMs,
                        approachCircleLeadTimeMs = frame.approachCircleLeadTimeMs
                    )
                ) {
                    continue
                }

                val geometry = getOrCreateGeometry(
                    keys = chord.keys,
                    isFalling = false,
                    keyPixelCenters = keyPixelCenters,
                    keyRadiusPx = keyRadiusPx,
                    haloMarginPx = haloMarginPx
                ) ?: continue

                val hasHalo = !geometry.haloPath.isEmpty

                // 1. Halo Fill (Chord Halo ON 時、背面に描画)
                if (showChordHalos && hasHalo) {
                    canvas.drawPath(geometry.haloPath, haloFillPaint)
                }
                // 2. Halo Stroke (外周線: 既存順序維持)
                if (showChordHalos && hasHalo) {
                    canvas.drawPath(geometry.haloPath, haloPaint)
                }
                // 3. Chord Link (MST 線分: 既存順序維持)
                if (showChordLinks && geometry.linkLines.isNotEmpty()) {
                    canvas.drawLines(geometry.linkLines, linkPaint)
                }
            } else {
                // Falling Notes ON: 落下中ノートの現在座標に追従
                val fallingProgress = FallingNoteCalculator.calculateProgress(
                    eventTimeMs = chord.timeMs,
                    currentTimeMs = frame.currentTimeMs,
                    noteLeadTimeMs = frame.noteLeadTimeMs
                )
                if (!FallingNoteCalculator.shouldDraw(fallingProgress)) continue

                val geometry = getOrCreateGeometry(
                    keys = chord.keys,
                    isFalling = true,
                    keyPixelCenters = keyPixelCenters,
                    keyRadiusPx = keyRadiusPx,
                    haloMarginPx = haloMarginPx
                ) ?: continue

                val dY = -fallDistancePx * (1.0f - fallingProgress)

                canvas.save()
                canvas.translate(0f, dY)

                val hasHalo = !geometry.haloPath.isEmpty

                // 1. Halo Fill (Chord Halo ON 時、背面に描画)
                if (showChordHalos && hasHalo) {
                    canvas.drawPath(geometry.haloPath, haloFillPaint)
                }
                // 2. Halo Stroke (外周線: 既存順序維持)
                if (showChordHalos && hasHalo) {
                    canvas.drawPath(geometry.haloPath, haloPaint)
                }
                // 3. Chord Link (MST 線分: 既存順序維持)
                if (showChordLinks && geometry.linkLines.isNotEmpty()) {
                    canvas.drawLines(geometry.linkLines, linkPaint)
                }

                canvas.restore()
            }
        }
    }
}
