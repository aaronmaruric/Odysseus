package com.odysseus.app.data.local.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "sessions", indices = [Index("startedAtEpochMillis")])
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Stored as the enum name: "RUN" | "STRENGTH". */
    val type: String,
    val startedAtEpochMillis: Long,
    val durationMillis: Long,
    val notes: String,
    val distanceMeters: Double?,
)

@Entity(
    tableName = "splits",
    foreignKeys = [ForeignKey(
        entity = SessionEntity::class,
        parentColumns = ["id"],
        childColumns = ["sessionId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("sessionId")],
)
data class SplitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val index: Int,
    val distanceMeters: Double,
    val durationMillis: Long,
    val avgHeartRate: Int?,
)

@Entity(tableName = "exercises", indices = [Index(value = ["name"], unique = true)])
data class ExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)

@Entity(
    tableName = "exercise_sets",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("sessionId"), Index("exerciseId")],
)
data class ExerciseSetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val exerciseId: Long,
    val setIndex: Int,
    val reps: Int,
    val weightKg: Double,
)

/** A set joined with its exercise, so the UI does not need a second lookup. */
data class ExerciseSetWithExercise(
    @Embedded val set: ExerciseSetEntity,
    @Relation(parentColumn = "exerciseId", entityColumn = "id")
    val exercise: ExerciseEntity,
)

/** A session with all children loaded. */
data class SessionWithDetails(
    @Embedded val session: SessionEntity,
    @Relation(parentColumn = "id", entityColumn = "sessionId")
    val splits: List<SplitEntity>,
    @Relation(entity = ExerciseSetEntity::class, parentColumn = "id", entityColumn = "sessionId")
    val sets: List<ExerciseSetWithExercise>,
    @Relation(parentColumn = "id", entityColumn = "sessionId")
    val trackPoints: List<TrackPointEntity>,
)

@Entity(
    tableName = "track_points",
    foreignKeys = [ForeignKey(
        entity = SessionEntity::class,
        parentColumns = ["id"],
        childColumns = ["sessionId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("sessionId")],
)
data class TrackPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val timeMillis: Long,
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double?,
    val accuracyMeters: Float?,
)

@Entity(
    tableName = "calendar_events",
    indices = [Index("startEpochMillis"), Index("source")],
)
data class CalendarEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uid: String,
    val summary: String,
    val description: String,
    val location: String,
    val startEpochMillis: Long,
    val endEpochMillis: Long,
    val allDay: Boolean,
    val source: String,
)

@Entity(tableName = "weight_entries", indices = [Index("atEpochMillis")])
data class WeightEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val atEpochMillis: Long,
    val weightKg: Double,
    val note: String,
)
