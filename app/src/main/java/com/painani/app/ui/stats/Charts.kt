package com.painani.app.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.painani.app.domain.stats.DayActivity
import com.painani.app.ui.theme.NothingRed
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.max

/*
 * Small chart kit for the stats page. Everything is drawn with Canvas so there is no charting
 * dependency, and the look stays in the monochrome + red language of the rest of the app:
 * hairline baseline, no grid, one annotation for the max/last value.
 */

data class BarSegment(val value: Float, val color: Color)

data class Bar(val segments: List<BarSegment>) {
    constructor(value: Float, color: Color) : this(listOf(BarSegment(value, color)))

    val total: Float get() = segments.sumOf { it.value.toDouble() }.toFloat()
}

/**
 * Vertical bars, optionally stacked. [reference] draws a dashed horizontal line (an average or a
 * target) and [xLabel] returns a caption for bar [i] or null to leave the slot blank.
 */
@Composable
fun BarChart(
    bars: List<Bar>,
    modifier: Modifier = Modifier,
    height: Dp = 120.dp,
    reference: Float? = null,
    yLabel: (Float) -> String = { it.toInt().toString() },
    xLabel: (Int) -> String? = { null },
) {
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val baseline = MaterialTheme.colorScheme.outlineVariant
    val faint = MaterialTheme.colorScheme.onSurfaceVariant

    val labels = (0 until bars.size).map(xLabel)
    val band = if (labels.any { it != null }) LABEL_BAND else 0.dp

    Canvas(modifier = modifier.fillMaxWidth().height(height + band)) {
        if (bars.isEmpty()) return@Canvas
        val plotBottom = size.height - band.toPx()
        val maxV = max(bars.maxOf { it.total }, reference ?: 0f).takeIf { it > 0 } ?: 1f
        val topPad = 18.dp.toPx() // room for the max label
        val plotH = plotBottom - topPad - 1.dp.toPx()
        val slot = size.width / bars.size
        val barW = (slot * 0.62f).coerceAtLeast(2.dp.toPx())
        bars.forEachIndexed { i, bar ->
            var y = plotBottom - 1.dp.toPx()
            val x = i * slot + (slot - barW) / 2
            bar.segments.forEach { seg ->
                val h = plotH * (seg.value / maxV)
                drawRect(color = seg.color, topLeft = Offset(x, y - h), size = Size(barW, h))
                y -= h
            }
        }
        drawLine(baseline, Offset(0f, plotBottom - 0.5f), Offset(size.width, plotBottom - 0.5f), strokeWidth = 1.dp.toPx())

        reference?.let { r ->
            val y = plotBottom - 1.dp.toPx() - plotH * (r / maxV)
            drawLine(
                faint, Offset(0f, y), Offset(size.width, y),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
            )
        }

        // Annotate the tallest bar with its value.
        val maxIndex = bars.indices.maxByOrNull { bars[it].total } ?: return@Canvas
        if (bars[maxIndex].total > 0) {
            val text = yLabel(bars[maxIndex].total)
            val layout = measurer.measure(text, labelStyle)
            val cx = maxIndex * slot + slot / 2
            val tx = (cx - layout.size.width / 2).coerceIn(0f, size.width - layout.size.width)
            drawText(layout, topLeft = Offset(tx, 0f))
        }
        drawXLabels(measurer, labelStyle, labels, plotBottom) { i -> i * slot + slot / 2 }
    }
}

/**
 * Line through [values]; nulls leave a gap. The last point is dotted in red and annotated so the
 * current state reads at a glance. When [lowerIsBetter] the annotation shows the min instead of max.
 */
