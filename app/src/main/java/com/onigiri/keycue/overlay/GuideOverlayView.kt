package com.onigiri.keycue.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Typeface
import android.view.View
import com.onigiri.keycue.model.FitProfile
import com.onigiri.keycue.model.VisualConfig
import com.onigiri.keycue.overlay.render.ChordVisualRenderer
import com.onigiri.keycue.overlay.render.FallingNotesRenderer
import com.onigiri.keycue.overlay.render.TimingEffectRenderer
import com.onigiri.keycue.playback.GuideFrame
import kotlin.math.min

/**
 * ゲーム画面上にキーの演奏ガイドおよび落下ノーツをタッチ透過で重ねて表示する全画面オーバーレイView。
 *
 * 設計方針:
 * - **タッチ完全透過**: 演奏操作を妨げないようタッチイベントは一切処理せず、背後のゲームアプリへ透過させます。
 * - **単一責任（描画専用）**: 再生時計やサービスを直接参照せず、[GuideFrame] および [VisualConfig] を受け取って画面を描画します。
 * - **レンダラー委譲**: 落下ノーツ描画は [FallingNotesRenderer]、ジャスト演出等は [TimingEffectRenderer]、和音リンク/ハローは [ChordVisualRenderer] に委譲し保守性を確保しています。
 * - **ゼロアロケーション描画**: 60fps以上の滑らかな描画を維持するため、Paint等の描画オブジェクトは事前に確保し、onDraw 内でのメモリアロケーションを完全に回避しています。
 */
