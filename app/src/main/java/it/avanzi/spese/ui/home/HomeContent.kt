package it.avanzi.spese.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import it.avanzi.spese.R
import it.avanzi.spese.data.CategoryEntity
import it.avanzi.spese.data.ExpenseEntity
import it.avanzi.spese.domain.ExpenseStatus
import it.avanzi.spese.domain.ItalianFormats
import it.avanzi.spese.ui.components.AppBottomSheet
import it.avanzi.spese.ui.components.CategoryTile
import it.avanzi.spese.ui.components.ProgressBar
import it.avanzi.spese.ui.components.SectionHeader
import it.avanzi.spese.ui.icons.SpeseIcons
import it.avanzi.spese.ui.recurring.scheduled
import it.avanzi.spese.ui.theme.LocalReducedMotion
import it.avanzi.spese.ui.theme.SpeseColors
import it.avanzi.spese.ui.theme.SpeseMotion
import it.avanzi.spese.ui.theme.SpeseShapes
import it.avanzi.spese.ui.theme.SpeseText
import it.avanzi.spese.ui.theme.pressScale
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.roundToLong

fun LedgerState.monthExpenses() = expenses.filter { YearMonth.from(it.date) == month }
fun List<ExpenseEntity>.confirmed() = filter { it.status == ExpenseStatus.CONFIRMED }
fun List<ExpenseEntity>.total() = sumOf(ExpenseEntity::amountCents)

@Composable
fun HomeContent(
    state: LedgerState,
    onMonth: () -> Unit,
    onSearch: () -> Unit,
    onAll: () -> Unit,
    onRecurring: () -> Unit,
    highlightedId: Long?,
    updateBanner: @Composable () -> Unit = {},
    onExpense: (ExpenseEntity) -> Unit,
) {
    val today = LocalDate.now()
    val selected = state.monthExpenses()
    val confirmed = selected.confirmed()
    val total = confirmed.total()
    val currentMonth = state.month == YearMonth.from(today)
    val monthName = state.month.month.getDisplayName(java.time.format.TextStyle.FULL, Locale.ITALY)
        .replaceFirstChar { it.titlecase(Locale.ITALY) }
    val monthLabel = stringResource(R.string.change_month, ItalianFormats.monthHeading(state.month))
    val monthInteraction = remember { MutableInteractionSource() }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.heightIn(min = 44.dp).pressScale(monthInteraction)
                .clickable(monthInteraction, indication = null, onClick = onMonth)
                .semantics { contentDescription = monthLabel }.padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(monthName, style = SpeseText.ScreenTitle)
            Icon(SpeseIcons["chevronDown"], null, Modifier.size(20.dp), tint = SpeseColors.TextFaint)
        }
        IconAction("search", stringResource(R.string.search), onSearch)
    }
    updateBanner()
    Card {
        Text(
            stringResource(if (currentMonth) R.string.spent_month else R.string.spent_period),
            color = SpeseColors.TextMuted, style = SpeseText.BodyMedium,
        )
        AnimatedTotal(total)
        val budget = state.preferences.overallBudgetCents
        if (state.preferences.overallBudgetEnabled && budget > 0) {
            val ratio = total.toDouble() / budget
            ProgressBar(
                ratio.toFloat(),
                color = when {
                    ratio > 1 -> SpeseColors.Danger
                    ratio >= .85 -> SpeseColors.Warning
                    else -> SpeseColors.Accent
                },
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.used_budget, (ratio * 100).roundToLong(), ItalianFormats.money(budget)), style = SpeseText.Meta)
                val left = budget - total
                Text(stringResource(if (left >= 0) R.string.remaining else R.string.over_budget, ItalianFormats.money(kotlin.math.abs(left))), style = SpeseText.Meta)
            }
        }
        HorizontalDivider(color = SpeseColors.Divider)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(SpeseIcons["tabRec"], null, Modifier.size(16.dp), tint = SpeseColors.TextMuted)
            Text(
                stringResource(R.string.recurring_charged, ItalianFormats.money(confirmed.filter { it.recurringId != null }.total())),
                style = SpeseText.Meta, color = SpeseColors.TextMuted,
            )
        }
    }
    Card {
        Text(stringResource(R.string.upcoming), style = SpeseText.CardTitle)
        val schedule = if (currentMonth) state.recurrings.scheduled(today.plusDays(1), state.month.atEndOfMonth())
            else emptyList()
        if (schedule.isEmpty()) Text(stringResource(R.string.none_upcoming), color = SpeseColors.TextMuted, style = SpeseText.Meta)
        schedule.take(3).forEach { entry ->
            val category = state.categories.firstOrNull { it.id == entry.recurring.categoryId }
            Row(
                Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (category != null) CategoryTile(entry.recurring.iconKey, Color(category.colorArgb), size = 30.dp, iconSize = 16.dp)
                Text(entry.recurring.name, Modifier.weight(1f), style = SpeseText.BodyMedium)
                Text(
                    if (entry.date == today.plusDays(1)) stringResource(R.string.tomorrow).lowercase(Locale.ITALY) else ItalianFormats.shortDate(entry.date),
                    style = SpeseText.Meta, color = SpeseColors.TextMuted,
                )
                Text(ItalianFormats.money(entry.cents), style = SpeseText.RowAmount)
            }
        }
        Action(stringResource(R.string.all_recurring), onRecurring, icon = "chevronRight")
    }
    WeekCard(state, today)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        SectionHeader(stringResource(R.string.latest), Modifier.weight(1f))
        if (selected.isNotEmpty()) Action(stringResource(R.string.all_expenses), onAll)
    }
    ExpenseGroups(selected.take(20), state.categories, highlightedId, onExpense)
}

