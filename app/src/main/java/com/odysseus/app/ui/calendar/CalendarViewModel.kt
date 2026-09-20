package com.odysseus.app.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.odysseus.app.domain.model.Session
import com.odysseus.app.domain.repository.SessionRepository
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class CalendarUiState(
    val month: YearMonth,
    /** Sessions grouped by the local date they started on. */
    val sessionsByDate: Map<LocalDate, List<Session>> = emptyMap(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModel(private val repository: SessionRepository) : ViewModel() {

    private val month = MutableStateFlow(YearMonth.now())

    val uiState: StateFlow<CalendarUiState> = month
        .flatMapLatest { m ->
            // Fetch the whole visible grid (up to 6 weeks) so leading/trailing days get markers too.
            val range = m.visibleRange()
            repository.sessionsBetween(range.first, range.second).map { sessions ->
                CalendarUiState(month = m, sessionsByDate = sessions.groupBy { it.localDate() })
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

    class Factory(private val repository: SessionRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = CalendarViewModel(repository) as T
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
