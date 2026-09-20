package com.odysseus.app.ui.strength

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.odysseus.app.domain.model.Exercise
import com.odysseus.app.domain.model.ExerciseSet
import com.odysseus.app.domain.model.Session
import com.odysseus.app.domain.model.SessionType
import com.odysseus.app.domain.repository.SessionRepository
import java.time.Instant
import kotlinx.coroutines.launch

/** One row of the in-progress workout form. Text fields so the user can type freely; parsed on save. */
private data class SetDraft(
    val exercise: String = "",
    val reps: String = "",
    val weightKg: String = "",
)

/**
 * Strength workout logger, modelled on the Flexify data shape (exercise -> sets of reps x weight).
 *
 * TODO(strength): exercise picker backed by [SessionRepository.exercises], rest timer, and
 *   per-exercise progress charts (see Flexify for the reference UX).
 */
@Composable
fun StrengthScreen(repository: SessionRepository) {
    val drafts = remember { mutableStateListOf(SetDraft()) }
    var notes by remember { mutableStateOf("") }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Log a workout", style = MaterialTheme.typography.headlineSmall)

        LazyColumn(
            modifier = Modifier.weight(1f).padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(drafts) { index, draft ->
                SetRow(
                    index = index,
                    draft = draft,
                    onChange = { drafts[index] = it },
                    onRemove = { if (drafts.size > 1) drafts.removeAt(index) },
                )
            }
            item {
                OutlinedButton(
                    onClick = {
                        // Pre-fill with the previous exercise name; most sets repeat the same lift.
                        drafts.add(SetDraft(exercise = drafts.lastOrNull()?.exercise.orEmpty()))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Add set") }
            }
            item {
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Button(
            onClick = {
                val parsed = drafts.mapIndexedNotNull { i, d -> d.toSet(i) }
                if (parsed.isEmpty()) {
                    scope.launch { snackbar.showSnackbar("Add at least one complete set") }
                    return@Button
                }
                scope.launch {
                    // Resolve exercise ids: create any exercise names we have not seen before.
                    val resolved = parsed.map { set ->
                        val id = repository.saveExercise(set.exercise)
                        set.copy(exercise = set.exercise.copy(id = id))
                    }
                    repository.save(
                        Session(
                            type = SessionType.STRENGTH,
                            startedAt = Instant.now(),
                            durationMillis = 0,
                            notes = notes.trim(),
                            sets = resolved,
                        )
                    )
                    drafts.clear(); drafts.add(SetDraft()); notes = ""
                    snackbar.showSnackbar("Workout saved")
                }
            },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) { Text("Save workout") }
        SnackbarHost(snackbar)
    }
}

@Composable
private fun SetRow(index: Int, draft: SetDraft, onChange: (SetDraft) -> Unit, onRemove: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = draft.exercise,
            onValueChange = { onChange(draft.copy(exercise = it)) },
            label = { Text("Exercise") },
            modifier = Modifier.weight(2f),
            singleLine = true,
        )
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
        IconButton(onClick = onRemove) {
            Icon(Icons.Default.Delete, contentDescription = "Remove set $index")
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