@Composable
private fun AnimatedTotal(cents: Long) {
    val reduced = LocalReducedMotion.current
    val start = remember { mutableStateOf(0L) }
    val displayed = remember { mutableStateOf(if (reduced) cents else 0L) }
    val fraction = remember { Animatable(0f) }
    LaunchedEffect(cents, reduced) {
        if (reduced) displayed.value = cents
        else {
            start.value = displayed.value
            fraction.snapTo(0f)
            fraction.animateTo(1f, tween(900, easing = Easing { progress ->
                1f - (1f - progress) * (1f - progress) * (1f - progress)
            })) {
                displayed.value = if (value == 1f) cents else
                    (start.value + (cents.toDouble() - start.value) * value).roundToLong()
            }
        }
    }
    val formatted = ItalianFormats.money(if (reduced) cents else displayed.value)
    Row(verticalAlignment = Alignment.Bottom) {
        Text(formatted.substringBefore(','), style = SpeseText.Total, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
        Text("," + formatted.substringAfter(','), Modifier.padding(bottom = 6.dp), style = SpeseText.TotalSuffix, color = SpeseColors.TextMuted, maxLines = 1)
    }
}

@Composable
private fun WeekCard(state: LedgerState, today: LocalDate) {
    val active = state.month == YearMonth.from(today)
    val reference = if (active) today else state.month.atEndOfMonth()
    val monday = reference.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val weekdays = (0L..6L).map(monday::plusDays)
    val totals = state.expenses.confirmed().filter { it.date in weekdays }
        .groupBy(ExpenseEntity::date).mapValues { it.value.total() }
    val maximum = totals.values.maxOrNull()?.coerceAtLeast(1) ?: 1
    Card {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                if (active) stringResource(R.string.this_week) else stringResource(R.string.week_starting, ItalianFormats.shortDate(monday)),
                style = SpeseText.CardTitle,
            )
            Text(ItalianFormats.money(totals.values.sum()), style = SpeseText.RowAmount)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            weekdays.forEachIndexed { index, date ->
                val total = totals[date] ?: 0
                val amount = total.toFloat() / maximum
                val future = date > today
                val reduced = LocalReducedMotion.current
                val animation = remember(date) { Animatable(if (reduced) amount else 0f) }
                LaunchedEffect(amount, reduced) {
                    if (reduced) animation.snapTo(amount)
                    else animation.animateTo(amount, tween(700, index * 45, SpeseMotion.Standard))
                }
                val label = date.dayOfWeek.getDisplayName(java.time.format.TextStyle.NARROW, Locale.ITALY)
                val spoken = stringResource(R.string.day_amount, ItalianFormats.dayHeading(date), ItalianFormats.money(total))
                Column(
                    Modifier.weight(1f).semantics { contentDescription = spoken },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Canvas(Modifier.fillMaxWidth().height(48.dp)) {
                        val h = if (future) size.height else (size.height * animation.value).coerceAtLeast(4.dp.toPx())
                        val stroke = 1.dp.toPx()
                        if (future) {
                            drawRoundRect(
                                SpeseColors.Border, topLeft = Offset(stroke, stroke),
                                size = Size((size.width - 2 * stroke).coerceAtLeast(0f), h - 2 * stroke),
                                cornerRadius = CornerRadius(4.dp.toPx()),
                                style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
                            )
                        } else {
                            drawRoundRect(
                                if (date == today) SpeseColors.Accent else SpeseColors.BarMuted,
                                topLeft = Offset(0f, size.height - h), size = Size(size.width, h),
                                cornerRadius = CornerRadius(4.dp.toPx()),
                            )
                        }
                    }
                    Text(label, style = SpeseText.Meta, color = if (date == today) SpeseColors.Accent else SpeseColors.TextFaint)
                }
            }
        }
    }
}

