package com.odysseus.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.odysseus.app.data.local.dao.SessionDao
import com.odysseus.app.data.local.entity.ExerciseEntity
import com.odysseus.app.data.local.entity.ExerciseSetEntity
import com.odysseus.app.data.local.entity.SessionEntity
import com.odysseus.app.data.local.entity.SplitEntity

@Database(
    entities = [SessionEntity::class, SplitEntity::class, ExerciseEntity::class, ExerciseSetEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class OdysseusDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao

    companion object {
        fun build(context: Context): OdysseusDatabase =
            Room.databaseBuilder(context, OdysseusDatabase::class.java, "odysseus.db")
                // Schema is still evolving; drop and recreate on version bumps until 1.0.
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
