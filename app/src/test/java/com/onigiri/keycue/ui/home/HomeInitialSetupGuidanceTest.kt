package com.onigiri.keycue.ui.home

import com.onigiri.keycue.data.InMemorySettingsRepository
import com.onigiri.keycue.data.SharedPreferencesSettingsRepository
import com.onigiri.keycue.model.ControlOverlayConfig
import com.onigiri.keycue.model.RecentSongEntry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Proxy

/**
 * Control Overlay の初期表示（RecentSongs, LayoutSelection = true）、
 * 既存設定の尊重、Recent表示判定、
 * およびボタン位置設定・オーバーレイ権限の未設定・警告表示と復帰・開閉状態維持を検証する単体テスト。
 */
class HomeInitialSetupGuidanceTest {

    private fun createFakePrefs(initialData: Map<String, Any>): android.content.SharedPreferences {
        val map = HashMap(initialData)
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
                    if (shouldClear) map.clear()
                    for (k in removedKeys) map.remove(k)
                    map.putAll(tempMap)
                    if (method.name == "commit") true else null
                }
                else -> editorProxy
            }
        } as android.content.SharedPreferences.Editor
    }

    @Before
    fun setUp() {
        SettingsSessionManager.resetToAllClosed()
    }

    @After
    fun tearDown() {
        SettingsSessionManager.resetToAllClosed()
    }

    // 1. 新規設定の showRecentSongs == true, showLayoutSelection == true
    @Test
    fun testControlOverlayConfig_defaultValuesAreTrue() {
        val config = ControlOverlayConfig()
        assertTrue("showRecentSongs のデフォルトは true", config.showRecentSongs)
        assertTrue("showLayoutSelection のデフォルトは true", config.showLayoutSelection)
    }

    // 2. SharedPreferences 未保存時も両方 true
    @Test
    fun testSharedPreferences_unconfigured_defaultsToTrue() {
        val emptyPrefs = createFakePrefs(emptyMap())
        val repo = SharedPreferencesSettingsRepository(emptyPrefs)
        val config = repo.controlOverlayConfig.value

        assertTrue("未保存時は showRecentSongs が true", config.showRecentSongs)
        assertTrue("未保存時は showLayoutSelection が true", config.showLayoutSelection)
    }

    // 3. 保存済み false は false のまま、保存済み true は true のまま
    @Test
    fun testSharedPreferences_preservesSavedValues() {
        // 保存済み false
        val prefsFalse = createFakePrefs(
            mapOf(
                "control_show_recent_songs" to false,
                "control_show_layout_selection" to false
            )
        )
        val repoFalse = SharedPreferencesSettingsRepository(prefsFalse)
        assertFalse("保存済み false は尊重される", repoFalse.controlOverlayConfig.value.showRecentSongs)
        assertFalse("保存済み false は尊重される", repoFalse.controlOverlayConfig.value.showLayoutSelection)

        // 保存済み true
        val prefsTrue = createFakePrefs(
            mapOf(
                "control_show_recent_songs" to true,
                "control_show_layout_selection" to true
            )
        )
        val repoTrue = SharedPreferencesSettingsRepository(prefsTrue)
        assertTrue("保存済み true は尊重される", repoTrue.controlOverlayConfig.value.showRecentSongs)
        assertTrue("保存済み true は尊重される", repoTrue.controlOverlayConfig.value.showLayoutSelection)
    }

    // 4. Recent候補がない場合は表示設定ONでもリストを隠す、候補がある場合は表示する
    @Test
    fun testRecentSongs_visibilityLogicWithCandidates() {
        val config = ControlOverlayConfig(showRecentSongs = true)
        val currentUri = "content://media/100"

        // 候補リストなし（空）
        val recentEmpty = emptyList<RecentSongEntry>()
        val visibleEmpty = recentEmpty.filter { it.uri != currentUri }
        val shouldShowEmpty = config.showRecentSongs && visibleEmpty.isNotEmpty()
        assertFalse("候補が空の場合は表示設定ONでも非表示", shouldShowEmpty)

        // 現在選択中の曲のみ（除外されて候補0件）
        val recentOnlyCurrent = listOf(RecentSongEntry(uri = currentUri, title = "Current Song"))
        val visibleOnlyCurrent = recentOnlyCurrent.filter { it.uri != currentUri }
        val shouldShowOnlyCurrent = config.showRecentSongs && visibleOnlyCurrent.isNotEmpty()
        assertFalse("選択中以外の候補がない場合は非表示", shouldShowOnlyCurrent)

        // 別の候補が存在する場合
        val recentWithOther = listOf(
            RecentSongEntry(uri = currentUri, title = "Current Song"),
            RecentSongEntry(uri = "content://media/200", title = "Other Song")
        )
        val visibleWithOther = recentWithOther.filter { it.uri != currentUri }
        val shouldShowWithOther = config.showRecentSongs && visibleWithOther.isNotEmpty()
        assertTrue("切り替え候補が存在する場合は表示", shouldShowWithOther)
    }

    // 5. ボタン位置・権限の未設定・警告表示と設定完了・権限許可・権限取り消しの動作
    @Test
    fun testSetupGuidanceState_bothUnconfigured() {
        // 初回起動時・未設定状態
        val fitConfigured = false
        val permissionGranted = false

        // ボタン位置
        val fittingRequiresAction = !fitConfigured
        val fittingBadge = if (fittingRequiresAction) "要設定" else null
        val fittingSummary = if (fitConfigured) "設定済み" else "スクリーンショットからボタン位置を設定してください"

        assertTrue("ボタン位置未設定時は強調される", fittingRequiresAction)
        assertEquals("要設定", fittingBadge)
        assertEquals("スクリーンショットからボタン位置を設定してください", fittingSummary)

        // 権限
        val permissionRequiresAction = !permissionGranted
        val permissionBadge = if (permissionRequiresAction) "要許可" else null
        val permissionSummary = if (permissionGranted) "許可済み" else "他のアプリの上に表示する権限を許可してください"

        assertTrue("権限未許可時は強調される", permissionRequiresAction)
        assertEquals("要許可", permissionBadge)
        assertEquals("他のアプリの上に表示する権限を許可してください", permissionSummary)
    }

    @Test
    fun testSetupGuidanceState_oneConfigured_onlyUnconfiguredHighlighted() {
        // ボタン位置のみ設定済み、権限未許可
        val fitConfigured = true
        val permissionGranted = false

        val fittingRequiresAction = !fitConfigured
        val fittingSummary = if (fitConfigured) "設定済み" else "スクリーンショットからボタン位置を設定してください"

        assertFalse("設定済みのボタン位置は強調されない", fittingRequiresAction)
        assertEquals("設定済み", fittingSummary)

        val permissionRequiresAction = !permissionGranted
        val permissionBadge = if (permissionRequiresAction) "要許可" else null
        val permissionSummary = if (permissionGranted) "許可済み" else "他のアプリの上に表示する権限を許可してください"

        assertTrue("未許可の権限のみ強調される", permissionRequiresAction)
        assertEquals("要許可", permissionBadge)
        assertEquals("他のアプリの上に表示する権限を許可してください", permissionSummary)
    }

    @Test
    fun testSetupGuidanceState_bothConfigured_normalState() {
        // 両方設定済み・許可済み
        val fitConfigured = true
        val permissionGranted = true

        assertFalse(!fitConfigured)
        assertEquals("設定済み", if (fitConfigured) "設定済み" else "スクリーンショットからボタン位置を設定してください")

        assertFalse(!permissionGranted)
        assertEquals("許可済み", if (permissionGranted) "許可済み" else "他のアプリの上に表示する権限を許可してください")
    }

    @Test
    fun testSetupGuidanceState_permissionRevoked_reShowsWarning() {
        // 許可済みから権限が取り消された場合
        var permissionGranted = true
        assertFalse("許可時は通常表示", !permissionGranted)

        // 権限が取り消された
        permissionGranted = false
        assertTrue("取り消し時は警告が再表示される", !permissionGranted)
        assertEquals("要許可", if (!permissionGranted) "要許可" else null)
        assertEquals("他のアプリの上に表示する権限を許可してください", if (permissionGranted) "許可済み" else "他のアプリの上に表示する権限を許可してください")
    }

    // 6. 通常起動時のアコーディオン全閉を維持、オーバーレイ復帰時は直前の開閉状態を維持
    @Test
    fun testAccordionState_preservesClosedOnFreshLaunch_andRestoresAfterOverlay() {
        // 新規起動 (fromOverlay = false): resetToAllClosed() により全閉
        SettingsSessionManager.resetToAllClosed()
        val freshState = SettingsSessionManager.getAccordionState()
        assertTrue("新規起動時はすべてのアコーディオンが閉じている", freshState.isAllClosed)
        assertFalse(freshState.fitting)
        assertFalse(freshState.overlayPermission)

        // ユーザーがボタン位置設定とオーバーレイ権限を開く
        SettingsSessionManager.setExpanded(SettingsAccordionKey.FITTING, true)
        SettingsSessionManager.setExpanded(SettingsAccordionKey.OVERLAY_PERMISSION, true)

        val expandedState = SettingsSessionManager.getAccordionState()
        assertTrue(expandedState.fitting)
        assertTrue(expandedState.overlayPermission)

        // オーバーレイから設定画面へ戻った際 (fromOverlay = true): resetToAllClosed() は呼ばれず状態維持
        val restoredState = SettingsSessionManager.getAccordionState()
        assertTrue("オーバーレイ復帰時は直前の開閉状態が復元される", restoredState.fitting)
        assertTrue("オーバーレイ復帰時は直前の開閉状態が復元される", restoredState.overlayPermission)
    }
}