@Composable
fun LineChart(
    values: List<Float?>,
    modifier: Modifier = Modifier,
    height: Dp = 120.dp,
    color: Color = MaterialTheme.colorScheme.onSurface,
    yLabel: (Float) -> String = { it.toInt().toString() },
    xLabel: (Int) -> String? = { null },
    reference: Float? = null,
    lowerIsBetter: Boolean = false,
) {
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val baseline = MaterialTheme.colorScheme.outlineVariant
    val faint = MaterialTheme.colorScheme.onSurfaceVariant

    val labels = (0 until values.size).map(xLabel)
    val band = if (labels.any { it != null }) LABEL_BAND else 0.dp

    Canvas(modifier = modifier.fillMaxWidth().height(height + band)) {
        val plotBottom = size.height - band.toPx()
        val present = values.filterNotNull()
        if (present.isEmpty()) return@Canvas
        var lo = present.min()
        var hi = present.max()
        reference?.let { lo = minOf(lo, it); hi = maxOf(hi, it) }
        if (hi - lo < 1e-3f) { hi += 1f; lo -= 1f }
        // Breathing room so the line never touches the edges.
        val pad = (hi - lo) * 0.12f
        lo -= pad; hi += pad

        val topPad = 18.dp.toPx()
        val plotH = plotBottom - topPad - 1.dp.toPx()
        val n = values.size
        val stepX = if (n > 1) size.width / (n - 1) else 0f
        fun xAt(i: Int) = if (n > 1) i * stepX else size.width / 2
        fun yAt(v: Float) = topPad + plotH * (1 - (v - lo) / (hi - lo))

        drawLine(baseline, Offset(0f, plotBottom - 0.5f), Offset(size.width, plotBottom - 0.5f), strokeWidth = 1.dp.toPx())
        reference?.let { r ->
            drawLine(
                faint, Offset(0f, yAt(r)), Offset(size.width, yAt(r)),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
            )
        }

        // Segments between consecutive non-null points.
        var prev: Offset? = null
        values.forEachIndexed { i, v ->
            val p = v?.let { Offset(xAt(i), yAt(it)) }
            if (p != null && prev != null) {
                drawLine(color, prev!!, p, strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
            }
            prev = p
        }
        // Dots when there are few points; a bare line of two dots looks broken otherwise.
        if (present.size <= 24) {
            values.forEachIndexed { i, v -> v?.let { drawCircle(color, 2.5.dp.toPx(), Offset(xAt(i), yAt(it))) } }
        }

        // Last point in red, annotated.
        val lastIndex = values.indexOfLast { it != null }
        val last = values[lastIndex]!!
        drawCircle(NothingRed, 4.dp.toPx(), Offset(xAt(lastIndex), yAt(last)))
        drawAnnotation(measurer, yLabel(last), labelStyle.copy(color = NothingRed), xAt(lastIndex), yAt(last))

        // Best value in the range, if it is not the last point.
        val bestIndex = values.indices.filter { values[it] != null }
            .let { idx -> if (lowerIsBetter) idx.minByOrNull { values[it]!! } else idx.maxByOrNull { values[it]!! } }
        if (bestIndex != null && bestIndex != lastIndex) {
            val v = values[bestIndex]!!
            drawAnnotation(measurer, yLabel(v), labelStyle, xAt(bestIndex), yAt(v))
        }
        drawXLabels(measurer, labelStyle, labels, plotBottom) { i -> xAt(i) }
    }
}

private fun DrawScope.drawAnnotation(
    measurer: androidx.compose.ui.text.TextMeasurer,
    text: String,
    style: TextStyle,
    x: Float,
    y: Float,
) {
    val layout = measurer.measure(text, style)
    val tx = (x - layout.size.width / 2).coerceIn(0f, size.width - layout.size.width)
    val above = y - layout.size.height - 6.dp.toPx()
    val ty = if (above >= 0) above else y + 6.dp.toPx()
    drawText(layout, topLeft = Offset(tx, ty))
}

/** Height reserved under a plot for x-axis captions. */
private val LABEL_BAND = 18.dp

/**
 * Captions centred on their slot, drawn from the plot's own coordinates so a label may spill into
 * the empty slots beside it instead of being clipped to a narrow column.
 */
private fun DrawScope.drawXLabels(
    measurer: androidx.compose.ui.text.TextMeasurer,
    style: TextStyle,
    labels: List<String?>,
    plotBottom: Float,
    centreOf: (Int) -> Float,
) {
    val top = plotBottom + 4.dp.toPx()
    labels.forEachIndexed { i, text ->
        if (text == null) return@forEachIndexed
        val layout = measurer.measure(text, style)
        val x = (centreOf(i) - layout.size.width / 2).coerceIn(0f, size.width - layout.size.width)
        drawText(layout, topLeft = Offset(x, top))
    }
}

/**
 * GitHub-style grid: one column per week, one row per weekday, cell darkness by minutes trained.
 * Days after today are left blank.
 */
@Composable
fun ActivityHeatmap(days: List<DayActivity>, from: LocalDate, today: LocalDate, modifier: Modifier = Modifier) {
    val empty = MaterialTheme.colorScheme.surfaceContainerHigh
    val on = MaterialTheme.colorScheme.onSurface
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val byDate = days.associateBy { it.date }
    val weekCount = heatmapWeeks(from, today)
    val maxMinutes = days.maxOfOrNull { it.minutes }?.coerceAtLeast(1) ?: 1
    val monthFmt = DateTimeFormatter.ofPattern("MMM")

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val gap = 3.dp
        val cell = ((maxWidth - gap * (weekCount - 1)) / weekCount).coerceIn(3.dp, 16.dp)
        Canvas(modifier = Modifier.fillMaxWidth().height(LABEL_BAND + cell * 7 + gap * 6)) {
            val band = LABEL_BAND.toPx()
            // Month name over the first column that starts in that month.
            var lastMonth = -1
            var lastRight = -1f
            for (w in 0 until weekCount) {
                val start = from.plusWeeks(w.toLong())
                if (start.monthValue == lastMonth) continue
                lastMonth = start.monthValue
                val layout = measurer.measure(start.format(monthFmt).uppercase(), labelStyle)
                val x = w * (cell + gap).toPx()
                if (x < lastRight) continue // would overlap the previous caption
                drawText(layout, topLeft = Offset(x, 0f))
                lastRight = x + layout.size.width + 6.dp.toPx()
            }
            translate(top = band) {
                drawGrid(cell.toPx(), gap.toPx(), weekCount, from, today, byDate, maxMinutes, empty, on)
            }
        }
    }
}

