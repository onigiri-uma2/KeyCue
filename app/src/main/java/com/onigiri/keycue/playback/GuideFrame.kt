package com.onigiri.keycue.playback

import com.onigiri.keycue.model.NoteEvent

/**
 * 演奏ガイドオーバーレイ（[com.onigiri.keycue.overlay.GuideOverlayView]）の1フレーム描画に必要な不変情報。
 *
 * 描画Viewが再生エンジンやクロック、Coroutine等の実行状態を直接参照することを防ぎ、
 * 疎結合なデータドリブン描画を実現するためのデータキャリアです。
 *
 * @param currentTimeMs 現在の楽曲再生位置（ミリ秒）
 * @param upcomingNotes 現在画面上に描画すべき先読み落下ノーツ一覧
 * @param highlightedKeys 事前ハイライトすべきキーインデックス (0..14) のセット
 * @param justKeys ジャストタイミングリングを表示すべきキーインデックス (0..14) のセット
 * @param countdownText カウントダウン中または開始時の表示文字列 ("3", "2", "1", "START" 等)
 * @param leadTimeMs 先読み時間（ミリ秒、位置計算用）
 */
data class GuideFrame(
    val currentTimeMs: Long,
    val upcomingNotes: List<NoteEvent> = emptyList(),
    val highlightedKeys: Set<Int> = emptySet(),
    val justKeys: Set<Int> = emptySet(),
    val countdownText: String? = null,
    val leadTimeMs: Long = 700L,
    val keyHighlightProgress: FloatArray = FloatArray(KEY_COUNT) { -1.0f }
) {
    companion object {
        const val KEY_COUNT = 15
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GuideFrame

        if (currentTimeMs != other.currentTimeMs) return false
        if (upcomingNotes != other.upcomingNotes) return false
        if (highlightedKeys != other.highlightedKeys) return false
        if (justKeys != other.justKeys) return false
        if (countdownText != other.countdownText) return false
        if (leadTimeMs != other.leadTimeMs) return false
        if (!keyHighlightProgress.contentEquals(other.keyHighlightProgress)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = currentTimeMs.hashCode()
        result = 31 * result + upcomingNotes.hashCode()
        result = 31 * result + highlightedKeys.hashCode()
        result = 31 * result + justKeys.hashCode()
        result = 31 * result + (countdownText?.hashCode() ?: 0)
        result = 31 * result + leadTimeMs.hashCode()
        result = 31 * result + keyHighlightProgress.contentHashCode()
        return result
    }
}
