package com.onigiri.keycue.data

import com.onigiri.keycue.model.ControlOverlayConfig
import com.onigiri.keycue.model.MetronomeConfig
import com.onigiri.keycue.model.MetronomeTimingMode
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

/**
 * ControlOverlayConfig のメトロノーム操作独立化、レイアウト切り替え初期値OFF、
 * 旧設定マイグレーション、および MetronomeConfig の排他制御（Mutex）を検証する単体テスト。
 */
class ControlOverlayConfigMetronomeTest {

    @Test
    fun `ControlOverlayConfig has correct default values`() {
        val config = ControlOverlayConfig()
        assertTrue("メトロノーム操作はデフォルトで表示(true)", config.showMetronomeControl)
        assertTrue("Skyレイアウト切り替えはデフォルトで表示(true)", config.showLayoutSelection)
        assertTrue("最近使った楽曲はデフォルトで表示(true)", config.showRecentSongs)
        assertTrue("ガイドクイック切替はデフォルトで表示(true)", config.showGuideQuickToggles)
    }

    @Test
    fun `InMemorySettingsRepository holds correct defaults and updates control overlay config`() = runBlocking {
        val repo = InMemorySettingsRepository()

        // 初期値（showRecentSongs, showLayoutSelection は true）
        val initial = repo.controlOverlayConfig.value
        assertTrue(initial.showMetronomeControl)
        assertTrue(initial.showLayoutSelection)
        assertTrue(initial.showRecentSongs)

        // 更新
        val updated = initial.copy(showMetronomeControl = false, showLayoutSelection = false, showRecentSongs = false)
        repo.saveControlOverlayConfig(updated)

        assertEquals(updated, repo.controlOverlayConfig.value)
        assertFalse(repo.controlOverlayConfig.value.showMetronomeControl)
        assertFalse(repo.controlOverlayConfig.value.showLayoutSelection)
        assertFalse(repo.controlOverlayConfig.value.showRecentSongs)
    }

    @Test
    fun `SharedPreferences defaults when empty`() {
        val prefs = createFakePrefs(emptyMap())
        val repo = SharedPreferencesSettingsRepository(prefs)

        val config = repo.controlOverlayConfig.value
        assertTrue("未保存時はshowMetronomeControlがtrue", config.showMetronomeControl)
        assertTrue("未保存時はshowLayoutSelectionがtrue", config.showLayoutSelection)
        assertTrue("未保存時はshowRecentSongsがtrue", config.showRecentSongs)
    }

    @Test
    fun `SharedPreferences migrates showMetronomeControl from showGuideQuickToggles when key not present`() {
        // 旧ユーザー: ガイド表示切替をOFFにしていた場合、メトロノーム設定キー未存在ならOFFを継承
        val legacyPrefsOff = createFakePrefs(
            mapOf("control_show_guide_quick_toggles" to false)
        )
        val repoOff = SharedPreferencesSettingsRepository(legacyPrefsOff)
        assertFalse("ガイド切替OFFを継承してメトロノーム操作もOFFになること", repoOff.controlOverlayConfig.value.showMetronomeControl)

        // 旧ユーザー: ガイド表示切替がON（または未設定true）だった場合、ONを継承
        val legacyPrefsOn = createFakePrefs(
            mapOf("control_show_guide_quick_toggles" to true)
        )
        val repoOn = SharedPreferencesSettingsRepository(legacyPrefsOn)
        assertTrue("ガイド切替ONを継承してメトロノーム操作もONになること", repoOn.controlOverlayConfig.value.showMetronomeControl)
    }

    @Test
    fun `SharedPreferences respects saved showMetronomeControl regardless of guideQuickToggles`() {
        // メトロノーム操作キーが保存されている場合は、ガイド切替の設定に関係なく保存値を優先
        val prefs = createFakePrefs(
            mapOf(
                "control_show_guide_quick_toggles" to false,
                "control_show_metronome_control" to true
            )
        )
        val repo = SharedPreferencesSettingsRepository(prefs)
        assertFalse(repo.controlOverlayConfig.value.showGuideQuickToggles)
        assertTrue(repo.controlOverlayConfig.value.showMetronomeControl)
    }

