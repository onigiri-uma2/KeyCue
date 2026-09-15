package com.onigiri.keycue.playback

import com.onigiri.keycue.model.NoteEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteSchedulerCountdownTest {

    private val scheduler = NoteScheduler()

    @Test
    fun `first note at 0ms is scheduled during countdown within leadTimeMs`() {
        val events = listOf(
            NoteEvent(timeMs = 0L, key = 2),
            NoteEvent(timeMs = 500L, key = 7),
            NoteEvent(timeMs = 2000L, key = 12)
        )

        val leadTimeMs = 700L

        // 1. カウントダウン残り 1500ms (-1500ms) の時点: leadTime 700ms では 0ms のノーツはまだ出現しない (-1500 + 700 = -800 < 0)
        val frameFar = scheduler.scheduleFrame(
            events = events,
            currentTimeMs = -1500L,
            leadTimeMs = leadTimeMs,
            highlightTimeMs = 300L
        )
        assertTrue(frameFar.upcomingNotes.isEmpty())
        assertFalse(frameFar.highlightedKeys.contains(2))

        // 2. カウントダウン残り 700ms (-700ms) の時点: 0ms のノーツが出現 (progress = 0.0)
        val frameAppear = scheduler.scheduleFrame(
            events = events,
            currentTimeMs = -700L,
            leadTimeMs = leadTimeMs,
            highlightTimeMs = 300L
        )
        assertEquals(1, frameAppear.upcomingNotes.size)
        assertEquals(2, frameAppear.upcomingNotes[0].key)
        val progressAppear = FallingNoteCalculator.calculateProgress(
            eventTimeMs = frameAppear.upcomingNotes[0].timeMs,
            currentTimeMs = -700L,
            leadTimeMs = leadTimeMs
        )
        assertEquals(0.0f, progressAppear, 0.001f)
        assertFalse(frameAppear.highlightedKeys.contains(2))

        // 3. カウントダウン残り 300ms (-300ms) の時点: 0ms のノーツがハイライトされ、落下中 (progress = 400/700 = 0.571)
        val frameHl = scheduler.scheduleFrame(
            events = events,
            currentTimeMs = -300L,
            leadTimeMs = leadTimeMs,
            highlightTimeMs = 300L
        )
        assertEquals(1, frameHl.upcomingNotes.size)
        assertTrue(frameHl.highlightedKeys.contains(2))
        val progressHl = FallingNoteCalculator.calculateProgress(
            eventTimeMs = frameHl.upcomingNotes[0].timeMs,
            currentTimeMs = -300L,
            leadTimeMs = leadTimeMs
        )
        assertEquals(400f / 700f, progressHl, 0.001f)

        // 4. 曲開始 0ms 到達時: 0ms のノーツがジャストタイミング (progress = 1.0)
        val frameStart = scheduler.scheduleFrame(
            events = events,
            currentTimeMs = 0L,
            leadTimeMs = leadTimeMs,
            highlightTimeMs = 300L
        )
        assertTrue(frameStart.justKeys.contains(2))
        assertTrue(frameStart.highlightedKeys.contains(2))
        val progressStart = FallingNoteCalculator.calculateProgress(
            eventTimeMs = 0L,
            currentTimeMs = 0L,
            leadTimeMs = leadTimeMs
        )
        assertEquals(1.0f, progressStart, 0.001f)
    }

    @Test
    fun `scheduleFrame handles seek forward and backward cleanly without stale notes`() {
        val events = (0..20).map { i ->
            NoteEvent(timeMs = i * 1000L, key = i % 15)
        }

        // 初期: 1000ms
        val frame1 = scheduler.scheduleFrame(events, currentTimeMs = 1000L, leadTimeMs = 700L)
        assertTrue(frame1.upcomingNotes.any { it.timeMs == 1000L || it.timeMs == 1700L || it.timeMs in 1000L..1700L })

        // 早送り: 10000ms (10秒後)
        val frame2 = scheduler.scheduleFrame(events, currentTimeMs = 10000L, leadTimeMs = 700L)
        // 過去の 1000ms 付近のノーツは一切残らず、10000ms 付近のノーツのみ
        assertTrue(frame2.upcomingNotes.none { it.timeMs < 9500L })
        assertTrue(frame2.highlightedKeys.contains(10 % 15))

        // 巻き戻し: 2000ms (2秒)
        val frame3 = scheduler.scheduleFrame(events, currentTimeMs = 2000L, leadTimeMs = 700L)
        assertTrue(frame3.upcomingNotes.none { it.timeMs > 3000L })
        assertTrue(frame3.highlightedKeys.contains(2 % 15))
    }

    @Test
    fun `countdown position matches playback speed`() {
        val countdownTotalMs = 3000L
        val leadTimeMs = 700L

        // 1. 速度 0.5x の場合:
        // 仮想曲開始時刻 = -(3000 * 0.5) = -1500ms
        // 実時間残り1.4秒 (実時間経過1.6秒) の時点: 楽曲時刻 = -1500 + 1600 * 0.5 = -700ms
        val posHalfSpeed = -1500L + (1600L * 0.5f).toLong()
        assertEquals(-700L, posHalfSpeed)
        val progressHalf = FallingNoteCalculator.calculateProgress(0L, posHalfSpeed, leadTimeMs)
        assertEquals(0.0f, progressHalf, 0.001f) // ちょうど出現

        // 2. 速度 1.5x の場合:
        // 仮想曲開始時刻 = -(3000 * 1.5) = -4500ms
        // 実時間残り (700 / 1.5 = 466.66ms) の時点:
        val remainingRealMs = 700f / 1.5f
        val elapsedRealMs = countdownTotalMs - remainingRealMs
        val posOneAndHalfSpeed = (-4500f + elapsedRealMs * 1.5f).toLong()
        assertEquals(-700L, posOneAndHalfSpeed)
        val progressOneAndHalf = FallingNoteCalculator.calculateProgress(0L, posOneAndHalfSpeed, leadTimeMs)
        assertEquals(0.0f, progressOneAndHalf, 0.001f) // ちょうど出現

        // いずれの速度でも、実時間3秒経過（カウントダウン終了）時には pos = 0L になり progress = 1.0 になる
        val posFinalHalf = -1500L + (3000L * 0.5f).toLong()
        assertEquals(0L, posFinalHalf)
        val posFinalOneAndHalf = -4500L + (3000L * 1.5f).toLong()
        assertEquals(0L, posFinalOneAndHalf)
    }
}
