package com.onigiri.keycue.model

/**
 * フローティング操作コントローラー（Control Overlay）に表示する各操作項目の表示・非表示設定。
 *
 * @param showSongInfo 楽曲情報（曲名・時間表示）の表示
 * @param showSeekBar ミニシークバー（進捗スライダー・現在時刻・総時間）の表示
 * @param showPlaybackControls 再生操作ボタン群の表示（常時表示項目・互換性保持）
 * @param showLoopControls ABリピート（区間ループ）操作ボタン群の表示
 * @param showSpeedControl 再生速度調整（Speed）行の表示
 * @param showNoteLeadTimeControl ノート先読み時間（Note）調整行の表示
 * @param showCircleLeadTimeControl タイミングサークル先読み時間（Circle）調整行の表示
 * @param showSongSelection 「楽曲を選択」ボタンの表示
 * @param showFitting 「位置微調整」ボタンの表示
 * @param showGuideToggle 「Guide ON / OFF」ボタンの表示
 * @param showCountdownControl 開始前カウントダウン調整セクションの表示
 * @param showGuideQuickToggles ガイドクイック表示切替（Notes/Circle）セクションの表示
 * @param showRecentSongs 最近使った楽曲履歴の表示（将来用・予約項目）
 */
data class ControlOverlayConfig(
    val showSongInfo: Boolean = true,
    val showSeekBar: Boolean = true,
    val showPlaybackControls: Boolean = true,
    val showLoopControls: Boolean = true,
    val showSpeedControl: Boolean = true,
    val showNoteLeadTimeControl: Boolean = true,
    val showCircleLeadTimeControl: Boolean = true,
    val showSongSelection: Boolean = true,
    val showFitting: Boolean = true,
    val showGuideToggle: Boolean = true,
    val showCountdownControl: Boolean = true,
    val showGuideQuickToggles: Boolean = true,
    val showRecentSongs: Boolean = false
)
