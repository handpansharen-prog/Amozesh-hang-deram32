package com.sharn.handpan

import com.sharn.handpan.audio.TechniqueAudioFeatures
import com.sharn.handpan.audio.TechniqueDetectionResult
import com.sharn.handpan.audio.toDiagnosticSnapshot
import com.sharn.handpan.model.HandpanTechnique
import org.junit.Assert.assertEquals
import org.junit.Test

class TechniqueDiagnosticSnapshotTest {
    @Test
    fun snapshotPreservesCalibrationFieldsAndClassificationDecision() {
        val result = TechniqueDetectionResult(
            detectedTechnique = HandpanTechnique.SLAP,
            confidence = 0.82f,
            features = TechniqueAudioFeatures(
                rms = 0.31f,
                peak = 0.9f,
                transientToSustainRatio = 4.2f,
                zeroCrossingRate = 0.18f,
                onsetStrength = 0.76f,
                pitchConfidence = 0.12f,
                signalQuality = 0.88f
            )
        )

        val snapshot = result.toDiagnosticSnapshot(123_000L, 0f)

        assertEquals(123_000L, snapshot.timestampNanos)
        assertEquals(0.31f, snapshot.rms, 0f)
        assertEquals(0.9f, snapshot.peak, 0f)
        assertEquals(0.76f, snapshot.onsetStrength, 0f)
        assertEquals(0f, snapshot.pitchHz, 0f)
        assertEquals(0.12f, snapshot.pitchConfidence, 0f)
        assertEquals(4.2f, snapshot.transientToSustainRatio, 0f)
        assertEquals(0.18f, snapshot.zeroCrossingRate, 0f)
        assertEquals(HandpanTechnique.SLAP, snapshot.detectedTechnique)
        assertEquals(0.82f, snapshot.confidence, 0f)
        assertEquals(null, snapshot.rejectionReason)
    }
}