package it.avanzi.spese.ui.stats

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.avanzi.spese.R
import it.avanzi.spese.domain.ItalianFormats
import it.avanzi.spese.ui.components.ProgressBar
import it.avanzi.spese.ui.home.Card
import it.avanzi.spese.ui.home.LedgerState
import it.avanzi.spese.ui.home.PageHeader
import it.avanzi.spese.domain.confirmedLines
import it.avanzi.spese.domain.linesIn
import it.avanzi.spese.domain.sumCents
import it.avanzi.spese.ui.components.SegmentedControl
import it.avanzi.spese.ui.theme.LocalReducedMotion
import it.avanzi.spese.ui.theme.SpeseColors
import it.avanzi.spese.ui.theme.SpeseMotion
import it.avanzi.spese.ui.theme.SpeseText
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

private enum class StatsPeriod(val label: Int) { MONTH(R.string.stats_month), YEAR(R.string.stats_year), ALL(R.string.period_all) }

@Composable
fun Statistics(state: LedgerState) {
    var period by rememberSaveable { mutableStateOf(StatsPeriod.MONTH) }
    val spread = state.preferences.spreadRecurring
    val now = YearMonth.now()
    val firstMonth = state.expenses.minOfOrNull { YearMonth.from(it.date) } ?: state.month
    val (from, to) = when (period) {
        StatsPeriod.MONTH -> state.month to state.month
        StatsPeriod.YEAR -> YearMonth.of(state.month.year, 1) to YearMonth.of(state.month.year, 12)
        StatsPeriod.ALL -> minOf(firstMonth, state.month) to maxOf(now, state.month)
    }
    PageHeader(stringResource(R.string.stats)) {
        Text(
            when (period) {
                StatsPeriod.MONTH -> ItalianFormats.monthHeading(state.month)
                StatsPeriod.YEAR -> state.month.year.toString()
                StatsPeriod.ALL -> stringResource(R.string.since, ItalianFormats.monthHeading(from))
            },
            style = SpeseText.MetaMedium, color = SpeseColors.TextMuted,
        )
    }
    SegmentedControl(StatsPeriod.entries, period, { stringResource(it.label) }, { period = it })
    val lines = state.expenses.linesIn(from, to, spread).confirmedLines()
    val total = lines.sumCents()
    val sums = lines.groupBy { it.expense.categoryId }.mapValues { it.value.sumCents() }
    val entries = state.categories.map { it to (sums[it.id] ?: 0L) }.sortedByDescending { it.second }
    val recurring = if (total == 0L) 0 else (lines.filter { it.expense.recurringId != null }.sumCents().toDouble() / total * 100).roundToInt()
    Card {
        Box(Modifier.align(Alignment.CenterHorizontally).size(220.dp), contentAlignment = Alignment.Center) {
            Donut(entries.filter { it.second > 0 }.map { Color(it.first.colorArgb) to (it.second.toDouble() / total).toFloat() })
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.total), style = SpeseText.MetaMedium, color = SpeseColors.TextMuted)
                Text(ItalianFormats.money(total), style = SpeseText.CardTitle.copy(fontSize = 28.sp))
                Text(stringResource(R.string.recurring_share, recurring), style = SpeseText.Meta, color = SpeseColors.TextMuted)
            }
        }
        entries.forEachIndexed { index, (category, value) ->
            val share = if (total == 0L) 0f else (value.toDouble() / total).toFloat()
            val color = Color(category.colorArgb)
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(color, RoundedCornerShape(3.dp)))
                    Text(category.name, Modifier.weight(1f), style = SpeseText.Body)
                    Text(stringResource(R.string.percentage, (share * 100).roundToInt()), style = SpeseText.Meta, color = SpeseColors.TextMuted)
                    Text(ItalianFormats.money(value), style = SpeseText.RowAmount)
                }
                ProgressBar(share, color = color, cascadeIndex = index, height = 4.dp)
            }
        }
    }
    val (barsFrom, barsTo) = when (period) {
        StatsPeriod.YEAR -> from to to
        else -> minOf(firstMonth, state.month.minusMonths(11)) to maxOf(state.month, now)
    }
    MonthlyTrend(state, barsFrom, barsTo, firstMonth)
}

