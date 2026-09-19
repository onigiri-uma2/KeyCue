package com.onigiri.keycue.overlay.render

import com.onigiri.keycue.model.NoteEvent
import com.onigiri.keycue.playback.NoteScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ChordVisualRenderer] の和音表示判定ロジックに関する単体テスト。
 *
 * - Falling Notes ON: Chord Link/Halo は落下ノートに追従するため [noteLeadTimeMs] を使用する。
 * - Falling Notes OFF: Chord Link/Halo はキー上の Approach Circle を視覚的にグループ化するため [approachCircleLeadTimeMs] を使用する。
 */
class ChordVisualRendererTest {

    private val chordTimeMs = 1000L

    @Test
    fun isChordVisible_fallingNotesOn_boundaryAtNoteLeadTimeMs() {
        val noteLeadTimeMs = 300L
        val approachCircleLeadTimeMs = 200L

        // 300ms前 (currentTimeMs = 700) -> 落下ノート追従のため表示対象
        val visibleAt300MsBefore = ChordVisualRenderer.isChordVisible(
            chordTimeMs = chordTimeMs,
            currentTimeMs = 700L,
            noteLeadTimeMs = noteLeadTimeMs,
            approachCircleLeadTimeMs = approachCircleLeadTimeMs,
            showFallingNotes = true
        )
        assertTrue("Falling Notes ON: 300ms前は表示対象", visibleAt300MsBefore)

        // 301ms前 (currentTimeMs = 699) -> 非表示
        val hiddenAt301MsBefore = ChordVisualRenderer.isChordVisible(
            chordTimeMs = chordTimeMs,
            currentTimeMs = 699L,
            noteLeadTimeMs = noteLeadTimeMs,
            approachCircleLeadTimeMs = approachCircleLeadTimeMs,
            showFallingNotes = true
        )
        assertFalse("Falling Notes ON: 301ms前は非表示", hiddenAt301MsBefore)
    }

    @Test
    fun isChordVisible_fallingNotesOff_boundaryAtApproachCircleLeadTimeMs() {
        val noteLeadTimeMs = 300L
        val approachCircleLeadTimeMs = 200L

        // 200ms前 (currentTimeMs = 800) -> Approach Circle グループ化のため表示対象
        val visibleAt200MsBefore = ChordVisualRenderer.isChordVisible(
            chordTimeMs = chordTimeMs,
            currentTimeMs = 800L,
            noteLeadTimeMs = noteLeadTimeMs,
            approachCircleLeadTimeMs = approachCircleLeadTimeMs,
            showFallingNotes = false
        )
        assertTrue("Falling Notes OFF: 200ms前は表示対象", visibleAt200MsBefore)

        // 201ms前 (currentTimeMs = 799) -> 非表示
        val hiddenAt201MsBefore = ChordVisualRenderer.isChordVisible(
            chordTimeMs = chordTimeMs,
            currentTimeMs = 799L,
            noteLeadTimeMs = noteLeadTimeMs,
            approachCircleLeadTimeMs = approachCircleLeadTimeMs,
            showFallingNotes = false
        )
        assertFalse("Falling Notes OFF: 201ms前は非表示", hiddenAt201MsBefore)

        // 300ms前 (currentTimeMs = 700) -> OFF 時は Approach Circle 未出現のため非表示
        val hiddenAt300MsBefore = ChordVisualRenderer.isChordVisible(
            chordTimeMs = chordTimeMs,
            currentTimeMs = 700L,
            noteLeadTimeMs = noteLeadTimeMs,
            approachCircleLeadTimeMs = approachCircleLeadTimeMs,
            showFallingNotes = false
        )
        assertFalse("Falling Notes OFF: 300ms前は非表示", hiddenAt300MsBefore)
    }

    @Test
    fun isChordVisible_fallingNotesOn_tracksCustomNoteLeadTimeMs() {
        val noteLeadTimeMs = 500L
        val approachCircleLeadTimeMs = 200L

        // 500ms前 (currentTimeMs = 500) -> ON 時は表示対象
        assertTrue(
            "Falling Notes ON: 500ms前は表示対象",
            ChordVisualRenderer.isChordVisible(
                chordTimeMs = chordTimeMs,
                currentTimeMs = 500L,
                noteLeadTimeMs = noteLeadTimeMs,
                approachCircleLeadTimeMs = approachCircleLeadTimeMs,
                showFallingNotes = true
            )
        )

        // 501ms前 (currentTimeMs = 499) -> ON 時は非表示
        assertFalse(
            "Falling Notes ON: 501ms前は非表示",
            ChordVisualRenderer.isChordVisible(
                chordTimeMs = chordTimeMs,
                currentTimeMs = 499L,
                noteLeadTimeMs = noteLeadTimeMs,
                approachCircleLeadTimeMs = approachCircleLeadTimeMs,
                showFallingNotes = true
            )
        )

        // OFF 時は 500ms 前は非表示（200ms 前から表示されるため）
        assertFalse(
            "Falling Notes OFF: 500ms前は非表示",
            ChordVisualRenderer.isChordVisible(
                chordTimeMs = chordTimeMs,
                currentTimeMs = 500L,
                noteLeadTimeMs = noteLeadTimeMs,
                approachCircleLeadTimeMs = approachCircleLeadTimeMs,
                showFallingNotes = false
            )
        )
    }

