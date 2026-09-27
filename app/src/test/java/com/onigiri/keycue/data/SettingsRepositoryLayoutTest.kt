package com.onigiri.keycue.data

import com.onigiri.keycue.profile.SkyLayout
import com.onigiri.keycue.profile.SkyLayoutAdjustment
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

/**
 * [SettingsRepository] におけるレイアウト関連設定（選択レイアウト、レイアウト別微調整、レイアウト別ガイドラベル表示）の単体テスト。
 * 6種類のレイアウト（TOUCH 2種、PAD 4種）に完全対応し、旧IDからのマイグレーションおよび独立保存を検証します。
 */
class SettingsRepositoryLayoutTest {

    @Test
    fun testInMemorySettingsRepository_layoutDefaults() {
        val repo = InMemorySettingsRepository(
            initialVisualConfig = com.onigiri.keycue.model.VisualConfig(showGuideLabels = true)
        )

        assertEquals(SkyLayout.TOUCH_STANDARD, repo.selectedSkyLayout.value)

        for (layout in SkyLayout.entries) {
            assertEquals(SkyLayoutAdjustment.DEFAULT, repo.getLayoutAdjustment(layout))
        }

        // TOUCH系は visualConfig.showGuideLabels (true) を引き継ぐ
        assertTrue(repo.getLayoutShowGuideLabels(SkyLayout.TOUCH_STANDARD))
        assertTrue(repo.getLayoutShowGuideLabels(SkyLayout.TOUCH_EXPANDED))

        // PAD系（4種）は初期値OFF (false)
        assertFalse(repo.getLayoutShowGuideLabels(SkyLayout.PAD_TRIGGER_FIRST))
        assertFalse(repo.getLayoutShowGuideLabels(SkyLayout.PAD_DPAD_FIRST))
        assertFalse(repo.getLayoutShowGuideLabels(SkyLayout.PAD_GRID_STANDARD))
        assertFalse(repo.getLayoutShowGuideLabels(SkyLayout.PAD_GRID_EXPANDED))
    }

    @Test
    fun testInMemorySettingsRepository_saveSelectedSkyLayout() = runBlocking {
        val repo = InMemorySettingsRepository()

        repo.saveSelectedSkyLayout(SkyLayout.PAD_TRIGGER_FIRST)
        assertEquals(SkyLayout.PAD_TRIGGER_FIRST, repo.selectedSkyLayout.value)

        repo.saveSelectedSkyLayout(SkyLayout.PAD_DPAD_FIRST)
        assertEquals(SkyLayout.PAD_DPAD_FIRST, repo.selectedSkyLayout.value)

        repo.saveSelectedSkyLayout(SkyLayout.PAD_GRID_STANDARD)
        assertEquals(SkyLayout.PAD_GRID_STANDARD, repo.selectedSkyLayout.value)

        repo.saveSelectedSkyLayout(SkyLayout.PAD_GRID_EXPANDED)
        assertEquals(SkyLayout.PAD_GRID_EXPANDED, repo.selectedSkyLayout.value)
    }

    @Test
    fun testInMemorySettingsRepository_layoutAdjustmentIndependence() = runBlocking {
        val repo = InMemorySettingsRepository()
        val standardPreset = com.onigiri.keycue.profile.SkyLayoutRegistry.getPreset(SkyLayout.TOUCH_STANDARD)
        repo.saveFitProfile(com.onigiri.keycue.model.FitProfile(standardPreset.keyCenters))

        val adjPad1 = SkyLayoutAdjustment(offsetX = 0.02f, offsetY = -0.01f, scaleX = 1.05f, scaleY = 0.95f)
        val adjPad2 = SkyLayoutAdjustment(offsetX = -0.01f, offsetY = 0.02f, scaleX = 1.02f, scaleY = 0.98f)
        val adjGridStd = SkyLayoutAdjustment(offsetX = -0.03f, offsetY = 0.04f, scaleX = 0.90f, scaleY = 1.10f)

        val res1 = repo.saveLayoutAdjustment(SkyLayout.PAD_TRIGGER_FIRST, adjPad1)
        val res2 = repo.saveLayoutAdjustment(SkyLayout.PAD_DPAD_FIRST, adjPad2)
        val res3 = repo.saveLayoutAdjustment(SkyLayout.PAD_GRID_STANDARD, adjGridStd)

        assertTrue(res1)
        assertTrue(res2)
        assertTrue(res3)
        assertEquals(adjPad1, repo.getLayoutAdjustment(SkyLayout.PAD_TRIGGER_FIRST))
        assertEquals(adjPad2, repo.getLayoutAdjustment(SkyLayout.PAD_DPAD_FIRST))
        assertEquals(adjGridStd, repo.getLayoutAdjustment(SkyLayout.PAD_GRID_STANDARD))
        assertEquals(SkyLayoutAdjustment.DEFAULT, repo.getLayoutAdjustment(SkyLayout.TOUCH_STANDARD))
    }

