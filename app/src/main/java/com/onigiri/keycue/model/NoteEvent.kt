package com.onigiri.keycue.model

/**
 * 演奏イベントを表すデータクラス。
 *
 * @param timeMs 楽曲開始からのミリ秒
 * @param key キーインデックス (0-based)
 */
data class NoteEvent(
    val timeMs: Long,
    val key: Int
)
