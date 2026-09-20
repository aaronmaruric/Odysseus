package com.painani.app.domain.stats

import com.painani.app.domain.model.DailyHealth
import com.painani.app.domain.model.Session
import com.painani.app.domain.model.SessionType
import com.painani.app.domain.model.WeightEntry
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** How far back the stats page looks. */
enum class StatsRange(val label: String, val days: Long) {
    W4("4W", 28), W12("12W", 84), M6("6M", 182), Y1("1Y", 365);

    /** First day in range, pulled back to a Monday so weekly buckets are whole weeks. */
    fun start(today: LocalDate): LocalDate =
        today.minusDays(days - 1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
}

/** One Monday-to-Sunday week of training. */
data class WeekBucket(
    val start: LocalDate,
    val runs: Int = 0,
    val strength: Int = 0,
    val runKm: Double = 0.0,
    val volumeKg: Double = 0.0,
    val minutes: Long = 0,
) {
    val sessions: Int get() = runs + strength
}

/** One calendar day, for the consistency heatmap. */
data class DayActivity(val date: LocalDate, val sessions: Int, val minutes: Long)

data class RunPoint(val date: LocalDate, val km: Double, val paceSecPerKm: Double?, val avgHr: Int?)

data class ExercisePoint(val date: LocalDate, val topWeightKg: Double, val estimatedOneRm: Double, val volumeKg: Double)

data class ExerciseProgress(val name: String, val sessions: Int, val points: List<ExercisePoint>) {
    val bestWeightKg: Double get() = points.maxOfOrNull { it.topWeightKg } ?: 0.0
    val bestOneRm: Double get() = points.maxOfOrNull { it.estimatedOneRm } ?: 0.0
}

data class ConsistencyStats(
    val weeks: List<WeekBucket>,
    val days: List<DayActivity>,
    /** Consecutive weeks, ending this week or last, with at least one session. */
    val streakWeeks: Int,
    val sessionsThisWeek: Int,
    val totalSessions: Int,
    val totalMinutes: Long,
) {
    val avgPerWeek: Double get() = if (weeks.isEmpty()) 0.0 else totalSessions.toDouble() / weeks.size
}

data class RunningStats(val runs: List<RunPoint>) {
    val count: Int get() = runs.size
    val totalKm: Double get() = runs.sumOf { it.km }
    val longestKm: Double get() = runs.maxOfOrNull { it.km } ?: 0.0
    /** Distance-weighted, so a long slow run counts more than a short fast one. */
    val avgPaceSecPerKm: Double?
        get() {
            val timed = runs.filter { it.paceSecPerKm != null && it.km > 0 }
            if (timed.isEmpty()) return null
            return timed.sumOf { it.paceSecPerKm!! * it.km } / timed.sumOf { it.km }
        }
    val avgHr: Int? get() = runs.mapNotNull { it.avgHr }.takeIf { it.isNotEmpty() }?.average()?.toInt()
}

data class StrengthStats(
    val workouts: Int,
    val totalSets: Int,
    val totalVolumeKg: Double,
    /** Most-trained first. */
    val exercises: List<ExerciseProgress>,
)

data class BodyStats(val weights: List<WeightEntry>) {
    /** Oldest to newest. */
    val ascending: List<WeightEntry> get() = weights.sortedBy { it.at }
    val changeKg: Double? get() = ascending.takeIf { it.size >= 2 }?.let { it.last().weightKg - it.first().weightKg }
}

data class HealthStats(val days: List<DailyHealth>) {
    val avgSteps: Long? get() = days.mapNotNull { it.steps }.takeIf { it.isNotEmpty() }?.average()?.toLong()
    val avgSleepMinutes: Int? get() = days.mapNotNull { it.sleepMinutes }.takeIf { it.isNotEmpty() }?.average()?.toInt()
    val avgRestingHr: Int? get() = days.mapNotNull { it.restingHr }.takeIf { it.isNotEmpty() }?.average()?.toInt()
    val avgActiveMinutes: Int? get() = days.mapNotNull { it.exerciseMinutes }.takeIf { it.isNotEmpty() }?.average()?.toInt()
    val latest: DailyHealth? get() = days.maxByOrNull { it.date }
}

/** Pure functions that turn the raw logs into the numbers the stats page shows. */
object Stats {

