package com.painani.app.domain.repository

import com.painani.app.domain.model.UserProfile
import com.painani.app.domain.model.WeightEntry
import kotlinx.coroutines.flow.Flow

interface ProfileRepository {
    val profile: Flow<UserProfile>
    suspend fun save(profile: UserProfile)
}

interface BodyStatsRepository {
    /** Newest first. */
    fun weights(): Flow<List<WeightEntry>>
    suspend fun addWeight(entry: WeightEntry): Long
    suspend fun deleteWeight(id: Long)
}
