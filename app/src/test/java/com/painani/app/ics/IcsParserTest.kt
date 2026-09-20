package com.painani.app.ics

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IcsParserTest {

    private val utc: ZoneId = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 9, 20)

    @Test
    fun `parses a single timed event and unfolds long lines`() {
        val ics = """
            BEGIN:VCALENDAR
            BEGIN:VEVENT
            UID:abc@example.com
            DTSTART:20260921T070000Z
            DTEND:20260921T080000Z
            SUMMARY:Tempo run\, 8k
            DESCRIPTION:This is a long description that has been folded onto
              a second line by the exporter.
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val events = IcsParser.parse(ics, "plan.ics", utc, today)
        assertEquals(1, events.size)
        val e = events.single()
        assertEquals("Tempo run, 8k", e.summary)
        assertEquals("This is a long description that has been folded onto a second line by the exporter.", e.description)
        assertEquals(Instant.parse("2026-09-21T07:00:00Z"), e.start)
        assertEquals(Duration.ofHours(1), Duration.between(e.start, e.end))
        assertTrue(!e.allDay)
    }

    @Test
    fun `all-day events use VALUE=DATE`() {
        val ics = "BEGIN:VEVENT\nDTSTART;VALUE=DATE:20261005\nSUMMARY:Race day\nEND:VEVENT"
        val e = IcsParser.parse(ics, "x", utc, today).single()
        assertTrue(e.allDay)
        assertEquals(LocalDate.of(2026, 10, 5), e.localDate(utc))
        assertEquals(Duration.ofDays(1), Duration.between(e.start, e.end))
    }

    @Test
    fun `weekly RRULE with BYDAY and COUNT expands into occurrences`() {
        val ics = """
            BEGIN:VEVENT
            UID:weekly
            DTSTART:20260921T180000Z
            DURATION:PT45M
            RRULE:FREQ=WEEKLY;BYDAY=MO,WE,FR;COUNT=6
            SUMMARY:Easy run
            END:VEVENT
        """.trimIndent()
        val events = IcsParser.parse(ics, "x", utc, today)
        assertEquals(6, events.size)
        val dates = events.map { it.localDate(utc) }
        assertEquals(
            listOf(21, 23, 25, 28, 30, 2),
            dates.map { it.dayOfMonth },
        )
        assertTrue(events.all { it.uid == "weekly" })
        assertEquals(Duration.ofMinutes(45), Duration.between(events[0].start, events[0].end))
    }

    @Test
    fun `daily RRULE with UNTIL stops at the boundary`() {
        val ics = "BEGIN:VEVENT\nDTSTART:20260921T060000Z\nRRULE:FREQ=DAILY;UNTIL=20260925T235959Z\nSUMMARY:Stretch\nEND:VEVENT"
        val events = IcsParser.parse(ics, "x", utc, today)
        assertEquals(5, events.size)
    }

    @Test
    fun `duration parser handles days and time parts`() {
        assertEquals(Duration.ofMinutes(90), IcsParser.parseDuration("PT1H30M"))
        assertEquals(Duration.ofDays(1), IcsParser.parseDuration("P1D"))
        assertEquals(Duration.ofDays(7).plusHours(2), IcsParser.parseDuration("P1WT2H"))
    }

    @Test
    fun `a malformed event does not abort the import`() {
        val ics = "BEGIN:VEVENT\nDTSTART:garbage\nEND:VEVENT\nBEGIN:VEVENT\nDTSTART:20260921T060000Z\nSUMMARY:ok\nEND:VEVENT"
        val events = IcsParser.parse(ics, "x", utc, today)
        assertEquals(1, events.size)
        assertEquals("ok", events.single().summary)
    }
}
