package com.odysseus.app.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileUpload
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.odysseus.app.domain.model.CalendarEvent
import com.odysseus.app.domain.model.Session
import com.odysseus.app.domain.model.SessionType
import com.odysseus.app.domain.repository.CalendarEventRepository
import com.odysseus.app.domain.repository.SessionRepository
import com.odysseus.app.ui.theme.NothingRed
import com.odysseus.app.ui.theme.RunColor
import com.odysseus.app.ui.theme.StrengthColor
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun CalendarScreen(
    sessionRepository: SessionRepository,
    eventRepository: CalendarEventRepository,
    onDayClick: (LocalDate) -> Unit,
    viewModel: CalendarViewModel = viewModel(factory = CalendarViewModel.Factory(sessionRepository, eventRepository)),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var showSources by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    // SAF picker: no storage permission needed; works with Files, Drive, Downloads, email attachments.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.importIcs(context.contentResolver, it) }
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        MonthHeader(
            month = state.month,
            onPrevious = viewModel::previousMonth,
            onNext = viewModel::nextMonth,
            onToday = viewModel::today,
        )
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { picker.launch(arrayOf("text/calendar", "application/octet-stream", "*/*")) }) {
                Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("IMPORT .ICS", style = MaterialTheme.typography.labelMedium)
            }
            if (state.importedSources.isNotEmpty()) {
                TextButton(onClick = { showSources = true }) {
                    Text(
                        "${state.importedSources.size} IMPORTED",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        WeekdayRow()
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(bottom = 4.dp))
        MonthGrid(
            month = state.month,
            sessionsByDate = state.sessionsByDate,
            eventsByDate = state.eventsByDate,
            onDayClick = onDayClick,
        )
        SnackbarHost(snackbar)
    }

    if (showSources) {
        AlertDialog(
            onDismissRequest = { showSources = false },
            confirmButton = { TextButton(onClick = { showSources = false }) { Text("DONE") } },
            title = { Text("IMPORTED CALENDARS", style = MaterialTheme.typography.titleSmall) },
            text = {
                Column {
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
                }
            },
        )
    }
}

@Composable
private fun MonthHeader(month: YearMonth, onPrevious: () -> Unit, onNext: () -> Unit, onToday: () -> Unit) {
    val formatter = DateTimeFormatter.ofPattern("MMMM yyyy")
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month")
        }
        Text(
            text = month.format(formatter).uppercase(),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
        )
        TextButton(onClick = onToday) {
            Text("TODAY", style = MaterialTheme.typography.labelMedium, color = NothingRed)
        }
        IconButton(onClick = onNext) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month")
        }
    }
}

@Composable
private fun WeekdayRow() {
    Row(modifier = Modifier.fillMaxWidth()) {
        DayOfWeek.entries.forEach { day ->
            Text(
                text = day.getDisplayName(TextStyle.NARROW, Locale.getDefault()).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    sessionsByDate: Map<LocalDate, List<Session>>,
    eventsByDate: Map<LocalDate, List<CalendarEvent>>,
    onDayClick: (LocalDate) -> Unit,
) {
    val (start, end) = month.visibleRange()
    val days = generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }.toList()
    val today = LocalDate.now()

    LazyVerticalGrid(
        columns = GridCells.Fixed(7),
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(days, key = { it.toEpochDay() }) { date ->
            DayCell(
                date = date,
                inMonth = YearMonth.from(date) == month,
                isToday = date == today,
                sessions = sessionsByDate[date].orEmpty(),
                events = eventsByDate[date].orEmpty(),
                onClick = { onDayClick(date) },
            )
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    inMonth: Boolean,
    isToday: Boolean,
    sessions: List<Session>,
    events: List<CalendarEvent>,
    onClick: () -> Unit,
) {
    val textColor = when {
        !inMonth -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
        else -> MaterialTheme.colorScheme.onSurface
    }
    Column(
        modifier = Modifier
            .aspectRatio(0.85f)
            .clip(MaterialTheme.shapes.small)
            .then(if (isToday) Modifier.border(1.dp, NothingRed, MaterialTheme.shapes.small) else Modifier)
            .clickable(onClick = onClick)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = date.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            color = if (isToday) NothingRed else textColor,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.padding(top = 4.dp)) {
            sessions.map { it.type }.distinct().forEach { type ->
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (type == SessionType.RUN) RunColor else StrengthColor),
                )
            }
        }
        // Short summary so the month view is useful at a glance without opening the day.
        sessions.firstOrNull()?.let { s ->
            Text(
                text = s.summaryLabel(),
                style = MaterialTheme.typography.labelSmall,
                color = textColor,
                maxLines = 1,
            )
        }
        // Planned events: hairline-boxed label, visually secondary to what actually happened.
        events.firstOrNull()?.let { e ->
            Text(
                text = e.summary,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.extraSmall)
                    .padding(horizontal = 2.dp),
            )
        }
    }
}

/** e.g. "5.2k" for a run, "4 sets" for strength. */
fun Session.summaryLabel(): String = when (type) {
    SessionType.RUN -> distanceMeters?.let { String.format(Locale.getDefault(), "%.1fk", it / 1000) } ?: "run"
    SessionType.STRENGTH -> "${sets.size} sets"
}
