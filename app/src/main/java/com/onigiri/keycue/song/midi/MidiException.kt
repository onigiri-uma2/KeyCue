package com.onigiri.keycue.song.midi

/**
 * MIDI処理に関する基底例外クラス。
 */
sealed class MidiException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * 不正なMIDIファイル形式の例外（ヘッダの欠損、途中で破損、VLQ異常など）。
 */
class InvalidMidiException(message: String, cause: Throwable? = null) : MidiException(message, cause)

/**
 * 未対応のMIDIファイル形式の例外（Format 2、SMPTEタイムコード分割など）。
 */
class UnsupportedMidiException(message: String, cause: Throwable? = null) : MidiException(message, cause)

/**
 * MIDI解析中に発生した一般的なエラーの例外。
 */
class MidiParseException(message: String, cause: Throwable? = null) : MidiException(message, cause)
