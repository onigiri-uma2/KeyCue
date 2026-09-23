package com.onigiri.keycue.ui.home

import android.net.Uri
import com.onigiri.keycue.model.SongData
import com.onigiri.keycue.model.SongFormat

/**
 * HomeScreenのUI状態を表すデータクラス。
 *
 * @param songTitle 選択中の曲名（未選択時はnull）
 * @param durationMs 楽曲の総演奏時間（ミリ秒）
 * @param speed 再生速度比率 (1.0f = 100%)
 * @param noteLeadTimeMs ノート先読み時間（ミリ秒）
 * @param approachCircleLeadTimeMs タイミングサークル先読み時間（ミリ秒）
 * @param countdownMs 開始前カウントダウン時間（ミリ秒）
 * @param fitConfigured ボタン位置が設定済みかどうか
 * @param overlayPermissionGranted オーバーレイ権限が付与されているかどうか
 * @param canStart 演奏支援を開始できる状態かどうか
 * @param isLoading 読み込み中フラグ
 * @param errorMessage エラーメッセージ（存在する場合）
 * @param selectedSongUri 選択された楽曲の content:// URI
 * @param selectedFileName 選択されたファイルの表示名
 * @param selectedMimeType 選択されたファイルの MIME type
 * @param songFormat 判定された楽曲フォーマット
 * @param noteCount 演奏イベント（ノート）数
 * @param songData 解析済みの楽曲データ
 */
data class HomeUiState(
    val songTitle: String? = null,
    val durationMs: Long = 0L,
    val speed: Float = 1.0f,
    val noteLeadTimeMs: Long = com.onigiri.keycue.model.PlaybackConfig.DEFAULT_NOTE_LEAD_TIME_MS,
    val approachCircleLeadTimeMs: Long = com.onigiri.keycue.model.PlaybackConfig.DEFAULT_APPROACH_CIRCLE_LEAD_TIME_MS,
    val countdownMs: Long = 3000L,
    val fitConfigured: Boolean = false,
    val overlayPermissionGranted: Boolean = false,
    val canStart: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val selectedSongUri: Uri? = null,
    val selectedFileName: String? = null,
    val selectedMimeType: String? = null,
    val songFormat: SongFormat? = null,
    val noteCount: Int = 0,
    val songData: SongData? = null,
    val visualConfig: com.onigiri.keycue.model.VisualConfig = com.onigiri.keycue.model.VisualConfig(),
    val midiMappingSettings: com.onigiri.keycue.model.MidiMappingSettings = com.onigiri.keycue.model.MidiMappingSettings(),
    val resolvedMidiMapping: com.onigiri.keycue.model.ResolvedMidiMapping? = null,
    val controlOverlayConfig: com.onigiri.keycue.model.ControlOverlayConfig = com.onigiri.keycue.model.ControlOverlayConfig()
) {
    fun withoutSong(): HomeUiState = copy(
        songTitle = null,
        durationMs = 0L,
        canStart = false,
        selectedSongUri = null,
        selectedFileName = null,
        selectedMimeType = null,
        songFormat = null,
        noteCount = 0,
        songData = null,
        resolvedMidiMapping = null
    )
}
