package com.painani.app.ics

import com.painani.app.domain.model.CalendarEvent
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

/**
 * Minimal iCalendar (RFC 5545) reader. Handles what training-plan exports actually use:
 * line unfolding, DTSTART/DTEND in UTC, floating, TZID and DATE forms, DURATION, and
 * RRULE with FREQ/INTERVAL/COUNT/UNTIL/BYDAY. Anything more exotic is ignored rather than
 * failing the whole import.
 */
object IcsParser {

    /** Recurrences are expanded at most this far past [now] so an endless RRULE stays bounded. */
    private const val MAX_EXPANSION_DAYS = 730L
    private const val MAX_OCCURRENCES = 1000

    private val basicDate = DateTimeFormatter.ofPattern("yyyyMMdd")
    private val basicDateTime = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")

    fun parse(
        text: String,
        source: String,
        defaultZone: ZoneId = ZoneId.systemDefault(),
        now: LocalDate = LocalDate.now(defaultZone),
    ): List<CalendarEvent> {
        val lines = unfold(text)
        val events = mutableListOf<CalendarEvent>()
        var current: MutableMap<String, Property>? = null

        for (line in lines) {
            when {
                line.equals("BEGIN:VEVENT", ignoreCase = true) -> current = mutableMapOf()
                line.equals("END:VEVENT", ignoreCase = true) -> {
                    current?.let { props ->
                        runCatching { toEvents(props, source, defaultZone, now) }
                            .getOrDefault(emptyList())
                            .let(events::addAll)
                    }
                    current = null
                }
                current != null -> parseProperty(line)?.let { current[it.name] = it }
            }
        }
        return events.sortedBy { it.start }
    }

    // --- line-level -----------------------------------------------------------------------

    /** Joins continuation lines (those starting with a space or tab) back onto the previous line. */
    private fun unfold(text: String): List<String> {
        val out = mutableListOf<String>()
        for (raw in text.lineSequence()) {
            val line = raw.trimEnd('\r')
            if (line.isEmpty()) continue
            if ((line[0] == ' ' || line[0] == '\t') && out.isNotEmpty()) {
                out[out.lastIndex] = out.last() + line.substring(1)
            } else {
                out += line
            }
        }
        return out
    }

    private data class Property(val name: String, val params: Map<String, String>, val value: String)

    private fun parseProperty(line: String): Property? {
        val colon = line.indexOf(':')
        if (colon <= 0) return null
        val head = line.substring(0, colon)
        val value = line.substring(colon + 1)
        val parts = head.split(';')
        val params = parts.drop(1).mapNotNull { p ->
            val eq = p.indexOf('=')
            if (eq > 0) p.substring(0, eq).uppercase() to p.substring(eq + 1).trim('"') else null
        }.toMap()
        return Property(parts[0].uppercase(), params, value)
    }

    // --- event-level ----------------------------------------------------------------------

    private fun toEvents(
        props: Map<String, Property>,
        source: String,
        defaultZone: ZoneId,
        now: LocalDate,
    ): List<CalendarEvent> {
        val dtstart = props["DTSTART"] ?: return emptyList()
        val (start, allDay) = parseDateTime(dtstart, defaultZone)

        val end = props["DTEND"]?.let { parseDateTime(it, defaultZone).first }
            ?: props["DURATION"]?.let { start.plus(parseDuration(it.value)) }
            ?: if (allDay) start.plusSeconds(86_400) else start

        val base = CalendarEvent(
            uid = props["UID"]?.value ?: "${source}:${start.toEpochMilli()}",
            summary = unescape(props["SUMMARY"]?.value ?: "(untitled)"),
            description = unescape(props["DESCRIPTION"]?.value.orEmpty()),
            location = unescape(props["LOCATION"]?.value.orEmpty()),
            start = start,
            end = end,
            allDay = allDay,
            source = source,
        )

        val rrule = props["RRULE"] ?: return listOf(base)
        val duration = java.time.Duration.between(start, end)
        val horizon = now.plusDays(MAX_EXPANSION_DAYS).atStartOfDay(defaultZone).toInstant()
        return expand(rrule.value, start, defaultZone, horizon).map { occurrence ->
            base.copy(start = occurrence, end = occurrence.plus(duration))
        }
    }

    private fun parseDateTime(p: Property, defaultZone: ZoneId): Pair<Instant, Boolean> {
        val v = p.value.trim()
        val isDate = p.params["VALUE"] == "DATE" || v.length == 8
        if (isDate) {
            val date = LocalDate.parse(v, basicDate)
            return date.atStartOfDay(defaultZone).toInstant() to true
        }
        val zone = when {
            v.endsWith("Z") -> ZoneOffset.UTC
            p.params["TZID"] != null -> runCatching { ZoneId.of(p.params.getValue("TZID")) }.getOrDefault(defaultZone)
            else -> defaultZone
        }
        val ldt = LocalDateTime.parse(v.removeSuffix("Z"), basicDateTime)
        return ldt.atZone(zone).toInstant() to false
    }

