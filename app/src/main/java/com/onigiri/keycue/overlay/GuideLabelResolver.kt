package com.onigiri.keycue.overlay

import com.onigiri.keycue.model.PlaybackSession
import com.onigiri.keycue.model.SongFormat

/**
 * 現在の演奏セッションに基づき、オーバーレイに描画すべきガイドラベル（MIDIキー配置音名文字列リスト等）を解決する純粋関数。
 *
 * - セッションが [SongFormat.MIDI] かつ [PlaybackSession.resolvedMidiMapping] を持つ場合:
 *   マッピング済みのキー配置音名文字列リスト ([com.onigiri.keycue.model.ResolvedMidiMapping.guideLabels]) を返す。
 * - それ以外（通常曲、セッション null、MIDI だがマッピング未解決）:
 *   デフォルトのガイド番号表示へ戻すため `null` を返す。
 */
internal fun resolveGuideLabels(
    session: PlaybackSession?
): List<String>? =
    if (session?.format == SongFormat.MIDI) {
        session.resolvedMidiMapping?.guideLabels
    } else {
        null
    }
