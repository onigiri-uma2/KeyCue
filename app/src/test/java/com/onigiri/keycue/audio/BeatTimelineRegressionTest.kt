package com.onigiri.keycue.audio

import com.onigiri.keycue.model.BeatSubdivision
import com.onigiri.keycue.model.MetronomeConfig
import com.onigiri.keycue.model.timing.TimeSignature
import com.onigiri.keycue.song.midi.RawTempoEvent
import com.onigiri.keycue.song.midi.RawTimeSignatureEvent
import com.onigiri.keycue.song.midi.TempoMap
import com.onigiri.keycue.song.midi.TimeSignatureMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [BeatTimeline]（特に [MidiBeatTimeline]）のグリッド計算・探索回帰テスト。
 *
 * 【検証要件】
 * 1. 基本拍位置: PPQN 480, 120 BPM:
 *    - tick 0 -> 0ms, tick 480 -> 500ms, tick 960 -> 1000ms
 *    tick 960 で 240 BPM へ変更:
 *    - tick 1440 -> 1250ms, tick 1920 -> 1500ms
 * 2. テンポ変更が拍の途中にある場合でも拍グリッドが崩れない
 * 3. 拍子変更が小節途中にある場合、新拍子イベントが新小節の第1拍（アクセント）となる
 * 4. 同一 tick でテンポと拍子が同時に変更されるケース
 * 5. 4/4、3/4、6/8 の小節頭アクセント周期
 * 6. 4分音符クリックと8分音符クリックの正しい分割
 * 7. 正負の拍位置オフセット (beatOffsetMs) の反映
 * 8. 端数msの拍が飛ばされないこと
 * 9. Seek 先が拍境界と完全一致する場合の直接検出
 * 10. テンポ変更地点の直前・直後でクリックの二重発音や抜けがないこと
 */
class BeatTimelineRegressionTest {

    // 1. 基本拍位置とテンポ変更: 120 BPM -> 240 BPM
    @Test
    fun testTempoChange_120_to_240_at_tick960() {
        val ppqn = 480
        val tempoEvents = listOf(
            RawTempoEvent(tick = 0L, usPerQuarter = 500_000L),  // 120 BPM
            RawTempoEvent(tick = 960L, usPerQuarter = 250_000L) // 240 BPM (250ms per 480 ticks)
        )
        val tempoMap = TempoMap(ppqn, tempoEvents)
        val tsMap = TimeSignatureMap(emptyList()) // 4/4

        val timeline = MidiBeatTimeline(
            ppqn = ppqn,
            tempoMap = tempoMap,
            timeSignatureMap = tsMap,
            subdivision = BeatSubdivision.QUARTER,
            accentEnabled = true,
            beatOffsetMs = 0L,
            songDurationMs = 3000L
        )

        // tick 0 -> 0ms
        val b0 = timeline.beatForIndex(0L)
        assertNotNull(b0)
        assertEquals(0L, b0!!.timeMs)
        assertTrue(b0.isAccent) // 第1拍アクセント

        // tick 480 -> 500ms
        val b1 = timeline.beatForIndex(1L)
        assertNotNull(b1)
        assertEquals(500L, b1!!.timeMs)
        assertFalse(b1.isAccent)

        // tick 960 -> 1000ms
        val b2 = timeline.beatForIndex(2L)
        assertNotNull(b2)
        assertEquals(1000L, b2!!.timeMs)
        assertFalse(b2.isAccent)

        // tick 1440 -> 1250ms (1000ms + 250ms)
        val b3 = timeline.beatForIndex(3L)
        assertNotNull(b3)
        assertEquals(1250L, b3!!.timeMs)
        assertFalse(b3.isAccent)

        // tick 1920 -> 1500ms (1000ms + 500ms)
        val b4 = timeline.beatForIndex(4L)
        assertNotNull(b4)
        assertEquals(1500L, b4!!.timeMs)
        assertTrue(b4.isAccent) // 4拍ごとにアクセント（0, 4, 8...）
    }

