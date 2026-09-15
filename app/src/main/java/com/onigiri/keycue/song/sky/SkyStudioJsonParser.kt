package com.onigiri.keycue.song.sky

import com.onigiri.keycue.model.NoteEvent
import com.onigiri.keycue.model.SongData
import com.onigiri.keycue.song.SongParser
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener
import java.io.InputStream
import java.nio.charset.StandardCharsets

/**
 * Sky Studio が出力した暗号化されていない JSON 譜面を解析し、[SongData] を生成するパーサー。
 *
 * 対応仕様:
 * - トップレベル配列 `[ { ... } ]` および 単一オブジェクト `{ ... }` の両方
 * - `isEncrypted: true` 検出時の [EncryptedSkyStudioException]
 * - `songNotes` 配列からの絶対時間（ms）およびキー表現の抽出
 * - [SkyKeyMapper] による 15 キー (0..14) への変換
 * - 和音の保持（同一時刻の異なるキーをすべて残す）
 * - 同一キー・同一時刻の完全重複ノートの deduplicate
 * - 曲名 (`name`) の取得（未設定時はデフォルトタイトル使用）
 *
 * @param keyMapper Sky Studioキー表現を内部キー (0..14) へマッピングする [SkyKeyMapper]
 */
class SkyStudioJsonParser(
    private val keyMapper: SkyKeyMapper = DefaultSkyKeyMapper()
) : SongParser {

    override fun parse(inputStream: InputStream, title: String): SongData {
        val jsonString = try {
            val bytes = inputStream.readBytes()
            com.onigiri.keycue.song.TextEncodingHelper.decodeText(bytes)
        } catch (e: Exception) {
            throw SkyStudioParseException("ストリームの読み込みに失敗しました", e)
        }
        return parse(jsonString, title)
    }

    /**
     * JSON 文字列から Sky Studio 譜面データを解析し、[SongData] を生成する。
     *
     * @param jsonString Sky Studio JSON 文字列
     * @param defaultTitle デフォルトの楽曲タイトル（JSON内に曲名がない場合に使用）
     * @return 解析された [SongData]
     */
    fun parse(jsonString: String, defaultTitle: String): SongData {
        val trimmed = jsonString.trim().removePrefix("\uFEFF").trim()
        if (trimmed.isEmpty()) {
            throw InvalidSkyStudioJsonException("JSONデータが空です")
        }

        val rootObject: JSONObject = try {
            val tokener = JSONTokener(trimmed)
            when (val value = tokener.nextValue()) {
                is JSONArray -> {
                    if (value.length() == 0) {
                        throw InvalidSkyStudioJsonException("JSON配列が空です")
                    }
                    val first = value.optJSONObject(0)
                        ?: throw InvalidSkyStudioJsonException("JSON配列の先頭要素がオブジェクトではありません")
                    first
                }
                is JSONObject -> value
                else -> throw InvalidSkyStudioJsonException("無効なJSONルート要素です")
            }
        } catch (e: JSONException) {
            throw InvalidSkyStudioJsonException("JSONの構文解析に失敗しました: ${e.message}", e)
        }

        // 1. 暗号化チェック（暗号化された譜面ファイルは解析不可としてエラーをスロー）
        val isEncrypted = rootObject.optBoolean("isEncrypted", false)
        if (isEncrypted) {
            throw EncryptedSkyStudioException()
        }

        // 2. 曲名の取得（未設定や空文字の場合はファイル名などのデフォルト値を使用）
        val songName = rootObject.optString("name", "").trim()
        val resolvedTitle = if (songName.isNotEmpty()) songName else defaultTitle

        // 3. songNotes 配列の検証と取得（譜面本体が存在しない場合は構文エラー）
        val songNotesArray = rootObject.optJSONArray("songNotes")
            ?: throw InvalidSkyStudioJsonException("Sky Studio譜面に必要な 'songNotes' が見つかりません")

        // 4. ノートの抽出と15キー変換
        val rawNotes = mutableListOf<RawSkyStudioNote>()
        for (i in 0 until songNotesArray.length()) {
            val noteObj = songNotesArray.optJSONObject(i) ?: continue

            val timeMs = if (noteObj.has("time")) {
                noteObj.optLong("time", -1L)
            } else {
                continue
            }
            if (timeMs < 0L) continue

            val keyStr = noteObj.optString("key", "").trim()
            if (keyStr.isEmpty()) continue

            val mappedKey = keyMapper.map(keyStr) ?: continue
            val track = keyMapper.extractTrack(keyStr)

            rawNotes.add(RawSkyStudioNote(timeMs = timeMs, key = mappedKey, track = track))
        }

        // 5. 重複ノートの除外（同一時刻・同一キーの完全重複のみを除去し、和音などの異キー同時打鍵は保持）
        val distinctNotes = rawNotes.distinctBy { it.timeMs to it.key }

        // 6. NoteEvent 生成 & 時刻順・キー順ソート
        val noteEvents = distinctNotes.map {
            NoteEvent(timeMs = it.timeMs, key = it.key)
        }.sortedWith(compareBy({ it.timeMs }, { it.key }))

        // 7. durationMs の算出（最終ノート時刻を楽曲全体の長さとする）
        val durationMs = noteEvents.maxOfOrNull { it.timeMs } ?: 0L

        return SongData(
            title = resolvedTitle,
            durationMs = durationMs,
            events = noteEvents
        )
    }

    /**
     * パース段階のノート情報（トラック情報を保持可能）。
     */
    private data class RawSkyStudioNote(
        val timeMs: Long,
        val key: Int,
        val track: Int? = null
    )
}
