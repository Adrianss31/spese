package it.avanzi.spese.ui.recurring

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.avanzi.spese.R
import it.avanzi.spese.data.CategoryEntity
import it.avanzi.spese.data.RecurringEntity
import it.avanzi.spese.domain.Frequency
import it.avanzi.spese.domain.ItalianFormats
import it.avanzi.spese.domain.nextOnOrAfter
import it.avanzi.spese.domain.occurrences
import it.avanzi.spese.ui.components.AppBottomSheet
import it.avanzi.spese.ui.components.CategoryTile
import it.avanzi.spese.ui.components.SectionHeader
import it.avanzi.spese.ui.components.SegmentedControl
import it.avanzi.spese.ui.components.SwitchRow
import it.avanzi.spese.ui.expense.CategoryChips
import it.avanzi.spese.ui.expense.DateAction
import it.avanzi.spese.ui.expense.label
import it.avanzi.spese.ui.home.Action
import it.avanzi.spese.ui.home.Card
import it.avanzi.spese.ui.home.Field
import it.avanzi.spese.ui.home.IconAction
import it.avanzi.spese.ui.home.LedgerState
import it.avanzi.spese.ui.home.PageHeader
import it.avanzi.spese.ui.home.SpeseViewModel
import it.avanzi.spese.ui.home.monthExpenses
import it.avanzi.spese.ui.home.parseCents
import it.avanzi.spese.ui.manage.asInput
import it.avanzi.spese.ui.theme.SpeseColors
import it.avanzi.spese.ui.theme.SpeseShapes
import it.avanzi.spese.ui.theme.SpeseText
import it.avanzi.spese.ui.theme.pressScale
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlinx.coroutines.launch
import kotlin.math.roundToLong

data class ScheduledPayment(val recurring: RecurringEntity, val date: LocalDate, val cents: Long)

fun List<RecurringEntity>.scheduled(from: LocalDate, through: LocalDate): List<ScheduledPayment> =
    filter { it.active }.flatMap { recurring ->
        recurring.occurrences(maxOf(from, recurring.lastGeneratedDate?.plusDays(1) ?: from), through)
            .map { ScheduledPayment(recurring, it, recurring.amountCents) }.toList()
    }.sortedBy { it.date }

@Composable
fun RecurringScreen(state: LedgerState, model: SpeseViewModel) {
    var editing by rememberSaveable { mutableLongStateOf(-1) }
    val scope = rememberCoroutineScope()
    val today = LocalDate.now()
    val month = YearMonth.from(today)
    val monthName = month.month.getDisplayName(TextStyle.FULL, Locale.ITALY)
    val active = state.recurrings.filter { it.active }
    val categories = state.categories.associateBy { it.id }

    PageHeader(stringResource(R.string.recurrings)) {
        Action(stringResource(R.string.new_item), { editing = 0 }, icon = "plus")
    }
    Card {
        Text(stringResource(R.string.estimated_monthly), style = SpeseText.MetaMedium, color = SpeseColors.TextMuted)
        val total = active.sumOf { it.amountCents * it.frequency.monthlyFactor }.roundToLong()
        Text(stringResource(R.string.approx, ItalianFormats.money(total)), style = SpeseText.Total.copy(fontSize = 40.sp))
        Text(stringResource(R.string.recurring_explanation, active.size), style = SpeseText.Meta, color = SpeseColors.TextMuted)
    }
    if (active.isEmpty()) Text(stringResource(R.string.none_recurring), color = SpeseColors.TextMuted)
    val upcoming = active.scheduled(today, month.atEndOfMonth())
    val byRecurring = state.recurrings.associateBy { it.id }
    val charged = state.copy(month = month).monthExpenses().mapNotNull { expense ->
        byRecurring[expense.recurringId]?.let { ScheduledPayment(it, expense.date, expense.amountCents) }
    }
    val upcomingIds = upcoming.mapTo(hashSetOf()) { it.recurring.id }
    val future = active.filter { it.id !in upcomingIds }.map { recurring ->
        val next = recurring.nextOnOrAfter(maxOf(today, recurring.lastGeneratedDate?.plusDays(1) ?: today))
        ScheduledPayment(recurring, next, recurring.amountCents)
    }.sortedBy { it.date }

    listOf(
        stringResource(R.string.upcoming_month, monthName) to upcoming,
        stringResource(R.string.charged_month, monthName) to charged,
        stringResource(R.string.future_months) to future,
    ).forEachIndexed { index, (title, items) ->
        if (items.isNotEmpty()) {
            SectionHeader(title, trailing = ItalianFormats.money(items.sumOf { it.cents }))
            items.forEach { item ->
                val category = categories[item.recurring.categoryId] ?: return@forEach
                val days = ChronoUnit.DAYS.between(today, item.date)
                val dateLabel = when (index) {
                    1 -> stringResource(R.string.charged_on, item.date.dayOfMonth)
                    2 -> ItalianFormats.shortDate(item.date)
                    else -> when (days) {
                        0L -> stringResource(R.string.today).lowercase(Locale.ITALY)
                        1L -> stringResource(R.string.tomorrow).lowercase(Locale.ITALY)
                        else -> stringResource(R.string.in_days, days)
                    }
                }
                RecurringRow(item, category, dateLabel, index == 0) { editing = item.recurring.id }
            }
        }
    }
    val existing = state.recurrings.firstOrNull { it.id == editing }
    AppBottomSheet(editing >= 0, stringResource(if (editing == 0L) R.string.new_recurring else R.string.edit_recurring), { editing = -1 }) {
        androidx.compose.runtime.key(editing) {
            RecurringEditor(
                existing, state.categories,
                onSave = { recurring ->
                    scope.launch { model.repository.saveRecurring(recurring) }
                    editing = -1
                },
                onDelete = {
                    if (existing != null) scope.launch { model.repository.deleteRecurring(existing.id) }
                    editing = -1
                },
            )
        }
    }
}

