package com.painani.app.ui.settings

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.painani.app.domain.model.UserProfile
import com.painani.app.domain.model.WeightEntry
import com.painani.app.domain.repository.BodyStatsRepository
import com.painani.app.domain.repository.CalendarEventRepository
import com.painani.app.domain.repository.ProfileRepository
import com.painani.app.ics.IcsParser
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
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

class SettingsViewModel(
    private val profiles: ProfileRepository,
    private val bodyStats: BodyStatsRepository,
    private val events: CalendarEventRepository,
) : ViewModel() {

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
            bodyStats.addWeight(WeightEntry(at = Instant.now(), weightKg = weightKg, note = note.trim()))
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
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SettingsViewModel(profiles, bodyStats, events) as T
    }
}