    @Test
    fun testLayoutAdjustment_outOfBounds_retainsPreviousValidAdjustment() = runBlocking {
        val repo = InMemorySettingsRepository()
        val standardPreset = com.onigiri.keycue.profile.SkyLayoutRegistry.getPreset(SkyLayout.TOUCH_STANDARD)
        repo.saveFitProfile(com.onigiri.keycue.model.FitProfile(standardPreset.keyCenters))

        val validAdj = SkyLayoutAdjustment(offsetX = 0.01f, offsetY = 0.01f, scaleX = 1.0f, scaleY = 1.0f)
        assertTrue(repo.saveLayoutAdjustment(SkyLayout.PAD_TRIGGER_FIRST, validAdj))
        assertEquals(validAdj, repo.getLayoutAdjustment(SkyLayout.PAD_TRIGGER_FIRST))

        // 画面外に大きく飛び出す無効な微調整値 (offsetX = 0.50f)
        val invalidAdj = SkyLayoutAdjustment(offsetX = 0.50f, offsetY = 0.00f, scaleX = 1.0f, scaleY = 1.0f)

        assertFalse(repo.updateLayoutAdjustmentInMemory(SkyLayout.PAD_TRIGGER_FIRST, invalidAdj))
        assertFalse(repo.saveLayoutAdjustment(SkyLayout.PAD_TRIGGER_FIRST, invalidAdj))

        assertEquals(validAdj, repo.getLayoutAdjustment(SkyLayout.PAD_TRIGGER_FIRST))
    }

    @Test
    fun testLabelSettings_allUpdatePaths_areSynchronizedAndSerialized() = runBlocking {
        val repo = InMemorySettingsRepository(
            initialVisualConfig = com.onigiri.keycue.model.VisualConfig(showGuideLabels = true)
        )

        // 1. 初期状態: TOUCH_STANDARD は true
        assertEquals(SkyLayout.TOUCH_STANDARD, repo.selectedSkyLayout.value)
        assertTrue(repo.visualConfig.value.showGuideLabels)
        assertTrue(repo.getLayoutShowGuideLabels(SkyLayout.TOUCH_STANDARD))

        // 2. saveShowGuideLabels(false) で TOUCH_STANDARD のラベル設定と visualConfig が同期
        repo.saveShowGuideLabels(false)
        assertFalse(repo.visualConfig.value.showGuideLabels)
        assertFalse(repo.getLayoutShowGuideLabels(SkyLayout.TOUCH_STANDARD))

        // 3. updateVisualConfig で showGuideLabels を true に更新した場合、選択中レイアウトにも同期
        repo.updateVisualConfig { it.copy(showGuideLabels = true) }
        assertTrue(repo.visualConfig.value.showGuideLabels)
        assertTrue(repo.getLayoutShowGuideLabels(SkyLayout.TOUCH_STANDARD))

        // 4. saveVisualConfig で showGuideLabels を false に更新した場合も同期
        repo.saveVisualConfig(repo.visualConfig.value.copy(showGuideLabels = false))
        assertFalse(repo.visualConfig.value.showGuideLabels)
        assertFalse(repo.getLayoutShowGuideLabels(SkyLayout.TOUCH_STANDARD))

        // 5. レイアウトを PAD_TRIGGER_FIRST（デフォルトOFF）に切り替え
        repo.saveSelectedSkyLayout(SkyLayout.PAD_TRIGGER_FIRST)
        assertEquals(SkyLayout.PAD_TRIGGER_FIRST, repo.selectedSkyLayout.value)
        assertFalse(repo.visualConfig.value.showGuideLabels)

        // 6. PAD_TRIGGER_FIRST でラベルを ON に変更
        repo.saveShowGuideLabels(true)
        assertTrue(repo.visualConfig.value.showGuideLabels)
        assertTrue(repo.getLayoutShowGuideLabels(SkyLayout.PAD_TRIGGER_FIRST))

        // 7. TOUCH_STANDARD に戻すと、TOUCH_STANDARD の前回の保存値 (false) が復元されること
        repo.saveSelectedSkyLayout(SkyLayout.TOUCH_STANDARD)
        assertEquals(SkyLayout.TOUCH_STANDARD, repo.selectedSkyLayout.value)
        assertFalse(repo.visualConfig.value.showGuideLabels)
        assertFalse(repo.getLayoutShowGuideLabels(SkyLayout.TOUCH_STANDARD))
    }

