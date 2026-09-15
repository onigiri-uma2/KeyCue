package com.onigiri.keycue.model

/**
 * 楽曲データを表す共通データクラス。
 *
 * @param title 楽曲タイトル
 * @param durationMs 楽曲の総演奏時間（ミリ秒）
 * @param events 演奏イベントのリスト（時系列順）
 */
data class SongData(
    val title: String,
    val durationMs: Long,
    val events: List<NoteEvent>
)
