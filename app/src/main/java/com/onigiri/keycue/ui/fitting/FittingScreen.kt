package com.onigiri.keycue.ui.fitting

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.onigiri.keycue.fitting.DetectedPoint
import com.onigiri.keycue.model.FitProfile
import com.onigiri.keycue.model.NormalizedPoint
import kotlin.math.min
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FittingScreen(
    modifier: Modifier = Modifier,
    viewModel: FittingViewModel = viewModel(
        factory = FittingViewModel.provideFactory(LocalContext.current.applicationContext)
    ),
    onNavigateBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var zoomScale by rememberSaveable { mutableFloatStateOf(1.0f) }
    var panX by rememberSaveable { mutableFloatStateOf(0.0f) }
    var panY by rememberSaveable { mutableFloatStateOf(0.0f) }
    val panOffset = remember(panX, panY) { Offset(panX, panY) }

    // 保存完了時の画面戻り
    LaunchedEffect(uiState.isSavedSuccess) {
        if (uiState.isSavedSuccess) {
            android.widget.Toast.makeText(context, "キー位置を保存しました", android.widget.Toast.LENGTH_SHORT).show()
            onNavigateBack()
        }
    }

    // エラーメッセージの表示
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { error ->
            snackbarHostState.showSnackbar(error)
        }
    }

    // SAF / Activity Result API による画像選択
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.onImageSelected(uri, context.contentResolver)
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
                            text = "ボタン位置フィッティング",
                            fontWeight = FontWeight.Bold,
                            style = if (isWideLayout) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge
                        )
                    },
                    navigationIcon = {
                        TextButton(onClick = onNavigateBack) {
                            Text(text = "戻る", color = MaterialTheme.colorScheme.primary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        ) { innerPadding ->
            val previewArea = @Composable { boxModifier: Modifier ->
                FittingPreviewArea(
                    modifier = boxModifier,
                    uiState = uiState,
                    zoomScale = zoomScale,
                    panOffset = panOffset,
                    onSelectCorner = { cornerIndex: Int -> viewModel.selectCorner(cornerIndex) },
                    onCornerDelta = { corner: Int, dx: Float, dy: Float ->
                        viewModel.moveManualCornerDelta(corner, dx, dy)
                    },
                    onTransform = { zoomChange: Float, panChange: Offset ->
                        zoomScale = (zoomScale * zoomChange).coerceIn(1.0f, 3.5f)
                        if (zoomScale > 1.0f) {
                            val newPan = panOffset + panChange
                            panX = newPan.x
                            panY = newPan.y
                        } else {
                            panX = 0f
                            panY = 0f
                        }
                    }
                )
            }

            val controlsArea = @Composable { colModifier: Modifier ->
                FittingControlsPanel(
                    modifier = colModifier,
                    uiState = uiState,
                    zoomScale = zoomScale,
                    onPickImage = { imagePickerLauncher.launch("image/*") },
                    onStartManualAdjust = { viewModel.startManualAdjust() },
                    onUseSavedProfile = { viewModel.useSavedProfile() },
                    onDetectAndFit = { viewModel.detectAndFit() },
                    onSaveCurrentProfile = { viewModel.saveCurrentProfile() },
                    onFinishManualAdjust = { viewModel.finishManualAdjust() },
                    onSelectCorner = { cornerIndex: Int -> viewModel.selectCorner(cornerIndex) },
                    onNudge = { dx: Float, dy: Float -> viewModel.nudgeSelectedCorner(dx, dy) },
                    onZoomChange = { scale: Float ->
                        zoomScale = scale
                        if (scale == 1.0f) {
                            panX = 0f
                            panY = 0f
                        }
                    },
                    onToggleDebugView = { viewModel.toggleDebugView() }
                )
            }

            if (isWideLayout) {
                // 横画面 (Landscape / タブレット): 左プレビュー固定 ＋ 右操作パネルスクロール
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    // 左カラム: 画像プレビュー（固定配置）
                    previewArea(
                        Modifier
                            .weight(0.62f)
                            .fillMaxHeight()
                    )

                    // 右カラム: 操作パネル（縦スクロール可能）
                    controlsArea(
                        Modifier
                            .weight(0.38f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                    )
                }
            } else {
                // 縦画面 (Portrait / 狭幅): 上下1カラムレイアウト
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    // 縦向き画像選択時の警告
                    if (uiState.imageBitmap != null && !uiState.isLandscape) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer
                            )
                        ) {
                            Text(
                                text = "注意: 縦向き画像が選択されています。演奏画面は通常横向き（Landscape）です。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }

                    // 上部画像プレビュー（weight(1f) で固定）
                    previewArea(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // 下部操作パネル（スクロール可能）
                    controlsArea(
                        Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    )
                }
            }
        }
    }
}

