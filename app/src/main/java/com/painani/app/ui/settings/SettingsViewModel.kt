package com.painani.app.ui.settings

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.painani.app.data.health.AutoSyncSettings
import com.painani.app.data.health.DailyReadout
import com.painani.app.data.health.HealthConnectManager
import com.painani.app.data.health.HealthStatus
import com.painani.app.data.health.HealthSync
import com.painani.app.domain.model.UserProfile
import com.painani.app.domain.model.WeightEntry
import com.painani.app.domain.repository.BodyStatsRepository
import com.painani.app.domain.repository.CalendarEventRepository
import com.painani.app.domain.repository.ProfileRepository
import com.painani.app.ics.IcsParser
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SettingsUiState(
    val profile: UserProfile = UserProfile(),
    val weights: List<WeightEntry> = emptyList(),
    val importedSources: List<String> = emptyList(),
    val loaded: Boolean = false,
)

data class HealthUiState(
    val status: HealthStatus = HealthStatus.UNSUPPORTED,
    val connected: Boolean = false,
    val readout: DailyReadout? = null,
    val lastSync: Instant? = null,
    val syncing: Boolean = false,
    /** Null when this Health Connect build cannot read in the background at all. */
    val backgroundGranted: Boolean? = null,
    val historyGranted: Boolean? = null,
    val autoSync: AutoSyncSettings = AutoSyncSettings(),
)

class SettingsViewModel(
    private val profiles: ProfileRepository,
    private val bodyStats: BodyStatsRepository,
    private val events: CalendarEventRepository,
    private val health: HealthConnectManager,
    private val healthSync: HealthSync,
) : ViewModel() {

    private val _health = MutableStateFlow(HealthUiState(status = health.status))
    val healthState: StateFlow<HealthUiState> = _health

    /** Data permissions plus background/history reads where the device offers them. */
    val healthPermissions: Set<String> get() = health.requestablePermissions()
    val healthPermissionContract get() = health.permissionContract

    init {
        refreshHealth()
        viewModelScope.launch { healthSync.lastSyncTime.collect { t -> _health.update { it.copy(lastSync = t) } } }
        viewModelScope.launch { healthSync.autoSyncSettings.collect { a -> _health.update { it.copy(autoSync = a) } } }
    }

    fun setAutoSync(settings: AutoSyncSettings) {
        viewModelScope.launch { healthSync.setAutoSync(settings) }
    }

    /** Re-checks availability and permissions, then loads the daily readout if connected. */
    fun refreshHealth() {
        viewModelScope.launch {
            val status = health.status
            val connected = runCatching { health.hasAllPermissions() }.getOrDefault(false)
            val background = if (health.supportsBackgroundRead) runCatching { health.hasBackgroundRead() }.getOrDefault(false) else null
            val history = if (health.supportsHistoryRead) runCatching { health.hasHistoryRead() }.getOrDefault(false) else null
            _health.update { it.copy(status = status, connected = connected, backgroundGranted = background, historyGranted = history) }
            if (connected) {
                val readout = runCatching { health.dailyReadout() }.getOrNull()
                _health.update { it.copy(readout = readout) }
            }
        }
    }

    fun syncHealthNow() {
        viewModelScope.launch {
            _health.update { it.copy(syncing = true) }
            val msg = runCatching { healthSync.syncNow() }.getOrElse { "Sync failed: ${it.message}" }
            _health.update { it.copy(syncing = false) }
            _messages.tryEmit(msg)
            refreshHealth()
        }
    }

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages: SharedFlow<String> = _messages

    val uiState: StateFlow<SettingsUiState> = combine(
        profiles.profile,
        bodyStats.weights(),
        events.sources(),
    ) { profile, weights, sources ->
        SettingsUiState(profile, weights, sources, loaded = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun saveProfile(profile: UserProfile) {
        viewModelScope.launch {
            profiles.save(profile)
            _messages.tryEmit("Profile saved")
        }
    }

    fun logWeight(weightKg: Double, note: String) {
        viewModelScope.launch {
            val entry = WeightEntry(at = Instant.now(), weightKg = weightKg, note = note.trim())
            val id = bodyStats.addWeight(entry)
            healthSync.onWeightLogged(entry.copy(id = id))
        }
    }

    fun deleteWeight(id: Long) {
        viewModelScope.launch { bodyStats.deleteWeight(id) }
    }

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
        private val profiles: ProfileRepository,
        private val bodyStats: BodyStatsRepository,
        private val events: CalendarEventRepository,
        private val health: HealthConnectManager,
        private val healthSync: HealthSync,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SettingsViewModel(profiles, bodyStats, events, health, healthSync) as T
    }
}
