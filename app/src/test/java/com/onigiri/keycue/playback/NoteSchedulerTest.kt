package com.onigiri.keycue.playback

import com.onigiri.keycue.model.NoteEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [NoteScheduler] の単体テスト。
 * 二分探索によるハイライト判定、先読み、和音、境界条件を検証する。
 */
class NoteSchedulerTest {

    private lateinit var scheduler: NoteScheduler

    @Before
    fun setUp() {
        scheduler = NoteScheduler()
    }

    @Test
    fun schedule_singleNote_highlightsAtTiming() {
        val events = listOf(NoteEvent(timeMs = 1000L, key = 5))

        // ジャストタイミング (1000ms)
        val resultExact = scheduler.schedule(
            events = events,
            currentTimeMs = 1000L,
            leadTimeMs = 700L,
            highlightTimeMs = 100L
        )
        assertTrue(resultExact.activeKeys.contains(5))
        assertEquals(1, resultExact.highlightedNotes.size)
        assertTrue(resultExact.upcomingNotes.isEmpty())

        // highlight範囲内 (950ms)
        val resultBefore = scheduler.schedule(
            events = events,
            currentTimeMs = 950L,
            leadTimeMs = 700L,
            highlightTimeMs = 100L
        )
        assertTrue(resultBefore.activeKeys.contains(5))

        // highlight範囲外・先読み範囲内 (800ms)
        val resultUpcoming = scheduler.schedule(
            events = events,
            currentTimeMs = 800L,
            leadTimeMs = 700L,
            highlightTimeMs = 100L
        )
        assertFalse(resultUpcoming.activeKeys.contains(5))
        assertEquals(1, resultUpcoming.upcomingNotes.size)
        assertEquals(5, resultUpcoming.upcomingNotes.first().key)

        // 通過後 (1200ms)
        val resultPast = scheduler.schedule(
            events = events,
            currentTimeMs = 1200L,
            leadTimeMs = 700L,
            highlightTimeMs = 100L
        )
        assertFalse(resultPast.activeKeys.contains(5))
        assertTrue(resultPast.upcomingNotes.isEmpty())
    }

    @Test
    fun schedule_chords_highlightsAllSimultaneousKeys() {
        // 同時刻 1000ms に 3 つのキーが同時に打鍵される和音
        val events = listOf(
            NoteEvent(timeMs = 1000L, key = 0),
            NoteEvent(timeMs = 1000L, key = 4),
            NoteEvent(timeMs = 1000L, key = 12)
        )

        val result = scheduler.schedule(
            events = events,
            currentTimeMs = 1000L,
            leadTimeMs = 700L,
            highlightTimeMs = 50L
        )

        assertEquals(3, result.activeKeys.size)
        assertTrue(result.activeKeys.contains(0))
        assertTrue(result.activeKeys.contains(4))
        assertTrue(result.activeKeys.contains(12))
        assertEquals(3, result.highlightedNotes.size)
    }

    @Test
    fun schedule_highlightTimeRange_exactBoundaries() {
        val events = listOf(NoteEvent(timeMs = 1000L, key = 2))
        val hl = 100L // 範囲: 900ms .. 1100ms

        // 下限境界ぴったり (900ms)
        val atLowerBound = scheduler.schedule(events, currentTimeMs = 900L, highlightTimeMs = hl)
        assertTrue(atLowerBound.activeKeys.contains(2))

        // 下限境界の直前 (899ms)
        val belowLowerBound = scheduler.schedule(events, currentTimeMs = 899L, highlightTimeMs = hl)
        assertFalse(belowLowerBound.activeKeys.contains(2))

        // 上限境界ぴったり (1100ms)
        val atUpperBound = scheduler.schedule(events, currentTimeMs = 1100L, highlightTimeMs = hl)
        assertTrue(atUpperBound.activeKeys.contains(2))

        // 上限境界の直後 (1101ms)
        val aboveUpperBound = scheduler.schedule(events, currentTimeMs = 1101L, highlightTimeMs = hl)
        assertFalse(aboveUpperBound.activeKeys.contains(2))
    }

    @Test
    fun schedule_leadTimeRange_exactBoundaries() {
        val events = listOf(NoteEvent(timeMs = 1700L, key = 7))
        val leadTime = 700L

        // currentTime = 1000L -> [1001, 1700] に含まれる
        val inside = scheduler.schedule(events, currentTimeMs = 1000L, leadTimeMs = leadTime, highlightTimeMs = 50L)
        assertEquals(1, inside.upcomingNotes.size)

        // currentTime = 999L -> [1000, 1699] に含まれない
        val outside = scheduler.schedule(events, currentTimeMs = 999L, leadTimeMs = leadTime, highlightTimeMs = 50L)
        assertTrue(outside.upcomingNotes.isEmpty())
    }

