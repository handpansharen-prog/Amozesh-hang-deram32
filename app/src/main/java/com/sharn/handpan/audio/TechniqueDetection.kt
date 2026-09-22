package com.sharn.handpan.audio

import com.sharn.handpan.model.AudioFrameQuality
import com.sharn.handpan.model.AudioFrameStatus
import com.sharn.handpan.model.HandpanTechnique
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class TechniqueAudioFeatures(
    val rms: Float,
    val peak: Float,
    val transientToSustainRatio: Float,
    val zeroCrossingRate: Float,
    val onsetStrength: Float,
    val pitchConfidence: Float,
    val signalQuality: Float
)

data class TechniqueDetectionResult(
    val detectedTechnique: HandpanTechnique?,
    val confidence: Float,
    val timestampNanos: Long = 0L,
    val features: TechniqueAudioFeatures,
    val rejectionReason: String? = null
) {
    init {
        require(confidence in 0f..1f)
        require(timestampNanos >= 0L)
    }
}

data class AudioDiagnosticSnapshot(
    val timestampNanos: Long,
    val rms: Float,
    val peak: Float,
    val onsetStrength: Float,
    val pitchHz: Float,
    val pitchConfidence: Float,
    val transientToSustainRatio: Float,
    val zeroCrossingRate: Float,
    val detectedTechnique: HandpanTechnique?,
    val confidence: Float,
    val rejectionReason: String?,
    val noteName: String = "--",
    val centsOffset: Int = 0,
    val pitchValid: Boolean = false,
    val onsetSampleOffset: Int = 0,
    val sampleCount: Int = 0,
    val sampleRateHz: Int = 0,
    val clippingRatio: Float = 0f,
    val noiseFloorRms: Float = 0f,
    val signalToNoiseRatioDb: Float = 0f,
    val frameQuality: AudioFrameStatus? = null
)

fun TechniqueDetectionResult.toDiagnosticSnapshot(
    timestampNanos: Long,
    pitchHz: Float,
    noteName: String = "--",
    centsOffset: Int = 0,
    pitchValid: Boolean = false,
    onsetSampleOffset: Int = 0,
    audioQuality: AudioFrameQuality? = null
) = AudioDiagnosticSnapshot(
    timestampNanos = timestampNanos,
    rms = features.rms,
    peak = features.peak,
    onsetStrength = features.onsetStrength,
    pitchHz = pitchHz,
    pitchConfidence = features.pitchConfidence,
    transientToSustainRatio = features.transientToSustainRatio,
    zeroCrossingRate = features.zeroCrossingRate,
    detectedTechnique = detectedTechnique,
    confidence = confidence,
    rejectionReason = rejectionReason,
    noteName = noteName,
    centsOffset = centsOffset,
    pitchValid = pitchValid,
    onsetSampleOffset = onsetSampleOffset,
    sampleCount = audioQuality?.sampleCount ?: 0,
    sampleRateHz = audioQuality?.sampleRateHz ?: 0,
    clippingRatio = audioQuality?.clippingRatio ?: 0f,
    noiseFloorRms = audioQuality?.noiseFloorRms ?: features.rms,
    signalToNoiseRatioDb = audioQuality?.signalToNoiseRatioDb ?: 0f,
    frameQuality = audioQuality?.status
)

object TechniqueFeatureExtractor {
    fun extract(
        buffer: ShortArray,
        sampleCount: Int,
        rms: Float,
        onsetStrength: Float,
        pitchConfidence: Float,
        signalQuality: Float
    ): TechniqueAudioFeatures {
        val count = sampleCount.coerceIn(0, buffer.size)
        if (count == 0) {
            return TechniqueAudioFeatures(0f, 0f, 0f, 0f, onsetStrength, pitchConfidence, signalQuality)
        }

        val quarter = max(1, count / 4)
        var peak = 0f
        var firstSumSquares = 0.0
        var lastSumSquares = 0.0
        var zeroCrossings = 0
        var previous = buffer[0]
        for (index in 0 until count) {
            val normalized = abs(buffer[index].toFloat() / Short.MAX_VALUE)
            peak = max(peak, normalized)
            if (index < quarter) firstSumSquares += normalized * normalized
            if (index >= count - quarter) lastSumSquares += normalized * normalized
            if (index > 0 && ((buffer[index] >= 0) != (previous >= 0))) zeroCrossings++
            previous = buffer[index]
        }
        val firstRms = sqrt(firstSumSquares / quarter).toFloat()
        val lastRms = sqrt(lastSumSquares / quarter).toFloat()
        val ratio = firstRms / max(lastRms, 0.0001f)
        return TechniqueAudioFeatures(
            rms = rms,
            peak = peak,
            transientToSustainRatio = ratio.coerceAtMost(100f),
            zeroCrossingRate = zeroCrossings.toFloat() / count,
            onsetStrength = onsetStrength.coerceIn(0f, 1f),
            pitchConfidence = pitchConfidence.coerceIn(0f, 1f),
            signalQuality = signalQuality.coerceIn(0f, 1f)
        )
    }
}

class RuleBasedTechniqueDetector(
    private val slapTransientRatio: Float = 2.5f,
    private val sustainedToneRatio: Float = 0.55f,
    private val dingFrequencyToleranceCents: Float = 70f
) {
    fun detect(
        features: TechniqueAudioFeatures,
        quality: AudioFrameQuality?,
        frequencyHz: Float,
        rootFrequencyHz: Float?
    ): TechniqueDetectionResult {
        if (quality == null || quality.status != com.sharn.handpan.model.AudioFrameStatus.VALID) {
            return unknown(features, "invalid-audio-quality")
        }
        if (features.rms <= 0f || features.signalQuality <= 0f) {
            return unknown(features, "insufficient-signal")
        }

        if (features.pitchConfidence < 0.5f &&
            features.transientToSustainRatio >= slapTransientRatio &&
            features.onsetStrength >= 0.2f
        ) {
            val confidence = min(
                1f,
                0.45f + (features.transientToSustainRatio / 10f) + features.signalQuality * 0.2f
            )
            return TechniqueDetectionResult(HandpanTechnique.SLAP, confidence, features = features)
        }

        if (frequencyHz > 0f && features.pitchConfidence >= 0.65f &&
            features.transientToSustainRatio <= 1f / sustainedToneRatio
        ) {
            val root = rootFrequencyHz
            if (root != null && centsDifference(frequencyHz, root) <= dingFrequencyToleranceCents) {
                val confidence = min(1f, 0.55f + features.pitchConfidence * 0.3f)
                return TechniqueDetectionResult(HandpanTechnique.DING, confidence, features = features)
            }
            val confidence = min(1f, 0.5f + features.pitchConfidence * 0.35f)
            return TechniqueDetectionResult(HandpanTechnique.TONE, confidence, features = features)
        }

        return unknown(features, "ambiguous-acoustic-features")
    }

    private fun unknown(features: TechniqueAudioFeatures, reason: String) =
        TechniqueDetectionResult(null, 0f, features = features, rejectionReason = reason)

    private fun centsDifference(actual: Float, expected: Float): Float {
        if (actual <= 0f || expected <= 0f) return Float.MAX_VALUE
        return abs(YinPitchDetector.calculateCentsDifference(actual, expected))
    }
}