    /** ISO 8601 durations as used by iCalendar, e.g. PT1H30M, P1D, PT45M. */
    internal fun parseDuration(v: String): java.time.Duration {
        val s = v.trim().removePrefix("+")
        // java.time.Duration does not accept the "P1DT..." day component the way iCal writes it
        // when weeks are present, so handle W/D manually and hand the T-part over.
        val negative = s.startsWith("-")
        val body = s.removePrefix("-").removePrefix("P")
        val tIndex = body.indexOf('T')
        val datePart = if (tIndex >= 0) body.substring(0, tIndex) else body
        val timePart = if (tIndex >= 0) body.substring(tIndex) else ""

        var seconds = 0L
        Regex("(\\d+)([WD])").findAll(datePart).forEach { m ->
            val n = m.groupValues[1].toLong()
            seconds += when (m.groupValues[2]) { "W" -> n * 7 * 86_400; else -> n * 86_400 }
        }
        if (timePart.isNotEmpty()) seconds += java.time.Duration.parse("P$timePart").seconds
        val d = java.time.Duration.ofSeconds(seconds)
        return if (negative) d.negated() else d
    }

    private fun unescape(s: String): String =
        s.replace("\\n", "\n").replace("\\N", "\n").replace("\\,", ",").replace("\\;", ";").replace("\\\\", "\\")

    // --- RRULE ----------------------------------------------------------------------------

    private val byDayCodes = mapOf(
        "MO" to DayOfWeek.MONDAY, "TU" to DayOfWeek.TUESDAY, "WE" to DayOfWeek.WEDNESDAY,
        "TH" to DayOfWeek.THURSDAY, "FR" to DayOfWeek.FRIDAY, "SA" to DayOfWeek.SATURDAY, "SU" to DayOfWeek.SUNDAY,
    )

    internal fun expand(rrule: String, start: Instant, zone: ZoneId, horizon: Instant): List<Instant> {
        val parts = rrule.split(';').mapNotNull { p ->
            val eq = p.indexOf('=')
            if (eq > 0) p.substring(0, eq).uppercase() to p.substring(eq + 1) else null
        }.toMap()

        val freq = parts["FREQ"]?.uppercase() ?: return listOf(start)
        val interval = parts["INTERVAL"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val count = parts["COUNT"]?.toIntOrNull()
        val until = parts["UNTIL"]?.let { u ->
            runCatching {
                if (u.length == 8) LocalDate.parse(u, basicDate).plusDays(1).atStartOfDay(zone).toInstant()
                else LocalDateTime.parse(u.removeSuffix("Z"), basicDateTime)
                    .atZone(if (u.endsWith("Z")) ZoneOffset.UTC else zone).toInstant()
            }.getOrNull()
        }
        val byDay = parts["BYDAY"]?.split(',')?.mapNotNull { byDayCodes[it.takeLast(2).uppercase()] }?.toSet()

        val limit = minOf(until ?: horizon, horizon)
        val startZ = start.atZone(zone)
        val out = mutableListOf<Instant>()

        fun accept(candidate: java.time.ZonedDateTime): Boolean {
            val inst = candidate.toInstant()
            if (inst > limit) return false
            if (inst >= start) out += inst
            return out.size < (count ?: MAX_OCCURRENCES) && out.size < MAX_OCCURRENCES
        }

        when (freq) {
            "DAILY" -> {
                var c = startZ
                while (accept(c)) c = c.plusDays(interval.toLong())
            }
            "WEEKLY" -> {
                val days = byDay ?: setOf(startZ.dayOfWeek)
                // Walk week by week from the Monday of the start week, emitting the requested weekdays.
                var weekStart = startZ.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                outer@ while (true) {
                    for (d in DayOfWeek.entries) {
                        if (d !in days) continue
                        val c = weekStart.with(TemporalAdjusters.nextOrSame(d))
                        if (c.toInstant() < start) continue
                        if (!accept(c)) break@outer
                    }
                    weekStart = weekStart.plusWeeks(interval.toLong())
                    if (weekStart.toInstant() > limit) break
                }
            }
            "MONTHLY" -> {
                // Always add months to the original start so a 31st does not drift after a short month.
                var i = 0L
                while (accept(startZ.plusMonths(i * interval))) i++
            }
            "YEARLY" -> {
                var c = startZ
                while (accept(c)) c = c.plusYears(interval.toLong())
            }
            else -> out += start
        }
        return out
    }
}
