package com.painani.app.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.painani.app.data.health.HealthConnectManager
import com.painani.app.data.health.HealthSync
import com.painani.app.domain.model.DailyHealth
import com.painani.app.domain.repository.BodyStatsRepository
import com.painani.app.domain.repository.HealthDataRepository
import com.painani.app.domain.repository.SessionRepository
import com.painani.app.domain.stats.BodyStats
import com.painani.app.domain.stats.ConsistencyStats
import com.painani.app.domain.stats.HealthStats
import com.painani.app.domain.stats.RunningStats
import com.painani.app.domain.stats.StatsRange
import com.painani.app.domain.stats.StrengthStats
import com.painani.app.domain.stats.WeekBucket
import com.painani.app.ui.theme.NothingRed
import com.painani.app.ui.theme.Numeral
import com.painani.app.ui.theme.RunColor
import com.painani.app.ui.theme.StrengthColor
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun StatsScreen(
    sessionRepository: SessionRepository,
    bodyStatsRepository: BodyStatsRepository,
    healthDataRepository: HealthDataRepository,
    healthConnect: HealthConnectManager,
    healthSync: HealthSync,
    onOpenSettings: () -> Unit,
    viewModel: StatsViewModel = viewModel(
        factory = StatsViewModel.Factory(sessionRepository, bodyStatsRepository, healthDataRepository, healthConnect, healthSync),
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedExercise by viewModel.selectedExercise.collectAsStateWithLifecycle()

    // Coming back from Settings (where Health Connect may have just been granted) should show data.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshHealth() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("STATS", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                // Settings lives behind this: small, muted, out of the way.
                IconButton(onClick = onOpenSettings, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Outlined.Settings,
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
        item { RangeSelector(state.range, viewModel::setRange) }

        if (!state.loaded) return@LazyColumn

        item {
            Section("CONSISTENCY") {
                state.consistency?.let { ConsistencySection(it, state.from, state.today) }
            }
        }
        item {
            Section("RUNNING") {
                val r = state.running
                if (r == null || r.count == 0) Empty("No runs in this range")
                else RunningSection(r, state.consistency?.weeks.orEmpty())
            }
        }
        item {
            Section("STRENGTH") {
                val s = state.strength
                if (s == null || s.workouts == 0) Empty("No strength sessions in this range")
                else StrengthSection(s, state.consistency?.weeks.orEmpty(), selectedExercise, viewModel::selectExercise)
            }
        }
        item {
            Section("BODY") {
                val b = state.body
                if (b == null || b.weights.isEmpty()) Empty("No weigh-ins in this range")
                else BodySection(b)
            }
        }
        item {
            Section("ACTIVITY & SLEEP") {
                when {
                    !state.healthConnected -> {
                        Empty("Connect Health Connect to see steps, sleep and resting heart rate from your watch or Samsung Health.")
                        OutlinedButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) { Text("OPEN SETTINGS") }
                    }
                    state.health?.days.isNullOrEmpty() -> Empty("Nothing synced yet. Health Connect data appears here after the first sync.")
                    else -> HealthSection(state.health!!, state.range, state.from, state.today)
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

// --- chrome ---------------------------------------------------------------------------

@Composable
private fun RangeSelector(range: StatsRange, onSelect: (StatsRange) -> Unit) {
    val ranges = StatsRange.entries
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        ranges.forEachIndexed { i, r ->
            SegmentedButton(
                selected = range == r,
                onClick = { onSelect(r) },
                shape = SegmentedButtonDefaults.itemShape(index = i, count = ranges.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = MaterialTheme.colorScheme.primary,
                    activeContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                icon = {},
            ) { Text(r.label, style = MaterialTheme.typography.labelMedium) }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text(title, style = MaterialTheme.typography.labelMedium, color = NothingRed)
        content()
    }
}

@Composable
private fun Caption(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Empty(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun StatRow(vararg stats: Pair<String, String>) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        stats.forEach { (label, value) -> Stat(label, value) }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = Numeral.copy(fontSize = MaterialTheme.typography.headlineSmall.fontSize), maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Legend swatch + label, for stacked charts. */
@Composable
private fun Legend(vararg entries: Pair<String, androidx.compose.ui.graphics.Color>) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        entries.forEach { (label, color) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(8.dp).background(color))
                Spacer(Modifier.width(4.dp))
                Caption(label)
            }
        }
    }
}

// --- sections -------------------------------------------------------------------------

@Composable
private fun ConsistencySection(c: ConsistencyStats, from: LocalDate, today: LocalDate) {
    StatRow(
        "STREAK" to "${c.streakWeeks}w",
        "THIS WEEK" to c.sessionsThisWeek.toString(),
        "AVG/WEEK" to String.format(Locale.getDefault(), "%.1f", c.avgPerWeek),
        "HOURS" to (c.totalMinutes / 60).toString(),
    )

    Caption("SESSIONS PER WEEK")
    BarChart(
        bars = c.weeks.map { w -> Bar(listOf(BarSegment(w.runs.toFloat(), RunColor), BarSegment(w.strength.toFloat(), StrengthColor))) },
        reference = c.avgPerWeek.toFloat().takeIf { it > 0 },
        xLabel = weekLabels(c.weeks),
    )
    Legend("RUN" to RunColor, "STRENGTH" to StrengthColor)

    Caption("TRAINING DAYS · ${c.totalSessions} SESSIONS")
    ActivityHeatmap(days = c.days, from = from, today = today)
}

@Composable
private fun RunningSection(r: RunningStats, weeks: List<WeekBucket>) {
    StatRow(
        "KM" to fmt1(r.totalKm),
        "RUNS" to r.count.toString(),
        "PACE" to (r.avgPaceSecPerKm?.let { fmtPace(it) } ?: "--"),
        "AVG HR" to (r.avgHr?.toString() ?: "--"),
    )

    Caption("KM PER WEEK")
    BarChart(
        bars = weeks.map { Bar(it.runKm.toFloat(), RunColor) },
        yLabel = { fmt1(it.toDouble()) },
        xLabel = weekLabels(weeks),
    )

    if (r.runs.count { it.paceSecPerKm != null } >= 2) {
        Caption("PACE PER RUN · LONGEST ${fmt1(r.longestKm)} KM")
        LineChart(
            values = r.runs.map { it.paceSecPerKm?.toFloat() },
            yLabel = { fmtPace(it.toDouble()) },
            xLabel = runLabels(r.runs.map { it.date }),
            lowerIsBetter = true,
        )
    }
    if (r.runs.count { it.avgHr != null } >= 2) {
        Caption("AVG HEART RATE PER RUN")
        LineChart(
            values = r.runs.map { it.avgHr?.toFloat() },
            xLabel = runLabels(r.runs.map { it.date }),
            lowerIsBetter = true,
        )
    }
}

@Composable
private fun StrengthSection(
    s: StrengthStats,
    weeks: List<WeekBucket>,
    selectedExercise: String?,
    onSelect: (String) -> Unit,
) {
    StatRow(
        "WORKOUTS" to s.workouts.toString(),
        "SETS" to s.totalSets.toString(),
        "VOLUME" to fmtTonnes(s.totalVolumeKg),
    )

    Caption("VOLUME PER WEEK")
    BarChart(
        bars = weeks.map { Bar(it.volumeKg.toFloat(), StrengthColor) },
        yLabel = { fmtTonnes(it.toDouble()) },
        xLabel = weekLabels(weeks),
    )

    if (s.exercises.isEmpty()) return
    val current = s.exercises.firstOrNull { it.name == selectedExercise } ?: s.exercises.first()

    Caption("PROGRESSION")
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(s.exercises, key = { it.name }) { ex ->
            FilterChip(
                selected = ex.name == current.name,
                onClick = { onSelect(ex.name) },
                label = { Text(ex.name.uppercase(), style = MaterialTheme.typography.labelSmall) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        }
    }
    StatRow(
        "BEST SET" to "${fmtKg(current.bestWeightKg)} kg",
        "EST. 1RM" to "${fmtKg(current.bestOneRm)} kg",
        "SESSIONS" to current.sessions.toString(),
    )
    if (current.points.size >= 2 && current.bestWeightKg > 0) {
        Caption("TOP SET WEIGHT · ${current.name.uppercase()}")
        LineChart(
            values = current.points.map { it.topWeightKg.toFloat() },
            yLabel = { fmtKg(it.toDouble()) },
            xLabel = runLabels(current.points.map { it.date }),
        )
    } else {
        Empty("Log ${current.name} a couple more times to see a trend.")
    }
}

@Composable
private fun BodySection(b: BodyStats) {
    val asc = b.ascending
    StatRow(
        "KG" to fmtKg(asc.last().weightKg),
        "CHANGE" to (b.changeKg?.let { String.format(Locale.getDefault(), "%+.1f", it) } ?: "--"),
        "WEIGH-INS" to asc.size.toString(),
    )
    if (asc.size >= 2) {
        Caption("WEIGHT")
        LineChart(
            values = asc.map { it.weightKg.toFloat() },
            yLabel = { fmtKg(it.toDouble()) },
            xLabel = runLabels(asc.map { it.at.atZone(ZoneId.systemDefault()).toLocalDate() }),
        )
    }
}

@Composable
private fun HealthSection(h: HealthStats, range: StatsRange, from: LocalDate, today: LocalDate) {
    StatRow(
        "STEPS/DAY" to (h.avgSteps?.let { fmtK(it) } ?: "--"),
        "SLEEP" to (h.avgSleepMinutes?.let { fmtHm(it) } ?: "--"),
        "REST HR" to (h.avgRestingHr?.toString() ?: "--"),
        "ACTIVE" to (h.avgActiveMinutes?.let { "${it}m" } ?: "--"),
    )

    // Daily bars up to 12 weeks; beyond that a bar per day is a hairline, so average by week.
    val daily = range.days <= StatsRange.W12.days
    val dates = if (daily) generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(today) }.toList()
    else generateSequence(from) { it.plusWeeks(1) }.takeWhile { !it.isAfter(today) }.toList()
    val byDate = h.days.associateBy { it.date }
    fun bucket(start: LocalDate): List<DailyHealth> =
        if (daily) listOfNotNull(byDate[start])
        else (0..6L).mapNotNull { byDate[start.plusDays(it)] }
    val labels: (Int) -> String? = if (daily) dayLabels(dates) else weekLabelsFor(dates)
    val on = MaterialTheme.colorScheme.onSurface

    Caption(if (daily) "STEPS" else "STEPS · WEEKLY AVERAGE")
    BarChart(
        bars = dates.map { d ->
            val v = bucket(d).mapNotNull { it.steps }.takeIf { it.isNotEmpty() }?.average()?.toFloat() ?: 0f
            Bar(v, on)
        },
        reference = h.avgSteps?.toFloat(),
        yLabel = { fmtK(it.toLong()) },
        xLabel = labels,
    )

    if (h.days.any { it.sleepMinutes != null }) {
        Caption(if (daily) "SLEEP" else "SLEEP · WEEKLY AVERAGE")
        val deep = on
        val rem = on.copy(alpha = 0.65f)
        val light = on.copy(alpha = 0.35f)
        BarChart(
            bars = dates.map { d ->
                val nights = bucket(d).filter { it.sleepMinutes != null }
                if (nights.isEmpty()) return@map Bar(0f, light)
                val staged = nights.filter { it.sleepDeepMinutes != null }
                if (staged.size == nights.size) {
                    Bar(
                        listOf(
                            BarSegment(staged.map { it.sleepDeepMinutes!! }.average().toFloat(), deep),
                            BarSegment(staged.map { it.sleepRemMinutes ?: 0 }.average().toFloat(), rem),
                            BarSegment(staged.map { it.sleepLightMinutes ?: 0 }.average().toFloat(), light),
                        )
                    )
                } else {
                    Bar(nights.map { it.sleepMinutes!! }.average().toFloat(), light)
                }
            },
            reference = h.avgSleepMinutes?.toFloat(),
            yLabel = { fmtHm(it.toInt()) },
            xLabel = labels,
        )
        Legend("DEEP" to deep, "REM" to rem, "LIGHT" to light)
        h.latest?.takeIf { it.sleepMinutes != null }?.let { n ->
            val parts = buildList {
                add("Last night ${fmtHm(n.sleepMinutes!!)}")
                n.sleepDeepMinutes?.let { add("deep ${fmtHm(it)}") }
                n.sleepRemMinutes?.let { add("REM ${fmtHm(it)}") }
                n.sleepLightMinutes?.let { add("light ${fmtHm(it)}") }
                n.sleepAwakeMinutes?.takeIf { it > 0 }?.let { add("awake ${it}m") }
            }
            Caption(parts.joinToString(" · ").uppercase())
        }
    }

    val hrSeries = dates.map { d -> bucket(d).mapNotNull { it.restingHr }.takeIf { it.isNotEmpty() }?.average()?.toFloat() }
    if (hrSeries.count { it != null } >= 2) {
        Caption("RESTING HEART RATE")
        LineChart(values = hrSeries, xLabel = labels, lowerIsBetter = true)
    }
}

// --- labels and formatting ------------------------------------------------------------

/** Caption under every n-th weekly bar, so labels never collide. */
private fun weekLabels(weeks: List<WeekBucket>): (Int) -> String? = weekLabelsFor(weeks.map { it.start })

private fun weekLabelsFor(starts: List<LocalDate>): (Int) -> String? {
    val every = when {
        starts.size <= 6 -> 1
        starts.size <= 14 -> 2
        starts.size <= 30 -> 4
        else -> 8
    }
    val fmt = DateTimeFormatter.ofPattern("d MMM")
    return { i -> if (i % every == 0) starts[i].format(fmt).uppercase() else null }
}

private fun dayLabels(dates: List<LocalDate>): (Int) -> String? {
    val every = when {
        dates.size <= 7 -> 1
        dates.size <= 28 -> 7
        else -> 14
    }
    val fmt = DateTimeFormatter.ofPattern("d MMM")
    return { i -> if (i % every == 0) dates[i].format(fmt).uppercase() else null }
}

/** First and last date only; per-run spacing is uneven so intermediate captions would mislead. */
private fun runLabels(dates: List<LocalDate>): (Int) -> String? {
    val fmt = DateTimeFormatter.ofPattern("d MMM")
    return { i ->
        when {
            dates.size == 1 -> dates[0].format(fmt).uppercase()
            i == 0 -> dates[0].format(fmt).uppercase()
            i == dates.lastIndex -> dates[i].format(fmt).uppercase()
            else -> null
        }
    }
}

private fun fmt1(v: Double): String = String.format(Locale.getDefault(), "%.1f", v)

private fun fmtKg(v: Double): String =
    if (v % 1.0 == 0.0) v.toInt().toString() else String.format(Locale.getDefault(), "%.1f", v)

private fun fmtTonnes(kg: Double): String =
    if (kg >= 1000) String.format(Locale.getDefault(), "%.1ft", kg / 1000) else "${kg.toInt()}kg"

private fun fmtK(v: Long): String =
    if (v >= 10_000) String.format(Locale.getDefault(), "%.1fk", v / 1000.0) else "%,d".format(v)

private fun fmtHm(minutes: Int): String = "${minutes / 60}h${"%02d".format(minutes % 60)}"

/** "5:05" — no unit, the caption says it is per km. */
private fun fmtPace(secPerKm: Double): String {
    if (secPerKm <= 0) return "--"
    val m = (secPerKm / 60).toInt()
    val s = (secPerKm % 60).toInt()
    return "%d:%02d".format(m, s)
}
