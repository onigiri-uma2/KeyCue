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
) {
    companion object {
        /** 履歴として保持・永続化する最大件数 */
        const val MAX_RECENT_SONGS = 5

        /** コントロールオーバーレイ内に表示する最大件数 */
        const val MAX_RECENT_SONGS_IN_OVERLAY = 3
    }
}
