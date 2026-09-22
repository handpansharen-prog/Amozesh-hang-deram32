package com.sharn.handpan

import com.sharn.handpan.audio.TechniqueAudioFeatures
import com.sharn.handpan.audio.TechniqueDetectionResult
import com.sharn.handpan.audio.toDiagnosticSnapshot
import com.sharn.handpan.model.HandpanTechnique
import com.sharn.handpan.model.AudioFrameQuality
import com.sharn.handpan.model.AudioFrameStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun snapshotPreservesFrameAndPitchDiagnostics() {
        val quality = AudioFrameQuality(
            sampleCount = 2048,
            sampleRateHz = 22050,
            rms = 0.31f,
            peak = 0.9f,
            clippingRatio = 0.01f,
            noiseFloorRms = 0.02f,
            signalToNoiseRatioDb = 23f,
            signalConfidence = 0.88f,
            status = AudioFrameStatus.VALID,
            captureTimestampNanos = 100L,
            analysisStartTimestampNanos = 200L,
            analysisEndTimestampNanos = 300L
        )
        val snapshot = TechniqueDetectionResult(
            detectedTechnique = HandpanTechnique.TONE,
            confidence = 0.8f,
            features = TechniqueAudioFeatures(0.31f, 0.9f, 1.2f, 0.1f, 0.7f, 0.9f, 0.88f)
        ).toDiagnosticSnapshot(
            timestampNanos = 500L,
            pitchHz = 146.8f,
            noteName = "D3",
            centsOffset = -4,
            pitchValid = true,
            onsetSampleOffset = 128,
            audioQuality = quality
        )

        assertEquals("D3", snapshot.noteName)
        assertEquals(-4, snapshot.centsOffset)
        assertTrue(snapshot.pitchValid)
        assertEquals(128, snapshot.onsetSampleOffset)
        assertEquals(2048, snapshot.sampleCount)
        assertEquals(22050, snapshot.sampleRateHz)
        assertEquals(0.02f, snapshot.noiseFloorRms, 0f)
        assertEquals(23f, snapshot.signalToNoiseRatioDb, 0f)
        assertEquals(AudioFrameStatus.VALID, snapshot.frameQuality)
    }
}