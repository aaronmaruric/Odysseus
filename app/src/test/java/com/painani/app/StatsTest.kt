package com.painani.app

import com.painani.app.domain.model.Exercise
import com.painani.app.domain.model.ExerciseSet
import com.painani.app.domain.model.Session
import com.painani.app.domain.model.SessionType
import com.painani.app.domain.stats.Stats
import com.painani.app.domain.stats.StatsRange
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StatsTest {
    private val zone = ZoneId.systemDefault()

    private fun run(date: LocalDate, km: Double, minutes: Long, hr: Int? = null) = Session(
        type = SessionType.RUN,
        startedAt = date.atTime(7, 0).atZone(zone).toInstant(),
        durationMillis = minutes * 60_000,
        distanceMeters = km * 1000,
        avgHeartRate = hr,
    )

    private fun lift(date: LocalDate, name: String, vararg sets: Pair<Int, Double>) = Session(
        type = SessionType.STRENGTH,
        startedAt = date.atTime(18, 0).atZone(zone).toInstant(),
        durationMillis = 45 * 60_000,
        sets = sets.mapIndexed { i, (reps, kg) -> ExerciseSet(exercise = Exercise(name = name), setIndex = i, reps = reps, weightKg = kg) },
    )

    // Wednesday, so "this week" has days before and after it.
    private val today: LocalDate = LocalDate.of(2026, 9, 16)

    @Test
    fun rangeStartsOnMonday() {
        StatsRange.entries.forEach { r ->
            assertEquals(DayOfWeek.MONDAY, r.start(today).dayOfWeek)
        }
        assertEquals(LocalDate.of(2026, 8, 17), StatsRange.W4.start(today))
    }

    @Test
    fun streakCountsBackFromLastCompleteWeekWhenThisWeekIsEmpty() {
        val from = StatsRange.W4.start(today) // 17 Aug
        val sessions = listOf(
            run(LocalDate.of(2026, 8, 18), 5.0, 25),  // week 1
            run(LocalDate.of(2026, 8, 26), 5.0, 25),  // week 2
            run(LocalDate.of(2026, 9, 2), 5.0, 25),   // week 3
            run(LocalDate.of(2026, 9, 8), 5.0, 25),   // week 4 (last week)
        )
        val c = Stats.consistency(sessions, from, today)
        assertEquals(5, c.weeks.size)
        assertEquals(0, c.sessionsThisWeek)
        assertEquals(4, c.streakWeeks)
    }

    @Test
    fun streakBreaksOnAnEmptyPreviousWeek() {
        val from = StatsRange.W4.start(today)
        val sessions = listOf(
            run(LocalDate.of(2026, 8, 18), 5.0, 25),
            run(LocalDate.of(2026, 8, 26), 5.0, 25),
            // 31 Aug - 6 Sep: nothing
            run(LocalDate.of(2026, 9, 8), 5.0, 25),
            run(LocalDate.of(2026, 9, 15), 5.0, 25), // this week
        )
        val c = Stats.consistency(sessions, from, today)
        assertEquals(1, c.sessionsThisWeek)
        assertEquals(2, c.streakWeeks)
        assertEquals(0, c.weeks[2].sessions)
    }

    @Test
    fun weeklyBucketsSplitByType() {
        val from = StatsRange.W4.start(today)
        val sessions = listOf(
            run(LocalDate.of(2026, 8, 18), 5.0, 25),
            run(LocalDate.of(2026, 8, 20), 10.0, 55),
            lift(LocalDate.of(2026, 8, 19), "Squat", 5 to 100.0, 5 to 100.0),
        )
        val w = Stats.consistency(sessions, from, today).weeks.first()
        assertEquals(2, w.runs)
        assertEquals(1, w.strength)
        assertEquals(15.0, w.runKm, 1e-9)
        assertEquals(1000.0, w.volumeKg, 1e-9)
        assertEquals(125L, w.minutes)
    }

    @Test
    fun runningPaceIsDistanceWeighted() {
        val r = Stats.running(
            listOf(
                run(today.minusDays(3), 10.0, 60), // 6:00/km
                run(today.minusDays(1), 2.0, 8),   // 4:00/km
            )
        )
        assertEquals(12.0, r.totalKm, 1e-9)
        // (360*10 + 240*2) / 12 = 340 s/km
        assertEquals(340.0, r.avgPaceSecPerKm!!, 1e-9)
        assertNull(r.avgHr)
    }

    @Test
    fun strengthProgressionTracksTopSetAndEpley() {
        val s = Stats.strength(
            listOf(
                lift(today.minusDays(10), "Bench", 8 to 60.0, 8 to 60.0),
                lift(today.minusDays(3), "Bench", 5 to 70.0, 3 to 75.0),
                lift(today.minusDays(3), "Squat", 5 to 100.0),
            )
        )
        assertEquals(3, s.workouts)
        assertEquals(5, s.totalSets)
        val bench = s.exercises.first()
        assertEquals("Bench", bench.name) // most sessions first
        assertEquals(2, bench.points.size)
        assertEquals(60.0, bench.points[0].topWeightKg, 1e-9)
        assertEquals(75.0, bench.points[1].topWeightKg, 1e-9)
        assertEquals(75.0 * (1 + 3 / 30.0), bench.bestOneRm, 1e-9)
        assertEquals(60.0 * (1 + 8 / 30.0), bench.points[0].estimatedOneRm, 1e-9)
    }

    @Test
    fun epleyLeavesSinglesAlone() {
        assertEquals(100.0, Stats.epley(100.0, 1), 1e-9)
        assertEquals(100.0 * (1 + 10 / 30.0), Stats.epley(100.0, 10), 1e-9)
    }
}
