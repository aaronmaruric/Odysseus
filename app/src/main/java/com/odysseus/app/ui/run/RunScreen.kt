package com.odysseus.app.ui.run

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.odysseus.app.domain.model.Session
import com.odysseus.app.domain.model.SessionType
import com.odysseus.app.domain.model.Split
import com.odysseus.app.domain.repository.SessionRepository
import java.time.Instant
import kotlinx.coroutines.launch

/**
 * Manual run entry. This is a placeholder until the GPS tracker is ported from RunnerUp;
 * it lets you log a run with even splits so the calendar and detail screens have real data.
 *
 * TODO(tracking): replace with a live tracking UI bound to [com.odysseus.app.tracking.RunTracker].
 */
@Composable
fun RunScreen(repository: SessionRepository) {
    var distanceKm by remember { mutableStateOf("") }
    var minutes by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("LOG A RUN", style = MaterialTheme.typography.titleLarge)
        Text(
            "GPS tracking is not wired up yet. Enter the run manually and it will appear on the calendar " +
                "with evenly computed kilometre splits.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = distanceKm,
                onValueChange = { distanceKm = it },
                label = { Text("Distance (km)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            OutlinedTextField(
                value = minutes,
                onValueChange = { minutes = it },
                label = { Text("Time (min)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
        }
        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text("Notes") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
        )
        Button(
            onClick = {
                val km = distanceKm.toDoubleOrNull()
                val min = minutes.toDoubleOrNull()
                if (km == null || min == null || km <= 0 || min <= 0) {
                    scope.launch { snackbar.showSnackbar("Enter a distance and time") }
                    return@Button
                }
                val session = buildManualRun(km, min, notes)
                scope.launch {
                    repository.save(session)
                    distanceKm = ""; minutes = ""; notes = ""
                    snackbar.showSnackbar("Run saved")
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Save run") }
        SnackbarHost(snackbar)
    }
}

/** Builds a run session ending now, with whole-km splits at an even pace plus a partial final split. */
internal fun buildManualRun(distanceKm: Double, minutes: Double, notes: String): Session {
    val durationMillis = (minutes * 60_000).toLong()
    val distanceMeters = distanceKm * 1000
    val msPerMeter = durationMillis / distanceMeters

    val splits = buildList {
        var covered = 0.0
        var index = 0
        while (covered < distanceMeters - 1e-6) {
            val segment = minOf(1000.0, distanceMeters - covered)
            add(Split(index = index++, distanceMeters = segment, durationMillis = (segment * msPerMeter).toLong()))
            covered += segment
        }
    }

    return Session(
        type = SessionType.RUN,
        startedAt = Instant.now().minusMillis(durationMillis),
        durationMillis = durationMillis,
        notes = notes.trim(),
        distanceMeters = distanceMeters,
        splits = splits,
    )
}
