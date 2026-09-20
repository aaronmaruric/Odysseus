package com.painani.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.painani.app.data.local.entity.DailyHealthEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyHealthDao {
    /** ISO dates sort lexically, so a plain string comparison is a date comparison. */
    @Query("SELECT * FROM daily_health WHERE date >= :from AND date <= :to ORDER BY date ASC")
    fun between(from: String, to: String): Flow<List<DailyHealthEntity>>

    @Query("SELECT MIN(date) FROM daily_health")
    suspend fun earliestDate(): String?

    @Upsert
    suspend fun upsertAll(days: List<DailyHealthEntity>)
}
