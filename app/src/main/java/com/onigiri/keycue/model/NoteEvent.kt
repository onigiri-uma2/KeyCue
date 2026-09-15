package com.onigiri.keycue.model

/**
 * 演奏イベントを表すデータクラス。
 *
 * @param timeMs 楽曲開始からのミリ秒
 * @param key 15キーのインデックス (0..14)
 */
data class NoteEvent(
    val timeMs: Long,
    val key: Int
)