    // 2. テンポ変更が拍の途中にあるケース
    @Test
    fun testTempoChange_inMiddleOfBeat() {
        val ppqn = 480
        // tick 240 (拍の中間) で 120 BPM -> 240 BPM
        val tempoEvents = listOf(
            RawTempoEvent(tick = 0L, usPerQuarter = 500_000L),  // 120 BPM: 240 tick = 250ms
            RawTempoEvent(tick = 240L, usPerQuarter = 250_000L) // 240 BPM: 240 tick = 125ms
        )
        val tempoMap = TempoMap(ppqn, tempoEvents)
        val tsMap = TimeSignatureMap(emptyList())

        val timeline = MidiBeatTimeline(
            ppqn = ppqn,
            tempoMap = tempoMap,
            timeSignatureMap = tsMap,
            subdivision = BeatSubdivision.QUARTER,
            accentEnabled = true,
            beatOffsetMs = 0L,
            songDurationMs = 2000L
        )

        // 4分音符クリックは tick 0, 480, 960... で不変
        // tick 0 -> 0ms
        assertEquals(0L, timeline.beatForIndex(0L)!!.timeMs)
        // tick 480 -> 250ms (0..240) + 125ms (240..480) = 375ms
        assertEquals(375L, timeline.beatForIndex(1L)!!.timeMs)
        // tick 960 -> 375ms + 250ms = 625ms
        assertEquals(625L, timeline.beatForIndex(2L)!!.timeMs)
    }

    // 3. 拍子変更が小節途中にある場合、新イベントが新小節の第1拍（アクセント）となる
    @Test
    fun testTimeSignatureChange_inMiddleOfMeasure() {
        val ppqn = 480
        // tick 0: 4/4 (小節長 1920 tick)
        // tick 960 (小節の途中・第3拍の開始位置) で 3/4 に変更
        val tsEvents = listOf(
            RawTimeSignatureEvent(tick = 0L, numerator = 4, denominator = 4, clocksPerClick = 24, notated32ndNotesPerQuarter = 8),
            RawTimeSignatureEvent(tick = 960L, numerator = 3, denominator = 4, clocksPerClick = 24, notated32ndNotesPerQuarter = 8)
        )
        val tempoMap = TempoMap(ppqn, listOf(RawTempoEvent(0L, 500_000L))) // 120 BPM
        val tsMap = TimeSignatureMap(tsEvents)

        val timeline = MidiBeatTimeline(
            ppqn = ppqn,
            tempoMap = tempoMap,
            timeSignatureMap = tsMap,
            subdivision = BeatSubdivision.QUARTER,
            accentEnabled = true,
            beatOffsetMs = 0L,
            songDurationMs = 3000L
        )

        // 区間1: 4/4 (tick 0, 480)
        val b0 = timeline.beatForIndex(0L)!! // tick 0
        assertEquals(0L, b0.timeMs)
        assertTrue(b0.isAccent)

        val b1 = timeline.beatForIndex(1L)!! // tick 480
        assertEquals(500L, b1.timeMs)
        assertFalse(b1.isAccent)

        // 区間2: 3/4 (tick 960 から開始)
        // tick 960 は新しい拍子の基準（第1拍・小節頭アクセント）となる
        val b2 = timeline.beatForIndex(2L)!! // tick 960
        assertEquals(1000L, b2.timeMs)
        assertTrue(b2.isAccent) // 新小節頭アクセント！

        val b3 = timeline.beatForIndex(3L)!! // tick 1440
        assertEquals(1500L, b3.timeMs)
        assertFalse(b3.isAccent)

        val b4 = timeline.beatForIndex(4L)!! // tick 1920
        assertEquals(2000L, b4.timeMs)
        assertFalse(b4.isAccent)

        val b5 = timeline.beatForIndex(5L)!! // tick 2400 (3拍サイクルの次小節頭)
        assertEquals(2500L, b5.timeMs)
        assertTrue(b5.isAccent) // 3拍小節の小節頭！
    }

    // 4. 同一 tick でテンポと拍子が同時に変更されるケース
    @Test
    fun testSimultaneousTempoAndTimeSignatureChange() {
        val ppqn = 480
        val tempoEvents = listOf(
            RawTempoEvent(tick = 0L, usPerQuarter = 500_000L),  // 120 BPM
            RawTempoEvent(tick = 960L, usPerQuarter = 250_000L) // 240 BPM at tick 960
        )
        val tsEvents = listOf(
            RawTimeSignatureEvent(tick = 0L, numerator = 4, denominator = 4, clocksPerClick = 24, notated32ndNotesPerQuarter = 8),
            RawTimeSignatureEvent(tick = 960L, numerator = 3, denominator = 4, clocksPerClick = 24, notated32ndNotesPerQuarter = 8)
        )
        val tempoMap = TempoMap(ppqn, tempoEvents)
        val tsMap = TimeSignatureMap(tsEvents)

        val timeline = MidiBeatTimeline(
            ppqn = ppqn,
            tempoMap = tempoMap,
            timeSignatureMap = tsMap,
            subdivision = BeatSubdivision.QUARTER,
            accentEnabled = true,
            beatOffsetMs = 0L,
            songDurationMs = 2500L
        )

        val b2 = timeline.beatForIndex(2L)!! // tick 960
        assertEquals(1000L, b2.timeMs)
        assertTrue(b2.isAccent) // 新拍子の小節頭

        // tick 1440 (240 BPM: +250ms)
        val b3 = timeline.beatForIndex(3L)!!
        assertEquals(1250L, b3.timeMs)
        assertFalse(b3.isAccent)
    }

