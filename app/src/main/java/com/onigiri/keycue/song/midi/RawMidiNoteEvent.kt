package com.onigiri.keycue.song.midi

/**
 * MIDIファイルから抽出された生のNote On演奏イベント。
 *
 * @param timeMs イベント発生時刻（ミリ秒）
 * @param midiNote MIDIノート番号 (0..127)
 * @param velocity ベロシティ (1..127)
 * @param channel MIDIチャネル (0-indexed, 0..15)
 */
data class RawMidiNoteEvent(
    val timeMs: Long,
    val midiNote: Int,
    val velocity: Int,
    val channel: Int
) {
    /**
     * General MIDIにおけるパーカッションチャネル（チャネル10、0-indexedで9）かどうか。
     */
    val isPercussion: Boolean
        get() = channel == PERCUSSION_CHANNEL

    companion object {
        /** General MIDIのPercussion Channel (0-indexed) */
        const val PERCUSSION_CHANNEL = 9
    }
}
