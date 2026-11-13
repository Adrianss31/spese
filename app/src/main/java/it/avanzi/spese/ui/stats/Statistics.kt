package it.avanzi.spese.ui.stats

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
import it.avanzi.spese.ui.home.confirmed
import it.avanzi.spese.ui.home.monthExpenses
import it.avanzi.spese.ui.home.total
import it.avanzi.spese.ui.theme.LocalReducedMotion
import it.avanzi.spese.ui.theme.SpeseColors
import it.avanzi.spese.ui.theme.SpeseMotion
import it.avanzi.spese.ui.theme.SpeseText
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun Statistics(state: LedgerState) {
    PageHeader(stringResource(R.string.stats)) {
        Text(ItalianFormats.monthHeading(state.month), style = SpeseText.MetaMedium, color = SpeseColors.TextMuted)
    }
    val expenses = state.monthExpenses().confirmed()
    val total = expenses.total()
    val sums = expenses.groupBy { it.categoryId }.mapValues { it.value.total() }
    val entries = state.categories.map { it to (sums[it.id] ?: 0L) }.sortedByDescending { it.second }
    val recurring = if (total == 0L) 0 else (expenses.filter { it.recurringId != null }.total().toDouble() / total * 100).roundToInt()
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
                ProgressBar(share, color = color, height = 4.dp, cascadeIndex = index)
            }
        }
    }
    SixMonths(state)
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
private fun SixMonths(state: LedgerState) {
    val months = (5L downTo 0L).map(state.month::minusMonths)
    val values = state.expenses.confirmed().groupBy { YearMonth.from(it.date) }.mapValues { it.value.total() }
    val amounts = months.map { values[it] ?: 0L }
    val max = amounts.maxOrNull()?.coerceAtLeast(1L) ?: 1L
    val average = amounts.take(5).sum() / 5
    Card {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.six_months), Modifier.weight(1f), style = SpeseText.CardTitle)
            Text(stringResource(R.string.average, ItalianFormats.money(average)), style = SpeseText.Meta, color = SpeseColors.TextMuted)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
            months.forEachIndexed { index, month ->
                val ratio = (amounts[index].toDouble() / max).toFloat()
                val reduced = LocalReducedMotion.current
                val growth = remember(month) { Animatable(if (reduced) ratio else 0f) }
                LaunchedEffect(ratio, reduced) {
                    if (reduced) growth.snapTo(ratio)
                    else growth.animateTo(ratio, tween(700, index * 45, SpeseMotion.Standard))
                }
                val color = if (index == 5) SpeseColors.Accent else SpeseColors.TextFaint
                Column(
                    Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        BigDecimal.valueOf(amounts[index], 2).setScale(0, RoundingMode.HALF_UP).toPlainString(),
                        textAlign = TextAlign.Center, color = color, style = SpeseText.Meta.copy(fontSize = 10.sp),
                    )
                    Canvas(Modifier.fillMaxWidth().height((100.dp * growth.value).coerceAtLeast(4.dp))) {
                        drawRoundRect(if (index == 5) SpeseColors.Accent else SpeseColors.BarMuted, cornerRadius = CornerRadius(8.dp.toPx()))
                    }
                    Text(month.month.getDisplayName(TextStyle.SHORT, Locale.ITALY), color = color, style = SpeseText.Meta)
                }
            }
        }
    }
}