    // 5. 6/8 拍子の小節頭アクセントとクリック間隔
    @Test
    fun testSixEightTimeSignature() {
        val ppqn = 480
        val tsEvents = listOf(
            RawTimeSignatureEvent(tick = 0L, numerator = 6, denominator = 8, clocksPerClick = 24, notated32ndNotesPerQuarter = 8)
        )
        val tempoMap = TempoMap(ppqn, listOf(RawTempoEvent(0L, 500_000L))) // 120 BPM (quarter = 500ms)
        val tsMap = TimeSignatureMap(tsEvents)

        // 8分音符クリック (step = ppqn / 2 = 240 tick = 250ms)
        // 6/8 小節長 = ppqn * 4 * 6 / 8 = 3 * ppqn = 1440 tick = 6クリック
        val timeline = MidiBeatTimeline(
            ppqn = ppqn,
            tempoMap = tempoMap,
            timeSignatureMap = tsMap,
            subdivision = BeatSubdivision.EIGHTH,
            accentEnabled = true,
            beatOffsetMs = 0L,
            songDurationMs = 3000L
        )

        assertEquals(0L, timeline.beatForIndex(0L)!!.timeMs)
        assertTrue(timeline.beatForIndex(0L)!!.isAccent)

        for (i in 1..5) {
            assertEquals(i * 250L, timeline.beatForIndex(i.toLong())!!.timeMs)
            assertFalse(timeline.beatForIndex(i.toLong())!!.isAccent)
        }

        // 第6クリック (第2小節頭) -> アクセント
        val b6 = timeline.beatForIndex(6L)!!
        assertEquals(1500L, b6.timeMs)
        assertTrue(b6.isAccent)
    }

    // 6. 4分音符クリック vs 8分音符クリック
    @Test
    fun testQuarterVsEighthSubdivision() {
        val ppqn = 480
        val tempoMap = TempoMap(ppqn, listOf(RawTempoEvent(0L, 500_000L))) // 120 BPM
        val tsMap = TimeSignatureMap(emptyList()) // 4/4

        val quarterTl = MidiBeatTimeline(
            ppqn = ppqn,
            tempoMap = tempoMap,
            timeSignatureMap = tsMap,
            subdivision = BeatSubdivision.QUARTER,
            accentEnabled = true,
            beatOffsetMs = 0L,
            songDurationMs = 2000L
        )

        val eighthTl = MidiBeatTimeline(
            ppqn = ppqn,
            tempoMap = tempoMap,
            timeSignatureMap = tsMap,
            subdivision = BeatSubdivision.EIGHTH,
            accentEnabled = true,
            beatOffsetMs = 0L,
            songDurationMs = 2000L
        )

        assertEquals(0L, quarterTl.beatForIndex(0L)!!.timeMs)
        assertEquals(500L, quarterTl.beatForIndex(1L)!!.timeMs)

        assertEquals(0L, eighthTl.beatForIndex(0L)!!.timeMs)
        assertEquals(250L, eighthTl.beatForIndex(1L)!!.timeMs)
        assertEquals(500L, eighthTl.beatForIndex(2L)!!.timeMs)
    }

    // 7. 正負の拍位置オフセット
    @Test
    fun testPositiveAndNegativeBeatOffset() {
        val ppqn = 480
        val tempoMap = TempoMap(ppqn, listOf(RawTempoEvent(0L, 500_000L)))
        val tsMap = TimeSignatureMap(emptyList())

        val positiveOffsetTl = MidiBeatTimeline(
            ppqn = ppqn,
            tempoMap = tempoMap,
            timeSignatureMap = tsMap,
            subdivision = BeatSubdivision.QUARTER,
            accentEnabled = true,
            beatOffsetMs = 50L,
            songDurationMs = 2000L
        )

        val negativeOffsetTl = MidiBeatTimeline(
            ppqn = ppqn,
            tempoMap = tempoMap,
            timeSignatureMap = tsMap,
            subdivision = BeatSubdivision.QUARTER,
            accentEnabled = true,
            beatOffsetMs = -50L,
            songDurationMs = 2000L
        )

        assertEquals(50L, positiveOffsetTl.beatForIndex(0L)!!.timeMs)
        assertEquals(550L, positiveOffsetTl.beatForIndex(1L)!!.timeMs)

        assertEquals(-50L, negativeOffsetTl.beatForIndex(0L)!!.timeMs)
        assertEquals(450L, negativeOffsetTl.beatForIndex(1L)!!.timeMs)
    }

