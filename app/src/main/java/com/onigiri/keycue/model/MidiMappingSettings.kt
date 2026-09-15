package com.onigiri.keycue.model

/**
 * MIDIマッピングモード（自動 / 手動）
 */
enum class MidiMappingMode {
    AUTO,
    MANUAL
}

/**
 * ユーザーが設定するMIDIマッピング設定モデル。
 *
 * @param mode マッピングモード (AUTO または MANUAL)
 * @param manualRoot 手動設定時のRoot音
 * @param manualScale 手動設定時のスケール種別
 * @param manualBaseOctave 手動設定時の開始オクターブ（例: 4）
 */
data class MidiMappingSettings(
    val mode: MidiMappingMode = MidiMappingMode.AUTO,
    val manualRoot: PitchClass = PitchClass.C,
    val manualScale: ScaleType = ScaleType.MAJOR,
    val manualBaseOctave: Int = 4
)
