package com.onigiri.keycue.playback

import com.onigiri.keycue.model.NoteEvent
import com.onigiri.keycue.model.PlaybackConfig

/**
 * 現在時刻に基づいて表示・発光すべきノートを判定するスケジューラ。
 *
 * 全イベントの総当たり (O(N)) を避け、二分探索 (O(log N + M)) により
 * 該当時間範囲のノートを高速に抽出する。
 */
class NoteScheduler {

    /**
     * 現在時刻に基づいて、発光対象ノートおよび先読みノートを抽出する。
     *
     * @param events 演奏イベント一覧（timeMs昇順）
     * @param currentTimeMs 現在の楽曲再生位置（ミリ秒）
     * @param leadTimeMs 先読み時間（ミリ秒、例: 700ms）
     * @param highlightTimeMs 発光許容時間（ミリ秒、例: 100ms）
     * @return [ScheduledNotes]
     */
    fun schedule(
        events: List<NoteEvent>,
        currentTimeMs: Long,
        leadTimeMs: Long = PlaybackConfig.DEFAULT_LEAD_TIME_MS,
        highlightTimeMs: Long = 100L
    ): ScheduledNotes {
        if (events.isEmpty()) {
            return ScheduledNotes(currentTimeMs, emptyList(), emptyList(), emptySet())
        }

        // 1. ハイライト範囲: [currentTimeMs - highlightTimeMs, currentTimeMs + highlightTimeMs]
        val hlStart = (currentTimeMs - highlightTimeMs).coerceAtLeast(0L)
        val hlEnd = currentTimeMs + highlightTimeMs
        val highlighted = if (hlEnd >= 0L && hlStart <= hlEnd) {
            findEventsInRange(events, hlStart, hlEnd)
        } else {
            emptyList()
        }

        // 2. 先読み範囲: (currentTimeMs, currentTimeMs + leadTimeMs]
        val upStart = (currentTimeMs + 1L).coerceAtLeast(0L)
        val upEnd = currentTimeMs + leadTimeMs
        val upcoming = if (upEnd >= 0L && upStart <= upEnd) {
            findEventsInRange(events, upStart, upEnd)
        } else {
            emptyList()
        }

        val activeKeys = highlighted.map { it.key }.toSet()

        return ScheduledNotes(
            currentTimeMs = currentTimeMs,
            highlightedNotes = highlighted,
            upcomingNotes = upcoming,
            activeKeys = activeKeys
        )
    }

