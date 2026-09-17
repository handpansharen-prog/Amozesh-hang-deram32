package com.sharn.handpan

import com.sharn.handpan.audio.RuleBasedTechniqueDetector
import com.sharn.handpan.audio.TechniqueFeatureExtractor
import com.sharn.handpan.model.AudioFrameStatus
import com.sharn.handpan.model.AudioFrameQualityAnalyzer
import com.sharn.handpan.model.HandpanTechnique
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

class TechniqueDetectionTest {
    private val sampleRate = 22_050
    private val frameSize = 2_048
    private val detector = RuleBasedTechniqueDetector()

    @Test
    fun syntheticDingIsClassifiedFromRootPitchAndSustain() {
        val samples = sineFrame(146.83, 0.8)
        val result = detect(samples, frequencyHz = 146.83f, pitchConfidence = 0.95f)

        assertEquals(HandpanTechnique.DING, result.detectedTechnique)
        assertTrue(result.confidence in 0f..1f)
    }

    @Test
    fun syntheticToneIsClassifiedFromNonRootPitchAndSustain() {
        val samples = sineFrame(220.0, 0.8)
        val result = detect(samples, frequencyHz = 220f, pitchConfidence = 0.95f)

        assertEquals(HandpanTechnique.TONE, result.detectedTechnique)
        assertTrue(result.confidence in 0f..1f)
    }

    @Test
    fun syntheticTransientIsClassifiedAsSlapWithoutPitchEvidence() {
        val samples = decayingTransient()
        val result = detect(samples, frequencyHz = 0f, pitchConfidence = 0.1f, onsetStrength = 0.9f)

        assertEquals(HandpanTechnique.SLAP, result.detectedTechnique)
        assertTrue(result.confidence in 0f..1f)
    }

    @Test
    fun silenceIsUnknown() {
        val samples = ShortArray(frameSize)
        val result = detect(samples, frequencyHz = 0f, pitchConfidence = 0f)

        assertNull(result.detectedTechnique)
        assertEquals("invalid-audio-quality", result.rejectionReason)
        assertEquals(0f, result.confidence, 0f)
    }

    @Test
    fun noisySignalIsRejected() {
        val samples = ShortArray(frameSize) { index ->
            (((index * 7919) % 20_000) - 10_000).toShort()
        }
        val quality = AudioFrameQualityAnalyzer.analyze(
            samples = samples,
            sampleCount = samples.size,
            sampleRateHz = sampleRate,
            noiseFloorRms = 0.2f,
            captureTimestampNanos = 1_000L,
            analysisStartTimestampNanos = 2_000L,
            analysisEndTimestampNanos = 3_000L
        )
        assertEquals(AudioFrameStatus.NOISY, quality.status)
        val features = TechniqueFeatureExtractor.extract(samples, samples.size, quality.rms, 0.8f, 0.1f, quality.signalConfidence)
        val result = detector.detect(features, quality, 0f, 146.83f)

        assertNull(result.detectedTechnique)
        assertEquals("invalid-audio-quality", result.rejectionReason)
    }

    @Test
    fun ambiguousSignalReturnsUnknownWithBoundedConfidenceAndTimestampCanBeAttached() {
        val samples = sineFrame(220.0, 0.8)
        val result = detect(samples, frequencyHz = 220f, pitchConfidence = 0.3f)
            .copy(timestampNanos = 123_456L)

        assertNull(result.detectedTechnique)
        assertTrue(result.confidence in 0f..1f)
        assertEquals(123_456L, result.timestampNanos)
        assertNotNull(result.features)
    }

    private fun detect(
        samples: ShortArray,
        frequencyHz: Float,
        pitchConfidence: Float,
        onsetStrength: Float = 0.8f
    ) = run {
        val quality = AudioFrameQualityAnalyzer.analyze(
                samples = samples,
            sampleCount = samples.size,
            sampleRateHz = sampleRate,
            noiseFloorRms = 0.001f,
            captureTimestampNanos = 1_000L,
            analysisStartTimestampNanos = 2_000L,
            analysisEndTimestampNanos = 3_000L
        )
        val features = TechniqueFeatureExtractor.extract(
            buffer = samples,
            sampleCount = samples.size,
            rms = quality.rms,
            onsetStrength = onsetStrength,
            pitchConfidence = pitchConfidence,
            signalQuality = quality.signalConfidence
        )
        detector.detect(features, quality, frequencyHz, 146.83f)
    }

    private fun sineFrame(frequencyHz: Double, amplitude: Double): ShortArray = ShortArray(frameSize) { index ->
        (sin(2.0 * PI * frequencyHz * index / sampleRate) * amplitude * Short.MAX_VALUE).toInt().toShort()
    }

    private fun decayingTransient(): ShortArray = ShortArray(frameSize) { index ->
        val envelope = exp(-index / 140.0)
        (sin(2.0 * PI * 1_100.0 * index / sampleRate) * envelope * Short.MAX_VALUE).toInt().toShort()
    }
}
