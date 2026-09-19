package com.onigiri.keycue.ui.home.sections

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.onigiri.keycue.model.MidiMappingMode
import com.onigiri.keycue.model.PitchClass
import com.onigiri.keycue.model.ScaleType
import com.onigiri.keycue.model.SongFormat
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
    onManualBaseOctaveChange: (Int) -> Unit
) {
    var midiMappingExpanded by rememberSaveable { mutableStateOf(false) }
    var timingExpanded by rememberSaveable { mutableStateOf(true) }
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

    // 2. ガイド・ノート表示設定
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

    // 3. ボタン位置設定（スクリーンショット自動検出）
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

    // 4. オーバーレイ権限
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
