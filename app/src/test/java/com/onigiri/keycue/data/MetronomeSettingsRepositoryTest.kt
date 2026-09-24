package com.onigiri.keycue.data

import android.content.SharedPreferences
import com.onigiri.keycue.model.BeatSubdivision
import com.onigiri.keycue.model.MetronomeConfig
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

/**
 * [SettingsRepository] における [MetronomeConfig] の保存・復元・正規化テスト。
 */
class MetronomeSettingsRepositoryTest {

    @Test
    fun `InMemorySettingsRepositoryは初期値enabled=falseを保持する`() {
        val repo = InMemorySettingsRepository()
        assertFalse(repo.metronomeConfig.value.enabled)
        assertEquals(MetronomeConfig.DEFAULT_BPM, repo.metronomeConfig.value.bpm)
        assertEquals(MetronomeConfig.DEFAULT_BEATS_PER_BAR, repo.metronomeConfig.value.beatsPerBar)
        assertEquals(BeatSubdivision.QUARTER, repo.metronomeConfig.value.subdivision)
        assertTrue(repo.metronomeConfig.value.accentEnabled)
        assertEquals(MetronomeConfig.DEFAULT_VOLUME_PERCENT, repo.metronomeConfig.value.volumePercent)
        assertEquals(MetronomeConfig.DEFAULT_BEAT_OFFSET_MS, repo.metronomeConfig.value.beatOffsetMs)
    }

    @Test
    fun `InMemorySettingsRepositoryは設定を保存・更新できる`() = runBlocking {
        val repo = InMemorySettingsRepository()
        val newConfig = MetronomeConfig(
            enabled = true,
            bpm = 140,
            beatsPerBar = 3,
            subdivision = BeatSubdivision.EIGHTH,
            accentEnabled = false,
            volumePercent = 80,
            beatOffsetMs = 50L
        )
        repo.saveMetronomeConfig(newConfig)

        val saved = repo.metronomeConfig.value
        assertTrue(saved.enabled)
        assertEquals(140, saved.bpm)
        assertEquals(3, saved.beatsPerBar)
        assertEquals(BeatSubdivision.EIGHTH, saved.subdivision)
        assertFalse(saved.accentEnabled)
        assertEquals(80, saved.volumePercent)
        assertEquals(50L, saved.beatOffsetMs)
    }

    @Test
    fun `不正な値は正規化されて保存される`() = runBlocking {
        val repo = InMemorySettingsRepository()
        // 範囲外のBPM(10), 範囲外の拍子(5), 範囲外の音量(150), 範囲外のオフセット(5000)
        val invalidConfig = MetronomeConfig(
            enabled = true,
            bpm = 10,
            beatsPerBar = 5,
            subdivision = BeatSubdivision.QUARTER,
            accentEnabled = true,
            volumePercent = 150,
            beatOffsetMs = 5000L
        )
        repo.saveMetronomeConfig(invalidConfig)

        val saved = repo.metronomeConfig.value
        assertEquals("BPMはMIN_BPM(40)へclamp", MetronomeConfig.MIN_BPM, saved.bpm)
        assertEquals("拍子はDEFAULT(4)へ正規化", MetronomeConfig.DEFAULT_BEATS_PER_BAR, saved.beatsPerBar)
        assertEquals("音量はMAX(100)へclamp", MetronomeConfig.MAX_VOLUME_PERCENT, saved.volumePercent)
        assertEquals("オフセットはMAX(2000)へclamp", MetronomeConfig.MAX_BEAT_OFFSET_MS, saved.beatOffsetMs)
    }

    @Test
    fun `SharedPreferencesSettingsRepository restores default when prefs empty`() {
        val fakePrefs = createFakePrefs(emptyMap())
        val repo = SharedPreferencesSettingsRepository(fakePrefs)

        val config = repo.metronomeConfig.value
        assertFalse("初期値はOFF", config.enabled)
        assertEquals(120, config.bpm)
        assertEquals(4, config.beatsPerBar)
        assertEquals(BeatSubdivision.QUARTER, config.subdivision)
        assertTrue(config.accentEnabled)
        assertEquals(30, config.volumePercent)
        assertEquals(0L, config.beatOffsetMs)
    }

    @Test
    fun `SharedPreferencesSettingsRepository saves and restores across instances`() = runBlocking {
        val fakePrefs = createFakePrefs(emptyMap())
        val repo1 = SharedPreferencesSettingsRepository(fakePrefs)

        val targetConfig = MetronomeConfig(
            enabled = true,
            bpm = 160,
            beatsPerBar = 3,
            subdivision = BeatSubdivision.EIGHTH,
            accentEnabled = false,
            volumePercent = 50,
            beatOffsetMs = -20L
        )
        repo1.saveMetronomeConfig(targetConfig)

        // 新しいリポジトリインスタンスでSharedPreferencesから復元
        val repo2 = SharedPreferencesSettingsRepository(fakePrefs)
        val restored = repo2.metronomeConfig.value

        assertTrue(restored.enabled)
        assertEquals(160, restored.bpm)
        assertEquals(3, restored.beatsPerBar)
        assertEquals(BeatSubdivision.EIGHTH, restored.subdivision)
        assertFalse(restored.accentEnabled)
        assertEquals(50, restored.volumePercent)
        assertEquals(-20L, restored.beatOffsetMs)
    }

