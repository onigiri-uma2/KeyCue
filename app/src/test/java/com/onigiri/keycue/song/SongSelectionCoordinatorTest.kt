package com.onigiri.keycue.song

import android.content.ContentResolver
import android.content.FakeContentResolver
import android.net.FakeUri
import android.net.Uri
import com.onigiri.keycue.data.InMemoryPlaybackSessionRepository
import com.onigiri.keycue.data.InMemorySettingsRepository
import com.onigiri.keycue.model.FitProfile
import com.onigiri.keycue.model.NormalizedPoint
import com.onigiri.keycue.model.NoteEvent
import com.onigiri.keycue.model.PlaybackConfig
import com.onigiri.keycue.model.PlaybackSession
import com.onigiri.keycue.model.SongData
import com.onigiri.keycue.model.SongFormat
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SongSelectionCoordinatorTest {

    private lateinit var contentResolver: FakeContentResolver
    private lateinit var settingsRepository: InMemorySettingsRepository
    private lateinit var sessionRepository: InMemoryPlaybackSessionRepository

    private val sampleUri = FakeUri("content://com.android.providers.media.documents/document/audio%3A100")
    private val sampleSongData = SongData(
        title = "NewSong.mid",
        durationMs = 180000L,
        events = listOf(
            NoteEvent(0L, 0),
            NoteEvent(500L, 1),
            NoteEvent(1000L, 2)
        )
    )
    private val sampleMetadata = SongFileMetadata(
        uri = sampleUri,
        displayName = "NewSong.mid",
        mimeType = "audio/midi",
        format = SongFormat.MIDI
    )

    @Before
    fun setUp() {
        contentResolver = FakeContentResolver()
        settingsRepository = InMemorySettingsRepository(
            initialSpeed = 1.25f,
            initialNoteLeadTimeMs = 800L,
            initialApproachCircleLeadTimeMs = 400L,
            initialCountdownMs = 2000L
        )
        sessionRepository = InMemoryPlaybackSessionRepository()
    }

    @Test
    fun select_onSuccess_usesLatestSettingsInsteadOfStaleSessionSnapshot() = runBlocking {
        val customFitProfile = FitProfile(
            keyCenters = (0..14).map { NormalizedPoint(it * 0.05f, 0.5f) },
            keyRadiusRatio = 0.04f,
            landscape = true
        )
        val initialSong = SongData("OldSong.mid", 120000L, emptyList())
        val customConfig = PlaybackConfig(speed = 1.5f, noteLeadTimeMs = 900L, approachCircleLeadTimeMs = 350L, countdownMs = 1000L)
        val staleConfig = PlaybackConfig(speed = 0.5f, noteLeadTimeMs = 400L)
        val staleProfile = FitProfile.createDefaultTestProfile()
        sessionRepository.setSession(initialSong, staleConfig, staleProfile)
        settingsRepository.savePlaybackConfig(customConfig)
        settingsRepository.saveFitProfile(customFitProfile)

        val fakeSongLoader = object : SongLoader() {
            override suspend fun loadSong(
                contentResolver: ContentResolver,
                uri: Uri,
                midiMappingSettings: com.onigiri.keycue.model.MidiMappingSettings?
            ): SongLoadResult {
                return SongLoadResult.Success(sampleSongData, sampleMetadata)
            }
        }

        val coordinator = SongSelectionCoordinator(
            contentResolver = contentResolver,
            songLoader = fakeSongLoader,
            sessionRepository = sessionRepository,
            settingsRepository = settingsRepository
        )

        val result = coordinator.select(sampleUri)

        // 読み込み成功
        assertTrue(result.isSuccess)
        assertEquals(sampleSongData, result.getOrNull())

        // persistable permission 取得が呼ばれたこと
        assertEquals(sampleUri, contentResolver.lastTakePermissionUri)

        // lastSongUri が更新されたこと
        assertEquals(sampleUri.toString(), settingsRepository.lastSongUri.value)

        // PlaybackSession が更新されたこと
        val newSession = sessionRepository.currentSession.value
        assertNotNull(newSession)
        assertEquals(sampleSongData, newSession?.song)
        assertEquals(sampleUri, newSession?.uri)
        assertEquals(SongFormat.MIDI, newSession?.format)

        // Config と FitProfile が維持されていること
        assertEquals(customConfig, newSession?.config)
        assertEquals(customFitProfile, newSession?.fitProfile)
    }

    @Test
    fun select_onSuccess_withNoPreexistingSession_usesSettingsConfigAndFitProfile() = runBlocking {
        assertNull(sessionRepository.currentSession.value)

        val fakeSongLoader = object : SongLoader() {
            override suspend fun loadSong(
                contentResolver: ContentResolver,
                uri: Uri,
                midiMappingSettings: com.onigiri.keycue.model.MidiMappingSettings?
            ): SongLoadResult {
                return SongLoadResult.Success(sampleSongData, sampleMetadata)
            }
        }

        val coordinator = SongSelectionCoordinator(
            contentResolver = contentResolver,
            songLoader = fakeSongLoader,
            sessionRepository = sessionRepository,
            settingsRepository = settingsRepository
        )

        val result = coordinator.select(sampleUri)

        assertTrue(result.isSuccess)
        val session = sessionRepository.currentSession.value
        assertNotNull(session)
        assertEquals(sampleSongData, session?.song)
        // SettingsRepository の config が反映されていること
        assertEquals(settingsRepository.playbackConfig.value, session?.config)
    }

    @Test
    fun select_onFailure_retainsOldSessionAndDoesNotUpdateLastSongUri() = runBlocking {
        // 事前セッションの設定
        val oldSong = SongData("OldSong.mid", 120000L, listOf(NoteEvent(0L, 0)))
        val oldSession = PlaybackSession(oldSong)
        sessionRepository.setSession(oldSong, oldSession.config, oldSession.fitProfile)
        settingsRepository.saveLastSongUri("content://old/song/uri")

        val fakeSongLoader = object : SongLoader() {
            override suspend fun loadSong(
                contentResolver: ContentResolver,
                uri: Uri,
                midiMappingSettings: com.onigiri.keycue.model.MidiMappingSettings?
            ): SongLoadResult {
                return SongLoadResult.Failure.UnsupportedFormat("invalid.foo", "application/octet-stream")
            }
        }

        val coordinator = SongSelectionCoordinator(
            contentResolver = contentResolver,
            songLoader = fakeSongLoader,
            sessionRepository = sessionRepository,
            settingsRepository = settingsRepository
        )

        val result = coordinator.select(sampleUri)

        // 読み込み失敗
        assertTrue(result.isFailure)

        // 旧セッションがそのまま維持されていること
        assertEquals(oldSong, sessionRepository.currentSession.value?.song)

        // lastSongUri が変更されていないこと
        assertEquals("content://old/song/uri", settingsRepository.lastSongUri.value)
    }

    @Test
    fun select_whenSecurityExceptionThrownOnTakePermission_stillSucceeds() = runBlocking {
        contentResolver.shouldThrowSecurityException = true

        val fakeSongLoader = object : SongLoader() {
            override suspend fun loadSong(
                contentResolver: ContentResolver,
                uri: Uri,
                midiMappingSettings: com.onigiri.keycue.model.MidiMappingSettings?
            ): SongLoadResult {
                return SongLoadResult.Success(sampleSongData, sampleMetadata)
            }
        }

        val coordinator = SongSelectionCoordinator(
            contentResolver = contentResolver,
            songLoader = fakeSongLoader,
            sessionRepository = sessionRepository,
            settingsRepository = settingsRepository
        )

        val result = coordinator.select(sampleUri)

        // 例外が発生してもクラッシュせず、読み込みは成功すること
        assertTrue(result.isSuccess)
        assertEquals(sampleSongData, result.getOrNull())
        assertEquals(sampleSongData, sessionRepository.currentSession.value?.song)
    }
}
