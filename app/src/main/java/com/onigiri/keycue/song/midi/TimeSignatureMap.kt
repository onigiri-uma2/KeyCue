package com.onigiri.keycue.song.midi

import com.onigiri.keycue.model.timing.TimeSignature

/**
 * 拍子変更ポイントを表すデータクラス。
 */
data class TimeSignaturePoint(
    val tick: Long,
    val timeSignature: TimeSignature,
    val clocksPerClick: Int,
    val notated32ndNotesPerQuarter: Int
)

/**
 * MIDI楽曲中の拍子変更を管理・追跡するマップ。
 *
 * @param rawEvents 収集された [RawTimeSignatureEvent] のリスト
 */
class TimeSignatureMap(
    rawEvents: List<RawTimeSignatureEvent> = emptyList()
) {
    /** ファイル内に明示的なTime Signatureイベントが存在したかどうか */
    val hasExplicitTimeSignature: Boolean = rawEvents.isNotEmpty()

    internal val points: List<TimeSignaturePoint>

    init {
        // 決定的なソート順序:
        // 1. tick 昇順
        // 2. trackIndex 昇順（Format 1ではトラック0のコンダクタートラックを優先）
        // 3. eventIndex 昇順
        val sortedEvents = rawEvents.sortedWith(
            compareBy<RawTimeSignatureEvent> { it.tick }
                .thenBy { it.trackIndex }
                .thenBy { it.eventIndex }
        )

        // tickごとに最初の（優先度最高な）イベントを採用
        val tickToEvent = java.util.TreeMap<Long, RawTimeSignatureEvent>()
        for (event in sortedEvents) {
            if (!tickToEvent.containsKey(event.tick)) {
                tickToEvent[event.tick] = event
            }
        }

        val pointList = mutableListOf<TimeSignaturePoint>()

        // tick 0 に明示的なイベントがなければデフォルト 4/4 を補完
        if (!tickToEvent.containsKey(0L)) {
            pointList.add(
                TimeSignaturePoint(
                    tick = 0L,
                    timeSignature = TimeSignature.DEFAULT,
                    clocksPerClick = 24,
                    notated32ndNotesPerQuarter = 8
                )
            )
        }

        for ((tick, event) in tickToEvent) {
            pointList.add(
                TimeSignaturePoint(
                    tick = tick,
                    timeSignature = event.toTimeSignature(),
                    clocksPerClick = event.clocksPerClick,
                    notated32ndNotesPerQuarter = event.notated32ndNotesPerQuarter
                )
            )
        }

        // tick昇順で確定
        points = pointList.sortedBy { it.tick }
    }

    /** 楽曲先頭の拍子 */
    val firstTimeSignature: TimeSignature
        get() = points.first().timeSignature

    /** 全拍子変更ポイントの不変リスト */
    val allPoints: List<TimeSignaturePoint>
        get() = points

    /**
     * 指定された絶対tickにおいて有効な [TimeSignaturePoint] を二分探索で取得する。
     */
    fun pointAtTick(tick: Long): TimeSignaturePoint {
        if (tick <= 0L || points.size == 1) return points.first()

        var low = 0
        var high = points.size - 1
        var bestIndex = 0

        while (low <= high) {
            val mid = (low + high) ushr 1
            val p = points[mid]
            if (p.tick <= tick) {
                bestIndex = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }

        return points[bestIndex]
    }

    /**
     * 指定された絶対tickにおける有効な [TimeSignature] を取得する。
     */
    fun timeSignatureAtTick(tick: Long): TimeSignature = pointAtTick(tick).timeSignature

    /**
     * 指定された楽曲再生位置（ミリ秒）における有効な [TimeSignature] を取得する。
     */
    fun timeSignatureAtMs(positionMs: Long, tempoMap: TempoMap): TimeSignature {
        val tick = tempoMap.msToTick(positionMs)
        return timeSignatureAtTick(tick)
    }
}
