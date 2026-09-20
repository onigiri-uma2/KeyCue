package com.onigiri.keycue.song

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import com.onigiri.keycue.model.SongData
import com.onigiri.keycue.model.SongFormat
import com.onigiri.keycue.song.midi.InvalidMidiException
import com.onigiri.keycue.song.midi.MidiParseException
import com.onigiri.keycue.song.midi.MidiParser
import com.onigiri.keycue.song.midi.UnsupportedMidiException
import com.onigiri.keycue.song.sky.EncryptedSkyStudioException
import com.onigiri.keycue.song.sky.InvalidSkyStudioJsonException
import com.onigiri.keycue.song.sky.SkyStudioJsonParser
import com.onigiri.keycue.song.sky.SkyStudioParseException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.FileNotFoundException
import java.io.IOException

/**
 * 楽曲読み込み結果を表す sealed interface。
 */
sealed interface SongLoadResult {
    /**
     * 読み込み成功（SongDataおよびメタデータ取得完了）
     */
    data class Success(
        val songData: SongData,
        val metadata: SongFileMetadata,
        val resolvedMidiMapping: com.onigiri.keycue.model.ResolvedMidiMapping? = null,
        val updatedMidiSettings: com.onigiri.keycue.model.MidiMappingSettings? = null
    ) : SongLoadResult

    /**
     * 読み込み失敗
     */
    sealed interface Failure : SongLoadResult {
        /**
         * 非対応のファイルフォーマット
         */
        data class UnsupportedFormat(
            val displayName: String,
            val mimeType: String?
        ) : Failure

        /**
         * ファイル読み取りエラー（ファイルが存在しない、権限エラーなど）
         */
        data class FileReadError(
            val message: String?,
            val cause: Throwable? = null
        ) : Failure

        /**
         * 不正なMIDIデータ
         */
        data class InvalidMidi(
            val message: String?,
            val cause: Throwable? = null
        ) : Failure

        /**
         * 未対応のMIDI形式
         */
        data class UnsupportedMidi(
            val message: String?,
            val cause: Throwable? = null
        ) : Failure

        /**
         * MIDI解析失敗
         */
        data class MidiParseError(
            val message: String?,
            val cause: Throwable? = null
        ) : Failure

        /**
         * 暗号化された譜面（復号不可）
         */
        data class EncryptedSong(
            val message: String,
            val cause: Throwable? = null
        ) : Failure

        /**
         * 不正なSky Studio JSONデータ
         */
        data class InvalidSkyStudioJson(
            val message: String?,
            val cause: Throwable? = null
        ) : Failure

        /**
         * Sky Studio JSON解析失敗
         */
        data class SkyStudioParseError(
            val message: String?,
            val cause: Throwable? = null
        ) : Failure
    }
}

/**
 * 楽曲ファイルの読み込みと解析を担当するクラス。
 *
 * ContentResolverからメタデータを取得し、フォーマット判定および各フォーマット対応のパーサーを呼び出して
 * 共通の [SongData] を生成する。
 *
 * @param formatDetector 楽曲フォーマット判定クラス
 * @param midiParser MIDI解析用パーサー
 * @param skyStudioJsonParser Sky Studio JSON解析用パーサー
 */
