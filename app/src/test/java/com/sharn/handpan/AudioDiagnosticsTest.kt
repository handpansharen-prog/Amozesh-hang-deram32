package com.sharn.handpan

import com.sharn.handpan.audio.AudioAnalysisSession
import com.sharn.handpan.audio.AudioDiagnosticSnapshot
import com.sharn.handpan.audio.AudioDiagnosticsController
import com.sharn.handpan.audio.DiagnosticRingBuffer
import com.sharn.handpan.audio.DetectedPitchResult
import com.sharn.handpan.audio.PracticeClock
import com.sharn.handpan.model.DetectedStrikeEvent
import com.sharn.handpan.model.NotePitchConfig
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private class DiagnosticClock : PracticeClock {
    var now = 1_000_000_000L
    override fun nowNanos(): Long = now
}

private class CapturingDiagnosticSession : AudioAnalysisSession() {
    var diagnostic: ((AudioDiagnosticSnapshot) -> Unit)? = null
    var strike: ((DetectedStrikeEvent) -> Unit)? = null
    var acquires = 0

    override fun acquire(
        scaleConfig: NotePitchConfig,
        onStrike: (DetectedStrikeEvent) -> Unit,
        onPitch: (DetectedPitchResult) -> Unit,
        sessionId: String,
        onDiagnostic: (AudioDiagnosticSnapshot) -> Unit
    ): Subscription {
        acquires++
        strike = onStrike
        diagnostic = onDiagnostic
        return Subscription({ }, isActive = true)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AudioDiagnosticsTest {
    @Test
    fun ringBufferDropsOldestEntryAndPreservesOrder() {
        val buffer = DiagnosticRingBuffer<Int>(capacity = 3)
        buffer.add(1)
        buffer.add(2)
        buffer.add(3)
        buffer.add(4)

        assertEquals(listOf(2, 3, 4), buffer.snapshot())
    }

    @Test
    fun startStopStartAndStaleCallbacksAreSessionSafe() {
        val session = CapturingDiagnosticSession()
        val clock = DiagnosticClock()
        val controller = AudioDiagnosticsController(
            analysisSession = session,
            clock = clock,
            publishIntervalNanos = 0L
        )
        val snapshot = AudioDiagnosticSnapshot(
            timestampNanos = 2_000_000_000L,
            rms = 0.2f,
            peak = 0.5f,
            onsetStrength = 0.8f,
            pitchHz = 146.8f,
            pitchConfidence = 0.9f,
            transientToSustainRatio = 2f,
            zeroCrossingRate = 0.1f,
            detectedTechnique = null,
            confidence = 0f,
            rejectionReason = "no-onset"
        )

        assertTrue(controller.start(NotePitchConfig.D_KURD_9))
        assertTrue(controller.state.value.isListening)
        session.diagnostic?.invoke(snapshot)
        assertEquals(1, controller.state.value.history.size)

        controller.stop()
        assertFalse(controller.state.value.isListening)
        session.diagnostic?.invoke(snapshot.copy(timestampNanos = 3_000_000_000L))
        assertEquals(1, controller.state.value.history.size)

        clock.now += 1_000_000L
        assertTrue(controller.start(NotePitchConfig.D_KURD_9))
        assertEquals(2, session.acquires)
        session.diagnostic?.invoke(snapshot.copy(timestampNanos = 4_000_000_000L))
        assertEquals(2, controller.state.value.history.size)
    }

    @Test
    fun clearHistoryRemovesSnapshotsAndExportContainsRealFields() {
        val session = CapturingDiagnosticSession()
        val controller = AudioDiagnosticsController(session, DiagnosticClock(), 0L)
        controller.start(NotePitchConfig.D_KURD_9)
        session.diagnostic?.invoke(
            AudioDiagnosticSnapshot(
                timestampNanos = 10L,
                rms = 0.1f,
                peak = 0.2f,
                onsetStrength = 0.3f,
                pitchHz = 100f,
                pitchConfidence = 0.4f,
                transientToSustainRatio = 1.5f,
                zeroCrossingRate = 0.05f,
                detectedTechnique = null,
                confidence = 0f,
                rejectionReason = "invalid-audio-quality"
            )
        )

        val json = controller.exportJson()
        val exported = JSONObject(json).getJSONArray("diagnostics").getJSONObject(0)
        assertEquals(0.1, exported.getDouble("rms"), 0.0001)
        assertEquals("invalid-audio-quality", exported.getString("rejectionReason"))

        controller.clearHistory()
        assertTrue(controller.state.value.history.isEmpty())
        assertEquals(null, controller.state.value.latest)
    }
}