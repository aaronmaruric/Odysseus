package com.odysseus.app

import android.app.Application
import com.odysseus.app.data.local.OdysseusDatabase
import com.odysseus.app.data.repository.RoomSessionRepository
import com.odysseus.app.domain.repository.SessionRepository

/**
 * Hand-rolled dependency container. Small enough that Hilt would be more ceremony than benefit;
 * swap it out if the graph grows.
 */
class AppContainer(app: Application) {
    val database: OdysseusDatabase by lazy { OdysseusDatabase.build(app) }
    val sessionRepository: SessionRepository by lazy { RoomSessionRepository(database) }
}

class OdysseusApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
