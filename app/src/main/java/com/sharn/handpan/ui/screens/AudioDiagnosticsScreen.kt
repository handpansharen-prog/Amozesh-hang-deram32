package com.sharn.handpan.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sharn.handpan.ui.HandpanViewModel
import com.sharn.handpan.ui.theme.CharcoalBlack
import com.sharn.handpan.ui.theme.CharcoalSurface
import com.sharn.handpan.ui.theme.HandpanGold

@Composable
fun AudioDiagnosticsScreen(
    viewModel: HandpanViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val state by viewModel.audioDiagnostics.state.collectAsStateWithLifecycle()
    val snapshot = state.latest

    DisposableEffect(Unit) {
        onDispose { viewModel.stopAudioDiagnostics() }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CharcoalBlack)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("audio_diagnostics_screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("audio_diagnostics_back")) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "بازگشت", tint = Color.White)
            }
            Text("Audio Diagnostics (DEBUG)", color = Color.White, style = MaterialTheme.typography.titleMedium)
            Text("${state.history.size}/100", color = HandpanGold, fontSize = 12.sp)
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { viewModel.startAudioDiagnostics() },
                enabled = !state.isListening,
                modifier = Modifier.weight(1f).testTag("audio_diagnostics_start")
            ) {
                Icon(Icons.Default.Mic, contentDescription = null)
                Text("Start")
            }
            Button(
                onClick = { viewModel.stopAudioDiagnostics() },
                enabled = state.isListening,
                modifier = Modifier.weight(1f).testTag("audio_diagnostics_stop")
            ) {
                Icon(Icons.Default.Stop, contentDescription = null)
                Text("Stop")
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { viewModel.clearAudioDiagnostics() },
                modifier = Modifier.weight(1f).testTag("audio_diagnostics_clear")
            ) {
                Icon(Icons.Default.DeleteSweep, contentDescription = null)
                Text("Clear")
            }
            Button(
                onClick = { shareDiagnostics(context, viewModel.audioDiagnostics.exportJson()) },
                enabled = state.history.isNotEmpty(),
                modifier = Modifier.weight(1f).testTag("audio_diagnostics_export")
            ) {
                Icon(Icons.Default.FileUpload, contentDescription = null)
                Text("Export JSON")
            }
        }

        Text(
            text = when {
                state.isListening -> "Listening: ${state.sessionId ?: "active"}"
                state.errorMessage != null -> state.errorMessage!!
                else -> "Stopped"
            },
            color = if (state.isListening) Color(0xFF81C784) else Color.LightGray,
            fontSize = 12.sp
        )

        DiagnosticCard("Audio Quality") {
            Metric("RMS", snapshot?.rms?.format() ?: "N/A")
            Metric("Peak", snapshot?.peak?.format() ?: "N/A")
            Metric("Noise floor", snapshot?.noiseFloorRms?.format() ?: "N/A")
            Metric("SNR dB", snapshot?.signalToNoiseRatioDb?.format() ?: "N/A")
            Metric("Clipping", snapshot?.clippingRatio?.format() ?: "N/A")
            Metric("Frame", snapshot?.frameQuality?.name ?: "N/A")
            Metric("Sample rate / buffer", if (snapshot != null) "${snapshot.sampleRateHz} Hz / ${snapshot.sampleCount}" else "N/A")
        }

        DiagnosticCard("Onset / Pitch") {
            Metric("Onset", snapshot?.onsetStrength?.format() ?: "N/A")
            Metric("Sample offset", snapshot?.onsetSampleOffset?.toString() ?: "N/A")
            Metric("Pitch", if (snapshot != null) "${snapshot.noteName} ${snapshot.pitchHz.format()} Hz" else "N/A")
            Metric("Cents", snapshot?.centsOffset?.toString() ?: "N/A")
            Metric("Pitch confidence / valid", if (snapshot != null) "${snapshot.pitchConfidence.format()} / ${snapshot.pitchValid}" else "N/A")
            Metric("Timestamp", snapshot?.timestampNanos?.toString() ?: "N/A")
        }

        DiagnosticCard("Technique") {
            Metric("Detected", snapshot?.detectedTechnique?.name ?: "N/A")
            Metric("Confidence", snapshot?.confidence?.format() ?: "N/A")
            Metric("Transient / sustain", snapshot?.transientToSustainRatio?.format() ?: "N/A")
            Metric("Zero crossing", snapshot?.zeroCrossingRate?.format() ?: "N/A")
            Metric("Rejection", snapshot?.rejectionReason ?: "N/A")
        }

        DiagnosticCard("Assessment / Timing") {
            Metric("Expected note", "N/A (standalone diagnostics session)")
            Metric("Detected note", snapshot?.noteName ?: "N/A")
            Metric("Expected technique", "N/A")
            Metric("Detected technique", snapshot?.detectedTechnique?.name ?: "N/A")
            Metric("Timing", "N/A (no active assessment target)")
        }
    }
}

@Composable
private fun DiagnosticCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CharcoalSurface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(title, color = HandpanGold, style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color.LightGray, fontSize = 12.sp)
        Text(value, color = Color.White, fontSize = 12.sp)
    }
}

private fun Float.format(): String = "%.3f".format(this)

private fun shareDiagnostics(context: Context, json: String) {
    context.startActivity(
        Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_TEXT, json)
            },
            "Export audio diagnostics"
        )
    )
}