    @Test
    fun isChordVisible_fallingNotesOff_tracksCustomApproachCircleLeadTimeMs() {
        val noteLeadTimeMs = 500L
        val approachCircleLeadTimeMs = 350L

        // 350ms前 (currentTimeMs = 650) -> OFF 時に表示対象
        assertTrue(
            "Falling Notes OFF: 350ms前は表示対象",
            ChordVisualRenderer.isChordVisible(
                chordTimeMs = chordTimeMs,
                currentTimeMs = 650L,
                noteLeadTimeMs = noteLeadTimeMs,
                approachCircleLeadTimeMs = approachCircleLeadTimeMs,
                showFallingNotes = false
            )
        )

        // 351ms前 (currentTimeMs = 649) -> OFF 時に非表示
        assertFalse(
            "Falling Notes OFF: 351ms前は非表示",
            ChordVisualRenderer.isChordVisible(
                chordTimeMs = chordTimeMs,
                currentTimeMs = 649L,
                noteLeadTimeMs = noteLeadTimeMs,
                approachCircleLeadTimeMs = approachCircleLeadTimeMs,
                showFallingNotes = false
            )
        )
    }

    @Test
    fun chordVisibility_withNoteSchedulerFrame_synchronizesWithApproachCircleInOffMode() {
        val scheduler = NoteScheduler()
        val events = listOf(
            NoteEvent(timeMs = 1000L, key = 0),
            NoteEvent(timeMs = 1000L, key = 4)
        )
        val noteLeadTimeMs = 300L
        val approachCircleLeadTimeMs = 200L

        // 200ms前 (800ms):
        val frameAt200Ms = scheduler.scheduleFrame(
            events = events,
            currentTimeMs = 800L,
            noteLeadTimeMs = noteLeadTimeMs,
            approachCircleLeadTimeMs = approachCircleLeadTimeMs
        )
        // chordGroups に含まれており、ApproachCircle も存在する
        assertEquals(1, frameAt200Ms.chordGroups.size)
        assertEquals(2, frameAt200Ms.approachCircles.size)

        val visibleOff200 = ChordVisualRenderer.isChordVisible(
            chordTimeMs = frameAt200Ms.chordGroups[0].timeMs,
            currentTimeMs = frameAt200Ms.currentTimeMs,
            noteLeadTimeMs = frameAt200Ms.noteLeadTimeMs,
            approachCircleLeadTimeMs = frameAt200Ms.approachCircleLeadTimeMs,
            showFallingNotes = false
        )
        assertTrue("Falling Notes OFF: 200ms前は表示対象", visibleOff200)

        // 250ms前 (750ms):
        val frameAt250Ms = scheduler.scheduleFrame(
            events = events,
            currentTimeMs = 750L,
            noteLeadTimeMs = noteLeadTimeMs,
            approachCircleLeadTimeMs = approachCircleLeadTimeMs
        )
        // noteLeadTimeMs (300ms) 範囲内なので chordGroups には含まれるが、ApproachCircle (200ms) はまだ無い
        assertEquals(1, frameAt250Ms.chordGroups.size)
        assertTrue(frameAt250Ms.approachCircles.isEmpty())

        val visibleOff250 = ChordVisualRenderer.isChordVisible(
            chordTimeMs = frameAt250Ms.chordGroups[0].timeMs,
            currentTimeMs = frameAt250Ms.currentTimeMs,
            noteLeadTimeMs = frameAt250Ms.noteLeadTimeMs,
            approachCircleLeadTimeMs = frameAt250Ms.approachCircleLeadTimeMs,
            showFallingNotes = false
        )
        assertFalse("Falling Notes OFF: Approach Circle 未出現の250ms前はChordも非表示", visibleOff250)

        val visibleOn250 = ChordVisualRenderer.isChordVisible(
            chordTimeMs = frameAt250Ms.chordGroups[0].timeMs,
            currentTimeMs = frameAt250Ms.currentTimeMs,
            noteLeadTimeMs = frameAt250Ms.noteLeadTimeMs,
            approachCircleLeadTimeMs = frameAt250Ms.approachCircleLeadTimeMs,
            showFallingNotes = true
        )
        assertTrue("Falling Notes ON: 落下追従中の250ms前はChord表示対象", visibleOn250)
    }
}
