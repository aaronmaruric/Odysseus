package com.painani.app

import android.app.Application
import com.painani.app.data.local.PainaniDatabase
import com.painani.app.data.repository.DataStoreProfileRepository
import com.painani.app.data.repository.RoomBodyStatsRepository
import com.painani.app.data.repository.RoomCalendarEventRepository
import com.painani.app.data.repository.RoomSessionRepository
import com.painani.app.domain.repository.BodyStatsRepository
import com.painani.app.domain.repository.CalendarEventRepository
import com.painani.app.domain.repository.ProfileRepository
import com.painani.app.domain.repository.SessionRepository
import com.painani.app.tracking.LocationRunTracker

/**
 * Hand-rolled dependency container. Small enough that Hilt would be more ceremony than benefit;
 * swap it out if the graph grows.
 */
class AppContainer(app: Application) {
    val database: PainaniDatabase by lazy { PainaniDatabase.build(app) }
    val sessionRepository: SessionRepository by lazy { RoomSessionRepository(database) }
    val calendarEventRepository: CalendarEventRepository by lazy { RoomCalendarEventRepository(database) }
    val profileRepository: ProfileRepository by lazy { DataStoreProfileRepository(app) }
    val bodyStatsRepository: BodyStatsRepository by lazy { RoomBodyStatsRepository(database) }

    /** Shared between the foreground service (keeps it alive) and the run screen (observes it). */
    val runTracker: LocationRunTracker by lazy { LocationRunTracker(app) }
}

class PainaniApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
