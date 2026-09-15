package com.onigiri.keycue.fitting

import com.onigiri.keycue.model.FitProfile

/**
 * スクリーンショットからの自動キー位置フィッティング解析結果。
 *
 * @param profile 算出されたキー配置プロファイル（フィッティング失敗時は null）
 * @param confidence 解析結果の総合的な信頼度・確信度 (0.0f..1.0f)
 * @param detectedPoints 画像解析によって抽出された円形キー候補点群
 * @param errorMessage フィッティング失敗時または警告時の詳細メッセージ
 */
data class FitResult(
    val profile: FitProfile?,
    val confidence: Float,
    val detectedPoints: List<DetectedPoint> = emptyList(),
    val errorMessage: String? = null
)
