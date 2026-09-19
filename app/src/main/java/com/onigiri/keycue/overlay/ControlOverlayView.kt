package com.onigiri.keycue.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.onigiri.keycue.playback.TimeFormatter
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * ゲーム画面の最前面にフローティング表示される操作パネル用オーバーレイView。
 *
 * 主な機能とUI動作:
 * - **最小化 / 展開**:
 *   - 最小化状態: ドラッグ移動可能なコンパクトなフローティングアイコンを表示。
 *   - 展開状態: 楽曲情報、再生・一時停止・停止・リスタート、シークバー、再生速度・先読み調整、演奏ガイドトグル等を備えた操作パネルを表示。
 * - **ドラッグ移動**: アイコンやパネルのヘッダーをドラッグすることで画面内の任意位置へ移動可能。
 * - **位置の永続化**: 移動完了時に正規化座標（0.0〜1.0）をコールバック経由で通知し、画面回転後も相対位置を維持可能。
 */
@SuppressLint("ViewConstructor")
class ControlOverlayView(
    context: Context,
    private val windowManager: WindowManager,
    private val layoutParams: WindowManager.LayoutParams,
    private val callbacks: ControlOverlayCallbacks
) : FrameLayout(context) {

    /** 後方互換用セカンダリコンストラクタ */
    constructor(
        context: Context,
        windowManager: WindowManager,
        layoutParams: WindowManager.LayoutParams,
        onOpenApp: () -> Unit,
        onCloseOverlay: () -> Unit,
        onPositionChanged: (normX: Float, normY: Float, pixelX: Int, pixelY: Int) -> Unit,
        onSelectFile: () -> Unit = {},
        onToggleGuide: () -> Boolean = { false },
        isGuideShowing: () -> Boolean = { false },
        onPlayPause: () -> Unit = {},
        onStop: () -> Unit = {},
        onRestart: () -> Unit = {},
        onSeekBack: () -> Unit = {},
        onSeekForward: () -> Unit = {},
        onSpeedChange: (Float) -> Unit = {},
        onLeadTimeChange: (Long) -> Unit = {},
        onStartFitting: () -> Unit = {}
    ) : this(
        context = context,
        windowManager = windowManager,
        layoutParams = layoutParams,
        callbacks = ControlOverlayCallbacks(
            onOpenApp = onOpenApp,
            onCloseOverlay = onCloseOverlay,
            onPositionChanged = onPositionChanged,
            onSelectFile = onSelectFile,
            onToggleGuide = onToggleGuide,
            isGuideShowing = isGuideShowing,
            onPlayPause = onPlayPause,
            onStop = onStop,
            onRestart = onRestart,
            onSeekBack = onSeekBack,
            onSeekForward = onSeekForward,
            onSpeedChange = onSpeedChange,
            onLeadTimeChange = onLeadTimeChange,
            onStartFitting = onStartFitting
        )
    )

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var initialLayoutX = 0
    private var initialLayoutY = 0
    private var isDragging = false

    private var isExpanded = false

    // 再生速度・先読み時間の設定候補
    private val speedPresets = listOf(0.50f, 0.75f, 1.00f, 1.25f, 1.50f)
    private val leadTimePresets = listOf(300L, 400L, 500L, 700L, 1000L, 1500L)

    private var currentSpeed: Float = 1.0f
    private var currentLeadTimeMs: Long = 300L
    private var isPlayingState: Boolean = false

    // UIコンポーネント
    private val collapsedView: TextView
    private val expandedView: View
    private lateinit var songTitleText: TextView
    private lateinit var timeText: TextView
    private lateinit var playPauseButton: Button
    private lateinit var speedValueText: TextView
    private lateinit var leadTimeValueText: TextView
    private lateinit var guideToggleButton: Button

    init {
        // --- 1. COLLAPSED View (最小化時の [ ♪ ] / [ ⏸ ] / [ ▶ ] ボタン) ---
        val buttonSize = dpToPx(52)
        collapsedView = TextView(context).apply {
            text = "♪"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            gravity = Gravity.CENTER
            background = createCircleDrawable(
                fillColor = Color.parseColor("#E621212E"),
                strokeColor = Color.parseColor("#66FFFFFF"),
                strokeWidthDp = 1.5f
            )
            elevation = dpToPx(6).toFloat()
            layoutParams = LayoutParams(buttonSize, buttonSize).apply {
                gravity = Gravity.CENTER
            }
        }

        // --- 2. EXPANDED View (操作パネル: 縦幅が収まらない場合はスクロール可能) ---
        val contentLayout = buildExpandedContentLayout()

        expandedView = object : ScrollView(context) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                // 画面高さの85%を上限とし、横画面等で縦幅が収まらない場合にスクロール可能にする
                val screenHeight = resources.displayMetrics.heightPixels
                val maxAllowedHeight = (screenHeight * 0.85f).toInt()
                val heightMode = MeasureSpec.getMode(heightMeasureSpec)
                val heightSize = MeasureSpec.getSize(heightMeasureSpec)
                val newHeightSize = if (heightMode == MeasureSpec.UNSPECIFIED) {
                    maxAllowedHeight
                } else {
                    min(heightSize, maxAllowedHeight)
                }
                val newHeightSpec = MeasureSpec.makeMeasureSpec(newHeightSize, MeasureSpec.AT_MOST)
                super.onMeasure(widthMeasureSpec, newHeightSpec)
            }
        }.apply {
            background = createRoundedDrawable(
                cornerRadiusDp = 16f,
                fillColor = Color.parseColor("#F21E1E2E"),
                strokeColor = Color.parseColor("#44FFFFFF"),
                strokeWidthDp = 1.5f
            )
            elevation = dpToPx(8).toFloat()
            visibility = View.GONE
            isVerticalScrollBarEnabled = true
            addView(contentLayout, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            layoutParams = LayoutParams(dpToPx(205), LayoutParams.WRAP_CONTENT)
        }

        addView(collapsedView)
        addView(expandedView)
    }

    private fun buildExpandedContentLayout(): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dpToPx(12)
            setPadding(pad, pad, pad, pad)

            // タイトルバー
            addView(buildTitleBar())

            // 楽曲情報
            buildSongInfoSection().forEach { addView(it) }

            // 再生コントロール
            buildPlaybackControls().forEach { addView(it) }

            // 速度・先読み設定
            addView(buildSpeedControls())
            addView(buildLeadTimeControls())

            // アクションボタン群
            buildActionButtons().forEach { addView(it) }

            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
    }

    private fun buildTitleBar(): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(6)
            }

            val titleText = TextView(context).apply {
                text = "KeyCue"
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val collapseBtn = TextView(context).apply {
                text = "✕"
                setTextColor(Color.parseColor("#B0B0C0"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                gravity = Gravity.CENTER
                setPadding(dpToPx(6), dpToPx(2), dpToPx(6), dpToPx(2))
                setOnClickListener { collapse() }
            }

            addView(titleText)
            addView(collapseBtn)
        }
    }

    private fun buildSongInfoSection(): List<View> {
        songTitleText = TextView(context).apply {
            text = "未選択"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(2)
            }
        }

        timeText = TextView(context).apply {
            text = "00:00 / 00:00"
            setTextColor(Color.parseColor("#B0B0D0"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(8)
            }
        }

        return listOf(songTitleText, timeText)
    }

    private fun buildPlaybackControls(): List<View> {
        val playbackRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(6)
            }

            val seekBackBtn = createMiniButton("↶10", Color.parseColor("#37474F")) {
                callbacks.onSeekBack()
            }
            addView(seekBackBtn)
            addView(createHorizontalSpacer(4))

            playPauseButton = createMiniButton("▶", Color.parseColor("#2E7D32")) {
                callbacks.onPlayPause()
            }
            addView(playPauseButton)
            addView(createHorizontalSpacer(4))

            val seekFwdBtn = createMiniButton("↷10", Color.parseColor("#37474F")) {
                callbacks.onSeekForward()
            }
            addView(seekFwdBtn)
        }

        val subControlsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(8)
            }

            val stopBtn = createMiniButton("■ 停止", Color.parseColor("#C62828")) {
                callbacks.onStop()
            }
            addView(stopBtn)
            addView(createHorizontalSpacer(4))

            val restartBtn = createMiniButton("🔄 Restart", Color.parseColor("#455A64")) {
                callbacks.onRestart()
            }
            addView(restartBtn)
        }

        return listOf(playbackRow, subControlsRow)
    }

    private fun buildSpeedControls(): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(6)
            }

            val speedLabel = TextView(context).apply {
                text = "Speed"
                setTextColor(Color.parseColor("#CFD8DC"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                layoutParams = LinearLayout.LayoutParams(dpToPx(42), LinearLayout.LayoutParams.WRAP_CONTENT)
            }
            addView(speedLabel)

            speedValueText = TextView(context).apply {
                text = "100%"
                setTextColor(Color.parseColor("#90CAF9"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            addView(speedValueText)

            val speedMinusBtn = createSmallAdjustButton("－") {
                adjustSpeedStep(-1)
            }
            val speedPlusBtn = createSmallAdjustButton("＋") {
                adjustSpeedStep(+1)
            }
            addView(speedMinusBtn)
            addView(createHorizontalSpacer(4))
            addView(speedPlusBtn)
        }
    }

    private fun buildLeadTimeControls(): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(10)
            }

            val leadLabel = TextView(context).apply {
                text = "Lead"
                setTextColor(Color.parseColor("#CFD8DC"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                layoutParams = LinearLayout.LayoutParams(dpToPx(42), LinearLayout.LayoutParams.WRAP_CONTENT)
            }
            addView(leadLabel)

            leadTimeValueText = TextView(context).apply {
                text = "700ms"
                setTextColor(Color.parseColor("#80CBC4"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            addView(leadTimeValueText)

            val leadMinusBtn = createSmallAdjustButton("－") {
                adjustLeadTimeStep(-1)
            }
            val leadPlusBtn = createSmallAdjustButton("＋") {
                adjustLeadTimeStep(+1)
            }
            addView(leadMinusBtn)
            addView(createHorizontalSpacer(4))
            addView(leadPlusBtn)
        }
    }

    private fun buildActionButtons(): List<View> {
        val views = mutableListOf<View>()

        // 「楽曲を選択」ボタン
        val selectFileBtn = createActionButton(
            text = "楽曲を選択",
            bgColor = Color.parseColor("#4A148C"),
            textColor = Color.WHITE
        ) {
            collapse()
            callbacks.onSelectFile()
        }
        views.add(selectFileBtn)
        views.add(createSpacer(5))

        // 「最小化」ボタン
        val minimizeBtn = createActionButton(
            text = "最小化",
            bgColor = Color.parseColor("#37474F"),
            textColor = Color.WHITE
        ) {
            collapse()
        }
        views.add(minimizeBtn)
        views.add(createSpacer(5))

        // 「KeyCueを開く」ボタン
        val openBtn = createActionButton(
            text = "KeyCueを開く",
            bgColor = Color.parseColor("#3F51B5"),
            textColor = Color.WHITE
        ) {
            collapse()
            callbacks.onOpenApp()
        }
        views.add(openBtn)
        views.add(createSpacer(5))

        // 「位置微調整」ボタン
        val fittingBtn = createActionButton(
            text = "位置微調整",
            bgColor = Color.parseColor("#1565C0"),
            textColor = Color.WHITE
        ) {
            collapse()
            callbacks.onStartFitting()
        }
        views.add(fittingBtn)
        views.add(createSpacer(5))

        // 「Guide ON / OFF」トグルボタン
        guideToggleButton = createActionButton(
            text = if (callbacks.isGuideShowing()) "Guide OFF" else "Guide ON",
            bgColor = Color.parseColor("#00796B"),
            textColor = Color.WHITE
        ) {
            val isShowingNow = callbacks.onToggleGuide()
            updateGuideButtonText(isShowingNow)
        }
        views.add(guideToggleButton)
        views.add(createSpacer(5))

        // 「終了」ボタン
        val closeBtn = createActionButton(
            text = "終了",
            bgColor = Color.parseColor("#5A1E1E"),
            textColor = Color.parseColor("#FFCDD2")
        ) {
            callbacks.onCloseOverlay()
        }
        views.add(closeBtn)

        return views
    }

    private fun adjustSpeedStep(direction: Int) {
        val currentIndex = speedPresets.indexOfFirst { abs(it - currentSpeed) < 0.01f }
        val nextIndex = if (currentIndex >= 0) {
            (currentIndex + direction).coerceIn(0, speedPresets.size - 1)
        } else {
            if (direction > 0) speedPresets.indexOfFirst { it > currentSpeed }.coerceAtLeast(0)
            else speedPresets.indexOfLast { it < currentSpeed }.coerceAtLeast(0)
        }
        val target = speedPresets[nextIndex]
        callbacks.onSpeedChange(target)
    }

    private fun adjustLeadTimeStep(direction: Int) {
        val currentIndex = leadTimePresets.indexOfFirst { it == currentLeadTimeMs }
        val nextIndex = if (currentIndex >= 0) {
            (currentIndex + direction).coerceIn(0, leadTimePresets.size - 1)
        } else {
            if (direction > 0) leadTimePresets.indexOfFirst { it > currentLeadTimeMs }.coerceAtLeast(0)
            else leadTimePresets.indexOfLast { it < currentLeadTimeMs }.coerceAtLeast(0)
        }
        val target = leadTimePresets[nextIndex]
        callbacks.onLeadTimeChange(target)
    }

    /**
     * 再生状態や曲情報をControlパネルに反映する。
     */
    fun updatePlaybackStatus(
        isPlaying: Boolean,
        positionMs: Long,
        durationMs: Long,
        speed: Float,
        songTitle: String?,
        leadTimeMs: Long = currentLeadTimeMs
    ) {
        isPlayingState = isPlaying
        currentSpeed = speed
        currentLeadTimeMs = leadTimeMs

        if (!songTitle.isNullOrEmpty()) {
            songTitleText.text = songTitle
        }
        timeText.text = TimeFormatter.formatDurationPair(positionMs, durationMs)

        playPauseButton.text = if (isPlaying) "⏸" else "▶"
        playPauseButton.background = createRoundedDrawable(
            cornerRadiusDp = 6f,
            fillColor = if (isPlaying) Color.parseColor("#F57F17") else Color.parseColor("#2E7D32")
        )

        // 最小化ボタンのアイコン表示更新（再生中: ⏸, 一時停止/停止: ♪）
        collapsedView.text = if (isPlaying) "⏸" else "♪"

        val percent = (speed * 100).toInt()
        speedValueText.text = "$percent%"
        leadTimeValueText.text = "${leadTimeMs}ms"
    }

    private fun createMiniButton(text: String, bgColor: Int, onClick: () -> Unit): Button {
        return Button(context).apply {
            this.text = text
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, 0)
            background = createRoundedDrawable(
                cornerRadiusDp = 6f,
                fillColor = bgColor
            )
            layoutParams = LinearLayout.LayoutParams(0, dpToPx(34), 1f)
            setOnClickListener { onClick() }
        }
    }

    private fun createSmallAdjustButton(text: String, onClick: () -> Unit): Button {
        return Button(context).apply {
            this.text = text
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, 0)
            background = createRoundedDrawable(
                cornerRadiusDp = 4f,
                fillColor = Color.parseColor("#455A64")
            )
            layoutParams = LinearLayout.LayoutParams(dpToPx(28), dpToPx(26))
            setOnClickListener { onClick() }
        }
    }

    private fun createHorizontalSpacer(widthDp: Int): View {
        return View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                dpToPx(widthDp),
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }
    }

    private fun updateGuideButtonText(isShowing: Boolean) {
        guideToggleButton.text = if (isShowing) "Guide OFF" else "Guide ON"
        guideToggleButton.background = createRoundedDrawable(
            cornerRadiusDp = 8f,
            fillColor = if (isShowing) Color.parseColor("#00897B") else Color.parseColor("#455A64")
        )
    }

    fun expand() {
        if (isExpanded) return
        isExpanded = true
        updateGuideButtonText(callbacks.isGuideShowing())
        collapsedView.visibility = View.GONE
        expandedView.visibility = View.VISIBLE
        post { clampPosition() }
    }

    fun collapse() {
        if (!isExpanded) return
        isExpanded = false
        expandedView.visibility = View.GONE
        collapsedView.visibility = View.VISIBLE
        post { clampPosition() }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        // パネル展開中は内部のScrollViewに縦スクロール操作を委ねるため、パネル自体のドラッグ移動はインターセプトしない
        if (isExpanded) {
            return false
        }

        // 最小化（フローティングアイコン）時は、タッチの移動量がtouchSlopを超えた時点でドラッグ操作とみなしインターセプトする
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                initialTouchX = ev.rawX
                initialTouchY = ev.rawY
                initialLayoutX = layoutParams.x
                initialLayoutY = layoutParams.y
                isDragging = false
                return false
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = ev.rawX - initialTouchX
                val dy = ev.rawY - initialTouchY
                if (abs(dx) > touchSlop || abs(dy) > touchSlop) {
                    isDragging = true
                    return true
                }
                return false
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                return false
            }
        }
        return super.onInterceptTouchEvent(ev)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                initialLayoutX = layoutParams.x
                initialLayoutY = layoutParams.y
                isDragging = false
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - initialTouchX
                val dy = event.rawY - initialTouchY

                // 移動量がスロップを超えたらドラッグ開始
                if (!isDragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                    isDragging = true
                }

                // アイコンを指の追従に合わせて移動させ、画面枠内にクランプ（はみ出し防止）
                if (isDragging) {
                    val rawTargetX = initialLayoutX + dx.toInt()
                    val rawTargetY = initialLayoutY + dy.toInt()

                    val (clampedX, clampedY) = clampCoordinates(rawTargetX, rawTargetY)
                    layoutParams.x = clampedX
                    layoutParams.y = clampedY
                    try {
                        windowManager.updateViewLayout(this, layoutParams)
                    } catch (_: IllegalArgumentException) {
                        // ウィンドウ破棄直後のタイミング競合エラーを無視
                    }
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                if (!isDragging) {
                    // 移動せずに指を離した場合は「タップ」と判定し、最小化状態ならパネルを展開
                    if (!isExpanded) {
                        expand()
                    }
                } else {
                    // ドラッグ完了時に正規化座標（0.0〜1.0）を保存リポジトリへ通知
                    notifyPositionChanged()
                }
                isDragging = false
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun notifyPositionChanged() {
        val displayMetrics = context.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels.toFloat()
        val screenHeight = displayMetrics.heightPixels.toFloat()
        val normX = (layoutParams.x.toFloat() / screenWidth).coerceIn(0f, 1f)
        val normY = (layoutParams.y.toFloat() / screenHeight).coerceIn(0f, 1f)
        callbacks.onPositionChanged(normX, normY, layoutParams.x, layoutParams.y)
    }

    private fun clampPosition() {
        val (clampedX, clampedY) = clampCoordinates(layoutParams.x, layoutParams.y)
        layoutParams.x = clampedX
        layoutParams.y = clampedY
        try {
            windowManager.updateViewLayout(this, layoutParams)
        } catch (_: IllegalArgumentException) {
        }
    }

    private fun clampCoordinates(targetX: Int, targetY: Int): Pair<Int, Int> {
        val displayMetrics = context.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        val viewWidth = max(width, dpToPx(52))
        val viewHeight = max(height, dpToPx(52))

        val maxX = max(0, screenWidth - viewWidth)
        val maxY = max(0, screenHeight - viewHeight)

        val clampedX = targetX.coerceIn(0, maxX)
        val clampedY = targetY.coerceIn(0, maxY)
        return Pair(clampedX, clampedY)
    }

    private fun createActionButton(
        text: String,
        bgColor: Int,
        textColor: Int,
        onClick: () -> Unit
    ): Button {
        return Button(context).apply {
            this.text = text
            setTextColor(textColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            background = createRoundedDrawable(
                cornerRadiusDp = 6f,
                fillColor = bgColor
            )
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(34)
            )
            setOnClickListener { onClick() }
        }
    }

    private fun createSpacer(heightDp: Int): View {
        return View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(heightDp)
            )
        }
    }

    private fun createCircleDrawable(fillColor: Int, strokeColor: Int, strokeWidthDp: Float): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(fillColor)
            setStroke(dpToPx(strokeWidthDp.toInt()), strokeColor)
        }
    }

    private fun createRoundedDrawable(
        cornerRadiusDp: Float,
        fillColor: Int,
        strokeColor: Int = Color.TRANSPARENT,
        strokeWidthDp: Float = 0f
    ): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(cornerRadiusDp.toInt()).toFloat()
            setColor(fillColor)
            if (strokeWidthDp > 0f) {
                setStroke(dpToPx(strokeWidthDp.toInt()), strokeColor)
            }
        }
    }

    private fun dpToPx(dp: Int): Int {
        val scale = context.resources.displayMetrics.density
        return (dp * scale + 0.5f).toInt()
    }
}
