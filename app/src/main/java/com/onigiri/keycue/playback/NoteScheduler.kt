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

    companion object {
        /**
         * Human-played chord notes may have small onset differences.
         * Notes starting within this window from the first note of a chord
         * are treated as simultaneous for chord visualization.
         *
         * 前ノートとの差ではなく、Chord先頭時刻との差で判定する。
         */
        private const val CHORD_TIME_TOLERANCE_MS = 30L
    }

    private var cachedEventsRef: List<NoteEvent>? = null
    private var precomputedChordGroups: List<ChordGroup> = emptyList()
    private var precomputedKeyTimes: Array<LongArray> = Array(GuideFrame.KEY_COUNT) { LongArray(0) }

    /**
     * 楽曲の全演奏イベントを受け取り、和音グループおよび連打判定用の静的情報を事前計算する。
     *
     * 【入力契約】
     * - [events] は timeMs 昇順にソートされていること。
     * - [events] の内容は楽曲再生中に変更されないこと。
     *
     * 楽曲ロード時（OverlayService や PlaybackEngine）に呼び出すことで、
     * 毎フレームの scheduleFrame() での Map/Set 生成や O(N) 探索を排除し、O(log N) の抽出を実現します。
     *
     * @param events 演奏イベント一覧（timeMs昇順）
     */
    fun prepare(events: List<NoteEvent>) {
        cachedEventsRef = events
        if (events.isEmpty()) {
            precomputedChordGroups = emptyList()
            precomputedKeyTimes = Array(GuideFrame.KEY_COUNT) { LongArray(0) }
            return
        }

        val maxEventKey = events.maxOfOrNull { it.key } ?: 0
        val numKeys = maxOf(GuideFrame.KEY_COUNT, maxEventKey + 1)

        // 1. 和音グループ (ChordGroup) の事前計算
        // グループ先頭の時刻から CHORD_TIME_TOLERANCE_MS (30ms) 以内のノートを集約し、
        // 異なるキーが2つ以上ある場合のみ ChordGroup として昇順保持する。
        // （直前ノートとの差ではなく、グループ先頭時刻との差で判定して連鎖を防ぐ）
        val chordGroupsList = ArrayList<ChordGroup>()

        var i = 0
        val n = events.size
        while (i < n) {
            val chordStartTime = events[i].timeMs
            var j = i + 1
            // 直前ノートとの差ではなく、グループ先頭時刻との差で判定する
            while (j < n && events[j].timeMs - chordStartTime <= CHORD_TIME_TOLERANCE_MS) {
                j++
            }

            // 同一Chord候補内のユニークキーを抽出
            val uniqueKeys = ArrayList<Int>()
            for (k in i until j) {
                val key = events[k].key
                if (key >= 0 && !uniqueKeys.contains(key)) {
                    uniqueKeys.add(key)
                }
            }

            // 異なるキーが2つ以上ある場合のみChordGroupを生成（同一キー連打は除外）
            if (uniqueKeys.size >= 2) {
                uniqueKeys.sort()
                chordGroupsList.add(ChordGroup(timeMs = chordStartTime, keys = uniqueKeys.toList()))
                // 有効Chordとして確定したので、この候補範囲を消費
                i = j
            } else {
                // Chord不成立。
                // 後続イベントを新しい起点として再評価できるよう、先頭イベントだけ進める。
                i++
            }
        }

        // 2. キーごとの打鍵時刻一覧 (連打バッジ判定用)
        // 既存仕様 (repeatBadgeGroupingWindowMs = approachCircleLeadTimeMs) に従い、
        // Chord判定の許容幅には流用せず、各イベント本来の timeMs をキーごとに保持する。
        val keyTimesList = Array(numKeys) { ArrayList<Long>() }
        for (event in events) {
            val key = event.key
            if (key in 0 until numKeys) {
                val times = keyTimesList[key]
                if (times.isEmpty() || times[times.lastIndex] != event.timeMs) {
                    times.add(event.timeMs)
                }
            }
        }

        precomputedChordGroups = chordGroupsList
        precomputedKeyTimes = Array(numKeys) { k -> keyTimesList[k].toLongArray() }
    }

    /**
     * 現在時刻に基づいて、発光対象ノートおよび先読みノートを抽出する。
     *
     * @param events 演奏イベント一覧（timeMs昇順）
     * @param currentTimeMs 現在の楽曲再生位置（ミリ秒）
     * @param noteLeadTimeMs ノート先読み時間（ミリ秒、例: 300ms）
     * @param approachCircleLeadTimeMs タイミングサークル先読み時間（ミリ秒、例: 200ms）
     * @return [ScheduledNotes]
     */
    fun schedule(
        events: List<NoteEvent>,
        currentTimeMs: Long,
        noteLeadTimeMs: Long = PlaybackConfig.DEFAULT_NOTE_LEAD_TIME_MS,
        approachCircleLeadTimeMs: Long = PlaybackConfig.DEFAULT_APPROACH_CIRCLE_LEAD_TIME_MS
    ): ScheduledNotes {
        if (events.isEmpty()) {
            return ScheduledNotes(currentTimeMs, emptyList(), emptyList(), emptySet())
        }

        // 1. ハイライト範囲: [currentTimeMs - approachCircleLeadTimeMs, currentTimeMs + approachCircleLeadTimeMs]
        val hlStart = (currentTimeMs - approachCircleLeadTimeMs).coerceAtLeast(0L)
        val hlEnd = currentTimeMs + approachCircleLeadTimeMs
        val highlighted = if (hlEnd >= 0L && hlStart <= hlEnd) {
            findEventsInRange(events, hlStart, hlEnd)
        } else {
            emptyList()
        }

        // 2. 先読み範囲: (currentTimeMs, currentTimeMs + noteLeadTimeMs]
        val upStart = (currentTimeMs + 1L).coerceAtLeast(0L)
        val upEnd = currentTimeMs + noteLeadTimeMs
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
     * @param noteLeadTimeMs ノート先読み時間（ミリ秒、例: 300ms）
     * @param approachCircleLeadTimeMs タイミングサークル先読み時間（ミリ秒、例: 200ms）
     * @param justThresholdMs ジャスト判定幅（ミリ秒、例: 80ms）
     * @param countdownText カウントダウン中テキスト（例: "3", "START" 等）
     * @return 描画に必要な情報を含む [GuideFrame]
     */
    fun scheduleFrame(
        events: List<NoteEvent>,
        currentTimeMs: Long,
        noteLeadTimeMs: Long = PlaybackConfig.DEFAULT_NOTE_LEAD_TIME_MS,
        approachCircleLeadTimeMs: Long = PlaybackConfig.DEFAULT_APPROACH_CIRCLE_LEAD_TIME_MS,
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
                noteLeadTimeMs = noteLeadTimeMs,
                approachCircleLeadTimeMs = approachCircleLeadTimeMs
            )
        }

        // 安全フォールバック: prepare が未実行または異なるイベント列の場合は自動で準備
        if (events !== cachedEventsRef) {
            prepare(events)
        }

        // 描画対象ノート範囲: 判定線を通過した直後のノートに描画の余韻を残すため、
        // わずかに過去 (-5% noteLeadTime、最低50ms) から未来の出現境界までを対象とする。
        // Schedulerが生成する各視覚要素のうち、最も長い先読み時間までイベントを探索する。
        val pastMargin = (noteLeadTimeMs * 0.05f).toLong().coerceAtLeast(50L)
        val startTimeMs = (currentTimeMs - pastMargin).coerceAtLeast(0L)
        val lookaheadMs = maxOf(noteLeadTimeMs, approachCircleLeadTimeMs)
        val endTimeMs = currentTimeMs + lookaheadMs

        // 全イベントの総当たりを避け、二分探索でO(log N)の範囲抽出
        val candidateNotes = if (endTimeMs >= 0L) {
            findEventsInRange(events, startTimeMs, endTimeMs)
        } else {
            emptyList()
        }

        val numKeys = precomputedKeyTimes.size
        val upcomingNotes = ArrayList<NoteEvent>(candidateNotes.size)
        val highlightedKeys = HashSet<Int>()
        val justKeys = HashSet<Int>()
        val closestFutureNote = arrayOfNulls<NoteEvent>(GuideFrame.KEY_COUNT)
        val tempCircles = ArrayList<ApproachCircle>()

        var lastCircleTimeMs = -1L
        var circleKeyMask = 0L

        for (note in candidateNotes) {
            val progress = FallingNoteCalculator.calculateProgress(note.timeMs, currentTimeMs, noteLeadTimeMs)
            if (FallingNoteCalculator.shouldDraw(progress)) {
                upcomingNotes.add(note)
            }

            if (FallingNoteCalculator.isHighlighted(note.timeMs, currentTimeMs, approachCircleLeadTimeMs)) {
                highlightedKeys.add(note.key)
            }

            if (FallingNoteCalculator.isJustTiming(note.timeMs, currentTimeMs, justThresholdMs)) {
                justKeys.add(note.key)
            }

            // 既存互換: KEY_COUNT 範囲内の直近未来ノートを記録
            if (note.key in 0 until GuideFrame.KEY_COUNT) {
                if (note.timeMs in currentTimeMs..(currentTimeMs + approachCircleLeadTimeMs)) {
                    if (closestFutureNote[note.key] == null) {
                        closestFutureNote[note.key] = note
                    }
                }
            }

            // タイミングサークル対象ノート (approachCircleLeadTimeMs 範囲内、負数でない全キー対象)
            // 同一 key かつ同一 timeMs の重複のみ除外し、同一時刻の異なる key（和音）はすべて残す
            if (note.key >= 0 && note.timeMs in currentTimeMs..(currentTimeMs + approachCircleLeadTimeMs)) {
                if (note.timeMs != lastCircleTimeMs) {
                    lastCircleTimeMs = note.timeMs
                    circleKeyMask = 0L
                }
                val isDuplicate = if (note.key < 64) {
                    val bit = 1L shl note.key
                    val dup = (circleKeyMask and bit) != 0L
                    if (!dup) circleKeyMask = circleKeyMask or bit
                    dup
                } else {
                    false
                }
                if (!isDuplicate) {
                    val remaining = note.timeMs - currentTimeMs
                    val circleProgress = if (approachCircleLeadTimeMs > 0L) {
                        (1f - remaining.toFloat() / approachCircleLeadTimeMs.toFloat()).coerceIn(0f, 1f)
                    } else {
                        1f
                    }
                    tempCircles.add(ApproachCircle(key = note.key, progress = circleProgress))
                }
            }
        }

        // 事前計算済み ChordGroup から [currentTimeMs, currentTimeMs + noteLeadTimeMs] 範囲を二分探索で抽出
        // 既存仕様に従い timeMs 降順（遠い未来 -> 直近）で格納
        val chordStart = lowerBoundChord(precomputedChordGroups, currentTimeMs)
        val chordEnd = upperBoundChord(precomputedChordGroups, currentTimeMs + noteLeadTimeMs)
        val chordCount = (chordEnd - chordStart).coerceAtLeast(0)
        val chordGroups = ArrayList<ChordGroup>(chordCount)
        for (idx in chordEnd - 1 downTo chordStart) {
            chordGroups.add(precomputedChordGroups[idx])
        }

        // キーごとの連続サークル総数を集計 & tempCircles 内の最初の出現インデックス特定
        val keyRepeatCounts = IntArray(numKeys)
        val firstIndexByKey = IntArray(numKeys) { -1 }
        for (i in 0 until tempCircles.size) {
            val k = tempCircles[i].key
            if (k in 0 until numKeys) {
                keyRepeatCounts[k]++
                if (firstIndexByKey[k] == -1) {
                    firstIndexByKey[k] = i
                }
            }
        }

        // --- 連打バッジのグルーピング時間幅と直前ノート判定 ---
        // 連打バッジは同一キーの Approach Circle が重なる場合の UI 補助であり、approachCircleLeadTimeMs に従う。
        // 事前計算済み precomputedKeyTimes から二分探索で過去ノート関係を O(1)〜O(log N) で解決
        val repeatBadgeGroupingWindowMs = approachCircleLeadTimeMs
        val hasValidRepeatPrevNote = BooleanArray(numKeys)
        for (k in 0 until numKeys) {
            val times = precomputedKeyTimes[k]
            if (times.isEmpty()) continue
            val sIdx = lowerBoundLong(times, currentTimeMs)
            if (sIdx in times.indices && times[sIdx] <= currentTimeMs + approachCircleLeadTimeMs) {
                if (sIdx > 0) {
                    val prevTime = times[sIdx - 1]
                    val futureTime = times[sIdx]
                    if (prevTime >= currentTimeMs - repeatBadgeGroupingWindowMs &&
                        futureTime - prevTime <= repeatBadgeGroupingWindowMs
                    ) {
                        hasValidRepeatPrevNote[k] = true
                    }
                }
            }
        }

        // アプローチサークル（縮小タイミング円）の進行度 (0.0: 開始 〜 1.0: ジャスト打鍵) を算出 (既存互換)
        val keyHighlightProgress = FloatArray(GuideFrame.KEY_COUNT) { -1.0f }
        for (k in 0 until GuideFrame.KEY_COUNT) {
            val note = closestFutureNote[k]
            if (note != null) {
                val remaining = note.timeMs - currentTimeMs
                val progress = if (approachCircleLeadTimeMs > 0L) {
                    (1f - remaining.toFloat() / approachCircleLeadTimeMs.toFloat()).coerceIn(0f, 1f)
                } else {
                    1f
                }
                keyHighlightProgress[k] = progress
            }
        }

        // candidateNotes は時系列昇順（直近ノートが先、未来ノートが後）のため、
        // tempCircles は直近（progress大）から未来（progress小）の順で追加されている。
        // progress が小さい（遠い未来・大きい円）ものから先に描画し、
        // progress が大きい（直近・小さい円）ものを最後に描画（最前面に重ねる）するため、
        // 毎フレームのソートを避け O(N) の逆順配置で順序を決定的に保証する。
        val approachCircles = ArrayList<ApproachCircle>(tempCircles.size)
        for (i in tempCircles.lastIndex downTo 0) {
            val circle = tempCircles[i]
            val k = circle.key
            val isClosestNoteForKey = (k in 0 until numKeys && firstIndexByKey[k] == i)

            val remainingCount: Int
            val showRepeatBadge: Boolean

            if (isClosestNoteForKey) {
                val count = keyRepeatCounts[k]
                if (count >= 2) {
                    remainingCount = count
                    showRepeatBadge = true
                } else {
                    remainingCount = 1
                    showRepeatBadge = hasValidRepeatPrevNote[k]
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
            noteLeadTimeMs = noteLeadTimeMs,
            approachCircleLeadTimeMs = approachCircleLeadTimeMs,
            keyHighlightProgress = keyHighlightProgress,
            approachCircles = approachCircles,
            chordGroups = chordGroups
        )
    }

    private fun lowerBoundChord(chords: List<ChordGroup>, targetTimeMs: Long): Int {
        var low = 0
        var high = chords.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (chords[mid].timeMs >= targetTimeMs) {
                high = mid
            } else {
                low = mid + 1
            }
        }
        return low
    }

    private fun upperBoundChord(chords: List<ChordGroup>, targetTimeMs: Long): Int {
        var low = 0
        var high = chords.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (chords[mid].timeMs > targetTimeMs) {
                high = mid
            } else {
                low = mid + 1
            }
        }
        return low
    }

    private fun lowerBoundLong(array: LongArray, target: Long): Int {
        var low = 0
        var high = array.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (array[mid] >= target) {
                high = mid
            } else {
                low = mid + 1
            }
        }
        return low
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
