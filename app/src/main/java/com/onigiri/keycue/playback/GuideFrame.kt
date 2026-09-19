package com.onigiri.keycue.playback

import com.onigiri.keycue.model.NoteEvent
import com.onigiri.keycue.model.PlaybackConfig

/**
 * 演奏ガイドオーバーレイ（[com.onigiri.keycue.overlay.GuideOverlayView]）の1フレーム描画に必要な不変情報。
 *
 * 描画Viewが再生エンジンやクロック、Coroutine等の実行状態を直接参照することを防ぎ、
 * 疎結合なデータドリブン描画を実現するためのデータキャリアです。
 *
 * @param currentTimeMs 現在の楽曲再生位置（ミリ秒）
 * @param upcomingNotes 現在画面上に描画すべき先読み落下ノート一覧
 * @param highlightedKeys 事前ハイライトすべきキーインデックスのセット
 * @param justKeys ジャストタイミングリングを表示すべきキーインデックスのセット
 * @param countdownText カウントダウン中または開始時の表示文字列 ("3", "2", "1", "START" 等)
 * @param noteLeadTimeMs ノート先読み時間（ミリ秒、位置計算用）
 * @param approachCircleLeadTimeMs タイミングサークル先読み時間（ミリ秒、縮小時間用）
 */
data class GuideFrame(
    val currentTimeMs: Long,
    val upcomingNotes: List<NoteEvent> = emptyList(),
    val highlightedKeys: Set<Int> = emptySet(),
    val justKeys: Set<Int> = emptySet(),
    val countdownText: String? = null,
    val noteLeadTimeMs: Long = PlaybackConfig.DEFAULT_NOTE_LEAD_TIME_MS,
    val approachCircleLeadTimeMs: Long = PlaybackConfig.DEFAULT_APPROACH_CIRCLE_LEAD_TIME_MS,
    val keyHighlightProgress: FloatArray = FloatArray(KEY_COUNT) { -1.0f },
    val approachCircles: List<ApproachCircle> = emptyList(),
    val chordGroups: List<ChordGroup> = emptyList()
) {
    companion object {
        // Legacy compatibility constant.
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
        if (noteLeadTimeMs != other.noteLeadTimeMs) return false
        if (approachCircleLeadTimeMs != other.approachCircleLeadTimeMs) return false
        if (!keyHighlightProgress.contentEquals(other.keyHighlightProgress)) return false
        if (approachCircles != other.approachCircles) return false
        if (chordGroups != other.chordGroups) return false

        return true
    }

    override fun hashCode(): Int {
        var result = currentTimeMs.hashCode()
        result = 31 * result + upcomingNotes.hashCode()
        result = 31 * result + highlightedKeys.hashCode()
        result = 31 * result + justKeys.hashCode()
        result = 31 * result + (countdownText?.hashCode() ?: 0)
        result = 31 * result + noteLeadTimeMs.hashCode()
        result = 31 * result + approachCircleLeadTimeMs.hashCode()
        result = 31 * result + keyHighlightProgress.contentHashCode()
        result = 31 * result + approachCircles.hashCode()
        result = 31 * result + chordGroups.hashCode()
        return result
    }
}

/**
 * 音ゲー風アプローチサークル（縮小タイミング円）の1ノート分の情報。
 *
 * @param key 対象キーインデックス (0..)
 * @param progress 進行度 (0.0: 開始 〜 1.0: ジャスト打鍵)
 * @param remainingCount 該当キーの未打鍵連続数（直近ノートに2以上が設定され、連打バッジ表示に使用）
 * @param showRepeatBadge このCircleに連打バッジ (×N) を表示すべきかどうか
 */
data class ApproachCircle(
    val key: Int,
    val progress: Float,
    val remainingCount: Int = 1,
    val showRepeatBadge: Boolean = false
)

/**
 * 同一時刻に複数の異なるキーを押す和音グループの情報。
 *
 * @param timeMs 和音の発生時刻（ミリ秒）
 * @param keys 構成するユニークなキーインデックスのリスト（昇順）
 */
data class ChordGroup(
    val timeMs: Long,
    val keys: List<Int>
)