    @Test
    fun `SharedPreferencesSettingsRepository normalizes corrupted stored values`() {
        val corruptedPrefs = createFakePrefs(
            mapOf(
                "metronome_enabled" to true,
                "metronome_bpm" to 9999, // 範囲外
                "metronome_beats_per_bar" to 9, // 不正拍子
                "metronome_subdivision" to "INVALID_SUBDIVISION",
                "metronome_accent_enabled" to true,
                "metronome_volume_percent" to -50, // 範囲外
                "metronome_beat_offset_ms" to 100000L // 範囲外
            )
        )
        val repo = SharedPreferencesSettingsRepository(corruptedPrefs)
        val config = repo.metronomeConfig.value

        assertTrue(config.enabled)
        assertEquals(MetronomeConfig.MAX_BPM, config.bpm)
        assertEquals(MetronomeConfig.DEFAULT_BEATS_PER_BAR, config.beatsPerBar)
        assertEquals(BeatSubdivision.QUARTER, config.subdivision)
        assertTrue(config.accentEnabled)
        assertEquals(MetronomeConfig.MIN_VOLUME_PERCENT, config.volumePercent)
        assertEquals(MetronomeConfig.MAX_BEAT_OFFSET_MS, config.beatOffsetMs)
    }

    @Test
    fun `新規インストール時は初期タイミングモードがAUTOかつenabled=falseであること`() {
        val fakePrefs = createFakePrefs(emptyMap())
        val repo = SharedPreferencesSettingsRepository(fakePrefs)

        val config = repo.metronomeConfig.value
        assertFalse("初期値はOFF", config.enabled)
        assertEquals("新規インストール時はAUTO", com.onigiri.keycue.model.MetronomeTimingMode.AUTO, config.timingMode)
    }

    @Test
    fun `旧メトロノーム設定が存在する環境からの移行時は初期モードがMANUALになること`() {
        // 第1段階でBPM=140が保存されており、timingModeキーは存在しない
        val legacyPrefsMap = mapOf<String, Any>(
            "metronome_bpm" to 140,
            "metronome_beats_per_bar" to 3,
            "metronome_enabled" to true
        )
        val fakePrefs = createFakePrefs(legacyPrefsMap)
        val repo = SharedPreferencesSettingsRepository(fakePrefs)

        val config = repo.metronomeConfig.value
        assertTrue("保存済みのenabledが反映", config.enabled)
        assertEquals("保存済みのBPMが反映", 140, config.bpm)
        assertEquals("保存済みの拍子が反映", 3, config.beatsPerBar)
        assertEquals("既存ユーザーはMANUALにフォールバック", com.onigiri.keycue.model.MetronomeTimingMode.MANUAL, config.timingMode)
    }

    @Test
    fun `明示的に保存されたタイミングモードが再起動後も正しく復元されること`() = runBlocking {
        val fakePrefs = createFakePrefs(emptyMap())
        val repo1 = SharedPreferencesSettingsRepository(fakePrefs)

        // AUTOで保存
        repo1.saveMetronomeConfig(
            MetronomeConfig(
                enabled = true,
                timingMode = com.onigiri.keycue.model.MetronomeTimingMode.AUTO,
                bpm = 150
            )
        )

        // 別インスタンスでロード
        val repo2 = SharedPreferencesSettingsRepository(fakePrefs)
        assertEquals(com.onigiri.keycue.model.MetronomeTimingMode.AUTO, repo2.metronomeConfig.value.timingMode)
        assertEquals(150, repo2.metronomeConfig.value.bpm)

        // MANUALに変更して保存
        repo2.saveMetronomeConfig(repo2.metronomeConfig.value.copy(timingMode = com.onigiri.keycue.model.MetronomeTimingMode.MANUAL))

        // 再度別インスタンスでロード
        val repo3 = SharedPreferencesSettingsRepository(fakePrefs)
        assertEquals(com.onigiri.keycue.model.MetronomeTimingMode.MANUAL, repo3.metronomeConfig.value.timingMode)
    }

    private fun createFakePrefs(initialData: Map<String, Any>): SharedPreferences {
        val map = HashMap<String, Any>(initialData)
        return Proxy.newProxyInstance(
            SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java)
        ) { _, method, args ->
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
                "contains" -> map.containsKey(args[0] as String)
                "edit" -> createFakeEditor(map)
                else -> null
            }
        } as SharedPreferences
    }

    private fun createFakeEditor(map: HashMap<String, Any>): SharedPreferences.Editor {
        val tempMap = HashMap<String, Any>()
        val removedKeys = HashSet<String>()
        var shouldClear = false

        return Proxy.newProxyInstance(
            SharedPreferences.Editor::class.java.classLoader,
            arrayOf(SharedPreferences.Editor::class.java)
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
        } as SharedPreferences.Editor
    }
}
