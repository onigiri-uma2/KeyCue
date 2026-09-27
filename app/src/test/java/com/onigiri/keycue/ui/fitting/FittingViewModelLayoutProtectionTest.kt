package com.onigiri.keycue.ui.fitting

import android.content.ContentResolver
import android.net.Uri
import com.onigiri.keycue.data.InMemorySettingsRepository
import com.onigiri.keycue.fitting.DetectedPoint
import com.onigiri.keycue.fitting.GridFitter
import com.onigiri.keycue.fitting.KeyDetector
import com.onigiri.keycue.model.FitProfile
import com.onigiri.keycue.profile.SkyLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Proxy

/**
 * [FittingViewModel] における非標準レイアウト時の基準フィッティング保護テスト。
 *
 * 要件:
 * 1. TOUCH_STANDARD 以外のレイアウト（TOUCH_EXPANDED, PAD_TRIGGER_FIRST, PAD_DPAD_FIRST, PAD_GRID）では
 *    基準フィッティング（画像選択・検出・手動調整・保存）を開始・保存できないようにする。
 * 2. 保存時等に選択レイアウトを自動変更（TOUCH_STANDARDへ強制変更）する方式は採用しない。
 */
class FittingViewModelLayoutProtectionTest {

    private lateinit var settingsRepo: InMemorySettingsRepository
    private lateinit var fakeDetector: KeyDetector
    private lateinit var gridFitter: GridFitter
    private val testScope = CoroutineScope(Dispatchers.Unconfined)

    private val nonStandardLayouts = listOf(
        SkyLayout.TOUCH_EXPANDED,
        SkyLayout.PAD_TRIGGER_FIRST,
        SkyLayout.PAD_DPAD_FIRST,
        SkyLayout.PAD_GRID
    )

    @Before
    fun setUp() {
        settingsRepo = InMemorySettingsRepository()
        fakeDetector = object : KeyDetector {
            override fun detect(image: android.graphics.Bitmap): List<DetectedPoint> = emptyList()
        }
        gridFitter = GridFitter()
    }

    private fun createViewModel(): FittingViewModel {
        return FittingViewModel(
            settingsRepository = settingsRepo,
            keyDetector = fakeDetector,
            gridFitter = gridFitter,
            externalScope = testScope
        )
    }

    @Test
    fun testIsFittingAllowed_onlyTrueForTouchStandard() = runBlocking {
        for (layout in SkyLayout.entries) {
            settingsRepo.saveSelectedSkyLayout(layout)
            val viewModel = createViewModel()
            if (layout == SkyLayout.TOUCH_STANDARD) {
                assertTrue("TOUCH_STANDARD では isFittingAllowed が true であるべき", viewModel.uiState.value.isFittingAllowed)
            } else {
                assertFalse("$layout では isFittingAllowed が false であるべき", viewModel.uiState.value.isFittingAllowed)
            }
        }
    }

    @Test
    fun testNonStandardLayout_blocksOnImageSelected_andRetainsLayout() = runBlocking {
        for (layout in nonStandardLayouts) {
            settingsRepo.saveSelectedSkyLayout(layout)
            val viewModel = createViewModel()

            val fakeUri = android.net.FakeUri("content://media/external/images/media/1")
            val fakeResolver = android.content.FakeContentResolver()

            viewModel.onImageSelected(fakeUri, fakeResolver)

            val state = viewModel.uiState.value
            assertNotNull("エラーメッセージが設定されること", state.errorMessage)
            assertTrue("タッチ（標準）専用の旨が記載されていること", state.errorMessage!!.contains("タッチ（標準）"))
            assertNull("画像は読み込まれないこと", state.imageBitmap)
            assertEquals("選択レイアウトが自動変更されずに維持されること", layout, settingsRepo.selectedSkyLayout.value)
            assertEquals(layout, state.currentSkyLayout)
        }
    }

    @Test
    fun testNonStandardLayout_blocksDetectAndFit_andRetainsLayout() = runBlocking {
        for (layout in nonStandardLayouts) {
            settingsRepo.saveSelectedSkyLayout(layout)
            val viewModel = createViewModel()

            viewModel.detectAndFit()

            val state = viewModel.uiState.value
            assertNotNull("エラーメッセージが設定されること", state.errorMessage)
            assertTrue("タッチ（標準）専用の旨が記載されていること", state.errorMessage!!.contains("タッチ（標準）"))
            assertEquals("Detectingステップに移行せずIdleのままであること", FittingStep.Idle, state.step)
            assertEquals("選択レイアウトが自動変更されずに維持されること", layout, settingsRepo.selectedSkyLayout.value)
            assertEquals(layout, state.currentSkyLayout)
        }
    }

    @Test
    fun testNonStandardLayout_blocksStartManualAdjust_andRetainsLayout() = runBlocking {
        for (layout in nonStandardLayouts) {
            settingsRepo.saveSelectedSkyLayout(layout)
            val viewModel = createViewModel()

            viewModel.startManualAdjust()

            val state = viewModel.uiState.value
            assertNotNull("エラーメッセージが設定されること", state.errorMessage)
            assertTrue("タッチ（標準）専用の旨が記載されていること", state.errorMessage!!.contains("タッチ（標準）"))
            assertEquals("ManualAdjustステップに移行せずIdleのままであること", FittingStep.Idle, state.step)
            assertEquals("選択レイアウトが自動変更されずに維持されること", layout, settingsRepo.selectedSkyLayout.value)
            assertEquals(layout, state.currentSkyLayout)
        }
    }

    @Test
    fun testNonStandardLayout_blocksSaveCurrentProfile_andRetainsLayout() = runBlocking {
        for (layout in nonStandardLayouts) {
            settingsRepo.saveSelectedSkyLayout(layout)
            val viewModel = createViewModel()

            // テスト用プロファイルを設定
            val testProfile = FitProfile.createDefaultTestProfile(landscape = true)
            viewModel.setDetectedResultForTest(testProfile)

            viewModel.saveCurrentProfile()

            val state = viewModel.uiState.value
            assertNotNull("エラーメッセージが設定されること", state.errorMessage)
            assertTrue("保存はタッチ（標準）専用の旨が記載されていること", state.errorMessage!!.contains("タッチ（標準）"))
            assertFalse("保存完了フラグはfalseのままであること", state.isSavedSuccess)
            assertNull("リポジトリにプロファイルが保存されていないこと", settingsRepo.fitProfile.value)
            assertEquals("選択レイアウトが自動変更されずに維持されること", layout, settingsRepo.selectedSkyLayout.value)
            assertEquals(layout, state.currentSkyLayout)
        }
    }

    @Test
    fun testTouchStandard_allowsSaveCurrentProfile() = runBlocking {
        settingsRepo.saveSelectedSkyLayout(SkyLayout.TOUCH_STANDARD)
        val viewModel = createViewModel()

        val testProfile = FitProfile.createDefaultTestProfile(landscape = true)
        viewModel.setDetectedResultForTest(testProfile)

        viewModel.saveCurrentProfile()

        val state = viewModel.uiState.value
        assertNull("エラーメッセージはnullであること", state.errorMessage)
        assertTrue("保存完了フラグがtrueになること", state.isSavedSuccess)
        assertEquals("リポジトリにプロファイルが保存されること", testProfile, settingsRepo.fitProfile.value)
        assertEquals(SkyLayout.TOUCH_STANDARD, settingsRepo.selectedSkyLayout.value)
    }
}
