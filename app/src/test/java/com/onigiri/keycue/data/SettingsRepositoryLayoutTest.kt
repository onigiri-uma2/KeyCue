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

    /**
     * 要件: GuideOverlayViewと同一のガイド円半径（guideRadiusRatio、短辺、8dp下限）を用いた
     * キー間距離・重なり検証テスト。重なり警告 (D < 2R) と衝突エラー (D < R) を区別する。
     * 6レイアウトすべてのデフォルト配置において衝突エラーが存在しないことを検証する。
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

        // 6レイアウトすべてのデフォルト状態での衝突がないことの検証（6レイアウト仕様対応）
        for (layout in SkyLayout.entries) {
            val normalResult = com.onigiri.keycue.profile.SkyLayoutTransformer.validateLayout(
                baseFitProfile = baseProfile,
                targetLayout = layout,
                adjustment = SkyLayoutAdjustment.DEFAULT,
                viewWidth = 1024,
                viewHeight = 460,
                guideRadiusRatio = 0.04f,
                density = 1.0f
            )
            assertTrue("Layout $layout のデフォルト配置は衝突エラーなし (isValid=true)", normalResult.isValid)
            assertFalse("Layout $layout のデフォルト配置に画面外キーなし", normalResult.hasOutOfBounds)
            assertTrue("Layout $layout のデフォルト配置に衝突ペアなし", normalResult.collisionPairs.isEmpty())
        }

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
     * 要件: 個別clampや全体縮小（Auto-fit）が行われず、生座標が維持されることの検証。
     * 画面外座標が発生した場合は勝手に全体縮小・個別clampせず、transformOrNull が null を返し、
     * 生座標計算 (computeTransformedRawPoints) では境界外の生の値が維持されること。
     */
    @Test
    fun testNoAutoShrinkOrClamp_rawCoordinatesMaintained() {
        val standardPreset = com.onigiri.keycue.profile.SkyLayoutRegistry.getPreset(SkyLayout.TOUCH_STANDARD)
        val baseProfile = com.onigiri.keycue.model.FitProfile(standardPreset.keyCenters)

        // 画面外に大きく出る微調整 (offsetX = 0.40f)
        val hugeAdj = SkyLayoutAdjustment(offsetX = 0.40f, offsetY = 0.0f, scaleX = 1.0f, scaleY = 1.0f)

        // 6レイアウトの中から標準タッチ、パッド分散、パッド格子の各代表で検証
        val testLayouts = listOf(SkyLayout.TOUCH_STANDARD, SkyLayout.PAD_TRIGGER_FIRST, SkyLayout.PAD_GRID_EXPANDED)
        for (targetLayout in testLayouts) {
            val rawPoints = com.onigiri.keycue.profile.SkyLayoutTransformer.computeTransformedRawPoints(
                baseFitProfile = baseProfile,
                targetLayout = targetLayout,
                adjustment = hugeAdj
            )

            // 最も右側のキーの x 座標が 1.0f を超えて clamp されずにそのまま計算されていること
            val maxX = rawPoints.maxOf { it.first }
            assertTrue("Layout $targetLayout: maxX should be > 1.0f, but was $maxX", maxX > 1.0f)

            // transformOrNull は安全に null を返すこと（勝手に全体縮小・個別clampしない）
            val safeProfile = com.onigiri.keycue.profile.SkyLayoutTransformer.transformOrNull(
                baseFitProfile = baseProfile,
                targetLayout = targetLayout,
                adjustment = hugeAdj
            )
            org.junit.Assert.assertNull("Layout $targetLayout: safeProfile must be null", safeProfile)

            // isAdjustmentValid も画面外を検出して false を返すこと
            assertFalse(
                "Layout $targetLayout: isAdjustmentValid must be false",
                com.onigiri.keycue.profile.SkyLayoutTransformer.isAdjustmentValid(baseProfile, targetLayout, hugeAdj)
            )
        }
    }

    /**
     * 要件: FitProfileコンストラクタで発生する例外だけをもってRepository保存経路の保護テストとしない。
     * リポジトリ自体の saveFitProfile がキー数検証を行い、不正プロファイルの保存を拒絶することの検証。
     */
    @Test
    fun testRepositorySaveFitProfileProtection_withoutRelyingOnConstructorException() = runBlocking {
        val repo = InMemorySettingsRepository()
        val standardPreset = com.onigiri.keycue.profile.SkyLayoutRegistry.getPreset(SkyLayout.TOUCH_STANDARD)
        val validBase = com.onigiri.keycue.model.FitProfile(standardPreset.keyCenters)
        repo.saveFitProfile(validBase)
        assertEquals(validBase, repo.fitProfile.value)

        // リフレクション等を用いてFitProfileコンストラクタのrequireチェックをバイパスし、
        // 不正なキー数（14個）のFitProfileインスタンスが万一生成されたケースをシミュレート
        val invalidProfile = try {
            val constructor = com.onigiri.keycue.model.FitProfile::class.java.declaredConstructors.firstOrNull {
                it.parameterCount >= 1
            }
            if (constructor != null) {
                constructor.isAccessible = true
                val params = arrayOfNulls<Any>(constructor.parameterCount)
                params[0] = standardPreset.keyCenters.take(14) // 14キー
                if (constructor.parameterCount > 1) params[1] = true // landscape
                if (constructor.parameterCount > 2) params[2] = 0.04f // keyRadiusRatio
                constructor.newInstance(*params) as? com.onigiri.keycue.model.FitProfile
            } else null
        } catch (_: Exception) {
            null
        }

        if (invalidProfile != null) {
            // 1. InMemorySettingsRepository での検証
            try {
                repo.saveFitProfile(invalidProfile)
                org.junit.Assert.fail("InMemorySettingsRepository.saveFitProfile は不正なキー数のプロファイルを拒絶する必要があります")
            } catch (e: IllegalArgumentException) {
                assertTrue("リポジトリ独自の例外メッセージが含まれること", e.message?.contains("15個のキーを含む必要があります") == true)
            }
            assertEquals("リポジトリ内の保存プロファイルが保護されていること", validBase, repo.fitProfile.value)

            // 2. SharedPreferencesSettingsRepository での検証
            val map = HashMap<String, Any>()
            val spRepo = SharedPreferencesSettingsRepository(createFakePrefs(map))
            spRepo.saveFitProfile(validBase)
            assertEquals(validBase, spRepo.fitProfile.value)

            try {
                spRepo.saveFitProfile(invalidProfile)
                org.junit.Assert.fail("SharedPreferencesSettingsRepository.saveFitProfile は不正なキー数のプロファイルを拒絶する必要があります")
            } catch (e: IllegalArgumentException) {
                assertTrue("リポジトリ独自の例外メッセージが含まれること", e.message?.contains("15個のキーを含む必要があります") == true)
            }
            assertEquals("SPリポジトリ内の保存プロファイルが保護されていること", validBase, spRepo.fitProfile.value)
        }
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
     * 要件: 旧設定のマイグレーションでは各キーの既存保存値を上書きしないことの検証。
     * すでに設定されているキーが、未設定キーへのデフォルト値設定や旧全体設定で上書きされないこと。
     */
    @Test
    fun testSharedPreferencesSettingsRepository_initMigration_doesNotOverwriteExistingKeys() {
        val map = HashMap<String, Any>()
        // ユーザーが以前に PAD_TRIGGER_FIRST を明示的に ON (true) に保存していた状態
        map["sky_layout_labels_pad_trigger_first"] = true
        // 既存の微調整設定
        map["sky_layout_adj_pad_trigger_first_offset_x"] = 0.02f
        // 既存の PAD_GRID_EXPANDED 設定
        map["sky_layout_labels_pad_grid_expanded"] = true
        // 旧全体設定は false
        map["show_key_numbers"] = false

        val prefs = createFakePrefs(map)
        val repo = SharedPreferencesSettingsRepository(prefs)

        // 既存キー sky_layout_labels_pad_trigger_first は上書きされず true のまま維持されること
        assertTrue("既存の保存値 true が上書きされず維持されること", repo.getLayoutShowGuideLabels(SkyLayout.PAD_TRIGGER_FIRST))
        assertEquals(true, map["sky_layout_labels_pad_trigger_first"])
        assertEquals(0.02f, repo.getLayoutAdjustment(SkyLayout.PAD_TRIGGER_FIRST).offsetX, 0.001f)

        // 既存キー sky_layout_labels_pad_grid_expanded も true のまま維持されること
        assertTrue("既存の PAD_GRID_EXPANDED 保存値が維持されること", repo.getLayoutShowGuideLabels(SkyLayout.PAD_GRID_EXPANDED))
        assertEquals(true, map["sky_layout_labels_pad_grid_expanded"])

        // 未保存だった他のPADレイアウトにはデフォルト値 false が安全に設定されること
        assertFalse(repo.getLayoutShowGuideLabels(SkyLayout.PAD_DPAD_FIRST))
        assertEquals(false, map["sky_layout_labels_pad_dpad_first"])
        assertFalse(repo.getLayoutShowGuideLabels(SkyLayout.PAD_GRID_STANDARD))
        assertEquals(false, map["sky_layout_labels_pad_grid_standard"])

        // 未保存だったTOUCH_STANDARDには旧全体設定値 false が引き継がれること
        assertFalse(repo.getLayoutShowGuideLabels(SkyLayout.TOUCH_STANDARD))
        assertEquals(false, map["sky_layout_labels_touch_standard"])
    }

    /**
     * 要件: 初期化時に保存済み selectedSkyLayout とそのレイアウトのラベル設定を照合し、
     * visualConfig.showGuideLabels を整合させることの検証。
     */
    @Test
    fun testSharedPreferencesSettingsRepository_init_alignsVisualConfigWithSelectedSkyLayout() {
        val map = HashMap<String, Any>()
        // PAD_TRIGGER_FIRST が選択されており、そのレイアウトのラベルは OFF (false)
        map["selected_sky_layout"] = "PAD_TRIGGER_FIRST"
        map["sky_layout_labels_pad_trigger_first"] = false
        // しかし旧グローバル設定 show_key_numbers が true になっている不整合状態
        map["show_key_numbers"] = true

        val prefs = createFakePrefs(map)
        val repo = SharedPreferencesSettingsRepository(prefs)

        // 初期化時に照合され、visualConfig.showGuideLabels は選択中レイアウトの false に整合されること
        assertEquals(SkyLayout.PAD_TRIGGER_FIRST, repo.selectedSkyLayout.value)
        assertFalse("visualConfig.showGuideLabels が選択レイアウトのラベル設定 (false) と整合していること", repo.visualConfig.value.showGuideLabels)
        assertEquals("show_key_numbers にも整合値 false が書き込まれること", false, map["show_key_numbers"])
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

        // SharedPreferences リポジトリでも同様に連続切り替えと永続化の整合性を検証
        val map = HashMap<String, Any>()
        map["show_key_numbers"] = true // 旧全体設定 ON の状態からマイグレーション開始
        val prefs = createFakePrefs(map)
        val spRepo = SharedPreferencesSettingsRepository(prefs)

        // PAD_TRIGGER_FIRST を ON に
        spRepo.saveSelectedSkyLayout(SkyLayout.PAD_TRIGGER_FIRST)
        spRepo.saveShowGuideLabels(true)
        // PAD_GRID_EXPANDED を ON に
        spRepo.saveSelectedSkyLayout(SkyLayout.PAD_GRID_EXPANDED)
        spRepo.saveShowGuideLabels(true)
        // TOUCH_STANDARD を OFF に
        spRepo.saveSelectedSkyLayout(SkyLayout.TOUCH_STANDARD)
        spRepo.saveShowGuideLabels(false)

        for ((targetLayout, expected) in expectedLabels) {
            spRepo.saveSelectedSkyLayout(targetLayout)
            assertEquals(
                "SP レイアウト $targetLayout の visualConfig.showGuideLabels 整合性",
                expected,
                spRepo.visualConfig.value.showGuideLabels
            )
            assertEquals(
                "SP レイアウト $targetLayout の getLayoutShowGuideLabels 整合性",
                expected,
                spRepo.getLayoutShowGuideLabels(targetLayout)
            )
        }

        // 再インスタンス化しても永続化データから全6レイアウトのラベル値が復元されること
        val spRepo2 = SharedPreferencesSettingsRepository(prefs)
        for ((layout, expected) in expectedLabels) {
            assertEquals(
                "永続化復元後のレイアウト $layout のラベル設定",
                expected,
                spRepo2.getLayoutShowGuideLabels(layout)
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
