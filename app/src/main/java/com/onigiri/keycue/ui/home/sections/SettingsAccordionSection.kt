package com.onigiri.keycue.ui.home.sections

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.onigiri.keycue.model.MidiMappingMode
import com.onigiri.keycue.model.PitchClass
import com.onigiri.keycue.model.ScaleType
import com.onigiri.keycue.model.SongFormat
import com.onigiri.keycue.profile.SkyLayout
import com.onigiri.keycue.ui.home.ExpandableCard
import com.onigiri.keycue.ui.home.HomeUiState
import java.util.Locale

/**
 * ホーム画面の設定アコーディオンセクション。
 * MIDIマッピング、再生設定、ビジュアル設定、ボタン位置設定、オーバーレイ権限をアコーディオン形式で表示する。
 */
@Composable
fun SettingsAccordionSection(
    uiState: HomeUiState,
    onFittingClick: () -> Unit,
    onRequestPermissionClick: () -> Unit,
    onSpeedDecrease: () -> Unit,
    onSpeedIncrease: () -> Unit,
    onNoteLeadTimeDecrease: () -> Unit,
    onNoteLeadTimeIncrease: () -> Unit,
    onApproachCircleLeadTimeDecrease: () -> Unit,
    onApproachCircleLeadTimeIncrease: () -> Unit,
    onCountdownChange: (Long) -> Unit,
    onGuideRadiusChange: (Float) -> Unit,
    onShowGuideLabelsChange: (Boolean) -> Unit,
    onShowFallingNotesChange: (Boolean) -> Unit,
    onShowApproachCirclesChange: (Boolean) -> Unit,
    onShowRepeatCountBadgeChange: (Boolean) -> Unit,
    onShowChordLinksChange: (Boolean) -> Unit = {},
    onShowChordHalosChange: (Boolean) -> Unit = {},
    onChordStrokeWidthChange: (Float) -> Unit = {},
    onChordStrokeAlphaChange: (Int) -> Unit = {},
    onChordHaloFillAlphaChange: (Int) -> Unit = {},
    onShowJustEffectChange: (Boolean) -> Unit,
    onGuideColorChange: (Int) -> Unit,
    onNoteColorTopChange: (Int) -> Unit,
    onNoteColorMiddleChange: (Int) -> Unit,
    onNoteColorBottomChange: (Int) -> Unit,
    onMidiMappingModeChange: (MidiMappingMode) -> Unit,
    onManualRootChange: (PitchClass) -> Unit,
    onScaleChange: (ScaleType) -> Unit,
    onManualBaseOctaveChange: (Int) -> Unit,
    onShowSongInfoChange: (Boolean) -> Unit = {},
    onShowSeekBarChange: (Boolean) -> Unit = {},
    onShowLoopControlsChange: (Boolean) -> Unit = {},
    onShowSpeedControlChange: (Boolean) -> Unit = {},
    onShowNoteLeadTimeControlChange: (Boolean) -> Unit = {},
    onShowCircleLeadTimeControlChange: (Boolean) -> Unit = {},
    onShowCountdownControlChange: (Boolean) -> Unit = {},
    onShowGuideQuickTogglesChange: (Boolean) -> Unit = {},
    onShowRecentSongsChange: (Boolean) -> Unit = {},
    onShowSongSelectionChange: (Boolean) -> Unit = {},
    onShowFittingChange: (Boolean) -> Unit = {},
    onShowGuideToggleChange: (Boolean) -> Unit = {},
    onMetronomeEnabledChange: (Boolean) -> Unit = {},
    onMetronomeTimingModeChange: (com.onigiri.keycue.model.MetronomeTimingMode) -> Unit = {},
    onMetronomeBpmChange: (Int) -> Unit = {},
    onMetronomeBeatsPerBarChange: (Int) -> Unit = {},
    onMetronomeSubdivisionChange: (com.onigiri.keycue.model.BeatSubdivision) -> Unit = {},
    onMetronomeAccentEnabledChange: (Boolean) -> Unit = {},
    onMetronomeVolumeChange: (Int) -> Unit = {},
    onMetronomeBeatOffsetChange: (Long) -> Unit = {},
    onLayoutChange: (SkyLayout) -> Unit = {},
    onShowLayoutSelectionChange: (Boolean) -> Unit = {}
) {
    var midiMappingExpanded by rememberSaveable { mutableStateOf(false) }
    var timingExpanded by rememberSaveable { mutableStateOf(true) }
    var metronomeExpanded by rememberSaveable { mutableStateOf(false) }
    var visualExpanded by rememberSaveable { mutableStateOf(false) }
    var permissionExpanded by rememberSaveable { mutableStateOf(!uiState.overlayPermissionGranted) }

    // 0. MIDIマッピング設定 (MIDIファイル読み込み時のみ表示)
    if (uiState.songFormat == SongFormat.MIDI) {
        val mapping = uiState.resolvedMidiMapping
        val mappingSummary = if (uiState.midiMappingSettings.mode == MidiMappingMode.AUTO) {
            if (mapping != null) {
                "${mapping.root.displayName} ${mapping.scale.displayName} / Oct${mapping.baseOctave} (${mapping.mappedEventCount}/${mapping.totalEventCount})"
            } else {
                "自動"
            }
        } else {
            "${uiState.midiMappingSettings.manualRoot.displayName} ${uiState.midiMappingSettings.manualScale.displayName} / Oct${uiState.midiMappingSettings.manualBaseOctave}"
        }

        ExpandableCard(
            title = "MIDIマッピング",
            summary = mappingSummary,
            expanded = midiMappingExpanded,
            onExpandedChange = { midiMappingExpanded = it }
        ) {
            MidiMappingConfigContent(
                midiMappingSettings = uiState.midiMappingSettings,
                resolvedMapping = uiState.resolvedMidiMapping,
                onModeChange = onMidiMappingModeChange,
                onRootChange = onManualRootChange,
                onScaleChange = onScaleChange,
                onOctaveChange = onManualBaseOctaveChange
            )
        }
    }

    // 1. 再生・タイミング設定
    val speedPercent = (uiState.speed * 100).toInt()
    val timingSummary = "$speedPercent% / ノート先読み${uiState.noteLeadTimeMs}ms"
    ExpandableCard(
        title = "再生・タイミング設定",
        summary = timingSummary,
        expanded = timingExpanded,
        onExpandedChange = { timingExpanded = it }
    ) {
        PlaybackConfigContent(
            speed = uiState.speed,
            noteLeadTimeMs = uiState.noteLeadTimeMs,
            approachCircleLeadTimeMs = uiState.approachCircleLeadTimeMs,
            countdownMs = uiState.countdownMs,
            onSpeedDecrease = onSpeedDecrease,
            onSpeedIncrease = onSpeedIncrease,
            onNoteLeadTimeDecrease = onNoteLeadTimeDecrease,
            onNoteLeadTimeIncrease = onNoteLeadTimeIncrease,
            onApproachCircleLeadTimeDecrease = onApproachCircleLeadTimeDecrease,
            onApproachCircleLeadTimeIncrease = onApproachCircleLeadTimeIncrease,
            onCountdownChange = onCountdownChange
        )
    }

    // 2. メトロノーム設定
    val metro = uiState.metronomeConfig
    val timeline = uiState.resolvedTimeline
    val currentBpm = Math.round(timeline.bpmAt(0L)).toInt()
    val currentTs = timeline.timeSignatureAt(0L).displayString
    val modeName = if (metro.timingMode == com.onigiri.keycue.model.MetronomeTimingMode.AUTO) "自動" else "手動"
    val subText = if (metro.subdivision == com.onigiri.keycue.model.BeatSubdivision.QUARTER) "4分" else "8分"
    val metronomeSummary = if (metro.enabled) {
        "ON / $modeName (曲頭: $currentBpm BPM, $currentTs / $subText) / 音量${metro.volumePercent}%"
    } else {
        "OFF / $modeName (曲頭: $currentBpm BPM, $currentTs)"
    }
    ExpandableCard(
        title = "メトロノーム",
        summary = metronomeSummary,
        expanded = metronomeExpanded,
        onExpandedChange = { metronomeExpanded = it }
    ) {
        MetronomeConfigContent(
            config = uiState.metronomeConfig,
            timeline = timeline,
            onEnabledChange = onMetronomeEnabledChange,
            onTimingModeChange = onMetronomeTimingModeChange,
            onBpmChange = onMetronomeBpmChange,
            onBeatsPerBarChange = onMetronomeBeatsPerBarChange,
            onSubdivisionChange = onMetronomeSubdivisionChange,
            onAccentEnabledChange = onMetronomeAccentEnabledChange,
            onVolumeChange = onMetronomeVolumeChange,
            onBeatOffsetChange = onMetronomeBeatOffsetChange
        )
    }

    // 3. ガイド・ノート表示設定
    val radiusPercentStr = String.format(Locale.US, "%.1f", uiState.visualConfig.guideRadiusRatio * 100)
    val visualSummary = "サイズ $radiusPercentStr% / ノート${if (uiState.visualConfig.showFallingNotes) "ON" else "OFF"}"
    ExpandableCard(
        title = "ガイド・ノート表示",
        summary = visualSummary,
        expanded = visualExpanded,
        onExpandedChange = { visualExpanded = it }
    ) {
        VisualConfigContent(
            visualConfig = uiState.visualConfig,
            onGuideRadiusChange = onGuideRadiusChange,
            onShowGuideLabelsChange = onShowGuideLabelsChange,
            onShowFallingNotesChange = onShowFallingNotesChange,
            onShowApproachCirclesChange = onShowApproachCirclesChange,
            onShowRepeatCountBadgeChange = onShowRepeatCountBadgeChange,
            onShowChordLinksChange = onShowChordLinksChange,
            onShowChordHalosChange = onShowChordHalosChange,
            onChordStrokeWidthChange = onChordStrokeWidthChange,
            onChordStrokeAlphaChange = onChordStrokeAlphaChange,
            onChordHaloFillAlphaChange = onChordHaloFillAlphaChange,
            onShowJustEffectChange = onShowJustEffectChange,
            onGuideColorChange = onGuideColorChange,
            onNoteColorTopChange = onNoteColorTopChange,
            onNoteColorMiddleChange = onNoteColorMiddleChange,
            onNoteColorBottomChange = onNoteColorBottomChange
        )
    }

    // 3. コントロールオーバーレイ表示設定
    var controlOverlayExpanded by rememberSaveable { mutableStateOf(false) }
    val activeControlCount = listOf(
        uiState.controlOverlayConfig.showSongInfo,
        uiState.controlOverlayConfig.showSeekBar,
        uiState.controlOverlayConfig.showLoopControls,
        uiState.controlOverlayConfig.showSpeedControl,
        uiState.controlOverlayConfig.showNoteLeadTimeControl,
        uiState.controlOverlayConfig.showCircleLeadTimeControl,
        uiState.controlOverlayConfig.showCountdownControl,
        uiState.controlOverlayConfig.showGuideQuickToggles,
        uiState.controlOverlayConfig.showRecentSongs,
        uiState.controlOverlayConfig.showSongSelection,
        uiState.controlOverlayConfig.showFitting,
        uiState.controlOverlayConfig.showGuideToggle,
        uiState.controlOverlayConfig.showLayoutSelection
    ).count { it }
    val controlSummary = "${activeControlCount}/13 項目表示"
    ExpandableCard(
        title = "コントロールオーバーレイ",
        summary = controlSummary,
        expanded = controlOverlayExpanded,
        onExpandedChange = { controlOverlayExpanded = it }
    ) {
        ControlOverlayConfigContent(
            config = uiState.controlOverlayConfig,
            onShowSongInfoChange = onShowSongInfoChange,
            onShowSeekBarChange = onShowSeekBarChange,
            onShowLoopControlsChange = onShowLoopControlsChange,
            onShowSpeedControlChange = onShowSpeedControlChange,
            onShowNoteLeadTimeControlChange = onShowNoteLeadTimeControlChange,
            onShowCircleLeadTimeControlChange = onShowCircleLeadTimeControlChange,
            onShowCountdownControlChange = onShowCountdownControlChange,
            onShowGuideQuickTogglesChange = onShowGuideQuickTogglesChange,
            onShowRecentSongsChange = onShowRecentSongsChange,
            onShowSongSelectionChange = onShowSongSelectionChange,
            onShowFittingChange = onShowFittingChange,
            onShowGuideToggleChange = onShowGuideToggleChange,
            onShowLayoutSelectionChange = onShowLayoutSelectionChange
        )
    }

    // 4. Sky ボタンレイアウト設定
    var layoutExpanded by rememberSaveable { mutableStateOf(false) }
    val layoutSummary = uiState.selectedSkyLayout.displayName
    ExpandableCard(
        title = "Sky ボタンレイアウト",
        summary = layoutSummary,
        expanded = layoutExpanded,
        onExpandedChange = { layoutExpanded = it }
    ) {
        SkyLayoutConfigContent(
            selectedLayout = uiState.selectedSkyLayout,
            onLayoutChange = onLayoutChange
        )
    }

    // 5. ボタン位置設定（スクリーンショット自動検出）
    var fittingExpanded by rememberSaveable { mutableStateOf(false) }
    val fittingSummary = if (uiState.fitConfigured) "設定済み" else "未設定"
    ExpandableCard(
        title = "ボタン位置設定",
        summary = fittingSummary,
        expanded = fittingExpanded,
        onExpandedChange = { fittingExpanded = it }
    ) {
        FittingConfigContent(
            isConfigured = uiState.fitConfigured,
            onFittingClick = onFittingClick
        )
    }

    // 6. オーバーレイ権限
    val permissionSummary = if (uiState.overlayPermissionGranted) "許可済み" else "未許可"
    ExpandableCard(
        title = "オーバーレイ権限",
        summary = permissionSummary,
        expanded = permissionExpanded,
        onExpandedChange = { permissionExpanded = it }
    ) {
        OverlayPermissionStatusContent(
            isGranted = uiState.overlayPermissionGranted,
            onRequestPermissionClick = onRequestPermissionClick
        )
    }
}

/**
 * Sky ボタンレイアウト（5種類）の選択UI。
 */
@Composable
fun SkyLayoutConfigContent(
    selectedLayout: SkyLayout,
    onLayoutChange: (SkyLayout) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "タッチ（標準）で位置合わせした結果を基準に、各レイアウトへ自動配置します。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        SkyLayout.entries.forEach { layout ->
            val isSelected = layout == selectedLayout
            val description = when (layout) {
                SkyLayout.TOUCH_STANDARD -> "タッチ操作・標準（位置合わせ基準）"
                SkyLayout.TOUCH_EXPANDED -> "タッチ操作・拡大"
                SkyLayout.PAD_TRIGGER_FIRST -> "ゲームパッド・トリガー優先（上部にトリガー、下部に十字キー）"
                SkyLayout.PAD_DPAD_FIRST -> "ゲームパッド・十字キー優先（上部に十字キー、下部にトリガー）"
                SkyLayout.PAD_GRID -> "ゲームパッド・グリッド（3行5列拡大配置）"
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onLayoutChange(layout) },
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                },
                shape = RoundedCornerShape(8.dp),
                border = if (isSelected) {
                    BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                } else null
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = isSelected,
                        onClick = { onLayoutChange(layout) }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = layout.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
