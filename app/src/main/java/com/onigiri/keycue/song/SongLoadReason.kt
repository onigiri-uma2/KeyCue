package com.onigiri.keycue.song

/**
 * 楽曲読み込みがトリガーされた理由・契機を分類する列挙型。
 */
enum class SongLoadReason {
    /** ユーザーがファイルピッカーから明示的に選択 */
    USER_SELECT,

    /** ユーザーが「最近使った曲」リストから選択 */
    RECENT_SELECT,

    /** アプリ起動時等の前回楽曲自動復元 */
    RESTORE,

    /** MIDI手動マッピング変更等に伴う内部再適用 */
    MIDI_REMAP
}