@Composable
fun ExpenseGroups(
    expenses: List<ExpenseEntity>,
    categories: List<CategoryEntity>,
    highlightedId: Long? = null,
    onExpense: (ExpenseEntity) -> Unit,
) {
    val today = LocalDate.now()
    val categoryById = categories.associateBy(CategoryEntity::id)
    if (expenses.isEmpty()) Text(stringResource(R.string.no_expenses), color = SpeseColors.TextMuted, style = SpeseText.Body)
    expenses.groupBy(ExpenseEntity::date).forEach { (date, rows) ->
        val title = when (date) {
            today -> stringResource(R.string.today)
            today.minusDays(1) -> stringResource(R.string.yesterday)
            else -> ItalianFormats.dayHeading(date)
        }
        SectionHeader(title, trailing = ItalianFormats.money(rows.confirmed().total()))
        Column(Modifier.fillMaxWidth().clip(SpeseShapes.Group).background(SpeseColors.Surface)) {
            rows.forEachIndexed { index, row ->
                val category = categoryById[row.categoryId] ?: return@forEachIndexed
                if (index > 0) HorizontalDivider(color = SpeseColors.Divider)
                ExpenseRow(row, category, row.id == highlightedId, onExpense)
            }
        }
    }
}

@Composable
private fun ExpenseRow(
    expense: ExpenseEntity, category: CategoryEntity, highlighted: Boolean,
    onExpense: (ExpenseEntity) -> Unit,
) {
    val reduced = LocalReducedMotion.current
    val intro = remember(expense.id) { Animatable(if (highlighted && !reduced) 0f else 1f) }
    LaunchedEffect(highlighted, reduced) {
        if (highlighted && !reduced) {
            intro.snapTo(0f)
            intro.animateTo(1f, tween(1100, easing = SpeseMotion.Standard))
        } else intro.snapTo(1f)
    }
    val pending = expense.status == ExpenseStatus.PENDING
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).graphicsLayer {
            translationY = -14.dp.toPx() * (1 - intro.value)
            scaleX = .97f + .03f * intro.value
            scaleY = scaleX
        }.background(SpeseColors.Accent.copy(alpha = .2f * (1 - intro.value)))
            .then(if (pending) Modifier.pressScale(interaction, .98f)
                .clickable(interaction, indication = null) { onExpense(expense) } else Modifier)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CategoryTile(category.iconKey, Color(category.colorArgb))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(expense.description ?: category.name, style = SpeseText.BodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (expense.recurringId != null) {
                    Icon(SpeseIcons["tabRec"], null, Modifier.size(12.dp), tint = SpeseColors.TextFaint)
                }
                val time = expense.time?.let(ItalianFormats::time) ?: stringResource(R.string.automatic)
                Text(stringResource(R.string.expense_meta, category.name, time), color = SpeseColors.TextFaint, style = SpeseText.Meta)
            }
            if (pending) Text(stringResource(R.string.pending), color = SpeseColors.Warning, style = SpeseText.MetaMedium)
        }
        Text(ItalianFormats.money(-expense.amountCents), style = SpeseText.RowAmount)
    }
}

@Composable
fun ExpenseSearch(state: LedgerState, search: Boolean, onBack: () -> Unit, onExpense: (ExpenseEntity) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    PageHeader(stringResource(if (search) R.string.search else R.string.latest), onBack)
    if (search) Field(query, { query = it }, stringResource(R.string.search), Modifier.fillMaxWidth())
    val rows = state.monthExpenses().filter { expense ->
        val category = state.categories.firstOrNull { it.id == expense.categoryId }
        query.isBlank() || expense.description.orEmpty().contains(query, ignoreCase = true) ||
            category?.name.orEmpty().contains(query, ignoreCase = true) || ItalianFormats.money(expense.amountCents).contains(query)
    }
    if (rows.isEmpty() && query.isNotBlank()) {
        Text(stringResource(R.string.no_search_results), color = SpeseColors.TextMuted)
    } else ExpenseGroups(rows, state.categories, onExpense = onExpense)
}

@Composable
fun MonthPicker(visible: Boolean, month: YearMonth, onDismiss: () -> Unit, onSelect: (YearMonth) -> Unit) {
    AppBottomSheet(visible, stringResource(R.string.change_month, ItalianFormats.monthHeading(month)), onDismiss) {
        var year by rememberSaveable(month) { mutableIntStateOf(month.year) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconAction("back", stringResource(R.string.year_before), { year-- })
            Text(year.toString(), style = SpeseText.CardTitle)
            IconAction("chevronRight", stringResource(R.string.year_after), { year++ })
        }
        Month.entries.chunked(3).forEach { group ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                group.forEach { entry ->
                    val option = YearMonth.of(year, entry)
                    Action(
                        entry.getDisplayName(java.time.format.TextStyle.SHORT, Locale.ITALY),
                        { onSelect(option) }, Modifier.weight(1f), primary = option == month,
                    )
                }
            }
        }
    }
}
