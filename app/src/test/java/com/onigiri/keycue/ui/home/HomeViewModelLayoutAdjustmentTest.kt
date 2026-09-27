package com.onigiri.keycue.ui.home

import com.onigiri.keycue.data.InMemorySettingsRepository
import com.onigiri.keycue.model.FitProfile
import com.onigiri.keycue.profile.SkyLayout
import com.onigiri.keycue.profile.SkyLayoutAdjustment
import com.onigiri.keycue.profile.SkyLayoutRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [HomeViewModel] におけるレイアウト微調整UI連携の単体テスト。
 *
 * 要件:
 * 1. 微調整値が拒否された場合（画面外等）、false を返し、スライダー表示および保存値を直前の有効値へ維持する。
 * 2. 保存失敗時も未保存の値を保存済みとして扱わない。
 * 3. 有効な微調整値は true を返し即座に反映・保存する。
 */
class HomeViewModelLayoutAdjustmentTest {

    private lateinit var settingsRepo: InMemorySettingsRepository
    private lateinit var viewModel: HomeViewModel
    private val testScope = CoroutineScope(Dispatchers.Unconfined)

    @Before
    fun setUp() = runBlocking {
        settingsRepo = InMemorySettingsRepository()
        // 基準プロファイルを TOUCH_STANDARD の実寸プリセットで設定
        val standardPreset = SkyLayoutRegistry.getPreset(SkyLayout.TOUCH_STANDARD)
        settingsRepo.saveFitProfile(FitProfile(standardPreset.keyCenters))

        viewModel = HomeViewModel(
            settingsRepository = settingsRepo,
            externalScope = testScope
        )
    }

    @Test
    fun testUpdateLayoutAdjustmentInMemory_valid_returnsTrue_andUpdatesRepository() {
        val targetLayout = SkyLayout.PAD_TRIGGER_FIRST
        val validAdj = SkyLayoutAdjustment(offsetX = 0.03f, offsetY = -0.02f, scaleX = 1.05f, scaleY = 0.95f)

        val result = viewModel.updateLayoutAdjustmentInMemory(targetLayout, validAdj)

        assertTrue("有効な微調整値は true を返すこと", result)
        assertEquals("リポジトリの微調整値が更新されていること", validAdj, settingsRepo.getLayoutAdjustment(targetLayout))
    }

    @Test
    fun testUpdateLayoutAdjustmentInMemory_outOfBounds_returnsFalse_andRetainsPrevious() {
        val targetLayout = SkyLayout.PAD_TRIGGER_FIRST
        val initialAdj = SkyLayoutAdjustment(offsetX = 0.02f, offsetY = 0.01f, scaleX = 1.0f, scaleY = 1.0f)
        viewModel.updateLayoutAdjustmentInMemory(targetLayout, initialAdj)

        // 画面外に大きく飛び出す無効な値 (offsetX = 0.50f)
        val invalidAdj = SkyLayoutAdjustment(offsetX = 0.50f, offsetY = 0.0f, scaleX = 1.0f, scaleY = 1.0f)
        val result = viewModel.updateLayoutAdjustmentInMemory(targetLayout, invalidAdj)

        assertFalse("範囲外の微調整値は拒否され false を返すこと", result)
        assertEquals("直前の有効な微調整値が維持されていること", initialAdj, settingsRepo.getLayoutAdjustment(targetLayout))
    }

    @Test
    fun testPersistLayoutAdjustment_valid_returnsTrue_andSavesToRepository() = runBlocking {
        val targetLayout = SkyLayout.PAD_DPAD_FIRST
        val validAdj = SkyLayoutAdjustment(offsetX = -0.02f, offsetY = 0.04f, scaleX = 0.95f, scaleY = 1.05f)

        val result = viewModel.persistLayoutAdjustment(targetLayout, validAdj)

        assertTrue("有効な微調整値の永続化は true を返すこと", result)
        assertEquals("リポジトリに正しく保存されていること", validAdj, settingsRepo.getLayoutAdjustment(targetLayout))
    }

    @Test
    fun testPersistLayoutAdjustment_outOfBounds_returnsFalse_andDoesNotSave() = runBlocking {
        val targetLayout = SkyLayout.PAD_DPAD_FIRST
        val initialAdj = SkyLayoutAdjustment(offsetX = 0.01f, offsetY = 0.02f, scaleX = 1.0f, scaleY = 1.0f)
        viewModel.persistLayoutAdjustment(targetLayout, initialAdj)

        // 画面外に大きく飛び出す無効な値 (scaleX = 2.0f)
        val invalidAdj = SkyLayoutAdjustment(offsetX = 0.0f, offsetY = 0.0f, scaleX = 2.0f, scaleY = 1.0f)
        val result = viewModel.persistLayoutAdjustment(targetLayout, invalidAdj)

        assertFalse("範囲外の微調整値の保存は拒否され false を返すこと", result)
        assertEquals("未保存の値は保存されず、直前の有効値が維持されること", initialAdj, settingsRepo.getLayoutAdjustment(targetLayout))
    }

    @Test
    fun testResetLayoutAdjustment_resetsToDefault() = runBlocking {
        val targetLayout = SkyLayout.PAD_GRID
        val customAdj = SkyLayoutAdjustment(offsetX = 0.03f, offsetY = -0.03f, scaleX = 1.02f, scaleY = 0.98f)
        viewModel.persistLayoutAdjustment(targetLayout, customAdj)
        assertEquals(customAdj, settingsRepo.getLayoutAdjustment(targetLayout))

        viewModel.resetLayoutAdjustment(targetLayout)

        assertEquals("リセット後はDEFAULT値に戻ること", SkyLayoutAdjustment.DEFAULT, settingsRepo.getLayoutAdjustment(targetLayout))
    }
}
