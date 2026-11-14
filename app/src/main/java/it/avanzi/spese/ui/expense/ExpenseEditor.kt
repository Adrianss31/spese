package it.avanzi.spese.ui.expense

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import it.avanzi.spese.R
import it.avanzi.spese.data.CategoryEntity
import it.avanzi.spese.domain.AmountInput
import it.avanzi.spese.domain.Frequency
import it.avanzi.spese.domain.ItalianFormats
import it.avanzi.spese.ui.components.AmountDisplay
import it.avanzi.spese.ui.components.CategoryChip
import it.avanzi.spese.ui.components.Keypad
import it.avanzi.spese.ui.components.SegmentedControl
import it.avanzi.spese.ui.components.SwitchRow
import it.avanzi.spese.ui.home.Action
import it.avanzi.spese.ui.home.ExpandOptions
import it.avanzi.spese.ui.home.Field
import it.avanzi.spese.ui.theme.SpeseColors
import it.avanzi.spese.ui.theme.SpeseShapes
import it.avanzi.spese.ui.theme.SpeseText
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

data class ExpenseDraft(
    val cents: Long,
    val category: CategoryEntity,
    val description: String,
    val date: LocalDate,
    val frequency: Frequency?,
)

@Composable
fun ExpenseEditor(
    categories: List<CategoryEntity>,
    saving: Boolean,
    onSave: (ExpenseDraft) -> Unit,
    compact: Boolean = false,
    onCancel: (() -> Unit)? = null,
    initialCents: Long? = null,
    initialCategory: Long? = null,
    initialDescription: String = "",
    initialDate: LocalDate = LocalDate.now(),
    confirmation: Boolean = false,
    allowRepeat: Boolean = true,
    onDelete: (() -> Unit)? = null,
) {
    var amount by rememberSaveable {
        mutableStateOf(initialCents?.let { BigDecimal.valueOf(it, 2).toPlainString().replace('.', ',') }
            ?.takeIf { it.substringBefore(',').length <= 5 } ?: "")
    }
    var categoryId by rememberSaveable { mutableLongStateOf(initialCategory ?: 0) }
    var description by rememberSaveable { mutableStateOf(initialDescription) }
    var day by rememberSaveable { mutableLongStateOf(initialDate.toEpochDay()) }
    var repeat by rememberSaveable { mutableStateOf(false) }
    var frequency by rememberSaveable { mutableStateOf(Frequency.MONTHLY) }
    var bump by remember { mutableIntStateOf(0) }
    var shake by remember { mutableIntStateOf(0) }
    val date = LocalDate.ofEpochDay(day)
    val input = remember(amount) { AmountInput.restore(amount) }
    val category = categories.firstOrNull { it.id == categoryId } ?: categories.firstOrNull()
    AmountDisplay(input, bump, shake, Modifier.fillMaxWidth())

    if (!confirmation) {
        CategoryChips(categories, category?.id) { categoryId = it }
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom,
        ) {
            Field(
                description, { description = it }, stringResource(if (compact) R.string.description_optional else R.string.description),
                Modifier.weight(1f), stringResource(R.string.description_hint),
            )
            if (!compact) {
                DateAction(date, stringResource(R.string.date)) { day = it.toEpochDay() }
            }
        }
        if (!compact && allowRepeat) {
            Column(
                Modifier.fillMaxWidth().background(SpeseColors.Bg, SpeseShapes.Key)
                    .padding(horizontal = 14.dp),
            ) {
                SwitchRow(stringResource(R.string.repeat), repeat, { repeat = it }, iconKey = "tabRec")
                ExpandOptions(repeat) {
                    Column(
                        Modifier.padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SegmentedControl(
                            listOf(Frequency.WEEKLY, Frequency.MONTHLY, Frequency.YEARLY),
                            frequency, { stringResource(it.label()) }, { frequency = it },
                        )
                        val hint = when (frequency) {
                            Frequency.WEEKLY -> stringResource(
                                R.string.repeat_week, date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ITALY),
                            )
                            Frequency.YEARLY -> stringResource(
                                R.string.repeat_year, date.dayOfMonth,
                                date.month.getDisplayName(TextStyle.FULL, Locale.ITALY),
                            )
                            else -> stringResource(R.string.repeat_month, date.dayOfMonth)
                        }
                        Text(hint, color = SpeseColors.TextMuted, style = SpeseText.Meta)
                    }
                }
            }
        }
    }
    Keypad(
        onKey = {
            val next = input.press(it)
            if (next == null) shake++
            else {
                amount = next.save()
                bump++
            }
        },
        keyHeight = if (compact) 50.dp else 54.dp,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (onCancel != null) Action(stringResource(R.string.undo), onCancel, Modifier.weight(1f))
        if (onDelete != null) Action(stringResource(R.string.delete), onDelete, danger = true, enabled = !saving)
        Action(
            stringResource(when {
                confirmation -> R.string.confirm
                onDelete != null -> R.string.save
                compact -> R.string.save
                else -> R.string.save_expense
            }),
            onClick = {
                if (input.cents <= 0 || category == null) shake++
                else onSave(ExpenseDraft(input.cents, category, description, date, frequency.takeIf { repeat && allowRepeat }))
            },
            Modifier.weight(1f), primary = true, enabled = !saving,
            dimmed = input.cents == 0L || category == null, icon = "check",
        )
    }
}

@Composable
fun CategoryChips(categories: List<CategoryEntity>, selected: Long?, onSelect: (Long) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        categories.forEach { category ->
            CategoryChip(
                category.name, category.iconKey, Color(category.colorArgb), category.id == selected,
                { onSelect(category.id) },
            )
        }
    }
}

@Composable
fun DateAction(date: LocalDate, label: String, onChange: (LocalDate) -> Unit) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = SpeseColors.TextMuted, style = SpeseText.Meta)
        Action(
            if (date == LocalDate.now()) stringResource(R.string.today) else ItalianFormats.shortDate(date),
            onClick = {
                DatePickerDialog(context, { _, year, month, day ->
                    onChange(LocalDate.of(year, month + 1, day))
                }, date.year, date.monthValue - 1, date.dayOfMonth).show()
            },
            icon = "calendar",
        )
    }
}

fun Frequency.label(plural: Boolean = false) = when (this) {
    Frequency.DAILY -> if (plural) R.string.days else R.string.day
    Frequency.WEEKLY -> if (plural) R.string.weeks else R.string.week
    Frequency.MONTHLY -> if (plural) R.string.months else R.string.month
    Frequency.YEARLY -> if (plural) R.string.years else R.string.year
}
