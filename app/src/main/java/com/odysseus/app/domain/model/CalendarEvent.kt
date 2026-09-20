package com.odysseus.app.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * A planned event imported from an .ics file (a training plan, a race, a class).
 * Distinct from [Session], which is something that actually happened.
 */
data class CalendarEvent(
    val id: Long = 0,
    /** iCalendar UID. Recurring events share a UID; each occurrence is its own row. */
    val uid: String,
    val summary: String,
    val description: String = "",
    val location: String = "",
    val start: Instant,
    val end: Instant,
    val allDay: Boolean = false,
    /** Display name of the file it came from, so imports can be listed and removed. */
    val source: String,
) {
    fun localDate(zone: ZoneId = ZoneId.systemDefault()): LocalDate =
        start.atZone(zone).toLocalDate()
}
