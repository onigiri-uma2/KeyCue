package com.onigiri.keycue.ui.home.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * 再生速度・先読み時間・事前ハイライト・開始カウントダウンの設定コンテンツ。
 */
@Composable
fun PlaybackConfigContent(
    speed: Float,
    leadTimeMs: Long,
    highlightTimeMs: Long,
    countdownMs: Long,
    onSpeedDecrease: () -> Unit,
    onSpeedIncrease: () -> Unit,
    onLeadTimeDecrease: () -> Unit,
    onLeadTimeIncrease: () -> Unit,
    onHighlightTimeDecrease: () -> Unit,
    onHighlightTimeIncrease: () -> Unit,
    onCountdownChange: (Long) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // 再生速度
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "再生速度",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                FilledTonalIconButton(
                    onClick = onSpeedDecrease,
                    enabled = speed > 0.25f
                ) {
                    Text(text = "－", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }

                val percent = (speed * 100).toInt()
                Text(
                    text = "$percent%",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(96.dp)
                )

                FilledTonalIconButton(
                    onClick = onSpeedIncrease,
                    enabled = speed < 2.0f
                ) {
                    Text(text = "＋", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
        }

        // 先読み時間
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "先読み時間",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                FilledTonalIconButton(
                    onClick = onLeadTimeDecrease,
                    enabled = leadTimeMs > 300L
                ) {
                    Text(text = "－", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }

                Text(
                    text = "${leadTimeMs}ms",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(96.dp)
                )

                FilledTonalIconButton(
                    onClick = onLeadTimeIncrease,
                    enabled = leadTimeMs < 2000L
                ) {
                    Text(text = "＋", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
        }

        // 事前ハイライト時間
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "事前ハイライト時間",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                FilledTonalIconButton(
                    onClick = onHighlightTimeDecrease,
                    enabled = highlightTimeMs > 100L
                ) {
                    Text(text = "－", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }

                Text(
                    text = "${highlightTimeMs}ms",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(96.dp)
                )

                FilledTonalIconButton(
                    onClick = onHighlightTimeIncrease,
                    enabled = highlightTimeMs < 1000L
                ) {
                    Text(text = "＋", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
        }

        // カウントダウン
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "開始カウントダウン",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val presets = listOf(0L to "なし", 1000L to "1秒", 3000L to "3秒", 5000L to "5秒")
                for ((ms, label) in presets) {
                    val isSelected = countdownMs == ms
                    FilledTonalButton(
                        onClick = { onCountdownChange(ms) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = if (isSelected) {
                            ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            ButtonDefaults.filledTonalButtonColors()
                        }
                    ) {
                        Text(text = label, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}
