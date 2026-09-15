package com.onigiri.keycue.song

import android.net.Uri
import com.onigiri.keycue.model.SongFormat

/**
 * 選択された楽曲ファイルのメタデータ。
 *
 * @param uri ファイルの content:// URI
 * @param displayName 表示ファイル名
 * @param mimeType MIME type（nullの場合あり）
 * @param format 判定されたフォーマット
 */
data class SongFileMetadata(
    val uri: Uri,
    val displayName: String,
    val mimeType: String?,
    val format: SongFormat
)
