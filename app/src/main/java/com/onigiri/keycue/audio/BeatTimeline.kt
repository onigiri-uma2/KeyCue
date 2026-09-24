package com.onigiri.keycue.audio

import com.onigiri.keycue.model.timing.TimeSignature

/**
 * メトロノームが使用するタイミング情報源の種別。
 */
enum class TimingSourceKind {
    /** MIDIファイルの明示的なテンポ/拍子イベント */
    MIDI,
    /** MIDI標準仕様上のデフォルト（120 BPM, 4/4） */
    MIDI_DEFAULT,
    /** Sky StudioファイルのBPMメタデータ */
    SKY_STUDIO,
    /** ユーザーによる手動設定 */
    MANUAL
}

/**
 * 楽曲時間軸におけるメトロノーム拍グリッドと現在タイミング情報を表す抽象タイムライン。
 *
 * 手動設定、Sky Studioの固定BPM、および曲中テンポ・拍子変更を含むMIDIの可変タイムラインを
 * 統一的に [MetronomeScheduler] から扱えるようにします。
 */
interface BeatTimeline {

    /**
     * 指定されたクリックインデックス [index] の拍情報を取得する。
     * インデックスが存在しない（曲の終端を超える等）場合は null を返す。
     */
    fun beatForIndex(index: Long): MetronomeBeat?

    /**
     * 指定された楽曲再生位置 [positionMs] 以降（現在位置または未来、遅延許容なし）にある
     * 最初のクリックインデックスを取得する。
     * 手動シーク後やABリピート巻き戻し時に使用。
     */
    fun nextBeatIndexAtOrAfter(positionMs: Long): Long

    /**
     * 指定された楽曲再生位置 [positionMs] において、許容遅延 [toleratedDelayMs] を加味して
     * 次に発音対象とすべき最小のクリックインデックスを取得する。
     * 初回再生開始時や処理遅延からのリカバリ時に使用。
     */
    fun candidateBeatIndex(positionMs: Long, toleratedDelayMs: Long): Long

    /**
     * 指定された楽曲位置 [positionMs] におけるテンポ（BPM）を取得する。
     */
    fun bpmAt(positionMs: Long): Double

    /**
     * 指定された楽曲位置 [positionMs] における拍子を取得する。
     */
    fun timeSignatureAt(positionMs: Long): TimeSignature

    /** タイミング情報の主情報源種別 */
    val sourceKind: TimingSourceKind

    /** BPMが楽曲から自動取得されたものかどうか */
    val isBpmAuto: Boolean

    /** 拍子が楽曲から自動取得されたものかどうか */
    val isTimeSignatureAuto: Boolean
}
