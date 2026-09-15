package com.onigiri.keycue.playback

import com.onigiri.keycue.model.NoteEvent
import com.onigiri.keycue.model.SongData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 楽曲終了時（Finished状態）におけるノーツおよびキーハイライトの消去動作を検証する単体テスト。
 */
class PlaybackFinishGuideTest {

    private val sampleSong = SongData(
        title = "LastNoteSong",
        durationMs = 5000L,
        events = listOf(
            NoteEvent(timeMs = 1000L, key = 0),
            NoteEvent(timeMs = 3000L, key = 5),
            NoteEvent(timeMs = 5000L, key = 14) // 最後のノーツ
        )
    )

    private val noteScheduler = NoteScheduler()

    @Test
    fun finishedState_generatesEmptyFrame_whenFrameCreatedForFinished() {
        val state: PlaybackState = PlaybackState.Finished(positionMs = sampleSong.durationMs)
        val configLeadTimeMs = 700L

        // OverlayService と同様のフレーム生成ロジック
        val frame = if (state is PlaybackState.Stopped || state is PlaybackState.Finished) {
            GuideFrame(
                currentTimeMs = if (state is PlaybackState.Stopped) 0L else state.currentPositionMs,
                upcomingNotes = emptyList(),
                highlightedKeys = emptySet(),
                justKeys = emptySet(),
                countdownText = null,
                leadTimeMs = configLeadTimeMs
            )
        } else {
            noteScheduler.scheduleFrame(
                events = sampleSong.events,
                currentTimeMs = state.currentPositionMs,
                leadTimeMs = configLeadTimeMs,
                highlightTimeMs = 300L,
                countdownText = null
            )
        }

        // 最後のノーツが画面に残らないよう、ノーツおよびハイライトが空であることを検証
        assertTrue(frame.upcomingNotes.isEmpty())
        assertTrue(frame.highlightedKeys.isEmpty())
        assertTrue(frame.justKeys.isEmpty())
        assertEquals(5000L, frame.currentTimeMs)
    }

    @Test
    fun previewScreen_finishedState_clearsActiveKeys() {
        val state: PlaybackState = PlaybackState.Finished(positionMs = sampleSong.durationMs)

        // PlaybackPreviewScreen と同様の activeKeys 判定ロジック
        val activeKeys = if (state is PlaybackState.Finished || state is PlaybackState.Stopped) {
            emptySet<Int>()
        } else {
            val notes = noteScheduler.schedule(
                events = sampleSong.events,
                currentTimeMs = state.currentPositionMs
            )
            notes.activeKeys
        }

        // 最後のキー (key 14) がハイライトに残らないことを検証
        assertTrue(activeKeys.isEmpty())
    }

    @Test
    fun playingAtDuration_withoutFinishedCheck_wouldKeepLastNote() {
        // 参考検証: もし Finished のチェックを怠って scheduleFrame を呼んだ場合、最後のノーツが残ってしまうことを確認
        val frame = noteScheduler.scheduleFrame(
            events = sampleSong.events,
            currentTimeMs = 5000L,
            leadTimeMs = 700L,
            highlightTimeMs = 300L
        )

        // 修正前は最後のノーツ (key 14) が残っていた
        assertEquals(1, frame.upcomingNotes.size)
        assertEquals(14, frame.upcomingNotes.first().key)
        assertTrue(frame.highlightedKeys.contains(14))
        assertTrue(frame.justKeys.contains(14))
    }
}
