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
import it.avanzi.spese.domain.monthlyCents
import it.avanzi.spese.domain.nextOnOrAfter
import it.avanzi.spese.domain.occurrences
import it.avanzi.spese.domain.periodName
import it.avanzi.spese.domain.scheduleLabel
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
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlinx.coroutines.launch

data class ScheduledPayment(val recurring: RecurringEntity, val date: LocalDate, val cents: Long)

fun List<RecurringEntity>.scheduled(from: LocalDate, through: LocalDate): List<ScheduledPayment> =
    filter { it.active }.flatMap { recurring ->
        recurring.occurrences(maxOf(from, recurring.lastGeneratedDate?.plusDays(1) ?: from), through)
            .map { ScheduledPayment(recurring, it, recurring.amountCents) }.toList()
    }.sortedBy { it.date }

@Composable
fun RecurringScreen(state: LedgerState, model: SpeseViewModel) {
    var editing by rememberSaveable { mutableLongStateOf(-1) }
    var perMonth by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val today = LocalDate.now()
    val month = YearMonth.from(today)
    val monthName = month.month.getDisplayName(TextStyle.FULL, Locale.ITALY)
    val active = state.recurrings.filter { it.active }
    val categories = state.categories.associateBy { it.id }
    val monthlyTotal = active.sumOf { it.monthlyCents() }

    PageHeader(stringResource(R.string.recurrings)) {
        Action(stringResource(R.string.new_item), { editing = 0 }, icon = "plus")
    }
    Card {
        Text(stringResource(R.string.estimated_monthly), style = SpeseText.MetaMedium, color = SpeseColors.TextMuted)
        Text(stringResource(R.string.approx, ItalianFormats.money(monthlyTotal)), style = SpeseText.Total.copy(fontSize = 40.sp))
        Text(stringResource(R.string.recurring_explanation, active.size), style = SpeseText.Meta, color = SpeseColors.TextMuted)
        SwitchRow(
            stringResource(R.string.spread_recurring), state.preferences.spreadRecurring,
            { scope.launch { model.preferences.setSpreadRecurring(it) } },
            subtitle = stringResource(R.string.spread_hint),
        )
    }
    if (active.isEmpty()) Text(stringResource(R.string.none_recurring), color = SpeseColors.TextMuted)
    else SegmentedControl(listOf(false, true), perMonth, {
        stringResource(if (it) R.string.view_per_month else R.string.view_per_charge)
    }, { perMonth = it })

    if (perMonth) {
        val rows = active.sortedByDescending { it.monthlyCents() }
        if (rows.isNotEmpty()) SectionHeader(stringResource(R.string.monthly_cost_each), trailing = ItalianFormats.money(monthlyTotal))
        rows.forEach { recurring ->
            val category = categories[recurring.categoryId] ?: return@forEach
            RecurringRow(
                recurring, category,
                amount = stringResource(R.string.per_month, ItalianFormats.money(recurring.monthlyCents())),
                dateLabel = stringResource(R.string.per_charge, ItalianFormats.money(recurring.amountCents)),
                highlighted = false,
            ) { editing = recurring.id }
        }
    } else {
        val upcoming = active.scheduled(today, month.atEndOfMonth())
        val byRecurring = state.recurrings.associateBy { it.id }
        val charged = state.copy(month = month).monthExpenses().mapNotNull { expense ->
            byRecurring[expense.recurringId]?.let { ScheduledPayment(it, expense.date, expense.amountCents) }
        }
        val upcomingIds = upcoming.mapTo(hashSetOf()) { it.recurring.id }
        val future = active.filter { it.id !in upcomingIds }.map { recurring ->
            ScheduledPayment(recurring, recurring.nextCharge(today), recurring.amountCents)
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
                    val amount = ItalianFormats.money(item.cents)
                    RecurringRow(
                        item.recurring, category,
                        amount = if (item.recurring.isVariable && index != 1) stringResource(R.string.estimated_amount, amount) else amount,
                        dateLabel = dateLabel, highlighted = index == 0,
                    ) { editing = item.recurring.id }
                }
            }
        }
    }
    val existing = state.recurrings.firstOrNull { it.id == editing }
    AppBottomSheet(editing >= 0, stringResource(if (editing == 0L) R.string.new_recurring else R.string.edit_recurring), { editing = -1 }) {
        androidx.compose.runtime.key(editing) {
            RecurringEditor(
                existing, state.categories,
                pastCount = existing?.let { recurring -> state.expenses.count { it.recurringId == recurring.id } } ?: 0,
                onSave = { recurring, applyToPast ->
                    scope.launch { model.repository.saveRecurring(recurring, applyToPast) }
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

/** Prossimo addebito non ancora generato. */
fun RecurringEntity.nextCharge(today: LocalDate = LocalDate.now()): LocalDate =
    nextOnOrAfter(maxOf(today, lastGeneratedDate?.plusDays(1) ?: today))

@Composable
private fun RecurringRow(
    recurring: RecurringEntity, category: CategoryEntity, amount: String, dateLabel: String, highlighted: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 66.dp).pressScale(interaction, .98f)
            .clip(SpeseShapes.Row).background(SpeseColors.Surface)
            .clickable(interaction, indication = null, onClick = onClick).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryTile(recurring.iconKey, Color(category.colorArgb))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(recurring.name, style = SpeseText.BodyMedium)
            Text(recurring.scheduleLabel(), style = SpeseText.Meta, color = SpeseColors.TextFaint)
            if (recurring.isVariable) Text(
                stringResource(R.string.variable),
                Modifier.background(Color(0xFF2A2418), SpeseShapes.Pill).padding(horizontal = 7.dp, vertical = 3.dp),
                style = SpeseText.Meta, color = SpeseColors.Warning,
            )
            if (!recurring.autoRecord) Text(stringResource(R.string.reminder_label), style = SpeseText.Meta, color = SpeseColors.TextMuted)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(amount, style = SpeseText.RowAmount)
            Text(dateLabel, style = SpeseText.Meta, color = if (highlighted) SpeseColors.Accent else SpeseColors.TextFaint)
        }
    }
}

@Composable
private fun RecurringEditor(
    existing: RecurringEntity?, categories: List<CategoryEntity>, pastCount: Int,
    onSave: (RecurringEntity, Boolean) -> Unit, onDelete: () -> Unit,
) {
    val today = LocalDate.now()
    val last = existing?.lastGeneratedDate
    val suggested = existing?.nextCharge(today) ?: today
    var name by rememberSaveable { mutableStateOf(existing?.name.orEmpty()) }
    var value by rememberSaveable { mutableStateOf(existing?.amountCents?.let(::asInput).orEmpty()) }
    var categoryId by rememberSaveable { mutableLongStateOf(existing?.categoryId ?: 0) }
    var unit by rememberSaveable { mutableStateOf(existing?.frequency ?: Frequency.MONTHLY) }
    var every by rememberSaveable { mutableIntStateOf(existing?.every ?: 1) }
    var variable by rememberSaveable { mutableStateOf(existing?.isVariable ?: false) }
    var auto by rememberSaveable { mutableStateOf(existing?.autoRecord ?: true) }
    var day by rememberSaveable { mutableLongStateOf(suggested.toEpochDay()) }
    var applyToPast by rememberSaveable { mutableStateOf(false) }

    val category = categories.firstOrNull { it.id == categoryId } ?: categories.firstOrNull()
    val amount = parseCents(value)
    val date = LocalDate.ofEpochDay(day)
    val dateValid = last == null || date > last
    val scheduleChanged = existing == null || unit != existing.frequency || every != existing.every || date != suggested
    val recurring = when {
        category == null -> null
        existing != null && !scheduleChanged -> existing
        else -> {
            val keepsLongerDay = existing?.dayOfMonth?.takeIf {
                date == date.with(TemporalAdjusters.lastDayOfMonth()) && it > date.dayOfMonth
            }
            RecurringEntity(
                id = existing?.id ?: 0, name = "", amountCents = 0, categoryId = category.id, iconKey = category.iconKey,
                frequency = unit, every = every,
                dayOfMonth = (keepsLongerDay ?: date.dayOfMonth).takeIf { unit == Frequency.MONTHLY || unit == Frequency.YEARLY },
                dayOfWeek = date.dayOfWeek.takeIf { unit == Frequency.WEEKLY },
                monthOfYear = date.monthValue.takeIf { unit == Frequency.YEARLY },
                anchorDate = date, active = existing?.active ?: true, lastGeneratedDate = last,
            )
        }
    }?.let {
        it.copy(
            name = name.trim(), amountCents = amount ?: 0, categoryId = category!!.id,
            iconKey = existing?.iconKey?.takeIf { existing.categoryId == category.id } ?: category.iconKey,
            isVariable = variable, autoRecord = auto,
        )
    }
    Field(name, { name = it }, stringResource(R.string.name), Modifier.fillMaxWidth())
    Field(value, { value = it }, stringResource(if (variable) R.string.estimate else R.string.amount), Modifier.fillMaxWidth(), decimal = true)
    SectionHeader(stringResource(R.string.frequency), trailing = periodName(unit, every))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.every), Modifier.weight(1f), style = SpeseText.Body)
        Action("−", { every = (every - 1).coerceAtLeast(1) })
        Text(every.toString(), style = SpeseText.RowAmount)
        IconAction("plus", stringResource(R.string.increase), { every = (every + 1).coerceAtMost(99) })
    }
    SegmentedControl(Frequency.entries, unit, { stringResource(it.label(every > 1)) }, { unit = it })
    DateAction(date, stringResource(if (existing == null) R.string.first_charge else R.string.next_charge)) {
        day = it.toEpochDay()
    }
    if (!dateValid && last != null) {
        Text(stringResource(R.string.after_last_charge, ItalianFormats.shortDate(last)), style = SpeseText.Meta, color = SpeseColors.Danger)
    }
    SectionHeader(stringResource(R.string.category))
    CategoryChips(categories, category?.id) { categoryId = it }
    val renamed = existing != null && (existing.name != name.trim() || existing.categoryId != category?.id)
    if (renamed && pastCount > 0) SwitchRow(
        stringResource(R.string.apply_to_past), applyToPast, { applyToPast = it },
        subtitle = stringResource(R.string.apply_to_past_hint, pastCount),
    )
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
        Text(
            stringResource(R.string.recurring_preview, ItalianFormats.money(recurring.monthlyCents()), ItalianFormats.shortDate(date)),
            style = SpeseText.Meta, color = SpeseColors.TextMuted,
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (existing != null) Action(stringResource(R.string.delete), onDelete, danger = true)
        Action(
            stringResource(R.string.save), { if (recurring != null) onSave(recurring, renamed && applyToPast) },
            Modifier.weight(1f), primary = true,
            enabled = name.isNotBlank() && amount != null && recurring != null && dateValid,
        )
    }
}
