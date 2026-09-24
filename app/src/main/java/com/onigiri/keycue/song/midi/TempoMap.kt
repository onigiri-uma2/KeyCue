package com.onigiri.keycue.song.midi

/**
 * MIDIのテンポ変更イベントを表すデータクラス。
 *
 * @param tick 変更が発生した絶対tick
 * @param usPerQuarter 4分音符あたりのマイクロ秒（microseconds per quarter note）
 * @param trackIndex 抽出元のトラック番号（Format 1ではトラック0のコンダクタートラックを優先）
 * @param eventIndex トラック内の出現順
 */
data class RawTempoEvent(
    val tick: Long,
    val usPerQuarter: Long,
    val trackIndex: Int = 0,
    val eventIndex: Int = 0
)

/**
 * テンポ変更ポイント。累積マイクロ秒を保持する。
 */
data class TempoPoint(
    val tick: Long,
    val usPerQuarter: Long,
    val timeUs: Long
) {
    /** 累積開始ミリ秒（四捨五入） */
    val timeMs: Long get() = (timeUs + 500L) / 1000L

    /** このポイントにおけるBPM */
    val bpm: Double get() = 60_000_000.0 / usPerQuarter
}

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

    /** ファイル内に明示的なSet Tempoイベントが存在したかどうか */
    val hasExplicitTempo: Boolean = rawTempoEvents.isNotEmpty()

    private val tempoPoints: List<TempoPoint>

    init {
        // 決定的なソート順序:
        // 1. tick 昇順
        // 2. trackIndex 昇順（Format 1ではトラック0を優先）
        // 3. eventIndex 昇順
        val sortedEvents = rawTempoEvents.sortedWith(
            compareBy<RawTempoEvent> { it.tick }
                .thenBy { it.trackIndex }
                .thenBy { it.eventIndex }
        )

        val tickToTempo = java.util.TreeMap<Long, Long>()
        for (event in sortedEvents) {
            // 不正な値（0以下）は無視
            if (event.usPerQuarter > 0L) {
                if (!tickToTempo.containsKey(event.tick)) {
                    tickToTempo[event.tick] = event.usPerQuarter
                }
            }
        }
        // tick 0 に明示的なイベントがなければデフォルト 120 BPM (500,000 us/quarter) を補完
        if (!tickToTempo.containsKey(0L)) {
            tickToTempo[0L] = DEFAULT_US_PER_QUARTER
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

    /** 楽曲先頭のテンポ（BPM） */
    val firstBpm: Double
        get() = tempoPoints.first().bpm

    /** 全テンポポイントの不変リスト */
    val allPoints: List<TempoPoint>
        get() = tempoPoints

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

    /**
     * 指定された絶対tick（Double精度）における楽曲再生時間（ミリ秒）を高精度に算出する。
     */
    fun tickToMs(tick: Double): Double {
        val longTick = tick.toLong()
        val point = pointAtTick(longTick)
        val deltaTick = tick - point.tick
        val deltaUs = (deltaTick * point.usPerQuarter.toDouble()) / ppqn.toDouble()
        val totalUs = point.timeUs.toDouble() + deltaUs
        return totalUs / 1000.0
    }

    /** 登録されているテンポポイントの最大tick */
    val maxTick: Long
        get() = tempoPoints.lastOrNull()?.tick ?: 0L

    /**
     * 指定された絶対tickにおいて有効な [TempoPoint] を二分探索で取得する。
     */
    fun pointAtTick(tick: Long): TempoPoint {
        if (tick <= 0L || tempoPoints.size == 1) return tempoPoints.first()

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

        return tempoPoints[bestIndex]
    }

    /**
     * 指定された楽曲時間 [positionMs] において有効な [TempoPoint] を二分探索で取得する。
     */
    fun pointAtMs(positionMs: Long): TempoPoint {
        if (positionMs <= 0L || tempoPoints.size == 1) return tempoPoints.first()

        var low = 0
        var high = tempoPoints.size - 1
        var bestIndex = 0

        while (low <= high) {
            val mid = (low + high) ushr 1
            val point = tempoPoints[mid]
            if (point.timeMs <= positionMs) {
                bestIndex = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }

        return tempoPoints[bestIndex]
    }

    /**
     * 指定された楽曲時間 [positionMs] における有効なテンポ（BPM）を取得する。
     */
    fun bpmAtMs(positionMs: Long): Double = pointAtMs(positionMs).bpm

    /**
     * 指定された楽曲時間 [positionMs] から絶対tickを逆算する。
     */
    fun msToTick(positionMs: Long): Long {
        if (positionMs <= 0L) return 0L

        val point = pointAtMs(positionMs)
        val targetUs = positionMs * 1000L
        val deltaUs = (targetUs - point.timeUs).coerceAtLeast(0L)
        val deltaTick = (deltaUs * ppqn) / point.usPerQuarter

        return point.tick + deltaTick
    }

    companion object {
        /**
         * MIDI標準デフォルトテンポ: 120 BPM = 500,000 microseconds per quarter note
         */
        const val DEFAULT_US_PER_QUARTER: Long = 500_000L
    }
}