/**
 * フィッティング画像プレビュー領域（共通コンポーネント）
 */
@Composable
private fun FittingPreviewArea(
    modifier: Modifier,
    uiState: FittingUiState,
    zoomScale: Float,
    panOffset: Offset,
    onSelectCorner: (Int) -> Unit,
    onCornerDelta: (Int, Float, Float) -> Unit,
    onTransform: (Float, Offset) -> Unit
) {
    Box(
        modifier = modifier
            .background(Color(0xFF121212), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        val showPreview = uiState.imageBitmap != null ||
                uiState.currentProfile != null ||
                uiState.savedProfile != null ||
                uiState.step is FittingStep.ManualAdjust ||
                uiState.step is FittingStep.Success

        if (showPreview) {
            val activeProfile = uiState.currentProfile
                ?: uiState.savedProfile
                ?: FitProfile.createDefaultTestProfile(uiState.isLandscape)

            FittingImagePreview(
                bitmap = uiState.imageBitmap,
                detectedPoints = uiState.detectedPoints,
                profile = activeProfile,
                isManualAdjust = uiState.step is FittingStep.ManualAdjust,
                manualTopLeft = uiState.manualTopLeft,
                manualTopRight = uiState.manualTopRight,
                manualBottomLeft = uiState.manualBottomLeft,
                manualBottomRight = uiState.manualBottomRight,
                selectedCorner = uiState.selectedCorner,
                onCornerSelected = onSelectCorner,
                onCornerDelta = onCornerDelta,
                zoomScale = zoomScale,
                panOffset = panOffset,
                onTransform = onTransform
            )
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(24.dp)
            ) {
                Text(
                    text = "演奏画面のスクリーンショットを\n選択してください",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = Color.Gray
                )
            }
        }
    }
}

/**
 * フィッティング操作パネル領域（共通コンポーネント）
 */
