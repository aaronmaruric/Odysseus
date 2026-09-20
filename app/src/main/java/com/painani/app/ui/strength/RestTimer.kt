package com.painani.app.ui.strength

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.painani.app.ui.calendar.formatDuration
import com.painani.app.ui.theme.NothingRed
import com.painani.app.ui.theme.Numeral
import kotlinx.coroutines.delay

/** Rest-between-sets countdown. Survives rotation; vibrates when it reaches zero. */
class RestTimerState(initialSeconds: Int) {
    var durationSeconds by androidx.compose.runtime.mutableIntStateOf(initialSeconds)
    /** Wall-clock end of the current rest, or 0 when idle. */
    var endsAtMillis by mutableLongStateOf(0L)
    var remainingMillis by mutableLongStateOf(0L)

    val isRunning get() = endsAtMillis > 0L

    fun start() { endsAtMillis = System.currentTimeMillis() + durationSeconds * 1000L; tick() }
    fun add(seconds: Int) { if (isRunning) { endsAtMillis += seconds * 1000L; tick() } }
    fun stop() { endsAtMillis = 0L; remainingMillis = 0L }
    fun tick() { remainingMillis = (endsAtMillis - System.currentTimeMillis()).coerceAtLeast(0L) }
}

@Composable
fun rememberRestTimerState(): RestTimerState {
    var duration by rememberSaveable { androidx.compose.runtime.mutableIntStateOf(90) }
    var endsAt by rememberSaveable { mutableLongStateOf(0L) }
    val state = remember { RestTimerState(duration).also { it.endsAtMillis = endsAt; it.tick() } }
    // Mirror back into the saveable holders so the timer survives rotation.
    LaunchedEffect(state.durationSeconds) { duration = state.durationSeconds }
    LaunchedEffect(state.endsAtMillis) { endsAt = state.endsAtMillis }
    return state
}

@Composable
fun RestTimer(state: RestTimerState, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    LaunchedEffect(state.endsAtMillis) {
        if (!state.isRunning) return@LaunchedEffect
        while (true) {
            state.tick()
            if (state.remainingMillis <= 0L) {
                vibrate(context)
                state.stop()
                break
            }
            delay(250)
        }
    }

    OutlinedCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "REST",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (state.isRunning) NothingRed else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (state.isRunning) {
                    TextButton(onClick = { state.add(30) }) { Text("+30", style = MaterialTheme.typography.labelMedium) }
                    TextButton(onClick = state::stop) { Text("SKIP", style = MaterialTheme.typography.labelMedium) }
                } else {
                    TextButton(onClick = state::start) { Text("START", style = MaterialTheme.typography.labelMedium, color = NothingRed) }
                }
            }
            Text(
                text = formatDuration(if (state.isRunning) state.remainingMillis else state.durationSeconds * 1000L),
                style = Numeral.copy(fontSize = 44.sp),
            )
            if (state.isRunning) {
                val total = state.durationSeconds * 1000f
                LinearProgressIndicator(
                    progress = { (state.remainingMillis / total).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    color = NothingRed,
                    trackColor = MaterialTheme.colorScheme.outlineVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                listOf(60, 90, 120, 180).forEach { s ->
                    FilterChip(
                        selected = state.durationSeconds == s,
                        onClick = { state.durationSeconds = s },
                        label = { Text("${s}s", style = MaterialTheme.typography.labelMedium) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    )
                }
            }
        }
    }
}

private fun vibrate(context: Context) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Vibrator::class.java)
    } ?: return
    // Three short buzzes: distinct from a notification, noticeable with the phone on a bench.
    vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 200, 120, 200, 120, 400), -1))
}