    @Test
    fun testSliderAdjustment_switchingLayout_preventsAccidentalOverwrite() = runBlocking {
        val repo = InMemorySettingsRepository()
        val standardPreset = com.onigiri.keycue.profile.SkyLayoutRegistry.getPreset(SkyLayout.TOUCH_STANDARD)
        repo.saveFitProfile(com.onigiri.keycue.model.FitProfile(standardPreset.keyCenters))

        repo.saveSelectedSkyLayout(SkyLayout.PAD_TRIGGER_FIRST)
        val pad1Adj = SkyLayoutAdjustment(offsetX = 0.02f, offsetY = 0.01f, scaleX = 1.0f, scaleY = 1.0f)
        repo.saveLayoutAdjustment(SkyLayout.PAD_TRIGGER_FIRST, pad1Adj)

        // 別レイアウト PAD_DPAD_FIRST に切り替え
        repo.saveSelectedSkyLayout(SkyLayout.PAD_DPAD_FIRST)
        val pad2Default = repo.getLayoutAdjustment(SkyLayout.PAD_DPAD_FIRST)
        assertEquals(SkyLayoutAdjustment.DEFAULT, pad2Default)

        // 切り替え前の古いスライダー操作イベントが遅れて PAD_TRIGGER_FIRST 宛てに届いた場合
        val delayedOldAdj = SkyLayoutAdjustment(offsetX = 0.04f, offsetY = 0.02f, scaleX = 1.0f, scaleY = 1.0f)
        repo.updateLayoutAdjustmentInMemory(SkyLayout.PAD_TRIGGER_FIRST, delayedOldAdj)
        repo.saveLayoutAdjustment(SkyLayout.PAD_TRIGGER_FIRST, delayedOldAdj)

        assertEquals(SkyLayoutAdjustment.DEFAULT, repo.getLayoutAdjustment(SkyLayout.PAD_DPAD_FIRST))
        assertEquals(delayedOldAdj, repo.getLayoutAdjustment(SkyLayout.PAD_TRIGGER_FIRST))
    }

    @Test
    fun testBaseFitProfileProtection_separationOfBaseAndDerivedData() = runBlocking {
        val repo = InMemorySettingsRepository()
        val standardPreset = com.onigiri.keycue.profile.SkyLayoutRegistry.getPreset(SkyLayout.TOUCH_STANDARD)
        val validBase = com.onigiri.keycue.model.FitProfile(standardPreset.keyCenters)

        repo.saveFitProfile(validBase)
        assertEquals(validBase, repo.fitProfile.value)

        try {
            val invalidProfile = com.onigiri.keycue.model.FitProfile(standardPreset.keyCenters.take(14))
            repo.saveFitProfile(invalidProfile)
            org.junit.Assert.fail("不正な要素数のFitProfile保存は例外をスローする必要があります")
        } catch (_: IllegalArgumentException) {
            // 期待通りの挙動
        }
        assertEquals(validBase, repo.fitProfile.value)
    }