@Composable
private fun FittingControlsPanel(
    modifier: Modifier,
    uiState: FittingUiState,
    zoomScale: Float,
    onPickImage: () -> Unit,
    onStartManualAdjust: () -> Unit,
    onUseSavedProfile: () -> Unit,
    onDetectAndFit: () -> Unit,
    onSaveCurrentProfile: () -> Unit,
    onFinishManualAdjust: () -> Unit,
    onSelectCorner: (Int) -> Unit,
    onNudge: (Float, Float) -> Unit,
    onZoomChange: (Float) -> Unit,
    onToggleDebugView: () -> Unit
) {
    if (uiState.step is FittingStep.ManualAdjust) {
        ManualAdjustControlPanel(
            selectedCorner = uiState.selectedCorner,
            hasImage = uiState.imageBitmap != null,
            onSelectCorner = onSelectCorner,
            onNudge = onNudge,
            onPickImage = onPickImage,
            onConfirm = onFinishManualAdjust,
            zoomScale = zoomScale,
            onZoomChange = onZoomChange
        )
    } else {
        Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ステータス・信頼度表示
            when (val step = uiState.step) {
                is FittingStep.Detecting -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                        Text(
                            text = "ボタン位置を解析中...",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                is FittingStep.Success -> {
                    val percent = (step.confidence * 100).toInt()
                    val badgeColor = if (percent >= 80) Color(0xFF2E7D32) else Color(0xFFE65100)
                    Surface(
                        color = badgeColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, badgeColor)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "信頼度: $percent%",
                                fontWeight = FontWeight.Bold,
                                color = badgeColor,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }
                is FittingStep.Failed -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text = step.message,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
                is FittingStep.ManualAdjust -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Text(
                            text = "4隅の青いハンドル（0, 4, 10, 14）をドラッグしてキー位置に合わせてください。内部11点は自動で追従します。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
                else -> {}
            }

            // 操作ボタン群
            when (uiState.step) {
                is FittingStep.Idle -> {
                    Button(
                        onClick = onPickImage,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(text = "スクリーンショットを選択", fontWeight = FontWeight.Bold)
                    }

                    if (uiState.savedProfile != null) {
                        OutlinedButton(
                            onClick = onUseSavedProfile,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(text = "現在の保存位置を使用")
                        }
                    }

                    Text(
                        text = "ゲーム画面のスクリーンショットを選択すると、キーの位置を自動検出します。\n手動での微調整は、オーバーレイ側の「位置微調整」で行えます。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                is FittingStep.ImageSelected -> {
                    Button(
                        onClick = onDetectAndFit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(text = "自動検出を開始", fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = onPickImage,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(text = "画像再選択")
                    }
                }

                is FittingStep.Detecting -> {
                    // 解析中は操作ボタン非活性
                }

                is FittingStep.Success -> {
                    Button(
                        onClick = onSaveCurrentProfile,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "この位置を保存して適用",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "キー位置が検出されました。確定後、プレイ中にオーバーレイの「位置微調整」でいつでも微調整できます。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (uiState.imageBitmap != null) {
                            OutlinedButton(
                                onClick = onDetectAndFit,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(text = "再解析")
                            }
                        }

                        OutlinedButton(
                            onClick = onPickImage,
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(text = if (uiState.imageBitmap != null) "画像再選択" else "画像を選択")
                        }
                    }
                }

                is FittingStep.Failed -> {
                    Button(
                        onClick = onPickImage,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(text = "別の画像を選択する", fontWeight = FontWeight.Bold)
                    }

                    Text(
                        text = "ボタン位置を自動検出できませんでした。より鮮明なゲーム画面のスクリーンショットを選択してください。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )

                    if (uiState.imageBitmap != null) {
                        OutlinedButton(
                            onClick = onDetectAndFit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(text = "再解析を試す")
                        }
                    }
                }

                is FittingStep.ManualAdjust -> {
                    Button(
                        onClick = onFinishManualAdjust,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(text = "完了", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // デバッグ表示トグル（開発用）
            TextButton(
                onClick = onToggleDebugView,
                modifier = Modifier.padding(top = 2.dp)
            ) {
                Text(text = if (uiState.showDebugView) "デバッグ情報を非表示" else "デバッグ情報を表示", fontSize = 12.sp)
            }

            if (uiState.showDebugView) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "画像サイズ: ${uiState.imageWidth} x ${uiState.imageHeight}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "検出候補点数: ${uiState.detectedPoints.size}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "画面方向: ${if (uiState.isLandscape) "Landscape" else "Portrait"}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        uiState.currentProfile?.let { prof ->
                            Text(
                                text = "キー半径比率: ${"%.4f".format(prof.keyRadiusRatio)}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * スクリーンショットとキーオーバーレイ / 手動ドラッグハンドルを描画するコンポーザブル。
 * ピンチズーム＆パン操作をサポートし、小さなキーでも拡大して精密に微調整可能。
 */
@Composable
private fun FittingImagePreview(
    bitmap: Bitmap?,
    detectedPoints: List<DetectedPoint>,
    profile: FitProfile?,
    isManualAdjust: Boolean,
    manualTopLeft: NormalizedPoint,
    manualTopRight: NormalizedPoint,
    manualBottomLeft: NormalizedPoint,
    manualBottomRight: NormalizedPoint,
    selectedCorner: Int,
    onCornerSelected: (Int) -> Unit,
    onCornerDelta: (cornerIndex: Int, deltaNormX: Float, deltaNormY: Float) -> Unit,
    zoomScale: Float,
    panOffset: Offset,
    onTransform: (zoomChange: Float, panChange: Offset) -> Unit
) {
    val textMeasurer = rememberTextMeasurer()

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .pointerInput(isManualAdjust) {
                detectTransformGestures { _, pan, zoom, _ ->
                    onTransform(zoom, pan)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        val containerWidth = constraints.maxWidth.toFloat()
        val containerHeight = constraints.maxHeight.toFloat()

        // スクリーンショット未選択時は 16:9 (1920x1080) を仮想キャンバスサイズとする
        val bmpW = bitmap?.width?.toFloat() ?: 1920f
        val bmpH = bitmap?.height?.toFloat() ?: 1080f

        // アスペクト比を維持した実際の描画サイズ
        val scale = min(containerWidth / bmpW, containerHeight / bmpH)
        val drawnWidth = bmpW * scale
        val drawnHeight = bmpH * scale

        val drawnWidthDp = with(LocalDensity.current) { drawnWidth.toDp() }
        val drawnHeightDp = with(LocalDensity.current) { drawnHeight.toDp() }

        // 画像枠と完全に一致するコンテナBox（ズーム・パンを適用）
        Box(
            modifier = Modifier
                .graphicsLayer {
                    scaleX = zoomScale
                    scaleY = zoomScale
                    translationX = panOffset.x
                    translationY = panOffset.y
                }
                .size(drawnWidthDp, drawnHeightDp)
        ) {
            // 1. 背景画像 または 仮想キャンバス
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "演奏画面スクリーンショット",
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors = listOf(Color(0xFF222B3B), Color(0xFF101520))
                            )
                        )
                        .border(1.5.dp, Color(0x66FFFFFF), RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Text(
                        text = "仮想キャンバス (16:9 演奏画面想定)",
                        color = Color(0x99FFFFFF),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }
            }

            // 2. 候補点 & キーのオーバーレイ描画 Canvas
            Canvas(
                modifier = Modifier.fillMaxSize()
            ) {
                val cWidth = size.width
                val cHeight = size.height

                // 検出された候補点（薄いシアンの点）
                for (p in detectedPoints) {
                    val px = (p.x / bmpW) * cWidth
                    val py = (p.y / bmpH) * cHeight
                    drawCircle(
                        color = Color(0xCC00E5FF),
                        radius = 4.dp.toPx(),
                        center = Offset(px, py)
                    )
                }

                // キーの描画（円 + 番号）
                profile?.let { prof ->
                    val radiusPx = cHeight * prof.keyRadiusRatio

                    for ((index, center) in prof.keyCenters.withIndex()) {
                        val cx = center.x * cWidth
                        val cy = center.y * cHeight

                        // 半透明の白塗り
                        drawCircle(
                            color = Color(0x33FFFFFF),
                            radius = radiusPx,
                            center = Offset(cx, cy)
                        )

                        // ストローク枠線（エメラルドグリーンまたは白）
                        val strokeColor = if (isManualAdjust && (index == 0 || index == 4 || index == 10 || index == 14)) {
                            Color(0xFF00E676) // コーナーキーは緑色で強調
                        } else {
                            Color(0xE6FFFFFF)
                        }

                        drawCircle(
                            color = strokeColor,
                            radius = radiusPx,
                            center = Offset(cx, cy),
                            style = Stroke(width = 2.5.dp.toPx())
                        )

                        // キー番号描画 (0..14)
                        val textLayout = textMeasurer.measure(
                            text = index.toString(),
                            style = TextStyle(
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        drawText(
                            textLayoutResult = textLayout,
                            topLeft = Offset(
                                cx - textLayout.size.width / 2.0f,
                                cy - textLayout.size.height / 2.0f
                            )
                        )
                    }
                }
            }

            // 3. 手動補正モード時の 4隅ドラッグハンドル
            if (isManualAdjust) {
                val corners = listOf(
                    0 to manualTopLeft,
                    1 to manualTopRight,
                    2 to manualBottomLeft,
                    3 to manualBottomRight
                )

                for ((cornerIndex, point) in corners) {
                    val isSelected = selectedCorner == cornerIndex
                    val handleSizeDp = if (isSelected) 52.dp else 46.dp
                    val handleHalfPx = with(LocalDensity.current) { (handleSizeDp / 2).toPx() }

                    val handleCenterPxX = point.x * drawnWidth
                    val handleCenterPxY = point.y * drawnHeight

                    val keyNumber = when (cornerIndex) {
                        0 -> 0
                        1 -> 4
                        2 -> 10
                        3 -> 14
                        else -> 0
                    }

                    val borderColor = if (isSelected) Color(0xFFFFD600) else Color(0xFF00E5FF)
                    val borderWidth = if (isSelected) 3.5.dp else 2.5.dp
                    val bgColor = if (isSelected) Color(0xFF2979FF).copy(alpha = 0.85f) else Color(0xFF2979FF).copy(alpha = 0.5f)

                    Box(
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    (handleCenterPxX - handleHalfPx).roundToInt(),
                                    (handleCenterPxY - handleHalfPx).roundToInt()
                                )
                            }
                            .size(handleSizeDp)
                            .background(bgColor, CircleShape)
                            .border(borderWidth, borderColor, CircleShape)
                            .clickable { onCornerSelected(cornerIndex) }
                            .pointerInput(cornerIndex, zoomScale) {
                                detectDragGestures(
                                    onDragStart = { onCornerSelected(cornerIndex) }
                                ) { change, dragAmount ->
                                    change.consume()
                                    val (deltaX, deltaY) = normalizedDragDelta(
                                        dragAmount.x,
                                        dragAmount.y,
                                        drawnWidth,
                                        drawnHeight,
                                        zoomScale
                                    )
                                    onCornerDelta(cornerIndex, deltaX, deltaY)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        // ハンドル中央の番号表示
                        Text(
                            text = keyNumber.toString(),
                            color = Color.White,
                            fontSize = if (isSelected) 15.sp else 13.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }
    }
}

/**
 * 手動微調整モード専用の操作パネル。
 * 選択中コーナーの切り替えタブ、十字キー微動ボタン、ズーム切り替え、確定ボタンを提供。
 */
@Composable
private fun ManualAdjustControlPanel(
    selectedCorner: Int,
    hasImage: Boolean,
    onSelectCorner: (Int) -> Unit,
    onNudge: (deltaNormX: Float, deltaNormY: Float) -> Unit,
    onPickImage: () -> Unit,
    onConfirm: () -> Unit,
    zoomScale: Float,
    onZoomChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 1. コーナー選択タブ
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val corners = listOf(
                0 to "0:左上",
                1 to "4:右上",
                2 to "10:左下",
                3 to "14:右下"
            )
            for ((index, label) in corners) {
                val isSelected = selectedCorner == index
                if (isSelected) {
                    Button(
                        onClick = { onSelectCorner(index) },
                        modifier = Modifier.weight(1f).height(38.dp),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        Text(text = label, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    OutlinedButton(
                        onClick = { onSelectCorner(index) },
                        modifier = Modifier.weight(1f).height(38.dp),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        Text(text = label, fontSize = 12.sp)
                    }
                }
            }
        }

        // 2. 十字微調整キー & ズーム切り替え
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ズーム切り替え (1.0x / 1.8x / 2.5x)
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(text = "表示倍率 (2本指ピンチも可)", fontSize = 11.sp, color = Color.Gray)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(1.0f to "1.0x", 1.8f to "1.8x", 2.5f to "2.5x").forEach { (scale, text) ->
                        val active = kotlin.math.abs(zoomScale - scale) < 0.15f
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier.clickable { onZoomChange(scale) }
                        ) {
                            Text(
                                text = text,
                                color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                fontSize = 12.sp,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            // 十字微動キー (Nudge: 約0.15%移動)
            val nudgeStep = 0.0015f
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // 上
                FilledTonalButton(
                    onClick = { onNudge(0f, -nudgeStep) },
                    modifier = Modifier.size(36.dp),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("▲", fontSize = 12.sp)
                }
                // 左右
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    FilledTonalButton(
                        onClick = { onNudge(-nudgeStep, 0f) },
                        modifier = Modifier.size(36.dp),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("◀", fontSize = 12.sp)
                    }
                    FilledTonalButton(
                        onClick = { onNudge(nudgeStep, 0f) },
                        modifier = Modifier.size(36.dp),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("▶", fontSize = 12.sp)
                    }
                }
                // 下
                FilledTonalButton(
                    onClick = { onNudge(0f, nudgeStep) },
                    modifier = Modifier.size(36.dp),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("▼", fontSize = 12.sp)
                }
            }
        }

        // 3. 画像選択 & 確定ボタン
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onPickImage,
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(text = if (hasImage) "画像再選択" else "画像を追加")
            }

            Button(
                onClick = onConfirm,
                modifier = Modifier
                    .weight(1.5f)
                    .height(44.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(text = "微調整を確定", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun FittingScreenPortraitPreview() {
    MaterialTheme {
        FittingScreen(
            onNavigateBack = {}
        )
    }
}

@Preview(showBackground = true, device = "spec:parent=pixel_5,orientation=landscape")
@Composable
fun FittingScreenLandscapePreview() {
    MaterialTheme {
        FittingScreen(
            onNavigateBack = {}
        )
    }
}


