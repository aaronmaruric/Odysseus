package com.odysseus.app

import com.odysseus.app.domain.model.Split
import com.odysseus.app.ui.calendar.visibleRange
import com.odysseus.app.ui.run.buildManualRun
import java.time.DayOfWeek
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Test

class SplitMathTest {

    @Test
    fun `pace is seconds per km`() {
        val split = Split(index = 0, distanceMeters = 1000.0, durationMillis = 5 * 60_000)
        assertEquals(300.0, split.paceSecPerKm, 0.001)
    }

    @Test
    fun `manual run produces whole km splits plus remainder`() {
        val run = buildManualRun(distanceKm = 5.3, minutes = 26.5, notes = "")
        assertEquals(6, run.splits.size)
        assertEquals(1000.0, run.splits.first().distanceMeters, 0.001)
        assertEquals(300.0, run.splits.last().distanceMeters, 0.5)
        assertEquals(run.durationMillis.toDouble(), run.splits.sumOf { it.durationMillis }.toDouble(), 10.0)
    }

    @Test
    fun `visible range starts on a Monday and spans six weeks`() {
        val (start, end) = YearMonth.of(2026, 9).visibleRange()
        assertEquals(DayOfWeek.MONDAY, start.dayOfWeek)
        assertEquals(41L, end.toEpochDay() - start.toEpochDay())
    }
}
