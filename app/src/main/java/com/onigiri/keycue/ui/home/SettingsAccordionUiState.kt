package com.onigiri.keycue.ui.home

/**
 * ホーム画面における各設定アコーディオンの開閉状態を表すUI状態モデル。
 *
 * @param midiMapping 「MIDIマッピング」アコーディオンの開閉状態
 * @param playbackTiming 「再生・タイミング設定」アコーディオンの開閉状態
 * @param metronome 「メトロノーム」アコーディオンの開閉状態
 * @param visual 「ガイド・ノート表示」アコーディオンの開閉状態
 * @param controlOverlay 「コントロールオーバーレイ」アコーディオンの開閉状態
 * @param skyLayout 「Sky ボタンレイアウト」アコーディオンの開閉状態
 * @param fitting 「ボタン位置設定」アコーディオンの開閉状態
 * @param overlayPermission 「オーバーレイ権限」アコーディオンの開閉状態
 */
data class SettingsAccordionUiState(
    val midiMapping: Boolean = false,
    val playbackTiming: Boolean = false,
    val metronome: Boolean = false,
    val visual: Boolean = false,
    val controlOverlay: Boolean = false,
    val skyLayout: Boolean = false,
    val fitting: Boolean = false,
    val overlayPermission: Boolean = false
) {
    /**
     * すべてのアコーディオンが閉じているかどうか。
     */
    val isAllClosed: Boolean
        get() = !midiMapping && !playbackTiming && !metronome && !visual &&
                !controlOverlay && !skyLayout && !fitting && !overlayPermission

    fun isExpanded(key: SettingsAccordionKey): Boolean = when (key) {
        SettingsAccordionKey.MIDI_MAPPING -> midiMapping
        SettingsAccordionKey.PLAYBACK_TIMING -> playbackTiming
        SettingsAccordionKey.METRONOME -> metronome
        SettingsAccordionKey.VISUAL -> visual
        SettingsAccordionKey.CONTROL_OVERLAY -> controlOverlay
        SettingsAccordionKey.SKY_LAYOUT -> skyLayout
        SettingsAccordionKey.FITTING -> fitting
        SettingsAccordionKey.OVERLAY_PERMISSION -> overlayPermission
    }

    fun isExpanded(key: String): Boolean = try {
        isExpanded(SettingsAccordionKey.valueOf(key))
    } catch (_: Exception) {
        false
    }
}
