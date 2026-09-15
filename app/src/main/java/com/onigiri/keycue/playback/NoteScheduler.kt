package com.onigiri.keycue.playback

import com.onigiri.keycue.model.NoteEvent

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
        leadTimeMs: Long = 700L,
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
     * @param leadTimeMs 先読み時間（ミリ秒、例: 700ms）
     * @param highlightTimeMs 事前ハイライト時間（ミリ秒、例: 500ms）
     * @param justThresholdMs ジャスト判定幅（ミリ秒、例: 80ms）
     * @param countdownText カウントダウン中テキスト（例: "3", "START" 等）
     * @return 描画に必要な情報を含む [GuideFrame]
     */
    fun scheduleFrame(
        events: List<NoteEvent>,
        currentTimeMs: Long,
        leadTimeMs: Long = 700L,
        highlightTimeMs: Long = 500L,
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
                leadTimeMs = leadTimeMs
            )
        }

        // 描画対象ノーツ範囲: 判定線を通過した直後のノーツに描画の余韻を残すため、
        // わずかに過去 (-5% leadTime、最低50ms) から未来の出現境界 (+leadTime) までを対象とする
        val pastMargin = (leadTimeMs * 0.05f).toLong().coerceAtLeast(50L)
        val startTimeMs = (currentTimeMs - pastMargin).coerceAtLeast(0L)
        val endTimeMs = currentTimeMs + leadTimeMs

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

            // 各キーについて、現在時刻から見て直近未来（ハイライト時間以内）のノートを記録
            // （音ゲー風アプローチサークルの縮小率計算に使用）
            if (note.key in 0 until GuideFrame.KEY_COUNT) {
                if (note.timeMs >= currentTimeMs && note.timeMs <= currentTimeMs + highlightTimeMs) {
                    if (closestFutureNote[note.key] == null) {
                        closestFutureNote[note.key] = note
                    }
                }
            }
        }

        // アプローチサークル（縮小タイミング円）の進行度 (0.0: 開始 〜 1.0: ジャスト打鍵) を算出
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

        return GuideFrame(
            currentTimeMs = currentTimeMs,
            upcomingNotes = upcomingNotes,
            highlightedKeys = highlightedKeys,
            justKeys = justKeys,
            countdownText = countdownText,
            leadTimeMs = leadTimeMs,
            keyHighlightProgress = keyHighlightProgress
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
