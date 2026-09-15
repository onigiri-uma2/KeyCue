package com.onigiri.keycue.overlay.render

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import com.onigiri.keycue.model.FitProfile
import com.onigiri.keycue.playback.GuideFrame
import kotlin.math.min

/**
 * ジャストタイミング発光演出および音ゲー風アプローチサークルの描画を担当するレンダラー。
 *
 * 以下の演出を描画します:
 * - **インパクトフラッシュ**: ジャスト打鍵瞬間の白〜高輝度ゴールドによるキー内部の発光。
 * - **ジャスト二重リング**: 打鍵キー外周の太枠メインリングおよびソフトグロー発光。
 * - **バースト・リップル波紋**: 打鍵直後から約200msかけて外側へ弾け広がる衝撃波アニメーション。
 * - **アプローチサークル**: 先読み時間に応じて外側からキー境界へ縮小していくタイミングガイド円。
 *
 * Paint等の描画オブジェクトは事前に確保し、描画ループ内でのアロケーションを完全に回避しています。
 */
class TimingEffectRenderer(
    private val density: Float
) {
    // デフォルト・シンプルなジャストリング用 Paint (showJustEffect = false 時)
    private val justRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4.0f * density
        color = Color.argb(240, 255, 235, 120) // 明るいゴールドのジャストリング
    }

    // --- ジャストタイミング強化演出用 Paint (showJustEffect = true 時) ---
    // キー内部の強烈なインパクトフラッシュ（白〜高輝度ゴールド塗りつぶし）
    private val justFlashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    // ジャストメインリング（太い高輝度ゴールド外周線）
    private val justMainRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5.5f * density
        color = Color.argb(255, 255, 240, 140)
    }

    // ジャスト外周グローリング（柔らかい光彩リング）
    private val justGlowRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
        color = Color.argb(160, 255, 215, 90)
    }

    // バースト・リップル（外側へ弾け広がる衝撃波紋）
    private val justRipplePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    // 各キーの直近ジャスト突入時刻 (SystemClock.uptimeMillis) と直前フレーム状態
    private val lastJustTriggerTimes = LongArray(FitProfile.KEY_COUNT) { -1L }
    private val wasJustActive = BooleanArray(FitProfile.KEY_COUNT) { false }

    // --- アプローチサークル（縮小タイミング円）用 Paint ---
    private val approachCirclePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
    }

    /**
     * エフェクト状態をリセットする。
     */
    fun reset() {
        lastJustTriggerTimes.fill(-1L)
        wasJustActive.fill(false)
    }

    /**
     * 各キーのジャスト演出を描画する。
     * @return リップル波紋等のアニメーションが継続中の場合 true
     */
    fun drawKeyJustEffect(
        canvas: Canvas,
        keyIndex: Int,
        center: PointF,
        keyRadiusPx: Float,
        isJust: Boolean,
        now: Long,
        showEnhancedEffect: Boolean
    ): Boolean {
        // ジャスト突入（立ち上がり）エッジの検知
        if (isJust && !wasJustActive[keyIndex]) {
            lastJustTriggerTimes[keyIndex] = now
        }
        wasJustActive[keyIndex] = isJust

        val triggerTime = lastJustTriggerTimes[keyIndex]
        val elapsed = if (triggerTime > 0L) now - triggerTime else Long.MAX_VALUE
        var hasActiveAnimation = false

        if (showEnhancedEffect) {
            // A. キー内部のインパクトフラッシュ（白〜高輝度ゴールド発光）
            if (isJust || elapsed in 0L..120L) {
                val flashProgress = (elapsed / 120f).coerceIn(0f, 1f)
                val flashAlpha = if (isJust && elapsed > 120L) {
                    180
                } else {
                    ((1f - flashProgress) * 230f).toInt().coerceIn(0, 255)
                }
                justFlashPaint.color = Color.argb(flashAlpha, 255, 248, 200)
                canvas.drawCircle(center.x, center.y, keyRadiusPx, justFlashPaint)
            }

            // B. ジャスト二重リング（太いメインリング ＋ 外側グローリング）
            if (isJust) {
                canvas.drawCircle(center.x, center.y, keyRadiusPx * 1.05f, justMainRingPaint)
                canvas.drawCircle(center.x, center.y, keyRadiusPx * 1.16f, justGlowRingPaint)
            }

            // C. バースト・リップル（外側へ弾け広がる衝撃波紋、約200ms）
            if (elapsed in 0L..200L) {
                val rippleProgress = (elapsed / 200f).coerceIn(0f, 1f)
                val rippleRadius = keyRadiusPx * (1.05f + rippleProgress * 0.65f)
                val rippleAlpha = ((1f - rippleProgress) * 240f).toInt().coerceIn(0, 255)
                val rippleStrokeWidth = (4.5f * (1f - rippleProgress * 0.65f)) * density
                justRipplePaint.strokeWidth = rippleStrokeWidth
                justRipplePaint.color = Color.argb(rippleAlpha, 255, 250, 190)
                canvas.drawCircle(center.x, center.y, rippleRadius, justRipplePaint)
                hasActiveAnimation = true
            }
        } else {
            // showEnhancedEffect = false: シンプルな従来のジャストリング
            if (isJust) {
                canvas.drawCircle(center.x, center.y, keyRadiusPx * 1.05f, justRingPaint)
            }
        }

        return hasActiveAnimation
    }

    /**
     * 各キーの縮小アプローチサークルを描画する。
     * - progress 0.0 -> 半径 約2.5倍
     * - progress 1.0 -> 半径 1.0倍（ガイド円とジャスト一致）
     * - alpha: 35 + progress * 220
     */
    fun drawApproachCircles(
        canvas: Canvas,
        frame: GuideFrame,
        keyPixelCenters: List<PointF>,
        keyRadiusPx: Float,
        guideColor: Int
    ) {
        val count = min(keyPixelCenters.size, frame.keyHighlightProgress.size)
        val gr = Color.red(guideColor)
        val gg = Color.green(guideColor)
        val gb = Color.blue(guideColor)

        for (i in 0 until count) {
            val progress = frame.keyHighlightProgress[i]
            if (progress < 0f) continue

            val center = keyPixelCenters[i]
            val approachRadius = keyRadiusPx * (1f + (1f - progress) * 1.5f)
            val alpha = (35f + progress * 220f).toInt().coerceIn(0, 255)

            approachCirclePaint.color = Color.argb(alpha, gr, gg, gb)
            approachCirclePaint.strokeWidth = (2.0f + progress * 1.5f) * density
            canvas.drawCircle(center.x, center.y, approachRadius, approachCirclePaint)
        }
    }
}