@SuppressLint("ViewConstructor")
class GuideOverlayView(
    context: Context,
    private var fitProfile: FitProfile = FitProfile.createDefaultTestProfile(),
    private var visualConfig: VisualConfig = VisualConfig()
) : View(context) {

    private val density = context.resources.displayMetrics.density

    // --- 専用レンダラー ---
    private val fallingNotesRenderer = FallingNotesRenderer(density)
    private val timingEffectRenderer = TimingEffectRenderer(density)
    private val chordVisualRenderer = ChordVisualRenderer(context)

    // --- キー基本描画用 Paint ---
    private val circleStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
    }

    private val circleFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(220, 255, 255, 255)
        textSize = android.util.TypedValue.applyDimension(
            android.util.TypedValue.COMPLEX_UNIT_SP,
            14f,
            context.resources.displayMetrics
        )
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    // --- 事前ハイライト用 Paint ---
    private val highlightFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(90, 255, 220, 100) // 薄い琥珀色のハイライト
    }

    // --- カウントダウンテキスト用 Paint ---
    private val countdownPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(240, 255, 255, 255)
        textSize = android.util.TypedValue.applyDimension(
            android.util.TypedValue.COMPLEX_UNIT_SP,
            56f,
            context.resources.displayMetrics
        )
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        setShadowLayer(8f * density, 0f, 0f, Color.argb(180, 0, 0, 0))
    }

    // 計算済みピクセル座標のキャッシュ
    private val keyPixelCenters = ArrayList<PointF>(fitProfile.keyCenters.size)
    private var keyRadiusPx: Float = 0f
    private var textYOffset: Float = 0f

    // 現在描画対象のフレーム
    private var currentFrame: GuideFrame? = null

    init {
        setBackgroundColor(Color.TRANSPARENT)
        applyVisualConfigPaints()
        recalculateTextOffset()
    }

    /**
     * 描画フレームを更新し、Viewを再描画する。
     */
    fun renderFrame(frame: GuideFrame) {
        currentFrame = frame
        invalidate()
    }

    /**
     * FitProfileを更新し、再計算・再描画を行う。
     */
    fun updateFitProfile(newProfile: FitProfile) {
        fitProfile = newProfile
        recalculateKeyPositions(width, height)
        invalidate()
    }

    /**
     * VisualConfig（見た目設定）を更新し、再描画を行う。
     */
    fun updateVisualConfig(newConfig: VisualConfig) {
        visualConfig = newConfig
        applyVisualConfigPaints()
        recalculateKeyPositions(width, height)
        invalidate()
    }

    private fun applyVisualConfigPaints() {
        val gc = visualConfig.guideColor
        val gr = Color.red(gc)
        val gg = Color.green(gc)
        val gb = Color.blue(gc)
        circleStrokePaint.color = Color.argb(180, gr, gg, gb)
        circleFillPaint.color = Color.argb(35, gr, gg, gb)

        fallingNotesRenderer.updateColors(
            topColor = visualConfig.noteColorTop,
            middleColor = visualConfig.noteColorMiddle,
            bottomColor = visualConfig.noteColorBottom
        )
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        recalculateKeyPositions(w, h)
    }

    private fun recalculateTextOffset() {
        val fontMetrics = textPaint.fontMetrics
        textYOffset = (fontMetrics.descent - fontMetrics.ascent) / 2f - fontMetrics.descent
    }

    private fun recalculateKeyPositions(viewWidth: Int, viewHeight: Int) {
        keyPixelCenters.clear()
        if (viewWidth <= 0 || viewHeight <= 0) return

        val baseDimension = min(viewWidth, viewHeight)
        // VisualConfigのguideRadiusRatioをSingle Source of Truthとして使用
        // 最小半径は8dp程度まで緩和
        keyRadiusPx = (baseDimension * visualConfig.guideRadiusRatio).coerceAtLeast(8f * density)

        for (point in fitProfile.keyCenters) {
            val (px, py) = point.toPixel(viewWidth, viewHeight)
            keyPixelCenters.add(PointF(px, py))
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (keyPixelCenters.isEmpty()) return
        val frame = currentFrame

        val now = android.os.SystemClock.uptimeMillis()
        var hasActiveRipple = false

        // 停止・完了時等の空フレーム検知でエフェクト状態をリセット
        val isFrameEmpty = frame == null ||
            (frame.upcomingNotes.isEmpty() && frame.highlightedKeys.isEmpty() && frame.justKeys.isEmpty())
        if (isFrameEmpty) {
            timingEffectRenderer.reset()
        }

        // 1. 各キーの基本形状 & 事前ハイライト & ジャスト演出描画
        val showEffect = visualConfig.showJustEffect
        for (i in 0 until keyPixelCenters.size) {
            val center = keyPixelCenters[i]
            val isJust = frame?.justKeys?.contains(i) == true

            // 事前ハイライト背景
            val isHighlighted = frame?.highlightedKeys?.contains(i) == true
            if (isHighlighted) {
                canvas.drawCircle(center.x, center.y, keyRadiusPx * 1.15f, highlightFillPaint)
            }

            // 基本キー背景 & 枠線
            canvas.drawCircle(center.x, center.y, keyRadiusPx, circleFillPaint)
            canvas.drawCircle(center.x, center.y, keyRadiusPx, circleStrokePaint)

            // ジャスト演出（レンダラーへ委譲）
            val active = timingEffectRenderer.drawKeyJustEffect(
                canvas = canvas,
                keyIndex = i,
                center = center,
                keyRadiusPx = keyRadiusPx,
                isJust = isJust,
                now = now,
                showEnhancedEffect = showEffect
            )
            if (active) {
                hasActiveRipple = true
            }

            // キー番号（設定で表示が有効な場合のみ）
            if (visualConfig.showKeyNumbers) {
                canvas.drawText(
                    i.toString(),
                    center.x,
                    center.y + textYOffset,
                    textPaint
                )
            }
        }

        // リップル拡散アニメーション中の継続再描画
        if (hasActiveRipple) {
            postInvalidateOnAnimation()
        }

        // 2. 和音リンク & 和音ハロー描画（レンダラーへ委譲、Approach Circle / Falling Notes の背面）
        if ((visualConfig.showChordLinks || visualConfig.showChordHalos) && frame != null) {
            chordVisualRenderer.drawChords(
                canvas = canvas,
                frame = frame,
                keyPixelCenters = keyPixelCenters,
                keyRadiusPx = keyRadiusPx,
                guideColor = visualConfig.guideColor,
                showChordLinks = visualConfig.showChordLinks,
                showChordHalos = visualConfig.showChordHalos,
                showFallingNotes = visualConfig.showFallingNotes,
                viewHeight = height,
                highlightTimeMs = frame.highlightTimeMs
            )
        }

        // 3. 音ゲー風アプローチサークル（縮小タイミング円）描画（レンダラーへ委譲）
        if (visualConfig.showApproachCircles && frame != null) {
            timingEffectRenderer.drawApproachCircles(
                canvas = canvas,
                frame = frame,
                keyPixelCenters = keyPixelCenters,
                keyRadiusPx = keyRadiusPx,
                guideColor = visualConfig.guideColor,
                showRepeatCountBadge = visualConfig.showRepeatCountBadge
            )
        }

        // 3. 落下ノーツ描画（レンダラーへ委譲）
        if (visualConfig.showFallingNotes && frame != null && frame.upcomingNotes.isNotEmpty()) {
            fallingNotesRenderer.drawFallingNotes(
                canvas = canvas,
                frame = frame,
                keyPixelCenters = keyPixelCenters,
                keyRadiusPx = keyRadiusPx,
                viewHeight = height
            )
        }

        // 4. カウントダウン描画 ("3", "2", "1", "START" 等)
        val countdown = frame?.countdownText
        if (!countdown.isNullOrEmpty()) {
            val centerX = width / 2f
            val centerY = height * 0.40f
            canvas.drawText(countdown, centerX, centerY, countdownPaint)
        }
    }
}
