package com.painani.app.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.painani.app.domain.model.CalendarEvent
import com.painani.app.domain.model.Session
import com.painani.app.domain.model.SessionType
import com.painani.app.domain.repository.CalendarEventRepository
import com.painani.app.domain.repository.SessionRepository
import com.painani.app.ui.theme.NothingRed
import com.painani.app.ui.theme.RunColor
import com.painani.app.ui.theme.StrengthColor
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
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

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        ModeSelector(mode = state.mode, onSelect = viewModel::setMode)
        Header(
            title = state.title(),
            onPrevious = viewModel::previous,
            onNext = viewModel::next,
            onToday = viewModel::today,
        )
        when (state.mode) {
            CalendarMode.MONTH -> {
                WeekdayRow()
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(bottom = 4.dp))
                MonthGrid(
                    month = YearMonth.from(state.anchor),
                    sessionsByDate = state.sessionsByDate,
                    eventsByDate = state.eventsByDate,
                    onDayClick = onDayClick,
                )
            }
            CalendarMode.WEEK -> WeekView(
                start = state.rangeStart,
                sessionsByDate = state.sessionsByDate,
                eventsByDate = state.eventsByDate,
                onDayClick = onDayClick,
            )
            CalendarMode.DAY -> DayContent(
                sessions = state.sessionsByDate[state.anchor].orEmpty(),
                events = state.eventsByDate[state.anchor].orEmpty(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
                eventActions = EventActions(onSave = viewModel::saveEvent, onDelete = viewModel::deleteEvent),
            )
            CalendarMode.LIST -> AgendaList(
                from = state.rangeStart,
                to = state.rangeEnd,
                sessionsByDate = state.sessionsByDate,
                eventsByDate = state.eventsByDate,
                onDayClick = onDayClick,
            )
        }
    }
}

private fun CalendarUiState.title(): String = when (mode) {
    CalendarMode.DAY -> anchor.format(DateTimeFormatter.ofPattern("EEE d MMM yyyy"))
    CalendarMode.WEEK -> {
        val f = DateTimeFormatter.ofPattern("d MMM")
        "${rangeStart.format(f)} – ${rangeEnd.format(f)}"
    }
    CalendarMode.MONTH, CalendarMode.LIST -> YearMonth.from(anchor).format(DateTimeFormatter.ofPattern("MMMM yyyy"))
}.uppercase()

// --- chrome ---------------------------------------------------------------------------

@Composable
private fun ModeSelector(mode: CalendarMode, onSelect: (CalendarMode) -> Unit) {
    val modes = CalendarMode.entries
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        modes.forEachIndexed { i, m ->
            SegmentedButton(
                selected = mode == m,
                onClick = { onSelect(m) },
                shape = SegmentedButtonDefaults.itemShape(index = i, count = modes.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = MaterialTheme.colorScheme.primary,
                    activeContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                icon = {},
            ) { Text(m.name, style = MaterialTheme.typography.labelMedium) }
        }
    }
}

@Composable
private fun Header(title: String, onPrevious: () -> Unit, onNext: () -> Unit, onToday: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous")
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        TextButton(onClick = onToday) {
            Text("TODAY", style = MaterialTheme.typography.labelMedium, color = NothingRed)
        }
        IconButton(onClick = onNext) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next")
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

// --- month ----------------------------------------------------------------------------

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
    val textColor = if (inMonth) MaterialTheme.colorScheme.onSurface
    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
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
        TypeDots(sessions)
        // Short summary so the month view is useful at a glance without opening the day.
        sessions.firstOrNull()?.let { s ->
            Text(text = s.summaryLabel(), style = MaterialTheme.typography.labelSmall, color = textColor, maxLines = 1)
        }
        events.firstOrNull()?.let { e -> EventChip(e.summary) }
    }
}

@Composable
private fun TypeDots(sessions: List<Session>) {
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
}

