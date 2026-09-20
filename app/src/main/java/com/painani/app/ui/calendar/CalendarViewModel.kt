package com.painani.app.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.painani.app.domain.model.CalendarEvent
import com.painani.app.domain.model.Session
import com.painani.app.domain.repository.CalendarEventRepository
import com.painani.app.domain.repository.SessionRepository
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

enum class CalendarMode { DAY, WEEK, MONTH, LIST }

data class CalendarUiState(
    val mode: CalendarMode = CalendarMode.MONTH,
    /** The date the view is centred on: the day, a day in the week, or a day in the month. */
    val anchor: LocalDate = LocalDate.now(),
    val rangeStart: LocalDate = anchor,
    val rangeEnd: LocalDate = anchor,
    /** Sessions grouped by the local date they started on. */
    val sessionsByDate: Map<LocalDate, List<Session>> = emptyMap(),
    /** Imported events grouped by local date. */
    val eventsByDate: Map<LocalDate, List<CalendarEvent>> = emptyMap(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModel(
    private val sessions: SessionRepository,
    private val events: CalendarEventRepository,
) : ViewModel() {

    private data class View(val mode: CalendarMode, val anchor: LocalDate)

    private val view = MutableStateFlow(View(CalendarMode.MONTH, LocalDate.now()))

    val uiState: StateFlow<CalendarUiState> = view
        .flatMapLatest { v ->
            val (from, to) = v.range()
            combine(
                sessions.sessionsBetween(from, to),
                events.eventsBetween(from, to),
            ) { sessionList, eventList ->
                CalendarUiState(
                    mode = v.mode,
                    anchor = v.anchor,
                    rangeStart = from,
                    rangeEnd = to,
                    sessionsByDate = sessionList.groupBy { it.localDate() },
                    eventsByDate = eventList.groupBy { it.localDate() },
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = CalendarUiState(),
        )

    fun setMode(mode: CalendarMode) = view.update { it.copy(mode = mode) }
    fun setAnchor(date: LocalDate) = view.update { it.copy(anchor = date) }
    fun today() = view.update { it.copy(anchor = LocalDate.now()) }

    fun previous() = view.update { it.copy(anchor = it.shift(-1)) }
    fun next() = view.update { it.copy(anchor = it.shift(1)) }

    private fun View.shift(n: Long): LocalDate = when (mode) {
        CalendarMode.DAY -> anchor.plusDays(n)
        CalendarMode.WEEK -> anchor.plusWeeks(n)
        CalendarMode.MONTH, CalendarMode.LIST -> anchor.plusMonths(n)
    }

    private fun View.range(): Pair<LocalDate, LocalDate> = when (mode) {
        CalendarMode.DAY -> anchor to anchor
        CalendarMode.WEEK -> anchor.weekRange()
        // The grid shows leading/trailing days of neighbouring months, so fetch the whole grid.
        CalendarMode.MONTH -> YearMonth.from(anchor).visibleRange()
        CalendarMode.LIST -> YearMonth.from(anchor).let { it.atDay(1) to it.atEndOfMonth() }
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

/** Monday..Sunday of the week containing this date. */
fun LocalDate.weekRange(): Pair<LocalDate, LocalDate> {
    val monday = with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    return monday to monday.plusDays(6)
}
