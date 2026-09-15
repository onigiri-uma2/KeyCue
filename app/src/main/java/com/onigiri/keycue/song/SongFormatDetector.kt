package com.onigiri.keycue.song

import com.onigiri.keycue.model.SongFormat

/**
 * ファイル名およびMIME typeから楽曲フォーマットを判定するクラス。
 *
 * MIME typeだけに依存せず、ファイル名の拡張子も確認して判定を行う。
 */
class SongFormatDetector {

    /**
     * ファイル名とMIME type（および任意のコンテンツプレビュー）を検証し、[SongFormat] を判定する。
     *
     * @param fileName 表示ファイル名（例: "Canon.mid", "song.json"）
     * @param mimeType MIME type（例: "audio/midi", "application/json"）
     * @param contentPreview ファイル先頭の文字列サンプル（省略可能）
     * @return 判定された [SongFormat]
     */
    fun detect(
        fileName: String?,
        mimeType: String?,
        contentPreview: String? = null
    ): SongFormat {
        val extension = fileName?.substringAfterLast('.', "")?.lowercase()
        val normalizedMimeType = mimeType?.lowercase()

        // 1. MIDI判定
        if (extension in MIDI_EXTENSIONS ||
            (normalizedMimeType != null && MIDI_MIME_TYPES.any { normalizedMimeType.contains(it) })
        ) {
            return SongFormat.MIDI
        }

        // 2. JSON/TXT 判定
        val isJsonExtension = extension in JSON_EXTENSIONS
        val isJsonMime = normalizedMimeType != null && JSON_MIME_TYPES.any { normalizedMimeType.contains(it) }

        if (isJsonExtension || isJsonMime) {
            if (contentPreview != null) {
                return if (isSkyStudioJson(contentPreview)) {
                    SongFormat.SKY_STUDIO_JSON
                } else {
                    SongFormat.UNKNOWN
                }
            }
            // プレビューがない場合、.json または json MIME のみ候補とし、.txt は UNKNOWN
            return if (extension == "json" || normalizedMimeType == "application/json" || normalizedMimeType == "text/json") {
                SongFormat.SKY_STUDIO_JSON
            } else {
                SongFormat.UNKNOWN
            }
        }

        if (contentPreview != null && isSkyStudioJson(contentPreview)) {
            return SongFormat.SKY_STUDIO_JSON
        }

        return SongFormat.UNKNOWN
    }

    /**
     * 文字列コンテンツが Sky Studio JSON 譜面であるかを判定する。
     *
     * 通常の無関係なJSON（設定ファイル等）を誤認しないよう、
     * Sky Studio 特有の "songNotes" キーの存在を確認する。
     */
    fun isSkyStudioJson(content: String): Boolean {
        return content.contains("\"songNotes\"")
    }

    companion object {
        private val MIDI_EXTENSIONS = setOf(
            "mid",
            "midi"
        )

        private val MIDI_MIME_TYPES = setOf(
            "audio/midi",
            "audio/x-midi",
            "audio/mid",
            "audio/sp-midi",
            "application/x-midi",
            "application/midi"
        )

        private val JSON_EXTENSIONS = setOf(
            "json",
            "txt"
        )

        private val JSON_MIME_TYPES = setOf(
            "application/json",
            "text/json",
            "text/plain"
        )
    }
}