    @Test
    fun testInMemorySettingsRepository_layoutShowGuideLabels() = runBlocking {
        val repo = InMemorySettingsRepository(
            initialVisualConfig = com.onigiri.keycue.model.VisualConfig(showGuideLabels = true)
        )

        // PAD系はデフォルトOFF -> ONに変更して保存
        assertFalse(repo.getLayoutShowGuideLabels(SkyLayout.PAD_TRIGGER_FIRST))
        repo.saveLayoutShowGuideLabels(SkyLayout.PAD_TRIGGER_FIRST, true)
        assertTrue(repo.getLayoutShowGuideLabels(SkyLayout.PAD_TRIGGER_FIRST))

        // TOUCH系はデフォルトON -> OFFに変更して保存
        assertTrue(repo.getLayoutShowGuideLabels(SkyLayout.TOUCH_STANDARD))
        repo.saveLayoutShowGuideLabels(SkyLayout.TOUCH_STANDARD, false)
        assertFalse(repo.getLayoutShowGuideLabels(SkyLayout.TOUCH_STANDARD))

        // 他のレイアウトに干渉していないこと
        assertFalse(repo.getLayoutShowGuideLabels(SkyLayout.PAD_DPAD_FIRST))
        assertTrue(repo.getLayoutShowGuideLabels(SkyLayout.TOUCH_EXPANDED))
    }

    @Test
    fun testSharedPreferencesSettingsRepository_layoutPersistence() = runBlocking {
        val map = HashMap<String, Any>()
        val prefs = createFakePrefs(map)
        val repo = SharedPreferencesSettingsRepository(prefs)

        assertEquals(SkyLayout.TOUCH_STANDARD, repo.selectedSkyLayout.value)
        assertFalse(repo.getLayoutShowGuideLabels(SkyLayout.PAD_GRID_STANDARD))

        // visualConfigでshowGuideLabelsをtrueにした場合、TOUCH系は引き継ぎ、PAD系は初期値falseのまま
        repo.saveVisualConfig(repo.visualConfig.value.copy(showGuideLabels = true))
        assertTrue(repo.getLayoutShowGuideLabels(SkyLayout.TOUCH_STANDARD))
        assertFalse(repo.getLayoutShowGuideLabels(SkyLayout.PAD_GRID_STANDARD))

        // レイアウト変更保存
        repo.saveSelectedSkyLayout(SkyLayout.PAD_GRID_STANDARD)
        assertEquals(SkyLayout.PAD_GRID_STANDARD, repo.selectedSkyLayout.value)
        assertEquals("PAD_GRID_STANDARD", map["selected_sky_layout"])

        // 微調整保存
        val adj = SkyLayoutAdjustment(offsetX = 0.01f, offsetY = -0.02f, scaleX = 1.02f, scaleY = 0.98f)
        repo.saveLayoutAdjustment(SkyLayout.PAD_GRID_STANDARD, adj)
        assertEquals(adj, repo.getLayoutAdjustment(SkyLayout.PAD_GRID_STANDARD))

        // ラベル表示変更保存
        repo.saveLayoutShowGuideLabels(SkyLayout.PAD_GRID_STANDARD, true)
        assertTrue(repo.getLayoutShowGuideLabels(SkyLayout.PAD_GRID_STANDARD))

        // 新しいリポジトリインスタンスを同Prefsで作成（永続化の確認）
        val repo2 = SharedPreferencesSettingsRepository(prefs)
        assertEquals(SkyLayout.PAD_GRID_STANDARD, repo2.selectedSkyLayout.value)
        assertEquals(adj, repo2.getLayoutAdjustment(SkyLayout.PAD_GRID_STANDARD))
        assertTrue(repo2.getLayoutShowGuideLabels(SkyLayout.PAD_GRID_STANDARD))
        assertFalse(repo2.getLayoutShowGuideLabels(SkyLayout.PAD_TRIGGER_FIRST))
    }

