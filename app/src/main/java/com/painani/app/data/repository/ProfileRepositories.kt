package com.painani.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.painani.app.data.local.PainaniDatabase
import com.painani.app.data.local.entity.WeightEntryEntity
import com.painani.app.domain.model.Gender
import com.painani.app.domain.model.UserProfile
import com.painani.app.domain.model.WeightEntry
import com.painani.app.domain.repository.BodyStatsRepository
import com.painani.app.domain.repository.ProfileRepository
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.profileStore: DataStore<Preferences> by preferencesDataStore(name = "profile")

class DataStoreProfileRepository(private val context: Context) : ProfileRepository {

    private object Keys {
        val name = stringPreferencesKey("name")
        val gender = stringPreferencesKey("gender")
        val heightCm = doublePreferencesKey("height_cm")
        val birthDate = stringPreferencesKey("birth_date") // ISO yyyy-MM-dd
    }

    override val profile: Flow<UserProfile> = context.profileStore.data.map { p ->
        UserProfile(
            name = p[Keys.name].orEmpty(),
            gender = p[Keys.gender]?.let { runCatching { Gender.valueOf(it) }.getOrNull() } ?: Gender.UNSPECIFIED,
            heightCm = p[Keys.heightCm],
            birthDate = p[Keys.birthDate]?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
        )
    }

    override suspend fun save(profile: UserProfile) {
        context.profileStore.edit { p ->
            p[Keys.name] = profile.name
            p[Keys.gender] = profile.gender.name
            profile.heightCm?.let { p[Keys.heightCm] = it } ?: p.remove(Keys.heightCm)
            profile.birthDate?.let { p[Keys.birthDate] = it.toString() } ?: p.remove(Keys.birthDate)
        }
    }
}

class RoomBodyStatsRepository(private val db: PainaniDatabase) : BodyStatsRepository {
    private val dao get() = db.weightEntryDao()

    override fun weights(): Flow<List<WeightEntry>> =
        dao.all().map { rows ->
            rows.map { WeightEntry(it.id, Instant.ofEpochMilli(it.atEpochMillis), it.weightKg, it.note, it.sourceId) }
        }

    override suspend fun addWeight(entry: WeightEntry): Long =
        dao.insert(WeightEntryEntity(0, entry.at.toEpochMilli(), entry.weightKg, entry.note, entry.sourceId))

    override suspend fun deleteWeight(id: Long) = dao.delete(id)

    override suspend fun knownSourceIds(): Set<String> = dao.sourceIds().toSet()
}
