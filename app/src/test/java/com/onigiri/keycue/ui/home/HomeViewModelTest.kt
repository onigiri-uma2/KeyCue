package com.onigiri.keycue.ui.home

import android.net.FakeUri
import com.onigiri.keycue.model.NoteEvent
import com.onigiri.keycue.model.PlaybackConfig
import com.onigiri.keycue.model.SongData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HomeViewModelTest {

    private lateinit var viewModel: HomeViewModel
    private val testScope = CoroutineScope(Dispatchers.Unconfined)

    @Before
    fun setUp() {
        viewModel = HomeViewModel(externalScope = testScope)
    }

    @Test
    fun initialState_hasDefaultValues() {
        val state = viewModel.uiState.value
        assertNull(state.songTitle)
        assertEquals(0L, state.durationMs)
        assertEquals(1.0f, state.speed, 0.001f)
        assertEquals(300L, state.noteLeadTimeMs)
        assertEquals(200L, state.approachCircleLeadTimeMs)
        assertEquals(PlaybackConfig.DEFAULT_NOTE_LEAD_TIME_MS, state.noteLeadTimeMs)
        assertEquals(PlaybackConfig.DEFAULT_APPROACH_CIRCLE_LEAD_TIME_MS, state.approachCircleLeadTimeMs)
        assertEquals(3000L, state.countdownMs)
        assertFalse(state.fitConfigured)
        assertFalse(state.overlayPermissionGranted)
        assertFalse(state.canStart)
        assertNull(state.selectedSongUri)
        assertNull(state.selectedFileName)
        assertNull(state.selectedMimeType)
        assertNull(state.songFormat)
    }

    @Test
    fun applySongMetadata_updatesStateAndSavesToSettingsRepository() {
        val fakeSettingsRepository = com.onigiri.keycue.data.InMemorySettingsRepository()
        val testViewModel = HomeViewModel(settingsRepository = fakeSettingsRepository, externalScope = testScope)

        val metadata = com.onigiri.keycue.song.SongFileMetadata(
            uri = FakeUri("content://media/external/audio/media/42"),
            displayName = "Canon.mid",
            mimeType = "audio/midi",
            format = com.onigiri.keycue.model.SongFormat.MIDI
        )

        runBlocking {
            testViewModel.applySongMetadata(metadata)
        }

        val state = testViewModel.uiState.value
        assertEquals("Canon.mid", state.songTitle)
        assertEquals("Canon.mid", state.selectedFileName)
        assertEquals("audio/midi", state.selectedMimeType)
        assertEquals(com.onigiri.keycue.model.SongFormat.MIDI, state.songFormat)
        assertEquals(metadata.uri, state.selectedSongUri)
        assertTrue(state.canStart)

        // SettingsRepository に URI 文字列が保存されていること
        assertEquals(metadata.uri.toString(), fakeSettingsRepository.lastSongUri.value)
    }

    @Test
    fun applySongData_updatesStateWithSongDataAndNoteCount() {
        val fakeSettingsRepository = com.onigiri.keycue.data.InMemorySettingsRepository()
        val testViewModel = HomeViewModel(settingsRepository = fakeSettingsRepository, externalScope = testScope)

        val metadata = com.onigiri.keycue.song.SongFileMetadata(
            uri = FakeUri("content://media/external/audio/media/42"),
            displayName = "sample.mid",
            mimeType = "audio/midi",
            format = com.onigiri.keycue.model.SongFormat.MIDI
        )
        val songData = SongData(
            title = "sample.mid",
            durationMs = 222000L,
            events = listOf(
                NoteEvent(0L, 0),
                NoteEvent(500L, 1),
                NoteEvent(1000L, 2)
            )
        )

        runBlocking {
            testViewModel.applySongData(songData, metadata)
        }

        val state = testViewModel.uiState.value
        assertEquals("sample.mid", state.songTitle)
        assertEquals(222000L, state.durationMs)
        assertEquals(3, state.noteCount)
        assertEquals(songData, state.songData)
        assertTrue(state.canStart)
    }

    @Test
    fun applySongData_withSkyStudioJson_updatesStateWithCorrectFormat() {
        val fakeSettingsRepository = com.onigiri.keycue.data.InMemorySettingsRepository()
        val testViewModel = HomeViewModel(settingsRepository = fakeSettingsRepository, externalScope = testScope)

        val metadata = com.onigiri.keycue.song.SongFileMetadata(
            uri = FakeUri("content://media/external/audio/media/99"),
            displayName = "twinkle.json",
            mimeType = "application/json",
            format = com.onigiri.keycue.model.SongFormat.SKY_STUDIO_JSON
        )
        val songData = SongData(
            title = "Twinkle_twinkle_little_star9",
            durationMs = 19200L,
            events = listOf(
                NoteEvent(600L, 7),
                NoteEvent(1200L, 7),
                NoteEvent(1500L, 11)
            )
        )

        runBlocking {
            testViewModel.applySongData(songData, metadata)
        }

        val state = testViewModel.uiState.value
        assertEquals("Twinkle_twinkle_little_star9", state.songTitle)
        assertEquals("twinkle.json", state.selectedFileName)
        assertEquals(com.onigiri.keycue.model.SongFormat.SKY_STUDIO_JSON, state.songFormat)
        assertEquals(19200L, state.durationMs)
        assertEquals(3, state.noteCount)
        assertEquals(songData, state.songData)
        assertTrue(state.canStart)
    }

    @Test
    fun clearError_clearsErrorMessage() {
        val testViewModel = HomeViewModel(externalScope = testScope)
        testViewModel.setSongData(null)

        testViewModel.clearError()
        assertNull(testViewModel.uiState.value.errorMessage)
    }

    @Test
    fun adjustSpeed_increasesAndDecreasesWithinRange() {
        viewModel.adjustSpeed(0.05f)
        assertEquals(1.05f, viewModel.uiState.value.speed, 0.001f)

        viewModel.adjustSpeed(-0.10f)
        assertEquals(0.95f, viewModel.uiState.value.speed, 0.001f)

        // 下限クランプ (0.25f)
        viewModel.adjustSpeed(-2.0f)
        assertEquals(0.25f, viewModel.uiState.value.speed, 0.001f)

        // 上限クランプ (2.0f)
        viewModel.adjustSpeed(5.0f)
        assertEquals(2.0f, viewModel.uiState.value.speed, 0.001f)
    }

    @Test
    fun adjustNoteLeadTime_increasesAndDecreasesWithinRange() {
        viewModel.adjustNoteLeadTime(100L)
        assertEquals(400L, viewModel.uiState.value.noteLeadTimeMs)

        viewModel.adjustNoteLeadTime(-50L)
        assertEquals(350L, viewModel.uiState.value.noteLeadTimeMs)

        // 下限クランプ (300ms)
        viewModel.adjustNoteLeadTime(-1000L)
        assertEquals(300L, viewModel.uiState.value.noteLeadTimeMs)

        // 上限クランプ (2000ms)
        viewModel.adjustNoteLeadTime(3000L)
        assertEquals(2000L, viewModel.uiState.value.noteLeadTimeMs)
    }

    @Test
    fun adjustApproachCircleLeadTime_and_setCountdownMs_updateState() {
        viewModel.adjustApproachCircleLeadTime(50L)
        assertEquals(250L, viewModel.uiState.value.approachCircleLeadTimeMs)

        viewModel.adjustApproachCircleLeadTime(-500L)
        assertEquals(100L, viewModel.uiState.value.approachCircleLeadTimeMs) // 下限 100ms

        viewModel.setCountdownMs(1000L)
        assertEquals(1000L, viewModel.uiState.value.countdownMs)

        viewModel.setCountdownMs(0L)
        assertEquals(0L, viewModel.uiState.value.countdownMs)
    }

    @Test
    fun adjustNoteLeadTime_followsApproachCircleWhenNoteDecreasesBelowCircle() {
        // note = 500, circle = 400
        viewModel.adjustNoteLeadTime(200L) // 500L
        viewModel.adjustApproachCircleLeadTime(200L) // 400L
        assertEquals(500L, viewModel.uiState.value.noteLeadTimeMs)
        assertEquals(400L, viewModel.uiState.value.approachCircleLeadTimeMs)

        // note を 300L に下げる -> circle も 300L に安全に追従
        viewModel.adjustNoteLeadTime(-200L)
        assertEquals(300L, viewModel.uiState.value.noteLeadTimeMs)
        assertEquals(300L, viewModel.uiState.value.approachCircleLeadTimeMs)
        assertTrue(viewModel.uiState.value.approachCircleLeadTimeMs <= viewModel.uiState.value.noteLeadTimeMs)
    }

    @Test
    fun adjustApproachCircleLeadTime_cannotExceedNoteLeadTime() {
        // note = 300L の状態で circle を 500L に上げようとしても 300L に制限される
        viewModel.adjustApproachCircleLeadTime(500L)
        assertEquals(300L, viewModel.uiState.value.noteLeadTimeMs)
        assertEquals(300L, viewModel.uiState.value.approachCircleLeadTimeMs)
    }

    @Test
    fun setSongData_updatesSongInfoAndCanStart() {
        val testSong = SongData(
            title = "Canon.mid",
            durationMs = 222000L,
            events = listOf(
                NoteEvent(timeMs = 0L, key = 0),
                NoteEvent(timeMs = 500L, key = 7)
            )
        )

        viewModel.setSongData(testSong)

        val state = viewModel.uiState.value
        assertEquals("Canon.mid", state.songTitle)
        assertEquals(222000L, state.durationMs)
        assertTrue(state.canStart)

        // クリア
        viewModel.setSongData(null)
        val clearedState = viewModel.uiState.value
        assertNull(clearedState.songTitle)
        assertEquals(0L, clearedState.durationMs)
        assertFalse(clearedState.canStart)
    }

    @Test
    fun domainModels_instantiateCorrectly() {
        val note = NoteEvent(timeMs = 1200L, key = 14)
        assertEquals(1200L, note.timeMs)
        assertEquals(14, note.key)

        val config = PlaybackConfig(speed = 1.25f, noteLeadTimeMs = 500L)
        assertEquals(1.25f, config.speed, 0.001f)
        assertEquals(500L, config.noteLeadTimeMs)
        assertEquals(200L, config.approachCircleLeadTimeMs)
        assertEquals(3000L, config.countdownMs)
    }

    @Test
    fun setOverlayPermissionGranted_updatesUiState() {
        assertFalse(viewModel.uiState.value.overlayPermissionGranted)

        viewModel.setOverlayPermissionGranted(true)
        assertTrue(viewModel.uiState.value.overlayPermissionGranted)

        viewModel.setOverlayPermissionGranted(false)
        assertFalse(viewModel.uiState.value.overlayPermissionGranted)
    }

    @Test
    fun settingsRepository_savesAndEmitsOverlayPosition() {
        val repo = com.onigiri.keycue.data.InMemorySettingsRepository()
        assertNull(repo.overlayPosition.value)

        runBlocking {
            repo.saveOverlayPosition(120, 340)
        }

        assertEquals(Pair(120, 340), repo.overlayPosition.value)
    }

    @Test
    fun playbackSession_syncsToHomeUiState_asSingleSourceOfTruth() {
        val fakeSessionRepo = com.onigiri.keycue.data.InMemoryPlaybackSessionRepository()
        val testViewModel = HomeViewModel(sessionRepository = fakeSessionRepo, externalScope = testScope)

        val newSong = SongData(
            title = "OverlaySelected.mid",
            durationMs = 150000L,
            events = listOf(NoteEvent(0L, 1))
        )
        val uri = FakeUri("content://test/song")

        fakeSessionRepo.setSession(
            songData = newSong,
            config = PlaybackConfig(),
            fitProfile = com.onigiri.keycue.model.FitProfile.createDefaultTestProfile(),
            uri = uri,
            format = com.onigiri.keycue.model.SongFormat.MIDI
        )

        val state = testViewModel.uiState.value
        assertEquals("OverlaySelected.mid", state.songTitle)
        assertEquals(150000L, state.durationMs)
        assertEquals(1, state.noteCount)
        assertEquals(uri, state.selectedSongUri)
        assertEquals(com.onigiri.keycue.model.SongFormat.MIDI, state.songFormat)
        assertEquals(newSong, state.songData)
    }

    @Test
    fun playbackSession_clear_removesAllSongState() {
        val fakeSessionRepo = com.onigiri.keycue.data.InMemoryPlaybackSessionRepository()
        val testViewModel = HomeViewModel(sessionRepository = fakeSessionRepo, externalScope = testScope)
        fakeSessionRepo.setSession(
            SongData("song.mid", 1000L, listOf(NoteEvent(0L, 1))),
            PlaybackConfig()
        )

        fakeSessionRepo.clearSession()

        val state = testViewModel.uiState.value
        assertNull(state.songTitle)
        assertNull(state.songData)
        assertNull(state.selectedSongUri)
        assertEquals(0, state.noteCount)
        assertFalse(state.canStart)
    }

    @Test
    fun onFileSelected_invokesCoordinatorWithInitializeManualFromAutoTrue() = runBlocking {
        val fakeContentResolver = android.content.FakeContentResolver()
        val fakeSettingsRepo = com.onigiri.keycue.data.InMemorySettingsRepository()
        val fakeSessionRepo = com.onigiri.keycue.data.InMemoryPlaybackSessionRepository()

        val latch = java.util.concurrent.CountDownLatch(1)
        var capturedInitializeManualFromAuto: Boolean? = null
        val fakeSongLoader = object : com.onigiri.keycue.song.SongLoader() {
            override suspend fun loadSong(
                contentResolver: android.content.ContentResolver,
                uri: android.net.Uri,
                midiMappingSettings: com.onigiri.keycue.model.MidiMappingSettings?,
                initializeManualFromAuto: Boolean
            ): com.onigiri.keycue.song.SongLoadResult {
                capturedInitializeManualFromAuto = initializeManualFromAuto
                latch.countDown()
                return com.onigiri.keycue.song.SongLoadResult.Success(
                    songData = SongData("test.mid", 1000L, emptyList()),
                    metadata = com.onigiri.keycue.song.SongFileMetadata(uri, "test.mid", "audio/midi", com.onigiri.keycue.model.SongFormat.MIDI)
                )
            }
        }

        val coordinator = com.onigiri.keycue.song.SongSelectionCoordinator(
            contentResolver = fakeContentResolver,
            songLoader = fakeSongLoader,
            sessionRepository = fakeSessionRepo,
            settingsRepository = fakeSettingsRepo
        )

        val viewModel = HomeViewModel(
            settingsRepository = fakeSettingsRepo,
            sessionRepository = fakeSessionRepo,
            songSelectionCoordinator = coordinator,
            externalScope = testScope
        )

        val uri = FakeUri("content://test/song.mid")
        viewModel.onFileSelected(uri)

        assertTrue(latch.await(3, java.util.concurrent.TimeUnit.SECONDS))
        assertEquals(true, capturedInitializeManualFromAuto)
    }

    @Test
    fun tryRestoreLastSong_invokesCoordinatorWithInitializeManualFromAutoFalse() = runBlocking {
        val fakeContentResolver = android.content.FakeContentResolver()
        val fakeSettingsRepo = com.onigiri.keycue.data.InMemorySettingsRepository()
        val fakeSessionRepo = com.onigiri.keycue.data.InMemoryPlaybackSessionRepository()

        val uri = FakeUri("content://test/last_song.mid")
        fakeSettingsRepo.saveLastSongUri(uri.toString())

        val latch = java.util.concurrent.CountDownLatch(1)
        var capturedInitializeManualFromAuto: Boolean? = null
        val fakeSongLoader = object : com.onigiri.keycue.song.SongLoader() {
            override suspend fun loadSong(
                contentResolver: android.content.ContentResolver,
                uri: android.net.Uri,
                midiMappingSettings: com.onigiri.keycue.model.MidiMappingSettings?,
                initializeManualFromAuto: Boolean
            ): com.onigiri.keycue.song.SongLoadResult {
                capturedInitializeManualFromAuto = initializeManualFromAuto
                latch.countDown()
                return com.onigiri.keycue.song.SongLoadResult.Success(
                    songData = SongData("last_song.mid", 1000L, emptyList()),
                    metadata = com.onigiri.keycue.song.SongFileMetadata(uri, "last_song.mid", "audio/midi", com.onigiri.keycue.model.SongFormat.MIDI)
                )
            }
        }

        val coordinator = com.onigiri.keycue.song.SongSelectionCoordinator(
            contentResolver = fakeContentResolver,
            songLoader = fakeSongLoader,
            sessionRepository = fakeSessionRepo,
            settingsRepository = fakeSettingsRepo
        )

        val viewModel = HomeViewModel(
            settingsRepository = fakeSettingsRepo,
            sessionRepository = fakeSessionRepo,
            songSelectionCoordinator = coordinator,
            externalScope = testScope,
            uriParser = { FakeUri(it) }
        )

        viewModel.tryRestoreLastSong()

        assertTrue(latch.await(3, java.util.concurrent.TimeUnit.SECONDS))
        assertEquals(false, capturedInitializeManualFromAuto)
    }
}
