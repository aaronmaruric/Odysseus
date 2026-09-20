package com.odysseus.app.ui.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.odysseus.app.domain.model.CalendarEvent
import com.odysseus.app.domain.model.Session
import com.odysseus.app.domain.model.SessionType
import com.odysseus.app.domain.repository.CalendarEventRepository
import com.odysseus.app.domain.repository.SessionRepository
import com.odysseus.app.ui.theme.NothingRed
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Shows every session on a given day with its splits or sets laid out in full. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayDetailScreen(
    date: LocalDate,
    repository: SessionRepository,
    eventRepository: CalendarEventRepository,
    onBack: () -> Unit,
) {
    val sessionFlow = remember(date) { repository.sessionsOn(date) }
    val eventFlow = remember(date) { eventRepository.eventsOn(date) }
    val sessions by sessionFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val events by eventFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        date.format(DateTimeFormatter.ofPattern("EEE d MMM yyyy")).uppercase(),
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (sessions.isEmpty() && events.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("REST DAY", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(events, key = { "e${it.id}" }) { event ->
                EventCard(event)
            }
            items(sessions, key = { "s${it.id}" }) { session ->
                SessionCard(session)
            }
        }
    }
}

@Composable
private fun EventCard(event: CalendarEvent) {
    val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
    val zone = ZoneId.systemDefault()
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("PLANNED", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = if (event.allDay) "ALL DAY"
                    else event.start.atZone(zone).format(timeFmt) + " – " + event.end.atZone(zone).format(timeFmt),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(event.summary, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
            if (event.location.isNotBlank()) {
                Text(event.location, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (event.description.isNotBlank()) {
                Text(event.description, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

@Composable
private fun SessionCard(session: Session) {
    val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = if (session.type == SessionType.RUN) "RUN" else "STRENGTH",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (session.type == SessionType.RUN) NothingRed else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = session.startedAt.atZone(ZoneId.systemDefault()).format(timeFmt),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(
                text = "Duration ${formatDuration(session.durationMillis)}" +
                    (session.distanceMeters?.let { " · %.2f km".format(Locale.getDefault(), it / 1000) } ?: ""),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (session.notes.isNotBlank()) {
                Text(session.notes, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            }

            when (session.type) {
                SessionType.RUN -> SplitsTable(session)
                SessionType.STRENGTH -> SetsTable(session)
            }
        }
    }
}

@Composable
private fun SplitsTable(session: Session) {
    if (session.splits.isEmpty()) return
    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
    TableHeader("SPLIT", "KM", "TIME", "PACE")
    session.splits.forEach { split ->
        TableRow(
            "${split.index + 1}",
            "%.2f".format(Locale.getDefault(), split.distanceMeters / 1000),
            formatDuration(split.durationMillis),
            formatPace(split.paceSecPerKm),
        )
    }
}

@Composable
private fun SetsTable(session: Session) {
    if (session.sets.isEmpty()) return
    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
    session.sets.groupBy { it.exercise.name }.forEach { (name, sets) ->
        Text(name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
        sets.forEach { set ->
            TableRow(
                "Set ${set.setIndex + 1}",
                "${set.reps} reps",
                "%.1f kg".format(Locale.getDefault(), set.weightKg),
                "",
            )
        }
    }
}

@Composable
private fun TableHeader(vararg cells: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        cells.forEach {
            Text(
                it,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun TableRow(vararg cells: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        cells.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f)) }
    }
}

fun formatDuration(millis: Long): String {
    val totalSec = millis / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

fun formatPace(secPerKm: Double): String {
    if (secPerKm <= 0) return "--"
    val m = (secPerKm / 60).toInt()
    val s = (secPerKm % 60).toInt()
    return "%d:%02d /km".format(m, s)
}
