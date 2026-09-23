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
import android.widget.SeekBar
import android.widget.TextView
import com.onigiri.keycue.model.ControlOverlayConfig
import com.onigiri.keycue.model.PlaybackConfig
import com.onigiri.keycue.playback.PlaybackEngine
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
        onNoteLeadTimeChange: (Long) -> Unit = {},
        onApproachCircleLeadTimeChange: (Long) -> Unit = {},
        onStartFitting: () -> Unit = {},
        onSeekTo: (Long) -> Unit = {},
        onSetLoopStart: () -> Unit = {},
        onSetLoopEnd: () -> Unit = {},
        onClearLoop: () -> Unit = {}
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
            onNoteLeadTimeChange = onNoteLeadTimeChange,
            onApproachCircleLeadTimeChange = onApproachCircleLeadTimeChange,
            onStartFitting = onStartFitting,
            onSeekTo = onSeekTo,
            onSetLoopStart = onSetLoopStart,
            onSetLoopEnd = onSetLoopEnd,
            onClearLoop = onClearLoop
        )
    )

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var initialLayoutX = 0
    private var initialLayoutY = 0
    private var isDragging = false

    private var isExpanded = false

    // 再生速度の設定候補（PlaybackConfig.MIN_SPEED 0.25f 〜 MAX_SPEED 2.0f を網羅）
    internal val speedPresets = SPEED_PRESETS

    private var currentSpeed: Float = 1.0f
    private var currentNoteLeadTimeMs: Long = PlaybackConfig.DEFAULT_NOTE_LEAD_TIME_MS
    private var currentApproachCircleLeadTimeMs: Long = PlaybackConfig.DEFAULT_APPROACH_CIRCLE_LEAD_TIME_MS
    private var currentIsPlaying: Boolean = false
    private var currentSongTitle: String? = null
    private var currentPositionMs: Long = 0L
    private var currentDurationMs: Long = 0L
    private var currentLoopStartMs: Long? = null
    private var currentLoopEndMs: Long? = null
    private var isUserSeeking: Boolean = false
    private var currentConfig: ControlOverlayConfig = ControlOverlayConfig()

    // UIコンポーネント
    private val collapsedView: TextView
    private val expandedView: View

    // セクションコンテナ（表示/非表示制御用）
    private lateinit var songInfoSection: View
    private lateinit var seekBarSection: View
    private lateinit var playbackControlsSection: View
    private lateinit var loopSection: View
    private lateinit var speedSection: View
    private lateinit var noteLeadTimeSection: View
    private lateinit var approachCircleLeadTimeSection: View

    // アクションボタンコンテナ/参照（表示/非表示制御用）
    private lateinit var selectFileBtn: View
    private lateinit var minimizeBtn: View
    private lateinit var openBtn: View
    private lateinit var fittingBtn: View
    private lateinit var guideToggleButton: Button
    private lateinit var closeBtn: View

    // 楽曲情報
    private lateinit var songTitleText: TextView
    private lateinit var timeText: TextView

    // ミニシークバー
    private lateinit var seekCurrentTimeText: TextView
    private lateinit var playbackSeekBar: SeekBar
    private lateinit var seekDurationText: TextView

    // 再生コントロール
    private lateinit var playPauseButton: Button

    // ABリピート
    private lateinit var loopStatusText: TextView
    private lateinit var loopAButton: Button
    private lateinit var loopBButton: Button
    private lateinit var loopClearButton: Button

    // 速度・先読み設定
    private lateinit var speedValueText: TextView
    private lateinit var speedMinusBtn: Button
    private lateinit var speedPlusBtn: Button
    private lateinit var noteLeadTimeValueText: TextView
    private lateinit var noteMinusBtn: Button
    private lateinit var notePlusBtn: Button
    private lateinit var approachCircleLeadTimeValueText: TextView
    private lateinit var circleMinusBtn: Button
    private lateinit var circlePlusBtn: Button

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
        updateControlOverlayConfig(currentConfig)
    }

    private fun buildExpandedContentLayout(): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dpToPx(12)
            setPadding(pad, pad, pad, pad)

            // タイトルバー
            addView(buildTitleBar())

            // 楽曲情報
            songInfoSection = buildSongInfoSection()
            addView(songInfoSection)

            // ミニシークバー
            seekBarSection = buildSeekBarSection()
            addView(seekBarSection)

            // 再生コントロール
            playbackControlsSection = buildPlaybackControls()
            addView(playbackControlsSection)

            // ABリピート
            loopSection = buildLoopSection()
            addView(loopSection)

            // 速度・先読み設定
            speedSection = buildSpeedControls()
            addView(speedSection)

            noteLeadTimeSection = buildNoteLeadTimeControls()
            addView(noteLeadTimeSection)

            approachCircleLeadTimeSection = buildApproachCircleLeadTimeControls()
            addView(approachCircleLeadTimeSection)

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

    private fun buildSongInfoSection(): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(6)
            }

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
                )
            }

            addView(songTitleText)
            addView(timeText)
        }
    }

    private fun buildSeekBarSection(): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(6)
            }

            seekCurrentTimeText = TextView(context).apply {
                text = "00:00"
                setTextColor(Color.parseColor("#B0B0D0"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                gravity = Gravity.CENTER
                minWidth = dpToPx(32)
            }

            playbackSeekBar = SeekBar(context).apply {
                max = 0
                progress = 0
                isEnabled = false
                setPadding(dpToPx(6), 0, dpToPx(6), 0)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        if (fromUser) {
                            seekCurrentTimeText.text = TimeFormatter.formatDuration(progress.toLong())
                        }
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar?) {
                        isUserSeeking = true
                    }

                    override fun onStopTrackingTouch(seekBar: SeekBar?) {
                        val targetMs = seekBar?.progress?.toLong() ?: 0L
                        isUserSeeking = false
                        callbacks.onSeekTo(targetMs)
                    }
                })
            }

            seekDurationText = TextView(context).apply {
                text = "00:00"
                setTextColor(Color.parseColor("#B0B0D0"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                gravity = Gravity.CENTER
                minWidth = dpToPx(32)
            }

            addView(seekCurrentTimeText)
            addView(playbackSeekBar)
            addView(seekDurationText)
        }
    }

    private fun buildPlaybackControls(): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(6)
            }

            val playbackRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dpToPx(4)
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
                )

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

            addView(playbackRow)
            addView(subControlsRow)
        }
    }

    private fun buildLoopSection(): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(6)
            }

            loopStatusText = TextView(context).apply {
                text = "A: --:--  B: --:--"
                setTextColor(Color.parseColor("#B0BEC5"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dpToPx(3)
                }
            }

            val loopButtonsRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )

                loopAButton = createMiniButton("A", Color.parseColor("#1565C0")) {
                    callbacks.onSetLoopStart()
                }
                addView(loopAButton)
                addView(createHorizontalSpacer(4))

                loopBButton = createMiniButton("B", Color.parseColor("#1565C0")) {
                    callbacks.onSetLoopEnd()
                }
                addView(loopBButton)
                addView(createHorizontalSpacer(4))

                loopClearButton = createMiniButton("Clear", Color.parseColor("#455A64")) {
                    callbacks.onClearLoop()
                }
                loopClearButton.isEnabled = false
                loopClearButton.alpha = 0.4f
                addView(loopClearButton)
            }

            addView(loopStatusText)
            addView(loopButtonsRow)
        }
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

            speedMinusBtn = createSmallAdjustButton("－") {
                adjustSpeedStep(-1)
            }
            speedPlusBtn = createSmallAdjustButton("＋") {
                adjustSpeedStep(+1)
            }
            addView(speedMinusBtn)
            addView(createHorizontalSpacer(4))
            addView(speedPlusBtn)
        }
    }

    private fun buildNoteLeadTimeControls(): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(6)
            }

            val leadLabel = TextView(context).apply {
                text = "Note"
                setTextColor(Color.parseColor("#CFD8DC"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                layoutParams = LinearLayout.LayoutParams(dpToPx(42), LinearLayout.LayoutParams.WRAP_CONTENT)
            }
            addView(leadLabel)

            noteLeadTimeValueText = TextView(context).apply {
                text = "${PlaybackConfig.DEFAULT_NOTE_LEAD_TIME_MS}ms"
                setTextColor(Color.parseColor("#90CAF9"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            addView(noteLeadTimeValueText)

            noteMinusBtn = createSmallAdjustButton("－") {
                adjustNoteLeadTimeStep(-1)
            }
            notePlusBtn = createSmallAdjustButton("＋") {
                adjustNoteLeadTimeStep(+1)
            }
            addView(noteMinusBtn)
            addView(createHorizontalSpacer(4))
            addView(notePlusBtn)
        }
    }

    private fun buildApproachCircleLeadTimeControls(): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(10)
            }

            val circleLabel = TextView(context).apply {
                text = "Circle"
                setTextColor(Color.parseColor("#CFD8DC"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                layoutParams = LinearLayout.LayoutParams(dpToPx(42), LinearLayout.LayoutParams.WRAP_CONTENT)
            }
            addView(circleLabel)

            approachCircleLeadTimeValueText = TextView(context).apply {
                text = "${PlaybackConfig.DEFAULT_APPROACH_CIRCLE_LEAD_TIME_MS}ms"
                setTextColor(Color.parseColor("#90CAF9"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            addView(approachCircleLeadTimeValueText)

            circleMinusBtn = createSmallAdjustButton("－") {
                adjustApproachCircleLeadTimeStep(-1)
            }
            circlePlusBtn = createSmallAdjustButton("＋") {
                adjustApproachCircleLeadTimeStep(+1)
            }
            addView(circleMinusBtn)
            addView(createHorizontalSpacer(4))
            addView(circlePlusBtn)
        }
    }

    private fun buildActionButtons(): List<View> {
        // 「楽曲を選択」ボタン
        selectFileBtn = createActionButton(
            text = "楽曲を選択",
            bgColor = Color.parseColor("#4A148C"),
            textColor = Color.WHITE
        ) {
            collapse()
            callbacks.onSelectFile()
        }

        // 「最小化」ボタン
        minimizeBtn = createActionButton(
            text = "最小化",
            bgColor = Color.parseColor("#37474F"),
            textColor = Color.WHITE
        ) {
            collapse()
        }

        // 「設定」ボタン
        openBtn = createActionButton(
            text = "設定",
            bgColor = Color.parseColor("#3F51B5"),
            textColor = Color.WHITE
        ) {
            collapse()
            callbacks.onOpenApp()
        }

        // 「位置微調整」ボタン
        fittingBtn = createActionButton(
            text = "位置微調整",
            bgColor = Color.parseColor("#1565C0"),
            textColor = Color.WHITE
        ) {
            collapse()
            callbacks.onStartFitting()
        }

        // 「Guide ON / OFF」トグルボタン
        guideToggleButton = createActionButton(
            text = if (callbacks.isGuideShowing()) "Guide OFF" else "Guide ON",
            bgColor = Color.parseColor("#00796B"),
            textColor = Color.WHITE
        ) {
            val isShowingNow = callbacks.onToggleGuide()
            updateGuideButtonText(isShowingNow)
        }

        // 「終了」ボタン
        closeBtn = createActionButton(
            text = "終了",
            bgColor = Color.parseColor("#5A1E1E"),
            textColor = Color.parseColor("#FFCDD2")
        ) {
            callbacks.onCloseOverlay()
        }

        return listOf(
            selectFileBtn,
            minimizeBtn,
            openBtn,
            fittingBtn,
            guideToggleButton,
            closeBtn
        )
    }

    private fun adjustSpeedStep(direction: Int) {
        val target = calculateNextSpeed(currentSpeed, direction, speedPresets)
        callbacks.onSpeedChange(target)
    }

    private fun adjustNoteLeadTimeStep(direction: Int) {
        val next = (currentNoteLeadTimeMs + direction * PlaybackConfig.NOTE_LEAD_TIME_STEP_MS)
            .coerceIn(PlaybackConfig.MIN_NOTE_LEAD_TIME_MS, PlaybackConfig.MAX_NOTE_LEAD_TIME_MS)
        if (next != currentNoteLeadTimeMs) {
            callbacks.onNoteLeadTimeChange(next)
        }
    }

    private fun adjustApproachCircleLeadTimeStep(direction: Int) {
        val next = (currentApproachCircleLeadTimeMs + direction * PlaybackConfig.APPROACH_CIRCLE_LEAD_TIME_STEP_MS)
            .coerceIn(PlaybackConfig.MIN_APPROACH_CIRCLE_LEAD_TIME_MS, PlaybackConfig.MAX_APPROACH_CIRCLE_LEAD_TIME_MS)
        if (next != currentApproachCircleLeadTimeMs) {
            callbacks.onApproachCircleLeadTimeChange(next)
        }
    }

    /**
     * 各種調整ボタン（Speed, Note, Circle）の境界値に応じた有効/無効およびアルファ値を更新する。
     */
    private fun updateAdjustButtonsEnabled() {
        if (::noteMinusBtn.isInitialized && ::notePlusBtn.isInitialized) {
            noteMinusBtn.isEnabled = currentNoteLeadTimeMs > PlaybackConfig.MIN_NOTE_LEAD_TIME_MS
            noteMinusBtn.alpha = if (noteMinusBtn.isEnabled) 1.0f else 0.4f
            notePlusBtn.isEnabled = currentNoteLeadTimeMs < PlaybackConfig.MAX_NOTE_LEAD_TIME_MS
            notePlusBtn.alpha = if (notePlusBtn.isEnabled) 1.0f else 0.4f
        }

        if (::circleMinusBtn.isInitialized && ::circlePlusBtn.isInitialized) {
            circleMinusBtn.isEnabled = currentApproachCircleLeadTimeMs > PlaybackConfig.MIN_APPROACH_CIRCLE_LEAD_TIME_MS
            circleMinusBtn.alpha = if (circleMinusBtn.isEnabled) 1.0f else 0.4f
            circlePlusBtn.isEnabled = currentApproachCircleLeadTimeMs < PlaybackConfig.MAX_APPROACH_CIRCLE_LEAD_TIME_MS
            circlePlusBtn.alpha = if (circlePlusBtn.isEnabled) 1.0f else 0.4f
        }

        if (::speedMinusBtn.isInitialized && ::speedPlusBtn.isInitialized) {
            speedMinusBtn.isEnabled = currentSpeed > PlaybackConfig.MIN_SPEED
            speedMinusBtn.alpha = if (speedMinusBtn.isEnabled) 1.0f else 0.4f
            speedPlusBtn.isEnabled = currentSpeed < PlaybackConfig.MAX_SPEED
            speedPlusBtn.alpha = if (speedPlusBtn.isEnabled) 1.0f else 0.4f
        }
    }

    // 再生/一時停止ボタン用キャッシュ Drawable
    private val playBackgroundPlaying by lazy {
        createRoundedDrawable(
            cornerRadiusDp = 6f,
            fillColor = Color.parseColor("#F57F17")
        )
    }
    private val playBackgroundPaused by lazy {
        createRoundedDrawable(
            cornerRadiusDp = 6f,
            fillColor = Color.parseColor("#2E7D32")
        )
    }

    private var lastIsPlaying: Boolean? = null
    private var lastPositionMs: Long = -1L
    private var lastDurationMs: Long = -1L
    private var lastSpeed: Float = -1f
    private var lastSongTitle: String? = null
    private var lastNoteLeadTimeMs: Long = -1L
    private var lastApproachCircleLeadTimeMs: Long = -1L
    private var lastLoopStartMs: Long? = null
    private var lastLoopEndMs: Long? = null

    /**
     * 再生状態や曲情報をControlパネルに反映する。
     */
    fun updatePlaybackStatus(
        isPlaying: Boolean,
        positionMs: Long,
        durationMs: Long,
        speed: Float,
        songTitle: String?,
        noteLeadTimeMs: Long = currentNoteLeadTimeMs,
        approachCircleLeadTimeMs: Long = currentApproachCircleLeadTimeMs,
        loopStartMs: Long? = null,
        loopEndMs: Long? = null
    ) {
        // 折りたたみ中・展開中に関わらず最新状態を常に保持
        currentIsPlaying = isPlaying
        currentPositionMs = positionMs
        currentDurationMs = durationMs
        currentSpeed = speed
        currentSongTitle = songTitle
        currentNoteLeadTimeMs = noteLeadTimeMs
        currentApproachCircleLeadTimeMs = approachCircleLeadTimeMs
        currentLoopStartMs = loopStartMs
        currentLoopEndMs = loopEndMs

        // 1. 再生状態の変更（Play / Pause）は折りたたみ中でも即時反映
        if (lastIsPlaying != isPlaying) {
            lastIsPlaying = isPlaying
            playPauseButton.text = if (isPlaying) "⏸" else "▶"
            playPauseButton.background = if (isPlaying) playBackgroundPlaying else playBackgroundPaused
            // 最小化ボタンのアイコン表示更新（再生中: ⏸, 一時停止/停止: ♪）
            collapsedView.text = if (isPlaying) "⏸" else "♪"
        }

        // 2. パネル展開時のみ詳細情報を更新（折りたたみ中は不要な TextView.setText による requestLayout を抑制）
        if (isExpanded) {
            refreshExpandedPlaybackStatus(force = false)
        }
    }

    /**
     * 展開中UIに最新の再生状態・楽曲情報を描画する。
     * @param force true の場合、直前キャッシュ値に関わらず確実に全UIを再描画する。
     */
    private fun refreshExpandedPlaybackStatus(force: Boolean = false) {
        if (!isExpanded) return

        val displayTitle = currentSongTitle?.takeIf { it.isNotBlank() } ?: "未選択"
        if (force || displayTitle != lastSongTitle) {
            lastSongTitle = displayTitle
            songTitleText.text = displayTitle
        }

        if (force || lastPositionMs != currentPositionMs || lastDurationMs != currentDurationMs) {
            lastPositionMs = currentPositionMs
            lastDurationMs = currentDurationMs
            timeText.text = TimeFormatter.formatDurationPair(currentPositionMs, currentDurationMs)
        }

        // シークバーの更新
        if (::playbackSeekBar.isInitialized) {
            if (currentDurationMs <= 0L) {
                playbackSeekBar.max = 0
                playbackSeekBar.progress = 0
                playbackSeekBar.isEnabled = false
                seekCurrentTimeText.text = "00:00"
                seekDurationText.text = "00:00"
            } else {
                playbackSeekBar.isEnabled = true
                val durInt = currentDurationMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                if (playbackSeekBar.max != durInt) {
                    playbackSeekBar.max = durInt
                }
                seekDurationText.text = TimeFormatter.formatDuration(currentDurationMs)
                if (!isUserSeeking) {
                    val posInt = currentPositionMs.coerceIn(0L, currentDurationMs).toInt()
                    playbackSeekBar.progress = posInt
                    seekCurrentTimeText.text = TimeFormatter.formatDuration(currentPositionMs)
                }
            }
        }

        // ABリピートの更新
        if (::loopStatusText.isInitialized) {
            val start = currentLoopStartMs
            val end = currentLoopEndMs
            val isValid = start != null && end != null && start < end && (end - start) >= PlaybackEngine.MIN_LOOP_DURATION_MS
            val aStr = if (start != null) TimeFormatter.formatDurationWithTenths(start) else "--:--"
            val bStr = if (end != null) TimeFormatter.formatDurationWithTenths(end) else "--:--"

            if (isValid) {
                loopStatusText.text = "🔁 A: $aStr → B: $bStr"
                loopStatusText.setTextColor(Color.parseColor("#80D8FF"))
                loopClearButton.isEnabled = true
                loopClearButton.alpha = 1.0f
            } else {
                loopStatusText.text = "A: $aStr  B: $bStr"
                loopStatusText.setTextColor(Color.parseColor("#B0BEC5"))
                val hasAny = (start != null || end != null)
                loopClearButton.isEnabled = hasAny
                loopClearButton.alpha = if (hasAny) 1.0f else 0.4f
            }
        }

        if (force || lastSpeed != currentSpeed) {
            lastSpeed = currentSpeed
            val percent = (currentSpeed * 100).toInt()
            speedValueText.text = "$percent%"
        }

        if (force || lastNoteLeadTimeMs != currentNoteLeadTimeMs) {
            lastNoteLeadTimeMs = currentNoteLeadTimeMs
            noteLeadTimeValueText.text = "${currentNoteLeadTimeMs}ms"
        }

        if (force || lastApproachCircleLeadTimeMs != currentApproachCircleLeadTimeMs) {
            lastApproachCircleLeadTimeMs = currentApproachCircleLeadTimeMs
            approachCircleLeadTimeValueText.text = "${currentApproachCircleLeadTimeMs}ms"
        }

        updateAdjustButtonsEnabled()
    }

    /**
     * 表示項目の設定を更新し、各セクションの表示/非表示を切り替える。
     * 設定、最小化、終了ボタンは常時表示を保証する。
     */
    fun updateControlOverlayConfig(config: ControlOverlayConfig) {
        currentConfig = config
        if (!::songInfoSection.isInitialized) return

        songInfoSection.visibility = if (config.showSongInfo) View.VISIBLE else View.GONE
        seekBarSection.visibility = if (config.showSeekBar) View.VISIBLE else View.GONE
        playbackControlsSection.visibility = if (config.showPlaybackControls) View.VISIBLE else View.GONE
        loopSection.visibility = if (config.showLoopControls) View.VISIBLE else View.GONE
        speedSection.visibility = if (config.showSpeedControl) View.VISIBLE else View.GONE
        noteLeadTimeSection.visibility = if (config.showNoteLeadTimeControl) View.VISIBLE else View.GONE
        approachCircleLeadTimeSection.visibility = if (config.showCircleLeadTimeControl) View.VISIBLE else View.GONE

        // 常時表示項目の安全性確保
        minimizeBtn.visibility = View.VISIBLE
        openBtn.visibility = View.VISIBLE
        closeBtn.visibility = View.VISIBLE
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
        // 展開した瞬間に、保持済みの最新状態をUIへ強制描画
        refreshExpandedPlaybackStatus(force = true)
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
            ).apply {
                bottomMargin = dpToPx(5)
            }
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

    companion object {
        const val SPEED_STEP = 0.25f

        /**
         * 最小速度から最大速度までの速度プリセットを生成する。
         */
        fun generateSpeedPresets(
            minSpeed: Float = PlaybackConfig.MIN_SPEED,
            maxSpeed: Float = PlaybackConfig.MAX_SPEED,
            step: Float = SPEED_STEP
        ): List<Float> {
            val count = kotlin.math.round((maxSpeed - minSpeed) / step).toInt()
            return (0..count).map { i ->
                val v = minSpeed + i * step
                kotlin.math.round(v * 100f) / 100f
            }
        }

        /**
         * 再生速度の設定プリセット一覧（PlaybackConfig.MIN_SPEED 〜 MAX_SPEED を網羅）。
         */
        val SPEED_PRESETS = generateSpeedPresets()

        /**
         * 現在の速度と増減方向（+1 または -1）から次の速度を算出する。
         * プリセット外の速度の場合は直近のプリセットへスナップする。
         */
        fun calculateNextSpeed(
            currentSpeed: Float,
            direction: Int,
            presets: List<Float> = SPEED_PRESETS
        ): Float {
            val currentIndex = presets.indexOfFirst { abs(it - currentSpeed) < 0.01f }
            val nextIndex = if (currentIndex >= 0) {
                (currentIndex + direction).coerceIn(0, presets.size - 1)
            } else {
                if (direction > 0) presets.indexOfFirst { it > currentSpeed }.coerceAtLeast(0)
                else presets.indexOfLast { it < currentSpeed }.coerceAtLeast(0)
            }
            return presets[nextIndex]
        }
    }
}