    /**
     * 要件: 旧設定（PAD_GRID, pad3等）から PAD_GRID_EXPANDED への安全なマイグレーション検証。
     * PAD_TRIGGER_FIRST, PAD_DPAD_FIRST はID不変で設定がそのまま維持されることの検証。
     */
    @Test
    fun testSharedPreferencesSettingsRepository_legacyMigration() {
        val map = HashMap<String, Any>()
        // 旧PAD_GRID選択
        map["selected_sky_layout"] = "PAD_GRID"
        // 旧PAD_GRIDラベルキー
        map["sky_layout_labels_pad_grid"] = true
        // 旧PAD_GRID微調整キー
        map["sky_layout_adj_pad_grid_offset_x"] = 0.03f
        map["sky_layout_adj_pad_grid_offset_y"] = -0.02f
        map["sky_layout_adj_pad_grid_scale_x"] = 1.05f
        map["sky_layout_adj_pad_grid_scale_y"] = 0.95f

        // PAD_TRIGGER_FIRST の既存設定（内部ID不変）
        map["sky_layout_labels_pad_trigger_first"] = true
        map["sky_layout_adj_pad_trigger_first_offset_x"] = 0.01f

        // 旧全体設定
        map["show_key_numbers"] = true

        val prefs = createFakePrefs(map)
        val repo = SharedPreferencesSettingsRepository(prefs)

        // 1. selected_sky_layout は PAD_GRID から PAD_GRID_EXPANDED にマイグレーションされること
        assertEquals(SkyLayout.PAD_GRID_EXPANDED, repo.selectedSkyLayout.value)
        assertEquals("PAD_GRID_EXPANDED", map["selected_sky_layout"])

        // 2. 旧PAD_GRIDのラベル設定が PAD_GRID_EXPANDED に引き継がれること
        assertTrue("旧ラベル設定 true が PAD_GRID_EXPANDED に引き継がれること", repo.getLayoutShowGuideLabels(SkyLayout.PAD_GRID_EXPANDED))
        assertEquals(true, map["sky_layout_labels_pad_grid_expanded"])

        // 3. 旧PAD_GRIDの微調整設定が PAD_GRID_EXPANDED に引き継がれること
        val adj = repo.getLayoutAdjustment(SkyLayout.PAD_GRID_EXPANDED)
        assertEquals(0.03f, adj.offsetX, 0.001f)
        assertEquals(-0.02f, adj.offsetY, 0.001f)
        assertEquals(1.05f, adj.scaleX, 0.001f)
        assertEquals(0.95f, adj.scaleY, 0.001f)
        assertEquals(0.03f, map["sky_layout_adj_pad_grid_expanded_offset_x"] as Float, 0.001f)

        // 4. PAD_TRIGGER_FIRST の既存設定は保持されていること
        assertTrue("PAD_TRIGGER_FIRSTのラベル設定が保持されていること", repo.getLayoutShowGuideLabels(SkyLayout.PAD_TRIGGER_FIRST))
        assertEquals(0.01f, repo.getLayoutAdjustment(SkyLayout.PAD_TRIGGER_FIRST).offsetX, 0.001f)

        // 5. visualConfig.showGuideLabels も選択中レイアウト (PAD_GRID_EXPANDED: true) と整合していること
        assertTrue(repo.visualConfig.value.showGuideLabels)
    }

