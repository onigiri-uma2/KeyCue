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
            initialNoteLeadTimeMs = 1000L,
            initialApproachCircleLeadTimeMs = 200L,
            initialCountdownMs = 5000L
        )

        assertEquals("content://test/song.mid", repo.lastSongUri.value)
        assertEquals(1.25f, repo.speed.value, 0.001f)
        assertEquals(1000L, repo.noteLeadTimeMs.value)
        assertEquals(200L, repo.approachCircleLeadTimeMs.value)
        assertEquals(5000L, repo.countdownMs.value)
        assertEquals(1.25f, repo.playbackConfig.value.speed, 0.001f)
        assertEquals(1000L, repo.playbackConfig.value.noteLeadTimeMs)
    }

    @Test
    fun `InMemorySettingsRepository holds default values`() {
        val repo = InMemorySettingsRepository()

        assertNull(repo.lastSongUri.value)
        assertEquals(1.0f, repo.speed.value, 0.001f)
        assertEquals(300L, repo.noteLeadTimeMs.value)
        assertEquals(200L, repo.approachCircleLeadTimeMs.value)
        assertEquals(3000L, repo.countdownMs.value)
        assertEquals(300L, repo.playbackConfig.value.noteLeadTimeMs)
        assertEquals(200L, repo.playbackConfig.value.approachCircleLeadTimeMs)
        assertEquals(false, repo.visualConfig.value.showChordLinks)
        assertEquals(true, repo.visualConfig.value.showChordHalos)
        assertEquals(2.0f, repo.visualConfig.value.chordStrokeWidthDp, 0.001f)
        assertEquals(60, repo.visualConfig.value.chordStrokeAlphaPercent)
        assertEquals(10, repo.visualConfig.value.chordHaloFillAlphaPercent)
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
    fun `saveNoteLeadTimeMs clamps invalid values without affecting approachCircleLeadTimeMs`() = runBlocking {
        val repo = InMemorySettingsRepository(
            initialNoteLeadTimeMs = 500L,
            initialApproachCircleLeadTimeMs = 400L
        )

        // 下限 clamp (300L) しても approachCircle (400L) は独立して維持される (Circle > Note 許容)
        repo.saveNoteLeadTimeMs(100L)
        assertEquals(300L, repo.noteLeadTimeMs.value)
        assertEquals(400L, repo.approachCircleLeadTimeMs.value)

        // 上限 clamp (2000L)
        repo.saveNoteLeadTimeMs(5000L)
        assertEquals(2000L, repo.noteLeadTimeMs.value)
    }

    @Test
    fun `saveApproachCircleLeadTimeMs clamps invalid values independently of noteLeadTimeMs`() = runBlocking {
        val repo = InMemorySettingsRepository(
            initialNoteLeadTimeMs = 300L,
            initialApproachCircleLeadTimeMs = 200L
        )

        // 下限 clamp (100L)
        repo.saveApproachCircleLeadTimeMs(50L)
        assertEquals(100L, repo.approachCircleLeadTimeMs.value)

        // noteLeadTimeMs (300L) を超えて独立して設定可能
        repo.saveApproachCircleLeadTimeMs(500L)
        assertEquals(500L, repo.approachCircleLeadTimeMs.value)

        // 上限 clamp (2000L)
        repo.saveApproachCircleLeadTimeMs(3000L)
        assertEquals(2000L, repo.approachCircleLeadTimeMs.value)
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
    fun `savePlaybackConfig updates all fields with normalization`() = runBlocking {
        val repo = InMemorySettingsRepository()
        repo.savePlaybackConfig(PlaybackConfig(speed = 1.5f, noteLeadTimeMs = 1500L, approachCircleLeadTimeMs = 500L, countdownMs = 1000L))

        assertEquals(1.5f, repo.speed.value, 0.001f)
        assertEquals(1500L, repo.noteLeadTimeMs.value)
        assertEquals(500L, repo.approachCircleLeadTimeMs.value)
        assertEquals(1000L, repo.countdownMs.value)
    }

    @Test
    fun `partial playback update preserves unrelated fields`() = runBlocking {
        val repo = InMemorySettingsRepository(
            initialNoteLeadTimeMs = 1500L,
            initialApproachCircleLeadTimeMs = 750L,
            initialCountdownMs = 5000L
        )

        repo.saveSpeed(1.5f)

        assertEquals(
            PlaybackConfig(1.5f, 1500L, 750L, 5000L),
            repo.playbackConfig.value
        )
    }

    @Test
    fun `PlaybackConfig normalization independently clamps playback values`() = runBlocking {
        val repo = InMemorySettingsRepository()

        // note = 1L (clamped to 300), circle = 9000L (clamped to 2000)
        repo.savePlaybackConfig(PlaybackConfig(9f, 1L, 9_000L, -1L))

        assertEquals(PlaybackConfig(2f, 300L, 2000L, 0L), repo.playbackConfig.value)
    }

    @Test
    fun `savePlaybackConfig allows circle lead time greater than note lead time`() = runBlocking {
        val repo = InMemorySettingsRepository()

        // Circle > Note (Note = 500L, Circle = 1800L) がそのまま保存されること
        repo.savePlaybackConfig(PlaybackConfig(1.0f, 500L, 1800L, 3000L))

        assertEquals(500L, repo.noteLeadTimeMs.value)
        assertEquals(1800L, repo.approachCircleLeadTimeMs.value)
        assertEquals(PlaybackConfig(1.0f, 500L, 1800L, 3000L), repo.playbackConfig.value)
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
        assertEquals(false, defaultConfig.showGuideLabels)
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
        assertEquals(2.0f, defaultConfig.chordStrokeWidthDp, 0.001f)
        assertEquals(60, defaultConfig.chordStrokeAlphaPercent)
        assertEquals(10, defaultConfig.chordHaloFillAlphaPercent)

        // 2. 各フィールドの更新
        repo.saveShowGuideLabels(true)
        assertEquals(true, repo.visualConfig.value.showGuideLabels)

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

        repo.saveChordStrokeWidthDp(3.5f)
        assertEquals(3.5f, repo.visualConfig.value.chordStrokeWidthDp, 0.001f)

        repo.saveChordStrokeAlphaPercent(85)
        assertEquals(85, repo.visualConfig.value.chordStrokeAlphaPercent)

        repo.saveChordHaloFillAlphaPercent(35)
        assertEquals(35, repo.visualConfig.value.chordHaloFillAlphaPercent)

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

        // 4. chordStrokeWidthDp の clamp テスト (0.5f .. 6.0f)
        repo.saveChordStrokeWidthDp(0.1f)
        assertEquals(0.5f, repo.visualConfig.value.chordStrokeWidthDp, 0.001f)

        repo.saveChordStrokeWidthDp(10.0f)
        assertEquals(6.0f, repo.visualConfig.value.chordStrokeWidthDp, 0.001f)

        // 5. chordStrokeAlphaPercent の clamp テスト (20 .. 100)
        repo.saveChordStrokeAlphaPercent(10)
        assertEquals(20, repo.visualConfig.value.chordStrokeAlphaPercent)

        repo.saveChordStrokeAlphaPercent(150)
        assertEquals(100, repo.visualConfig.value.chordStrokeAlphaPercent)

        // 6. chordHaloFillAlphaPercent の clamp テスト (10 .. 50)
        repo.saveChordHaloFillAlphaPercent(5)
        assertEquals(10, repo.visualConfig.value.chordHaloFillAlphaPercent)

        repo.saveChordHaloFillAlphaPercent(80)
        assertEquals(50, repo.visualConfig.value.chordHaloFillAlphaPercent)

        // 7. VisualConfig.safe() によるプロパティ保持テスト
        val safeCfg = com.onigiri.keycue.model.VisualConfig.safe(
            showChordLinks = false,
            showChordHalos = true,
            chordStrokeWidthDp = 4.0f,
            chordStrokeAlphaPercent = 75,
            chordHaloFillAlphaPercent = 25
        )
        assertEquals(false, safeCfg.showChordLinks)
        assertEquals(true, safeCfg.showChordHalos)
        assertEquals(4.0f, safeCfg.chordStrokeWidthDp, 0.001f)
        assertEquals(75, safeCfg.chordStrokeAlphaPercent)
        assertEquals(25, safeCfg.chordHaloFillAlphaPercent)
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

    @Test
    fun `SharedPreferences backwards compatibility with legacy keys`() {
        val legacyPrefs = createFakePrefs(
            mapOf(
                "lead_time_ms" to 700L,
                "highlight_time_ms" to 300L
            )
        )
        val repo = SharedPreferencesSettingsRepository(legacyPrefs)

        assertEquals(700L, repo.noteLeadTimeMs.value)
        assertEquals(300L, repo.approachCircleLeadTimeMs.value)
        assertEquals(700L, repo.playbackConfig.value.noteLeadTimeMs)
        assertEquals(300L, repo.playbackConfig.value.approachCircleLeadTimeMs)
    }

    @Test
    fun `SharedPreferences allows saved values where circle exceeds note`() {
        val prefs = createFakePrefs(
            mapOf(
                "lead_time_ms" to 300L,
                "highlight_time_ms" to 400L
            )
        )
        val repo = SharedPreferencesSettingsRepository(prefs)

        // Circle > Note が許可され、それぞれの値がそのまま復元される
        assertEquals(300L, repo.noteLeadTimeMs.value)
        assertEquals(400L, repo.approachCircleLeadTimeMs.value)
        assertEquals(400L, repo.playbackConfig.value.approachCircleLeadTimeMs)
    }

    @Test
    fun `SharedPreferences defaults to 300ms note and 200ms approach circle when empty`() {
        val emptyPrefs = createFakePrefs(emptyMap())
        val repo = SharedPreferencesSettingsRepository(emptyPrefs)

        assertEquals(300L, repo.noteLeadTimeMs.value)
        assertEquals(200L, repo.approachCircleLeadTimeMs.value)
        assertEquals(PlaybackConfig.DEFAULT_NOTE_LEAD_TIME_MS, repo.noteLeadTimeMs.value)
        assertEquals(PlaybackConfig.DEFAULT_APPROACH_CIRCLE_LEAD_TIME_MS, repo.approachCircleLeadTimeMs.value)
    }

    @Test
    fun `SharedPreferences persists chordHaloFillAlphaPercent across instances`() = runBlocking {
        val prefs = createFakePrefs(emptyMap())
        val repo1 = SharedPreferencesSettingsRepository(prefs)

        // 初期値は 10%
        assertEquals(10, repo1.visualConfig.value.chordHaloFillAlphaPercent)

        // 35% に変更して保存
        repo1.saveChordHaloFillAlphaPercent(35)
        assertEquals(35, repo1.visualConfig.value.chordHaloFillAlphaPercent)

        // アプリ再起動を模して同一 prefs から新しいリポジトリインスタンスを生成
        val repo2 = SharedPreferencesSettingsRepository(prefs)
        assertEquals(35, repo2.visualConfig.value.chordHaloFillAlphaPercent)

        // clamp の永続化 (上限超え 60% -> 50%)
        repo2.saveChordHaloFillAlphaPercent(60)
        assertEquals(50, repo2.visualConfig.value.chordHaloFillAlphaPercent)

        val repo3 = SharedPreferencesSettingsRepository(prefs)
        assertEquals(50, repo3.visualConfig.value.chordHaloFillAlphaPercent)
    }

    @Test
    fun `saveFitProfile does not overwrite guideRadiusRatio`() = runBlocking {
        val prefs = createFakePrefs(emptyMap())
        val repo = SharedPreferencesSettingsRepository(prefs)

        // VisualConfig.guideRadiusRatio = 0.06f に設定
        repo.saveGuideRadiusRatio(0.06f)
        assertEquals(0.06f, repo.visualConfig.value.guideRadiusRatio, 0.001f)

        // FitProfile.keyRadiusRatio = 0.04f のプロファイルを保存
        val profile = com.onigiri.keycue.model.FitProfile.createDefaultTestProfile().copy(keyRadiusRatio = 0.04f)
        repo.saveFitProfile(profile)

        // VisualConfig.guideRadiusRatio が 0.06f のまま維持されること
        assertEquals(0.06f, repo.visualConfig.value.guideRadiusRatio, 0.001f)
    }

    @Test
    fun `saveFitProfile does not modify VisualConfig`() = runBlocking {
        val prefs = createFakePrefs(emptyMap())
        val repo = SharedPreferencesSettingsRepository(prefs)

        val customVisualConfig = com.onigiri.keycue.model.VisualConfig(
            showGuideLabels = true,
            showFallingNotes = false,
            showApproachCircles = false,
            showRepeatCountBadge = false,
            showJustEffect = true,
            guideRadiusRatio = 0.065f,
            guideColor = 0xFF123456.toInt(),
            noteColorTop = 0xFF112233.toInt(),
            noteColorMiddle = 0xFF445566.toInt(),
            noteColorBottom = 0xFF778899.toInt(),
            showChordLinks = true,
            showChordHalos = false,
            chordStrokeWidthDp = 3.5f,
            chordStrokeAlphaPercent = 80,
            chordHaloFillAlphaPercent = 25
        )
        repo.saveVisualConfig(customVisualConfig)

        val profile = com.onigiri.keycue.model.FitProfile.createDefaultTestProfile().copy(keyRadiusRatio = 0.035f)
        repo.saveFitProfile(profile)

        // FitProfileが保存されていること
        assertEquals(profile, repo.fitProfile.value)

        // VisualConfigの各プロパティが一切変更されていないこと
        val currentVisual = repo.visualConfig.value
        assertEquals(customVisualConfig.showGuideLabels, currentVisual.showGuideLabels)
        assertEquals(customVisualConfig.showFallingNotes, currentVisual.showFallingNotes)
        assertEquals(customVisualConfig.showApproachCircles, currentVisual.showApproachCircles)
        assertEquals(customVisualConfig.showRepeatCountBadge, currentVisual.showRepeatCountBadge)
        assertEquals(customVisualConfig.showJustEffect, currentVisual.showJustEffect)
        assertEquals(customVisualConfig.guideRadiusRatio, currentVisual.guideRadiusRatio, 0.001f)
        assertEquals(customVisualConfig.guideColor, currentVisual.guideColor)
        assertEquals(customVisualConfig.noteColorTop, currentVisual.noteColorTop)
        assertEquals(customVisualConfig.noteColorMiddle, currentVisual.noteColorMiddle)
        assertEquals(customVisualConfig.noteColorBottom, currentVisual.noteColorBottom)
        assertEquals(customVisualConfig.showChordLinks, currentVisual.showChordLinks)
        assertEquals(customVisualConfig.showChordHalos, currentVisual.showChordHalos)
        assertEquals(customVisualConfig.chordStrokeWidthDp, currentVisual.chordStrokeWidthDp, 0.001f)
        assertEquals(customVisualConfig.chordStrokeAlphaPercent, currentVisual.chordStrokeAlphaPercent)
        assertEquals(customVisualConfig.chordHaloFillAlphaPercent, currentVisual.chordHaloFillAlphaPercent)
    }

    private fun createFakePrefs(initialData: Map<String, Any>): android.content.SharedPreferences {
        val map = HashMap<String, Any>(initialData)
        return java.lang.reflect.Proxy.newProxyInstance(
            android.content.SharedPreferences::class.java.classLoader,
            arrayOf(android.content.SharedPreferences::class.java)
        ) { proxy, method, args ->
            when (method.name) {
                "getLong" -> {
                    val key = args[0] as String
                    val def = args[1] as Long
                    (map[key] as? Long) ?: def
                }
                "getFloat" -> {
                    val key = args[0] as String
                    val def = args[1] as Float
                    (map[key] as? Float) ?: def
                }
                "getString" -> {
                    val key = args[0] as String
                    val def = if (args != null && args.size > 1) args[1] as? String else null
                    (map[key] as? String) ?: def
                }
                "getBoolean" -> {
                    val key = args[0] as String
                    val def = args[1] as Boolean
                    (map[key] as? Boolean) ?: def
                }
                "getInt" -> {
                    val key = args[0] as String
                    val def = args[1] as Int
                    (map[key] as? Int) ?: def
                }
                "contains" -> {
                    map.containsKey(args[0] as String)
                }
                "edit" -> {
                    createFakeEditor(map)
                }
                "registerOnSharedPreferenceChangeListener", "unregisterOnSharedPreferenceChangeListener" -> null
                else -> null
            }
        } as android.content.SharedPreferences
    }

    private fun createFakeEditor(map: HashMap<String, Any>): android.content.SharedPreferences.Editor {
        val tempMap = HashMap<String, Any>()
        val removedKeys = HashSet<String>()
        var shouldClear = false

        return java.lang.reflect.Proxy.newProxyInstance(
            android.content.SharedPreferences.Editor::class.java.classLoader,
            arrayOf(android.content.SharedPreferences.Editor::class.java)
        ) { editorProxy, method, args ->
            when (method.name) {
                "putBoolean" -> {
                    tempMap[args[0] as String] = args[1] as Boolean
                    removedKeys.remove(args[0] as String)
                    editorProxy
                }
                "putInt" -> {
                    tempMap[args[0] as String] = args[1] as Int
                    removedKeys.remove(args[0] as String)
                    editorProxy
                }
                "putFloat" -> {
                    tempMap[args[0] as String] = args[1] as Float
                    removedKeys.remove(args[0] as String)
                    editorProxy
                }
                "putLong" -> {
                    tempMap[args[0] as String] = args[1] as Long
                    removedKeys.remove(args[0] as String)
                    editorProxy
                }
                "putString" -> {
                    if (args[1] != null) {
                        tempMap[args[0] as String] = args[1] as String
                        removedKeys.remove(args[0] as String)
                    } else {
                        removedKeys.add(args[0] as String)
                        tempMap.remove(args[0] as String)
                    }
                    editorProxy
                }
                "remove" -> {
                    removedKeys.add(args[0] as String)
                    tempMap.remove(args[0] as String)
                    editorProxy
                }
                "clear" -> {
                    shouldClear = true
                    tempMap.clear()
                    editorProxy
                }
                "apply", "commit" -> {
                    if (shouldClear) {
                        map.clear()
                    }
                    for (k in removedKeys) {
                        map.remove(k)
                    }
                    map.putAll(tempMap)
                    if (method.name == "commit") true else null
                }
                else -> editorProxy
            }
        } as android.content.SharedPreferences.Editor
    }
}

