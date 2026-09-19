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
    fun strokeWidthDpToPx_calculatesCorrectPxForVariousDensities() {
        // 2.0 dp
        assertEquals(2.0f, ChordVisualRenderer.strokeWidthDpToPx(2.0f, 1.0f), 0.001f)
        assertEquals(4.0f, ChordVisualRenderer.strokeWidthDpToPx(2.0f, 2.0f), 0.001f)
        assertEquals(6.0f, ChordVisualRenderer.strokeWidthDpToPx(2.0f, 3.0f), 0.001f)
        assertEquals(8.0f, ChordVisualRenderer.strokeWidthDpToPx(2.0f, 4.0f), 0.001f)

        // 0.5 dp
        assertEquals(1.5f, ChordVisualRenderer.strokeWidthDpToPx(0.5f, 3.0f), 0.001f)

        // 6.0 dp
        assertEquals(18.0f, ChordVisualRenderer.strokeWidthDpToPx(6.0f, 3.0f), 0.001f)
    }

    @Test
    fun alphaPercentToAlpha_convertsPercentTo0To255Correctly() {
        // 10% (Halo Fill デフォルト) -> round(10 * 255 / 100) = round(25.5) = 26 (従来の HALO_FILL_ALPHA=26 と同等)
        assertEquals(26, ChordVisualRenderer.alphaPercentToAlpha(10))

        // 20% -> round(20 * 255 / 100) = round(51.0) = 51
        assertEquals(51, ChordVisualRenderer.alphaPercentToAlpha(20))

        // 50% (Halo Fill 上限) -> round(50 * 255 / 100) = round(127.5) = 128
        assertEquals(128, ChordVisualRenderer.alphaPercentToAlpha(50))

        // 60% (Stroke デフォルト) -> round(60 * 255 / 100) = round(153.0) = 153
        assertEquals(153, ChordVisualRenderer.alphaPercentToAlpha(60))

        // 100% -> round(100 * 255 / 100) = 255
        assertEquals(255, ChordVisualRenderer.alphaPercentToAlpha(100))
    }
}

