package com.painani.app.domain.model

import java.time.Instant
import java.time.LocalDate

/**
 * What a watch or Samsung Health recorded for one day, as surfaced through Health Connect.
 * Sleep is attributed to the day it ended on, so "Tuesday's sleep" is Monday night.
 */
data class DailyHealth(
    val date: LocalDate,
    val steps: Long? = null,
    val distanceMeters: Double? = null,
    val activeCalories: Double? = null,
    val exerciseMinutes: Int? = null,
    val sleepMinutes: Int? = null,
    val sleepDeepMinutes: Int? = null,
    val sleepLightMinutes: Int? = null,
    val sleepRemMinutes: Int? = null,
    val sleepAwakeMinutes: Int? = null,
    val sleepStart: Instant? = null,
    val sleepEnd: Instant? = null,
    val restingHr: Int? = null,
) {
    /** True when the day has nothing worth storing. */
    val isEmpty: Boolean
        get() = steps == null && distanceMeters == null && activeCalories == null && exerciseMinutes == null &&
            sleepMinutes == null && restingHr == null
}
