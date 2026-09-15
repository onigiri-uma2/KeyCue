package com.onigiri.keycue.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.Button
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.onigiri.keycue.fitting.Corner
import com.onigiri.keycue.fitting.GridFitter
import com.onigiri.keycue.model.FitProfile
import com.onigiri.keycue.model.NormalizedPoint
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

/**
 * ゲーム画面上で15キー位置を直接微調整するための全画面オーバーレイView。
 *
 * 主な機能とUI仕様:
 * 1. **全画面タッチ遮断**: 下層のゲーム画面へのタッチ伝播を完全に遮断し、誤操作を防ぎます。
 * 2. **4隅ハンドル微調整**: 4隅（Corner）の指ドラッグおよび十字キー（nudge）で位置を直感的に微調整。
 * 3. **4点個別調整モード**:
 *    - OFF (矩形連動モード): 角を動かすと対向する軸も連動し、長方形の比率・平行度を維持。
 *    - ON (個別調整モード): 選択したCornerのみ独立移動し、台形歪みや斜め配置に対応。
 * 4. **バイリニア補間リアルタイム追従**: 4隅の移動に連動して内部の11キーが即座に再計算・再描画されます。
 * 5. **保存 / キャンセル**: 確定操作でプロファイルを永続化し、キャンセル時は元の位置へ戻します。
 */
