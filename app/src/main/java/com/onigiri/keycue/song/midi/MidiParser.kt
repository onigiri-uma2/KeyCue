package com.onigiri.keycue.song.midi

import com.onigiri.keycue.model.NoteEvent
import com.onigiri.keycue.model.SongData
import com.onigiri.keycue.song.SongParser
import java.io.InputStream
import kotlin.math.max

/**
 * Standard MIDI File (.mid / .midi) を解析し、[SongData] を生成するパーサー。
 *
 * 対応仕様:
 * - Format 0（単一トラック） / Format 1（複数トラック）
 * - MThd ヘッダチャンク、MTrk トラックチャンク
 * - Variable Length Quantity (VLQ)
 * - Running Status
 * - Note On (velocity=0 は Note Off 扱い) / Note Off
 * - Set Tempo メタイベント (0xFF 0x51)
 * - End of Track メタイベント (0xFF 0x2F)
 *
 * @param keyMapper MIDIノートを15キーへマッピングする [KeyMapper]
 */
class MidiParser(
    private val keyMapper: KeyMapper = MidiKeyMapper()
) : SongParser {

    override fun parse(inputStream: InputStream, title: String): SongData {
        val bytes = try {
            inputStream.readBytes()
        } catch (e: Exception) {
            throw MidiParseException("Failed to read MIDI stream", e)
        }
        return parse(bytes, title)
    }

    /**
     * バイト配列から MIDI データを解析し、[SongData] を生成する。
     *
     * @param bytes MIDIバイナリデータ
     * @param title 楽曲タイトル
     * @return 解析された [SongData]
     */
    /**
     * MIDIバイナリから生イベントおよびテンポ情報を解析した結果データ。
     */
    data class ExtractedMidiData(
        val tempoMap: TempoMap,
        val rawEvents: List<RawMidiNoteEvent>,
        val globalMaxTick: Long
    )

    fun parse(bytes: ByteArray, title: String): SongData {
        val extracted = extractRawEvents(bytes)

        val noteEvents = mutableListOf<NoteEvent>()
        for (event in extracted.rawEvents) {
            val key = keyMapper.map(event.midiNote) ?: continue
            noteEvents.add(NoteEvent(timeMs = event.timeMs, key = key))
        }

        noteEvents.sortWith(compareBy({ it.timeMs }, { it.key }))

        val calculatedDurationMs = extracted.tempoMap.tickToMs(extracted.globalMaxTick)
        val finalDurationMs = if (noteEvents.isNotEmpty()) {
            max(calculatedDurationMs, noteEvents.last().timeMs)
        } else {
            calculatedDurationMs
        }

        return SongData(
            title = title,
            durationMs = finalDurationMs,
            events = noteEvents
        )
    }

    /**
     * MIDIバイナリから生イベントおよびテンポ情報を解析する。
     */
    fun extractRawEvents(bytes: ByteArray): ExtractedMidiData {
        if (bytes.size < 14) {
            throw InvalidMidiException("File too short for MIDI header (${bytes.size} bytes)")
        }

        val reader = MidiByteReader(bytes)

        val chunkType = reader.readString(4)
        if (chunkType != "MThd") {
            throw InvalidMidiException("Expected 'MThd' header, found '$chunkType'")
        }

        val headerLength = reader.readInt()
        if (headerLength < 6) {
            throw InvalidMidiException("Invalid MThd header length: $headerLength")
        }

        val format = reader.readShort()
        if (format != 0 && format != 1) {
            throw UnsupportedMidiException("Unsupported MIDI format: $format (only Format 0 and 1 are supported)")
        }

        val ntrks = reader.readShort()
        if (ntrks <= 0) {
            throw InvalidMidiException("Invalid number of tracks: $ntrks")
        }
        if (format == 0 && ntrks != 1) {
            throw InvalidMidiException("Format 0 MIDI must have exactly 1 track, found $ntrks")
        }

        val division = reader.readShort()
        if ((division and 0x8000) != 0) {
            throw UnsupportedMidiException("SMPTE timecode division is not supported")
        }
        val ppqn = division and 0x7FFF
        if (ppqn <= 0) {
            throw InvalidMidiException("Invalid PPQN: $ppqn")
        }

        if (headerLength > 6) {
            reader.skip(headerLength - 6)
        }

        val rawTempoEvents = mutableListOf<RawTempoEvent>()
        val rawNoteOns = mutableListOf<RawNoteOn>()
        var tracksRead = 0
        var globalMaxTick = 0L

        while (reader.hasRemaining && tracksRead < ntrks) {
            val trackChunkType = reader.readString(4)
            val trackLength = reader.readInt()

            if (trackChunkType != "MTrk") {
                reader.skip(trackLength)
                continue
            }

            tracksRead++
            val trackEndIndex = reader.position + trackLength
            if (trackEndIndex > bytes.size) {
                throw InvalidMidiException("Track chunk length exceeds file size")
            }

            var currentTick = 0L
            var runningStatus: Int? = null

            while (reader.position < trackEndIndex) {
                val deltaTick = reader.readVlq()
                currentTick += deltaTick
                globalMaxTick = max(globalMaxTick, currentTick)

                val peekByte = reader.peekByte()
                val statusByte: Int
                val isRunningStatus: Boolean

                if (peekByte < 0x80) {
                    statusByte = runningStatus
                        ?: throw InvalidMidiException("Running status used before any status byte at tick $currentTick")
                    isRunningStatus = true
                } else {
                    statusByte = reader.readByte()
                    isRunningStatus = false
                }

                if (statusByte < 0xF0) {
                    // チャンネルボイスメッセージ (0x80..0xEF)
                    runningStatus = statusByte
                    val command = statusByte and 0xF0
                    val channel = statusByte and 0x0F
                    val param1 = if (isRunningStatus) reader.readByte() else reader.readByte()

                    when (command) {
                        0x90 -> {
                            // Note On: ノート番号 (param1) とベロシティ
                            val velocity = reader.readByte()
                            // MIDI仕様: velocity == 0 は Note Off と同等として扱う
                            if (velocity > 0) {
                                rawNoteOns.add(RawNoteOn(currentTick, param1, velocity, channel))
                            }
                        }
                        0x80 -> {
                            // Note Off: ノート番号 (param1) とオフベロシティをスキップ
                            reader.readByte()
                        }
                        0xA0 -> {
                            // Polyphonic Key Pressure (Aftertouch): 圧力値をスキップ
                            reader.readByte()
                        }
                        0xB0 -> {
                            // Control Change: コントロール値をスキップ
                            reader.readByte()
                        }
                        0xC0 -> {
                            // Program Change: データバイトは param1 の1バイトのみ
                        }
                        0xD0 -> {
                            // Channel Pressure (Aftertouch): データバイトは param1 の1バイトのみ
                        }
                        0xE0 -> {
                            // Pitch Bend: ピッチベンド値（MSB）をスキップ
                            reader.readByte()
                        }
                        else -> {
                            throw InvalidMidiException("Unknown channel message command: ${command.toString(16)}")
                        }
                    }
                } else if (statusByte == 0xFF) {
                    // メタイベント (0xFF): ランニングステータスをクリア
                    runningStatus = null
                    val metaType = reader.readByte()
                    val metaLength = reader.readVlq().toInt()

                    when (metaType) {
                        0x51 -> {
                            // Set Tempo: 3バイトのマイクロ秒/四分音符 (microsecond per quarter note)
                            if (metaLength == 3) {
                                val b0 = reader.readByte()
                                val b1 = reader.readByte()
                                val b2 = reader.readByte()
                                val usPerQuarter = ((b0 shl 16) or (b1 shl 8) or b2).toLong()
                                rawTempoEvents.add(RawTempoEvent(currentTick, usPerQuarter))
                            } else {
                                reader.skip(metaLength)
                            }
                        }
                        0x2F -> {
                            // End of Track: トラック終端を検出しチャンク処理を終了
                            reader.skip(metaLength)
                            break
                        }
                        else -> {
                            // その他のメタイベント（テキスト、著作権、歌詞等）はスキップ
                            reader.skip(metaLength)
                        }
                    }
                } else if (statusByte == 0xF0 || statusByte == 0xF7) {
                    runningStatus = null
                    val sysexLength = reader.readVlq().toInt()
                    reader.skip(sysexLength)
                } else {
                    runningStatus = null
                }
            }

            if (reader.position < trackEndIndex) {
                reader.seek(trackEndIndex)
            }
        }

        val tempoMap = TempoMap(ppqn, rawTempoEvents)
        val rawEvents = rawNoteOns.map { rawNote ->
            RawMidiNoteEvent(
                timeMs = tempoMap.tickToMs(rawNote.tick),
                midiNote = rawNote.note,
                velocity = rawNote.velocity,
                channel = rawNote.channel
            )
        }
        return ExtractedMidiData(tempoMap, rawEvents, globalMaxTick)
    }

    /**
     * 指定された [com.onigiri.keycue.model.ResolvedMidiMapping] を適用して [SongData] を生成する。
     */
    fun parseWithResolvedMapping(
        bytes: ByteArray,
        title: String,
        mapping: com.onigiri.keycue.model.ResolvedMidiMapping
    ): SongData {
        val extracted = extractRawEvents(bytes)

        val noteEvents = mutableListOf<NoteEvent>()
        for (event in extracted.rawEvents) {
            val key = mapping.keyOf(event.midiNote) ?: continue
            noteEvents.add(NoteEvent(timeMs = event.timeMs, key = key))
        }

        noteEvents.sortWith(compareBy({ it.timeMs }, { it.key }))

        val calculatedDurationMs = extracted.tempoMap.tickToMs(extracted.globalMaxTick)
        val finalDurationMs = if (noteEvents.isNotEmpty()) {
            max(calculatedDurationMs, noteEvents.last().timeMs)
        } else {
            calculatedDurationMs
        }

        return SongData(
            title = title,
            durationMs = finalDurationMs,
            events = noteEvents
        )
    }

    private data class RawNoteOn(
        val tick: Long,
        val note: Int,
        val velocity: Int,
        val channel: Int
    )

    /**
     * MIDIバイト配列の読み取りヘルパークラス。
     */
    internal class MidiByteReader(private val bytes: ByteArray) {
        var position: Int = 0

        val hasRemaining: Boolean
            get() = position < bytes.size

        fun peekByte(): Int {
            if (position >= bytes.size) {
                throw InvalidMidiException("Unexpected end of MIDI data")
            }
            return bytes[position].toInt() and 0xFF
        }

        fun readByte(): Int {
            if (position >= bytes.size) {
                throw InvalidMidiException("Unexpected end of MIDI data")
            }
            return bytes[position++].toInt() and 0xFF
        }

        fun readShort(): Int {
            val b0 = readByte()
            val b1 = readByte()
            return (b0 shl 8) or b1
        }

        fun readInt(): Int {
            val b0 = readByte()
            val b1 = readByte()
            val b2 = readByte()
            val b3 = readByte()
            return (b0 shl 24) or (b1 shl 16) or (b2 shl 8) or b3
        }

        fun readString(length: Int): String {
            if (position + length > bytes.size) {
                throw InvalidMidiException("Unexpected end of MIDI data while reading string")
            }
            val str = String(bytes, position, length, Charsets.US_ASCII)
            position += length
            return str
        }

        fun readVlq(): Long {
            var value = 0L
            var bytesRead = 0
            while (true) {
                val b = readByte()
                bytesRead++
                value = (value shl 7) or ((b and 0x7F).toLong())
                if ((b and 0x80) == 0) {
                    break
                }
                if (bytesRead > 4) {
                    throw InvalidMidiException("Variable Length Quantity exceeds 4 bytes")
                }
            }
            return value
        }

        fun skip(length: Int) {
            if (length < 0) {
                throw InvalidMidiException("Invalid negative skip length: $length")
            }
            if (position + length > bytes.size) {
                throw InvalidMidiException("Unexpected end of MIDI data while skipping $length bytes")
            }
            position += length
        }

        fun seek(targetIndex: Int) {
            if (targetIndex < 0 || targetIndex > bytes.size) {
                throw InvalidMidiException("Seek target out of bounds: $targetIndex")
            }
            position = targetIndex
        }
    }
}
