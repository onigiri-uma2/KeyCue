package com.onigiri.keycue.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.hardware.input.InputManager
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.onigiri.keycue.model.FitProfile
import kotlin.math.max

/**
 * [WindowManager] に対する各オーバーレイViewの追加・更新・破棄を一元管理するコントローラー。
 *
 * 以下の3種類のオーバーレイウィンドウを制御します:
 * - **Control Overlay ([ControlOverlayView])**:
 *   - タッチ可能・ドラッグ移動可能なフローティングUI。
 *   - 再生/停止/シークや設定変更、最小化/展開を提供。
 * - **Guide Overlay ([GuideOverlayView])**:
 *   - `FLAG_NOT_TOUCHABLE` によりタッチ完全透過の全画面オーバーレイ。
 *   - キー配置ガイド、落下ノート、ジャスト演出を描画。
 * - **Fitting Overlay ([FittingOverlayView])**:
 *   - 手動位置微調整時に前面を覆い、背後へのタッチを遮断してキー位置を調整。
 */
class OverlayWindowController(
    private val context: Context,
    private val callbacks: ControlOverlayCallbacks
) {

    /** 後方互換用セカンダリコンストラクタ */
    constructor(
        context: Context,
        onOpenApp: () -> Unit,
        onCloseOverlay: () -> Unit,
        onPositionChanged: (normX: Float, normY: Float, pixelX: Int, pixelY: Int) -> Unit,
        onSelectFile: () -> Unit = {},
        onPlayPause: () -> Unit = {},
        onStop: () -> Unit = {},
        onRestart: () -> Unit = {},
        onSeekBack: () -> Unit = {},
        onSeekForward: () -> Unit = {},
        onSpeedChange: (Float) -> Unit = {},
        onNoteLeadTimeChange: (Long) -> Unit = {},
        onSaveFitProfile: (FitProfile) -> Unit = {}
    ) : this(
        context = context,
        callbacks = ControlOverlayCallbacks(
            onOpenApp = onOpenApp,
            onCloseOverlay = onCloseOverlay,
            onPositionChanged = onPositionChanged,
            onSelectFile = onSelectFile,
            onPlayPause = onPlayPause,
            onStop = onStop,
            onRestart = onRestart,
            onSeekBack = onSeekBack,
            onSeekForward = onSeekForward,
            onSpeedChange = onSpeedChange,
            onNoteLeadTimeChange = onNoteLeadTimeChange,
            onSaveFitProfile = onSaveFitProfile
        )
    )

    private val windowManager: WindowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var controlOverlayView: ControlOverlayView? = null
    private var guideOverlayView: GuideOverlayView? = null
    private var fittingOverlayView: FittingOverlayView? = null

    private var currentFitProfile: FitProfile = FitProfile.createDefaultTestProfile()
    private var currentVisualConfig: com.onigiri.keycue.model.VisualConfig = com.onigiri.keycue.model.VisualConfig()

    // --- Control Overlay 管理 ---

    /**
     * Control Overlayを表示する。すでに表示されている場合は何もしない（二重起動防止）。
     *
     * @param initialNormX 保存された正規化X座標（0.0..1.0）
     * @param initialNormY 保存された正規化Y座標（0.0..1.0）
     * @param initialPixelX 保存されたXピクセル（正規化座標がない場合のフォールバック）
     * @param initialPixelY 保存されたYピクセル（正規化座標がない場合のフォールバック）
     */
    fun showControlOverlay(
        initialNormX: Float? = null,
        initialNormY: Float? = null,
        initialPixelX: Int? = null,
        initialPixelY: Int? = null
    ) {
        if (controlOverlayView != null) return

        val displayMetrics = context.resources.displayMetrics
        val density = displayMetrics.density
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        // デフォルト初期位置（画面右上寄り、上端から少し下）
        val defaultX = screenWidth - (70 * density).toInt()
        val defaultY = (120 * density).toInt()

        val targetX = when {
            initialNormX != null -> (initialNormX * screenWidth).toInt()
            initialPixelX != null -> initialPixelX
            else -> defaultX
        }

        val targetY = when {
            initialNormY != null -> (initialNormY * screenHeight).toInt()
            initialPixelY != null -> initialPixelY
            else -> defaultY
        }

        val clampedX = targetX.coerceIn(0, max(0, screenWidth - (52 * density).toInt()))
        val clampedY = targetY.coerceIn(0, max(0, screenHeight - (52 * density).toInt()))

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = clampedX
            y = clampedY
        }

        val viewCallbacks = callbacks.copy(
            onToggleGuide = { toggleGuideOverlay() },
            isGuideShowing = { isGuideOverlayShowing() },
            onStartFitting = { startManualFitting() }
        )

        val view = ControlOverlayView(
            context = context,
            windowManager = windowManager,
            layoutParams = layoutParams,
            callbacks = viewCallbacks
        )

        try {
            windowManager.addView(view, layoutParams)
            controlOverlayView = view
        } catch (_: Exception) {
            controlOverlayView = null
        }
    }

    /** 互換性のためのエイリアス */
    fun show(initialX: Int? = null, initialY: Int? = null) =
        showControlOverlay(initialPixelX = initialX, initialPixelY = initialY)

    /**
     * Control Overlayを非表示（GONE）にする。
     */
    fun hideControlOverlay() {
        controlOverlayView?.visibility = View.GONE
    }

    /** 互換性のためのエイリアス */
    fun hide() = hideControlOverlay()

    /**
     * Control Overlayを再表示（VISIBLE）にする。
     */
    fun showControls() {
        controlOverlayView?.visibility = View.VISIBLE
    }

    fun isShowing(): Boolean = controlOverlayView != null

    /**
     * 外部Activity（ファイルピッカー等）起動時にオーバーレイ全般を一時的に非表示にする。
     */
    fun hideForFilePicker() {
        controlOverlayView?.visibility = View.GONE
        guideOverlayView?.visibility = View.GONE
    }

    /**
     * 外部Activity終了後にオーバーレイの表示を復元する。
     */
    fun restoreAfterFilePicker() {
        controlOverlayView?.visibility = View.VISIBLE
        guideOverlayView?.visibility = View.VISIBLE
    }

    // --- Guide Overlay 管理 ---

    /**
     * Guide Overlay（全画面・タッチ透過）を表示する。すでに表示中の場合は二重追加しない。
     */
    fun showGuideOverlay() {
        if (guideOverlayView != null) return

        val layoutParams = createGuideLayoutParams()
        val view = GuideOverlayView(context, currentFitProfile, currentVisualConfig)

        try {
            windowManager.addView(view, layoutParams)
            guideOverlayView = view
        } catch (_: Exception) {
            guideOverlayView = null
        }
    }

    /**
     * Guide Overlayを削除・非表示にする。
     */
    fun hideGuideOverlay() {
        guideOverlayView?.let { view ->
            try {
                windowManager.removeView(view)
            } catch (_: IllegalArgumentException) {
                // すでに削除されている場合
            }
        }
        guideOverlayView = null
    }

    /**
     * Guide Overlayの表示・非表示をトグル切り替えする。
     *
     * @return 切り替え後の表示状態（表示中ならtrue）
     */
    fun toggleGuideOverlay(): Boolean {
        return if (isGuideOverlayShowing()) {
            hideGuideOverlay()
            false
        } else {
            showGuideOverlay()
            true
        }
    }

    /**
     * Guide Overlayが表示中かどうかを判定する。
     */
    fun isGuideOverlayShowing(): Boolean = guideOverlayView != null

    /**
     * Guide Overlay に最新フレームを描画させる。
     */
    fun renderGuideFrame(frame: com.onigiri.keycue.playback.GuideFrame) {
        guideOverlayView?.renderFrame(frame)
    }

    /**
     * VisualConfig（見た目設定）を更新し、Guide Overlay に反映する。
     */
    fun updateVisualConfig(config: com.onigiri.keycue.model.VisualConfig) {
        currentVisualConfig = config
        guideOverlayView?.updateVisualConfig(config)
    }

    /**
     * Control Overlay の再生ステータス表示を更新する。
     */
    fun updateControlStatus(
        isPlaying: Boolean,
        positionMs: Long,
        durationMs: Long,
        speed: Float,
        songTitle: String?,
        noteLeadTimeMs: Long = com.onigiri.keycue.model.PlaybackConfig.DEFAULT_NOTE_LEAD_TIME_MS
    ) {
        controlOverlayView?.updatePlaybackStatus(
            isPlaying = isPlaying,
            positionMs = positionMs,
            durationMs = durationMs,
            speed = speed,
            songTitle = songTitle,
            noteLeadTimeMs = noteLeadTimeMs
        )
    }

    /**
     * FitProfileを更新し、表示中のGuide Overlayに反映する。
     */
    fun updateFitProfile(profile: FitProfile) {
        currentFitProfile = profile
        guideOverlayView?.updateFitProfile(profile)
    }

    // --- Manual Fitting Overlay 管理 ---

    /**
     * ゲーム画面上でのキー手動微調整モードを開始する。
     * 全画面でタッチを遮断し、背後のゲームへのタッチ伝播を完全にブロックする。
     */
    fun startManualFitting() {
        if (fittingOverlayView != null) return

        // コントロールビューおよび通常ガイドビューを一時非表示
        controlOverlayView?.visibility = View.GONE
        guideOverlayView?.visibility = View.GONE

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
        }

        val view = FittingOverlayView(
            context = context,
            initialProfile = currentFitProfile,
            onSave = { updatedProfile ->
                updateFitProfile(updatedProfile)
                callbacks.onSaveFitProfile(updatedProfile)
                finishManualFitting()
            },
            onCancel = {
                finishManualFitting()
            }
        )

        try {
            windowManager.addView(view, layoutParams)
            fittingOverlayView = view
        } catch (_: Exception) {
            fittingOverlayView = null
            controlOverlayView?.visibility = View.VISIBLE
            guideOverlayView?.visibility = View.VISIBLE
        }
    }

    /**
     * 手動微調整モードを終了し、通常のタッチ透過オーバーレイ表示に戻る。
     */
    fun finishManualFitting() {
        fittingOverlayView?.let { view ->
            try {
                windowManager.removeView(view)
            } catch (_: IllegalArgumentException) {
            }
        }
        fittingOverlayView = null

        controlOverlayView?.visibility = View.VISIBLE
        guideOverlayView?.visibility = View.VISIBLE
    }

    fun isFittingShowing(): Boolean = fittingOverlayView != null

    // --- LayoutParams 生成ヘルパー ---

    /**
     * Android 12 (API 31+) の untrusted touch 制限に対応した Guide Overlay 用 LayoutParams を生成する。
     */
    private fun createGuideLayoutParams(): WindowManager.LayoutParams {
        val inputManager = context.getSystemService(Context.INPUT_SERVICE) as? InputManager
        val maxOpacity = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            inputManager?.maximumObscuringOpacityForTouch ?: 0.8f
        } else {
            0.8f
        }

        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            alpha = maxOpacity.coerceIn(0.1f, 0.8f)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
        }
    }

    // --- 破棄処理 ---

    /**
     * WindowManagerから全View（Control Overlay, Guide Overlay, Fitting Overlay）を安全に削除してリソースを解放する。
     */
    fun destroy() {
        fittingOverlayView?.let { view ->
            try {
                windowManager.removeView(view)
            } catch (_: IllegalArgumentException) {
            }
        }
        fittingOverlayView = null

        controlOverlayView?.let { view ->
            try {
                windowManager.removeView(view)
            } catch (_: IllegalArgumentException) {
            }
        }
        controlOverlayView = null

        guideOverlayView?.let { view ->
            try {
                windowManager.removeView(view)
            } catch (_: IllegalArgumentException) {
            }
        }
        guideOverlayView = null
    }
}
