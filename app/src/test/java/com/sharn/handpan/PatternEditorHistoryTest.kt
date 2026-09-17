package com.sharn.handpan

import com.sharn.handpan.model.HandpanTechnique
import com.sharn.handpan.model.InstrumentProfile
import com.sharn.handpan.model.NoteEvent
import com.sharn.handpan.model.PatternEditorHistory
import com.sharn.handpan.model.PatternTextCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PatternEditorHistoryTest {
    @Test
    fun mutationsUndoRedoAndNewMutationInvalidatesRedo() {
        val first = NoteEvent(1, 0.0)
        val second = NoteEvent(7, 1.0, duration = 0.5, accent = true, hand = "L")
        val history = PatternEditorHistory(listOf(first))

        history.apply(listOf(first, second))
        assertTrue(history.canUndo)
        assertEquals(listOf(first), history.undo())
        assertTrue(history.canRedo)
        assertEquals(listOf(first, second), history.redo())

        history.apply(listOf(second))
        assertFalse(history.canRedo)
        assertEquals(listOf(first, second), history.undo())
    }

    @Test
    fun historyKeepsEventMetadataAcrossMoveDuplicateAndDeleteSnapshots() {
        val source = NoteEvent(
            noteNumber = 5,
            beatPosition = 1.25,
            duration = 1.5,
            velocity = 0.6f,
            accent = true,
            hand = "R",
            technique = HandpanTechnique.TAK
        )
        val duplicate = source.copy(
            id = java.util.UUID.randomUUID().toString(),
            beatPosition = 2.75
        )
        val history = PatternEditorHistory(listOf(source))

        history.apply(listOf(source, duplicate))
        assertEquals(source, history.current.first())
        assertEquals(duplicate, history.current[1])
        history.apply(listOf(duplicate))
        assertEquals(listOf(source, duplicate), history.undo())
        assertTrue(source.id != duplicate.id)
    }

    @Test
    fun parserUsesActiveProfileInsteadOfDefaultProfileMapping() {
        val parsed = PatternTextCodec.parse("C5@0:1", InstrumentProfile.D_CELTIC_MINOR).getOrThrow()
        assertEquals(8, parsed.single().noteNumber)
        assertEquals(523.25f, InstrumentProfile.D_CELTIC_MINOR.getFieldByNumber(8)?.frequencyHz)
    }
}
