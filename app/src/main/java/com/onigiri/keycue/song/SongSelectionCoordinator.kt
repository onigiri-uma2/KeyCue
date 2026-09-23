package com.onigiri.keycue.song

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import com.onigiri.keycue.data.InMemoryPlaybackSessionRepository
import com.onigiri.keycue.data.PlaybackSessionRepository
import com.onigiri.keycue.data.SettingsRepository
import com.onigiri.keycue.model.FitProfile
import com.onigiri.keycue.model.PlaybackConfig
import com.onigiri.keycue.model.RecentSongEntry
import com.onigiri.keycue.model.SongData

/**
 * 楽曲読み込みエラーの詳細を保持する例外クラス。
 */
class SongLoadException(
    message: String,
    val failure: SongLoadResult.Failure? = null
) : Exception(message)

/**
 * 楽曲の選択・読み込み・セッション更新を統括するコーディネーター。
 *
 * HomeScreenおよびOverlayの両方から共通して利用され、
 * URIパーミッション取得、SongLoaderによる楽曲パース、PlaybackSessionRepository更新、
 * およびSettingsRepositoryへのlastSongUri保存を単一のデータフローで実行する。
 */
class SongSelectionCoordinator(
    private val contentResolver: ContentResolver,
    private val songLoader: SongLoader = SongLoader(),
    private val sessionRepository: PlaybackSessionRepository = InMemoryPlaybackSessionRepository.instance,
    private val settingsRepository: SettingsRepository
) {

    /**
     * 指定された URI から楽曲を読み込み、現在のセッションおよび保存設定を更新する。
     *
     * @param uri 選択された楽曲ファイルの content:// URI
     * @param midiMappingSettings MIDIマッピング設定 (null の場合は settingsRepository の設定を使用)
     * @param initializeManualFromAuto 新規MIDIファイル選択時にAUTO解析結果を手動設定の初期値へ反映するかどうか
     * @return 読み込みに成功した場合は [Result.success] (SongData)、失敗時は [Result.failure] (SongLoadException)
     */
    suspend fun select(
        uri: Uri,
        midiMappingSettings: com.onigiri.keycue.model.MidiMappingSettings? = null,
        initializeManualFromAuto: Boolean = false
    ): Result<SongData> {
        // 1. 永続URIパーミッション取得の試行 (Provider非対応でもクラッシュしない)
        try {
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            contentResolver.takePersistableUriPermission(uri, takeFlags)
        } catch (_: SecurityException) {
            // 永続化をサポートしていないProviderやフラグの場合は継続
        } catch (_: Exception) {
            // その他の例外でもクラッシュさせず継続
        }

        // 2. 使用するMidiMappingSettingsの決定
        val mappingSettings = midiMappingSettings ?: settingsRepository.midiMappingSettings.value

        // 3. SongLoader による楽曲解析
        return when (val result = songLoader.loadSong(contentResolver, uri, mappingSettings, initializeManualFromAuto)) {
            is SongLoadResult.Success -> {
                val songData = result.songData

                // 4. 現在の設定 (PlaybackConfig, FitProfile) を維持して新Sessionを構築
                val config = settingsRepository.playbackConfig.value
                val fitProfile = settingsRepository.fitProfile.value
                    ?: FitProfile.createDefaultTestProfile()

                // 5. PlaybackSessionRepository へ一度に反映
                sessionRepository.setSession(
                    songData = songData,
                    config = config,
                    fitProfile = fitProfile,
                    uri = uri,
                    format = result.metadata.format,
                    resolvedMidiMapping = result.resolvedMidiMapping
                )

                // 6. SettingsRepository へ lastSongUri を保存
                settingsRepository.saveLastSongUri(uri.toString())

                // 最近使った曲リストへ追加（MRU）
                val songTitle = songData.title.ifBlank { result.metadata.displayName.ifBlank { "楽曲" } }
                settingsRepository.addRecentSong(RecentSongEntry(uri.toString(), songTitle))

                // 7. 全読み込み成功後にのみ、AUTO解析結果による手動設定更新を永続化
                if (result.updatedMidiSettings != null) {
                    settingsRepository.saveMidiMappingSettings(result.updatedMidiSettings)
                }

                Result.success(songData)
            }

            is SongLoadResult.Failure -> {
                val errorMessage = when (result) {
                    is SongLoadResult.Failure.UnsupportedFormat ->
                        "非対応のファイル形式です (.mid, .midi, .json, .txt に対応)"
                    is SongLoadResult.Failure.EncryptedSong ->
                        result.message
                    is SongLoadResult.Failure.InvalidSkyStudioJson ->
                        "不正なSky Studio譜面です: ${result.message ?: "形式が壊れています"}"
                    is SongLoadResult.Failure.SkyStudioParseError ->
                        "Sky Studio JSON解析失敗: ${result.message ?: "解析処理中にエラーが発生しました"}"
                    is SongLoadResult.Failure.InvalidMidi ->
                        "不正なMIDIファイルです: ${result.message ?: "形式が壊れています"}"
                    is SongLoadResult.Failure.UnsupportedMidi ->
                        "未対応のMIDIファイルです: ${result.message ?: "Format 0または1のみ対応しています"}"
                    is SongLoadResult.Failure.MidiParseError ->
                        "MIDI解析失敗: ${result.message ?: "解析処理中にエラーが発生しました"}"
                    is SongLoadResult.Failure.FileReadError ->
                        "ファイル読み込み失敗: ${result.message ?: "ファイルを開くことができませんでした"}"
                }
                Result.failure(SongLoadException(errorMessage, result))
            }
        }
    }

    /**
     * 現在読み込み中の楽曲がMIDIの場合、新設定で再マッピングを実行する。
     *
     * @param midiMappingSettings 適用する新しいMIDIマッピング設定
     * @return 成功時は [Result.success] (SongData)、失敗またはMIDI未読み込み時は [Result.failure]
     */
    suspend fun reapplyMapping(
        midiMappingSettings: com.onigiri.keycue.model.MidiMappingSettings
    ): Result<SongData> {
        val currentSession = sessionRepository.currentSession.value
        val uri = currentSession?.uri
            ?: return Result.failure(IllegalStateException("再マッピング対象の楽曲URIが存在しません"))

        if (currentSession.format != com.onigiri.keycue.model.SongFormat.MIDI) {
            return Result.failure(IllegalStateException("現在の楽曲はMIDI形式ではありません"))
        }

        return select(uri, midiMappingSettings, initializeManualFromAuto = false)
    }
}
