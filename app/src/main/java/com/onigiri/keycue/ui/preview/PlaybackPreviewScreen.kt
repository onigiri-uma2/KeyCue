package com.onigiri.keycue.ui.preview

import android.content.res.Configuration
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.onigiri.keycue.playback.PlaybackState
import com.onigiri.keycue.playback.currentPositionMs
import com.onigiri.keycue.playback.isPlaying
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaybackPreviewScreen(
    modifier: Modifier = Modifier,
    viewModel: PlaybackPreviewViewModel = viewModel(
        factory = PlaybackPreviewViewModel.provideFactory(LocalContext.current)
    ),
    onNavigateBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()

    // 画面向きの取得（縦画面 / 横画面）
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // フレーム単位で同期する現在時刻とアクティブキー
    var currentFramePositionMs by remember { mutableLongStateOf(0L) }
    var activeKeys by remember { mutableStateOf<Set<Int>>(emptySet()) }

    // 再生中の滑らかなフレーム更新ループ
    LaunchedEffect(playbackState) {
        if (playbackState.isPlaying) {
            while (true) {
                withFrameMillis {
                    val pos = viewModel.engine.getCurrentPositionMs()
                    currentFramePositionMs = pos
                    val notes = viewModel.getScheduledNotes(pos)
                    activeKeys = notes.activeKeys
                }
            }
        } else {
            // 一時停止・停止・完了時は現在の状態に固定（停止・完了時はアクティブキーをクリア）
            currentFramePositionMs = playbackState.currentPositionMs
            if (playbackState is PlaybackState.Finished || playbackState is PlaybackState.Stopped) {
                activeKeys = emptySet()
            } else {
                val notes = viewModel.getScheduledNotes(currentFramePositionMs)
                activeKeys = notes.activeKeys
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "プレビュー再生",
                        fontWeight = FontWeight.Bold,
                        style = if (isLandscape) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    OutlinedIconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(if (isLandscape) 36.dp else 44.dp)
                    ) {
                        Text(text = "←", fontSize = if (isLandscape) 14.sp else 18.sp, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        if (isLandscape) {
            // 横画面用 左右2カラムレイアウト
            PlaybackPreviewLandscapeContent(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                uiState = uiState,
                playbackState = playbackState,
                currentPositionMs = currentFramePositionMs,
                activeKeys = activeKeys,
                onSeekTo = { targetMs ->
                    viewModel.seekTo(targetMs)
                    currentFramePositionMs = targetMs
                },
                onPlay = { viewModel.play() },
                onPause = { viewModel.pause() },
                onResume = { viewModel.resume() },
                onStop = { viewModel.stop() },
                onRestart = { viewModel.restart() },
                onSeekBack = { viewModel.seekBack() },
                onSpeedDecrease = { viewModel.adjustSpeed(-0.05f) },
                onSpeedIncrease = { viewModel.adjustSpeed(0.05f) }
            )
        } else {
            // 縦画面用 1カラムレイアウト
            PlaybackPreviewPortraitContent(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                uiState = uiState,
                playbackState = playbackState,
                currentPositionMs = currentFramePositionMs,
                activeKeys = activeKeys,
                onSeekTo = { targetMs ->
                    viewModel.seekTo(targetMs)
                    currentFramePositionMs = targetMs
                },
                onPlay = { viewModel.play() },
                onPause = { viewModel.pause() },
                onResume = { viewModel.resume() },
                onStop = { viewModel.stop() },
                onRestart = { viewModel.restart() },
                onSeekBack = { viewModel.seekBack() },
                onSpeedDecrease = { viewModel.adjustSpeed(-0.05f) },
                onSpeedIncrease = { viewModel.adjustSpeed(0.05f) }
            )
        }
    }
}

/**
 * 縦画面 (Portrait) レイアウト
 */
@Composable
private fun PlaybackPreviewPortraitContent(
    modifier: Modifier = Modifier,
    uiState: PlaybackPreviewUiState,
    playbackState: PlaybackState,
    currentPositionMs: Long,
    activeKeys: Set<Int>,
    onSeekTo: (Long) -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onRestart: () -> Unit,
    onSeekBack: () -> Unit,
    onSpeedDecrease: () -> Unit,
    onSpeedIncrease: () -> Unit
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // 上部: 楽曲情報カード
        SongHeaderCard(
            title = uiState.songTitle.ifEmpty { "楽曲が選択されていません" },
            currentPositionMs = currentPositionMs,
            durationMs = uiState.durationMs
        )

        // シークバー
        if (uiState.durationMs > 0L) {
            val sliderPos = (currentPositionMs.toFloat() / uiState.durationMs).coerceIn(0f, 1f)
            Slider(
                value = sliderPos,
                onValueChange = { ratio ->
                    onSeekTo((ratio * uiState.durationMs).toLong())
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        // 中央: 15キー配置 & カウントダウン表示
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Keypad15(
                activeKeys = activeKeys,
                modifier = Modifier.fillMaxWidth()
            )

            if (playbackState is PlaybackState.CountingDown) {
                CountdownBadge(count = playbackState.countNumber)
            }
        }

        // 下部: 再生コントロール & 速度
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            PlaybackControlBar(
                playbackState = playbackState,
                onPlay = onPlay,
                onPause = onPause,
                onResume = onResume,
                onStop = onStop,
                onRestart = onRestart,
                onSeekBack = onSeekBack
            )

            SpeedControlRow(
                speed = uiState.speed,
                onSpeedDecrease = onSpeedDecrease,
                onSpeedIncrease = onSpeedIncrease
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

/**
 * 横画面 (Landscape) レイアウト
 * ゲーム画面に模した、左側操作パネル ＋ 右側15キー演奏エリアの2カラム構成。
 */
@Composable
private fun PlaybackPreviewLandscapeContent(
    modifier: Modifier = Modifier,
    uiState: PlaybackPreviewUiState,
    playbackState: PlaybackState,
    currentPositionMs: Long,
    activeKeys: Set<Int>,
    onSeekTo: (Long) -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onRestart: () -> Unit,
    onSeekBack: () -> Unit,
    onSpeedDecrease: () -> Unit,
    onSpeedIncrease: () -> Unit
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左カラム: 操作・情報パネル (幅比率: 約40%)
        Column(
            modifier = Modifier
                .weight(0.40f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 楽曲情報
            SongHeaderCard(
                title = uiState.songTitle.ifEmpty { "楽曲が選択されていません" },
                currentPositionMs = currentPositionMs,
                durationMs = uiState.durationMs,
                compact = true
            )

            // シークバー
            if (uiState.durationMs > 0L) {
                val sliderPos = (currentPositionMs.toFloat() / uiState.durationMs).coerceIn(0f, 1f)
                Slider(
                    value = sliderPos,
                    onValueChange = { ratio ->
                        onSeekTo((ratio * uiState.durationMs).toLong())
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // 再生コントロール
            PlaybackControlBar(
                playbackState = playbackState,
                onPlay = onPlay,
                onPause = onPause,
                onResume = onResume,
                onStop = onStop,
                onRestart = onRestart,
                onSeekBack = onSeekBack,
                compact = true
            )

            // 再生速度調整
            SpeedControlRow(
                speed = uiState.speed,
                onSpeedDecrease = onSpeedDecrease,
                onSpeedIncrease = onSpeedIncrease,
                compact = true
            )
        }

        // 右カラム: 15キー演奏グリッド (幅比率: 約60%)
        Box(
            modifier = Modifier
                .weight(0.60f)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            Keypad15(
                activeKeys = activeKeys,
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .padding(vertical = 4.dp)
            )

            if (playbackState is PlaybackState.CountingDown) {
                CountdownBadge(count = playbackState.countNumber)
            }
        }
    }
}

/**
 * カウントダウンバッジ
 */
@Composable
private fun CountdownBadge(count: Int) {
    Box(
        modifier = Modifier
            .size(110.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f))
            .border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(24.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = count.toString(),
            fontSize = 60.sp,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

/**
 * 楽曲名・現在時刻・曲長ヘッダーカード
 */
@Composable
private fun SongHeaderCard(
    title: String,
    currentPositionMs: Long,
    durationMs: Long,
    compact: Boolean = false
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (compact) 10.dp else 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 6.dp)
        ) {
            Text(
                text = title,
                style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            val curStr = com.onigiri.keycue.playback.TimeFormatter.formatDuration(currentPositionMs)
            val durStr = com.onigiri.keycue.playback.TimeFormatter.formatDuration(durationMs)
            Text(
                text = "$curStr / $durStr",
                style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * 15キー (3段 × 5列) グリッド
 */
@Composable
private fun Keypad15(
    activeKeys: Set<Int>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        for (row in 0..2) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (col in 0..4) {
                    val keyIndex = row * 5 + col // 0..14
                    val displayNumber = keyIndex + 1 // 1..15
                    val isActive = activeKeys.contains(keyIndex)

                    KeypadButton(
                        number = displayNumber,
                        isActive = isActive,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/**
 * 個別の15キーボタン
 */
@Composable
private fun KeypadButton(
    number: Int,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    val targetBgColor = if (isActive) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    val targetTextColor = if (isActive) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    val targetBorderColor = if (isActive) {
        MaterialTheme.colorScheme.inversePrimary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }

    val animatedBg by animateColorAsState(
        targetValue = targetBgColor,
        animationSpec = tween(durationMillis = 80),
        label = "keyBg"
    )

    val animatedBorder by animateColorAsState(
        targetValue = targetBorderColor,
        animationSpec = tween(durationMillis = 80),
        label = "keyBorder"
    )

    val animatedScale by animateFloatAsState(
        targetValue = if (isActive) 1.06f else 1.0f,
        animationSpec = tween(durationMillis = 80),
        label = "keyScale"
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = animatedScale
                scaleY = animatedScale
            }
            .aspectRatio(1.15f)
            .shadow(
                elevation = if (isActive) 12.dp else 2.dp,
                shape = RoundedCornerShape(10.dp),
                spotColor = if (isActive) MaterialTheme.colorScheme.primary else Color.Transparent
            )
            .clip(RoundedCornerShape(10.dp))
            .background(animatedBg)
            .border(
                width = if (isActive) 3.5.dp else 1.dp,
                color = animatedBorder,
                shape = RoundedCornerShape(10.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = number.toString(),
            fontSize = 16.sp,
            fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Medium,
            color = targetTextColor
        )
    }
}

/**
 * 再生操作コントロールバー: ↶10s, ▶/⏸, ■, ↺
 */
@Composable
private fun PlaybackControlBar(
    playbackState: PlaybackState,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onRestart: () -> Unit,
    onSeekBack: () -> Unit,
    compact: Boolean = false
) {
    val playBtnSize = if (compact) 52.dp else 64.dp
    val normalBtnSize = if (compact) 42.dp else 52.dp

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 10秒巻き戻し (↶10s)
        FilledTonalIconButton(
            onClick = onSeekBack,
            modifier = Modifier.size(normalBtnSize)
        ) {
            Text(text = "↶10s", fontSize = if (compact) 12.sp else 14.sp, fontWeight = FontWeight.Bold)
        }

        // 再生 / 一時停止 (▶ / ⏸)
        when (playbackState) {
            is PlaybackState.Playing -> {
                FilledIconButton(
                    onClick = onPause,
                    modifier = Modifier.size(playBtnSize),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(text = "⏸", fontSize = if (compact) 22.sp else 26.sp, color = MaterialTheme.colorScheme.onPrimary)
                }
            }
            is PlaybackState.Paused -> {
                FilledIconButton(
                    onClick = onResume,
                    modifier = Modifier.size(playBtnSize),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(text = "▶", fontSize = if (compact) 22.sp else 26.sp, color = MaterialTheme.colorScheme.onPrimary)
                }
            }
            else -> {
                // Stopped, Finished, CountingDown
                FilledIconButton(
                    onClick = onPlay,
                    enabled = playbackState !is PlaybackState.CountingDown,
                    modifier = Modifier.size(playBtnSize),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(text = "▶", fontSize = if (compact) 22.sp else 26.sp, color = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }

        // 停止 (■)
        FilledTonalIconButton(
            onClick = onStop,
            modifier = Modifier.size(normalBtnSize)
        ) {
            Text(text = "■", fontSize = if (compact) 15.sp else 18.sp, fontWeight = FontWeight.Bold)
        }

        // リスタート (↺)
        FilledTonalIconButton(
            onClick = onRestart,
            modifier = Modifier.size(normalBtnSize)
        ) {
            Text(text = "↺", fontSize = if (compact) 17.sp else 20.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * 再生速度調整バー
 */
@Composable
private fun SpeedControlRow(
    speed: Float,
    onSpeedDecrease: () -> Unit,
    onSpeedIncrease: () -> Unit,
    compact: Boolean = false
) {
    val btnSize = if (compact) 32.dp else 36.dp

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Speed:",
            style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.width(8.dp))

        FilledTonalIconButton(
            onClick = onSpeedDecrease,
            enabled = speed > 0.25f,
            modifier = Modifier.size(btnSize)
        ) {
            Text(text = "－", fontWeight = FontWeight.Bold)
        }

        val percent = (speed * 100).toInt()
        Text(
            text = "$percent%",
            style = if (compact) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(if (compact) 54.dp else 64.dp)
        )

        FilledTonalIconButton(
            onClick = onSpeedIncrease,
            enabled = speed < 2.0f,
            modifier = Modifier.size(btnSize)
        ) {
            Text(text = "＋", fontWeight = FontWeight.Bold)
        }
    }
}


