package com.onigiri.keycue.ui.home

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 設定画面アコーディオンの項目を識別する列挙型。
 */
enum class SettingsAccordionKey {
    MIDI_MAPPING,
    PLAYBACK_TIMING,
    METRONOME,
    VISUAL,
    CONTROL_OVERLAY,
    SKY_LAYOUT,
    FITTING,
    OVERLAY_PERMISSION
}

/**
 * アプリプロセス内で設定画面アコーディオンの開閉セッション状態を管理するシングルトンマネージャー。
 *
 * 【設計仕様】
 * 1. **Single Source of Truth**:
 *    設定画面のアコーディオン開閉状態の唯一の保存元です。
 * 2. **オーバーレイ復帰時の復元**:
 *    Activity が finish された後でも、オーバーレイから設定画面を開き直した際（EXTRA_OPENED_FROM_OVERLAY == true）は
 *    直前の開閉状態を完全復元します。
 * 3. **通常新規起動時のリセット**:
 *    通常の新規起動（タスクキル後の起動、ランチャーからの新規起動）時は [resetToAllClosed] により全閉に初期化します。
 * 4. **Compose 再構成耐性**:
 *    状態は本 Manager に集約されるため、画面回転や Compose の Recomposition で状態が失われることはありません。
 */
object SettingsSessionManager {

    private val _accordionState = MutableStateFlow(SettingsAccordionUiState())
    val accordionState: StateFlow<SettingsAccordionUiState> = _accordionState.asStateFlow()

    /**
     * 現在のアコーディオン開閉状態を取得する。
     */
    fun getAccordionState(): SettingsAccordionUiState = _accordionState.value

    /**
     * 開閉状態を更新する。
     */
    fun updateAccordionState(transform: (SettingsAccordionUiState) -> SettingsAccordionUiState) {
        _accordionState.update(transform)
    }

    /**
     * 特定のアコーディオン項目の開閉状態を設定する。
     */
    fun setExpanded(key: SettingsAccordionKey, expanded: Boolean) {
        _accordionState.update { current ->
            when (key) {
                SettingsAccordionKey.MIDI_MAPPING -> current.copy(midiMapping = expanded)
                SettingsAccordionKey.PLAYBACK_TIMING -> current.copy(playbackTiming = expanded)
                SettingsAccordionKey.METRONOME -> current.copy(metronome = expanded)
                SettingsAccordionKey.VISUAL -> current.copy(visual = expanded)
                SettingsAccordionKey.CONTROL_OVERLAY -> current.copy(controlOverlay = expanded)
                SettingsAccordionKey.SKY_LAYOUT -> current.copy(skyLayout = expanded)
                SettingsAccordionKey.FITTING -> current.copy(fitting = expanded)
                SettingsAccordionKey.OVERLAY_PERMISSION -> current.copy(overlayPermission = expanded)
            }
        }
    }

    /**
     * 文字列キー指定で開閉状態を設定する。
     */
    fun setExpanded(key: String, expanded: Boolean) {
        try {
            setExpanded(SettingsAccordionKey.valueOf(key), expanded)
        } catch (_: Exception) {
            // 不明なキーは無視
        }
    }

    /**
     * すべてのアコーディオンを閉じた初期状態にリセットする。
     */
    fun resetToAllClosed(): SettingsAccordionUiState {
        val allClosed = SettingsAccordionUiState()
        _accordionState.value = allClosed
        return allClosed
    }
}
