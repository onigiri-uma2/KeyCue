package com.onigiri.keycue.model

import com.onigiri.keycue.model.timing.SongTimingMetadata

/**
 * 楽曲データを表す共通データクラス。
 *
 * @param title 楽曲タイトル
 * @param durationMs 楽曲の総演奏時間（ミリ秒）
 * @param events 演奏イベントのリスト（時系列順）
 * @param timingMetadata 楽曲ファイル由来のテンポ・拍子メタデータ（未解析または存在しない場合はnull）
 */
data class SongData(
    val title: String,
    val durationMs: Long,
    val events: List<NoteEvent>,
    val timingMetadata: SongTimingMetadata? = null
)
