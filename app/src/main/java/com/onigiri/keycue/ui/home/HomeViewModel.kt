package com.onigiri.keycue.ui.home

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.onigiri.keycue.data.InMemoryPlaybackSessionRepository
import com.onigiri.keycue.data.InMemorySettingsRepository
import com.onigiri.keycue.data.PlaybackSessionRepository
import com.onigiri.keycue.data.SettingsRepository
import com.onigiri.keycue.data.SharedPreferencesSettingsRepository
import com.onigiri.keycue.model.PlaybackConfig
import com.onigiri.keycue.model.SongData
import com.onigiri.keycue.song.SongFileMetadata
import com.onigiri.keycue.song.SongSelectionCoordinator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * ホーム画面（[com.onigiri.keycue.ui.home.HomeScreen]）のViewModel。
 *
 * [SettingsRepository]（永続設定）および [PlaybackSessionRepository]（実行時楽曲セッション）を
 * Single Source of Truth として状態を統合・同期し、UI状態 [HomeUiState] を提供します。
 *
 * @param settingsRepository 設定およびURI永続化リポジトリ
 * @param sessionRepository 演奏セッションリポジトリ
 * @param songLoader 楽曲読み込み・フォーマット判定ローダー
 */
class HomeViewModel(
    private val settingsRepository: SettingsRepository = InMemorySettingsRepository(),
    private val sessionRepository: PlaybackSessionRepository = InMemoryPlaybackSessionRepository(),
    private val songSelectionCoordinator: SongSelectionCoordinator? = null,
    externalScope: kotlinx.coroutines.CoroutineScope? = null
) : ViewModel() {

    private val scope: kotlinx.coroutines.CoroutineScope = externalScope ?: viewModelScope

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        // 設定およびセッションの変更フローを合成し、UiState へアトミックに同期
        scope.launch {
            kotlinx.coroutines.flow.combine(
                settingsRepository.playbackConfig,
                settingsRepository.fitProfile,
                settingsRepository.visualConfig,
                settingsRepository.midiMappingSettings,
                sessionRepository.currentSession
            ) { config, profile, visualConfig, mappingSettings, session ->
                RepositoryState(config, profile, visualConfig, mappingSettings, session)
            }.collect { state ->
                _uiState.update { current ->
                    val base = if (state.session != null) {
                        current.copy(
                            songTitle = state.session.song.title,
                            durationMs = state.session.song.durationMs,
                            noteCount = state.session.song.events.size,
                            selectedSongUri = state.session.uri,
                            songFormat = state.session.format,
                            songData = state.session.song,
                            resolvedMidiMapping = state.session.resolvedMidiMapping,
                            canStart = true
                        )
                    } else {
                        current.withoutSong()
                    }

                    base.copy(
                        speed = state.config.speed,
                        noteLeadTimeMs = state.config.noteLeadTimeMs,
                        approachCircleLeadTimeMs = state.config.approachCircleLeadTimeMs,
                        countdownMs = state.config.countdownMs,
                        fitConfigured = state.profile != null,
                        visualConfig = state.visualConfig,
                        midiMappingSettings = state.mappingSettings
                    )
                }
            }
        }
    }

    private data class RepositoryState(
        val config: PlaybackConfig,
        val profile: com.onigiri.keycue.model.FitProfile?,
        val visualConfig: com.onigiri.keycue.model.VisualConfig,
        val mappingSettings: com.onigiri.keycue.model.MidiMappingSettings,
        val session: com.onigiri.keycue.model.PlaybackSession?
    )

    /**
     * 保存済みの FitProfile を取得する。
     */
    fun getSavedFitProfile(): com.onigiri.keycue.model.FitProfile? =
        settingsRepository.fitProfile.value

    /**
     * アプリ起動時等に、前回選択されていた楽曲URIの自動読み込み・復元を試行する。
     */
    fun tryRestoreLastSong() {
        val uriStr = settingsRepository.lastSongUri.value ?: return
        if (_uiState.value.songData != null || sessionRepository.currentSession.value != null) return

        val uri = try {
            Uri.parse(uriStr)
        } catch (_: Exception) {
            scope.launch { settingsRepository.saveLastSongUri(null) }
            return
        }

        val coordinator = songSelectionCoordinator ?: return
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = coordinator.select(uri)
            if (result.isSuccess) {
                _uiState.update { it.copy(isLoading = false, errorMessage = null) }
            } else {
                _uiState.update { current ->
                    current.copy(
                        isLoading = false,
                        errorMessage = "前回のファイルを読み込めませんでした。ファイルを再選択してください"
                    )
                }
                settingsRepository.saveLastSongUri(null)
            }
        }
    }

    /**
     * ファイルが選択された際の処理。
     * SongSelectionCoordinator を介して楽曲解析およびセッション更新を行う。
     */
    fun onFileSelected(uri: Uri?) {
        if (uri == null) return

        val coordinator = songSelectionCoordinator ?: return
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = coordinator.select(uri)
            if (result.isSuccess) {
                _uiState.update { it.copy(isLoading = false, errorMessage = null) }
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "ファイルの読み込みに失敗しました"
                _uiState.update { current ->
                    current.copy(
                        isLoading = false,
                        errorMessage = errorMsg
                    )
                }
            }
        }
    }

    /** Legacy entry point kept for callers that already parsed a song. */
    suspend fun applySongData(songData: SongData, metadata: SongFileMetadata) {
        settingsRepository.saveLastSongUri(metadata.uri.toString())
        sessionRepository.setSession(
            songData = songData,
            config = settingsRepository.playbackConfig.value,
            fitProfile = settingsRepository.fitProfile.value
                ?: com.onigiri.keycue.model.FitProfile.createDefaultTestProfile(),
            uri = metadata.uri,
            format = metadata.format
        )
        _uiState.update {
            it.copy(selectedFileName = metadata.displayName, selectedMimeType = metadata.mimeType)
        }
    }

    /** Legacy metadata-only entry point. New file selection should use [onFileSelected]. */
    suspend fun applySongMetadata(metadata: SongFileMetadata) {
        settingsRepository.saveLastSongUri(metadata.uri.toString())
        _uiState.update {
            it.copy(
                selectedSongUri = metadata.uri,
                selectedFileName = metadata.displayName,
                selectedMimeType = metadata.mimeType,
                songFormat = metadata.format,
                songTitle = metadata.displayName,
                canStart = true,
                isLoading = false,
                errorMessage = null
            )
        }
    }

    /**
     * MIDIマッピングモード (AUTO / MANUAL) を切り替える。
     * 現在MIDIが読み込まれている場合は新設定で再マッピングを実行する。
     */
    fun setMidiMappingMode(mode: com.onigiri.keycue.model.MidiMappingMode) {
        val updated = _uiState.value.midiMappingSettings.copy(mode = mode)
        applyMappingChange(updated)
    }

    /**
     * 手動マッピングのRoot音を変更する。
     */
    fun setManualRoot(root: com.onigiri.keycue.model.PitchClass) {
        val updated = _uiState.value.midiMappingSettings.copy(manualRoot = root)
        applyMappingChange(updated)
    }

    /**
     * 手動マッピングのスケール種別を変更する。
     */
    fun setManualScale(scale: com.onigiri.keycue.model.ScaleType) {
        val updated = _uiState.value.midiMappingSettings.copy(manualScale = scale)
        applyMappingChange(updated)
    }

    /**
     * 手動マッピングの開始オクターブを変更する。
     */
    fun setManualBaseOctave(octave: Int) {
        val updated = _uiState.value.midiMappingSettings.copy(manualBaseOctave = octave.coerceIn(2, 6))
        applyMappingChange(updated)
    }

    private fun applyMappingChange(newSettings: com.onigiri.keycue.model.MidiMappingSettings) {
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            settingsRepository.saveMidiMappingSettings(newSettings)
            val session = sessionRepository.currentSession.value
            if (session != null && session.format == com.onigiri.keycue.model.SongFormat.MIDI && session.uri != null) {
                val coordinator = songSelectionCoordinator ?: return@launch
                val result = coordinator.reapplyMapping(newSettings)
                if (result.isFailure) {
                    val errorMsg = result.exceptionOrNull()?.message ?: "マッピングの再適用に失敗しました"
                    _uiState.update { it.copy(errorMessage = errorMsg) }
                }
            }
        }
    }

    /**
     * 再生速度をデルタ分変更する（最小0.25x、最大2.0x、0.05刻み）
     */
    fun adjustSpeed(delta: Float) {
        val newSpeed = ((_uiState.value.speed + delta) * 100).roundToInt() / 100f
        val clamped = newSpeed.coerceIn(0.25f, 2.0f)
        _uiState.update { it.copy(speed = clamped) }
        scope.launch {
            settingsRepository.saveSpeed(clamped)
        }
    }

    /**
     * ノート先読み時間をデルタ分変更する（最小300ms、最大2000ms、100ms刻み）
     * サークル時間が超過する場合は自然に追従する。
     */
    fun adjustNoteLeadTime(deltaMs: Long) {
        val currentNote = _uiState.value.noteLeadTimeMs
        val currentCircle = _uiState.value.approachCircleLeadTimeMs
        val candidateNote = currentNote + deltaMs
        val (clampedNote, clampedCircle) = PlaybackConfig.normalizeLeadTimes(candidateNote, currentCircle)
        _uiState.update { it.copy(noteLeadTimeMs = clampedNote, approachCircleLeadTimeMs = clampedCircle) }
        scope.launch {
            settingsRepository.saveNoteLeadTimeMs(clampedNote)
        }
    }

    /**
     * タイミングサークル先読み時間をデルタ分変更する（最小100ms、最大1000ms、50ms刻み）
     * ノート先読み時間を超える値にならないよう正規化する。
     */
    fun adjustApproachCircleLeadTime(deltaMs: Long) {
        val currentNote = _uiState.value.noteLeadTimeMs
        val currentCircle = _uiState.value.approachCircleLeadTimeMs
        val candidateCircle = currentCircle + deltaMs
        val (_, clampedCircle) = PlaybackConfig.normalizeLeadTimes(currentNote, candidateCircle)
        _uiState.update { it.copy(approachCircleLeadTimeMs = clampedCircle) }
        scope.launch {
            settingsRepository.saveApproachCircleLeadTimeMs(clampedCircle)
        }
    }

    /**
     * カウントダウン時間を設定する（例: 0ms, 1000ms, 3000ms, 5000ms）
     */
    fun setCountdownMs(countdownMs: Long) {
        val clamped = countdownMs.coerceIn(0L, 5000L)
        _uiState.update { it.copy(countdownMs = clamped) }
        scope.launch {
            settingsRepository.saveCountdownMs(clamped)
        }
    }

    /** Test and migration helper; production file selection uses [onFileSelected]. */
    fun setSongData(song: SongData?) {
        if (song == null) {
            sessionRepository.clearSession()
        } else {
            sessionRepository.setSession(
                songData = song,
                config = settingsRepository.playbackConfig.value,
                fitProfile = settingsRepository.fitProfile.value
                    ?: com.onigiri.keycue.model.FitProfile.createDefaultTestProfile()
            )
        }
    }

    /**
     * ボタン位置設定状態の更新
     */
    fun setFitConfigured(configured: Boolean) {
        _uiState.update { current ->
            current.copy(
                fitConfigured = configured,
                canStart = current.songData != null
            )
        }
    }

    /**
     * オーバーレイ権限状態の更新
     */
    fun setOverlayPermissionGranted(granted: Boolean) {
        _uiState.update { current ->
            current.copy(
                overlayPermissionGranted = granted,
                canStart = current.songData != null
            )
        }
    }

    /**
     * OverlayPermissionManagerを利用して権限状態を同期する
     */
    fun refreshOverlayPermission(permissionManager: com.onigiri.keycue.permission.OverlayPermissionManager) {
        setOverlayPermissionGranted(permissionManager.canDrawOverlays())
    }

    /**
     * エラーメッセージのクリア
     */
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    // --- VisualConfig 更新メソッド ---

    /**
     * VisualConfig を一括・ラムダ式で更新する。
     */
    fun updateVisualConfig(transform: (com.onigiri.keycue.model.VisualConfig) -> com.onigiri.keycue.model.VisualConfig) {
        scope.launch {
            settingsRepository.updateVisualConfig(transform)
        }
    }

    fun setShowGuideLabels(show: Boolean) = updateVisualConfig { it.copy(showGuideLabels = show) }
    fun setShowFallingNotes(show: Boolean) = updateVisualConfig { it.copy(showFallingNotes = show) }
    fun setShowApproachCircles(show: Boolean) = updateVisualConfig { it.copy(showApproachCircles = show) }
    fun setShowRepeatCountBadge(show: Boolean) = updateVisualConfig { it.copy(showRepeatCountBadge = show) }
    fun setShowJustEffect(show: Boolean) = updateVisualConfig { it.copy(showJustEffect = show) }
    fun setGuideRadiusRatio(ratio: Float) = updateVisualConfig { it.copy(guideRadiusRatio = ratio) }
    fun setGuideColor(color: Int) = updateVisualConfig { it.copy(guideColor = color) }
    fun setNoteColorTop(color: Int) = updateVisualConfig { it.copy(noteColorTop = color) }
    fun setNoteColorMiddle(color: Int) = updateVisualConfig { it.copy(noteColorMiddle = color) }
    fun setNoteColorBottom(color: Int) = updateVisualConfig { it.copy(noteColorBottom = color) }
    fun setShowChordLinks(show: Boolean) = updateVisualConfig { it.copy(showChordLinks = show) }
    fun setShowChordHalos(show: Boolean) = updateVisualConfig { it.copy(showChordHalos = show) }

    companion object {
        fun provideFactory(context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val appContext = context.applicationContext
                    val settings = SharedPreferencesSettingsRepository.getInstance(appContext)
                    val session = InMemoryPlaybackSessionRepository.instance
                    val coordinator = SongSelectionCoordinator(
                        contentResolver = appContext.contentResolver,
                        sessionRepository = session,
                        settingsRepository = settings
                    )
                    return HomeViewModel(
                        settingsRepository = settings,
                        sessionRepository = session,
                        songSelectionCoordinator = coordinator
                    ) as T
                }
            }
    }
}
