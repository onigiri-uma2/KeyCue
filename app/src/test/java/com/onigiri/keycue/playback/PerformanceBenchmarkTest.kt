package com.onigiri.keycue.playback

import com.onigiri.keycue.model.NoteEvent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.random.Random

/**
 * 先読み時間（noteLeadTimeMs / approachCircleLeadTimeMs）拡大時における
 * [NoteScheduler.scheduleFrame] の処理時間参考計測およびアルゴリズム整合性検証テスト。
 *
 * CI 安定性のため、ハードなミリ秒判定（○ms以下等）による Assert は行わず、
 * 計測ログの標準出力およびデータ構造・結果の正しさの検証に限定します。
 */
class PerformanceBenchmarkTest {

    private lateinit var scheduler: NoteScheduler
    private lateinit var denseEvents: List<NoteEvent>

    @Before
    fun setUp() {
        scheduler = NoteScheduler()
        // 1000 ノートの密集譜面を生成（0..100000ms の範囲、和音や連打を含む）
        val list = ArrayList<NoteEvent>(1000)
        val random = Random(42) // シード固定で決定的
        var t = 100L
        while (list.size < 1000) {
            val key = random.nextInt(15)
            list.add(NoteEvent(timeMs = t, key = key))

            // 30%の確率で同じ時刻に別キーを追加（和音）
            if (random.nextFloat() < 0.3f && list.size < 1000) {
                val chordKey = (key + 1 + random.nextInt(14)) % 15
                list.add(NoteEvent(timeMs = t, key = chordKey))
            }

            // 15%の確率で至近距離（80ms後）に同一キーを追加（連打）
            if (random.nextFloat() < 0.15f && list.size < 1000) {
                t += 80L
                list.add(NoteEvent(timeMs = t, key = key))
            } else {
                t += 50L + random.nextLong(150L)
            }
        }
        denseEvents = list.sortedBy { it.timeMs }
    }

    @Test
    fun benchmarkScheduleFrame_atDifferentLeadTimes() {
        // 譜面の事前計算
        scheduler.prepare(denseEvents)

        val leadTimes = listOf(300L, 1000L, 2000L)
        println("================================================================")
        println(" [Benchmark] NoteScheduler.scheduleFrame Performance Measurement")
        println(" Total Events: ${denseEvents.size} notes")
        println("================================================================")

        for (leadTimeMs in leadTimes) {
            val approachCircleLeadTimeMs = (leadTimeMs * 0.6f).toLong().coerceAtLeast(200L)

            // JIT ウォームアップ
            for (step in 0 until 50) {
                val curTime = 10_000L + step * 16L
                scheduler.scheduleFrame(
                    events = denseEvents,
                    currentTimeMs = curTime,
                    noteLeadTimeMs = leadTimeMs,
                    approachCircleLeadTimeMs = approachCircleLeadTimeMs
                )
            }

            // 計測実行 (100 フレームシミュレーション)
            val frameCount = 100
            val elapsedNanos = LongArray(frameCount)
            var totalCandidateNotes = 0
            var totalChordGroups = 0
            var totalCircles = 0

            for (step in 0 until frameCount) {
                val curTime = 20_000L + step * 16L
                val start = System.nanoTime()
                val frame = scheduler.scheduleFrame(
                    events = denseEvents,
                    currentTimeMs = curTime,
                    noteLeadTimeMs = leadTimeMs,
                    approachCircleLeadTimeMs = approachCircleLeadTimeMs
                )
                val duration = System.nanoTime() - start
                elapsedNanos[step] = duration

                totalCandidateNotes += frame.upcomingNotes.size
                totalChordGroups += frame.chordGroups.size
                totalCircles += frame.approachCircles.size

                // 結果整合性の検証
                assertFalse("フレームが正しく生成されていること", frame.keyHighlightProgress.isEmpty())
                // ChordGroup は timeMs 降順であること
                for (i in 0 until frame.chordGroups.size - 1) {
                    assertTrue(
                        "chordGroups は降順",
                        frame.chordGroups[i].timeMs >= frame.chordGroups[i + 1].timeMs
                    )
                }
            }

            val avgNanos = elapsedNanos.average()
            val maxNanos = elapsedNanos.maxOrNull() ?: 0L
            val avgMs = avgNanos / 1_000_000.0
            val maxMs = maxNanos / 1_000_000.0
            val avgUpcoming = totalCandidateNotes / frameCount.toDouble()
            val avgChords = totalChordGroups / frameCount.toDouble()
            val avgCircles = totalCircles / frameCount.toDouble()

            println(
                String.format(
                    "LeadTime: %4dms | Avg: %6.3f ms (%7.1f μs) | Max: %6.3f ms | Notes: %4.1f | Chords: %4.1f | Circles: %4.1f",
                    leadTimeMs, avgMs, avgNanos / 1_000.0, maxMs, avgUpcoming, avgChords, avgCircles
                )
            )
        }
        println("================================================================")
    }
}
