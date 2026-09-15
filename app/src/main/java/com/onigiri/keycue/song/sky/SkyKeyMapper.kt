package com.onigiri.keycue.song.sky

/**
 * Sky Studioのキー文字列表現を内部キーインデックス (0..14) へ変換するマッパー。
 */
interface SkyKeyMapper {
    /**
     * キー文字列を内部キーインデックス (0..14) に変換する。
     *
     * @param keyStr "1Key7", "A1", "C5", "0" 等のキー文字列
     * @return 内部キー番号 (0..14)、不明または範囲外の場合は null
     */
    fun map(keyStr: String): Int?

    /**
     * キー文字列からトラック/レイヤー番号を抽出する（取得できない場合は null）。
     *
     * @param keyStr "1Key7", "2Key0" 等
     * @return トラック番号（例: "1Key7" -> 1）
     */
    fun extractTrack(keyStr: String): Int? = null
}

/**
 * Sky Studioキー表現のデフォルト実装。
 *
 * 対応パターン:
 * 1. `[track]Key[index]` 形式 (例: "1Key7", "2Key0", "Key14")
 * 2. `[track][A-C][1-5]` 形式 (例: "A1", "B3", "C5", "1A1", 大文字小文字両対応)
 * 3. 単純な数値形式 (例: "0" .. "14")
 */
class DefaultSkyKeyMapper : SkyKeyMapper {

    private val keyPattern = Regex("""^(?:(\d+))?[kK]ey(\d+)$""")
    private val gridPattern = Regex("""^(?:(\d+))?([a-cA-C])([1-5])$""")
    private val numericPattern = Regex("""^\d+$""")

    override fun map(keyStr: String): Int? {
        val trimmed = keyStr.trim()

        // 1. [track]Key[index] 形式 (実ファイル形式)
        val keyMatch = keyPattern.matchEntire(trimmed)
        if (keyMatch != null) {
            val keyIndex = keyMatch.groupValues[2].toIntOrNull()
            return if (keyIndex != null && keyIndex in 0..14) keyIndex else null
        }

        // 2. A1..C5 形式 (A1=0..A5=4, B1=5..B5=9, C1=10..C5=14)
        val gridMatch = gridPattern.matchEntire(trimmed)
        if (gridMatch != null) {
            val rowChar = gridMatch.groupValues[2].first().uppercaseChar()
            val colNum = gridMatch.groupValues[3].toInt()
            val row = when (rowChar) {
                'A' -> 0
                'B' -> 1
                'C' -> 2
                else -> return null
            }
            val col = colNum - 1
            return row * 5 + col
        }

        // 3. 単純な数値形式
        if (numericPattern.matches(trimmed)) {
            val num = trimmed.toIntOrNull()
            return if (num != null && num in 0..14) num else null
        }

        return null
    }

    override fun extractTrack(keyStr: String): Int? {
        val trimmed = keyStr.trim()
        val keyMatch = keyPattern.matchEntire(trimmed)
        if (keyMatch != null) {
            return keyMatch.groupValues[1].takeIf { it.isNotEmpty() }?.toIntOrNull()
        }
        val gridMatch = gridPattern.matchEntire(trimmed)
        if (gridMatch != null) {
            return gridMatch.groupValues[1].takeIf { it.isNotEmpty() }?.toIntOrNull()
        }
        return null
    }
}