    // 8. 端数msの拍が飛ばされないこと (例: 140 BPM -> 4分音符 = 428.57ms)
    @Test
    fun testFractionalMs_notSkipped() {
        val ppqn = 480
        // 140 BPM: 60,000,000 / 140 = 428,571 us
        val tempoMap = TempoMap(ppqn, listOf(RawTempoEvent(0L, 428_571L)))
        val tsMap = TimeSignatureMap(emptyList())

        val timeline = MidiBeatTimeline(
            ppqn = ppqn,
            tempoMap = tempoMap,
            timeSignatureMap = tsMap,
            subdivision = BeatSubdivision.QUARTER,
            accentEnabled = true,
            beatOffsetMs = 0L,
            songDurationMs = 2000L
        )

        val b0 = timeline.beatForIndex(0L)!! // 0ms
        val b1 = timeline.beatForIndex(1L)!! // round(428.57ms) = 429ms
        val b2 = timeline.beatForIndex(2L)!! // round(857.14ms) = 857ms

        assertEquals(0L, b0.timeMs)
        assertEquals(429L, b1.timeMs)
        assertEquals(857L, b2.timeMs)

        // 429ms からの nextBeatIndexAtOrAfter は 429ms の拍 (index 1) を返すこと
        assertEquals(1L, timeline.nextBeatIndexAtOrAfter(429L))
        // 430ms からは index 2 (857ms) を返すこと
        assertEquals(2L, timeline.nextBeatIndexAtOrAfter(430L))
    }

    // 9. Seek 先と拍境界の完全一致
    @Test
    fun testSeekTargetExactlyOnBeatBoundary() {
        val ppqn = 480
        val tempoMap = TempoMap(ppqn, listOf(RawTempoEvent(0L, 500_000L))) // 120 BPM (500ms intervals)
        val tsMap = TimeSignatureMap(emptyList())

        val timeline = MidiBeatTimeline(
            ppqn = ppqn,
            tempoMap = tempoMap,
            timeSignatureMap = tsMap,
            subdivision = BeatSubdivision.QUARTER,
            accentEnabled = true,
            beatOffsetMs = 0L,
            songDurationMs = 3000L
        )

        // ちょうど 1000ms にシークした場合、1000ms の拍 (index 2) が返ること
        val nextIdx = timeline.nextBeatIndexAtOrAfter(1000L)
        assertEquals(2L, nextIdx)
        assertEquals(1000L, timeline.beatForIndex(nextIdx)!!.timeMs)
    }

    // 10. テンポ変更地点での二重発音なし
    @Test
    fun testNoDoubleBeatAtTempoChangeBoundary() {
        val ppqn = 480
        // tick 960 でテンポ変更
        val tempoEvents = listOf(
            RawTempoEvent(tick = 0L, usPerQuarter = 500_000L),  // 120 BPM
            RawTempoEvent(tick = 960L, usPerQuarter = 400_000L) // 150 BPM
        )
        val tempoMap = TempoMap(ppqn, tempoEvents)
        val tsMap = TimeSignatureMap(emptyList())

        val timeline = MidiBeatTimeline(
            ppqn = ppqn,
            tempoMap = tempoMap,
            timeSignatureMap = tsMap,
            subdivision = BeatSubdivision.QUARTER,
            accentEnabled = true,
            beatOffsetMs = 0L,
            songDurationMs = 3000L
        )

        // 各拍の時刻が狭まりすぎたり（二重発音）、飛んだりしていないか検証
        var lastTime = -1L
        for (i in 0..5) {
            val beat = timeline.beatForIndex(i.toLong())!!
            assertTrue("Beat times must be strictly increasing", beat.timeMs > lastTime)
            if (lastTime >= 0L) {
                val interval = beat.timeMs - lastTime
                assertTrue("Interval should be at least 350ms, was $interval", interval >= 350L)
            }
            lastTime = beat.timeMs
        }
    }
}
