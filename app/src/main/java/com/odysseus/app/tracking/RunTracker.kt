package com.odysseus.app.tracking

import com.odysseus.app.domain.model.Split
import kotlinx.coroutines.flow.StateFlow

/** Live snapshot of an in-progress run, updated as GPS fixes arrive. */
data class RunState(
    val isRunning: Boolean = false,
    val elapsedMillis: Long = 0,
    val distanceMeters: Double = 0.0,
    val completedSplits: List<Split> = emptyList(),
    /** Current pace over the last ~30 s, in seconds per km. Null until enough fixes exist. */
    val currentPaceSecPerKm: Double? = null,
)

/**
 * Abstraction over the GPS engine so the UI never touches LocationManager directly.
 * The Android implementation will wrap the RunnerUp tracker; an iOS port would wrap CoreLocation.
 */
interface RunTracker {
    val state: StateFlow<RunState>
    fun start()
    fun pause()
    fun resume()
    /** Stops tracking and returns the final state for persisting as a Session. */
    fun stop(): RunState
}
