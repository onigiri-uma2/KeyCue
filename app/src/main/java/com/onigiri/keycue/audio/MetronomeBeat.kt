package com.onigiri.keycue.audio

/**
 * 1つのメトロノームクリックイベント情報を表す不変データモデル。
 *
 * @param index クリックインデックス（0以上の通し番号）
 * @param timeMs 楽曲時間軸上の発音予定時刻（ミリ秒）
 * @param isAccent 小節頭（第1拍）のアクセント音かどうか
 */
data class MetronomeBeat(
    val index: Long,
    val timeMs: Long,
    val isAccent: Boolean
)
