package com.onigiri.keycue.ui.home.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.onigiri.keycue.model.VisualConfig
import com.onigiri.keycue.ui.home.CheckMarkIcon
import java.util.Locale
import kotlin.math.roundToInt

/**
 * ガイド円サイズ、各種表示スイッチ、カラーパレット選択の設定コンテンツ。
 */
@Composable
fun VisualConfigContent(
    visualConfig: VisualConfig,
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
    onNoteColorBottomChange: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // ガイド円サイズスライダー (2.0% 〜 8.0%)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ガイド円の大きさ",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val percentStr = String.format(Locale.US, "%.1f", visualConfig.guideRadiusRatio * 100)
                Text(
                    text = "$percentStr%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Slider(
                value = visualConfig.guideRadiusRatio,
                onValueChange = { onGuideRadiusChange(it) },
                valueRange = VisualConfig.MIN_GUIDE_RADIUS_RATIO..VisualConfig.MAX_GUIDE_RADIUS_RATIO,
                steps = 59 // 0.1%刻み
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

        // スイッチ項目群（ガイドラベル、落下ノート、タイミングサークル、連打バッジ、和音リンク、和音ハロー、ジャスト演出）
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SettingSwitchRow(
                label = "ガイドラベル表示",
                description = "通常はガイド番号、MIDIではキー配置音を表示します",
                checked = visualConfig.showGuideLabels,
                onCheckedChange = onShowGuideLabelsChange
            )
            SettingSwitchRow(
                label = "落下ノート表示",
                checked = visualConfig.showFallingNotes,
                onCheckedChange = onShowFallingNotesChange
            )
            SettingSwitchRow(
                label = "タイミングサークル表示 (縮小円)",
                checked = visualConfig.showApproachCircles,
                onCheckedChange = onShowApproachCirclesChange
            )
            SettingSwitchRow(
                label = "連打カウントバッジ表示 (残り打数)",
                checked = visualConfig.showRepeatCountBadge,
                onCheckedChange = onShowRepeatCountBadgeChange
            )
            SettingSwitchRow(
                label = "和音リンク",
                description = "同時に押すキーを線でつなぎます",
                checked = visualConfig.showChordLinks,
                onCheckedChange = onShowChordLinksChange
            )
            SettingSwitchRow(
                label = "和音ハロー",
                description = "同時押しのキー全体を薄い枠で囲みます",
                checked = visualConfig.showChordHalos,
                onCheckedChange = onShowChordHalosChange
            )

            // 和音線の太さスライダー (0.5 dp 〜 6.0 dp、0.5dp刻み)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "線の太さ",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Chord Link / Halo の線の太さを調整します",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                    val widthStr = String.format(Locale.US, "%.1f dp", visualConfig.chordStrokeWidthDp)
                    Text(
                        text = widthStr,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Slider(
                    value = visualConfig.chordStrokeWidthDp,
                    onValueChange = {
                        val rounded = ((it * 2f).roundToInt() / 2f).coerceIn(
                            VisualConfig.MIN_CHORD_STROKE_WIDTH_DP,
                            VisualConfig.MAX_CHORD_STROKE_WIDTH_DP
                        )
                        onChordStrokeWidthChange(rounded)
                    },
                    valueRange = VisualConfig.MIN_CHORD_STROKE_WIDTH_DP..VisualConfig.MAX_CHORD_STROKE_WIDTH_DP,
                    steps = 10
                )
            }

            // 和音線の濃さスライダー (20 % 〜 100 %、5%刻み)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "線の濃さ",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Chord Link / Halo の線の濃さを調整します",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                    Text(
                        text = "${visualConfig.chordStrokeAlphaPercent} %",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Slider(
                    value = visualConfig.chordStrokeAlphaPercent.toFloat(),
                    onValueChange = {
                        val rounded = ((it / 5f).roundToInt() * 5).coerceIn(
                            VisualConfig.MIN_CHORD_STROKE_ALPHA_PERCENT,
                            VisualConfig.MAX_CHORD_STROKE_ALPHA_PERCENT
                        )
                        onChordStrokeAlphaChange(rounded)
                    },
                    valueRange = VisualConfig.MIN_CHORD_STROKE_ALPHA_PERCENT.toFloat()..VisualConfig.MAX_CHORD_STROKE_ALPHA_PERCENT.toFloat(),
                    steps = 15
                )
            }

            // 和音ハロー塗りの濃さスライダー (10 % 〜 50 %、5%刻み)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "塗りの濃さ",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "和音ハロー内側の塗りの濃さを調整します",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                    Text(
                        text = "${visualConfig.chordHaloFillAlphaPercent} %",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Slider(
                    value = visualConfig.chordHaloFillAlphaPercent.toFloat(),
                    onValueChange = {
                        val rounded = ((it / 5f).roundToInt() * 5).coerceIn(
                            VisualConfig.MIN_CHORD_HALO_FILL_ALPHA_PERCENT,
                            VisualConfig.MAX_CHORD_HALO_FILL_ALPHA_PERCENT
                        )
                        onChordHaloFillAlphaChange(rounded)
                    },
                    valueRange = VisualConfig.MIN_CHORD_HALO_FILL_ALPHA_PERCENT.toFloat()..VisualConfig.MAX_CHORD_HALO_FILL_ALPHA_PERCENT.toFloat(),
                    steps = 7
                )
            }

            SettingSwitchRow(
                label = "ジャストタイミング演出 (発光)",
                checked = visualConfig.showJustEffect,
                onCheckedChange = onShowJustEffectChange
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

        // ガイド色
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "ガイド枠色",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ColorPaletteRow(
                selectedColor = visualConfig.guideColor,
                onColorSelect = onGuideColorChange
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

        // ノート色（上段・中段・下段）
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "ノート色（段別）",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold
            )

            // 上段
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "上段 (キー 0..4)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ColorPaletteRow(
                    selectedColor = visualConfig.noteColorTop,
                    onColorSelect = onNoteColorTopChange
                )
            }

            // 中段
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "中段 (キー 5..9)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ColorPaletteRow(
                    selectedColor = visualConfig.noteColorMiddle,
                    onColorSelect = onNoteColorMiddleChange
                )
            }

            // 下段
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "下段 (キー 10..14)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ColorPaletteRow(
                    selectedColor = visualConfig.noteColorBottom,
                    onColorSelect = onNoteColorBottomChange
                )
            }
        }
    }
}

/**
 * スイッチ付き設定行
 */
@Composable
fun SettingSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    description: String? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .padding(end = 8.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

/**
 * プリセットカラーパレット（8色チップ）選択行
 */
@Composable
fun ColorPaletteRow(
    selectedColor: Int,
    onColorSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (colorInt in VisualConfig.PRESET_COLORS) {
            val isSelected = (selectedColor == colorInt)
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color(colorInt))
                    .border(
                        width = if (isSelected) 3.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.5f),
                        shape = CircleShape
                    )
                    .clickable { onColorSelect(colorInt) },
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    CheckMarkIcon(
                        color = if (colorInt == VisualConfig.DEFAULT_GUIDE_COLOR || colorInt == 0xFFFFEB3B.toInt()) Color.Black else Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
