package com.sharn.handpan

import com.sharn.handpan.model.HandpanTechnique
import com.sharn.handpan.model.MusicEvent
import com.sharn.handpan.model.NoteEvent
import com.sharn.handpan.model.PatternTextCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PatternTextCodecTest {
    @Test
    fun parsesNumericPitchSolfegeDingRestAndMetadata() {
        val events = PatternTextCodec.parse(
            "1@0:0.5  Bb3@0.5:1.5#GHOST_NOTE^L~0.7!  لا@2.5:1  DING@3:1  REST@4:2"
        ).getOrThrow()

        assertEquals(listOf(1, 2, 1, 0, 0), events.map(NoteEvent::noteNumber))
        assertEquals(0.5, events[0].duration, 0.0)
        assertEquals(0.5, events[1].beatPosition, 0.0)
        assertEquals(HandpanTechnique.GHOST_NOTE, events[1].technique)
        assertEquals("L", events[1].hand)
        assertEquals(0.7f, events[1].velocity)
        assertTrue(events[1].accent)
        assertTrue(events.last().isRest)
        assertEquals(HandpanTechnique.REST, events.last().technique)
    }

    @Test
    fun parserUsesSequentialPositionsWhenPositionIsOmitted() {
        val events = PatternTextCodec.parse("1 7 REST 3").getOrThrow()
        assertEquals(listOf(0.0, 1.0, 2.0, 3.0), events.map(NoteEvent::beatPosition))
        assertTrue(events[2].isRest)
    }

    @Test
    fun serializerRoundTripPreservesMusicalEventMetadata() {
        val original = listOf(
            NoteEvent(1, 0.25, duration = 0.5, velocity = 0.7f, accent = true, hand = "R"),
            NoteEvent(5, 1.25, duration = 1.5, technique = HandpanTechnique.TAK),
            NoteEvent(0, 2.5, duration = 2.0, isRest = true)
        )
        val restored = PatternTextCodec.parse(PatternTextCodec.serialize(original)).getOrThrow()
        assertEquals(original.map { it.copy(id = "") }, restored.map { it.copy(id = "") })
    }

    @Test
    fun invalidTokensReturnFailureWithoutThrowingToCaller() {
        assertFalse(PatternTextCodec.parse("10").isSuccess)
        assertFalse(PatternTextCodec.parse("A3@-1").isSuccess)
        assertFalse(PatternTextCodec.parse("1@0:0").isSuccess)
        assertFalse(PatternTextCodec.parse("UNKNOWN").isSuccess)
        assertFalse(PatternTextCodec.parse("1#UNKNOWN").isSuccess)
        assertFalse(PatternTextCodec.parse("D").isSuccess)
    }

    @Test
    fun musicEventConversionPreservesTechnique() {
        val source = MusicEvent(
            targetNumber = 5,
            beatPosition = 1.25,
            duration = 0.5,
            technique = HandpanTechnique.TAK
        )
        val restored = MusicEvent.fromLegacyNoteEvent(source.toLegacyNoteEvent())
        assertEquals(HandpanTechnique.TAK, restored.technique)
        assertEquals(source.beatPosition, restored.beatPosition, 0.0)
        assertEquals(source.duration, restored.duration, 0.0)
    }
}
