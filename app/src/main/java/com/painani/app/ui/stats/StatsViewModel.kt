package com.painani.app.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.painani.app.data.health.HealthConnectManager
import com.painani.app.data.health.HealthSync
import com.painani.app.domain.repository.BodyStatsRepository
import com.painani.app.domain.repository.HealthDataRepository
import com.painani.app.domain.repository.SessionRepository
import com.painani.app.domain.stats.BodyStats
import com.painani.app.domain.stats.ConsistencyStats
import com.painani.app.domain.stats.HealthStats
import com.painani.app.domain.stats.RunningStats
import com.painani.app.domain.stats.Stats
import com.painani.app.domain.stats.StatsRange
import com.painani.app.domain.stats.StrengthStats
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class StatsUiState(
    val range: StatsRange = StatsRange.W12,
    val from: LocalDate = LocalDate.now(),
    val today: LocalDate = LocalDate.now(),
    val consistency: ConsistencyStats? = null,
    val running: RunningStats? = null,
    val strength: StrengthStats? = null,
    val body: BodyStats? = null,
    val health: HealthStats? = null,
    val healthConnected: Boolean = false,
    val loaded: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModel(
    private val sessions: SessionRepository,
    private val bodyStats: BodyStatsRepository,
    private val healthData: HealthDataRepository,
    private val health: HealthConnectManager,
    private val healthSync: HealthSync,
) : ViewModel() {

    private val range = MutableStateFlow(StatsRange.W12)
    private val connected = MutableStateFlow(false)

    /** Exercise whose progression chart is shown; null means "the most-trained one". */
    private val _selectedExercise = MutableStateFlow<String?>(null)
    val selectedExercise: StateFlow<String?> = _selectedExercise

    init {
        refreshHealth()
    }

    val uiState: StateFlow<StatsUiState> = range.flatMapLatest { r ->
        val today = LocalDate.now()
        val from = r.start(today)
        combine(
            sessions.sessionsBetween(from, today),
            bodyStats.weights(),
            healthData.daily(from, today),
            connected,
        ) { sessionList, weights, days, isConnected ->
            StatsUiState(
                range = r,
                from = from,
                today = today,
                consistency = Stats.consistency(sessionList, from, today),
                running = Stats.running(sessionList),
                strength = Stats.strength(sessionList),
                body = BodyStats(weights.filter { !it.at.isBefore(from.atStartOfDay(ZoneId.systemDefault()).toInstant()) }),
                health = HealthStats(days),
                healthConnected = isConnected,
                loaded = true,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())

    fun setRange(r: StatsRange) {
        range.value = r
    }

    fun selectExercise(name: String) {
        _selectedExercise.value = name
    }

    /** Re-checks the Health Connect grant and, if connected, pulls anything new since last time. */
    fun refreshHealth() {
        viewModelScope.launch {
            val ok = runCatching { health.hasAllPermissions() }.getOrDefault(false)
            connected.value = ok
            if (ok) healthSync.refreshIfStale()
        }
    }

    class Factory(
        private val sessions: SessionRepository,
        private val bodyStats: BodyStatsRepository,
        private val healthData: HealthDataRepository,
        private val health: HealthConnectManager,
        private val healthSync: HealthSync,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            StatsViewModel(sessions, bodyStats, healthData, health, healthSync) as T
    }
}
