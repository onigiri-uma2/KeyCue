package com.onigiri.keycue.data

import android.net.Uri
import com.onigiri.keycue.model.FitProfile
import com.onigiri.keycue.model.PlaybackConfig
import com.onigiri.keycue.model.PlaybackSession
import com.onigiri.keycue.model.SongData
import com.onigiri.keycue.model.SongFormat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 現在の演奏セッションを保持・提供するリポジトリインターフェース。
 */
interface PlaybackSessionRepository {
    val currentSession: StateFlow<PlaybackSession?>

    fun setSession(
        songData: SongData,
        config: PlaybackConfig,
        fitProfile: FitProfile = FitProfile.createDefaultTestProfile(),
        uri: Uri? = null,
        format: SongFormat? = null,
        resolvedMidiMapping: com.onigiri.keycue.model.ResolvedMidiMapping? = null
    )
    fun clearSession()
}

/**
 * プロセス内メモリでセッションを保持するインメモリ実装。
 */
class InMemoryPlaybackSessionRepository : PlaybackSessionRepository {
    private val _currentSession = MutableStateFlow<PlaybackSession?>(null)
    override val currentSession: StateFlow<PlaybackSession?> = _currentSession.asStateFlow()

    override fun setSession(
        songData: SongData,
        config: PlaybackConfig,
        fitProfile: FitProfile,
        uri: Uri?,
        format: SongFormat?,
        resolvedMidiMapping: com.onigiri.keycue.model.ResolvedMidiMapping?
    ) {
        _currentSession.value = PlaybackSession(songData, config, fitProfile, uri, format, resolvedMidiMapping)
    }

    override fun clearSession() {
        _currentSession.value = null
    }

    companion object {
        val instance: PlaybackSessionRepository by lazy { InMemoryPlaybackSessionRepository() }
    }
}
