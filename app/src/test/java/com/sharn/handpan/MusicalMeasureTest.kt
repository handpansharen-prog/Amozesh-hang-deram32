package com.sharn.handpan

import com.sharn.handpan.model.HandpanTechnique
import com.sharn.handpan.model.NoteEvent
import com.sharn.handpan.model.MusicalMeasure
import com.sharn.handpan.model.TimeSignature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicalMeasureTest {
    @Test
    fun positionsRespectMeasureBoundariesForAllSupportedMeters() {
        val cases = listOf(
            TimeSignature.March24 to listOf(0.0 to 0, 2.0 to 1),
            TimeSignature.Waltz34 to listOf(0.0 to 0, 3.0 to 1),
            TimeSignature.Common44 to listOf(0.0 to 0, 4.0 to 1),
            TimeSignature.SixEight68 to listOf(0.5 to 0, 6.0 to 1),
            TimeSignature.SevenEight272 to listOf(1.5 to 0, 7.0 to 1),
            TimeSignature.NineEight98 to listOf(2.75 to 0, 9.0 to 1)
        )
        cases.forEach { (signature, positions) ->
            positions.forEach { (beat, expectedMeasure) ->
                assertEquals(expectedMeasure, MusicalMeasure.positionAt(beat, signature).measureIndex)
            }
        }
    }

    @Test
    fun fractionalBeatAndSubdivisionArePreserved() {
        val position = MusicalMeasure.positionAt(1.5, TimeSignature.Common44)
        assertEquals(0, position.measureIndex)
        assertEquals(1.5, position.beatInMeasure, 0.0)
        assertEquals(8, position.subdivisionIndex)
    }

    @Test
    fun durationOverlapIsDetectedWithoutMovingEvents() {
        val first = NoteEvent(1, 0.0, duration = 2.0)
        val second = NoteEvent(7, 1.0, duration = 1.0, technique = HandpanTechnique.TONE)
        val overlaps = MusicalMeasure.overlaps(listOf(first, second))
        assertTrue(overlaps.any { it.firstEventId == first.id && it.secondEventId == second.id })
        assertEquals(0.0, first.beatPosition, 0.0)
        assertEquals(1.0, second.beatPosition, 0.0)
    }
}
