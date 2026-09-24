package com.onigiri.keycue.model.timing

import com.onigiri.keycue.song.midi.TempoMap
import com.onigiri.keycue.song.midi.TimeSignatureMap

/**
 * 楽曲に含まれるタイミング（テンポ・拍子）メタデータを表す sealed interface。
 *
 * MIDIフォーマット由来とSky Studioフォーマット由来を型安全に区別し、
 * メトロノームが楽曲の時間軸と連動するために必要な情報を提供します。
 */
sealed interface SongTimingMetadata {

    /** 楽曲の代表BPM（表示用・参照用） */
    val primaryBpm: Double

    /** 楽曲の代表拍子（表示用・参照用、不明な場合はnull） */
    val primaryTimeSignature: TimeSignature?

    /**
     * MIDIファイルから抽出されたタイミングメタデータ。
     *
     * 曲中のテンポ変更および拍子変更の完全なタイムライン情報を保持します。
     *
     * @param ppqn 4分音符あたりのtick数
     * @param tempoMap テンポ変更マップ
     * @param timeSignatureMap 拍子変更マップ
     * @param hasExplicitTempo ファイル内に明示的なSet Tempoイベントが存在したかどうか
     * @param hasExplicitTimeSignature ファイル内に明示的なTime Signatureイベントが存在したかどうか
     */
    data class Midi(
        val ppqn: Int,
        val tempoMap: TempoMap,
        val timeSignatureMap: TimeSignatureMap,
        val hasExplicitTempo: Boolean = tempoMap.hasExplicitTempo,
        val hasExplicitTimeSignature: Boolean = timeSignatureMap.hasExplicitTimeSignature
    ) : SongTimingMetadata {
        override val primaryBpm: Double
            get() = tempoMap.firstBpm

        override val primaryTimeSignature: TimeSignature
            get() = timeSignatureMap.firstTimeSignature
    }

    /**
     * Sky Studio譜面から抽出されたタイミングメタデータ。
     *
     * 譜面ヘッダーに明示されたBPM情報が存在し、かつ安全な範囲（40〜240 BPM）にある場合のみ
     * [validBpm] に値が設定されます。
     *
     * @param rawBpm JSONから抽出された生のBPM値（存在しない場合はnull）
     * @param validBpm 安全に適用可能な正規化BPM（適用不可時はnull）
     * @param invalidReason 自動適用されなかった場合の理由（デバッグ・UI用）
     */
    data class SkyStudio(
        val rawBpm: Double?,
        val validBpm: Int?,
        val invalidReason: String? = null
    ) : SongTimingMetadata {
        override val primaryBpm: Double
            get() = (validBpm ?: 120).toDouble()

        override val primaryTimeSignature: TimeSignature?
            get() = null // Sky Studio形式では拍子の自動推測を行わない
    }
}