/** Planned events: hairline-boxed label, visually secondary to what actually happened. */
@Composable
private fun EventChip(text: String) {
    Text(
        text = text,
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

// --- week -----------------------------------------------------------------------------

/**
 * Seven day sections stacked vertically. Each is at least a seventh of the screen so an empty
 * week still fills it, and grows when a day has more to show. Detail sits between the month
 * grid (dots) and the day view (full cards): one line per item with its headline numbers.
 */
@Composable
private fun WeekView(
    start: LocalDate,
    sessionsByDate: Map<LocalDate, List<Session>>,
    eventsByDate: Map<LocalDate, List<CalendarEvent>>,
    onDayClick: (LocalDate) -> Unit,
) {
    val today = LocalDate.now()
    val zone = ZoneId.systemDefault()
    val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val minSection = maxHeight / 7
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            (0..6).forEach { offset ->
                val date = start.plusDays(offset.toLong())
                val isToday = date == today
                val events = eventsByDate[date].orEmpty()
                val sessions = sessionsByDate[date].orEmpty()

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = minSection)
                        .clickable { onDayClick(date) }
                        .padding(vertical = 6.dp),
                ) {
                    // Date gutter: weekday initial over the day number, red for today.
                    Column(
                        modifier = Modifier.width(44.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isToday) NothingRed else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = date.dayOfMonth.toString(),
                            style = MaterialTheme.typography.titleLarge,
                            color = if (isToday) NothingRed else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (events.isEmpty() && sessions.isEmpty()) {
                            Text(
                                "REST",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                        events.forEach { e ->
                            WeekLine(
                                time = if (e.allDay) "ALL DAY" else e.start.atZone(zone).format(timeFmt),
                                label = e.summary,
                                detail = e.location,
                                accent = MaterialTheme.colorScheme.outlineVariant,
                            )
                        }
                        sessions.forEach { s ->
                            WeekLine(
                                time = s.startedAt.atZone(zone).format(timeFmt),
                                label = if (s.type == SessionType.RUN) "RUN" else "STRENGTH",
                                detail = s.weekDetail(),
                                accent = if (s.type == SessionType.RUN) RunColor else StrengthColor,
                            )
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

/** One item in the week view: a coloured bar, time, label and the headline numbers. */
@Composable
private fun WeekLine(time: String, label: String, detail: String, accent: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(28.dp)
                .background(accent, MaterialTheme.shapes.extraSmall),
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(time, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (detail.isNotBlank()) {
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** e.g. "5.20 km · 26:30 · 5:05 /km" for a run, "Squat, Bench · 8 sets" for strength. */
private fun Session.weekDetail(): String = when (type) {
    SessionType.RUN -> buildList {
        distanceMeters?.let { add(String.format(Locale.getDefault(), "%.2f km", it / 1000)) }
        add(formatDuration(durationMillis))
        val d = distanceMeters
        if (d != null && d > 0) add(formatPace((durationMillis / 1000.0) / (d / 1000.0)))
    }.joinToString(" · ")
    SessionType.STRENGTH -> {
        val names = sets.map { it.exercise.name }.distinct()
        val head = names.take(3).joinToString(", ") + if (names.size > 3) "…" else ""
        listOf(head, "${sets.size} sets").filter { it.isNotBlank() }.joinToString(" · ")
    }
}

// --- list -----------------------------------------------------------------------------

private sealed class AgendaRow {
    data class Header(val date: LocalDate) : AgendaRow()
    data class Event(val date: LocalDate, val event: CalendarEvent) : AgendaRow()
    data class Done(val date: LocalDate, val session: Session) : AgendaRow()
}

/** Chronological list of everything in the range, grouped under date headers. */
@Composable
private fun AgendaList(
    from: LocalDate,
    to: LocalDate,
    sessionsByDate: Map<LocalDate, List<Session>>,
    eventsByDate: Map<LocalDate, List<CalendarEvent>>,
    onDayClick: (LocalDate) -> Unit,
) {
    val rows = buildList {
        var d = from
        while (!d.isAfter(to)) {
            val events = eventsByDate[d].orEmpty()
            val sessions = sessionsByDate[d].orEmpty()
            if (events.isNotEmpty() || sessions.isNotEmpty()) {
                add(AgendaRow.Header(d))
                events.forEach { add(AgendaRow.Event(d, it)) }
                sessions.forEach { add(AgendaRow.Done(d, it)) }
            }
            d = d.plusDays(1)
        }
    }
    if (rows.isEmpty()) {
        Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
            Text("NOTHING THIS MONTH", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    val today = LocalDate.now()
    val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
    val zone = ZoneId.systemDefault()

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        rows.forEach { row ->
            when (row) {
                is AgendaRow.Header -> item(key = "h${row.date}") {
                    Text(
                        text = row.date.format(DateTimeFormatter.ofPattern("EEE d MMM")).uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (row.date == today) NothingRed else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onDayClick(row.date) }
                            .padding(top = 16.dp, bottom = 4.dp),
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                is AgendaRow.Event -> item(key = "e${row.event.id}") {
                    AgendaLine(
                        time = if (row.event.allDay) "ALL DAY" else row.event.start.atZone(zone).format(timeFmt),
                        label = row.event.summary,
                        tag = "PLANNED",
                        tagColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = { onDayClick(row.date) },
                    )
                }
                is AgendaRow.Done -> item(key = "s${row.session.id}") {
                    val s = row.session
                    AgendaLine(
                        time = s.startedAt.atZone(zone).format(timeFmt),
                        label = when (s.type) {
                            SessionType.RUN -> "Run · " + s.summaryLabel() + " · " + formatDuration(s.durationMillis)
                            SessionType.STRENGTH -> "Strength · " + s.summaryLabel()
                        },
                        tag = if (s.type == SessionType.RUN) "RUN" else "GYM",
                        tagColor = if (s.type == SessionType.RUN) RunColor else StrengthColor,
                        onClick = { onDayClick(row.date) },
                    )
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun AgendaLine(
    time: String,
    label: String,
    tag: String,
    tagColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(time, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(56.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(tag, style = MaterialTheme.typography.labelSmall, color = tagColor)
    }
}
