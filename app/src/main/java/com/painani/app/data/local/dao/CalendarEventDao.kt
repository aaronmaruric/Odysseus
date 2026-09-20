package com.painani.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.painani.app.data.local.entity.CalendarEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CalendarEventDao {
    @Query(
        "SELECT * FROM calendar_events WHERE startEpochMillis >= :fromMillis AND startEpochMillis < :toMillis " +
            "ORDER BY allDay DESC, startEpochMillis ASC"
    )
    fun eventsBetween(fromMillis: Long, toMillis: Long): Flow<List<CalendarEventEntity>>

    @Query("SELECT DISTINCT source FROM calendar_events ORDER BY source ASC")
    fun sources(): Flow<List<String>>

    @Insert
    suspend fun insertAll(events: List<CalendarEventEntity>)

    @Query("DELETE FROM calendar_events WHERE source = :source")
    suspend fun deleteSource(source: String)
}
