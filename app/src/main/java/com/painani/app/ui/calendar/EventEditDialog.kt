package com.painani.app.ui.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.painani.app.domain.model.CalendarEvent
import com.painani.app.ui.theme.NothingRed
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Edit an imported event in place. Times are edited in the device zone. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventEditDialog(
    event: CalendarEvent,
    onSave: (CalendarEvent) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val zone = ZoneId.systemDefault()
    var summary by remember { mutableStateOf(event.summary) }
    var location by remember { mutableStateOf(event.location) }
    var description by remember { mutableStateOf(event.description) }
    var allDay by remember { mutableStateOf(event.allDay) }
    var date by remember { mutableStateOf(event.start.atZone(zone).toLocalDate()) }
    var startTime by remember { mutableStateOf(event.start.atZone(zone).toLocalTime()) }
    var endTime by remember { mutableStateOf(event.end.atZone(zone).toLocalTime()) }

    var pickDate by remember { mutableStateOf(false) }
    var pickStart by remember { mutableStateOf(false) }
    var pickEnd by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("EDIT EVENT", style = MaterialTheme.typography.titleSmall) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = summary,
                    onValueChange = { summary = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(onClick = { pickDate = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(date.format(DateTimeFormatter.ofPattern("EEE d MMM yyyy")))
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("All day", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(checked = allDay, onCheckedChange = { allDay = it })
                }
                if (!allDay) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { pickStart = true }, modifier = Modifier.weight(1f)) {
                            Text("From " + startTime.format(timeFmt))
                        }
                        OutlinedButton(onClick = { pickEnd = true }, modifier = Modifier.weight(1f)) {
                            Text("To " + endTime.format(timeFmt))
                        }
                    }
                }
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("Location") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Notes") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Re-importing ${event.source} will overwrite this edit.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = summary.isNotBlank(),
                onClick = {
                    val (start, end) = if (allDay) {
                        val s = date.atStartOfDay(zone).toInstant()
                        s to s.plusSeconds(86_400)
                    } else {
                        val s = date.atTime(startTime).atZone(zone).toInstant()
                        var e = date.atTime(endTime).atZone(zone).toInstant()
                        // An end before the start means it crosses midnight.
                        if (e <= s) e = e.plusSeconds(86_400)
                        s to e
                    }
                    onSave(
                        event.copy(
                            summary = summary.trim(),
                            location = location.trim(),
                            description = description.trim(),
                            allDay = allDay,
                            start = start,
                            end = end,
                        )
                    )
                },
            ) { Text("SAVE") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { confirmDelete = true }) { Text("DELETE", color = NothingRed) }
                TextButton(onClick = onDismiss) { Text("CANCEL") }
            }
        },
    )

    if (pickDate) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    pickDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("CANCEL") } },
        ) { DatePicker(state = state) }
    }

    if (pickStart) TimeDialog(startTime, onPick = { startTime = it; pickStart = false }, onDismiss = { pickStart = false })
    if (pickEnd) TimeDialog(endTime, onPick = { endTime = it; pickEnd = false }, onDismiss = { pickEnd = false })

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("DELETE EVENT?", style = MaterialTheme.typography.titleSmall) },
            text = { Text("Removes this occurrence only. Other occurrences of a repeating event stay.") },
            confirmButton = { TextButton(onClick = onDelete) { Text("DELETE", color = NothingRed) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("CANCEL") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(initial: LocalTime, onPick: (LocalTime) -> Unit, onDismiss: () -> Unit) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { TimePicker(state = state) },
        confirmButton = { TextButton(onClick = { onPick(LocalTime.of(state.hour, state.minute)) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL") } },
    )
}