@Composable
private fun Donut(slices: List<Pair<Color, Float>>) {
    val reduced = LocalReducedMotion.current
    val progress = remember(slices) { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(slices, reduced) {
        if (reduced) progress.snapTo(1f)
        else progress.animateTo(1f, tween(1000, easing = SpeseMotion.Standard))
    }
    Canvas(Modifier.size(220.dp)) {
        val stroke = Stroke(22.dp.toPx())
        drawCircle(SpeseColors.Surface2, radius = 88.dp.toPx(), style = stroke)
        val radius = 88.dp.toPx()
        val topLeft = center - Offset(radius, radius)
        var angle = 0f
        val rotation = -150 + 60 * progress.value
        slices.forEach { (color, share) ->
            val sweep = 360 * share
            val gap = if (slices.size == 1) 0f else minOf(3f, sweep * .2f)
            val start = angle + gap / 2
            val visible = minOf(sweep - gap, 360 * progress.value - start)
            if (visible > 0) drawArc(color, rotation + start, visible, false, topLeft, Size(radius * 2, radius * 2), style = stroke)
            angle += sweep
        }
    }
}

@Composable
private fun MonthlyTrend(state: LedgerState, from: YearMonth, to: YearMonth, firstMonth: YearMonth) {
    val months = generateSequence(from) { it.plusMonths(1) }.takeWhile { it <= to }.toList()
    val values = state.expenses.linesIn(from, to, state.preferences.spreadRecurring).confirmedLines()
        .groupBy { it.month }.mapValues { it.value.sumCents() }
    val amounts = months.map { values[it] ?: 0L }
    val max = amounts.maxOrNull()?.coerceAtLeast(1L) ?: 1L
    val now = YearMonth.now()
    val counted = months.indices.filter { months[it] >= firstMonth && months[it] <= now }
    val average = if (counted.isEmpty()) 0L else counted.sumOf { amounts[it] } / counted.size
    val selected = months.indexOf(state.month)
    Card {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.monthly_trend), Modifier.weight(1f), style = SpeseText.CardTitle)
            Text(stringResource(R.string.monthly_average, ItalianFormats.money(average)), style = SpeseText.Meta, color = SpeseColors.TextMuted)
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val gap = 4.dp
            val barWidth = (maxWidth - gap * 11) / 12
            val scroll = rememberScrollState()
            val step = with(LocalDensity.current) { (barWidth + gap).roundToPx() }
            LaunchedEffect(from, to, selected, step) {
                val target = (if (selected >= 0) selected else months.lastIndex) - 11
                scroll.scrollTo(target.coerceAtLeast(0) * step)
            }
            Row(
                Modifier.horizontalScroll(scroll),
                horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.Bottom,
            ) {
                months.forEachIndexed { index, month ->
                    val ratio = (amounts[index].toDouble() / max).toFloat()
                    val reduced = LocalReducedMotion.current
                    val growth = remember(month) { Animatable(if (reduced) ratio else 0f) }
                    LaunchedEffect(ratio, reduced) {
                        if (reduced) growth.snapTo(ratio)
                        else growth.animateTo(ratio, tween(700, (months.size - index).coerceAtMost(12) * 45, SpeseMotion.Standard))
                    }
                    val highlighted = index == selected
                    val color = if (highlighted) SpeseColors.Accent else SpeseColors.TextFaint
                    Column(
                        Modifier.width(barWidth),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            compactEuros(amounts[index]), textAlign = TextAlign.Center, color = color, maxLines = 1, softWrap = false,
                            style = SpeseText.Meta.copy(fontSize = 9.sp),
                        )
                        Canvas(Modifier.fillMaxWidth().height((100.dp * growth.value).coerceAtLeast(4.dp))) {
                            drawRoundRect(
                                if (highlighted) SpeseColors.Accent else SpeseColors.BarMuted,
                                cornerRadius = CornerRadius(6.dp.toPx()),
                            )
                        }
                        Text(
                            month.month.getDisplayName(TextStyle.SHORT, Locale.ITALY).take(3),
                            color = color, maxLines = 1, softWrap = false, style = SpeseText.Meta.copy(fontSize = 10.sp),
                        )
                        Text(
                            if (index == 0 || month.monthValue == 1) "’" + (month.year % 100).toString().padStart(2, '0') else "",
                            color = SpeseColors.TextFaint, maxLines = 1, style = SpeseText.Meta.copy(fontSize = 9.sp),
                        )
                    }
                }
            }
        }
    }
}

/** Importo arrotondato all'euro, in migliaia sopra i 1000 € ("1,2k"). */
internal fun compactEuros(cents: Long): String {
    val euros = Math.round(cents / 100.0)
    return if (euros < 1000) euros.toString()
    else String.format(Locale.ITALY, "%.1fk", euros / 1000.0).replace(",0k", "k")
}
