package com.painani.app.ui.calendar

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.painani.app.domain.repository.CalendarEventRepository
import com.painani.app.domain.repository.SessionRepository
import com.painani.app.ui.theme.NothingRed
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Full-screen view of one day, with arrows to step to neighbouring days without leaving it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayDetailScreen(
    initialDate: LocalDate,
    repository: SessionRepository,
    eventRepository: CalendarEventRepository,
    onBack: () -> Unit,
) {
    // LocalDate is not Saveable; keep the epoch day so the position survives rotation.
    var epochDay by rememberSaveable { mutableLongStateOf(initialDate.toEpochDay()) }
    val date = LocalDate.ofEpochDay(epochDay)

    val sessionFlow = remember(date) { repository.sessionsOn(date) }
    val eventFlow = remember(date) { eventRepository.eventsOn(date) }
    val sessions by sessionFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val events by eventFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val isToday = date == LocalDate.now()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        date.format(DateTimeFormatter.ofPattern("EEE d MMM yyyy")).uppercase(),
                        style = MaterialTheme.typography.titleLarge,
                        color = if (isToday) NothingRed else MaterialTheme.colorScheme.onSurface,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { epochDay -= 1 }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous day")
                    }
                    IconButton(onClick = { epochDay += 1 }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next day")
                    }
                },
            )
        },
    ) { padding ->
        DayContent(sessions = sessions, events = events, modifier = Modifier.padding(padding))
    }
}
