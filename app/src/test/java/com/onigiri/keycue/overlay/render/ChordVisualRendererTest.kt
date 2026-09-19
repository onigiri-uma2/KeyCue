package com.onigiri.keycue.overlay.render

import com.onigiri.keycue.model.NoteEvent
import com.onigiri.keycue.playback.FallingNoteCalculator
import com.onigiri.keycue.playback.NoteScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ChordVisualRenderer] の和音表示判定ロジックに関する単体テスト。
 *
 * Falling Notes OFF 時はキー上の Approach Circle を視覚的にグループ化するため、
 * [ChordVisualRenderer.isChordVisibleWhenFallingNotesOff] を実描画処理とテストで一元的に使用する。
 */
class ChordVisualRendererTest {

    private val chordTimeMs = 1000L

    @Test
    fun isChordVisibleWhenFallingNotesOff_default200Ms_boundary() {
        val approachCircleLeadTimeMs = 200L

        // remainingTime = 200ms (currentTimeMs = 800) -> true
        val visibleAt200Ms = ChordVisualRenderer.isChordVisibleWhenFallingNotesOff(
            chordTimeMs = chordTimeMs,
            currentTimeMs = 800L,
            approachCircleLeadTimeMs = approachCircleLeadTimeMs
        )
        assertTrue("remainingTime = 200ms は表示対象 (true)", visibleAt200Ms)

        // remainingTime = 201ms (currentTimeMs = 799) -> false
        val hiddenAt201Ms = ChordVisualRenderer.isChordVisibleWhenFallingNotesOff(
            chordTimeMs = chordTimeMs,
            currentTimeMs = 799L,
            approachCircleLeadTimeMs = approachCircleLeadTimeMs
        )
        assertFalse("remainingTime = 201ms は非表示 (false)", hiddenAt201Ms)
    }

    @Test
    fun isChordVisibleWhenFallingNotesOff_custom300Ms_tracksSetting() {
        val approachCircleLeadTimeMs = 300L

        // remainingTime = 300ms (currentTimeMs = 700) -> true
        val visibleAt300Ms = ChordVisualRenderer.isChordVisibleWhenFallingNotesOff(
            chordTimeMs = chordTimeMs,
            currentTimeMs = 700L,
            approachCircleLeadTimeMs = approachCircleLeadTimeMs
        )
        assertTrue("remainingTime = 300ms は表示対象 (true)", visibleAt300Ms)

        // remainingTime = 301ms (currentTimeMs = 699) -> false
        val hiddenAt301Ms = ChordVisualRenderer.isChordVisibleWhenFallingNotesOff(
            chordTimeMs = chordTimeMs,
            currentTimeMs = 699L,
            approachCircleLeadTimeMs = approachCircleLeadTimeMs
        )
        assertFalse("remainingTime = 301ms は非表示 (false)", hiddenAt301Ms)
    }

