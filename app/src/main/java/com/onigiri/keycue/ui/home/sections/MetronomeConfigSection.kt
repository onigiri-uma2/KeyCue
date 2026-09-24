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
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.onigiri.keycue.model.BeatSubdivision
import com.onigiri.keycue.model.MetronomeConfig

/**
 * ホーム画面のメトロノーム設定セクションコンテンツ。
 *
 * @param config 現在のメトロノーム設定
 * @param onEnabledChange 有効・無効の変更
 * @param onBpmChange BPMの変更
 * @param onBeatsPerBarChange 1小節の拍数（3/4 または 4/4）の変更
 * @param onSubdivisionChange クリック分割単位（4分または8分）の変更
 * @param onAccentEnabledChange 小節頭アクセントの変更
 * @param onVolumeChange 音量の変更（0〜100%）
 * @param onBeatOffsetChange 拍位置オフセットの変更（ms）
 */
@Composable
fun MetronomeConfigContent(
    config: MetronomeConfig,
    onEnabledChange: (Boolean) -> Unit,
    onBpmChange: (Int) -> Unit,
    onBeatsPerBarChange: (Int) -> Unit,
    onSubdivisionChange: (BeatSubdivision) -> Unit,
    onAccentEnabledChange: (Boolean) -> Unit,
    onVolumeChange: (Int) -> Unit,
    onBeatOffsetChange: (Long) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // 1. メトロノーム ON / OFF
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "メトロノーム機能",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "演奏中に一定の拍（クリック音）を耳で確認",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = config.enabled,
                onCheckedChange = onEnabledChange
            )
        }

        // 2. テンポ (BPM)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "テンポ (BPM)",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                FilledTonalIconButton(
                    onClick = { onBpmChange(config.bpm - MetronomeConfig.BPM_STEP) },
                    enabled = config.bpm > MetronomeConfig.MIN_BPM
                ) {
                    Text(text = "－", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }

                Text(
                    text = "${config.bpm}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(96.dp)
                )

                FilledTonalIconButton(
                    onClick = { onBpmChange(config.bpm + MetronomeConfig.BPM_STEP) },
                    enabled = config.bpm < MetronomeConfig.MAX_BPM
                ) {
                    Text(text = "＋", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
        }

        // 3. 拍子 (3/4, 4/4)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "拍子",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(3, 4).forEach { beats ->
                    val isSelected = config.beatsPerBar == beats
                    FilledTonalButton(
                        onClick = { onBeatsPerBarChange(beats) },
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
                        Text(
                            text = "${beats}/4 拍子",
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }

        // 4. クリック間隔（4分音符 / 8分音符）
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "クリック間隔",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    BeatSubdivision.QUARTER to "4分音符",
                    BeatSubdivision.EIGHTH to "8分音符"
                ).forEach { (subdivision, label) ->
                    val isSelected = config.subdivision == subdivision
                    FilledTonalButton(
                        onClick = { onSubdivisionChange(subdivision) },
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
                        Text(
                            text = label,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }

        // 5. 小節頭アクセント
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "小節頭アクセント音",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "1拍目を高音（ピッ）で強調する",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = config.accentEnabled,
                onCheckedChange = onAccentEnabledChange
            )
        }

        // 6. 音量 (0〜100%)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "クリック音量",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${config.volumePercent}%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Slider(
                value = config.volumePercent.toFloat(),
                onValueChange = { onVolumeChange(it.toInt()) },
                valueRange = MetronomeConfig.MIN_VOLUME_PERCENT.toFloat()..MetronomeConfig.MAX_VOLUME_PERCENT.toFloat(),
                modifier = Modifier.fillMaxWidth()
            )
        }

        // 7. 拍位置オフセット (-2000ms〜+2000ms)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "拍位置オフセット",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "楽曲タイミングとクリック音の発音タイミングを微調整（±10ms刻み）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                FilledTonalIconButton(
                    onClick = { onBeatOffsetChange(config.beatOffsetMs - MetronomeConfig.BEAT_OFFSET_STEP_MS) },
                    enabled = config.beatOffsetMs > MetronomeConfig.MIN_BEAT_OFFSET_MS
                ) {
                    Text(text = "－", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }

                val offsetText = if (config.beatOffsetMs > 0) "+${config.beatOffsetMs}ms" else "${config.beatOffsetMs}ms"
                Text(
                    text = offsetText,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(120.dp)
                )

                FilledTonalIconButton(
                    onClick = { onBeatOffsetChange(config.beatOffsetMs + MetronomeConfig.BEAT_OFFSET_STEP_MS) },
                    enabled = config.beatOffsetMs < MetronomeConfig.MAX_BEAT_OFFSET_MS
                ) {
                    Text(text = "＋", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
