package com.onigiri.keycue.ui.home

import com.onigiri.keycue.data.InMemorySettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 設定画面アコーディオンの開閉状態（SettingsAccordionUiState / SettingsSessionManager）および
 * HomeViewModel との連携を検証する単体テスト。
 */
class SettingsAccordionStateTest {

    private val testScope = CoroutineScope(Dispatchers.Unconfined)

    @Before
    fun setUp() {
        SettingsSessionManager.resetToAllClosed()
    }

    @After
    fun tearDown() {
        SettingsSessionManager.resetToAllClosed()
    }

    @Test
    fun testSettingsAccordionUiState_defaultsToAllClosed() {
        val state = SettingsAccordionUiState()
        assertTrue("初期状態ではisAllClosedがtrueであること", state.isAllClosed)
        assertFalse(state.midiMapping)
        assertFalse(state.playbackTiming)
        assertFalse(state.metronome)
        assertFalse(state.visual)
        assertFalse(state.controlOverlay)
        assertFalse(state.skyLayout)
        assertFalse(state.fitting)
        assertFalse(state.overlayPermission)
    }

    @Test
    fun testSettingsSessionManager_setExpandedAndReset() {
        // 初期状態は全閉
        assertTrue(SettingsSessionManager.getAccordionState().isAllClosed)

        // 各項目を個別に開く
        SettingsSessionManager.setExpanded(SettingsAccordionKey.METRONOME, true)
        SettingsSessionManager.setExpanded(SettingsAccordionKey.VISUAL, true)

        val updated = SettingsSessionManager.getAccordionState()
        assertFalse(updated.isAllClosed)
        assertTrue(updated.metronome)
        assertTrue(updated.visual)
        assertFalse(updated.playbackTiming)

        // resetToAllClosed で全閉に初期化
        val resetState = SettingsSessionManager.resetToAllClosed()
        assertTrue("リセット後は全閉であること", resetState.isAllClosed)
        assertTrue("Manager内のStateも全閉であること", SettingsSessionManager.getAccordionState().isAllClosed)
    }

    @Test
    fun testSettingsSessionManager_stringKeyInteroperability() {
        SettingsSessionManager.setExpanded("CONTROL_OVERLAY", true)
        assertTrue(SettingsSessionManager.getAccordionState().controlOverlay)
        assertTrue(SettingsSessionManager.getAccordionState().isExpanded("CONTROL_OVERLAY"))
        assertTrue(SettingsSessionManager.getAccordionState().isExpanded(SettingsAccordionKey.CONTROL_OVERLAY))

        SettingsSessionManager.setExpanded("CONTROL_OVERLAY", false)
        assertFalse(SettingsSessionManager.getAccordionState().controlOverlay)
    }

    @Test
    fun testHomeViewModel_syncsWithSettingsSessionManager() = runBlocking {
        val repository = InMemorySettingsRepository()
        val viewModel = HomeViewModel(
            settingsRepository = repository,
            externalScope = testScope,
            defaultDispatcher = Dispatchers.Unconfined
        )

        // 初期状態は全閉
        assertTrue(viewModel.uiState.value.accordionUiState.isAllClosed)

        // ViewModel経由でメトロノームを開く
        viewModel.setAccordionExpanded(SettingsAccordionKey.METRONOME, true)

        assertTrue(
            "ViewModelのUiStateに開いた状態が反映されること",
            viewModel.uiState.value.accordionUiState.metronome
        )
        assertTrue(
            "SessionManagerにも反映されていること",
            SettingsSessionManager.getAccordionState().metronome
        )

        // タイミング設定も開く
        viewModel.setAccordionExpanded(SettingsAccordionKey.PLAYBACK_TIMING, true)

        val currentState = viewModel.uiState.value.accordionUiState
        assertTrue(currentState.metronome)
        assertTrue(currentState.playbackTiming)
        assertFalse(currentState.visual)

        // オーバーレイ遷移・Activity再生成をシミュレート（resetToAllClosedは呼ばれない）
        // 新しいViewModelインスタンスを生成した場合でも、SessionManagerから開閉状態が復元されること
        val restoredViewModel = HomeViewModel(
            settingsRepository = repository,
            externalScope = testScope,
            defaultDispatcher = Dispatchers.Unconfined
        )

        val restoredState = restoredViewModel.uiState.value.accordionUiState
        assertTrue("直前のメトロノーム開閉状態が復元されること", restoredState.metronome)
        assertTrue("直前のタイミング開閉状態が復元されること", restoredState.playbackTiming)
        assertFalse(restoredState.visual)

        // 通常の新規起動時（MainActivity.onCreate / onNewIntent）の resetToAllClosed シミュレーション
        SettingsSessionManager.resetToAllClosed()

        assertTrue(
            "リセット後はViewModelのUiStateも全閉になること",
            restoredViewModel.uiState.value.accordionUiState.isAllClosed
        )
    }
}
