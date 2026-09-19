package com.onigiri.keycue.ui.fitting

import com.onigiri.keycue.data.InMemorySettingsRepository
import com.onigiri.keycue.fitting.DetectedPoint
import com.onigiri.keycue.fitting.GridFitter
import com.onigiri.keycue.fitting.KeyDetector
import com.onigiri.keycue.model.FitProfile
import com.onigiri.keycue.model.NormalizedPoint
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
}
