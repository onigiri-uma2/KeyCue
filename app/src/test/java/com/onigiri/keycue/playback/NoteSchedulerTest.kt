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
            noteLeadTimeMs = 700L,
            approachCircleLeadTimeMs = 100L
        )
        assertTrue(resultExact.activeKeys.contains(5))
        assertEquals(1, resultExact.highlightedNotes.size)
        assertTrue(resultExact.upcomingNotes.isEmpty())

        // highlight範囲内 (950ms)
        val resultBefore = scheduler.schedule(
            events = events,
            currentTimeMs = 950L,
            noteLeadTimeMs = 700L,
            approachCircleLeadTimeMs = 100L
        )
        assertTrue(resultBefore.activeKeys.contains(5))

        // highlight範囲外・先読み範囲内 (800ms)
        val resultUpcoming = scheduler.schedule(
            events = events,
            currentTimeMs = 800L,
            noteLeadTimeMs = 700L,
            approachCircleLeadTimeMs = 100L
        )
        assertFalse(resultUpcoming.activeKeys.contains(5))
        assertEquals(1, resultUpcoming.upcomingNotes.size)
        assertEquals(5, resultUpcoming.upcomingNotes.first().key)

        // 通過後 (1200ms)
        val resultPast = scheduler.schedule(
            events = events,
            currentTimeMs = 1200L,
            noteLeadTimeMs = 700L,
            approachCircleLeadTimeMs = 100L
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
            noteLeadTimeMs = 700L,
            approachCircleLeadTimeMs = 50L
        )

        assertEquals(3, result.activeKeys.size)
        assertTrue(result.activeKeys.contains(0))
        assertTrue(result.activeKeys.contains(4))
        assertTrue(result.activeKeys.contains(12))
        assertEquals(3, result.highlightedNotes.size)
    }

    @Test
    fun schedule_approachCircleLeadTimeRange_exactBoundaries() {
        val events = listOf(NoteEvent(timeMs = 1000L, key = 2))
        val hl = 100L // 範囲: 900ms .. 1100ms

        // 下限境界ぴったり (900ms)
        val atLowerBound = scheduler.schedule(events, currentTimeMs = 900L, approachCircleLeadTimeMs = hl)
        assertTrue(atLowerBound.activeKeys.contains(2))

        // 下限境界の直前 (899ms)
        val belowLowerBound = scheduler.schedule(events, currentTimeMs = 899L, approachCircleLeadTimeMs = hl)
        assertFalse(belowLowerBound.activeKeys.contains(2))

        // 上限境界ぴったり (1100ms)
        val atUpperBound = scheduler.schedule(events, currentTimeMs = 1100L, approachCircleLeadTimeMs = hl)
        assertTrue(atUpperBound.activeKeys.contains(2))

        // 上限境界の直後 (1101ms)
        val aboveUpperBound = scheduler.schedule(events, currentTimeMs = 1101L, approachCircleLeadTimeMs = hl)
        assertFalse(aboveUpperBound.activeKeys.contains(2))
    }

    @Test
    fun schedule_noteLeadTimeRange_exactBoundaries() {
        val events = listOf(NoteEvent(timeMs = 1700L, key = 7))
        val noteLeadTime = 700L

        // currentTime = 1000L -> [1001, 1700] に含まれる
        val inside = scheduler.schedule(events, currentTimeMs = 1000L, noteLeadTimeMs = noteLeadTime, approachCircleLeadTimeMs = 50L)
        assertEquals(1, inside.upcomingNotes.size)

        // currentTime = 999L -> [1000, 1699] に含まれない
        val outside = scheduler.schedule(events, currentTimeMs = 999L, noteLeadTimeMs = noteLeadTime, approachCircleLeadTimeMs = 50L)
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
            noteLeadTimeMs = 500L,
            approachCircleLeadTimeMs = 50L
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
            noteLeadTimeMs = 700L,
            approachCircleLeadTimeMs = 100L
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
        val frameStart = scheduler.scheduleFrame(events, currentTimeMs = 500L, approachCircleLeadTimeMs = hlTime)
        assertEquals(0.0f, frameStart.keyHighlightProgress[3], 0.01f)

        // 2. 中間 (750ms): progress ≈ 0.5f
        val frameMid = scheduler.scheduleFrame(events, currentTimeMs = 750L, approachCircleLeadTimeMs = hlTime)
        assertEquals(0.5f, frameMid.keyHighlightProgress[3], 0.01f)

        // 3. event時 (1000ms): progress ≈ 1.0f
        val frameExact = scheduler.scheduleFrame(events, currentTimeMs = 1000L, approachCircleLeadTimeMs = hlTime)
        assertEquals(1.0f, frameExact.keyHighlightProgress[3], 0.01f)

        // 4. 対象なしキー (key 0 など) は -1.0f
        assertEquals(-1.0f, frameStart.keyHighlightProgress[0], 0.001f)
    }

    @Test
    fun scheduleFrame_zeroHighlightTime_handledSafely() {
        val events = listOf(NoteEvent(timeMs = 1000L, key = 3))
        // approachCircleLeadTimeMs = 0L でもゼロ除算せず安全に処理
        val frame = scheduler.scheduleFrame(events, currentTimeMs = 1000L, approachCircleLeadTimeMs = 0L)
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
        val frame1 = scheduler.scheduleFrame(events, currentTimeMs = 950L, approachCircleLeadTimeMs = hlTime)
        assertEquals(0.9f, frame1.keyHighlightProgress[3], 0.01f)

        // 1000ms 通過後 (1050ms): 1200ms 側へ切り替え (remaining = 150ms -> 1 - 150/500 = 0.7f)
        val frame2 = scheduler.scheduleFrame(events, currentTimeMs = 1050L, approachCircleLeadTimeMs = hlTime)
        assertEquals(0.7f, frame2.keyHighlightProgress[3], 0.01f)
    }

    @Test
    fun scheduleFrame_noTargetNotes_returnsMinusOne() {
        val events = listOf(NoteEvent(timeMs = 1000L, key = 3))
        // currentTime = 2000L (過去のノートのみ)
        val frame = scheduler.scheduleFrame(events, currentTimeMs = 2000L, approachCircleLeadTimeMs = 500L)
        for (k in 0 until GuideFrame.KEY_COUNT) {
            assertEquals(-1.0f, frame.keyHighlightProgress[k], 0.001f)
        }
    }

    @Test
    fun scheduleFrame_consecutiveThreeNotesOnSameKey_returnsAllThreeApproachCircles() {
        // 同一 key 3 に 3連打: 1000ms, 1100ms, 1200ms
        val events = listOf(
            NoteEvent(timeMs = 1000L, key = 3),
            NoteEvent(timeMs = 1100L, key = 3),
            NoteEvent(timeMs = 1200L, key = 3)
        )
        val hlTime = 500L
        val frame = scheduler.scheduleFrame(events, currentTimeMs = 800L, approachCircleLeadTimeMs = hlTime)

        // 3個すべてのアプローチサークルが返ること
        assertEquals(3, frame.approachCircles.size)

        // progress昇順（遠い未来・大きい円 -> 直近・小さい円）の順序保証を検証
        // 1200ms: remaining = 400ms -> 1 - 400/500 = 0.2f
        // 1100ms: remaining = 300ms -> 1 - 300/500 = 0.4f
        // 1000ms: remaining = 200ms -> 1 - 200/500 = 0.6f
        assertEquals(3, frame.approachCircles[0].key)
        assertEquals(0.2f, frame.approachCircles[0].progress, 0.01f)
        assertEquals(1, frame.approachCircles[0].remainingCount)

        assertEquals(3, frame.approachCircles[1].key)
        assertEquals(0.4f, frame.approachCircles[1].progress, 0.01f)
        assertEquals(1, frame.approachCircles[1].remainingCount)

        // 最前面（直近）のサークルにキーの合計連続打数 3 が設定される
        assertEquals(3, frame.approachCircles[2].key)
        assertEquals(0.6f, frame.approachCircles[2].progress, 0.01f)
        assertEquals(3, frame.approachCircles[2].remainingCount)
    }

    @Test
    fun scheduleFrame_firstNotePassed_remainingTwoNotesContinueWithCorrectProgress() {
        val events = listOf(
            NoteEvent(timeMs = 1000L, key = 3),
            NoteEvent(timeMs = 1100L, key = 3),
            NoteEvent(timeMs = 1200L, key = 3)
        )
        val hlTime = 500L

        // 1000ms 通過後 (1050ms): 最初のノートは消え、残り2個が正しいprogressで継続
        val frame = scheduler.scheduleFrame(events, currentTimeMs = 1050L, approachCircleLeadTimeMs = hlTime)

        assertEquals(2, frame.approachCircles.size)

        // 1200ms: remaining = 150ms -> 1 - 150/500 = 0.7f
        assertEquals(3, frame.approachCircles[0].key)
        assertEquals(0.7f, frame.approachCircles[0].progress, 0.01f)
        assertEquals(1, frame.approachCircles[0].remainingCount)

        // 1100ms: remaining = 50ms -> 1 - 50/500 = 0.9f、直近の残り打数は 2 にカウントダウン
        assertEquals(3, frame.approachCircles[1].key)
        assertEquals(0.9f, frame.approachCircles[1].progress, 0.01f)
        assertEquals(2, frame.approachCircles[1].remainingCount)
    }

    @Test
    fun scheduleFrame_duplicateNotesOnSameKeyAndSameTime_returnsSingleCircle() {
        // 同一 key 2・同一 timeMs 1000L の重複ノート
        val events = listOf(
            NoteEvent(timeMs = 1000L, key = 2),
            NoteEvent(timeMs = 1000L, key = 2)
        )
        val frame = scheduler.scheduleFrame(events, currentTimeMs = 800L, approachCircleLeadTimeMs = 500L)

        // 重複は1 circleになること
        assertEquals(1, frame.approachCircles.size)
        assertEquals(2, frame.approachCircles[0].key)
        assertEquals(0.6f, frame.approachCircles[0].progress, 0.01f)
    }

    @Test
    fun scheduleFrame_sameTimeDifferentKeys_generatesCirclesForEachKey() {
        // 同一 timeMs 1000L で異なる key の和音 (key 1, 5, 12)
        val events = listOf(
            NoteEvent(timeMs = 1000L, key = 1),
            NoteEvent(timeMs = 1000L, key = 5),
            NoteEvent(timeMs = 1000L, key = 12)
        )
        val frame = scheduler.scheduleFrame(events, currentTimeMs = 800L, approachCircleLeadTimeMs = 500L)

        // 異なる key の和音はすべてサークルが生成されること
        assertEquals(3, frame.approachCircles.size)
        val keys = frame.approachCircles.map { it.key }.toSet()
        assertEquals(setOf(1, 5, 12), keys)
    }

    @Test
    fun scheduleFrame_twentyOneKeys_handledSafelyWithoutOutOfBounds() {
        // Sky 15 keys 以外のキーインデックス (key 0 .. 20)
        val events = listOf(
            NoteEvent(timeMs = 1000L, key = 0),
            NoteEvent(timeMs = 1000L, key = 14),
            NoteEvent(timeMs = 1000L, key = 20)
        )
        // 21 keys の場合も例外が発生せず安全に ApproachCircle が返ること
        val frame = scheduler.scheduleFrame(events, currentTimeMs = 800L, approachCircleLeadTimeMs = 500L)

        assertEquals(3, frame.approachCircles.size)
        val keys = frame.approachCircles.map { it.key }.toSet()
        assertTrue(keys.contains(20))
    }

    @Test
    fun scheduleFrame_singleNote_remainingCountIsOne() {
        val events = listOf(NoteEvent(timeMs = 1000L, key = 5))
        val frame = scheduler.scheduleFrame(events, currentTimeMs = 800L, approachCircleLeadTimeMs = 500L)

        assertEquals(1, frame.approachCircles.size)
        assertEquals(5, frame.approachCircles[0].key)
        assertEquals(1, frame.approachCircles[0].remainingCount)
        assertEquals(false, frame.approachCircles[0].showRepeatBadge)
    }

    // --- 和音 (ChordGroup) テスト ---

    @Test
    fun scheduleFrame_singleNote_noChordGroups() {
        val events = listOf(NoteEvent(timeMs = 1000L, key = 3))
        val frame = scheduler.scheduleFrame(events, currentTimeMs = 800L, approachCircleLeadTimeMs = 500L)
        assertTrue(frame.chordGroups.isEmpty())
    }

    @Test
    fun scheduleFrame_twoNotesAtSameTime_oneChordGroup() {
        val events = listOf(
            NoteEvent(timeMs = 1000L, key = 2),
            NoteEvent(timeMs = 1000L, key = 7)
        )
        val frame = scheduler.scheduleFrame(events, currentTimeMs = 800L, approachCircleLeadTimeMs = 500L)
        assertEquals(1, frame.chordGroups.size)
        assertEquals(1000L, frame.chordGroups[0].timeMs)
        assertEquals(listOf(2, 7), frame.chordGroups[0].keys)
    }

    @Test
    fun scheduleFrame_threeNotesAtSameTime_oneChordGroupWithSortedKeys() {
        val events = listOf(
            NoteEvent(timeMs = 1000L, key = 7),
            NoteEvent(timeMs = 1000L, key = 0),
            NoteEvent(timeMs = 1000L, key = 4)
        )
        val frame = scheduler.scheduleFrame(events, currentTimeMs = 800L, approachCircleLeadTimeMs = 500L)
        assertEquals(1, frame.chordGroups.size)
        assertEquals(1000L, frame.chordGroups[0].timeMs)
        assertEquals(listOf(0, 4, 7), frame.chordGroups[0].keys)
    }

    @Test
    fun scheduleFrame_differentTimes_twoSeparateChordGroups() {
        val events = listOf(
            NoteEvent(timeMs = 1000L, key = 0),
            NoteEvent(timeMs = 1000L, key = 4),
            NoteEvent(timeMs = 1050L, key = 1),
            NoteEvent(timeMs = 1050L, key = 5)
        )
        val frame = scheduler.scheduleFrame(events, currentTimeMs = 800L, approachCircleLeadTimeMs = 500L)
        assertEquals(2, frame.chordGroups.size)
        // timeMs 降順（遠い未来が先）
        assertEquals(1050L, frame.chordGroups[0].timeMs)
        assertEquals(listOf(1, 5), frame.chordGroups[0].keys)

        assertEquals(1000L, frame.chordGroups[1].timeMs)
        assertEquals(listOf(0, 4), frame.chordGroups[1].keys)
    }

    @Test
    fun scheduleFrame_duplicateNoteAtSameTime_treatedAsSingleKeyInChord() {
        val events = listOf(
            NoteEvent(timeMs = 1000L, key = 2),
            NoteEvent(timeMs = 1000L, key = 2),
            NoteEvent(timeMs = 1000L, key = 5)
        )
        val frame = scheduler.scheduleFrame(events, currentTimeMs = 800L, approachCircleLeadTimeMs = 500L)
        assertEquals(1, frame.chordGroups.size)
        assertEquals(listOf(2, 5), frame.chordGroups[0].keys)
    }

    @Test
    fun scheduleFrame_noteLeadTimeGreaterThanApproachCircleLeadTime_chordGroupsIncludeFarFuture() {
        val events = listOf(
            NoteEvent(timeMs = 1000L, key = 0),
            NoteEvent(timeMs = 1000L, key = 4),
            NoteEvent(timeMs = 1400L, key = 2),
            NoteEvent(timeMs = 1400L, key = 6)
        )
        // currentTime = 800, approachCircleLeadTime = 500 (800..1300), noteLeadTime = 1000 (800..1800)
        // 1400ms は approachCircle 範囲外だが noteLeadTime 範囲内
        val frame = scheduler.scheduleFrame(
            events,
            currentTimeMs = 800L,
            noteLeadTimeMs = 1000L,
            approachCircleLeadTimeMs = 500L
        )
        // 1400ms の和音も chordGroups に含まれること
        assertEquals(2, frame.chordGroups.size)
        assertEquals(1400L, frame.chordGroups[0].timeMs)
        assertEquals(listOf(2, 6), frame.chordGroups[0].keys)
        assertEquals(1000L, frame.chordGroups[1].timeMs)
        assertEquals(listOf(0, 4), frame.chordGroups[1].keys)

        // 一方で ApproachCircle は approachCircle 範囲内の 1000ms のみ（key 0, 4）
        assertEquals(2, frame.approachCircles.size)
        val circleKeys = frame.approachCircles.map { it.key }.toSet()
        assertEquals(setOf(0, 4), circleKeys)
    }

    // --- 連打バッジ (showRepeatBadge / ×1) 判定テスト ---

    @Test
    fun scheduleFrame_threeRepeatSequence_fullTransitionAndFutureCircleFalse() {
        val events = listOf(
            NoteEvent(timeMs = 1000L, key = 3),
            NoteEvent(timeMs = 1100L, key = 3),
            NoteEvent(timeMs = 1200L, key = 3)
        )
        val hlTime = 500L

        // 1. 開始時 (800ms): 3個すべて表示
        val frame1 = scheduler.scheduleFrame(events, currentTimeMs = 800L, approachCircleLeadTimeMs = hlTime)
        assertEquals(3, frame1.approachCircles.size)
        // 未来側Circleは showRepeatBadge = false
        assertEquals(false, frame1.approachCircles[0].showRepeatBadge)
        assertEquals(1, frame1.approachCircles[0].remainingCount)
        assertEquals(false, frame1.approachCircles[1].showRepeatBadge)
        assertEquals(1, frame1.approachCircles[1].remainingCount)
        // 直近Circleは残り3回かつ showRepeatBadge = true (×3)
        assertEquals(true, frame1.approachCircles[2].showRepeatBadge)
        assertEquals(3, frame1.approachCircles[2].remainingCount)

        // 2. 1回目終了後 (1050ms): 1100ms と 1200ms が残る
        val frame2 = scheduler.scheduleFrame(events, currentTimeMs = 1050L, approachCircleLeadTimeMs = hlTime)
        assertEquals(2, frame2.approachCircles.size)
        assertEquals(false, frame2.approachCircles[0].showRepeatBadge) // 未来側 (1200ms)
        assertEquals(1, frame2.approachCircles[0].remainingCount)
        assertEquals(true, frame2.approachCircles[1].showRepeatBadge) // 直近 (1100ms)
        assertEquals(2, frame2.approachCircles[1].remainingCount) // ×2

        // 3. 2回目終了後 (1150ms): 1200ms のみ残る（連打系列の最後の1回）
        val frame3 = scheduler.scheduleFrame(events, currentTimeMs = 1150L, approachCircleLeadTimeMs = hlTime)
        assertEquals(1, frame3.approachCircles.size)
        // 直前ノート 1100ms との間隔は 1200 - 1100 = 100ms <= 500ms なので showRepeatBadge = true (×1)
        assertEquals(1, frame3.approachCircles[0].remainingCount)
        assertEquals(true, frame3.approachCircles[0].showRepeatBadge)

        // 4. 3回目終了後 (1250ms): すべて終了し非表示
        val frame4 = scheduler.scheduleFrame(events, currentTimeMs = 1250L, approachCircleLeadTimeMs = hlTime)
        assertTrue(frame4.approachCircles.isEmpty())
    }

    @Test
    fun scheduleFrame_repeatBadge_falsePositivePrevention_differentInterNoteInterval() {
        // 前回ノート 0ms, 現在時刻 400ms, 次のノート 900ms, repeatSequenceMaxIntervalMs = 500ms
        // 現在時刻400msから見ると 0ms は過去500ms以内にあるが、次ノート(900ms)と前ノート(0ms)の間隔は 900ms > 500ms
        val events = listOf(
            NoteEvent(timeMs = 0L, key = 5),
            NoteEvent(timeMs = 900L, key = 5)
        )
        val frame = scheduler.scheduleFrame(events, currentTimeMs = 400L, approachCircleLeadTimeMs = 500L)
        assertEquals(1, frame.approachCircles.size)
        assertEquals(5, frame.approachCircles[0].key)
        assertEquals(1, frame.approachCircles[0].remainingCount)
        // 元々同じウィンドウに入り得ない別個のノートなので showRepeatBadge は false (×1 は表示しない)
        assertEquals(false, frame.approachCircles[0].showRepeatBadge)
    }

    @Test
    fun scheduleFrame_repeatBadge_boundaryConditions() {
        val hlTime = 500L

        // 境界値1: 間隔がちょうど 500ms (prev = 500ms, next = 1000ms, current = 600ms)
        val eventsExact = listOf(
            NoteEvent(timeMs = 500L, key = 1),
            NoteEvent(timeMs = 1000L, key = 1)
        )
        val frameExact = scheduler.scheduleFrame(eventsExact, currentTimeMs = 600L, approachCircleLeadTimeMs = hlTime)
        assertEquals(1, frameExact.approachCircles.size)
        assertEquals(1, frameExact.approachCircles[0].remainingCount)
        assertEquals(true, frameExact.approachCircles[0].showRepeatBadge) // ちょうど500ms差は連打として扱う (×1)

        // 境界値2: 間隔が 501ms (prev = 500ms, next = 1001ms, current = 600ms)
        val eventsExceeded = listOf(
            NoteEvent(timeMs = 500L, key = 1),
            NoteEvent(timeMs = 1001L, key = 1)
        )
        val frameExceeded = scheduler.scheduleFrame(eventsExceeded, currentTimeMs = 600L, approachCircleLeadTimeMs = hlTime)
        assertEquals(1, frameExceeded.approachCircles.size)
        assertEquals(1, frameExceeded.approachCircles[0].remainingCount)
        assertEquals(false, frameExceeded.approachCircles[0].showRepeatBadge) // 501ms差は通常単音 (×1 なし)
    }

    @Test
    fun scheduleFrame_repeatSequence_followsApproachCircleLeadTime() {
        // 前ノート 500ms, 次ノート 750ms (間隔 250ms)
        val events = listOf(
            NoteEvent(timeMs = 500L, key = 1),
            NoteEvent(timeMs = 750L, key = 1)
        )

        // 1. サークル先読み時間が 200ms の場合:
        // 間隔 250ms > 200ms なのでサークルは画面上に重ならず、単発サークルとして扱われ showRepeatBadge は false (×1 は出ない)
        val frame200 = scheduler.scheduleFrame(
            events = events,
            currentTimeMs = 600L,
            approachCircleLeadTimeMs = 200L
        )
        assertEquals(1, frame200.approachCircles.size)
        assertEquals(false, frame200.approachCircles[0].showRepeatBadge)

        // 2. サークル先読み時間を 300ms に変更した場合:
        // 設定変更に追従し、間隔 250ms <= 300ms となるため画面上で重なり系列と判定され showRepeatBadge は true (×1)
        val frame300 = scheduler.scheduleFrame(
            events = events,
            currentTimeMs = 600L,
            approachCircleLeadTimeMs = 300L
        )
        assertEquals(1, frame300.approachCircles.size)
        assertEquals(true, frame300.approachCircles[0].showRepeatBadge)
    }

    @Test
    fun scheduleFrame_chordGroups_independentFromApproachCircleLeadTime() {
        val events = listOf(
            NoteEvent(timeMs = 1000L, key = 2),
            NoteEvent(timeMs = 1000L, key = 7),
            NoteEvent(timeMs = 1000L, key = 12)
        )

        val frameSmallCircle = scheduler.scheduleFrame(
            events = events,
            currentTimeMs = 800L,
            noteLeadTimeMs = 300L,
            approachCircleLeadTimeMs = 100L
        )

        val frameLargeCircle = scheduler.scheduleFrame(
            events = events,
            currentTimeMs = 800L,
            noteLeadTimeMs = 300L,
            approachCircleLeadTimeMs = 300L
        )

        // ChordGroupの集約はnoteLeadTimeMsを基準としており、approachCircleLeadTimeMsの変更に影響されない
        assertEquals(1, frameSmallCircle.chordGroups.size)
        assertEquals(1, frameLargeCircle.chordGroups.size)
        assertEquals(listOf(2, 7, 12), frameSmallCircle.chordGroups[0].keys)
        assertEquals(listOf(2, 7, 12), frameLargeCircle.chordGroups[0].keys)
    }

    @Test
    fun guideFrame_equalsAndHashCode_includesChordGroupsAndApproachCircleLeadTime() {
        val base = GuideFrame(
            currentTimeMs = 1000L,
            approachCircleLeadTimeMs = 500L,
            chordGroups = listOf(ChordGroup(timeMs = 1000L, keys = listOf(0, 4)))
        )
        val identical = GuideFrame(
            currentTimeMs = 1000L,
            approachCircleLeadTimeMs = 500L,
            chordGroups = listOf(ChordGroup(timeMs = 1000L, keys = listOf(0, 4)))
        )
        val differentChords = GuideFrame(
            currentTimeMs = 1000L,
            approachCircleLeadTimeMs = 500L,
            chordGroups = listOf(ChordGroup(timeMs = 1000L, keys = listOf(1, 5)))
        )
        val differentApproachCircle = GuideFrame(
            currentTimeMs = 1000L,
            approachCircleLeadTimeMs = 600L,
            chordGroups = listOf(ChordGroup(timeMs = 1000L, keys = listOf(0, 4)))
        )

        assertEquals(base, identical)
        assertEquals(base.hashCode(), identical.hashCode())

        assertTrue(base != differentChords)
        assertTrue(base.hashCode() != differentChords.hashCode())

        assertTrue(base != differentApproachCircle)
        assertTrue(base.hashCode() != differentApproachCircle.hashCode())
    }
}
