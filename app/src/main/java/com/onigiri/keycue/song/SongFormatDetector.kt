package com.onigiri.keycue.song

import com.onigiri.keycue.model.SongFormat

/**
 * ファイル名およびMIME typeから楽曲フォーマットを判定するクラス。
 *
 * MIME typeだけに依存せず、ファイル名の拡張子も確認して判定を行う。
 */
class SongFormatDetector {

    /**
     * ファイル名とMIME type（および任意のファイルコンテンツ）を検証し、[SongFormat] を判定する。
     *
     * @param fileName 表示ファイル名（例: "Canon.mid", "song.json"）
     * @param mimeType MIME type（例: "audio/midi", "application/json"）
     * @param content ファイル文字列コンテンツ（省略可能）
     * @return 判定された [SongFormat]
     */
    fun detect(
        fileName: String?,
        mimeType: String?,
        content: String? = null
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
            if (content != null) {
                return if (isSkyStudioJson(content)) {
                    SongFormat.SKY_STUDIO_JSON
                } else {
                    SongFormat.UNKNOWN
                }
            }
            // コンテンツがない場合、.json または json MIME のみ候補とし、.txt は UNKNOWN
            return if (extension == "json" || normalizedMimeType == "application/json" || normalizedMimeType == "text/json") {
                SongFormat.SKY_STUDIO_JSON
            } else {
                SongFormat.UNKNOWN
            }
        }

        if (content != null && isSkyStudioJson(content)) {
            return SongFormat.SKY_STUDIO_JSON
        }

        return SongFormat.UNKNOWN
    }

    /**
     * 文字列コンテンツが Sky Studio JSON 譜面であるかを判定する。
     *
     * 1. UTF BOMおよび先頭空白を除去後、先頭が '{' または '[' であるかを確認。
     * 2. コンテンツを1パス走査し、JSON文字列リテラル（エスケープ \" や \\ を考慮）の外側にある
     *    実際の "songNotes" キー（閉じクォートの後にコロン ':' が続くもの）が存在するかを判定。
     *    文字列値の中に "songNotes": という文言が書かれている場合の誤判定を防止する。
     */
    fun isSkyStudioJson(content: String): Boolean {
        var i = 0
        val len = content.length

        // BOM除去
        if (i < len && content[i] == '\uFEFF') {
            i++
        }

        // 先頭空白スキップ
        while (i < len && content[i].isWhitespace()) {
            i++
        }

        if (i >= len) return false

        // 最初の有効文字が '{' または '[' であるか
        val firstChar = content[i]
        if (firstChar != '{' && firstChar != '[') {
            return false
        }

        // 文字列トークンを走査
        while (i < len) {
            val c = content[i]
            if (c == '"') {
                val strStart = i + 1
                i++
                var escaped = false
                while (i < len) {
                    val sc = content[i]
                    if (escaped) {
                        escaped = false
                        i++
                    } else if (sc == '\\') {
                        escaped = true
                        i++
                    } else if (sc == '"') {
                        break
                    } else {
                        i++
                    }
                }

                if (i >= len) {
                    return false
                }

                val strEnd = i
                val tokenLength = strEnd - strStart

                // 文字列内容が厳密に "songNotes" であり、直後に ':' が続くか確認
                if (tokenLength == TARGET_KEY.length &&
                    content.regionMatches(strStart, TARGET_KEY, 0, TARGET_KEY.length)
                ) {
                    var postIndex = i + 1
                    while (postIndex < len && content[postIndex].isWhitespace()) {
                        postIndex++
                    }
                    if (postIndex < len && content[postIndex] == ':') {
                        return true
                    }
                }
                i++
            } else {
                i++
            }
        }

        return false
    }

    companion object {
        private const val TARGET_KEY = "songNotes"

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