    @Test
    fun chordVisibility_noteLeadTimeMs_separationAndIndependence() {
        val approachCircleLeadTimeMs = 100L

        // 1. noteLeadTimeMs = 300ms の場合
        // Falling Notes OFF の表示開始は approachCircleLeadTimeMs (100ms前) に従う
        assertTrue(
            "OFF時: 100ms前は表示対象",
            ChordVisualRenderer.isChordVisibleWhenFallingNotesOff(
                chordTimeMs = chordTimeMs,
                currentTimeMs = 900L,
                approachCircleLeadTimeMs = approachCircleLeadTimeMs
            )
        )
        assertFalse(
            "OFF時: 101ms前は非表示",
            ChordVisualRenderer.isChordVisibleWhenFallingNotesOff(
                chordTimeMs = chordTimeMs,
                currentTimeMs = 899L,
                approachCircleLeadTimeMs = approachCircleLeadTimeMs
            )
        )
        assertFalse(
            "OFF時: 300ms前は非表示",
            ChordVisualRenderer.isChordVisibleWhenFallingNotesOff(
                chordTimeMs = chordTimeMs,
                currentTimeMs = 700L,
                approachCircleLeadTimeMs = approachCircleLeadTimeMs
            )
        )

        // 2. noteLeadTimeMs = 500ms へ変更しても、OFF時の表示開始は 100ms 前のまま不変
        assertTrue(
            "OFF時 (noteLeadTimeMs=500ms変更後も): 100ms前は表示対象のまま",
            ChordVisualRenderer.isChordVisibleWhenFallingNotesOff(
                chordTimeMs = chordTimeMs,
                currentTimeMs = 900L,
                approachCircleLeadTimeMs = approachCircleLeadTimeMs
            )
        )
        assertFalse(
            "OFF時 (noteLeadTimeMs=500ms変更後も): 101ms前は非表示",
            ChordVisualRenderer.isChordVisibleWhenFallingNotesOff(
                chordTimeMs = chordTimeMs,
                currentTimeMs = 899L,
                approachCircleLeadTimeMs = approachCircleLeadTimeMs
            )
        )
        assertFalse(
            "OFF時 (noteLeadTimeMs=500ms変更後も): 500ms前は非表示",
            ChordVisualRenderer.isChordVisibleWhenFallingNotesOff(
                chordTimeMs = chordTimeMs,
                currentTimeMs = 500L,
                approachCircleLeadTimeMs = approachCircleLeadTimeMs
            )
        )

        // 3. 一方で Falling Notes ON 側は noteLeadTimeMs に従う責務分離を確認
        val progressOn300At300Ms = FallingNoteCalculator.calculateProgress(
            eventTimeMs = chordTimeMs,
            currentTimeMs = 700L,
            noteLeadTimeMs = 300L
        )
        assertTrue("ON時 (noteLeadTimeMs=300ms): 300ms前は表示対象", FallingNoteCalculator.shouldDraw(progressOn300At300Ms))

        val progressOn300At301Ms = FallingNoteCalculator.calculateProgress(
            eventTimeMs = chordTimeMs,
            currentTimeMs = 699L,
            noteLeadTimeMs = 300L
        )
        assertFalse("ON時 (noteLeadTimeMs=300ms): 301ms前は非表示", FallingNoteCalculator.shouldDraw(progressOn300At301Ms))

        val progressOn500At500Ms = FallingNoteCalculator.calculateProgress(
            eventTimeMs = chordTimeMs,
            currentTimeMs = 500L,
            noteLeadTimeMs = 500L
        )
        assertTrue("ON時 (noteLeadTimeMs=500ms): 500ms前は表示対象", FallingNoteCalculator.shouldDraw(progressOn500At500Ms))
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

        val visibleOff200 = ChordVisualRenderer.isChordVisibleWhenFallingNotesOff(
            chordTimeMs = frameAt200Ms.chordGroups[0].timeMs,
            currentTimeMs = frameAt200Ms.currentTimeMs,
            approachCircleLeadTimeMs = frameAt200Ms.approachCircleLeadTimeMs
        )
        assertTrue("Falling Notes OFF: 200ms前はChord表示対象", visibleOff200)

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

        val visibleOff250 = ChordVisualRenderer.isChordVisibleWhenFallingNotesOff(
            chordTimeMs = frameAt250Ms.chordGroups[0].timeMs,
            currentTimeMs = frameAt250Ms.currentTimeMs,
            approachCircleLeadTimeMs = frameAt250Ms.approachCircleLeadTimeMs
        )
        assertFalse("Falling Notes OFF: Approach Circle 未出現の250ms前はChordも非表示", visibleOff250)
    }

    @Test
    fun shouldDrawHaloFill_fourCases() {
        // Case 1: Halo ON + Falling OFF -> true (唯一Fillが表示される条件)
        assertTrue(
            "Halo ON + Falling OFF は Fill 表示対象 (true)",
            ChordVisualRenderer.shouldDrawHaloFill(showChordHalos = true, showFallingNotes = false)
        )

        // Case 2: Halo ON + Falling ON -> false (落下ノート表示時はFill無効)
        assertFalse(
            "Halo ON + Falling ON は Fill 非表示 (false)",
            ChordVisualRenderer.shouldDrawHaloFill(showChordHalos = true, showFallingNotes = true)
        )

        // Case 3: Halo OFF + Falling OFF -> false (Halo自体が無効時はFillも無効)
        assertFalse(
            "Halo OFF + Falling OFF は Fill 非表示 (false)",
            ChordVisualRenderer.shouldDrawHaloFill(showChordHalos = false, showFallingNotes = false)
        )

        // Case 4: Halo OFF + Falling ON -> false (両方無効)
        assertFalse(
            "Halo OFF + Falling ON は Fill 非表示 (false)",
            ChordVisualRenderer.shouldDrawHaloFill(showChordHalos = false, showFallingNotes = true)
        )
    }
}