@SuppressLint("ViewConstructor")
class FittingOverlayView(
    context: Context,
    initialProfile: FitProfile,
    private val onSave: (FitProfile) -> Unit,
    private val onCancel: () -> Unit
) : FrameLayout(context) {

    private val density = context.resources.displayMetrics.density
    private val gridFitter = GridFitter()

    private val cornerController: FittingCornerController

    internal val topLeft: NormalizedPoint get() = cornerController.topLeft
    internal val topRight: NormalizedPoint get() = cornerController.topRight
    internal val bottomLeft: NormalizedPoint get() = cornerController.bottomLeft
    internal val bottomRight: NormalizedPoint get() = cornerController.bottomRight

    private var currentProfile: FitProfile
    internal var selectedCorner: Corner = Corner.TOP_LEFT
        private set

    /** 4点個別調整モード（true: 独立移動, false: 矩形連動トリミング移動） */
    internal var isIndividualMode: Boolean
        get() = cornerController.isIndividualMode
        set(value) { cornerController.isIndividualMode = value }

    // キャッシュ用ピクセル座標
    private val keyPixelCenters = ArrayList<PointF>(FitProfile.KEY_COUNT)
    private var keyRadiusPx: Float = 0f

    // 描画用ビュー
    private val canvasView: FittingCanvasView

    // 操作用UI
    private val cornerButtons = ArrayList<Button>(Corner.entries.size)

    init {
        // 全画面背景は薄い暗幕（ゲーム画面の視認性を保ちつつオーバーレイであるとわかる色）
        setBackgroundColor(Color.argb(40, 0, 0, 0))

        val centers = initialProfile.keyCenters
        cornerController = FittingCornerController(
            initialTopLeft = centers.getOrElse(Corner.TOP_LEFT.keyIndex) { NormalizedPoint(0.20f, 0.65f) },
            initialTopRight = centers.getOrElse(Corner.TOP_RIGHT.keyIndex) { NormalizedPoint(0.80f, 0.65f) },
            initialBottomLeft = centers.getOrElse(Corner.BOTTOM_LEFT.keyIndex) { NormalizedPoint(0.20f, 0.85f) },
            initialBottomRight = centers.getOrElse(Corner.BOTTOM_RIGHT.keyIndex) { NormalizedPoint(0.80f, 0.85f) },
            isIndividualMode = false
        )
        currentProfile = initialProfile

        // 1. キー＆ハンドル描画 Canvas View
        canvasView = FittingCanvasView(context)
        addView(canvasView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        // 2. 下部コントロールパネル（十分な幅を確保して見切れを防止）
        val controlPanel = createControlPanel()
        val screenWidth = resources.displayMetrics.widthPixels
        val desiredPanelWidth = min(dpToPx(380), (screenWidth * 0.90f).toInt())
        val panelLp = LayoutParams(
            desiredPanelWidth,
            LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            bottomMargin = dpToPx(16)
        }
        addView(controlPanel, panelLp)

        recalculateGrid()
    }

    /**
     * 背後のゲーム画面へのタッチ伝播を確実に遮断する。
     */
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        return true
    }

    private fun recalculateGrid() {
        val interpolated = gridFitter.interpolateGridFromCorners(topLeft, topRight, bottomLeft, bottomRight)
        currentProfile = FitProfile(
            keyCenters = interpolated,
            keyRadiusRatio = currentProfile.keyRadiusRatio,
            landscape = currentProfile.landscape
        )
        updatePixelPositions()
        canvasView.invalidate()
    }

    private fun updatePixelPositions() {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val minDim = min(w, h)
        keyRadiusPx = minDim * currentProfile.keyRadiusRatio

        keyPixelCenters.clear()
        for (norm in currentProfile.keyCenters) {
            keyPixelCenters.add(PointF(norm.x * w, norm.y * h))
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updatePixelPositions()
    }

    /**
     * 対象コーナーを微動（nudge）する。
     */
    private fun nudgeSelectedCorner(dxNorm: Float, dyNorm: Float) {
        moveCornerDelta(selectedCorner, dxNorm, dyNorm)
    }

    /**
     * コーナーの座標に delta を加算する。
     */
    internal fun moveCornerDelta(corner: Corner, dxNorm: Float, dyNorm: Float) {
        cornerController.moveCornerDelta(corner, dxNorm, dyNorm)
        recalculateGrid()
    }

    internal fun selectCorner(corner: Corner) {
        selectedCorner = corner
        updateCornerButtonsState()
        canvasView.invalidate()
    }

    private fun updateCornerButtonsState() {
        for ((idx, corner) in Corner.entries.withIndex()) {
            val btn = cornerButtons.getOrNull(idx) ?: continue
            val isSelected = corner == selectedCorner
            btn.background = createRoundedDrawable(
                cornerRadiusDp = 8f,
                fillColor = if (isSelected) Color.parseColor("#1976D2") else Color.parseColor("#37474F"),
                strokeColor = if (isSelected) Color.parseColor("#FFD600") else Color.parseColor("#66FFFFFF"),
                strokeWidthDp = if (isSelected) 2.0f else 1.0f
            )
            btn.setTextColor(if (isSelected) Color.parseColor("#FFD600") else Color.WHITE)
        }
    }

    // --- 下部操作パネル生成 ---

    private fun createControlPanel(): View {
        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = createRoundedDrawable(
                cornerRadiusDp = 16f,
                fillColor = Color.parseColor("#E6181B26"),
                strokeColor = Color.parseColor("#66FFFFFF"),
                strokeWidthDp = 1.5f
            )
            elevation = dpToPx(12).toFloat()
            val padH = dpToPx(14)
            val padV = dpToPx(10)
            setPadding(padH, padV, padH, padV)
        }

        // タイトル＆ドラッグハンドル行
        val headerLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(4)
            }
        }
        val titleText = TextView(context).apply {
            text = "キー位置微調整"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        val hintText = TextView(context).apply {
            text = "ここをドラッグして小窓を移動"
            setTextColor(Color.parseColor("#90A4AE"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            gravity = Gravity.CENTER
        }
        headerLayout.addView(titleText)
        headerLayout.addView(hintText)
        panel.addView(headerLayout)

        // ヘッダーをドラッグして小窓全体を画面内で自由に移動可能にする
        var startTouchX = 0f
        var startTouchY = 0f
        var origTransX = 0f
        var origTransY = 0f
        var isDragging = false
        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

        headerLayout.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startTouchX = event.rawX
                    startTouchY = event.rawY
                    origTransX = panel.translationX
                    origTransY = panel.translationY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - startTouchX
                    val dy = event.rawY - startTouchY
                    if (!isDragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                        isDragging = true
                    }
                    if (isDragging) {
                        panel.translationX = origTransX + dx
                        panel.translationY = origTransY + dy
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isDragging = false
                    true
                }
                else -> false
            }
        }

        // 4点個別調整チェックボックス（デフォルト: OFF）
        val individualCheckbox = CheckBox(context).apply {
            text = "4点個別調整 (台形対応)"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            isChecked = isIndividualMode
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = dpToPx(6)
            }
            setOnCheckedChangeListener { _, isChecked ->
                isIndividualMode = isChecked
            }
        }
        panel.addView(individualCheckbox)

        // 1. コーナー選択タブ（4つ均等配置で見切れ防止）
        val tabRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            weightSum = 4f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(10)
            }
        }

        cornerButtons.clear()
        for ((i, corner) in Corner.entries.withIndex()) {
            val btn = Button(context).apply {
                text = corner.label
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                typeface = Typeface.DEFAULT_BOLD
                setPadding(dpToPx(4), 0, dpToPx(4), 0)
                layoutParams = LinearLayout.LayoutParams(0, dpToPx(36), 1f).apply {
                    if (i > 0) leftMargin = dpToPx(4)
                }
                setOnClickListener { selectCorner(corner) }
            }
            cornerButtons.add(btn)
            tabRow.addView(btn)
        }
        updateCornerButtonsState()
        panel.addView(tabRow)

        // 2. 十字微動キー（大きめで押しやすいサイズ）
        val dpadRow = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(10)
            }
        }

        val nudgeStep = 0.0015f

        // 上
        val upBtn = createDpadButton("▲") { nudgeSelectedCorner(0f, -nudgeStep) }
        dpadRow.addView(upBtn)

        // 左右
        val lrRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val leftBtn = createDpadButton("◀") { nudgeSelectedCorner(-nudgeStep, 0f) }
        val rightBtn = createDpadButton("▶") { nudgeSelectedCorner(nudgeStep, 0f) }
        lrRow.addView(leftBtn)
        val spacer = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(dpToPx(46), dpToPx(1))
        }
        lrRow.addView(spacer)
        lrRow.addView(rightBtn)
        dpadRow.addView(lrRow)

        // 下
        val downBtn = createDpadButton("▼") { nudgeSelectedCorner(0f, nudgeStep) }
        dpadRow.addView(downBtn)

        panel.addView(dpadRow)

        // 3. 確定 & キャンセルボタン（横幅フル活用で見切れ防止）
        val actionRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            weightSum = 2.3f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val cancelBtn = Button(context).apply {
            text = "キャンセル"
            setTextColor(Color.parseColor("#FFCDD2"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            typeface = Typeface.DEFAULT_BOLD
            background = createRoundedDrawable(
                cornerRadiusDp = 8f,
                fillColor = Color.parseColor("#5A1E1E"),
                strokeColor = Color.parseColor("#88FF5252"),
                strokeWidthDp = 1.0f
            )
            layoutParams = LinearLayout.LayoutParams(0, dpToPx(42), 1.0f).apply {
                rightMargin = dpToPx(8)
            }
            setOnClickListener { onCancel() }
        }

        val saveBtn = Button(context).apply {
            text = "確定して保存"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            typeface = Typeface.DEFAULT_BOLD
            background = createRoundedDrawable(
                cornerRadiusDp = 8f,
                fillColor = Color.parseColor("#2E7D32"),
                strokeColor = Color.parseColor("#81C784"),
                strokeWidthDp = 1.5f
            )
            layoutParams = LinearLayout.LayoutParams(0, dpToPx(42), 1.3f)
            setOnClickListener { onSave(currentProfile) }
        }

        actionRow.addView(cancelBtn)
        actionRow.addView(saveBtn)
        panel.addView(actionRow)

        return panel
    }

    private fun createDpadButton(label: String, onClick: () -> Unit): Button {
        return Button(context).apply {
            text = label
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.DEFAULT_BOLD
            background = createRoundedDrawable(
                cornerRadiusDp = 6f,
                fillColor = Color.parseColor("#455A64"),
                strokeColor = Color.parseColor("#90A4AE"),
                strokeWidthDp = 1.0f
            )
            setPadding(0, 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(dpToPx(46), dpToPx(38))
            setOnClickListener { onClick() }
        }
    }

    private fun createRoundedDrawable(
        cornerRadiusDp: Float,
        fillColor: Int,
        strokeColor: Int? = null,
        strokeWidthDp: Float = 1.0f
    ): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = cornerRadiusDp * density
            setColor(fillColor)
            if (strokeColor != null) {
                setStroke((strokeWidthDp * density).toInt(), strokeColor)
            }
        }
    }

    private fun dpToPx(dp: Int): Int = (dp * density).toInt()

    // --- 内部 Canvas 描画 & ハンドル直接ドラッグ View ---

    private inner class FittingCanvasView(context: Context) : View(context) {

        private val circleStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.5f * density
            color = Color.argb(200, 255, 255, 255)
        }

        private val circleFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.argb(40, 255, 255, 255)
        }

        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 13f, resources.displayMetrics)
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }

        private val handleFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.argb(190, 41, 121, 255) // 青
        }

        private val handleStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3.5f * density
            color = Color.argb(255, 0, 229, 255) // シアン
        }

        private val selectedHandleStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 4.5f * density
            color = Color.parseColor("#FFD600") // 黄色
        }

        // ドラッグ状態
        private var draggingCorner: Corner? = null
        private var lastX = 0f
        private var lastY = 0f
        private val touchRadiusPx = 36f * density

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            val x = event.x
            val y = event.y

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    // 4隅のドラッグハンドルのうち、タッチ座標から判定半径 (36dp) 以内にある最近接コーナーを探索
                    var foundCorner: Corner? = null

                    for (corner in Corner.entries) {
                        val pt = keyPixelCenters.getOrNull(corner.keyIndex) ?: continue
                        val dist = sqrt((x - pt.x) * (x - pt.x) + (y - pt.y) * (y - pt.y))
                        if (dist <= touchRadiusPx) {
                            foundCorner = corner
                            break
                        }
                    }

                    // ハンドルを掴んだ場合はドラッグ状態に入り、操作対象コーナーを選択
                    if (foundCorner != null) {
                        draggingCorner = foundCorner
                        selectCorner(foundCorner)
                        lastX = x
                        lastY = y
                        return true
                    }
                }

                MotionEvent.ACTION_MOVE -> {
                    val corner = draggingCorner
                    if (corner != null && width > 0 && height > 0) {
                        // ピクセル単位の移動量を View の幅・高さで正規化 (0.0〜1.0) して反映
                        val dx = (x - lastX) / width.toFloat()
                        val dy = (y - lastY) / height.toFloat()
                        moveCornerDelta(corner, dx, dy)
                        lastX = x
                        lastY = y
                        return true
                    }
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    draggingCorner = null
                }
            }
            return super.onTouchEvent(event)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            if (keyPixelCenters.size < FitProfile.KEY_COUNT) return

            // 1. 全15キーの描画
            for (i in 0 until FitProfile.KEY_COUNT) {
                val pt = keyPixelCenters[i]
                canvas.drawCircle(pt.x, pt.y, keyRadiusPx, circleFillPaint)
                canvas.drawCircle(pt.x, pt.y, keyRadiusPx, circleStrokePaint)

                // 番号 (0..14)
                val textY = pt.y - (textPaint.descent() + textPaint.ascent()) / 2f
                canvas.drawText(i.toString(), pt.x, textY, textPaint)
            }

            // 2. 4隅のドラッグハンドルの描画
            val handleRadius = 24f * density

            for (corner in Corner.entries) {
                val pt = keyPixelCenters.getOrNull(corner.keyIndex) ?: continue
                val isSelected = corner == selectedCorner

                // ハンドル背景
                canvas.drawCircle(pt.x, pt.y, handleRadius, handleFillPaint)

                // ハンドル外枠（選択時は黄色太枠、それ以外はシアン）
                val strokePaint = if (isSelected) selectedHandleStrokePaint else handleStrokePaint
                canvas.drawCircle(pt.x, pt.y, handleRadius, strokePaint)

                // 番号強調描画
                val textY = pt.y - (textPaint.descent() + textPaint.ascent()) / 2f
                canvas.drawText(corner.keyIndex.toString(), pt.x, textY, textPaint)
            }
        }
    }
}
