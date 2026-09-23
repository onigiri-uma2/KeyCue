package com.onigiri.keycue.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.onigiri.keycue.model.PlaybackConfig
import com.onigiri.keycue.model.VisualConfig
import com.onigiri.keycue.playback.TimeFormatter
import com.onigiri.keycue.ui.home.sections.ErrorMessageCard
import com.onigiri.keycue.ui.home.sections.SettingsAccordionSection
import com.onigiri.keycue.ui.home.sections.SongInfoCard
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel(
        factory = HomeViewModel.provideFactory(LocalContext.current.applicationContext)
    ),
    openedFromOverlay: Boolean = false,
    onCloseClick: () -> Unit = {},
    onSelectFileClick: () -> Unit = {},
    onFittingClick: () -> Unit = {},
    onStartSupportClick: () -> Unit = {},
    onPreviewClick: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    val permissionManager = remember {
        com.onigiri.keycue.permission.OverlayPermissionManager(context)
    }

    // 起動時: 前回楽曲の自動復元試行
    LaunchedEffect(Unit) {
        viewModel.tryRestoreLastSong()
    }

    // ライフサイクル（復帰時）に権限状態を再取得
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.refreshOverlayPermission(permissionManager)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Android Storage Access Framework (SAF) によるファイル選択
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.onFileSelected(uri)
        }
    }

    val handleSelectFile: () -> Unit = {
        filePickerLauncher.launch(
            arrayOf(
                "application/json",
                "text/plain",
                "text/json",
                "text/*",
                "audio/midi",
                "audio/x-midi",
                "audio/*",
                "application/octet-stream",
                "*/*"
            )
        )
        onSelectFileClick()
    }

    val handlePreviewClick: () -> Unit = {
        val song = uiState.songData
        val fitProfile = viewModel.getSavedFitProfile()
            ?: com.onigiri.keycue.model.FitProfile.createDefaultTestProfile()
        if (song != null) {
            com.onigiri.keycue.data.InMemoryPlaybackSessionRepository.instance.setSession(
                songData = song,
                config = PlaybackConfig(
                    speed = uiState.speed,
                    noteLeadTimeMs = uiState.noteLeadTimeMs,
                    approachCircleLeadTimeMs = uiState.approachCircleLeadTimeMs,
                    countdownMs = uiState.countdownMs
                ),
                fitProfile = fitProfile,
                uri = uiState.selectedSongUri,
                format = uiState.songFormat,
                resolvedMidiMapping = uiState.resolvedMidiMapping
            )
            onPreviewClick()
        }
    }

    val handleRequestPermission: () -> Unit = {
        try {
            context.startActivity(permissionManager.createSettingsIntent())
        } catch (_: Exception) {
        }
    }

    val handleStartSupport: () -> Unit = {
        if (permissionManager.canDrawOverlays()) {
            val song = uiState.songData
            val fitProfile = viewModel.getSavedFitProfile()
                ?: com.onigiri.keycue.model.FitProfile.createDefaultTestProfile()
            if (song != null) {
                com.onigiri.keycue.data.InMemoryPlaybackSessionRepository.instance.setSession(
                    songData = song,
                    config = PlaybackConfig(
                        speed = uiState.speed,
                        noteLeadTimeMs = uiState.noteLeadTimeMs,
                        approachCircleLeadTimeMs = uiState.approachCircleLeadTimeMs,
                        countdownMs = uiState.countdownMs
                    ),
                    fitProfile = fitProfile,
                    uri = uiState.selectedSongUri,
                    format = uiState.songFormat,
                    resolvedMidiMapping = uiState.resolvedMidiMapping
                )
            }
            com.onigiri.keycue.overlay.OverlayService.start(context)
            onStartSupportClick()
        } else {
            handleRequestPermission()
        }
    }

    HomeScreenContent(
        modifier = modifier,
        uiState = uiState,
        openedFromOverlay = openedFromOverlay,
        onCloseClick = onCloseClick,
        onSelectFileClick = handleSelectFile,
        onFittingClick = onFittingClick,
        onStartSupportClick = handleStartSupport,
        onPreviewClick = handlePreviewClick,
        onRequestPermissionClick = handleRequestPermission,
        onSpeedDecrease = { viewModel.adjustSpeed(-0.05f) },
        onSpeedIncrease = { viewModel.adjustSpeed(0.05f) },
        onNoteLeadTimeDecrease = { viewModel.adjustNoteLeadTime(-PlaybackConfig.NOTE_LEAD_TIME_STEP_MS) },
        onNoteLeadTimeIncrease = { viewModel.adjustNoteLeadTime(PlaybackConfig.NOTE_LEAD_TIME_STEP_MS) },
        onApproachCircleLeadTimeDecrease = { viewModel.adjustApproachCircleLeadTime(-PlaybackConfig.APPROACH_CIRCLE_LEAD_TIME_STEP_MS) },
        onApproachCircleLeadTimeIncrease = { viewModel.adjustApproachCircleLeadTime(PlaybackConfig.APPROACH_CIRCLE_LEAD_TIME_STEP_MS) },
        onCountdownChange = { viewModel.setCountdownMs(it) },
        onGuideRadiusChange = { viewModel.setGuideRadiusRatio(it) },
        onShowGuideLabelsChange = { viewModel.setShowGuideLabels(it) },
        onShowFallingNotesChange = { viewModel.setShowFallingNotes(it) },
        onShowApproachCirclesChange = { viewModel.setShowApproachCircles(it) },
        onShowRepeatCountBadgeChange = { viewModel.setShowRepeatCountBadge(it) },
        onShowChordLinksChange = { viewModel.setShowChordLinks(it) },
        onShowChordHalosChange = { viewModel.setShowChordHalos(it) },
        onChordStrokeWidthChange = { viewModel.setChordStrokeWidthDp(it) },
        onChordStrokeAlphaChange = { viewModel.setChordStrokeAlphaPercent(it) },
        onChordHaloFillAlphaChange = { viewModel.setChordHaloFillAlphaPercent(it) },
        onShowJustEffectChange = { viewModel.setShowJustEffect(it) },
        onGuideColorChange = { viewModel.setGuideColor(it) },
        onNoteColorTopChange = { viewModel.setNoteColorTop(it) },
        onNoteColorMiddleChange = { viewModel.setNoteColorMiddle(it) },
        onNoteColorBottomChange = { viewModel.setNoteColorBottom(it) },
        onMidiMappingModeChange = { viewModel.setMidiMappingMode(it) },
        onManualRootChange = { viewModel.setManualRoot(it) },
        onManualScaleChange = { viewModel.setManualScale(it) },
        onManualBaseOctaveChange = { viewModel.setManualBaseOctave(it) },
        onShowSongInfoChange = { viewModel.updateControlOverlayConfig { cfg -> cfg.copy(showSongInfo = it) } },
        onShowSeekBarChange = { viewModel.updateControlOverlayConfig { cfg -> cfg.copy(showSeekBar = it) } },
        onShowPlaybackControlsChange = { viewModel.updateControlOverlayConfig { cfg -> cfg.copy(showPlaybackControls = it) } },
        onShowLoopControlsChange = { viewModel.updateControlOverlayConfig { cfg -> cfg.copy(showLoopControls = it) } },
        onShowSpeedControlChange = { viewModel.updateControlOverlayConfig { cfg -> cfg.copy(showSpeedControl = it) } },
        onShowNoteLeadTimeControlChange = { viewModel.updateControlOverlayConfig { cfg -> cfg.copy(showNoteLeadTimeControl = it) } },
        onShowCircleLeadTimeControlChange = { viewModel.updateControlOverlayConfig { cfg -> cfg.copy(showCircleLeadTimeControl = it) } },
        onDismissError = { viewModel.clearError() }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreenContent(
    modifier: Modifier = Modifier,
    uiState: HomeUiState,
    openedFromOverlay: Boolean = false,
    onCloseClick: () -> Unit = {},
    onSelectFileClick: () -> Unit = {},
    onFittingClick: () -> Unit = {},
    onStartSupportClick: () -> Unit = {},
    onPreviewClick: () -> Unit = {},
    onRequestPermissionClick: () -> Unit = {},
    onSpeedDecrease: () -> Unit = {},
    onSpeedIncrease: () -> Unit = {},
    onNoteLeadTimeDecrease: () -> Unit = {},
    onNoteLeadTimeIncrease: () -> Unit = {},
    onApproachCircleLeadTimeDecrease: () -> Unit = {},
    onApproachCircleLeadTimeIncrease: () -> Unit = {},
    onCountdownChange: (Long) -> Unit = {},
    onGuideRadiusChange: (Float) -> Unit = {},
    onShowGuideLabelsChange: (Boolean) -> Unit = {},
    onShowFallingNotesChange: (Boolean) -> Unit = {},
    onShowApproachCirclesChange: (Boolean) -> Unit = {},
    onShowRepeatCountBadgeChange: (Boolean) -> Unit = {},
    onShowChordLinksChange: (Boolean) -> Unit = {},
    onShowChordHalosChange: (Boolean) -> Unit = {},
    onChordStrokeWidthChange: (Float) -> Unit = {},
    onChordStrokeAlphaChange: (Int) -> Unit = {},
    onChordHaloFillAlphaChange: (Int) -> Unit = {},
    onShowJustEffectChange: (Boolean) -> Unit = {},
    onGuideColorChange: (Int) -> Unit = {},
    onNoteColorTopChange: (Int) -> Unit = {},
    onNoteColorMiddleChange: (Int) -> Unit = {},
    onNoteColorBottomChange: (Int) -> Unit = {},
    onMidiMappingModeChange: (com.onigiri.keycue.model.MidiMappingMode) -> Unit = {},
    onManualRootChange: (com.onigiri.keycue.model.PitchClass) -> Unit = {},
    onManualScaleChange: (com.onigiri.keycue.model.ScaleType) -> Unit = {},
    onManualBaseOctaveChange: (Int) -> Unit = {},
    onShowSongInfoChange: (Boolean) -> Unit = {},
    onShowSeekBarChange: (Boolean) -> Unit = {},
    onShowPlaybackControlsChange: (Boolean) -> Unit = {},
    onShowLoopControlsChange: (Boolean) -> Unit = {},
    onShowSpeedControlChange: (Boolean) -> Unit = {},
    onShowNoteLeadTimeControlChange: (Boolean) -> Unit = {},
    onShowCircleLeadTimeControlChange: (Boolean) -> Unit = {},
    onDismissError: () -> Unit = {}
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val isDebugBuild = remember(context) {
        (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }

    LaunchedEffect(uiState.errorMessage) {
        val error = uiState.errorMessage
        if (error != null) {
            snackbarHostState.showSnackbar(error)
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isWideLayout = maxWidth > maxHeight && maxWidth >= 540.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = "KeyCue",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                    },
                    navigationIcon = {
                        if (openedFromOverlay) {
                            FilledTonalIconButton(
                                onClick = onCloseClick,
                                modifier = Modifier.padding(start = 8.dp)
                            ) {
                                Text(text = "←", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isWideLayout) {
                    HomeScreenWideContent(
                        modifier = Modifier.fillMaxSize(),
                        uiState = uiState,
                        isDebugBuild = isDebugBuild,
                        onSelectFileClick = onSelectFileClick,
                        onFittingClick = onFittingClick,
                        onStartSupportClick = onStartSupportClick,
                        onPreviewClick = onPreviewClick,
                        onRequestPermissionClick = onRequestPermissionClick,
                        onSpeedDecrease = onSpeedDecrease,
                        onSpeedIncrease = onSpeedIncrease,
                        onNoteLeadTimeDecrease = onNoteLeadTimeDecrease,
                        onNoteLeadTimeIncrease = onNoteLeadTimeIncrease,
                        onApproachCircleLeadTimeDecrease = onApproachCircleLeadTimeDecrease,
                        onApproachCircleLeadTimeIncrease = onApproachCircleLeadTimeIncrease,
                        onCountdownChange = onCountdownChange,
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
                        onNoteColorBottomChange = onNoteColorBottomChange,
                        onMidiMappingModeChange = onMidiMappingModeChange,
                        onManualRootChange = onManualRootChange,
                        onManualScaleChange = onManualScaleChange,
                        onManualBaseOctaveChange = onManualBaseOctaveChange,
                        onShowSongInfoChange = onShowSongInfoChange,
                        onShowSeekBarChange = onShowSeekBarChange,
                        onShowPlaybackControlsChange = onShowPlaybackControlsChange,
                        onShowLoopControlsChange = onShowLoopControlsChange,
                        onShowSpeedControlChange = onShowSpeedControlChange,
                        onShowNoteLeadTimeControlChange = onShowNoteLeadTimeControlChange,
                        onShowCircleLeadTimeControlChange = onShowCircleLeadTimeControlChange,
                        onDismissError = onDismissError
                    )
                } else {
                    HomeScreenNarrowContent(
                        modifier = Modifier.fillMaxSize(),
                        uiState = uiState,
                        isDebugBuild = isDebugBuild,
                        onSelectFileClick = onSelectFileClick,
                        onFittingClick = onFittingClick,
                        onStartSupportClick = onStartSupportClick,
                        onPreviewClick = onPreviewClick,
                        onRequestPermissionClick = onRequestPermissionClick,
                        onSpeedDecrease = onSpeedDecrease,
                        onSpeedIncrease = onSpeedIncrease,
                        onNoteLeadTimeDecrease = onNoteLeadTimeDecrease,
                        onNoteLeadTimeIncrease = onNoteLeadTimeIncrease,
                        onApproachCircleLeadTimeDecrease = onApproachCircleLeadTimeDecrease,
                        onApproachCircleLeadTimeIncrease = onApproachCircleLeadTimeIncrease,
                        onCountdownChange = onCountdownChange,
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
                        onNoteColorBottomChange = onNoteColorBottomChange,
                        onMidiMappingModeChange = onMidiMappingModeChange,
                        onManualRootChange = onManualRootChange,
                        onManualScaleChange = onManualScaleChange,
                        onManualBaseOctaveChange = onManualBaseOctaveChange,
                        onShowSongInfoChange = onShowSongInfoChange,
                        onShowSeekBarChange = onShowSeekBarChange,
                        onShowPlaybackControlsChange = onShowPlaybackControlsChange,
                        onShowLoopControlsChange = onShowLoopControlsChange,
                        onShowSpeedControlChange = onShowSpeedControlChange,
                        onShowNoteLeadTimeControlChange = onShowNoteLeadTimeControlChange,
                        onShowCircleLeadTimeControlChange = onShowCircleLeadTimeControlChange,
                        onDismissError = onDismissError
                    )
                }
            }
        }
    }
}

/**
 * 縦画面 (Portrait) および狭い画面向けの1カラムスクロールレイアウト
 */
@Composable
private fun HomeScreenNarrowContent(
    modifier: Modifier = Modifier,
    uiState: HomeUiState,
    isDebugBuild: Boolean = false,
    onSelectFileClick: () -> Unit,
    onFittingClick: () -> Unit,
    onStartSupportClick: () -> Unit,
    onPreviewClick: () -> Unit,
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
    onMidiMappingModeChange: (com.onigiri.keycue.model.MidiMappingMode) -> Unit,
    onManualRootChange: (com.onigiri.keycue.model.PitchClass) -> Unit,
    onManualScaleChange: (com.onigiri.keycue.model.ScaleType) -> Unit,
    onManualBaseOctaveChange: (Int) -> Unit,
    onShowSongInfoChange: (Boolean) -> Unit = {},
    onShowSeekBarChange: (Boolean) -> Unit = {},
    onShowPlaybackControlsChange: (Boolean) -> Unit = {},
    onShowLoopControlsChange: (Boolean) -> Unit = {},
    onShowSpeedControlChange: (Boolean) -> Unit = {},
    onShowNoteLeadTimeControlChange: (Boolean) -> Unit = {},
    onShowCircleLeadTimeControlChange: (Boolean) -> Unit = {},
    onDismissError: () -> Unit
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // エラー表示カード
        if (uiState.errorMessage != null) {
            ErrorMessageCard(
                errorMessage = uiState.errorMessage,
                onDismiss = onDismissError
            )
        }

        // 楽曲情報カード
        SongInfoCard(
            songTitle = uiState.selectedFileName ?: uiState.songTitle,
            songFormat = uiState.songFormat,
            durationMs = uiState.durationMs,
            noteCount = uiState.noteCount,
            isLoading = uiState.isLoading,
            onSelectFileClick = onSelectFileClick
        )

        // 設定アコーディオンカード群
        SettingsAccordionSection(
            uiState = uiState,
            onFittingClick = onFittingClick,
            onRequestPermissionClick = onRequestPermissionClick,
            onSpeedDecrease = onSpeedDecrease,
            onSpeedIncrease = onSpeedIncrease,
            onNoteLeadTimeDecrease = onNoteLeadTimeDecrease,
            onNoteLeadTimeIncrease = onNoteLeadTimeIncrease,
            onApproachCircleLeadTimeDecrease = onApproachCircleLeadTimeDecrease,
            onApproachCircleLeadTimeIncrease = onApproachCircleLeadTimeIncrease,
            onCountdownChange = onCountdownChange,
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
            onNoteColorBottomChange = onNoteColorBottomChange,
            onMidiMappingModeChange = onMidiMappingModeChange,
            onManualRootChange = onManualRootChange,
            onScaleChange = onManualScaleChange,
            onManualBaseOctaveChange = onManualBaseOctaveChange,
            onShowSongInfoChange = onShowSongInfoChange,
            onShowSeekBarChange = onShowSeekBarChange,
            onShowPlaybackControlsChange = onShowPlaybackControlsChange,
            onShowLoopControlsChange = onShowLoopControlsChange,
            onShowSpeedControlChange = onShowSpeedControlChange,
            onShowNoteLeadTimeControlChange = onShowNoteLeadTimeControlChange,
            onShowCircleLeadTimeControlChange = onShowCircleLeadTimeControlChange
        )

        // 演奏支援開始ボタン
        Button(
            onClick = onStartSupportClick,
            enabled = uiState.canStart,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                text = "ガイドを開始",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        if (isDebugBuild && uiState.songData != null) {
            TextButton(
                onClick = onPreviewClick,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text(
                    text = "[Debug] プレビュー画面で検証",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * 横画面 (Landscape) および広幅画面向けの左右2カラムレイアウト
 */
@Composable
private fun HomeScreenWideContent(
    modifier: Modifier = Modifier,
    uiState: HomeUiState,
    isDebugBuild: Boolean = false,
    onSelectFileClick: () -> Unit,
    onFittingClick: () -> Unit,
    onStartSupportClick: () -> Unit,
    onPreviewClick: () -> Unit,
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
    onMidiMappingModeChange: (com.onigiri.keycue.model.MidiMappingMode) -> Unit,
    onManualRootChange: (com.onigiri.keycue.model.PitchClass) -> Unit,
    onManualScaleChange: (com.onigiri.keycue.model.ScaleType) -> Unit,
    onManualBaseOctaveChange: (Int) -> Unit,
    onShowSongInfoChange: (Boolean) -> Unit = {},
    onShowSeekBarChange: (Boolean) -> Unit = {},
    onShowPlaybackControlsChange: (Boolean) -> Unit = {},
    onShowLoopControlsChange: (Boolean) -> Unit = {},
    onShowSpeedControlChange: (Boolean) -> Unit = {},
    onShowNoteLeadTimeControlChange: (Boolean) -> Unit = {},
    onShowCircleLeadTimeControlChange: (Boolean) -> Unit = {},
    onDismissError: () -> Unit
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.Top
    ) {
        // 左カラム: 楽曲表示 & アクションボタン（固定表示）
        Column(
            modifier = Modifier
                .weight(0.44f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (uiState.errorMessage != null) {
                    ErrorMessageCard(
                        errorMessage = uiState.errorMessage,
                        onDismiss = onDismissError
                    )
                }

                SongInfoCard(
                    songTitle = uiState.selectedFileName ?: uiState.songTitle,
                    songFormat = uiState.songFormat,
                    durationMs = uiState.durationMs,
                    noteCount = uiState.noteCount,
                    isLoading = uiState.isLoading,
                    onSelectFileClick = onSelectFileClick,
                    compact = true
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onStartSupportClick,
                    enabled = uiState.canStart,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = "ガイドを開始",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (isDebugBuild && uiState.songData != null) {
                    TextButton(
                        onClick = onPreviewClick,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text(
                            text = "[Debug] プレビュー画面で検証",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }

        // 右カラム: 設定アコーディオンカード群（スクロール可能）
        Column(
            modifier = Modifier
                .weight(0.56f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SettingsAccordionSection(
                uiState = uiState,
                onFittingClick = onFittingClick,
                onRequestPermissionClick = onRequestPermissionClick,
                onSpeedDecrease = onSpeedDecrease,
                onSpeedIncrease = onSpeedIncrease,
                onNoteLeadTimeDecrease = onNoteLeadTimeDecrease,
                onNoteLeadTimeIncrease = onNoteLeadTimeIncrease,
                onApproachCircleLeadTimeDecrease = onApproachCircleLeadTimeDecrease,
                onApproachCircleLeadTimeIncrease = onApproachCircleLeadTimeIncrease,
                onCountdownChange = onCountdownChange,
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
                onNoteColorBottomChange = onNoteColorBottomChange,
                onMidiMappingModeChange = onMidiMappingModeChange,
                onManualRootChange = onManualRootChange,
                onScaleChange = onManualScaleChange,
                onManualBaseOctaveChange = onManualBaseOctaveChange,
                onShowSongInfoChange = onShowSongInfoChange,
                onShowSeekBarChange = onShowSeekBarChange,
                onShowPlaybackControlsChange = onShowPlaybackControlsChange,
                onShowLoopControlsChange = onShowLoopControlsChange,
                onShowSpeedControlChange = onShowSpeedControlChange,
                onShowNoteLeadTimeControlChange = onShowNoteLeadTimeControlChange,
                onShowCircleLeadTimeControlChange = onShowCircleLeadTimeControlChange
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    MaterialTheme {
        HomeScreenContent(
            uiState = HomeUiState(
                songTitle = "Canon.mid",
                durationMs = 222000L,
                noteCount = 127,
                speed = 1.0f,
                noteLeadTimeMs = PlaybackConfig.DEFAULT_NOTE_LEAD_TIME_MS,
                approachCircleLeadTimeMs = PlaybackConfig.DEFAULT_APPROACH_CIRCLE_LEAD_TIME_MS,
                countdownMs = 3000L,
                fitConfigured = true,
                canStart = true
            )
        )
    }
}

@Preview(showBackground = true, device = "spec:parent=pixel_5,orientation=landscape")
@Composable
fun HomeScreenLandscapePreview() {
    MaterialTheme {
        HomeScreenContent(
            uiState = HomeUiState(
                songTitle = "Canon.mid",
                durationMs = 222000L,
                noteCount = 127,
                speed = 1.0f,
                noteLeadTimeMs = PlaybackConfig.DEFAULT_NOTE_LEAD_TIME_MS,
                approachCircleLeadTimeMs = PlaybackConfig.DEFAULT_APPROACH_CIRCLE_LEAD_TIME_MS,
                countdownMs = 3000L,
                fitConfigured = true,
                canStart = true
            )
        )
    }
}
