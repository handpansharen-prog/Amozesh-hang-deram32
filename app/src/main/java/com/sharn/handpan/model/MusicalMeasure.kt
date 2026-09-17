package com.sharn.handpan.model

import kotlin.math.floor

/** Position of an event in the canonical musical measure grid. */
data class MusicalMeasurePosition(
    val measureIndex: Int,
    val beatInMeasure: Double,
    val subdivisionIndex: Int
)

data class MusicalOverlap(
    val firstEventId: String,
    val secondEventId: String
)

object MusicalMeasure {
    fun positionAt(beatPosition: Double, timeSignature: TimeSignature): MusicalMeasurePosition {
        require(beatPosition >= 0.0) { "Beat position must be non-negative" }
        val beatsPerMeasure = timeSignature.beatsPerBar.toDouble()
        val measureIndex = floor(beatPosition / beatsPerMeasure).toInt()
        val beatInMeasure = beatPosition - measureIndex * beatsPerMeasure
        val subdivisionIndex = floor((beatInMeasure - floor(beatInMeasure)) * 16.0 + 1e-9).toInt()
        return MusicalMeasurePosition(measureIndex, beatInMeasure, subdivisionIndex)
    }

    fun overlaps(events: List<NoteEvent>): List<MusicalOverlap> {
        val ordered = events.sortedBy { it.beatPosition }
        return ordered.flatMapIndexed { index, first ->
            ordered.drop(index + 1).takeWhile { it.beatPosition < first.beatPosition + first.duration }
                .filter { it.beatPosition < first.beatPosition + first.duration }
                .map { MusicalOverlap(first.id, it.id) }
        }
    }
}
