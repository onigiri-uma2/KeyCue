package com.onigiri.keycue.overlay.render

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import com.onigiri.keycue.playback.FallingNoteCalculator
import com.onigiri.keycue.playback.GuideFrame

/**
 * 演奏ガイド上へ降下してくるノート（落下ノート）の描画を担当するレンダラー。
 *
 * キーの位置（段）に応じた幾何学形状で描画します:
 * - 上段キー (Key 0..4): ○ (円形)
 * - 中段キー (Key 5..9): □ (正方形)
 * - 下段キー (Key 10..14): △ (上向き正三角形)
 *
 * 描画パフォーマンスを最大化するため、Paint や Path、RectF 等の描画オブジェクトを
 * インスタンス生成時に保持し、描画ループ内でのメモリアロケーションを防止しています。
 */
class FallingNotesRenderer(
    private val density: Float
) {
    private val noteFillPaintTop = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val noteFillPaintMiddle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val noteFillPaintBottom = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val noteStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.0f * density
        color = Color.argb(255, 255, 255, 255)
    }

    private val tempRect = RectF()
    private val tempPath = Path()

    /**
     * ノートの色設定を更新する。
     */
    fun updateColors(topColor: Int, middleColor: Int, bottomColor: Int) {
        noteFillPaintTop.color = Color.argb(
            210,
            Color.red(topColor),
            Color.green(topColor),
            Color.blue(topColor)
        )
        noteFillPaintMiddle.color = Color.argb(
            210,
            Color.red(middleColor),
            Color.green(middleColor),
            Color.blue(middleColor)
        )
        noteFillPaintBottom.color = Color.argb(
            210,
            Color.red(bottomColor),
            Color.green(bottomColor),
            Color.blue(bottomColor)
        )
    }

    /**
     * 落下ノート群を描画する。
     */
    fun drawFallingNotes(
        canvas: Canvas,
        frame: GuideFrame,
        keyPixelCenters: List<PointF>,
        keyRadiusPx: Float,
        viewHeight: Int
    ) {
        val fallDistancePx = viewHeight * FallingNoteCalculator.DEFAULT_FALL_DISTANCE_RATIO
        val noteRadius = keyRadiusPx * 0.65f

        for (note in frame.upcomingNotes) {
            if (note.key !in keyPixelCenters.indices) continue

            val target = keyPixelCenters[note.key]
            val progress = FallingNoteCalculator.calculateProgress(
                eventTimeMs = note.timeMs,
                currentTimeMs = frame.currentTimeMs,
                noteLeadTimeMs = frame.noteLeadTimeMs
            )

            if (!FallingNoteCalculator.shouldDraw(progress)) continue

            val currentX = target.x
            val currentY = FallingNoteCalculator.calculateY(target.y, fallDistancePx, progress)
            val row = FallingNoteCalculator.getRow(note.key)

            val fillPaint = when (row) {
                0 -> noteFillPaintTop
                1 -> noteFillPaintMiddle
                else -> noteFillPaintBottom
            }

            when (row) {
                0 -> {
                    // 上段 key 0..4 -> ○ (Circle)
                    canvas.drawCircle(currentX, currentY, noteRadius, fillPaint)
                    canvas.drawCircle(currentX, currentY, noteRadius, noteStrokePaint)
                }
                1 -> {
                    // 中段 key 5..9 -> □ (Square)
                    tempRect.set(
                        currentX - noteRadius,
                        currentY - noteRadius,
                        currentX + noteRadius,
                        currentY + noteRadius
                    )
                    canvas.drawRect(tempRect, fillPaint)
                    canvas.drawRect(tempRect, noteStrokePaint)
                }
                2 -> {
                    // 下段 key 10..14 -> △ (Triangle)
                    tempPath.reset()
                    tempPath.moveTo(currentX, currentY - noteRadius) // 頂点
                    tempPath.lineTo(currentX - noteRadius, currentY + noteRadius) // 左下
                    tempPath.lineTo(currentX + noteRadius, currentY + noteRadius) // 右下
                    tempPath.close()

                    canvas.drawPath(tempPath, fillPaint)
                    canvas.drawPath(tempPath, noteStrokePaint)
                }
            }
        }
    }
}