    @Test
    fun `SharedPreferences preserves saved false for existing users`() {
        // 既存ユーザーが明示的にレイアウト切り替えやRecentをOFFにしていた場合は上書きせず維持
        val prefs = createFakePrefs(
            mapOf(
                "control_show_layout_selection" to false,
                "control_show_recent_songs" to false
            )
        )
        val repo = SharedPreferencesSettingsRepository(prefs)
        assertFalse("保存済みのshowLayoutSelection=falseが維持されること", repo.controlOverlayConfig.value.showLayoutSelection)
        assertFalse("保存済みのshowRecentSongs=falseが維持されること", repo.controlOverlayConfig.value.showRecentSongs)
    }

    @Test
    fun `SharedPreferences preserves saved true for existing users`() {
        // 既存ユーザーが明示的にレイアウト切り替えやRecentをONにしていた場合も維持
        val prefs = createFakePrefs(
            mapOf(
                "control_show_layout_selection" to true,
                "control_show_recent_songs" to true
            )
        )
        val repo = SharedPreferencesSettingsRepository(prefs)
        assertTrue("保存済みのshowLayoutSelection=trueが維持されること", repo.controlOverlayConfig.value.showLayoutSelection)
        assertTrue("保存済みのshowRecentSongs=trueが維持されること", repo.controlOverlayConfig.value.showRecentSongs)
    }

    @Test
    fun `SharedPreferences saveControlOverlayConfig persists showMetronomeControl`() = runBlocking {
        val backingMap = mutableMapOf<String, Any>()
        val prefs = createFakePrefsWithBackingMap(backingMap)
        val repo = SharedPreferencesSettingsRepository(prefs)

        val newConfig = repo.controlOverlayConfig.value.copy(showMetronomeControl = false)
        repo.saveControlOverlayConfig(newConfig)

        assertEquals(false, backingMap["control_show_metronome_control"])
        assertEquals(false, repo.controlOverlayConfig.value.showMetronomeControl)
    }

    @Test
    fun `Metronome mutex prevents race condition between update and save`() = runBlocking {
        val backingMap = mutableMapOf<String, Any>()
        val prefs = createFakePrefsWithBackingMap(backingMap)
        val repo = SharedPreferencesSettingsRepository(prefs)

        // 初期設定: enabled = false, timingMode = MANUAL, bpm = 120
        repo.saveMetronomeConfig(
            MetronomeConfig(enabled = false, timingMode = MetronomeTimingMode.MANUAL, bpm = 120)
        )

        // 並行実行シミュレーション:
        // 1. 曲選択による AUTO 復帰: updateMetronomeConfig { it.copy(timingMode = AUTO) }
        // 2. ユーザー操作による ON 切り替え: updateMetronomeConfig { it.copy(enabled = true) }
        val jobs = listOf(
            async {
                repo.updateMetronomeConfig { it.copy(timingMode = MetronomeTimingMode.AUTO) }
            },
            async {
                repo.updateMetronomeConfig { it.copy(enabled = true) }
            }
        )
        jobs.awaitAll()

        val finalConfig = repo.metronomeConfig.value
        assertTrue("ONへの変更が保持されていること", finalConfig.enabled)
        assertEquals("AUTOへの復帰が保持されていること", MetronomeTimingMode.AUTO, finalConfig.timingMode)
        assertEquals(120, finalConfig.bpm)
    }

    // --- テスト用 Fake SharedPreferences ヘルパー ---
    private fun createFakePrefs(initialData: Map<String, Any>): android.content.SharedPreferences {
        return createFakePrefsWithBackingMap(HashMap(initialData))
    }

    private fun createFakePrefsWithBackingMap(map: MutableMap<String, Any>): android.content.SharedPreferences {
        return Proxy.newProxyInstance(
            android.content.SharedPreferences::class.java.classLoader,
            arrayOf(android.content.SharedPreferences::class.java)
        ) { _, method, args ->
            when (method.name) {
                "getString" -> map[args[0]] as? String ?: args[1]
                "getInt" -> map[args[0]] as? Int ?: args[1]
                "getLong" -> (map[args[0]] as? Number)?.toLong() ?: args[1]
                "getFloat" -> (map[args[0]] as? Number)?.toFloat() ?: args[1]
                "getBoolean" -> map[args[0]] as? Boolean ?: args[1]
                "contains" -> map.containsKey(args[0])
                "edit" -> createFakeEditor(map)
                else -> null
            }
        } as android.content.SharedPreferences
    }

    private fun createFakeEditor(map: MutableMap<String, Any>): android.content.SharedPreferences.Editor {
        val tempMap = mutableMapOf<String, Any>()
        val removedKeys = mutableSetOf<String>()
        var shouldClear = false

        return Proxy.newProxyInstance(
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
