package com.onigiri.keycue.ui.fitting

import com.onigiri.keycue.data.InMemorySettingsRepository
import com.onigiri.keycue.fitting.DetectedPoint
import com.onigiri.keycue.fitting.GridFitter
import com.onigiri.keycue.fitting.KeyDetector
import com.onigiri.keycue.model.FitProfile
import com.onigiri.keycue.model.FitProfileSerializer
import com.onigiri.keycue.model.NormalizedPoint
import com.onigiri.keycue.profile.SkyProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FittingViewModelTest {

    private lateinit var settingsRepo: InMemorySettingsRepository
    private lateinit var fakeDetector: KeyDetector
    private lateinit var gridFitter: GridFitter
    private lateinit var viewModel: FittingViewModel
    private val testScope = CoroutineScope(Dispatchers.Unconfined)

    @Before
    fun setup() {
        settingsRepo = InMemorySettingsRepository()
        fakeDetector = object : KeyDetector {
            override fun detect(image: android.graphics.Bitmap): List<DetectedPoint> = emptyList()
        }
        gridFitter = GridFitter()
        viewModel = FittingViewModel(
            settingsRepository = settingsRepo,
            keyDetector = fakeDetector,
            gridFitter = gridFitter,
            externalScope = testScope
        )
    }

    @Test
    fun `initial state is Idle`() {
        val state = viewModel.uiState.value
        assertEquals(FittingStep.Idle, state.step)
        assertEquals(false, state.isSavedSuccess)
    }

    @Test
    fun `manual adjust mode updates corners and recalculates points with bilinear interpolation`() {
        viewModel.startManualAdjust()

        val state1 = viewModel.uiState.value
        assertEquals(FittingStep.ManualAdjust, state1.step)
        assertNotNull(state1.currentProfile)
        assertEquals(15, state1.currentProfile!!.keyCenters.size)

        // 左上ハンドル (cornerIndex 0) をドラッグ移動
        viewModel.updateManualCorner(cornerIndex = 0, normX = 0.15f, normY = 0.55f)

        val state2 = viewModel.uiState.value
        assertEquals(0.15f, state2.manualTopLeft.x, 0.001f)
        assertEquals(0.55f, state2.manualTopLeft.y, 0.001f)
        // Key 0 (左上) が更新されていること
        assertEquals(0.15f, state2.currentProfile!!.keyCenters[0].x, 0.001f)
        assertEquals(0.55f, state2.currentProfile!!.keyCenters[0].y, 0.001f)

        // 右下ハンドル (cornerIndex 3) をドラッグ移動
        viewModel.updateManualCorner(cornerIndex = 3, normX = 0.85f, normY = 0.95f)
        val state3 = viewModel.uiState.value
        assertEquals(0.85f, state3.manualBottomRight.x, 0.001f)
        assertEquals(0.95f, state3.manualBottomRight.y, 0.001f)
        assertEquals(0.85f, state3.currentProfile!!.keyCenters[14].x, 0.001f)
        assertEquals(0.95f, state3.currentProfile!!.keyCenters[14].y, 0.001f)

        // 微調整を確定
        viewModel.finishManualAdjust()
        val state4 = viewModel.uiState.value
        assertTrue(state4.step is FittingStep.Success)
    }

    @Test
    fun `saveCurrentProfile persists profile to SettingsRepository`() = runBlocking {
        viewModel.startManualAdjust()
        viewModel.saveCurrentProfile()

        assertTrue(viewModel.uiState.value.isSavedSuccess)
        assertNotNull(settingsRepo.fitProfile.value)
        assertEquals(15, settingsRepo.fitProfile.value!!.keyCenters.size)
    }

    @Test
    fun `useSavedProfile applies existing profile`() = runBlocking {
        val testProfile = FitProfile.createDefaultTestProfile(landscape = true)
        settingsRepo.saveFitProfile(testProfile)

        assertEquals(testProfile, viewModel.uiState.value.savedProfile)

        viewModel.useSavedProfile()
        val state = viewModel.uiState.value
        assertEquals(testProfile, state.currentProfile)
        assertTrue(state.step is FittingStep.Success)
    }

    @Test
    fun `toggleDebugView switches flag`() {
        assertEquals(false, viewModel.uiState.value.showDebugView)
        viewModel.toggleDebugView()
        assertEquals(true, viewModel.uiState.value.showDebugView)
        viewModel.toggleDebugView()
        assertEquals(false, viewModel.uiState.value.showDebugView)
    }

    @Test
    fun `custom user adjusted profile from legacy json persists through repository and ViewModel`() = runBlocking {
        // ユーザーが手動調整した意図的にズラした15座標＋半径比率の旧JSONデータ
        // Key 0: (0.17, 0.61) ... Key 14: (0.84, 0.89), keyRadiusRatio: 0.053f
        val customAdjustedJson = """
            {"landscape":true,"keyRadiusRatio":0.053,"keyCenters":[{"x":0.17,"y":0.61},{"x":0.33,"y":0.62},{"x":0.49,"y":0.61},{"x":0.66,"y":0.63},{"x":0.82,"y":0.62},{"x":0.18,"y":0.74},{"x":0.34,"y":0.75},{"x":0.51,"y":0.74},{"x":0.67,"y":0.76},{"x":0.83,"y":0.75},{"x":0.19,"y":0.88},{"x":0.35,"y":0.89},{"x":0.52,"y":0.88},{"x":0.68,"y":0.90},{"x":0.84,"y":0.89}]}
        """.trimIndent()

        // 1. 旧JSONのデシリアライズ
        val restoredProfile = FitProfileSerializer.fromJson(customAdjustedJson)
        assertNotNull(restoredProfile)
        assertEquals(SkyProfile.keyCount, restoredProfile!!.keyCenters.size)
        assertEquals(0.053f, restoredProfile.keyRadiusRatio, 0.0001f)
        assertEquals(0.17f, restoredProfile.keyCenters[0].x, 0.0001f)
        assertEquals(0.61f, restoredProfile.keyCenters[0].y, 0.0001f)
        assertEquals(0.84f, restoredProfile.keyCenters[14].x, 0.0001f)
        assertEquals(0.89f, restoredProfile.keyCenters[14].y, 0.0001f)

        // 2. SettingsRepository に保存（SharedPreferencesへの復元シミュレーション）
        settingsRepo.saveFitProfile(restoredProfile)
        assertEquals(restoredProfile, viewModel.uiState.value.savedProfile)

        // 3. ViewModel の useSavedProfile() で画面に適用
        viewModel.useSavedProfile()
        val appliedState = viewModel.uiState.value
        assertEquals(restoredProfile, appliedState.currentProfile)
        assertEquals(0.053f, appliedState.currentProfile!!.keyRadiusRatio, 0.0001f)
        assertEquals(0.17f, appliedState.currentProfile!!.keyCenters[0].x, 0.0001f)
        assertEquals(0.61f, appliedState.currentProfile!!.keyCenters[0].y, 0.0001f)
        assertEquals(0.84f, appliedState.currentProfile!!.keyCenters[14].x, 0.0001f)
        assertEquals(0.89f, appliedState.currentProfile!!.keyCenters[14].y, 0.0001f)

        // 4. 手動微調整モードに入っても保存済みプロファイルの4隅アンカーが正確に引き継がれること
        viewModel.startManualAdjust()
        val manualState = viewModel.uiState.value
        assertEquals(0.17f, manualState.manualTopLeft.x, 0.001f)
        assertEquals(0.61f, manualState.manualTopLeft.y, 0.001f)
        assertEquals(0.82f, manualState.manualTopRight.x, 0.001f)
        assertEquals(0.62f, manualState.manualTopRight.y, 0.001f)
        assertEquals(0.19f, manualState.manualBottomLeft.x, 0.001f)
        assertEquals(0.88f, manualState.manualBottomLeft.y, 0.001f)
        assertEquals(0.84f, manualState.manualBottomRight.x, 0.001f)
        assertEquals(0.89f, manualState.manualBottomRight.y, 0.001f)
    }

    @Test
    fun `detected profile on initial fitting can apply detected radius`() = runBlocking {
        // 初回フィッティング時 (savedProfile == null)
        assertEquals(null, settingsRepo.fitProfile.value)
        assertEquals(0.04f, settingsRepo.visualConfig.value.guideRadiusRatio, 0.001f)

        val detectedProfile = FitProfile.createDefaultTestProfile(landscape = true).copy(keyRadiusRatio = 0.055f)
        viewModel.setDetectedResultForTest(detectedProfile)

        viewModel.saveCurrentProfile()

        assertTrue(viewModel.uiState.value.isSavedSuccess)
        assertEquals(detectedProfile, settingsRepo.fitProfile.value)
        // 初回自動検出のため、検出された半径 0.055f が VisualConfig に反映される
        assertEquals(0.055f, settingsRepo.visualConfig.value.guideRadiusRatio, 0.001f)
    }

    @Test
    fun `detected profile on existing profile does not overwrite customized guide radius`() = runBlocking {
        // 既存Profileがあり、ユーザーがガイドサイズを 0.07f にカスタマイズしている状態
        val existingProfile = FitProfile.createDefaultTestProfile(landscape = true).copy(keyRadiusRatio = 0.04f)
        settingsRepo.saveFitProfile(existingProfile)
        settingsRepo.saveGuideRadiusRatio(0.07f)

        // ViewModelを既存Profile購読状態で再初期化
        viewModel = FittingViewModel(
            settingsRepository = settingsRepo,
            keyDetector = fakeDetector,
            gridFitter = gridFitter,
            externalScope = testScope
        )
        assertEquals(existingProfile, viewModel.uiState.value.savedProfile)

        // 新たに自動検出（半径 0.05f）を実行
        val newDetectedProfile = FitProfile.createDefaultTestProfile(landscape = true).copy(keyRadiusRatio = 0.05f)
        viewModel.setDetectedResultForTest(newDetectedProfile)

        viewModel.saveCurrentProfile()

        assertTrue(viewModel.uiState.value.isSavedSuccess)
        assertEquals(newDetectedProfile, settingsRepo.fitProfile.value)
        // 既に保存済みProfileが存在するため、カスタマイズされた 0.07f は上書きされず維持される
        assertEquals(0.07f, settingsRepo.visualConfig.value.guideRadiusRatio, 0.001f)
    }

    @Test
    fun `manually adjusted profile does not overwrite guide radius`() = runBlocking {
        // ユーザーがガイドサイズを 0.065f にカスタマイズ
        settingsRepo.saveGuideRadiusRatio(0.065f)
        assertEquals(0.065f, settingsRepo.visualConfig.value.guideRadiusRatio, 0.001f)

        // 手動調整モードを開始
        viewModel.startManualAdjust()
        viewModel.updateManualCorner(cornerIndex = 0, normX = 0.15f, normY = 0.55f)

        viewModel.saveCurrentProfile()

        assertTrue(viewModel.uiState.value.isSavedSuccess)
        assertNotNull(settingsRepo.fitProfile.value)
        // 手動調整による保存では、カスタマイズされた 0.065f が変更されず維持される
        assertEquals(0.065f, settingsRepo.visualConfig.value.guideRadiusRatio, 0.001f)
    }

    @Test
    fun `saved profile reuse does not overwrite guide radius`() = runBlocking {
        // 既存Profileがあり、ガイドサイズは 0.062f
        val savedProfile = FitProfile.createDefaultTestProfile(landscape = true).copy(keyRadiusRatio = 0.038f)
        settingsRepo.saveFitProfile(savedProfile)
        settingsRepo.saveGuideRadiusRatio(0.062f)

        viewModel = FittingViewModel(
            settingsRepository = settingsRepo,
            keyDetector = fakeDetector,
            gridFitter = gridFitter,
            externalScope = testScope
        )

        // 保存済みProfileを採用して保存
        viewModel.useSavedProfile()
        viewModel.saveCurrentProfile()

        assertTrue(viewModel.uiState.value.isSavedSuccess)
        assertEquals(savedProfile, settingsRepo.fitProfile.value)
        // ガイドサイズ 0.062f が変更されず維持される
        assertEquals(0.062f, settingsRepo.visualConfig.value.guideRadiusRatio, 0.001f)
    }
}