    /**
     * GuideOverlayView 描画用の [GuideFrame] を生成する。
     *
     * @param events 演奏イベント一覧（timeMs昇順）
     * @param currentTimeMs 現在の楽曲再生位置（ミリ秒）。カウントダウン中は負の値（例: -1500ms）を取り得る。
     * @param leadTimeMs 先読み時間（ミリ秒、例: 300ms）
     * @param highlightTimeMs 事前ハイライト時間（ミリ秒、例: 200ms）
     * @param justThresholdMs ジャスト判定幅（ミリ秒、例: 80ms）
     * @param countdownText カウントダウン中テキスト（例: "3", "START" 等）
     * @return 描画に必要な情報を含む [GuideFrame]
     */
    fun scheduleFrame(
        events: List<NoteEvent>,
        currentTimeMs: Long,
        leadTimeMs: Long = PlaybackConfig.DEFAULT_LEAD_TIME_MS,
        highlightTimeMs: Long = PlaybackConfig.DEFAULT_HIGHLIGHT_TIME_MS,
        justThresholdMs: Long = FallingNoteCalculator.DEFAULT_JUST_THRESHOLD_MS,
        countdownText: String? = null
    ): GuideFrame {
        if (events.isEmpty()) {
            return GuideFrame(
                currentTimeMs = currentTimeMs,
                upcomingNotes = emptyList(),
                highlightedKeys = emptySet(),
                justKeys = emptySet(),
                countdownText = countdownText,
                leadTimeMs = leadTimeMs,
                highlightTimeMs = highlightTimeMs
            )
        }

        // 描画対象ノーツ範囲: 判定線を通過した直後のノーツに描画の余韻を残すため、
        // わずかに過去 (-5% leadTime、最低50ms) から未来の出現境界 (+leadTime) までを対象とする
        val pastMargin = (leadTimeMs * 0.05f).toLong().coerceAtLeast(50L)
        val startTimeMs = (currentTimeMs - pastMargin).coerceAtLeast(0L)
        val lookaheadMs = maxOf(leadTimeMs, highlightTimeMs)
        val endTimeMs = currentTimeMs + lookaheadMs

        // 全イベントの総当たりを避け、二分探索でO(log N)の範囲抽出
        val candidateNotes = if (endTimeMs >= 0L) {
            findEventsInRange(events, startTimeMs, endTimeMs)
        } else {
            emptyList()
        }

        val upcomingNotes = ArrayList<NoteEvent>()
        val highlightedKeys = HashSet<Int>()
        val justKeys = HashSet<Int>()
        val closestFutureNote = arrayOfNulls<NoteEvent>(GuideFrame.KEY_COUNT)
        val tempCircles = ArrayList<ApproachCircle>()
        val seenKeyTimes = HashSet<Long>()

        // 和音グループ (ChordGroup) 収集用: leadTimeMs 範囲内のノーツから同一 timeMs のユニークキーを集計
        val chordKeysByTime = LinkedHashMap<Long, MutableList<Int>>()
        val seenChordKeyTimes = HashSet<Long>()

        for (note in candidateNotes) {
            val progress = FallingNoteCalculator.calculateProgress(note.timeMs, currentTimeMs, leadTimeMs)
            if (FallingNoteCalculator.shouldDraw(progress)) {
                upcomingNotes.add(note)
            }

            if (FallingNoteCalculator.isHighlighted(note.timeMs, currentTimeMs, highlightTimeMs)) {
                highlightedKeys.add(note.key)
            }

            if (FallingNoteCalculator.isJustTiming(note.timeMs, currentTimeMs, justThresholdMs)) {
                justKeys.add(note.key)
            }

            // 既存互換: KEY_COUNT 範囲内の直近未来ノートを記録
            if (note.key in 0 until GuideFrame.KEY_COUNT) {
                if (note.timeMs in currentTimeMs..(currentTimeMs + highlightTimeMs)) {
                    if (closestFutureNote[note.key] == null) {
                        closestFutureNote[note.key] = note
                    }
                }
            }

            // 和音グループ対象ノーツ (leadTimeMs 範囲内、負数でない全キー対象)
            // 同一 (key, timeMs) の重複を除去し、同一時刻のキーを集約
            if (note.key >= 0 && note.timeMs in currentTimeMs..(currentTimeMs + leadTimeMs)) {
                val chordPairKey = (note.timeMs shl 16) or (note.key.toLong() and 0xFFFFL)
                if (seenChordKeyTimes.add(chordPairKey)) {
                    val keyList = chordKeysByTime.getOrPut(note.timeMs) { ArrayList() }
                    keyList.add(note.key)
                }
            }

            // アプローチサークル対象ノーツ (highlightTimeMs 範囲内)
            // 同一 key かつ同一 timeMs の重複のみ除外し、同一時刻の異なる key（和音）はすべて残す
            if (note.key >= 0 && note.timeMs in currentTimeMs..(currentTimeMs + highlightTimeMs)) {
                val pairKey = (note.timeMs shl 16) or (note.key.toLong() and 0xFFFFL)
                if (seenKeyTimes.add(pairKey)) {
                    val remaining = note.timeMs - currentTimeMs
                    val circleProgress = if (highlightTimeMs > 0L) {
                        (1f - remaining.toFloat() / highlightTimeMs.toFloat()).coerceIn(0f, 1f)
                    } else {
                        1f
                    }
                    tempCircles.add(ApproachCircle(key = note.key, progress = circleProgress))
                }
            }
        }

        // キー数が2以上の和音グループのみ抽出し、timeMs 降順（遠い未来 -> 直近）でソート
        val chordGroups = ArrayList<ChordGroup>()
        for ((timeMs, keys) in chordKeysByTime) {
            if (keys.size >= 2) {
                keys.sort()
                chordGroups.add(ChordGroup(timeMs = timeMs, keys = keys))
            }
        }
        chordGroups.sortByDescending { it.timeMs }

        // キーごとの連続サークル総数を集計
        val keyRepeatCounts = HashMap<Int, Int>()
        for (circle in tempCircles) {
            keyRepeatCounts[circle.key] = (keyRepeatCounts[circle.key] ?: 0) + 1
        }

        // 各キーにおける直近未来ノートの timeMs と tempCircles 内の最初のインデックスを特定
        val firstIndexByKey = HashMap<Int, Int>()
        val firstFutureTimeByKey = HashMap<Int, Long>()
        for (note in candidateNotes) {
            if (note.key >= 0 && note.timeMs in currentTimeMs..(currentTimeMs + highlightTimeMs)) {
                if (!firstFutureTimeByKey.containsKey(note.key)) {
                    firstFutureTimeByKey[note.key] = note.timeMs
                }
            }
        }
        for (i in 0 until tempCircles.size) {
            val k = tempCircles[i].key
            if (!firstIndexByKey.containsKey(k)) {
                firstIndexByKey[k] = i
            }
        }

        // 過去 highlight 範囲のイベントから各キーの最後のノート時刻を取得（現在時刻ちょうどを含めない）
        val lastPastTimeByKey = HashMap<Int, Long>()
        val pastRangeEnd = currentTimeMs - 1L
        val pastRangeStart = (currentTimeMs - highlightTimeMs).coerceAtLeast(0L)
        if (pastRangeEnd >= pastRangeStart) {
            val pastEvents = findEventsInRange(events, pastRangeStart, pastRangeEnd)
            for (pNote in pastEvents) {
                if (pNote.key >= 0) {
                    lastPastTimeByKey[pNote.key] = pNote.timeMs
                }
            }
        }

        // アプローチサークル（縮小タイミング円）の進行度 (0.0: 開始 〜 1.0: ジャスト打鍵) を算出 (既存互換)
        val keyHighlightProgress = FloatArray(GuideFrame.KEY_COUNT) { -1.0f }
        for (k in 0 until GuideFrame.KEY_COUNT) {
            val note = closestFutureNote[k]
            if (note != null) {
                val remaining = note.timeMs - currentTimeMs
                val progress = if (highlightTimeMs > 0L) {
                    (1f - remaining.toFloat() / highlightTimeMs.toFloat()).coerceIn(0f, 1f)
                } else {
                    1f
                }
                keyHighlightProgress[k] = progress
            }
        }

        // candidateNotes は時系列昇順（直近ノーツが先、未来ノーツが後）のため、
        // tempCircles は直近（progress大）から未来（progress小）の順で追加されている。
        // progress が小さい（遠い未来・大きい円）ものから先に描画し、
        // progress が大きい（直近・小さい円）ものを最後に描画（最前面に重ねる）するため、
        // 毎フレームのソートを避け O(N) の逆順配置で順序を決定的に保証する。
        val approachCircles = ArrayList<ApproachCircle>(tempCircles.size)
        for (i in tempCircles.lastIndex downTo 0) {
            val circle = tempCircles[i]
            val isClosestNoteForKey = firstIndexByKey[circle.key] == i

            val remainingCount: Int
            val showRepeatBadge: Boolean

            if (isClosestNoteForKey) {
                val count = keyRepeatCounts[circle.key] ?: 1
                if (count >= 2) {
                    remainingCount = count
                    showRepeatBadge = true
                } else {
                    remainingCount = 1
                    val prevTime = lastPastTimeByKey[circle.key]
                    val futureTime = firstFutureTimeByKey[circle.key]
                    showRepeatBadge = prevTime != null && futureTime != null &&
                            (futureTime - prevTime <= highlightTimeMs)
                }
            } else {
                remainingCount = 1
                showRepeatBadge = false
            }

            approachCircles.add(
                ApproachCircle(
                    key = circle.key,
                    progress = circle.progress,
                    remainingCount = remainingCount,
                    showRepeatBadge = showRepeatBadge
                )
            )
        }

        return GuideFrame(
            currentTimeMs = currentTimeMs,
            upcomingNotes = upcomingNotes,
            highlightedKeys = highlightedKeys,
            justKeys = justKeys,
            countdownText = countdownText,
            leadTimeMs = leadTimeMs,
            highlightTimeMs = highlightTimeMs,
            keyHighlightProgress = keyHighlightProgress,
            approachCircles = approachCircles,
            chordGroups = chordGroups
        )
    }

