package com.odysseus.app.domain.repository

import com.odysseus.app.domain.model.CalendarEvent
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

interface CalendarEventRepository {
    fun eventsBetween(from: LocalDate, to: LocalDate): Flow<List<CalendarEvent>>

    fun eventsOn(date: LocalDate): Flow<List<CalendarEvent>>

    /** Names of every file that has been imported, for a manage/remove list. */
    fun sources(): Flow<List<String>>

    /**
     * Replaces everything previously imported from [source] with [events].
     * Re-importing the same file is therefore idempotent.
     */
    suspend fun replaceSource(source: String, events: List<CalendarEvent>)

    suspend fun deleteSource(source: String)
}
