package com.sharn.handpan.audio

import com.sharn.handpan.model.NotePitchConfig
import com.sharn.handpan.model.DetectedStrikeEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class AudioDiagnosticState(
    val isListening: Boolean = false,
    val sessionId: String? = null,
    val latest: AudioDiagnosticSnapshot? = null,
    val history: List<AudioDiagnosticSnapshot> = emptyList(),
    val lastStrike: DetectedStrikeEvent? = null,
    val errorMessage: String? = null
)

class DiagnosticRingBuffer<T>(private val capacity: Int = DEFAULT_CAPACITY) {
    init {
        require(capacity > 0)
    }

    private val values = ArrayDeque<T>(capacity)

    @Synchronized
    fun add(value: T) {
        if (values.size == capacity) values.removeFirst()
        values.addLast(value)
    }

    @Synchronized
    fun clear() = values.clear()

    @Synchronized
    fun snapshot(): List<T> = values.toList()

    companion object {
        const val DEFAULT_CAPACITY = 100
    }
}

class AudioDiagnosticsController(
    private val analysisSession: AudioAnalysisSession,
    private val clock: PracticeClock = PracticeClock.Default,
    private val publishIntervalNanos: Long = 100_000_000L
) {
    private val _state = MutableStateFlow(AudioDiagnosticState())
    val state: StateFlow<AudioDiagnosticState> = _state.asStateFlow()
    private val history = DiagnosticRingBuffer<AudioDiagnosticSnapshot>()
    private var subscription: AudioAnalysisSession.Subscription? = null
    private var lastPublishedTimestampNanos = Long.MIN_VALUE

    @Synchronized
    fun start(scaleConfig: NotePitchConfig): Boolean {
        if (_state.value.isListening) return true
        val sessionId = "diagnostics-${clock.nowNanos()}"
        analysisSession.bindSessionId(sessionId)
        val candidate = analysisSession.acquire(
            scaleConfig = scaleConfig,
            onStrike = { event ->
                if (_state.value.sessionId == sessionId && _state.value.isListening) {
                    _state.value = _state.value.copy(lastStrike = event)
                }
            },
            sessionId = sessionId,
            onDiagnostic = { snapshot -> publish(sessionId, snapshot) }
        )
        subscription = candidate
        val active = candidate.isActive
        _state.value = AudioDiagnosticState(
            isListening = active,
            sessionId = if (active) sessionId else null,
            errorMessage = if (active) null else "Microphone session could not start"
        )
        lastPublishedTimestampNanos = Long.MIN_VALUE
        return active
    }

    @Synchronized
    fun stop() {
        subscription?.close()
        subscription = null
        _state.value = _state.value.copy(isListening = false, sessionId = null)
    }

    @Synchronized
    fun clearHistory() {
        history.clear()
        _state.value = _state.value.copy(history = emptyList(), latest = null, lastStrike = null)
    }

    fun exportJson(): String {
        val current = _state.value
        val root = JSONObject()
            .put("sessionId", current.sessionId)
            .put("isListening", current.isListening)
        val entries = JSONArray()
        current.history.forEach { snapshot ->
            entries.put(snapshot.toJson())
        }
        root.put("diagnostics", entries)
        return root.toString(2)
    }

    private fun publish(sessionId: String, snapshot: AudioDiagnosticSnapshot) {
        if (_state.value.sessionId != sessionId || !_state.value.isListening) return
        val lastTimestamp = lastPublishedTimestampNanos
        if (lastTimestamp != Long.MIN_VALUE &&
            snapshot.timestampNanos - lastTimestamp < publishIntervalNanos &&
            snapshot.detectedTechnique == null
        ) return
        lastPublishedTimestampNanos = snapshot.timestampNanos
        history.add(snapshot)
        _state.value = _state.value.copy(
            latest = snapshot,
            history = history.snapshot(),
            errorMessage = null
        )
    }

    private fun AudioDiagnosticSnapshot.toJson(): JSONObject = JSONObject()
        .put("timestampNanos", timestampNanos)
        .put("noteName", noteName)
        .put("centsOffset", centsOffset)
        .put("pitchHz", pitchHz)
        .put("pitchValid", pitchValid)
        .put("pitchConfidence", pitchConfidence)
        .put("onsetStrength", onsetStrength)
        .put("onsetSampleOffset", onsetSampleOffset)
        .put("detectedTechnique", detectedTechnique?.name ?: JSONObject.NULL)
        .put("techniqueConfidence", confidence)
        .put("transientToSustainRatio", transientToSustainRatio)
        .put("zeroCrossingRate", zeroCrossingRate)
        .put("rms", rms)
        .put("peak", peak)
        .put("sampleCount", sampleCount)
        .put("sampleRateHz", sampleRateHz)
        .put("clippingRatio", clippingRatio)
        .put("noiseFloorRms", noiseFloorRms)
        .put("signalToNoiseRatioDb", signalToNoiseRatioDb)
        .put("frameQuality", frameQuality?.name ?: JSONObject.NULL)
        .put("rejectionReason", rejectionReason ?: JSONObject.NULL)
}