@Composable
private fun RecurringRow(
    payment: ScheduledPayment, category: CategoryEntity, dateLabel: String, upcoming: Boolean, onClick: () -> Unit,
) {
    val recurring = payment.recurring
    val interaction = remember { MutableInteractionSource() }
    val day = recurring.dayOfMonth ?: recurring.anchorDate.dayOfMonth
    val detail = when (recurring.frequency) {
        Frequency.WEEKLY -> stringResource(
            R.string.weekly_on, (recurring.dayOfWeek ?: recurring.anchorDate.dayOfWeek).getDisplayName(TextStyle.FULL, Locale.ITALY),
        )
        Frequency.MONTHLY -> stringResource(R.string.monthly_on, day)
        Frequency.BIMONTHLY -> stringResource(R.string.bimonthly_on, day)
        Frequency.YEARLY -> stringResource(
            R.string.yearly_on, day, Month.of(recurring.monthOfYear ?: recurring.anchorDate.monthValue).getDisplayName(TextStyle.FULL, Locale.ITALY),
        )
    }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 66.dp).pressScale(interaction, .98f)
            .clip(SpeseShapes.Row).background(SpeseColors.Surface)
            .clickable(interaction, indication = null, onClick = onClick).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryTile(recurring.iconKey, Color(category.colorArgb))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(recurring.name, style = SpeseText.BodyMedium)
            Text(detail, style = SpeseText.Meta, color = SpeseColors.TextFaint)
            if (recurring.isVariable) Text(
                stringResource(R.string.variable),
                Modifier.background(Color(0xFF2A2418), SpeseShapes.Pill).padding(horizontal = 7.dp, vertical = 3.dp),
                style = SpeseText.Meta, color = SpeseColors.Warning,
            )
            if (!recurring.autoRecord) Text(stringResource(R.string.reminder_label), style = SpeseText.Meta, color = SpeseColors.TextMuted)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val amount = ItalianFormats.money(payment.cents)
            Text(if (recurring.isVariable) stringResource(R.string.estimated_amount, amount) else amount, style = SpeseText.RowAmount)
            Text(dateLabel, style = SpeseText.Meta, color = if (upcoming) SpeseColors.Accent else SpeseColors.TextFaint)
        }
    }
}