    @Test
    fun schedule_songStart_atZeroMs() {
        val events = listOf(
            NoteEvent(timeMs = 0L, key = 1),
            NoteEvent(timeMs = 300L, key = 2),
            NoteEvent(timeMs = 600L, key = 3)
        )

        val result = scheduler.schedule(
            events = events,
            currentTimeMs = 0L,
            leadTimeMs = 500L,
            highlightTimeMs = 50L
        )

        assertTrue(result.activeKeys.contains(1))
        assertEquals(1, result.upcomingNotes.size)
        assertEquals(2, result.upcomingNotes.first().key)
    }

    @Test
    fun schedule_songEnd_pastAllNotes() {
        val events = listOf(
            NoteEvent(timeMs = 1000L, key = 0),
            NoteEvent(timeMs = 2000L, key = 14)
        )

        val result = scheduler.schedule(
            events = events,
            currentTimeMs = 5000L,
            leadTimeMs = 700L,
            highlightTimeMs = 100L
        )

        assertTrue(result.activeKeys.isEmpty())
        assertTrue(result.highlightedNotes.isEmpty())
        assertTrue(result.upcomingNotes.isEmpty())
    }

    @Test
    fun lowerBound_and_upperBound_binarySearchAccuracy() {
        val events = (0..100).map { NoteEvent(timeMs = it * 100L, key = it % 15) }

        // [250L, 550L] の範囲のイベントを抽出
        val inRange = scheduler.findEventsInRange(events, 250L, 550L)

        // 300L, 400L, 500L の3件
        assertEquals(3, inRange.size)
        assertEquals(300L, inRange[0].timeMs)
        assertEquals(400L, inRange[1].timeMs)
        assertEquals(500L, inRange[2].timeMs)
    }

    @Test
    fun scheduleFrame_approachCircleProgress_start_middle_event() {
        val events = listOf(NoteEvent(timeMs = 1000L, key = 3))
        val hlTime = 500L

        // 1. highlight開始時 (500ms): progress ≈ 0.0f
        val frameStart = scheduler.scheduleFrame(events, currentTimeMs = 500L, highlightTimeMs = hlTime)
        assertEquals(0.0f, frameStart.keyHighlightProgress[3], 0.01f)

        // 2. 中間 (750ms): progress ≈ 0.5f
        val frameMid = scheduler.scheduleFrame(events, currentTimeMs = 750L, highlightTimeMs = hlTime)
        assertEquals(0.5f, frameMid.keyHighlightProgress[3], 0.01f)

        // 3. event時 (1000ms): progress ≈ 1.0f
        val frameExact = scheduler.scheduleFrame(events, currentTimeMs = 1000L, highlightTimeMs = hlTime)
        assertEquals(1.0f, frameExact.keyHighlightProgress[3], 0.01f)

        // 4. 対象なしキー (key 0 など) は -1.0f
        assertEquals(-1.0f, frameStart.keyHighlightProgress[0], 0.001f)
    }

    @Test
    fun scheduleFrame_zeroHighlightTime_handledSafely() {
        val events = listOf(NoteEvent(timeMs = 1000L, key = 3))
        // highlightTimeMs = 0L でもゼロ除算せず安全に処理
        val frame = scheduler.scheduleFrame(events, currentTimeMs = 1000L, highlightTimeMs = 0L)
        assertEquals(1.0f, frame.keyHighlightProgress[3], 0.01f)
    }

    @Test
    fun scheduleFrame_consecutiveNotesOnSameKey_picksClosestFutureNote() {
        // 同じ key 3 に 1000ms と 1200ms
        val events = listOf(
            NoteEvent(timeMs = 1000L, key = 3),
            NoteEvent(timeMs = 1200L, key = 3)
        )
        val hlTime = 500L

        // 現在 950ms: 1000ms 側の progress (remaining = 50ms -> 1 - 50/500 = 0.9f)
        val frame1 = scheduler.scheduleFrame(events, currentTimeMs = 950L, highlightTimeMs = hlTime)
        assertEquals(0.9f, frame1.keyHighlightProgress[3], 0.01f)

        // 1000ms 通過後 (1050ms): 1200ms 側へ切り替え (remaining = 150ms -> 1 - 150/500 = 0.7f)
        val frame2 = scheduler.scheduleFrame(events, currentTimeMs = 1050L, highlightTimeMs = hlTime)
        assertEquals(0.7f, frame2.keyHighlightProgress[3], 0.01f)
    }

    @Test
    fun scheduleFrame_noTargetNotes_returnsMinusOne() {
        val events = listOf(NoteEvent(timeMs = 1000L, key = 3))
        // currentTime = 2000L (過去のノートのみ)
        val frame = scheduler.scheduleFrame(events, currentTimeMs = 2000L, highlightTimeMs = 500L)
        for (k in 0 until GuideFrame.KEY_COUNT) {
            assertEquals(-1.0f, frame.keyHighlightProgress[k], 0.001f)
        }
    }
}
