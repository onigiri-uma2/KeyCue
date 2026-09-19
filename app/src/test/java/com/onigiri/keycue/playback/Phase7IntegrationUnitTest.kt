package com.onigiri.keycue.playback

import com.onigiri.keycue.model.NoteEvent
import com.onigiri.keycue.model.SongData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [NoteScheduler] を中心とする再生スケジューリングの統合単体テスト。
 *
 * 以下の振る舞いを検証します:
 * - 和音（同一時刻・複数キーの同時打鍵イベント）の同時抽出
 * - シーク操作直後のスケジューラ更新と先読みノートの整合性
 * - 一時停止（pause）中に再生位置が進まないことの確認
 */
class Phase7IntegrationUnitTest {

    private lateinit var scheduler: NoteScheduler

    @Before
    fun setUp() {
        scheduler = NoteScheduler()
    }

    /**
     * 和音（同一時刻に打鍵される複数ノート）
     * 同じ timeMs を持つ NoteEvent が全て欠落なく upcomingNotes および justKeys に含まれることを検証。
     */
    @Test
    fun test8_chords_simultaneousNotesIncluded() {
        val chordTime = 2000L
        val events = listOf(
            NoteEvent(timeMs = 1000L, key = 1),
            // 和音: key 2, key 7, key 12 (それぞれ上段・中段・下段)
            NoteEvent(timeMs = chordTime, key = 2),
            NoteEvent(timeMs = chordTime, key = 7),
            NoteEvent(timeMs = chordTime, key = 12),
            NoteEvent(timeMs = 3000L, key = 4)
        )

        // currentTime = 1500L, noteLeadTime = 700L -> 和音(2000L)は先読み範囲内
        val frameUpcoming = scheduler.scheduleFrame(
            events = events,
            currentTimeMs = 1500L,
            noteLeadTimeMs = 700L
        )
        val upcomingKeys = frameUpcoming.upcomingNotes.map { it.key }.toSet()
        assertTrue(upcomingKeys.contains(2))
        assertTrue(upcomingKeys.contains(7))
        assertTrue(upcomingKeys.contains(12))

        // currentTime = 2000L -> 和音(2000L)はジャストタイミング
        val frameJust = scheduler.scheduleFrame(
            events = events,
            currentTimeMs = 2000L,
            noteLeadTimeMs = 700L
        )
        assertTrue(frameJust.justKeys.contains(2))
        assertTrue(frameJust.justKeys.contains(7))
        assertTrue(frameJust.justKeys.contains(12))
        assertEquals(3, frameJust.justKeys.size)
    }

    /**
     * シーク操作後のスケジューリング更新
     * 巻き戻しやスキップ操作直後に、新しい再生時刻に応じたノートが即座に抽出されることを検証。
     */
    @Test
    fun test9_seek_updatesScheduledNotesImmediately() {
        val events = listOf(
            NoteEvent(timeMs = 1000L, key = 0),
            NoteEvent(timeMs = 5000L, key = 5),
            NoteEvent(timeMs = 10000L, key = 10),
            NoteEvent(timeMs = 15000L, key = 14)
        )

        // 1. 最初は 5000ms 付近にいたとする (currentTime = 4800ms, leadTime = 700ms -> key 5 が先読み)
        val frameInitial = scheduler.scheduleFrame(events, currentTimeMs = 4800L, noteLeadTimeMs = 700L)
        assertEquals(1, frameInitial.upcomingNotes.size)
        assertEquals(5, frameInitial.upcomingNotes.first().key)

        // 2. 10000ms 付近へシーク (currentTime = 9800ms)
        val frameSeekForward = scheduler.scheduleFrame(events, currentTimeMs = 9800L, noteLeadTimeMs = 700L)
        assertEquals(1, frameSeekForward.upcomingNotes.size)
        assertEquals(10, frameSeekForward.upcomingNotes.first().key)

        // 3. 10秒巻き戻し (currentTime = 0ms)
        val frameSeekBack = scheduler.scheduleFrame(events, currentTimeMs = 500L, noteLeadTimeMs = 700L)
        assertEquals(1, frameSeekBack.upcomingNotes.size)
        assertEquals(0, frameSeekBack.upcomingNotes.first().key)
    }

    /**
     * 一時停止中の再生位置フリーズ
     * [PlaybackClock.pause] 呼び出し後は時間が停止し、経過実時間が進んでも positionMs が増加しないことを検証。
     */
    @Test
    fun test10_pause_freezesPosition() {
        var simulatedRealtime = 10000L
        val clock = PlaybackClock(timeProvider = { simulatedRealtime })

        // 再生開始 (0ms から)
        clock.play(startPositionMs = 0L)
        assertEquals(0L, clock.getCurrentPositionMs())

        // 1000ms 経過
        simulatedRealtime += 1000L
        assertEquals(1000L, clock.getCurrentPositionMs())

        // 一時停止
        clock.pause()
        assertEquals(1000L, clock.getCurrentPositionMs())

        // 一時停止中に実時間が 5000ms 経過
        simulatedRealtime += 5000L
        // 再生位置は 1000L のまま停止していること
        assertEquals(1000L, clock.getCurrentPositionMs())

        // 再開 (Resume)
        clock.resume()
        assertEquals(1000L, clock.getCurrentPositionMs())

        // 再開後に 200ms 経過
        simulatedRealtime += 200L
        assertEquals(1200L, clock.getCurrentPositionMs())
    }
}
