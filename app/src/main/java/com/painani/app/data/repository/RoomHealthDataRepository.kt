package com.painani.app.data.repository

import com.painani.app.data.local.PainaniDatabase
import com.painani.app.data.local.entity.DailyHealthEntity
import com.painani.app.domain.model.DailyHealth
import com.painani.app.domain.repository.HealthDataRepository
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomHealthDataRepository(private val db: PainaniDatabase) : HealthDataRepository {
    private val dao get() = db.dailyHealthDao()

    override fun daily(from: LocalDate, to: LocalDate): Flow<List<DailyHealth>> =
        dao.between(from.toString(), to.toString()).map { rows -> rows.map { it.toDomain() } }

    override suspend fun earliestDate(): LocalDate? = dao.earliestDate()?.let { LocalDate.parse(it) }

    override suspend fun upsert(days: List<DailyHealth>) {
        if (days.isEmpty()) return
        val now = System.currentTimeMillis()
        dao.upsertAll(days.map { it.toEntity(now) })
    }

    private fun DailyHealthEntity.toDomain() = DailyHealth(
        date = LocalDate.parse(date),
        steps = steps,
        distanceMeters = distanceMeters,
        activeCalories = activeCalories,
        exerciseMinutes = exerciseMinutes,
        sleepMinutes = sleepMinutes,
        sleepDeepMinutes = sleepDeepMinutes,
        sleepLightMinutes = sleepLightMinutes,
        sleepRemMinutes = sleepRemMinutes,
        sleepAwakeMinutes = sleepAwakeMinutes,
        sleepStart = sleepStartEpochMillis?.let { Instant.ofEpochMilli(it) },
        sleepEnd = sleepEndEpochMillis?.let { Instant.ofEpochMilli(it) },
        restingHr = restingHr,
    )

    private fun DailyHealth.toEntity(now: Long) = DailyHealthEntity(
        date = date.toString(),
        steps = steps,
        distanceMeters = distanceMeters,
        activeCalories = activeCalories,
        exerciseMinutes = exerciseMinutes,
        sleepMinutes = sleepMinutes,
        sleepDeepMinutes = sleepDeepMinutes,
        sleepLightMinutes = sleepLightMinutes,
        sleepRemMinutes = sleepRemMinutes,
        sleepAwakeMinutes = sleepAwakeMinutes,
        sleepStartEpochMillis = sleepStart?.toEpochMilli(),
        sleepEndEpochMillis = sleepEnd?.toEpochMilli(),
        restingHr = restingHr,
        updatedAtEpochMillis = now,
    )
}