private fun DrawScope.drawGrid(
    cell: Float,
    gap: Float,
    weekCount: Int,
    from: LocalDate,
    today: LocalDate,
    byDate: Map<LocalDate, DayActivity>,
    maxMinutes: Long,
    empty: Color,
    on: Color,
) {
    val rowStep = cell + gap
    for (w in 0 until weekCount) {
        for (d in 0 until 7) {
            val date = from.plusWeeks(w.toLong()).plusDays(d.toLong())
            if (date.isAfter(today)) continue
            val act = byDate[date]
            val color = when {
                act == null || act.sessions == 0 -> empty
                else -> {
                    // Three steps of grey, red when it is the heaviest day.
                    val ratio = act.minutes.toFloat() / maxMinutes
                    when {
                        ratio >= 0.999f -> NothingRed
                        ratio >= 0.6f -> on
                        ratio >= 0.3f -> on.copy(alpha = 0.7f)
                        else -> on.copy(alpha = 0.4f)
                    }
                }
            }
            drawRoundRect(
                color = color,
                topLeft = Offset(w * rowStep, d * rowStep),
                size = Size(cell, cell),
                cornerRadius = CornerRadius(2.dp.toPx()),
            )
        }
    }
}

/** Number of columns the heatmap will draw, for laying out month captions above it. */
fun heatmapWeeks(from: LocalDate, today: LocalDate): Int = (ChronoUnit.DAYS.between(from, today) / 7 + 1).toInt().coerceAtLeast(1)
