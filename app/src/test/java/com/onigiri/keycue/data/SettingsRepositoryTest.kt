package com.onigiri.keycue.data

import com.onigiri.keycue.model.PlaybackConfig
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsRepositoryTest {

    @Test
    fun `InMemorySettingsRepository holds initial values`() {
        val repo = InMemorySettingsRepository(
            initialSongUri = "content://test/song.mid",
            initialSpeed = 1.25f,
            initialLeadTimeMs = 1000L,
            initialHighlightTimeMs = 200L,
            initialCountdownMs = 5000L
        )

        assertEquals("content://test/song.mid", repo.lastSongUri.value)
        assertEquals(1.25f, repo.speed.value, 0.001f)
        assertEquals(1000L, repo.leadTimeMs.value)
        assertEquals(200L, repo.highlightTimeMs.value)
        assertEquals(5000L, repo.countdownMs.value)
        assertEquals(1.25f, repo.playbackConfig.value.speed, 0.001f)
        assertEquals(1000L, repo.playbackConfig.value.leadTimeMs)
    }

    @Test
    fun `InMemorySettingsRepository holds default values`() {
        val repo = InMemorySettingsRepository()

        assertNull(repo.lastSongUri.value)
        assertEquals(1.0f, repo.speed.value, 0.001f)
        assertEquals(300L, repo.leadTimeMs.value)
        assertEquals(200L, repo.highlightTimeMs.value)
        assertEquals(3000L, repo.countdownMs.value)
        assertEquals(300L, repo.playbackConfig.value.leadTimeMs)
        assertEquals(200L, repo.playbackConfig.value.highlightTimeMs)
        assertEquals(false, repo.visualConfig.value.showChordLinks)
        assertEquals(true, repo.visualConfig.value.showChordHalos)
    }

    @Test
    fun `saveSpeed clamps invalid values`() = runBlocking {
        val repo = InMemorySettingsRepository()

        repo.saveSpeed(3.0f)
        assertEquals(2.0f, repo.speed.value, 0.001f)
        assertEquals(2.0f, repo.playbackConfig.value.speed, 0.001f)

        repo.saveSpeed(0.1f)
        assertEquals(0.25f, repo.speed.value, 0.001f)
        assertEquals(0.25f, repo.playbackConfig.value.speed, 0.001f)
    }

    @Test
    fun `saveLeadTimeMs clamps invalid values`() = runBlocking {
        val repo = InMemorySettingsRepository()

        repo.saveLeadTimeMs(100L)
        assertEquals(300L, repo.leadTimeMs.value)

        repo.saveLeadTimeMs(5000L)
        assertEquals(2000L, repo.leadTimeMs.value)
    }

    @Test
    fun `saveHighlightTimeMs clamps invalid values`() = runBlocking {
        val repo = InMemorySettingsRepository()

        repo.saveHighlightTimeMs(50L)
        assertEquals(100L, repo.highlightTimeMs.value)

        repo.saveHighlightTimeMs(2000L)
        assertEquals(1000L, repo.highlightTimeMs.value)
    }

    @Test
    fun `saveCountdownMs clamps invalid values`() = runBlocking {
        val repo = InMemorySettingsRepository()

        repo.saveCountdownMs(-500L)
        assertEquals(0L, repo.countdownMs.value)

        repo.saveCountdownMs(10000L)
        assertEquals(5000L, repo.countdownMs.value)
    }

    @Test
    fun `saveOverlayPositionNormalized clamps and stores coordinates`() = runBlocking {
        val repo = InMemorySettingsRepository()

        assertNull(repo.overlayPositionNormalized.value)

        repo.saveOverlayPositionNormalized(0.75f, 0.20f)
        val point = repo.overlayPositionNormalized.value
        assertNotNull(point)
        assertEquals(0.75f, point!!.x, 0.001f)
        assertEquals(0.20f, point.y, 0.001f)

        // clamp test
        repo.saveOverlayPositionNormalized(-0.5f, 1.5f)
        val clamped = repo.overlayPositionNormalized.value!!
        assertEquals(0.0f, clamped.x, 0.001f)
        assertEquals(1.0f, clamped.y, 0.001f)
    }

    @Test
    fun `savePlaybackConfig updates all fields`() = runBlocking {
        val repo = InMemorySettingsRepository()
        repo.savePlaybackConfig(PlaybackConfig(speed = 1.5f, leadTimeMs = 1500L, highlightTimeMs = 500L, countdownMs = 1000L))

        assertEquals(1.5f, repo.speed.value, 0.001f)
        assertEquals(1500L, repo.leadTimeMs.value)
        assertEquals(500L, repo.highlightTimeMs.value)
        assertEquals(1000L, repo.countdownMs.value)
    }

    @Test
    fun `partial playback update preserves unrelated fields`() = runBlocking {
        val repo = InMemorySettingsRepository(
            initialLeadTimeMs = 1500L,
            initialHighlightTimeMs = 750L,
            initialCountdownMs = 5000L
        )

        repo.saveSpeed(1.5f)

        assertEquals(
            PlaybackConfig(1.5f, 1500L, 750L, 5000L),
            repo.playbackConfig.value
        )
    }

    @Test
    fun `PlaybackConfig normalization is shared by bulk updates`() = runBlocking {
        val repo = InMemorySettingsRepository()

        repo.savePlaybackConfig(PlaybackConfig(9f, 1L, 9_000L, -1L))

        assertEquals(PlaybackConfig(2f, 300L, 1000L, 0L), repo.playbackConfig.value)
    }

    @Test
    fun `saveFitProfile saves and clears profile`() = runBlocking {
        val repo = InMemorySettingsRepository()
        assertNull(repo.fitProfile.value)

        val profile = com.onigiri.keycue.model.FitProfile.createDefaultTestProfile()
        repo.saveFitProfile(profile)
        assertEquals(profile, repo.fitProfile.value)

        repo.saveFitProfile(null)
        assertNull(repo.fitProfile.value)
    }

    @Test
    fun `visualConfig default values and updates`() = runBlocking {
        val repo = InMemorySettingsRepository()

        // 1. デフォルト値の検証
        val defaultConfig = repo.visualConfig.value
        assertEquals(false, defaultConfig.showKeyNumbers)
        assertEquals(true, defaultConfig.showFallingNotes)
        assertEquals(true, defaultConfig.showApproachCircles)
        assertEquals(false, defaultConfig.showJustEffect)
        assertEquals(0.04f, defaultConfig.guideRadiusRatio, 0.001f)
        assertEquals(0xFFFFFFFF.toInt(), defaultConfig.guideColor)
        assertEquals(0xFF4CAF50.toInt(), defaultConfig.noteColorTop)
        assertEquals(0xFF2196F3.toInt(), defaultConfig.noteColorMiddle)
        assertEquals(0xFFE91E63.toInt(), defaultConfig.noteColorBottom)
        assertEquals(false, defaultConfig.showChordLinks)
        assertEquals(true, defaultConfig.showChordHalos)

        // 2. 各フィールドの更新
        repo.saveShowKeyNumbers(true)
        assertEquals(true, repo.visualConfig.value.showKeyNumbers)

        repo.saveShowFallingNotes(false)
        assertEquals(false, repo.visualConfig.value.showFallingNotes)

        repo.saveShowApproachCircles(false)
        assertEquals(false, repo.visualConfig.value.showApproachCircles)

        repo.saveShowJustEffect(true)
        assertEquals(true, repo.visualConfig.value.showJustEffect)

        repo.saveShowChordLinks(true)
        assertEquals(true, repo.visualConfig.value.showChordLinks)

        repo.saveShowChordHalos(false)
        assertEquals(false, repo.visualConfig.value.showChordHalos)

        repo.saveGuideColor(0xFF00E5FF.toInt())
        assertEquals(0xFF00E5FF.toInt(), repo.visualConfig.value.guideColor)

        repo.saveNoteColorTop(0xFFFF9800.toInt())
        assertEquals(0xFFFF9800.toInt(), repo.visualConfig.value.noteColorTop)

        repo.saveNoteColorMiddle(0xFFFFEB3B.toInt())
        assertEquals(0xFFFFEB3B.toInt(), repo.visualConfig.value.noteColorMiddle)

        repo.saveNoteColorBottom(0xFF9C27B0.toInt())
        assertEquals(0xFF9C27B0.toInt(), repo.visualConfig.value.noteColorBottom)

        // 3. guideRadiusRatio の clamp テスト (0.02f .. 0.08f)
        repo.saveGuideRadiusRatio(0.01f)
        assertEquals(0.02f, repo.visualConfig.value.guideRadiusRatio, 0.001f)

        repo.saveGuideRadiusRatio(0.15f)
        assertEquals(0.08f, repo.visualConfig.value.guideRadiusRatio, 0.001f)

        repo.saveGuideRadiusRatio(0.05f)
        assertEquals(0.05f, repo.visualConfig.value.guideRadiusRatio, 0.001f)

        // 4. VisualConfig.safe() によるプロパティ保持テスト
        val safeCfg = com.onigiri.keycue.model.VisualConfig.safe(showChordLinks = false, showChordHalos = true)
        assertEquals(false, safeCfg.showChordLinks)
        assertEquals(true, safeCfg.showChordHalos)
    }

    @Test
    fun `midiMappingSettings default values and updates`() = runBlocking {
        val repo = InMemorySettingsRepository()

        // 1. デフォルト値の検証
        val defaultSettings = repo.midiMappingSettings.value
        assertEquals(com.onigiri.keycue.model.MidiMappingMode.AUTO, defaultSettings.mode)
        assertEquals(com.onigiri.keycue.model.PitchClass.C, defaultSettings.manualRoot)
        assertEquals(com.onigiri.keycue.model.ScaleType.MAJOR, defaultSettings.manualScale)
        assertEquals(4, defaultSettings.manualBaseOctave)

        // 2. saveMidiMappingMode
        repo.saveMidiMappingMode(com.onigiri.keycue.model.MidiMappingMode.MANUAL)
        assertEquals(com.onigiri.keycue.model.MidiMappingMode.MANUAL, repo.midiMappingSettings.value.mode)

        // 3. saveManualRoot
        repo.saveManualRoot(com.onigiri.keycue.model.PitchClass.G)
        assertEquals(com.onigiri.keycue.model.PitchClass.G, repo.midiMappingSettings.value.manualRoot)

        // 4. saveManualScale
        repo.saveManualScale(com.onigiri.keycue.model.ScaleType.NATURAL_MINOR)
        assertEquals(com.onigiri.keycue.model.ScaleType.NATURAL_MINOR, repo.midiMappingSettings.value.manualScale)

        // 5. saveManualBaseOctave と clamp (2..6)
        repo.saveManualBaseOctave(5)
        assertEquals(5, repo.midiMappingSettings.value.manualBaseOctave)

        repo.saveManualBaseOctave(1)
        assertEquals(2, repo.midiMappingSettings.value.manualBaseOctave)

        repo.saveManualBaseOctave(9)
        assertEquals(6, repo.midiMappingSettings.value.manualBaseOctave)

        // 6. saveMidiMappingSettings 一括更新
        val newSettings = com.onigiri.keycue.model.MidiMappingSettings(
            mode = com.onigiri.keycue.model.MidiMappingMode.AUTO,
            manualRoot = com.onigiri.keycue.model.PitchClass.D,
            manualScale = com.onigiri.keycue.model.ScaleType.MAJOR,
            manualBaseOctave = 3
        )
        repo.saveMidiMappingSettings(newSettings)
        assertEquals(newSettings, repo.midiMappingSettings.value)
    }
}
