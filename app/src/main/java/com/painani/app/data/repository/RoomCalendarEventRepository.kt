package com.painani.app.data.repository

import androidx.room.withTransaction
import com.painani.app.data.local.PainaniDatabase
import com.painani.app.data.local.entity.CalendarEventEntity
import com.painani.app.domain.model.CalendarEvent
import com.painani.app.domain.repository.CalendarEventRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomCalendarEventRepository(
    private val db: PainaniDatabase,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : CalendarEventRepository {

    private val dao get() = db.calendarEventDao()

    override fun eventsBetween(from: LocalDate, to: LocalDate): Flow<List<CalendarEvent>> {
        val fromMillis = from.atStartOfDay(zone).toInstant().toEpochMilli()
        val toMillis = to.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return dao.eventsBetween(fromMillis, toMillis).map { rows -> rows.map { it.toDomain() } }
    }

    override fun eventsOn(date: LocalDate): Flow<List<CalendarEvent>> = eventsBetween(date, date)

    override fun sources(): Flow<List<String>> = dao.sources()

    override suspend fun replaceSource(source: String, events: List<CalendarEvent>) = db.withTransaction {
        dao.deleteSource(source)
        dao.insertAll(events.map { it.toEntity(source) })
    }

    override suspend fun deleteSource(source: String) = dao.deleteSource(source)

    override suspend fun update(event: CalendarEvent) = dao.update(event.toEntity(event.source).copy(id = event.id))

    override suspend fun delete(id: Long) = dao.delete(id)

    private fun CalendarEventEntity.toDomain() = CalendarEvent(
        id = id,
        uid = uid,
        summary = summary,
        description = description,
        location = location,
        start = Instant.ofEpochMilli(startEpochMillis),
        end = Instant.ofEpochMilli(endEpochMillis),
        allDay = allDay,
        source = source,
    )

    private fun CalendarEvent.toEntity(source: String) = CalendarEventEntity(
        id = 0,
        uid = uid,
        summary = summary,
        description = description,
        location = location,
        startEpochMillis = start.toEpochMilli(),
        endEpochMillis = end.toEpochMilli(),
        allDay = allDay,
        source = source,
    )
}
