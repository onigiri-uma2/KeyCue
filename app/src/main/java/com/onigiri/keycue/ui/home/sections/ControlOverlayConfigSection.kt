package com.onigiri.keycue.ui.home.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.onigiri.keycue.model.ControlOverlayConfig

/**
 * フローティング操作コントローラー（Control Overlay）の表示項目設定コンテンツ。
 */
@Composable
fun ControlOverlayConfigContent(
    config: ControlOverlayConfig,
    onShowSongInfoChange: (Boolean) -> Unit,
    onShowSeekBarChange: (Boolean) -> Unit,
    onShowPlaybackControlsChange: (Boolean) -> Unit,
    onShowLoopControlsChange: (Boolean) -> Unit,
    onShowSpeedControlChange: (Boolean) -> Unit,
    onShowNoteLeadTimeControlChange: (Boolean) -> Unit,
    onShowCircleLeadTimeControlChange: (Boolean) -> Unit,
    onShowSongSelectionChange: (Boolean) -> Unit,
    onShowFittingChange: (Boolean) -> Unit,
    onShowGuideToggleChange: (Boolean) -> Unit,
    onShowCountdownControlChange: (Boolean) -> Unit,
    onShowGuideQuickTogglesChange: (Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SettingSwitchRow(
            label = "楽曲情報",
            description = "曲名や演奏時間テキストを表示します",
            checked = config.showSongInfo,
            onCheckedChange = onShowSongInfoChange
        )
        SettingSwitchRow(
            label = "シークバー",
            description = "ミニ進捗スライダーを表示します",
            checked = config.showSeekBar,
            onCheckedChange = onShowSeekBarChange
        )
        SettingSwitchRow(
            label = "再生操作",
            description = "再生・一時停止・スキップ・停止・リスタートボタンを表示します",
            checked = config.showPlaybackControls,
            onCheckedChange = onShowPlaybackControlsChange
        )
        SettingSwitchRow(
            label = "ABリピート",
            description = "区間ループ（A/B地点）コントロールを表示します",
            checked = config.showLoopControls,
            onCheckedChange = onShowLoopControlsChange
        )
        SettingSwitchRow(
            label = "再生速度",
            description = "Speed調整行を表示します",
            checked = config.showSpeedControl,
            onCheckedChange = onShowSpeedControlChange
        )
        SettingSwitchRow(
            label = "Note先読み時間",
            description = "Note先読み時間調整行を表示します",
            checked = config.showNoteLeadTimeControl,
            onCheckedChange = onShowNoteLeadTimeControlChange
        )
        SettingSwitchRow(
            label = "Circle先読み時間",
            description = "Circle先読み時間調整行を表示します",
            checked = config.showCircleLeadTimeControl,
            onCheckedChange = onShowCircleLeadTimeControlChange
        )
        SettingSwitchRow(
            label = "楽曲を選択",
            description = "「楽曲を選択」ボタンを表示します",
            checked = config.showSongSelection,
            onCheckedChange = onShowSongSelectionChange
        )
        SettingSwitchRow(
            label = "位置微調整",
            description = "「位置微調整」ボタンを表示します",
            checked = config.showFitting,
            onCheckedChange = onShowFittingChange
        )
        SettingSwitchRow(
            label = "演奏ガイド切替",
            description = "「Guide ON / OFF」ボタンを表示します",
            checked = config.showGuideToggle,
            onCheckedChange = onShowGuideToggleChange
        )
        SettingSwitchRow(
            label = "カウントダウン",
            description = "開始カウントダウン（0s/1s/3s/5s）選択行を表示します",
            checked = config.showCountdownControl,
            onCheckedChange = onShowCountdownControlChange
        )
        SettingSwitchRow(
            label = "ガイド表示切替",
            description = "Notes/Circle 個別クイック切替行を表示します",
            checked = config.showGuideQuickToggles,
            onCheckedChange = onShowGuideQuickTogglesChange
        )
    }
}
