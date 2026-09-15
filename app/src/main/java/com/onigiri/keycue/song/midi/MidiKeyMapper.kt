package com.onigiri.keycue.song.midi

import com.onigiri.keycue.model.PitchClass
import com.onigiri.keycue.model.ResolvedMidiMapping
import com.onigiri.keycue.model.ScaleType
import com.onigiri.keycue.model.MidiMappingMode
import com.onigiri.keycue.model.MidiMappingSettings
import kotlin.math.abs

/**
 * MIDIノート番号を15キー（5列×3段、0..14）へマッピングするインターフェース。
 */
interface KeyMapper {
    /**
     * MIDIノート番号を15キーのインデックス (0..14) に変換する。
     * マッピング対象外のノートは null を返す。
     *
     * @param midiNote MIDIノート番号 (0..127)
     * @return 15キーのインデックス (0..14)、または対象外の場合 null
     */
    fun map(midiNote: Int): Int?
}

/**
 * 15キー用 MIDI ノートマッパー。
 *
 * ダイアトニック15音（0..14）へのノートマッピング、手動生成、
 * およびMIDIイベント解析に基づくAUTO自動選択を提供する。
 */
class MidiKeyMapper(
    val mapping: ResolvedMidiMapping
) : KeyMapper {

    /**
     * デフォルトの解決済みマッピング (C Major Octave 4) を使用するコンストラクタ。
     */
    constructor() : this(DEFAULT_RESOLVED_MAPPING)

    /**
     * 後方互換用コンストラクタ（移調およびオクターブシフト対応）。
     */
    constructor(transpose: Int = 0, octaveShift: Int = 0) : this(
        createDefaultMappingWithShift(transpose, octaveShift)
    )

    override fun map(midiNote: Int): Int? {
        return mapping.keyOf(midiNote)
    }

    companion object {
        /** AUTO探索対象の開始オクターブ最小値 */
        const val MIN_AUTO_OCTAVE = 2

        /** AUTO探索対象の開始オクターブ最大値 */
        const val MAX_AUTO_OCTAVE = 6

        /** タイブレークの基準オクターブ（中央値） */
        const val TIE_BREAK_BASE_OCTAVE = 4

        /** デフォルトの解決済みマッピング (C Major Octave 4) */
        val DEFAULT_RESOLVED_MAPPING = ResolvedMidiMapping(
            root = PitchClass.C,
            scale = ScaleType.MAJOR,
            baseOctave = 4,
            midiNotes = listOf(
                60, 62, 64, 65, 67,
                69, 71, 72, 74, 76,
                77, 79, 81, 83, 84
            ),
            mappedEventCount = 0,
            totalEventCount = 0
        )

        /**
         * Root音、スケール、開始オクターブから15音のMIDIノート番号リストを生成する。
         *
         * 生成されたノートが 0..127 の範囲を超える場合は null を返す。
         *
         * @param root ルート音
         * @param scale スケール種別
         * @param baseOctave 開始オクターブ
         * @return 15要素のMIDIノート番号リスト、または範囲外の場合 null
         */
        fun createMapping(
            root: PitchClass,
            scale: ScaleType,
            baseOctave: Int
        ): List<Int>? {
            val baseMidiNote = root.toMidiNote(baseOctave)
            val notes = ArrayList<Int>(15)
            for (interval in scale.intervals) {
                val note = baseMidiNote + interval
                if (note !in 0..127) {
                    return null
                }
                notes.add(note)
            }
            return notes
        }

        /**
         * 指定されたマッピングに基づき、MIDIノートをキーインデックス (0..14) へ変換する。
         */
        fun mapNote(midiNote: Int, mapping: ResolvedMidiMapping): Int? {
            return mapping.keyOf(midiNote)
        }

        /**
         * 生のMIDIイベント群と設定に基づき、最適な [ResolvedMidiMapping] を解決する。
         *
         * @param rawEvents MIDIファイルから抽出されたNote Onイベント群
         * @param settings MIDIマッピング設定 (AUTO / MANUAL)
         * @return 解決された [ResolvedMidiMapping]
         */
        fun resolveMapping(
            rawEvents: List<RawMidiNoteEvent>,
            settings: MidiMappingSettings
        ): ResolvedMidiMapping {
            // Percussionチャネル (チャネル9) は除外
            val playableEvents = rawEvents.filter { !it.isPercussion }
            val totalCount = playableEvents.size

            return when (settings.mode) {
                MidiMappingMode.MANUAL -> {
                    resolveManualMapping(playableEvents, settings, totalCount)
                }
                MidiMappingMode.AUTO -> {
                    resolveAutoMapping(playableEvents, totalCount)
                }
            }
        }

        private fun resolveManualMapping(
            playableEvents: List<RawMidiNoteEvent>,
            settings: MidiMappingSettings,
            totalCount: Int
        ): ResolvedMidiMapping {
            val notes = createMapping(
                settings.manualRoot,
                settings.manualScale,
                settings.manualBaseOctave
            ) ?: DEFAULT_RESOLVED_MAPPING.midiNotes

            val noteSet = notes.toSet()
            val mappedCount = playableEvents.count { it.midiNote in noteSet }

            return ResolvedMidiMapping(
                root = settings.manualRoot,
                scale = settings.manualScale,
                baseOctave = settings.manualBaseOctave,
                midiNotes = notes,
                mappedEventCount = mappedCount,
                totalEventCount = totalCount
            )
        }

        private fun resolveAutoMapping(
            playableEvents: List<RawMidiNoteEvent>,
            totalCount: Int
        ): ResolvedMidiMapping {
            // Note On イベントの頻度テーブルを構築
            val noteFrequencies = HashMap<Int, Int>()
            for (event in playableEvents) {
                noteFrequencies[event.midiNote] = (noteFrequencies[event.midiNote] ?: 0) + 1
            }

            data class CandidateScore(
                val root: PitchClass,
                val scale: ScaleType,
                val baseOctave: Int,
                val midiNotes: List<Int>,
                val mappedCount: Int,
                val distinctCount: Int
            )

            val candidates = mutableListOf<CandidateScore>()

            // 探索範囲: Root 12種 × Scale 2種 × Octave 2..6
            for (root in PitchClass.entries) {
                for (scale in ScaleType.entries) {
                    for (octave in MIN_AUTO_OCTAVE..MAX_AUTO_OCTAVE) {
                        val notes = createMapping(root, scale, octave) ?: continue
                        var mappedCount = 0
                        var distinctCount = 0
                        for (note in notes) {
                            val freq = noteFrequencies[note] ?: 0
                            if (freq > 0) {
                                mappedCount += freq
                                distinctCount++
                            }
                        }
                        candidates.add(
                            CandidateScore(
                                root = root,
                                scale = scale,
                                baseOctave = octave,
                                midiNotes = notes,
                                mappedCount = mappedCount,
                                distinctCount = distinctCount
                            )
                        )
                    }
                }
            }

            if (candidates.isEmpty()) {
                return DEFAULT_RESOLVED_MAPPING.copy(totalEventCount = totalCount)
            }

            // 固定決定論的タイブレークルール:
            // 1. mappedCount (最多マッチ) 降順
            // 2. distinctCount (最多ユニーク音カバー) 降順
            // 3. abs(octave - 4) (中央オクターブ4への距離) 昇順
            // 4. octave (低いオクターブ優先) 昇順
            // 5. root.ordinal (C, C#, D...) 昇順
            // 6. scale.ordinal (MAJOR, NATURAL_MINOR) 昇順
            val bestCandidate = candidates.maxWith(
                compareBy<CandidateScore> { it.mappedCount }
                    .thenBy { it.distinctCount }
                    .thenByDescending { abs(it.baseOctave - TIE_BREAK_BASE_OCTAVE) } // maxWith なので小さい方が勝ち＝降順反転
                    .thenByDescending { it.baseOctave } // 低い方が勝ち＝降順反転
                    .thenByDescending { it.root.ordinal } // 小さいordinalが勝ち＝降順反転
                    .thenByDescending { it.scale.ordinal } // 小さいordinalが勝ち＝降順反転
            )

            return ResolvedMidiMapping(
                root = bestCandidate.root,
                scale = bestCandidate.scale,
                baseOctave = bestCandidate.baseOctave,
                midiNotes = bestCandidate.midiNotes,
                mappedEventCount = bestCandidate.mappedCount,
                totalEventCount = totalCount
            )
        }

        private fun createDefaultMappingWithShift(transpose: Int, octaveShift: Int): ResolvedMidiMapping {
            val shiftedNotes = DEFAULT_RESOLVED_MAPPING.midiNotes.map { note ->
                note - transpose - (octaveShift * 12)
            }
            return DEFAULT_RESOLVED_MAPPING.copy(midiNotes = shiftedNotes)
        }
    }
}
