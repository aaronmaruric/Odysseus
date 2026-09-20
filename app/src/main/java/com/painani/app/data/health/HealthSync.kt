package com.painani.app.data.health

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.painani.app.domain.model.Session
import com.painani.app.domain.model.SessionType
import com.painani.app.domain.model.WeightEntry
import com.painani.app.domain.repository.BodyStatsRepository
import com.painani.app.domain.repository.SessionRepository
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val Context.syncStore: DataStore<Preferences> by preferencesDataStore(name = "health_sync")

/**
 * What we do with Health Connect and when. Every call is best-effort: a missing permission or a
 * transient error is logged and swallowed, so the app never fails a save because of it.
 */
class HealthSync(
    private val context: Context,
    private val health: HealthConnectManager,
    private val sessions: SessionRepository,
    private val bodyStats: BodyStatsRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lastWeightPull = longPreferencesKey("last_weight_pull_epoch_ms")
    private val lastSync = longPreferencesKey("last_sync_epoch_ms")

    val lastSyncTime = context.syncStore.data.map { p -> p[lastSync]?.let { Instant.ofEpochMilli(it) } }

    /** Fire-and-forget: called right after a session row is written. */
    fun onSessionSaved(id: Long) {
        scope.launch {
            if (!health.hasAllPermissions()) return@launch
            val session = sessions.session(id) ?: return@launch
            runCatching { enrichWithHeartRate(session) }.onFailure { Log.w(TAG, "HR enrich failed", it) }
            val refreshed = sessions.session(id) ?: session
            runCatching { health.writeSession(refreshed) }.onFailure { Log.w(TAG, "write session failed", it) }
            touch()
        }
    }

    fun onWeightLogged(entry: WeightEntry) {
        if (entry.sourceId != null) return // came from Health Connect; do not echo it back
        scope.launch {
            if (!health.hasAllPermissions()) return@launch
            runCatching { health.writeWeight(entry) }.onFailure { Log.w(TAG, "write weight failed", it) }
            touch()
        }
    }

    /**
     * Manual "sync now": pulls weigh-ins from other apps and back-fills heart rate on any run
     * that does not have it yet. Returns a short human summary.
     */
    suspend fun syncNow(): String {
        if (!health.hasAllPermissions()) return "Not connected"
        var pulled = 0
        var enriched = 0

        runCatching {
            val since = context.syncStore.data.first()[lastWeightPull]?.let { Instant.ofEpochMilli(it) }
                ?: Instant.now().minus(Duration.ofDays(365))
            val known = bodyStats.knownSourceIds()
            health.externalWeights(since).filter { it.recordId !in known }.forEach { w ->
                bodyStats.addWeight(
                    WeightEntry(at = w.time, weightKg = w.kg, note = originLabel(w.origin), sourceId = w.recordId)
                )
                pulled++
            }
            context.syncStore.edit { it[lastWeightPull] = Instant.now().minus(Duration.ofDays(1)).toEpochMilli() }
        }.onFailure { Log.w(TAG, "weight pull failed", it) }

        runCatching {
            val cutoff = java.time.LocalDate.now().minusDays(30)
            sessions.sessionsBetween(cutoff, java.time.LocalDate.now()).first()
                .filter { it.type == SessionType.RUN && it.avgHeartRate == null }
                .forEach { if (enrichWithHeartRate(it)) enriched++ }
        }.onFailure { Log.w(TAG, "HR back-fill failed", it) }

        touch()
        return buildString {
            append("Synced")
            if (pulled > 0) append(" · $pulled weigh-in${if (pulled == 1) "" else "s"}")
            if (enriched > 0) append(" · HR on $enriched run${if (enriched == 1) "" else "s"}")
        }
    }

    /** Reads HR for the session window and stores the average/max plus a per-split average. */
    private suspend fun enrichWithHeartRate(session: Session): Boolean {
        val start = session.startedAt
        val end = start.plusMillis(session.durationMillis)
        val samples = health.heartRate(start, end)
        if (samples.isEmpty()) return false

        val avg = samples.map { it.bpm }.average().toInt()
        val max = samples.maxOf { it.bpm }

        // Split boundaries in wall-clock time. Pauses are not modelled, so this is approximate
        // for runs with long stops; still far better than nothing.
        var cursor = start
        val splitAverages = session.splits.map { split ->
            val splitEnd = cursor.plusMillis(split.durationMillis)
            val inWindow = samples.filter { it.time >= cursor && it.time < splitEnd }
            cursor = splitEnd
            inWindow.takeIf { it.isNotEmpty() }?.map { it.bpm }?.average()?.toInt()
        }
        sessions.updateHeartRate(session.id, avg, max, splitAverages)
        return true
    }

    private suspend fun touch() {
        context.syncStore.edit { it[lastSync] = System.currentTimeMillis() }
    }

    private fun originLabel(pkg: String): String = when {
        pkg.contains("samsung") -> "Samsung Health"
        pkg.contains("fitbit") -> "Fitbit"
        pkg.contains("garmin") -> "Garmin"
        pkg.contains("withings") -> "Withings"
        pkg.contains("google") -> "Google"
        else -> "Health Connect"
    }

    companion object {
        private const val TAG = "HealthSync"
    }
}
