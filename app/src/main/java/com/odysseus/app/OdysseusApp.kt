package com.odysseus.app

import android.app.Application
import com.odysseus.app.data.local.OdysseusDatabase
import com.odysseus.app.data.repository.DataStoreProfileRepository
import com.odysseus.app.data.repository.RoomBodyStatsRepository
import com.odysseus.app.data.repository.RoomCalendarEventRepository
import com.odysseus.app.data.repository.RoomSessionRepository
import com.odysseus.app.domain.repository.BodyStatsRepository
import com.odysseus.app.domain.repository.CalendarEventRepository
import com.odysseus.app.domain.repository.ProfileRepository
import com.odysseus.app.domain.repository.SessionRepository
import com.odysseus.app.tracking.LocationRunTracker

/**
 * Hand-rolled dependency container. Small enough that Hilt would be more ceremony than benefit;
 * swap it out if the graph grows.
 */
class AppContainer(app: Application) {
    val database: OdysseusDatabase by lazy { OdysseusDatabase.build(app) }
    val sessionRepository: SessionRepository by lazy { RoomSessionRepository(database) }
    val calendarEventRepository: CalendarEventRepository by lazy { RoomCalendarEventRepository(database) }
    val profileRepository: ProfileRepository by lazy { DataStoreProfileRepository(app) }
    val bodyStatsRepository: BodyStatsRepository by lazy { RoomBodyStatsRepository(database) }

    /** Shared between the foreground service (keeps it alive) and the run screen (observes it). */
    val runTracker: LocationRunTracker by lazy { LocationRunTracker(app) }
}

class OdysseusApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
