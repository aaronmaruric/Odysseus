package com.painani.app.ui.strength

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.painani.app.domain.model.Exercise
import com.painani.app.domain.model.ExerciseSet
import com.painani.app.domain.model.Session
import com.painani.app.domain.model.SessionType
import com.painani.app.domain.repository.SessionRepository
import com.painani.app.ui.theme.NothingRed
import java.time.Instant
import kotlinx.coroutines.launch

/** One row of the in-progress workout form. Text fields so the user can type freely; parsed on save. */
private data class SetDraft(
    val exercise: String = "",
    val reps: String = "",
    val weightKg: String = "",
    /** Ticked when the set has been performed; ticking starts the rest timer. */
    val done: Boolean = false,
)

/**
 * Strength workout logger, modelled on the Flexify flow: pick an exercise, log sets as you do
 * them (each tick starts the rest timer), save the workout at the end.
 */
@Composable
fun StrengthScreen(repository: SessionRepository) {
    val drafts = remember { mutableStateListOf(SetDraft()) }
    var notes by remember { mutableStateOf("") }
    var pickingFor by remember { mutableIntStateOf(-1) } // index of the row whose exercise is being chosen
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val rest = rememberRestTimerState()
    var startedAt by remember { mutableStateOf<Instant?>(null) }

    val exerciseFlow = remember { repository.exercises() }
    val ownExercises by exerciseFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    if (pickingFor >= 0) {
        ExercisePickerDialog(
            ownExercises = ownExercises.map { it.name },
            onPick = { name ->
                drafts.getOrNull(pickingFor)?.let { drafts[pickingFor] = it.copy(exercise = name) }
                pickingFor = -1
            },
            onDismiss = { pickingFor = -1 },
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("LOG A WORKOUT", style = MaterialTheme.typography.titleLarge)

        RestTimer(state = rest, modifier = Modifier.padding(top = 12.dp))

        LazyColumn(
            modifier = Modifier.weight(1f).padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(drafts) { index, draft ->
                SetRow(
                    index = index,
                    draft = draft,
                    onPickExercise = { pickingFor = index },
                    onChange = { drafts[index] = it },
                    onDone = {
                        if (startedAt == null) startedAt = Instant.now()
                        drafts[index] = draft.copy(done = !draft.done)
                        if (!draft.done) rest.start()
                    },
                    onRemove = { if (drafts.size > 1) drafts.removeAt(index) },
                )
            }
            item {
                OutlinedButton(
                    onClick = {
                        // Pre-fill with the previous row: most sets repeat the same lift and load.
                        val last = drafts.lastOrNull()
                        drafts.add(SetDraft(exercise = last?.exercise.orEmpty(), reps = last?.reps.orEmpty(), weightKg = last?.weightKg.orEmpty()))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("ADD SET") }
            }
            item {
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item { Spacer(Modifier.height(4.dp)) }
        }

        Button(
            onClick = {
                val parsed = drafts.mapIndexedNotNull { i, d -> d.toSet(i) }
                if (parsed.isEmpty()) {
                    scope.launch { snackbar.showSnackbar("Add at least one complete set") }
                    return@Button
                }
                val began = startedAt ?: Instant.now()
                scope.launch {
                    // Resolve exercise ids: create any exercise names we have not seen before.
                    val resolved = parsed.map { set ->
                        val id = repository.saveExercise(set.exercise)
                        set.copy(exercise = set.exercise.copy(id = id))
                    }
                    repository.save(
                        Session(
                            type = SessionType.STRENGTH,
                            startedAt = began,
                            durationMillis = (Instant.now().toEpochMilli() - began.toEpochMilli()).coerceAtLeast(0),
                            notes = notes.trim(),
                            sets = resolved,
                        )
                    )
                    drafts.clear(); drafts.add(SetDraft()); notes = ""; startedAt = null
                    rest.stop()
                    snackbar.showSnackbar("Workout saved")
                }
            },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) { Text("SAVE WORKOUT") }
        SnackbarHost(snackbar)
    }
}

@Composable
private fun SetRow(
    index: Int,
    draft: SetDraft,
    onPickExercise: () -> Unit,
    onChange: (SetDraft) -> Unit,
    onDone: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedButton(
            onClick = onPickExercise,
            modifier = Modifier.weight(2f).height(56.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp),
        ) {
            Text(
                text = draft.exercise.ifBlank { "Exercise" },
                style = MaterialTheme.typography.bodyMedium,
                color = if (draft.exercise.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        OutlinedTextField(
            value = draft.reps,
            onValueChange = { onChange(draft.copy(reps = it)) },
            label = { Text("Reps") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
            singleLine = true,
        )
        OutlinedTextField(
            value = draft.weightKg,
            onValueChange = { onChange(draft.copy(weightKg = it)) },
            label = { Text("kg") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f),
            singleLine = true,
        )
        IconButton(
            onClick = onDone,
            colors = IconButtonDefaults.iconButtonColors(
                contentColor = if (draft.done) MaterialTheme.colorScheme.onPrimary else NothingRed,
                containerColor = if (draft.done) NothingRed else androidx.compose.ui.graphics.Color.Transparent,
            ),
        ) {
            Icon(Icons.Default.Check, contentDescription = if (draft.done) "Set ${index + 1} done" else "Mark set ${index + 1} done")
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Default.Close, contentDescription = "Remove set ${index + 1}")
        }
    }
}

private fun SetDraft.toSet(index: Int): ExerciseSet? {
    val name = exercise.trim()
    val r = reps.toIntOrNull()
    val w = weightKg.toDoubleOrNull() ?: 0.0
    if (name.isEmpty() || r == null || r <= 0) return null
    return ExerciseSet(exercise = Exercise(name = name), setIndex = index, reps = r, weightKg = w)
}