@Composable
private fun RecurringEditor(
    existing: RecurringEntity?, categories: List<CategoryEntity>,
    onSave: (RecurringEntity) -> Unit, onDelete: () -> Unit,
) {
    val today = LocalDate.now()
    var name by rememberSaveable { mutableStateOf(existing?.name.orEmpty()) }
    var value by rememberSaveable { mutableStateOf(existing?.amountCents?.let(::asInput).orEmpty()) }
    var categoryId by rememberSaveable { mutableLongStateOf(existing?.categoryId ?: 0) }
    var frequency by rememberSaveable { mutableStateOf(existing?.frequency ?: Frequency.MONTHLY) }
    var variable by rememberSaveable { mutableStateOf(existing?.isVariable ?: false) }
    var auto by rememberSaveable { mutableStateOf(existing?.autoRecord ?: true) }
    var anchor by rememberSaveable { mutableLongStateOf((existing?.anchorDate ?: today).toEpochDay()) }
    var day by rememberSaveable { mutableIntStateOf(existing?.dayOfMonth ?: today.dayOfMonth) }
    var weekday by rememberSaveable { mutableIntStateOf((existing?.dayOfWeek ?: today.dayOfWeek).value) }
    var showAnchor by rememberSaveable { mutableStateOf(false) }

    val category = categories.firstOrNull { it.id == categoryId } ?: categories.firstOrNull()
    val amount = parseCents(value)
    val first = LocalDate.ofEpochDay(anchor)
    val recurring = if (category == null) null else RecurringEntity(
        id = existing?.id ?: 0, name = name.trim(), amountCents = amount ?: 0,
        categoryId = category.id, iconKey = existing?.iconKey?.takeIf { existing.categoryId == category.id } ?: category.iconKey,
        frequency = frequency, dayOfMonth = day.takeUnless { frequency == Frequency.WEEKLY },
        dayOfWeek = DayOfWeek.of(weekday).takeIf { frequency == Frequency.WEEKLY },
        monthOfYear = first.monthValue.takeIf { frequency == Frequency.YEARLY },
        anchorDate = first, isVariable = variable, autoRecord = auto, active = existing?.active ?: true,
        lastGeneratedDate = existing?.lastGeneratedDate,
    )
    Field(name, { name = it }, stringResource(R.string.name), Modifier.fillMaxWidth())
    Field(value, { value = it }, stringResource(if (variable) R.string.estimate else R.string.amount), Modifier.fillMaxWidth(), decimal = true)
    SectionHeader(stringResource(R.string.frequency))
    SegmentedControl(Frequency.entries, frequency, { stringResource(it.label()) }, { frequency = it })
    val weekly = frequency == Frequency.WEEKLY
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(if (weekly) R.string.weekday else R.string.day_of_month), Modifier.weight(1f), style = SpeseText.Body)
        Action("−", {
            if (weekly) weekday = (weekday + 5) % 7 + 1 else day = (day - 1).coerceAtLeast(1)
        })
        Text(if (weekly) DayOfWeek.of(weekday).getDisplayName(TextStyle.SHORT, Locale.ITALY) else day.toString(), style = SpeseText.RowAmount)
        IconAction("plus", stringResource(R.string.increase), {
            if (weekly) weekday = weekday % 7 + 1 else day = (day + 1).coerceAtMost(31)
        })
    }
    if (frequency == Frequency.YEARLY || frequency == Frequency.BIMONTHLY || showAnchor) {
        DateAction(first, stringResource(R.string.first_charge)) { date ->
            anchor = date.toEpochDay()
            day = date.dayOfMonth
            weekday = date.dayOfWeek.value
            showAnchor = true
        }
    }
    SectionHeader(stringResource(R.string.category))
    CategoryChips(categories, category?.id) { categoryId = it }
    SwitchRow(stringResource(R.string.variable_amount), variable, { variable = it }, subtitle = stringResource(R.string.variable_hint))
    SwitchRow(
        stringResource(R.string.automatic_recording), auto, { auto = it },
        subtitle = stringResource(when {
            !auto -> R.string.reminder_hint
            variable -> R.string.variable_auto_hint
            else -> R.string.auto_hint
        }),
    )
    if (recurring != null) {
        val next = recurring.nextOnOrAfter(maxOf(today, recurring.lastGeneratedDate?.plusDays(1) ?: today))
        val monthly = ((amount ?: 0) * frequency.monthlyFactor).roundToLong()
        Text(
            stringResource(R.string.recurring_preview, ItalianFormats.money(monthly), ItalianFormats.shortDate(next)),
            style = SpeseText.Meta, color = SpeseColors.TextMuted,
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (existing != null) Action(stringResource(R.string.delete), onDelete, danger = true)
        Action(
            stringResource(R.string.save), { if (recurring != null) onSave(recurring) },
            Modifier.weight(1f), primary = true, enabled = name.isNotBlank() && amount != null && recurring != null,
        )
    }
}
