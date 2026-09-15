package com.onigiri.keycue.model

/**
 * 12音の半音（0..11）を表現する列挙型。
 *
 * @param semitone 0をCとした半音オフセット (0..11)
 * @param displayName UI表示用の音名表記
 */
enum class PitchClass(val semitone: Int, val displayName: String) {
    C(0, "C"),
    C_SHARP(1, "C#/Db"),
    D(2, "D"),
    E_FLAT(3, "Eb"),
    E(4, "E"),
    F(5, "F"),
    F_SHARP(6, "F#/Gb"),
    G(7, "G"),
    A_FLAT(8, "Ab"),
    A(9, "A"),
    B_FLAT(10, "Bb"),
    B(11, "B");

    /**
     * 指定されたオクターブにおけるMIDIノート番号を計算する。
     *
     * 統一仕様: C4 = MIDI note 60
     * midiNote = 12 * (octave + 1) + semitone
     *
     * @param octave オクターブ番号 (例: 4)
     * @return MIDIノート番号 (例: C4 -> 60, D4 -> 62)
     */
    fun toMidiNote(octave: Int): Int {
        return 12 * (octave + 1) + semitone
    }

    companion object {
        /**
         * 半音値 (0..11) から対応する [PitchClass] を取得する。
         */
        fun fromSemitone(semitone: Int): PitchClass {
            val normalized = ((semitone % 12) + 12) % 12
            return entries.first { it.semitone == normalized }
        }

        /**
         * MIDIノート番号からノート名とオクターブの文字列表記を生成する。
         * 例: 60 -> "C4", 61 -> "C#4", 62 -> "D4"
         */
        fun formatMidiNote(midiNote: Int): String {
            val pitch = fromSemitone(midiNote)
            val octave = (midiNote / 12) - 1
            val simpleName = when (pitch) {
                C -> "C"
                C_SHARP -> "C#"
                D -> "D"
                E_FLAT -> "Eb"
                E -> "E"
                F -> "F"
                F_SHARP -> "F#"
                G -> "G"
                A_FLAT -> "Ab"
                A -> "A"
                B_FLAT -> "Bb"
                B -> "B"
            }
            return "$simpleName$octave"
        }
    }
}
