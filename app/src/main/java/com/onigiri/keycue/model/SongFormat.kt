package com.onigiri.keycue.model

/**
 * 対応する楽曲ファイルのフォーマットを表す列挙型。
 */
enum class SongFormat {
    /**
     * MIDIフォーマット (.mid, .midi)
     */
    MIDI,
 
     /**
      * Sky Studio JSONフォーマット (.json)
      */
     SKY_STUDIO_JSON,

    /**
     * 未知・非対応フォーマット
     */
    UNKNOWN
}