    /**
     * 要件: 6レイアウト高速連続切り替え時のラベル整合性テスト。
     */
    @Test
    fun testRapidLayoutSwitching_all6Layouts_maintainsLabelSettingsIntegrity() = runBlocking {
        val inMemoryRepo = InMemorySettingsRepository(
            initialVisualConfig = com.onigiri.keycue.model.VisualConfig(showGuideLabels = true)
        )

        // PAD_TRIGGER_FIRST を ON に設定
        inMemoryRepo.saveSelectedSkyLayout(SkyLayout.PAD_TRIGGER_FIRST)
        inMemoryRepo.saveShowGuideLabels(true)
        assertTrue(inMemoryRepo.visualConfig.value.showGuideLabels)

        // PAD_GRID_EXPANDED を ON に設定
        inMemoryRepo.saveSelectedSkyLayout(SkyLayout.PAD_GRID_EXPANDED)
        inMemoryRepo.saveShowGuideLabels(true)
        assertTrue(inMemoryRepo.visualConfig.value.showGuideLabels)

        // TOUCH_STANDARD を OFF に設定
        inMemoryRepo.saveSelectedSkyLayout(SkyLayout.TOUCH_STANDARD)
        inMemoryRepo.saveShowGuideLabels(false)
        assertFalse(inMemoryRepo.visualConfig.value.showGuideLabels)

        // 6レイアウトの期待値マップ
        val expectedLabels = mapOf(
            SkyLayout.TOUCH_STANDARD to false,
            SkyLayout.TOUCH_EXPANDED to true,
            SkyLayout.PAD_TRIGGER_FIRST to true,
            SkyLayout.PAD_DPAD_FIRST to false,
            SkyLayout.PAD_GRID_STANDARD to false,
            SkyLayout.PAD_GRID_EXPANDED to true
        )

        // 全6レイアウトを順次切り替えて検証
        for ((targetLayout, expected) in expectedLabels) {
            inMemoryRepo.saveSelectedSkyLayout(targetLayout)
            assertEquals(targetLayout, inMemoryRepo.selectedSkyLayout.value)
            assertEquals(
                "レイアウト $targetLayout の visualConfig.showGuideLabels 整合性",
                expected,
                inMemoryRepo.visualConfig.value.showGuideLabels
            )
            assertEquals(
                "レイアウト $targetLayout の getLayoutShowGuideLabels 整合性",
                expected,
                inMemoryRepo.getLayoutShowGuideLabels(targetLayout)
            )
        }
    }

    private fun createFakePrefs(map: HashMap<String, Any>): android.content.SharedPreferences {
        return Proxy.newProxyInstance(
            android.content.SharedPreferences::class.java.classLoader,
            arrayOf(android.content.SharedPreferences::class.java)
        ) { _, method, args ->
            when (method.name) {
                "getString" -> {
                    val key = args[0] as String
                    val def = if (args.size > 1) args[1] as? String else null
                    (map[key] as? String) ?: def
                }
                "getFloat" -> {
                    val key = args[0] as String
                    val def = args[1] as Float
                    (map[key] as? Float) ?: def
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
                "getLong" -> {
                    val key = args[0] as String
                    val def = args[1] as Long
                    (map[key] as? Long) ?: def
                }
                "contains" -> map.containsKey(args[0] as String)
                "edit" -> createFakeEditor(map)
                "registerOnSharedPreferenceChangeListener", "unregisterOnSharedPreferenceChangeListener" -> null
                else -> null
            }
        } as android.content.SharedPreferences
    }

    private fun createFakeEditor(map: HashMap<String, Any>): android.content.SharedPreferences.Editor {
        val tempMap = HashMap<String, Any>()
        val removedKeys = HashSet<String>()

        return Proxy.newProxyInstance(
            android.content.SharedPreferences.Editor::class.java.classLoader,
            arrayOf(android.content.SharedPreferences.Editor::class.java)
        ) { proxy, method, args ->
            when (method.name) {
                "putString" -> {
                    tempMap[args[0] as String] = args[1]
                    proxy
                }
                "putFloat" -> {
                    tempMap[args[0] as String] = args[1]
                    proxy
                }
                "putBoolean" -> {
                    tempMap[args[0] as String] = args[1]
                    proxy
                }
                "putInt" -> {
                    tempMap[args[0] as String] = args[1]
                    proxy
                }
                "putLong" -> {
                    tempMap[args[0] as String] = args[1]
                    proxy
                }
                "remove" -> {
                    removedKeys.add(args[0] as String)
                    proxy
                }
                "apply", "commit" -> {
                    for (k in removedKeys) map.remove(k)
                    map.putAll(tempMap)
                    true
                }
                else -> proxy
            }
        } as android.content.SharedPreferences.Editor
    }
}
