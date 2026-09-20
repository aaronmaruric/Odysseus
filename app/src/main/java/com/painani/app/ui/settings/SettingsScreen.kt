package com.painani.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import android.content.Intent
import android.net.Uri
import com.painani.app.data.health.HealthConnectManager
import com.painani.app.data.health.HealthStatus
import com.painani.app.data.health.HealthSync
import java.time.Duration
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.painani.app.domain.model.Gender
import com.painani.app.domain.model.UserProfile
import com.painani.app.domain.model.WeightEntry
import com.painani.app.domain.model.bmi
import com.painani.app.domain.repository.BodyStatsRepository
import com.painani.app.domain.repository.CalendarEventRepository
import com.painani.app.domain.repository.ProfileRepository
import com.painani.app.ui.theme.NothingRed
import com.painani.app.ui.theme.Numeral
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun SettingsScreen(
    profileRepository: ProfileRepository,
    bodyStatsRepository: BodyStatsRepository,
    eventRepository: CalendarEventRepository,
    healthConnect: HealthConnectManager,
    healthSync: HealthSync,
    viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.Factory(profileRepository, bodyStatsRepository, eventRepository, healthConnect, healthSync),
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val health by viewModel.healthState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    val healthPermissionLauncher = rememberLauncherForActivityResult(viewModel.healthPermissionContract) {
        viewModel.refreshHealth()
    }
    // Permissions can be changed in the system Health Connect screen; re-check whenever we come back.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshHealth() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.importIcs(context.contentResolver, it) }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("SETTINGS", style = MaterialTheme.typography.titleLarge)

        // Re-key the form on the loaded profile so it does not fight the first emission.
        if (state.loaded) {
            SectionLabel("PROFILE")
            ProfileForm(initial = state.profile, onSave = viewModel::saveProfile)
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        SectionLabel("BODY")
        WeightSection(
            weights = state.weights,
            heightCm = state.profile.heightCm,
            onLog = viewModel::logWeight,
            onDelete = viewModel::deleteWeight,
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        SectionLabel("HEALTH CONNECT")
        HealthSection(
            state = health,
            onConnect = { healthPermissionLauncher.launch(viewModel.healthPermissions) },
            onInstall = {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(HealthConnectManager.INSTALL_URL))) }
            },
            onSync = viewModel::syncHealthNow,
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        SectionLabel("CALENDARS")
        Text(
            "Import a training plan or race calendar. Re-importing a file replaces its events.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = { picker.launch(arrayOf("text/calendar", "application/octet-stream", "*/*")) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.FileUpload, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("IMPORT .ICS")
        }
        state.importedSources.forEach { source ->
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    source,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                IconButton(onClick = { viewModel.removeSource(source) }) {
                    Icon(Icons.Default.Close, contentDescription = "Remove $source")
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        SnackbarHost(snackbar)
    }
}

@Composable
private fun HealthSection(
    state: HealthUiState,
    onConnect: () -> Unit,
    onInstall: () -> Unit,
    onSync: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when {
            state.status == HealthStatus.UNSUPPORTED -> Text(
                "Health Connect is not supported on this device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            state.status != HealthStatus.AVAILABLE -> {
                Text(
                    if (state.status == HealthStatus.UPDATE_REQUIRED) "The Health Connect app needs an update."
                    else "Health Connect is not installed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = onInstall, modifier = Modifier.fillMaxWidth()) { Text("GET HEALTH CONNECT") }
            }
            !state.connected -> {
                Text(
                    "Pull heart rate, steps, sleep and weigh-ins from your watch or Samsung Health, and write your runs and workouts back.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = onConnect, modifier = Modifier.fillMaxWidth()) { Text("CONNECT") }
            }
            else -> {
                val r = state.readout
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Stat("STEPS", r?.steps?.let { "%,d".format(it) } ?: "--")
                    Stat("REST HR", r?.restingHr?.toString() ?: "--")
                    Stat("SLEEP", r?.sleepLastNight?.let { formatHours(it) } ?: "--")
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Connected", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            state.lastSync?.let { "Last sync " + it.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM HH:mm")) }
                                ?: "Runs and workouts sync automatically when saved",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (state.syncing) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = NothingRed, strokeWidth = 2.dp)
                    } else {
                        OutlinedButton(onClick = onSync) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("SYNC NOW")
                        }
                    }
                }
            }
        }
    }
}

private fun formatHours(d: Duration): String {
    val h = d.toHours()
    val m = d.minusHours(h).toMinutes()
    return "${h}h${"%02d".format(m)}"
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = NothingRed)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileForm(initial: UserProfile, onSave: (UserProfile) -> Unit) {
    var name by remember(initial) { mutableStateOf(initial.name) }
    var gender by remember(initial) { mutableStateOf(initial.gender) }
    var heightCm by remember(initial) { mutableStateOf(initial.heightCm?.let { fmt(it) } ?: "") }
    var birthDate by remember(initial) { mutableStateOf(initial.birthDate) }
    var showDatePicker by remember { mutableStateOf(false) }

    val dirty = name != initial.name || gender != initial.gender ||
        heightCm.toDoubleOrNull() != initial.heightCm || birthDate != initial.birthDate

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            val options = listOf(
                Gender.MALE to "M", Gender.FEMALE to "F", Gender.OTHER to "OTHER", Gender.UNSPECIFIED to "—",
            )
            options.forEachIndexed { i, (g, label) ->
                SegmentedButton(
                    selected = gender == g,
                    onClick = { gender = g },
                    shape = SegmentedButtonDefaults.itemShape(index = i, count = options.size),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = MaterialTheme.colorScheme.primary,
                        activeContentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) { Text(label, style = MaterialTheme.typography.labelMedium) }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = heightCm,
                onValueChange = { heightCm = it },
                label = { Text("Height (cm)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            OutlinedButton(
                onClick = { showDatePicker = true },
                modifier = Modifier.weight(1f).height(56.dp).padding(top = 8.dp),
            ) {
                Text(
                    birthDate?.format(DateTimeFormatter.ofPattern("d MMM yyyy")) ?: "Birth date",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        initial.age()?.let { age ->
            Text("Age $age", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Button(
            onClick = {
                onSave(
                    UserProfile(
                        name = name.trim(),
                        gender = gender,
                        heightCm = heightCm.toDoubleOrNull()?.takeIf { it > 0 },
                        birthDate = birthDate,
                    )
                )
            },
            enabled = dirty,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("SAVE PROFILE") }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = (birthDate ?: LocalDate.of(1990, 1, 1))
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        birthDate = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("CANCEL") } },
        ) { DatePicker(state = pickerState) }
    }
}

@Composable
private fun WeightSection(
    weights: List<WeightEntry>,
    heightCm: Double?,
    onLog: (Double, String) -> Unit,
    onDelete: (Long) -> Unit,
) {
    var weightText by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val latest = weights.firstOrNull()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Stat("KG", latest?.let { fmt(it.weightKg) } ?: "--")
            Stat("BMI", bmi(latest?.weightKg, heightCm)?.let { String.format(Locale.getDefault(), "%.1f", it) } ?: "--")
            Stat("CHANGE", trend(weights))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = weightText,
                onValueChange = { weightText = it },
                label = { Text("Weight (kg)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Note") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
        }
        Button(
            onClick = {
                weightText.toDoubleOrNull()?.takeIf { it > 0 }?.let {
                    onLog(it, note)
                    weightText = ""; note = ""
                }
            },
            enabled = weightText.toDoubleOrNull()?.let { it > 0 } == true,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("LOG WEIGHT") }

        if (weights.isNotEmpty()) {
            val fmt = DateTimeFormatter.ofPattern("EEE d MMM")
            weights.take(10).forEach { w ->
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        w.at.atZone(ZoneId.systemDefault()).format(fmt).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Text("${fmt(w.weightKg)} kg", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(
                        w.note,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1.5f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    IconButton(onClick = { onDelete(w.id) }) {
                        Icon(Icons.Default.Close, contentDescription = "Delete entry")
                    }
                }
            }
            if (weights.size > 10) {
                Text(
                    "+${weights.size - 10} older",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = Numeral.copy(fontSize = MaterialTheme.typography.headlineMedium.fontSize))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Difference between the latest weigh-in and the one before it. */
private fun trend(weights: List<WeightEntry>): String {
    if (weights.size < 2) return "--"
    val d = weights[0].weightKg - weights[1].weightKg
    return String.format(Locale.getDefault(), "%+.1f", d)
}

private fun fmt(v: Double): String =
    if (v % 1.0 == 0.0) v.toInt().toString() else String.format(Locale.getDefault(), "%.1f", v)
