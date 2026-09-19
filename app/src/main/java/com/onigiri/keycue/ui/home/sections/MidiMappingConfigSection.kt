package com.onigiri.keycue.ui.home.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.onigiri.keycue.model.MidiMappingMode
import com.onigiri.keycue.model.MidiMappingSettings
import com.onigiri.keycue.model.PitchClass
import com.onigiri.keycue.model.ResolvedMidiMapping
import com.onigiri.keycue.model.ScaleType
import com.onigiri.keycue.song.midi.MidiKeyMapper
import java.util.Locale

/**
 * MIDIマッピング設定コンテンツ（AUTO/MANUAL切り替え、Root音、スケール、オクターブ、キー配置音）。
 */
@Composable
fun MidiMappingConfigContent(
    midiMappingSettings: MidiMappingSettings,
    resolvedMapping: ResolvedMidiMapping?,
    onModeChange: (MidiMappingMode) -> Unit,
    onRootChange: (PitchClass) -> Unit,
    onScaleChange: (ScaleType) -> Unit,
    onOctaveChange: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // モード選択（AUTO / MANUAL）
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "マッピングモード",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val isAuto = midiMappingSettings.mode == MidiMappingMode.AUTO
                FilledTonalButton(
                    onClick = { onModeChange(MidiMappingMode.AUTO) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = if (isAuto) {
                        ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        ButtonDefaults.filledTonalButtonColors()
                    }
                ) {
                    Text(text = "自動 (AUTO)", style = MaterialTheme.typography.labelMedium)
                }

                FilledTonalButton(
                    onClick = { onModeChange(MidiMappingMode.MANUAL) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = if (!isAuto) {
                        ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        ButtonDefaults.filledTonalButtonColors()
                    }
                ) {
                    Text(text = "手動 (MANUAL)", style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

        // 判定・適用中マッピングのサマリー
        if (resolvedMapping != null) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val label = if (midiMappingSettings.mode == MidiMappingMode.AUTO) {
                    "自動判定結果"
                } else {
                    "現在のマッピング"
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${resolvedMapping.root.displayName} ${resolvedMapping.scale.displayName} / Octave ${resolvedMapping.baseOctave}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "有効ノート",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val percentStr = String.format(Locale.US, "%.1f", resolvedMapping.mappedPercentage)
                    Text(
                        text = "${resolvedMapping.mappedEventCount} / ${resolvedMapping.totalEventCount} ($percentStr%)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // MANUAL用セレクタ群
        if (midiMappingSettings.mode == MidiMappingMode.MANUAL) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

            // Root選択 (12音: 6列×2行)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Root音",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val pitchClasses = PitchClass.entries
                val row1 = pitchClasses.subList(0, 6)
                val row2 = pitchClasses.subList(6, 12)

                for (row in listOf(row1, row2)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        for (pitch in row) {
                            val isSelected = midiMappingSettings.manualRoot == pitch
                            FilledTonalButton(
                                onClick = { onRootChange(pitch) },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(6.dp),
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
                                    text = pitch.displayName,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            // Scale選択
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "スケール",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (scale in ScaleType.entries) {
                        val isSelected = midiMappingSettings.manualScale == scale
                        FilledTonalButton(
                            onClick = { onScaleChange(scale) },
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
                            Text(text = scale.displayName, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            // 開始オクターブ選択 (2..6)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "開始オクターブ",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    for (octave in 2..6) {
                        val isSelected = midiMappingSettings.manualBaseOctave == octave
                        FilledTonalButton(
                            onClick = { onOctaveChange(octave) },
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
                            Text(text = octave.toString(), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }

        // ミニ鍵盤グリッド表示
        val noteNames = resolvedMapping?.getFormattedNoteNames()
            ?: MidiKeyMapper.createMapping(
                midiMappingSettings.manualRoot,
                midiMappingSettings.manualScale,
                midiMappingSettings.manualBaseOctave
            )?.map { PitchClass.formatMidiNote(it) }

        val profile = com.onigiri.keycue.profile.GameProfileRegistry.current
        if (noteNames != null && noteNames.size == profile.keyCount) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "キー配置音",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                for (rowIdx in 0 until profile.rowCount) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        for (colIdx in 0 until profile.columnCount) {
                            val keyIdx = rowIdx * profile.columnCount + colIdx
                            val noteName = noteNames[keyIdx]
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(30.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f))
                                    .border(
                                        width = 1.dp,
                                        color = MaterialTheme.colorScheme.outlineVariant,
                                        shape = RoundedCornerShape(6.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = noteName,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
