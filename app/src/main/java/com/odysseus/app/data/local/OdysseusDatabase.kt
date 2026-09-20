package com.odysseus.app.data.local

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.odysseus.app.data.local.dao.CalendarEventDao
import com.odysseus.app.data.local.dao.SessionDao
import com.odysseus.app.data.local.entity.CalendarEventEntity
import com.odysseus.app.data.local.entity.ExerciseEntity
import com.odysseus.app.data.local.entity.ExerciseSetEntity
import com.odysseus.app.data.local.entity.SessionEntity
import com.odysseus.app.data.local.entity.SplitEntity
import com.odysseus.app.data.local.entity.TrackPointEntity

@Database(
    entities = [
        SessionEntity::class,
        SplitEntity::class,
        ExerciseEntity::class,
        ExerciseSetEntity::class,
        TrackPointEntity::class,
        CalendarEventEntity::class,
    ],
    version = 2,
    exportSchema = true,
    autoMigrations = [
        // v1 -> v2: adds track_points and calendar_events. Pure additions, so Room derives it.
        AutoMigration(from = 1, to = 2),
    ],
)
abstract class OdysseusDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun calendarEventDao(): CalendarEventDao

    companion object {
        fun build(context: Context): OdysseusDatabase =
            Room.databaseBuilder(context, OdysseusDatabase::class.java, "odysseus.db")
                .build()
    }
}
