package com.painani.app.domain.repository

import com.painani.app.domain.model.DailyHealth
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/** Locally cached daily readouts from Health Connect. */
interface HealthDataRepository {
    fun daily(from: LocalDate, to: LocalDate): Flow<List<DailyHealth>>

    /** Earliest cached day, so a sync knows how far back it has already pulled. */
    suspend fun earliestDate(): LocalDate?

    suspend fun upsert(days: List<DailyHealth>)
}