open class SongLoader(
    private val formatDetector: SongFormatDetector = SongFormatDetector(),
    private val midiParser: MidiParser = MidiParser(),
    private val skyStudioJsonParser: SongParser = SkyStudioJsonParser()
) {

    /**
     * 指定された URI から楽曲ファイルを読み込み、解析して [SongData] を生成する。
     *
     * @param contentResolver ContentResolver インスタンス
     * @param uri 選択された content:// URI
     * @param midiMappingSettings MIDIマッピング設定 (AUTO / MANUAL)
     * @param initializeManualFromAuto 新規ファイル選択時にAUTO解析結果を手動設定の初期値へ反映するかどうか
     * @return 読み込み結果 [SongLoadResult]
     */
    open suspend fun loadSong(
        contentResolver: ContentResolver,
        uri: Uri,
        midiMappingSettings: com.onigiri.keycue.model.MidiMappingSettings? = null,
        initializeManualFromAuto: Boolean = false
    ): SongLoadResult = withContext(Dispatchers.IO) {
        val displayName = queryDisplayName(contentResolver, uri) ?: "unknown"
        val mimeType = contentResolver.getType(uri)
        val initialFormat = formatDetector.detect(displayName, mimeType)

        // ファイルデータをバイト配列として取得
        val bytes = try {
            contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@withContext SongLoadResult.Failure.FileReadError("ファイルを開くことができませんでした")
        } catch (e: FileNotFoundException) {
            return@withContext SongLoadResult.Failure.FileReadError("ファイルが見つかりません", e)
        } catch (e: IOException) {
            return@withContext SongLoadResult.Failure.FileReadError("ファイルの読み込みに失敗しました: ${e.localizedMessage}", e)
        } catch (e: Exception) {
            return@withContext SongLoadResult.Failure.FileReadError(e.localizedMessage ?: "ファイルの読み取り中にエラーが発生しました", e)
        }

        loadFromBytes(
            bytes = bytes,
            displayName = displayName,
            mimeType = mimeType,
            uri = uri,
            midiMappingSettings = midiMappingSettings,
            initializeManualFromAuto = initializeManualFromAuto
        )
    }

    /**
     * バイト配列およびファイル情報から楽曲フォーマットを判定・解析して [SongLoadResult] を生成する。
     *
     * @param bytes 楽曲ファイルのバイト配列
     * @param displayName ファイル名
     * @param mimeType MIME type
     * @param uri 楽曲のURI（省略時はUri.EMPTY）
     * @param midiMappingSettings MIDIマッピング設定
     * @param initializeManualFromAuto 新規ファイル選択時にAUTO解析結果を手動設定の初期値へ反映するかどうか
     * @return 読み込み結果 [SongLoadResult]
     */
    fun loadFromBytes(
        bytes: ByteArray,
        displayName: String,
        mimeType: String?,
        uri: Uri? = null,
        midiMappingSettings: com.onigiri.keycue.model.MidiMappingSettings? = null,
        initializeManualFromAuto: Boolean = false
    ): SongLoadResult {
        val initialFormat = formatDetector.detect(displayName, mimeType)
        var format = initialFormat

        // もしMIDIヘッダ(MThd)があればMIDIにフォールバック
        if (format == SongFormat.UNKNOWN && bytes.size >= 4 &&
            bytes[0] == 'M'.code.toByte() && bytes[1] == 'T'.code.toByte() &&
            bytes[2] == 'h'.code.toByte() && bytes[3] == 'd'.code.toByte()
        ) {
            format = SongFormat.MIDI
        }

        // Sky Studio JSONの判定:
        // 拡張子/MIMEタイプからJSON候補、または未判定（.txtや拡張子なし等）の場合、
        // ファイル全体のテキストからSky Studio形式か判定する（固定長プレビュー依存の解消）。
        if (format == SongFormat.SKY_STUDIO_JSON || format == SongFormat.UNKNOWN) {
            val text = TextEncodingHelper.decodeText(bytes)
            if (formatDetector.isSkyStudioJson(text)) {
                format = SongFormat.SKY_STUDIO_JSON
            } else {
                format = SongFormat.UNKNOWN
            }
        }

        if (format == SongFormat.UNKNOWN) {
            return SongLoadResult.Failure.UnsupportedFormat(
                displayName = displayName,
                mimeType = mimeType
            )
        }

        val metadata = SongFileMetadata(
            uri = uri ?: Uri.EMPTY,
            displayName = displayName,
            mimeType = mimeType,
            format = format
        )

        return try {
            val (songData, resolvedMapping, updatedSettings) = when (format) {
                SongFormat.MIDI -> parseMidi(bytes, displayName, midiMappingSettings, initializeManualFromAuto)
                SongFormat.SKY_STUDIO_JSON -> {
                    val (song, mapping) = parseSkyStudioJson(bytes, displayName)
                    Triple(song, mapping, null)
                }
                else -> throw UnsupportedOperationException("Unsupported format: $format")
            }

            SongLoadResult.Success(
                songData = songData,
                metadata = metadata,
                resolvedMidiMapping = resolvedMapping,
                updatedMidiSettings = updatedSettings
            )
        } catch (e: Throwable) {
            mapToFailure(e)
        }
    }

    private fun parseMidi(
        bytes: ByteArray,
        displayName: String,
        midiMappingSettings: com.onigiri.keycue.model.MidiMappingSettings?,
        initializeManualFromAuto: Boolean
    ): Triple<SongData, com.onigiri.keycue.model.ResolvedMidiMapping, com.onigiri.keycue.model.MidiMappingSettings?> {
        val currentSettings = midiMappingSettings ?: com.onigiri.keycue.model.MidiMappingSettings()
        val extracted = midiParser.extractRawEvents(bytes)

        val (resolved, updatedSettings) = if (initializeManualFromAuto) {
            // AUTO解析を実行し、曲の最適なRoot/Scale/BaseOctaveを取得
            val autoSettings = currentSettings.copy(mode = com.onigiri.keycue.model.MidiMappingMode.AUTO)
            val autoResolved = com.onigiri.keycue.song.midi.MidiKeyMapper.resolveMapping(extracted.rawEvents, autoSettings)

            // AUTO解析結果を手動設定の初期値へ反映（modeは現在のものを維持）
            val updated = currentSettings.copy(
                manualRoot = autoResolved.root,
                manualScale = autoResolved.scale,
                manualBaseOctave = autoResolved.baseOctave
            )
            // AUTO modeでは autoResolved をそのまま finalResolved として再利用し、AUTO探索を2回しない。
            // MANUAL modeの場合も手動値がautoResolvedと完全に同一になるため、探索結果autoResolvedをそのままfinalResolvedとして再利用可能。
            Pair(autoResolved, updated)
        } else {
            val resolved = com.onigiri.keycue.song.midi.MidiKeyMapper.resolveMapping(extracted.rawEvents, currentSettings)
            Pair(resolved, null)
        }

        val song = midiParser.parseWithResolvedMapping(bytes, displayName, resolved)
        return Triple(song, resolved, updatedSettings)
    }

    private fun parseSkyStudioJson(
        bytes: ByteArray,
        displayName: String
    ): Pair<SongData, com.onigiri.keycue.model.ResolvedMidiMapping?> {
        val song = ByteArrayInputStream(bytes).use { inputStream ->
            skyStudioJsonParser.parse(inputStream, displayName)
        }
        return Pair(song, null)
    }

    private fun mapToFailure(e: Throwable): SongLoadResult.Failure {
        return when (e) {
            is EncryptedSkyStudioException -> SongLoadResult.Failure.EncryptedSong(
                message = e.localizedMessage ?: "このSky Studio譜面は暗号化されているため読み込めません。暗号化なしでエクスポートしたファイルを使用してください。",
                cause = e
            )
            is InvalidSkyStudioJsonException -> SongLoadResult.Failure.InvalidSkyStudioJson(
                message = e.localizedMessage ?: "不正なSky Studio JSONファイルです",
                cause = e
            )
            is SkyStudioParseException -> SongLoadResult.Failure.SkyStudioParseError(
                message = e.localizedMessage ?: "Sky Studio JSONファイルの解析に失敗しました",
                cause = e
            )
            is InvalidMidiException -> SongLoadResult.Failure.InvalidMidi(
                message = e.localizedMessage ?: "不正なMIDIファイルです",
                cause = e
            )
            is UnsupportedMidiException -> SongLoadResult.Failure.UnsupportedMidi(
                message = e.localizedMessage ?: "未対応のMIDIファイル形式です",
                cause = e
            )
            is MidiParseException -> SongLoadResult.Failure.MidiParseError(
                message = e.localizedMessage ?: "MIDIファイルの解析に失敗しました",
                cause = e
            )
            else -> SongLoadResult.Failure.FileReadError(
                message = e.localizedMessage ?: "ファイルの処理中にエラーが発生しました",
                cause = e
            )
        }
    }

    /**
     * 指定されたURIから楽曲のメタデータおよび演奏データを取得する。
     */
    suspend fun loadMetadata(
        contentResolver: ContentResolver,
        uri: Uri
    ): SongLoadResult = loadSong(contentResolver, uri)

    private fun queryDisplayName(contentResolver: ContentResolver, uri: Uri): String? {
        return try {
            contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) {
                        cursor.getString(index)
                    } else null
                } else null
            }
        } catch (_: Exception) {
            null
        } ?: uri.lastPathSegment
    }
}
