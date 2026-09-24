package com.onigiri.keycue.audio

import com.onigiri.keycue.model.BeatSubdivision
import com.onigiri.keycue.model.MetronomeConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [MetronomeBeatCalculator] の拍計算・インデックス算出に関する単体テスト。
 */
class MetronomeBeatCalculatorTest {

    @Test
    fun `BPM120 4分音符の間隔は500ms`() {
        val config = MetronomeConfig(bpm = 120, subdivision = BeatSubdivision.QUARTER)
        val interval = MetronomeBeatCalculator.calculateClickIntervalMs(config)
        assertEquals(500.0, interval, 0.001)

        val beat0 = MetronomeBeatCalculator.calculateBeat(config, 0)
        val beat1 = MetronomeBeatCalculator.calculateBeat(config, 1)
        val beat2 = MetronomeBeatCalculator.calculateBeat(config, 2)

        assertEquals(0L, beat0.timeMs)
        assertEquals(500L, beat1.timeMs)
        assertEquals(1000L, beat2.timeMs)
    }

    @Test
    fun `BPM120 8分音符の間隔は250ms`() {
        val config = MetronomeConfig(bpm = 120, subdivision = BeatSubdivision.EIGHTH)
        val interval = MetronomeBeatCalculator.calculateClickIntervalMs(config)
        assertEquals(250.0, interval, 0.001)

        val beat0 = MetronomeBeatCalculator.calculateBeat(config, 0)
        val beat1 = MetronomeBeatCalculator.calculateBeat(config, 1)
        val beat2 = MetronomeBeatCalculator.calculateBeat(config, 2)

        assertEquals(0L, beat0.timeMs)
        assertEquals(250L, beat1.timeMs)
        assertEquals(500L, beat2.timeMs)
    }

    @Test
    fun `BPM60 4分音符の間隔は1000ms`() {
        val config = MetronomeConfig(bpm = 60, subdivision = BeatSubdivision.QUARTER)
        val interval = MetronomeBeatCalculator.calculateClickIntervalMs(config)
        assertEquals(1000.0, interval, 0.001)

        val beat0 = MetronomeBeatCalculator.calculateBeat(config, 0)
        val beat1 = MetronomeBeatCalculator.calculateBeat(config, 1)

        assertEquals(0L, beat0.timeMs)
        assertEquals(1000L, beat1.timeMs)
    }

    @Test
    fun `BPM140で丸め誤差が累積しない`() {
        val config = MetronomeConfig(bpm = 140, subdivision = BeatSubdivision.QUARTER)
        // 60000 / 140 = 428.57142857142856 ms
        // 140拍後は厳密に 60000 ms (1分) になるべき
        val beat140 = MetronomeBeatCalculator.calculateBeat(config, 140)
        assertEquals(60000L, beat140.timeMs)

        // 70拍後は 30000 ms
        val beat70 = MetronomeBeatCalculator.calculateBeat(config, 70)
        assertEquals(30000L, beat70.timeMs)
    }

    @Test
    fun `4分の4拍子のアクセント周期（強弱弱弱）`() {
        val config = MetronomeConfig(
            beatsPerBar = 4,
            subdivision = BeatSubdivision.QUARTER,
            accentEnabled = true
        )
        // 0: 強, 1: 弱, 2: 弱, 3: 弱, 4: 強
        assertTrue("Beat 0 はアクセント", MetronomeBeatCalculator.calculateBeat(config, 0).isAccent)
        assertFalse("Beat 1 は通常", MetronomeBeatCalculator.calculateBeat(config, 1).isAccent)
        assertFalse("Beat 2 は通常", MetronomeBeatCalculator.calculateBeat(config, 2).isAccent)
        assertFalse("Beat 3 は通常", MetronomeBeatCalculator.calculateBeat(config, 3).isAccent)
        assertTrue("Beat 4 はアクセント", MetronomeBeatCalculator.calculateBeat(config, 4).isAccent)
    }

    @Test
    fun `3分の4拍子のアクセント周期（強弱弱）`() {
        val config = MetronomeConfig(
            beatsPerBar = 3,
            subdivision = BeatSubdivision.QUARTER,
            accentEnabled = true
        )
        // 0: 強, 1: 弱, 2: 弱, 3: 強, 4: 弱
        assertTrue("Beat 0 はアクセント", MetronomeBeatCalculator.calculateBeat(config, 0).isAccent)
        assertFalse("Beat 1 は通常", MetronomeBeatCalculator.calculateBeat(config, 1).isAccent)
        assertFalse("Beat 2 は通常", MetronomeBeatCalculator.calculateBeat(config, 2).isAccent)
        assertTrue("Beat 3 はアクセント", MetronomeBeatCalculator.calculateBeat(config, 3).isAccent)
        assertFalse("Beat 4 は通常", MetronomeBeatCalculator.calculateBeat(config, 4).isAccent)
    }

