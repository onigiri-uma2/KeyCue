package com.onigiri.keycue.model

/**
 * 演奏ガイドおよびノーツのビジュアル（見た目・配色・エフェクト）設定を集約する不変データモデル。
 *
 * キーの位置・配置を保持する [FitProfile] と責務を分離し、
 * ユーザーが調整可能な外観パラメータの Single Source of Truth として機能します。
 *
 * @param showKeyNumbers ガイドのキー番号 (0..14) を表示するかどうか
 * @param showFallingNotes 落下ノーツを表示するかどうか
 * @param showApproachCircles 音ゲー風アプローチサークル（縮小タイミング円）を表示するかどうか
 * @param showJustEffect ジャストタイミング時の発光演出を表示するかどうか
 * @param guideRadiusRatio 画面サイズに対するキーガイド円の半径比率 (0.02f..0.08f)
 * @param guideColor キーガイド円の基本描画色 (ARGB Int)
 * @param noteColorTop 上段ノーツ（Row 0, Key 0..4）の描画色 (ARGB Int)
 * @param noteColorMiddle 中段ノーツ（Row 1, Key 5..9）の描画色 (ARGB Int)
 * @param noteColorBottom 下段ノーツ（Row 2, Key 10..14）の描画色 (ARGB Int)
 */
data class VisualConfig(
    val showKeyNumbers: Boolean = DEFAULT_SHOW_KEY_NUMBERS,
    val showFallingNotes: Boolean = DEFAULT_SHOW_FALLING_NOTES,
    val showApproachCircles: Boolean = DEFAULT_SHOW_APPROACH_CIRCLES,
    val showJustEffect: Boolean = DEFAULT_SHOW_JUST_EFFECT,
    val guideRadiusRatio: Float = DEFAULT_GUIDE_RADIUS_RATIO,
    val guideColor: Int = DEFAULT_GUIDE_COLOR,
    val noteColorTop: Int = DEFAULT_NOTE_COLOR_TOP,
    val noteColorMiddle: Int = DEFAULT_NOTE_COLOR_MIDDLE,
    val noteColorBottom: Int = DEFAULT_NOTE_COLOR_BOTTOM
) {
    init {
        require(guideRadiusRatio in MIN_GUIDE_RADIUS_RATIO..MAX_GUIDE_RADIUS_RATIO) {
            "guideRadiusRatio must be between $MIN_GUIDE_RADIUS_RATIO and $MAX_GUIDE_RADIUS_RATIO, but was $guideRadiusRatio"
        }
    }

    companion object {
        const val MIN_GUIDE_RADIUS_RATIO = 0.02f
        const val MAX_GUIDE_RADIUS_RATIO = 0.08f

        const val DEFAULT_SHOW_KEY_NUMBERS = false
        const val DEFAULT_SHOW_FALLING_NOTES = true
        const val DEFAULT_SHOW_APPROACH_CIRCLES = true
        const val DEFAULT_SHOW_JUST_EFFECT = false

        const val DEFAULT_GUIDE_RADIUS_RATIO = 0.04f

        const val DEFAULT_GUIDE_COLOR: Int = 0xFFFFFFFF.toInt()
        const val DEFAULT_NOTE_COLOR_TOP: Int = 0xFF4CAF50.toInt()      // 緑系
        const val DEFAULT_NOTE_COLOR_MIDDLE: Int = 0xFF2196F3.toInt()   // 青系
        const val DEFAULT_NOTE_COLOR_BOTTOM: Int = 0xFFE91E63.toInt()   // ピンク系

        /**
         * プリセットカラーパレット（8色）
         */
        val PRESET_COLORS: List<Int> = listOf(
            0xFFFFFFFF.toInt(), // White
            0xFF00E5FF.toInt(), // Cyan
            0xFF2196F3.toInt(), // Blue
            0xFF4CAF50.toInt(), // Green
            0xFFFFEB3B.toInt(), // Yellow
            0xFFFF9800.toInt(), // Orange
            0xFFE91E63.toInt(), // Pink
            0xFF9C27B0.toInt()  // Purple
        )

        /**
         * 半径比率を安全な範囲にclampして新しいインスタンスを生成する。
         */
        fun safe(
            showKeyNumbers: Boolean = DEFAULT_SHOW_KEY_NUMBERS,
            showFallingNotes: Boolean = DEFAULT_SHOW_FALLING_NOTES,
            showApproachCircles: Boolean = DEFAULT_SHOW_APPROACH_CIRCLES,
            showJustEffect: Boolean = DEFAULT_SHOW_JUST_EFFECT,
            guideRadiusRatio: Float = DEFAULT_GUIDE_RADIUS_RATIO,
            guideColor: Int = DEFAULT_GUIDE_COLOR,
            noteColorTop: Int = DEFAULT_NOTE_COLOR_TOP,
            noteColorMiddle: Int = DEFAULT_NOTE_COLOR_MIDDLE,
            noteColorBottom: Int = DEFAULT_NOTE_COLOR_BOTTOM
        ): VisualConfig {
            return VisualConfig(
                showKeyNumbers = showKeyNumbers,
                showFallingNotes = showFallingNotes,
                showApproachCircles = showApproachCircles,
                showJustEffect = showJustEffect,
                guideRadiusRatio = guideRadiusRatio.coerceIn(MIN_GUIDE_RADIUS_RATIO, MAX_GUIDE_RADIUS_RATIO),
                guideColor = guideColor,
                noteColorTop = noteColorTop,
                noteColorMiddle = noteColorMiddle,
                noteColorBottom = noteColorBottom
            )
        }
    }
}
