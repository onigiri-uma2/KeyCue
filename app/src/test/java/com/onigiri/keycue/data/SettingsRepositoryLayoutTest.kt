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

        // PAD系は初期値OFF (false)
        assertFalse(repo.getLayoutShowGuideLabels(SkyLayout.PAD_TRIGGER_FIRST))
        assertFalse(repo.getLayoutShowGuideLabels(SkyLayout.PAD_DPAD_FIRST))
        assertFalse(repo.getLayoutShowGuideLabels(SkyLayout.PAD_GRID))
    }

    @Test
    fun testInMemorySettingsRepository_saveSelectedSkyLayout() = runBlocking {
        val repo = InMemorySettingsRepository()

        repo.saveSelectedSkyLayout(SkyLayout.PAD_TRIGGER_FIRST)
        assertEquals(SkyLayout.PAD_TRIGGER_FIRST, repo.selectedSkyLayout.value)

        repo.saveSelectedSkyLayout(SkyLayout.PAD_DPAD_FIRST)
        assertEquals(SkyLayout.PAD_DPAD_FIRST, repo.selectedSkyLayout.value)
    }

    @Test
    fun testInMemorySettingsRepository_layoutAdjustmentIndependence() = runBlocking {
        val repo = InMemorySettingsRepository()
        // 基準プロファイルを TOUCH_STANDARD の実寸プリセットで設定
        val standardPreset = com.onigiri.keycue.profile.SkyLayoutRegistry.getPreset(SkyLayout.TOUCH_STANDARD)
        repo.saveFitProfile(com.onigiri.keycue.model.FitProfile(standardPreset.keyCenters))

        val adjPad1 = SkyLayoutAdjustment(offsetX = 0.02f, offsetY = -0.01f, scaleX = 1.05f, scaleY = 0.95f)
        val adjPad2 = SkyLayoutAdjustment(offsetX = -0.03f, offsetY = 0.04f, scaleX = 0.90f, scaleY = 1.10f)

        val res1 = repo.saveLayoutAdjustment(SkyLayout.PAD_TRIGGER_FIRST, adjPad1)
        val res2 = repo.saveLayoutAdjustment(SkyLayout.PAD_DPAD_FIRST, adjPad2)

        assertTrue(res1)
        assertTrue(res2)
        assertEquals(adjPad1, repo.getLayoutAdjustment(SkyLayout.PAD_TRIGGER_FIRST))
        assertEquals(adjPad2, repo.getLayoutAdjustment(SkyLayout.PAD_DPAD_FIRST))
        assertEquals(SkyLayoutAdjustment.DEFAULT, repo.getLayoutAdjustment(SkyLayout.TOUCH_STANDARD))
    }

    /**
     * 要件1: 画面外座標の個別clamp廃止 & 直前の有効な微調整値の維持テスト。
     * 微調整によって画面外に出る場合は更新・保存を採用せず、直前の有効値を維持する。
     */
    @Test
    fun testLayoutAdjustment_outOfBounds_retainsPreviousValidAdjustment() = runBlocking {
        val repo = InMemorySettingsRepository()
        val standardPreset = com.onigiri.keycue.profile.SkyLayoutRegistry.getPreset(SkyLayout.TOUCH_STANDARD)
        repo.saveFitProfile(com.onigiri.keycue.model.FitProfile(standardPreset.keyCenters))

        // 有効な微調整値を設定
        val validAdj = SkyLayoutAdjustment(offsetX = 0.01f, offsetY = 0.01f, scaleX = 1.0f, scaleY = 1.0f)
        assertTrue(repo.saveLayoutAdjustment(SkyLayout.PAD_TRIGGER_FIRST, validAdj))
        assertEquals(validAdj, repo.getLayoutAdjustment(SkyLayout.PAD_TRIGGER_FIRST))

        // 画面外に大きく飛び出す無効な微調整値 (offsetX = 0.50f)
        val invalidAdj = SkyLayoutAdjustment(offsetX = 0.50f, offsetY = 0.00f, scaleX = 1.0f, scaleY = 1.0f)

        // メモリ更新も永続化保存も拒絶され、false を返すこと
        assertFalse(repo.updateLayoutAdjustmentInMemory(SkyLayout.PAD_TRIGGER_FIRST, invalidAdj))
        assertFalse(repo.saveLayoutAdjustment(SkyLayout.PAD_TRIGGER_FIRST, invalidAdj))

        // 直前の有効な微調整値が維持されていること
        assertEquals(validAdj, repo.getLayoutAdjustment(SkyLayout.PAD_TRIGGER_FIRST))
    }

    /**
     * 要件2: ラベル設定の全更新経路 (saveShowGuideLabels, saveVisualConfig, updateVisualConfig) の
     * 同期および直列化テスト。
     */
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
        // 切り替え先の保存値（false）が visualConfig に即時反映されていること
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

    /**
     * 要件: スライダー操作中に別レイアウトへ切り替わった場合、古い操作結果が
     * 別レイアウトへ誤保存・誤適用されないことの検証。
     */
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

        // 現在選択中の PAD_DPAD_FIRST の微調整値は一切影響を受けていないこと
        assertEquals(SkyLayoutAdjustment.DEFAULT, repo.getLayoutAdjustment(SkyLayout.PAD_DPAD_FIRST))
        // PAD_TRIGGER_FIRST のみが更新されていること
        assertEquals(delayedOldAdj, repo.getLayoutAdjustment(SkyLayout.PAD_TRIGGER_FIRST))
    }

    /**
     * 要件3: 基準FitProfileと派生データの構造的分離および保護テスト。
     */
    @Test
    fun testBaseFitProfileProtection_separationOfBaseAndDerivedData() = runBlocking {
        val repo = InMemorySettingsRepository()
        val standardPreset = com.onigiri.keycue.profile.SkyLayoutRegistry.getPreset(SkyLayout.TOUCH_STANDARD)
        val validBase = com.onigiri.keycue.model.FitProfile(standardPreset.keyCenters)

        // 正常な15キー基準プロファイルは保存可能
        repo.saveFitProfile(validBase)
        assertEquals(validBase, repo.fitProfile.value)

        // 要素数が15でない不正プロファイルは saveFitProfile で例外が発生してブロックされる
        try {
            val invalidProfile = com.onigiri.keycue.model.FitProfile(standardPreset.keyCenters.take(14))
            repo.saveFitProfile(invalidProfile)
            org.junit.Assert.fail("不正な要素数のFitProfile保存は例外をスローする必要があります")
        } catch (_: IllegalArgumentException) {
            // 期待通りの挙動
        }
        // 基準プロファイルが保護されていること
        assertEquals(validBase, repo.fitProfile.value)
    }

    /**
     * 要件4: GuideOverlayViewと同一のガイド円半径（guideRadiusRatio、短辺、8dp下限）を用いた
     * キー間距離・重なり検証テスト。重なり警告 (D < 2R) と衝突エラー (D < R) を区別する。
     */
    @Test
    fun testKeyDistanceAndOverlapValidation_withExactGuideRadiusPx() {
        val standardPreset = com.onigiri.keycue.profile.SkyLayoutRegistry.getPreset(SkyLayout.TOUCH_STANDARD)
        val baseProfile = com.onigiri.keycue.model.FitProfile(standardPreset.keyCenters)

        // 1024x460 画面、density = 1.0f, guideRadiusRatio = 0.04f (短辺 460 * 0.04 = 18.4px >= 8dp)
        val radiusPx = com.onigiri.keycue.profile.SkyLayoutTransformer.calculateKeyRadiusPx(
            viewWidth = 1024,
            viewHeight = 460,
            guideRadiusRatio = 0.04f,
            density = 1.0f
        )
        assertEquals(18.4f, radiusPx, 0.01f)

        // 正常配置の検証
        val normalResult = com.onigiri.keycue.profile.SkyLayoutTransformer.validateLayout(
            baseFitProfile = baseProfile,
            targetLayout = SkyLayout.TOUCH_STANDARD,
            adjustment = SkyLayoutAdjustment.DEFAULT,
            viewWidth = 1024,
            viewHeight = 460,
            guideRadiusRatio = 0.04f,
            density = 1.0f
        )
        assertTrue(normalResult.isValid)
        assertFalse(normalResult.hasOutOfBounds)
        assertTrue(normalResult.collisionPairs.isEmpty())
        assertTrue(normalResult.overlapPairs.isEmpty())

        // 重なり (D < 2R) と 衝突 (D < R) のテスト
        val key0 = Pair(100.0f / 1024f, 100.0f / 460f)
        val keyOverlap = Pair((100.0f + radiusPx * 1.5f) / 1024f, 100.0f / 460f) // 距離 1.5R (重なり警告)
        val keyCollision = Pair((100.0f + radiusPx * 0.5f) / 1024f, 100.0f / 460f) // 距離 0.5R (衝突エラー)

        val overlapPoints = standardPreset.keyCenters.mapIndexed { idx, pt ->
            when (idx) {
                0 -> key0
                1 -> keyOverlap
                else -> Pair(pt.x, pt.y)
            }
        }
        val overlapResult = com.onigiri.keycue.profile.SkyLayoutTransformer.validateRawPoints(
            rawPoints = overlapPoints,
            viewWidth = 1024,
            viewHeight = 460,
            guideRadiusRatio = 0.04f,
            density = 1.0f
        )
        assertTrue(overlapResult.isValid) // 重なり警告のみでは致命的エラー (isValid = false) にはならない
        assertTrue(overlapResult.overlapPairs.contains(Pair(0, 1)))
        assertTrue(overlapResult.collisionPairs.isEmpty())

        val collisionPoints = standardPreset.keyCenters.mapIndexed { idx, pt ->
            when (idx) {
                0 -> key0
                1 -> keyCollision
                else -> Pair(pt.x, pt.y)
            }
        }
        val collisionResult = com.onigiri.keycue.profile.SkyLayoutTransformer.validateRawPoints(
            rawPoints = collisionPoints,
            viewWidth = 1024,
            viewHeight = 460,
            guideRadiusRatio = 0.04f,
            density = 1.0f
        )
        assertFalse(collisionResult.isValid) // 衝突エラーがあるため isValid = false
        assertTrue(collisionResult.collisionPairs.contains(Pair(0, 1)))
    }

    /**
     * 要件1補足: 個別clampや全体縮小が行われず、生座標が維持されることの検証。
     */
    @Test
    fun testNoAutoShrinkOrClamp_rawCoordinatesMaintained() {
        val standardPreset = com.onigiri.keycue.profile.SkyLayoutRegistry.getPreset(SkyLayout.TOUCH_STANDARD)
        val baseProfile = com.onigiri.keycue.model.FitProfile(standardPreset.keyCenters)

        // 画面外に大きく出る微調整 (offsetX = 0.40f)
        val hugeAdj = SkyLayoutAdjustment(offsetX = 0.40f, offsetY = 0.0f, scaleX = 1.0f, scaleY = 1.0f)
        val rawPoints = com.onigiri.keycue.profile.SkyLayoutTransformer.computeTransformedRawPoints(
            baseFitProfile = baseProfile,
            targetLayout = SkyLayout.TOUCH_STANDARD,
            adjustment = hugeAdj
        )

        // 右端のキー (Key 4) の x 座標が 1.0f を超えて clamp されずにそのまま計算されていること (0.685 + 0.40 = 1.085 > 1.0)
        val rightKeyX = rawPoints[4].first
        assertTrue("rightKeyX should be > 1.0f, but was $rightKeyX", rightKeyX > 1.0f)

        // transformOrNull は安全に null を返すこと（勝手に全体縮小・個別clampしない）
        val safeProfile = com.onigiri.keycue.profile.SkyLayoutTransformer.transformOrNull(
            baseFitProfile = baseProfile,
            targetLayout = SkyLayout.TOUCH_STANDARD,
            adjustment = hugeAdj
        )
        org.junit.Assert.assertNull(safeProfile)
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

        // 初期値
        assertEquals(SkyLayout.TOUCH_STANDARD, repo.selectedSkyLayout.value)
        assertFalse(repo.getLayoutShowGuideLabels(SkyLayout.PAD_TRIGGER_FIRST))

        // visualConfigでshowGuideLabelsをtrueにした場合、TOUCH系は引き継ぎ、PAD系は初期値falseのまま
        repo.saveVisualConfig(repo.visualConfig.value.copy(showGuideLabels = true))
        assertTrue(repo.getLayoutShowGuideLabels(SkyLayout.TOUCH_STANDARD))
        assertFalse(repo.getLayoutShowGuideLabels(SkyLayout.PAD_TRIGGER_FIRST))

        // レイアウト変更保存
        repo.saveSelectedSkyLayout(SkyLayout.PAD_GRID)
        assertEquals(SkyLayout.PAD_GRID, repo.selectedSkyLayout.value)
        assertEquals("PAD_GRID", map["selected_sky_layout"])

        // 微調整保存
        val adj = SkyLayoutAdjustment(offsetX = 0.01f, offsetY = -0.02f, scaleX = 1.02f, scaleY = 0.98f)
        repo.saveLayoutAdjustment(SkyLayout.PAD_GRID, adj)
        assertEquals(adj, repo.getLayoutAdjustment(SkyLayout.PAD_GRID))

        // ラベル表示変更保存
        repo.saveLayoutShowGuideLabels(SkyLayout.PAD_GRID, true)
        assertTrue(repo.getLayoutShowGuideLabels(SkyLayout.PAD_GRID))

        // 新しいリポジトリインスタンスを同Prefsで作成（永続化の確認）
        val repo2 = SharedPreferencesSettingsRepository(prefs)
        assertEquals(SkyLayout.PAD_GRID, repo2.selectedSkyLayout.value)
        assertEquals(adj, repo2.getLayoutAdjustment(SkyLayout.PAD_GRID))
        assertTrue(repo2.getLayoutShowGuideLabels(SkyLayout.PAD_GRID))
        assertFalse(repo2.getLayoutShowGuideLabels(SkyLayout.PAD_TRIGGER_FIRST))
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
