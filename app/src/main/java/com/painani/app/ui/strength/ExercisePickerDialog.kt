package com.painani.app.ui.strength

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import com.painani.app.domain.model.ExerciseLibrary
import com.painani.app.ui.theme.NothingRed

/**
 * Searchable exercise list. The user's own exercises come first (they are what gets repeated),
 * then the bundled library. Typing a name that matches nothing offers to create it.
 */
@Composable
fun ExercisePickerDialog(
    ownExercises: List<String>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    val own = ownExercises.sorted()
    val library = ExerciseLibrary.defaults.filter { d -> own.none { it.equals(d, ignoreCase = true) } }
    val q = query.trim()
    fun matches(name: String) = q.isEmpty() || name.contains(q, ignoreCase = true)
    val ownHits = own.filter(::matches)
    val libraryHits = library.filter(::matches)
    val exactExists = (own + library).any { it.equals(q, ignoreCase = true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("EXERCISE", style = MaterialTheme.typography.titleSmall) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search or type a new one") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp).padding(top = 8.dp)) {
                    if (q.isNotEmpty() && !exactExists) {
                        item(key = "new") {
                            PickRow(text = "Create “$q”", accent = true) { onPick(q) }
                            HorizontalDivider()
                        }
                    }
                    if (ownHits.isNotEmpty()) {
                        item(key = "h-own") { SectionHeader("YOURS") }
                        items(ownHits, key = { "o$it" }) { PickRow(it) { onPick(it) } }
                    }
                    if (libraryHits.isNotEmpty()) {
                        item(key = "h-lib") { SectionHeader("LIBRARY") }
                        items(libraryHits, key = { "l$it" }) { PickRow(it) { onPick(it) } }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL") } },
    )
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
    )
}

@Composable
private fun PickRow(text: String, accent: Boolean = false, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = if (accent) NothingRed else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
    )
}