    @Test
    fun `8分音符時のアクセント周期`() {
        // 4/4 8分音符: 1小節あたり8クリック。index 0, 8, 16... がアクセント
        val config44 = MetronomeConfig(
            beatsPerBar = 4,
            subdivision = BeatSubdivision.EIGHTH,
            accentEnabled = true
        )
        assertTrue(MetronomeBeatCalculator.calculateBeat(config44, 0).isAccent)
        for (i in 1..7) {
            assertFalse("Beat $i should not be accent", MetronomeBeatCalculator.calculateBeat(config44, i.toLong()).isAccent)
        }
        assertTrue(MetronomeBeatCalculator.calculateBeat(config44, 8).isAccent)

        // 3/4 8分音符: 1小節あたり6クリック。index 0, 6, 12... がアクセント
        val config34 = MetronomeConfig(
            beatsPerBar = 3,
            subdivision = BeatSubdivision.EIGHTH,
            accentEnabled = true
        )
        assertTrue(MetronomeBeatCalculator.calculateBeat(config34, 0).isAccent)
        for (i in 1..5) {
            assertFalse("Beat $i should not be accent", MetronomeBeatCalculator.calculateBeat(config34, i.toLong()).isAccent)
        }
        assertTrue(MetronomeBeatCalculator.calculateBeat(config34, 6).isAccent)
    }

    @Test
    fun `アクセントOFF時は全クリックが通常音`() {
        val config = MetronomeConfig(
            beatsPerBar = 4,
            subdivision = BeatSubdivision.QUARTER,
            accentEnabled = false
        )
        for (i in 0..8) {
            assertFalse(MetronomeBeatCalculator.calculateBeat(config, i.toLong()).isAccent)
        }
    }

    @Test
    fun `正負の拍位置オフセットが正確に反映される`() {
        // 正オフセット: +200ms -> 200, 700, 1200...
        val posConfig = MetronomeConfig(bpm = 120, beatOffsetMs = 200L)
        assertEquals(200L, MetronomeBeatCalculator.calculateBeat(posConfig, 0).timeMs)
        assertEquals(700L, MetronomeBeatCalculator.calculateBeat(posConfig, 1).timeMs)

        // 負オフセット: -200ms -> -200, 300, 800...
        val negConfig = MetronomeConfig(bpm = 120, beatOffsetMs = -200L)
        assertEquals(-200L, MetronomeBeatCalculator.calculateBeat(negConfig, 0).timeMs)
        assertEquals(300L, MetronomeBeatCalculator.calculateBeat(negConfig, 1).timeMs)
    }

    @Test
    fun `findCandidateClickIndexは負の予定時刻の拍をスキップする`() {
        // offset = -200ms: index 0 は -200ms、index 1 は 300ms
        val config = MetronomeConfig(bpm = 120, beatOffsetMs = -200L)
        val candidate = MetronomeBeatCalculator.findCandidateClickIndex(config, currentPositionMs = 0L)
        assertEquals("負の-200msはスキップされ、300msのindex 1が候補となる", 1L, candidate)
    }

    @Test
    fun `findCandidateClickIndexは許容遅延内の直近拍を採用し、超過した拍はスキップする`() {
        val config = MetronomeConfig(bpm = 120, beatOffsetMs = 0L) // 0, 500, 1000...
        // 許容遅延 30ms の場合
        // 0ms時点: index 0
        assertEquals(0L, MetronomeBeatCalculator.findCandidateClickIndex(config, currentPositionMs = 0L, toleratedDelayMs = 30L))
        // 25ms時点: 0msの拍から25ms遅れ（<= 30ms）なので index 0 が返る
        assertEquals(0L, MetronomeBeatCalculator.findCandidateClickIndex(config, currentPositionMs = 25L, toleratedDelayMs = 30L))
        // 35ms時点: 0msの拍から35ms遅れ（> 30ms）なのでスキップされ、次の500ms（index 1）が返る
        assertEquals(1L, MetronomeBeatCalculator.findCandidateClickIndex(config, currentPositionMs = 35L, toleratedDelayMs = 30L))
    }

    @Test
    fun `findNextClickIndexFromPositionは厳密に現在位置以降の拍を返す`() {
        val config = MetronomeConfig(bpm = 120, beatOffsetMs = 0L) // 0, 500, 1000...
        assertEquals(0L, MetronomeBeatCalculator.findNextClickIndexFromPosition(config, 0L))
        assertEquals(1L, MetronomeBeatCalculator.findNextClickIndexFromPosition(config, 1L))
        assertEquals(1L, MetronomeBeatCalculator.findNextClickIndexFromPosition(config, 500L))
        assertEquals(2L, MetronomeBeatCalculator.findNextClickIndexFromPosition(config, 501L))
    }

    @Test
    fun `BPM範囲外の正規化テスト`() {
        val tooLow = MetronomeConfig.normalize(bpm = 10)
        assertEquals(MetronomeConfig.MIN_BPM, tooLow.bpm)

        val tooHigh = MetronomeConfig.normalize(bpm = 500)
        assertEquals(MetronomeConfig.MAX_BPM, tooHigh.bpm)

        val invalidBeats = MetronomeConfig.normalize(beatsPerBar = 7)
        assertEquals(MetronomeConfig.DEFAULT_BEATS_PER_BAR, invalidBeats.beatsPerBar)

        val invalidOffset = MetronomeConfig.normalize(beatOffsetMs = 5000L)
        assertEquals(MetronomeConfig.MAX_BEAT_OFFSET_MS, invalidOffset.beatOffsetMs)
    }
}
