package com.odysseus.app.ui.calendar

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.odysseus.app.domain.model.CalendarEvent
import com.odysseus.app.domain.model.Session
import com.odysseus.app.domain.repository.CalendarEventRepository
import com.odysseus.app.domain.repository.SessionRepository
import com.odysseus.app.ics.IcsParser
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CalendarUiState(
    val month: YearMonth,
    /** Sessions grouped by the local date they started on. */
    val sessionsByDate: Map<LocalDate, List<Session>> = emptyMap(),
    /** Imported events grouped by local date. */
    val eventsByDate: Map<LocalDate, List<CalendarEvent>> = emptyMap(),
    val importedSources: List<String> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModel(
    private val sessions: SessionRepository,
    private val events: CalendarEventRepository,
) : ViewModel() {

    private val month = MutableStateFlow(YearMonth.now())

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    /** One-shot user-facing messages (import results). */
    val messages: SharedFlow<String> = _messages

    val uiState: StateFlow<CalendarUiState> = month
        .flatMapLatest { m ->
            // Fetch the whole visible grid (up to 6 weeks) so leading/trailing days get markers too.
            val (from, to) = m.visibleRange()
            combine(
                sessions.sessionsBetween(from, to),
                events.eventsBetween(from, to),
                events.sources(),
            ) { sessionList, eventList, sources ->
                CalendarUiState(
                    month = m,
                    sessionsByDate = sessionList.groupBy { it.localDate() },
                    eventsByDate = eventList.groupBy { it.localDate() },
                    importedSources = sources,
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = CalendarUiState(month.value),
        )

    fun previousMonth() = month.update { it.minusMonths(1) }
    fun nextMonth() = month.update { it.plusMonths(1) }
    fun today() = month.update { YearMonth.now() }

    /** Reads an .ics from a SAF URI, parses it, and replaces any earlier import of the same file name. */
    fun importIcs(resolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val name = displayName(resolver, uri) ?: uri.lastPathSegment ?: "import.ics"
                    val text = resolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        ?: error("Could not open file")
                    val parsed = IcsParser.parse(text, source = name)
                    events.replaceSource(name, parsed)
                    name to parsed.size
                }
            }
            result.fold(
                onSuccess = { (name, n) -> _messages.tryEmit("Imported $n events from $name") },
                onFailure = { _messages.tryEmit("Import failed: ${it.message ?: it::class.simpleName}") },
            )
        }
    }

    fun removeSource(source: String) {
        viewModelScope.launch {
            events.deleteSource(source)
            _messages.tryEmit("Removed $source")
        }
    }

    private fun displayName(resolver: ContentResolver, uri: Uri): String? =
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }

    class Factory(
        private val sessions: SessionRepository,
        private val events: CalendarEventRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = CalendarViewModel(sessions, events) as T
    }
}

/** First and last dates shown in a 7-column month grid starting on Monday. */
fun YearMonth.visibleRange(): Pair<LocalDate, LocalDate> {
    val first = atDay(1)
    val leading = (first.dayOfWeek.value - 1).toLong() // Monday = 0
    val start = first.minusDays(leading)
    val end = start.plusDays(6 * 7 - 1)
    return start to end
}
