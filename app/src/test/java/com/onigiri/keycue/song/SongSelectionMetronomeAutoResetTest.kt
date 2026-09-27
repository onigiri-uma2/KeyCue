package com.onigiri.keycue.song

import android.content.ContentResolver
import android.content.FakeContentResolver
import android.net.FakeUri
import android.net.Uri
import com.onigiri.keycue.data.InMemoryPlaybackSessionRepository
import com.onigiri.keycue.data.InMemorySettingsRepository
import com.onigiri.keycue.data.PlaybackSessionRepository
import com.onigiri.keycue.model.MetronomeConfig
import com.onigiri.keycue.model.MetronomeTimingMode
import com.onigiri.keycue.model.NoteEvent
import com.onigiri.keycue.model.SongData
import com.onigiri.keycue.model.SongFormat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 楽曲選択時におけるメトロノーム timingMode の AUTO 復帰挙動、
 * セッション公開との順序安全性、および自動復元・マッピング再適用時の維持を検証する単体テスト。
 */
class SongSelectionMetronomeAutoResetTest {

    private lateinit var contentResolver: FakeContentResolver
    private lateinit var settingsRepository: InMemorySettingsRepository
    private lateinit var sessionRepository: InMemoryPlaybackSessionRepository

    private val sampleUri = FakeUri("content://com.android.providers.media.documents/document/audio%3A100")
    private val sampleSongData = SongData(
        title = "TestSong.mid",
        durationMs = 120000L,
        events = listOf(NoteEvent(0L, 0))
    )
    private val sampleMetadata = SongFileMetadata(
        uri = sampleUri,
        displayName = "TestSong.mid",
        mimeType = "audio/midi",
        format = SongFormat.MIDI
    )

    @Before
    fun setUp() {
        contentResolver = FakeContentResolver()
        settingsRepository = InMemorySettingsRepository()
        sessionRepository = InMemoryPlaybackSessionRepository()
    }

    @Test
    fun testUserSelect_resetsTimingModeToAuto() = runBlocking {
        // 事前条件: 手動モード (MANUAL), BPM 150
        settingsRepository.saveMetronomeConfig(
            MetronomeConfig(timingMode = MetronomeTimingMode.MANUAL, bpm = 150, beatsPerBar = 3)
        )
        assertEquals(MetronomeTimingMode.MANUAL, settingsRepository.metronomeConfig.value.timingMode)

        val fakeSongLoader = object : SongLoader() {
            override suspend fun loadSong(
                contentResolver: ContentResolver,
                uri: Uri,
                midiMappingSettings: com.onigiri.keycue.model.MidiMappingSettings?,
                initializeManualFromAuto: Boolean
            ): SongLoadResult = SongLoadResult.Success(sampleSongData, sampleMetadata)
        }

        val coordinator = SongSelectionCoordinator(
            contentResolver = contentResolver,
            songLoader = fakeSongLoader,
            sessionRepository = sessionRepository,
            settingsRepository = settingsRepository
        )

        val result = coordinator.select(sampleUri, reason = SongLoadReason.USER_SELECT)
        assertTrue(result.isSuccess)

        val currentMetro = settingsRepository.metronomeConfig.value
        assertEquals("新曲選択(USER_SELECT)によりtimingModeがAUTOへ復帰すること", MetronomeTimingMode.AUTO, currentMetro.timingMode)
        assertEquals("BPM設定等の手動値はクリアされず保持されること", 150, currentMetro.bpm)
        assertEquals(3, currentMetro.beatsPerBar)
    }

    @Test
    fun testAutoResetHappensBeforePlaybackSessionPublished() = runBlocking {
        // 安全要件: AUTO復帰完了後に新曲の PlaybackSession を公開する
        settingsRepository.saveMetronomeConfig(
            MetronomeConfig(timingMode = MetronomeTimingMode.MANUAL, bpm = 160)
        )

        var timingModeWhenSessionPublished: MetronomeTimingMode? = null
        val delegateSessionRepo = InMemoryPlaybackSessionRepository()
        val hookedSessionRepo = object : PlaybackSessionRepository by delegateSessionRepo {
            override fun setSession(
                songData: SongData,
                config: com.onigiri.keycue.model.PlaybackConfig,
                fitProfile: com.onigiri.keycue.model.FitProfile,
                uri: Uri?,
                format: SongFormat?,
                resolvedMidiMapping: com.onigiri.keycue.model.ResolvedMidiMapping?
            ) {
                timingModeWhenSessionPublished = settingsRepository.metronomeConfig.value.timingMode
                delegateSessionRepo.setSession(songData, config, fitProfile, uri, format, resolvedMidiMapping)
            }
        }

        val fakeSongLoader = object : SongLoader() {
            override suspend fun loadSong(
                contentResolver: ContentResolver,
                uri: Uri,
                midiMappingSettings: com.onigiri.keycue.model.MidiMappingSettings?,
                initializeManualFromAuto: Boolean
            ): SongLoadResult = SongLoadResult.Success(sampleSongData, sampleMetadata)
        }

        val coordinator = SongSelectionCoordinator(
            contentResolver = contentResolver,
            songLoader = fakeSongLoader,
            sessionRepository = hookedSessionRepo,
            settingsRepository = settingsRepository
        )

        coordinator.select(sampleUri, reason = SongLoadReason.USER_SELECT)

        assertEquals(
            "新Session公開の時点で、すでにtimingModeはAUTOに復帰していること",
            MetronomeTimingMode.AUTO,
            timingModeWhenSessionPublished
        )
    }

