package com.onigiri.keycue.model

/**
 * 最近使った曲の履歴エントリを表すデータクラス。
 *
 * @param uri 楽曲ファイルの content:// URI 文字列
 * @param title 楽曲のタイトル（空の場合は表示名またはフォールバック名）
 */
data class RecentSongEntry(
    val uri: String,
    val title: String
)
