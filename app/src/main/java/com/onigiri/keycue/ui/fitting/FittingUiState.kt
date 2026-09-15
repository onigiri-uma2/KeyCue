package com.onigiri.keycue.ui.fitting

import android.graphics.Bitmap
import android.net.Uri
import com.onigiri.keycue.fitting.DetectedPoint
import com.onigiri.keycue.model.FitProfile
import com.onigiri.keycue.model.NormalizedPoint

/**
 * フィッティング画面（[FittingScreen]）の一連の操作フローを表す遷移ステップ。
 *
 * - [Idle]: スクリーンショット画像選択待ち（初期状態）
 * - [ImageSelected]: 端末内からスクリーンショット画像を選択完了
 * - [Detecting]: OpenCVによる画像解析およびグリッドフィッティング処理中
 * - [Success]: 自動検出成功（信頼度スコアとともにプレビュー表示）
 * - [Failed]: 自動検出失敗（エラーメッセージ表示、手動調整への案内）
 * - [ManualAdjust]: 4隅アンカーによる手動位置微調整中
 */
sealed interface FittingStep {
    data object Idle : FittingStep
    data object ImageSelected : FittingStep
    data object Detecting : FittingStep
    data class Success(val confidence: Float) : FittingStep
    data class Failed(val message: String) : FittingStep
    data object ManualAdjust : FittingStep
}

/**
 * FittingScreen の UI 状態。
 */
data class FittingUiState(
    val step: FittingStep = FittingStep.Idle,
    val imageUri: Uri? = null,
    val imageBitmap: Bitmap? = null,
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,
    val isLandscape: Boolean = true,
    val detectedPoints: List<DetectedPoint> = emptyList(),
    val currentProfile: FitProfile? = null,
    val savedProfile: FitProfile? = null,
    val confidence: Float = 0f,
    val errorMessage: String? = null,

    // 手動補正用の4隅ハンドル (正規化座標: Key 0, 4, 10, 14)
    val manualTopLeft: NormalizedPoint = NormalizedPoint(0.20f, 0.65f),
    val manualTopRight: NormalizedPoint = NormalizedPoint(0.80f, 0.65f),
    val manualBottomLeft: NormalizedPoint = NormalizedPoint(0.20f, 0.85f),
    val manualBottomRight: NormalizedPoint = NormalizedPoint(0.80f, 0.85f),
    val selectedCorner: Int = 0, // 0: Key0, 1: Key4, 2: Key10, 3: Key14

    val isSavedSuccess: Boolean = false,
    val showDebugView: Boolean = false
)