    fun consistency(sessions: List<Session>, from: LocalDate, today: LocalDate): ConsistencyStats {
        val byDate = sessions.groupBy { it.localDate() }
        val days = generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(today) }.map { d ->
            val s = byDate[d].orEmpty()
            DayActivity(d, s.size, s.sumOf { it.durationMillis } / 60_000)
        }.toList()

        val thisWeekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val weeks = generateSequence(from) { it.plusWeeks(1) }.takeWhile { !it.isAfter(thisWeekStart) }.map { start ->
            val end = start.plusDays(6)
            val inWeek = sessions.filter { val d = it.localDate(); !d.isBefore(start) && !d.isAfter(end) }
            WeekBucket(
                start = start,
                runs = inWeek.count { it.type == SessionType.RUN },
                strength = inWeek.count { it.type == SessionType.STRENGTH },
                runKm = inWeek.filter { it.type == SessionType.RUN }.sumOf { (it.distanceMeters ?: 0.0) / 1000 },
                volumeKg = inWeek.sumOf { it.volumeKg() },
                minutes = inWeek.sumOf { it.durationMillis } / 60_000,
            )
        }.toList()

        // Streak: walk back from this week; an empty current week does not break it (the week is
        // not over), but an empty previous week does.
        var streak = 0
        val reversed = weeks.asReversed()
        val startIndex = if (reversed.firstOrNull()?.sessions == 0) 1 else 0
        for (w in reversed.drop(startIndex)) {
            if (w.sessions > 0) streak++ else break
        }

        return ConsistencyStats(
            weeks = weeks,
            days = days,
            streakWeeks = streak,
            sessionsThisWeek = weeks.lastOrNull()?.sessions ?: 0,
            totalSessions = sessions.size,
            totalMinutes = sessions.sumOf { it.durationMillis } / 60_000,
        )
    }

    fun running(sessions: List<Session>): RunningStats =
        RunningStats(
            sessions.filter { it.type == SessionType.RUN }.sortedBy { it.startedAt }.map { s ->
                val km = (s.distanceMeters ?: 0.0) / 1000
                RunPoint(
                    date = s.localDate(),
                    km = km,
                    paceSecPerKm = if (km > 0) (s.durationMillis / 1000.0) / km else null,
                    avgHr = s.avgHeartRate,
                )
            }
        )

    fun strength(sessions: List<Session>): StrengthStats {
        val workouts = sessions.filter { it.type == SessionType.STRENGTH }
        val allSets = workouts.flatMap { w -> w.sets.map { w.localDate() to it } }
        val exercises = allSets
            .groupBy { it.second.exercise.name }
            .map { (name, rows) ->
                val points = rows.groupBy { it.first }.toSortedMap().map { (date, sets) ->
                    val loaded = sets.map { it.second }.filter { it.weightKg > 0 }
                    ExercisePoint(
                        date = date,
                        topWeightKg = loaded.maxOfOrNull { it.weightKg } ?: 0.0,
                        estimatedOneRm = loaded.maxOfOrNull { epley(it.weightKg, it.reps) } ?: 0.0,
                        volumeKg = sets.sumOf { it.second.weightKg * it.second.reps },
                    )
                }
                ExerciseProgress(name, points.size, points)
            }
            .sortedWith(compareByDescending<ExerciseProgress> { it.sessions }.thenBy { it.name })
        return StrengthStats(
            workouts = workouts.size,
            totalSets = allSets.size,
            totalVolumeKg = workouts.sumOf { it.volumeKg() },
            exercises = exercises,
        )
    }

    /** Epley estimate of a one-rep max. A single rep is the lift itself. */
    fun epley(weightKg: Double, reps: Int): Double =
        if (reps <= 1) weightKg else weightKg * (1 + reps / 30.0)

    private fun Session.volumeKg(): Double = sets.sumOf { it.weightKg * it.reps }
}
