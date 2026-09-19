package com.onigiri.keycue.ui.preview

import android.content.Context
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
import com.onigiri.keycue.playback.NoteScheduler
import com.onigiri.keycue.playback.PlaybackClock
import com.onigiri.keycue.playback.PlaybackEngine
import com.onigiri.keycue.playback.PlaybackState
import com.onigiri.keycue.playback.ScheduledNotes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * プレビュー再生画面のUI状態。
 */
data class PlaybackPreviewUiState(
    val songTitle: String = "",
    val durationMs: Long = 0L,
    val noteCount: Int = 0,
    val speed: Float = 1.0f,
    val noteLeadTimeMs: Long = PlaybackConfig.DEFAULT_NOTE_LEAD_TIME_MS,
    val approachCircleLeadTimeMs: Long = PlaybackConfig.DEFAULT_APPROACH_CIRCLE_LEAD_TIME_MS,
    val hasSong: Boolean = false
)

/**
 * プレビュー再生画面のViewModel。
 *
 * @param sessionRepository 演奏セッション情報リポジトリ
 * @param scheduler ノート発光スケジューラ
 */
class PlaybackPreviewViewModel(
    private val sessionRepository: PlaybackSessionRepository = InMemoryPlaybackSessionRepository.instance,
    private val settingsRepository: SettingsRepository = InMemorySettingsRepository.instance,
    val scheduler: NoteScheduler = NoteScheduler()
) : ViewModel() {

    val engine = PlaybackEngine(
        clock = PlaybackClock(),
        externalScope = viewModelScope
    )

    val playbackState: StateFlow<PlaybackState> = engine.state

    private val _uiState = MutableStateFlow(PlaybackPreviewUiState())
    val uiState: StateFlow<PlaybackPreviewUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            sessionRepository.currentSession.collect { session ->
                if (session != null && engine.songData !== session.song) {
                    setupWithSong(session.song, settingsRepository.playbackConfig.value)
                }
            }
        }
        viewModelScope.launch {
            settingsRepository.playbackConfig.collect(::applyPlaybackConfig)
        }
    }

    /**
     * 楽曲データおよび設定をエンジンとUIにセットアップする。
     */
    fun setupWithSong(song: SongData, config: PlaybackConfig) {
        engine.setSong(song)
        applyPlaybackConfig(config)
        _uiState.value = _uiState.value.copy(
            songTitle = song.title,
            durationMs = song.durationMs,
            noteCount = song.events.size,
            hasSong = true
        )
    }

    private fun applyPlaybackConfig(config: PlaybackConfig) {
        engine.countdownMs = config.countdownMs
        engine.setSpeed(config.speed)
        _uiState.value = _uiState.value.copy(
            speed = config.speed,
            noteLeadTimeMs = config.noteLeadTimeMs,
            approachCircleLeadTimeMs = config.approachCircleLeadTimeMs
        )
    }

    fun play() {
        engine.play()
    }

    fun pause() {
        engine.pause()
    }

    fun resume() {
        engine.resume()
    }

    fun stop() {
        engine.stop()
    }

    fun restart() {
        engine.restart()
    }

    fun seekBack() {
        engine.seekBack(10_000L)
    }

    fun seekTo(positionMs: Long) {
        engine.seekTo(positionMs)
    }

    fun adjustSpeed(delta: Float) {
        val currentSpeed = _uiState.value.speed
        val newSpeed = ((currentSpeed + delta) * 20).roundToInt() / 20.0f
        val clamped = newSpeed.coerceIn(0.25f, 2.0f)
        engine.setSpeed(clamped)
        _uiState.value = _uiState.value.copy(speed = clamped)
        viewModelScope.launch {
            settingsRepository.savePlaybackConfig(
                settingsRepository.playbackConfig.value.copy(speed = clamped)
            )
        }
    }

    /**
     * 現在時刻に基づいて、表示・発光すべきノートを抽出する。
     */
    fun getScheduledNotes(currentTimeMs: Long): ScheduledNotes {
        val events = engine.songData?.events ?: return ScheduledNotes(currentTimeMs, emptyList(), emptyList(), emptySet())
        return scheduler.schedule(
            events = events,
            currentTimeMs = currentTimeMs,
            noteLeadTimeMs = _uiState.value.noteLeadTimeMs,
            approachCircleLeadTimeMs = _uiState.value.approachCircleLeadTimeMs
        )
    }

    override fun onCleared() {
        super.onCleared()
        engine.release()
    }

    companion object {
        fun provideFactory(context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    PlaybackPreviewViewModel(
                        sessionRepository = InMemoryPlaybackSessionRepository.instance,
                        settingsRepository = SharedPreferencesSettingsRepository.getInstance(context.applicationContext)
                    ) as T
            }
    }
}
