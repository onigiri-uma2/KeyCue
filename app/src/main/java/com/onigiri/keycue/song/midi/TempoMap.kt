package com.onigiri.keycue.song.midi

/**
 * MIDIのテンポ変更イベントを表すデータクラス。
 *
 * @param tick 変更が発生した絶対tick
 * @param usPerQuarter 4分音符あたりのマイクロ秒（microseconds per quarter note）
 */
data class RawTempoEvent(
    val tick: Long,
    val usPerQuarter: Long
)

/**
 * テンポ変更ポイント。累積マイクロ秒を保持する。
 */
data class TempoPoint(
    val tick: Long,
    val usPerQuarter: Long,
    val timeUs: Long
)

/**
 * MIDIのtick値をミリ秒（ms）へ変換するTempo Map。
 *
 * 曲中のテンポ変更イベントを追跡し、区間ごとの正確な経過時間を累積計算する。
 *
 * @param ppqn 4分音符あたりのtick数 (Ticks Per Quarter Note)
 * @param rawTempoEvents 収集されたテンポ変更イベントのリスト
 */
class TempoMap(
    val ppqn: Int,
    rawTempoEvents: List<RawTempoEvent> = emptyList()
) {
    init {
        require(ppqn > 0) { "PPQN must be greater than 0, but was $ppqn" }
    }

    private val tempoPoints: List<TempoPoint>

    init {
        // tick昇順でソート。同一tickの場合は後勝ち
        val sortedEvents = rawTempoEvents.sortedWith(
            compareBy<RawTempoEvent> { it.tick }
        )

        // tickごとの最新テンポをまとめる
        val tickToTempo = LinkedHashMap<Long, Long>()
        // デフォルトテンポ: 120 BPM = 500,000 us/quarter
        tickToTempo[0L] = DEFAULT_US_PER_QUARTER
        for (event in sortedEvents) {
            tickToTempo[event.tick] = event.usPerQuarter
        }

        val points = mutableListOf<TempoPoint>()
        var accumulatedTimeUs = 0L
        var previousTick = 0L
        var previousUsPerQuarter = DEFAULT_US_PER_QUARTER

        for ((tick, usPerQuarter) in tickToTempo) {
            if (tick > previousTick) {
                val deltaTick = tick - previousTick
                val deltaUs = (deltaTick * previousUsPerQuarter) / ppqn
                accumulatedTimeUs += deltaUs
            }
            points.add(TempoPoint(tick, usPerQuarter, accumulatedTimeUs))
            previousTick = tick
            previousUsPerQuarter = usPerQuarter
        }

        tempoPoints = points
    }

    /**
     * 指定された絶対tickを楽曲開始からの経過ミリ秒 (ms) に変換する。
     *
     * @param tick 絶対tick
     * @return 経過時間（ミリ秒）
     */
    fun tickToMs(tick: Long): Long {
        if (tick <= 0L) return 0L

        // 二分探索で tick 以下の最大のポイントを検索
        var low = 0
        var high = tempoPoints.size - 1
        var bestIndex = 0

        while (low <= high) {
            val mid = (low + high) ushr 1
            val point = tempoPoints[mid]
            if (point.tick <= tick) {
                bestIndex = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }

        val point = tempoPoints[bestIndex]
        val deltaTick = tick - point.tick
        val deltaUs = (deltaTick * point.usPerQuarter) / ppqn
        val totalUs = point.timeUs + deltaUs

        // マイクロ秒からミリ秒へ四捨五入して変換
        return (totalUs + 500) / 1000
    }

    companion object {
        /**
         * MIDI標準デフォルトテンポ: 120 BPM = 500,000 microseconds per quarter note
         */
        const val DEFAULT_US_PER_QUARTER: Long = 500_000L
    }
}
