package com.painani.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.painani.app.data.local.entity.WeightEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WeightEntryDao {
    @Query("SELECT * FROM weight_entries ORDER BY atEpochMillis DESC")
    fun all(): Flow<List<WeightEntryEntity>>

    @Insert
    suspend fun insert(entry: WeightEntryEntity): Long

    @Query("DELETE FROM weight_entries WHERE id = :id")
    suspend fun delete(id: Long)
}