    /**
     * 二分探索を用いて [startTimeMs, endTimeMs] の範囲に含まれるイベントのリストを抽出する。
     */
    fun findEventsInRange(
        events: List<NoteEvent>,
        startTimeMs: Long,
        endTimeMs: Long
    ): List<NoteEvent> {
        if (events.isEmpty() || startTimeMs > endTimeMs) return emptyList()

        val startIndex = lowerBound(events, startTimeMs)
        if (startIndex >= events.size) return emptyList()

        val endIndex = upperBound(events, endTimeMs)
        if (startIndex >= endIndex) return emptyList()

        return events.subList(startIndex, endIndex)
    }

    /**
     * event.timeMs >= targetTimeMs を満たす最小のインデックスを返す。
     */
    internal fun lowerBound(events: List<NoteEvent>, targetTimeMs: Long): Int {
        var low = 0
        var high = events.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (events[mid].timeMs >= targetTimeMs) {
                high = mid
            } else {
                low = mid + 1
            }
        }
        return low
    }

    /**
     * event.timeMs > targetTimeMs を満たす最小のインデックスを返す。
     */
    internal fun upperBound(events: List<NoteEvent>, targetTimeMs: Long): Int {
        var low = 0
        var high = events.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (events[mid].timeMs > targetTimeMs) {
                high = mid
            } else {
                low = mid + 1
            }
        }
        return low
    }
}

/**
 * スケジューリング結果。
 *
 * @param currentTimeMs 判定時の現在時刻（ミリ秒）
 * @param highlightedNotes 現在ハイライト対象のノート一覧
 * @param upcomingNotes 直近の先読みノート一覧
 * @param activeKeys 現在ハイライトすべきキーインデックス (0..14) のセット
 */
data class ScheduledNotes(
    val currentTimeMs: Long,
    val highlightedNotes: List<NoteEvent>,
    val upcomingNotes: List<NoteEvent>,
    val activeKeys: Set<Int>
)