    @Test
    fun testRecentSelect_resetsTimingModeToAuto() = runBlocking {
        settingsRepository.saveMetronomeConfig(
            MetronomeConfig(timingMode = MetronomeTimingMode.MANUAL, bpm = 140)
        )

        val fakeSongLoader = object : SongLoader() {
            override suspend fun loadSong(
                contentResolver: ContentResolver,
                uri: Uri,
                midiMappingSettings: com.onigiri.keycue.model.MidiMappingSettings?,
                initializeManualFromAuto: Boolean
            ): SongLoadResult = SongLoadResult.Success(sampleSongData, sampleMetadata)
        }

        val coordinator = SongSelectionCoordinator(
            contentResolver = contentResolver,
            songLoader = fakeSongLoader,
            sessionRepository = sessionRepository,
            settingsRepository = settingsRepository
        )

        val result = coordinator.select(sampleUri, reason = SongLoadReason.RECENT_SELECT)
        assertTrue(result.isSuccess)

        assertEquals(
            "最近使った曲からの選択(RECENT_SELECT)でもAUTOへ復帰すること",
            MetronomeTimingMode.AUTO,
            settingsRepository.metronomeConfig.value.timingMode
        )
    }

    @Test
    fun testRestore_preservesManualTimingMode() = runBlocking {
        // 起動時の自動復元では、既存ユーザーの手動モードを勝手に上書きしない
        settingsRepository.saveMetronomeConfig(
            MetronomeConfig(timingMode = MetronomeTimingMode.MANUAL, bpm = 135)
        )

        val fakeSongLoader = object : SongLoader() {
            override suspend fun loadSong(
                contentResolver: ContentResolver,
                uri: Uri,
                midiMappingSettings: com.onigiri.keycue.model.MidiMappingSettings?,
                initializeManualFromAuto: Boolean
            ): SongLoadResult = SongLoadResult.Success(sampleSongData, sampleMetadata)
        }

        val coordinator = SongSelectionCoordinator(
            contentResolver = contentResolver,
            songLoader = fakeSongLoader,
            sessionRepository = sessionRepository,
            settingsRepository = settingsRepository
        )

        val result = coordinator.select(sampleUri, reason = SongLoadReason.RESTORE)
        assertTrue(result.isSuccess)

        assertEquals(
            "自動復元(RESTORE)ではMANUALモードが維持されること",
            MetronomeTimingMode.MANUAL,
            settingsRepository.metronomeConfig.value.timingMode
        )
    }

    @Test
    fun testReapplyMapping_preservesTimingMode() = runBlocking {
        // 初期状態としてセッションを設定
        sessionRepository.setSession(sampleSongData, com.onigiri.keycue.model.PlaybackConfig(), com.onigiri.keycue.model.FitProfile.createDefaultTestProfile(), sampleUri, SongFormat.MIDI)

        settingsRepository.saveMetronomeConfig(
            MetronomeConfig(timingMode = MetronomeTimingMode.MANUAL, bpm = 130)
        )

        val fakeSongLoader = object : SongLoader() {
            override suspend fun loadSong(
                contentResolver: ContentResolver,
                uri: Uri,
                midiMappingSettings: com.onigiri.keycue.model.MidiMappingSettings?,
                initializeManualFromAuto: Boolean
            ): SongLoadResult = SongLoadResult.Success(sampleSongData, sampleMetadata)
        }

        val coordinator = SongSelectionCoordinator(
            contentResolver = contentResolver,
            songLoader = fakeSongLoader,
            sessionRepository = sessionRepository,
            settingsRepository = settingsRepository
        )

        val result = coordinator.reapplyMapping(com.onigiri.keycue.model.MidiMappingSettings())
        assertTrue(result.isSuccess)

        assertEquals(
            "MIDI再マッピング(MIDI_REMAP)時はMANUALモードが維持されること",
            MetronomeTimingMode.MANUAL,
            settingsRepository.metronomeConfig.value.timingMode
        )
    }

    @Test
    fun testFailure_preservesTimingMode() = runBlocking {
        settingsRepository.saveMetronomeConfig(
            MetronomeConfig(timingMode = MetronomeTimingMode.MANUAL, bpm = 125)
        )

        val fakeSongLoader = object : SongLoader() {
            override suspend fun loadSong(
                contentResolver: ContentResolver,
                uri: Uri,
                midiMappingSettings: com.onigiri.keycue.model.MidiMappingSettings?,
                initializeManualFromAuto: Boolean
            ): SongLoadResult = SongLoadResult.Failure.FileReadError("Read error")
        }

        val coordinator = SongSelectionCoordinator(
            contentResolver = contentResolver,
            songLoader = fakeSongLoader,
            sessionRepository = sessionRepository,
            settingsRepository = settingsRepository
        )

        val result = coordinator.select(sampleUri, reason = SongLoadReason.USER_SELECT)
        assertTrue(result.isFailure)

        assertEquals(
            "楽曲読み込み失敗時はtimingModeは変更されず維持されること",
            MetronomeTimingMode.MANUAL,
            settingsRepository.metronomeConfig.value.timingMode
        )
    }
}
