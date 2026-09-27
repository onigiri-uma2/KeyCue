package com.onigiri.keycue.profile

/**
 * 『Sky 星を紡ぐ子どもたち』で提供される5種類の演奏ボタンレイアウト定義。
 *
 * 内部IDとUI表示名は分離されており、永続化には安定した本enum名を使用します。
 * 旧識別名 (sk1, sk2, pad1, pad2, pad3) からの安全な移行処理を提供します。
 */
enum class SkyLayout(
    val displayName: String,
    val isGamepad: Boolean
) {
    /** タッチ（標準）: 3×5格子配置。すべての位置合わせの唯一の基準。旧: sk1 */
    TOUCH_STANDARD("タッチ（標準）", isGamepad = false),

    /** タッチ（拡大）: 拡大3×5格子配置。旧: sk2 */
    TOUCH_EXPANDED("タッチ（拡大）", isGamepad = false),

    /** パッド（分散1）: 低音側2キーがLT/RTに割り当てられるゲームパッド分散配置。旧: pad1 */
    PAD_TRIGGER_FIRST("パッド（分散1）", isGamepad = true),

    /** パッド（分散2）: 低音側3キーが十字キー↓/←/↑に割り当てられるゲームパッド分散配置。旧: pad2 */
    PAD_DPAD_FIRST("パッド（分散2）", isGamepad = true),

    /** パッド（格子）: PAD_TRIGGER_FIRSTと同じキーマッピングを持つ3×5格子配置。旧: pad3 */
    PAD_GRID("パッド（格子）", isGamepad = true);

    /** 永続化およびAPI互換用の小文字識別ID */
    val id: String get() = name.lowercase()

    companion object {
        /**
         * 内部ID文字列または旧識別名から安全に [SkyLayout] を解決する。
         */
        fun fromId(id: String?): SkyLayout = fromIdOrDefault(id)

        /**
         * 内部ID文字列または旧識別名から安全に [SkyLayout] を解決する。
         * nullまたは不正な文字列の場合はフォールバックとして [TOUCH_STANDARD] を返す。
         */
        fun fromIdOrDefault(id: String?, default: SkyLayout = TOUCH_STANDARD): SkyLayout {
            if (id.isNullOrBlank()) return default
            val trimmed = id.trim()

            // 1. 正式な内部IDとの完全一致 (大文字小文字無視)
            entries.firstOrNull { it.name.equals(trimmed, ignoreCase = true) }?.let {
                return it
            }

            // 2. 旧識別名からの安全なマイグレーション
            return when (trimmed.lowercase()) {
                "sk1" -> TOUCH_STANDARD
                "sk2" -> TOUCH_EXPANDED
                "pad1" -> PAD_TRIGGER_FIRST
                "pad2" -> PAD_DPAD_FIRST
                "pad3" -> PAD_GRID
                else -> default
            }
        }
    }
}
