package it.avanzi.spese.ui.manage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import it.avanzi.spese.R
import it.avanzi.spese.data.CategoryEntity
import it.avanzi.spese.domain.ItalianFormats
import it.avanzi.spese.ui.components.AppBottomSheet
import it.avanzi.spese.ui.components.CategoryTile
import it.avanzi.spese.ui.components.ProgressBar
import it.avanzi.spese.ui.components.SectionHeader
import it.avanzi.spese.ui.components.SwitchRow
import it.avanzi.spese.ui.home.Action
import it.avanzi.spese.ui.home.Card
import it.avanzi.spese.ui.home.Field
import it.avanzi.spese.ui.home.IconAction
import it.avanzi.spese.ui.home.LedgerState
import it.avanzi.spese.ui.home.PageHeader
import it.avanzi.spese.ui.home.SpeseViewModel
import it.avanzi.spese.domain.confirmedLines
import it.avanzi.spese.domain.sumCents
import it.avanzi.spese.ui.home.monthLines
import it.avanzi.spese.ui.home.parseCents
import it.avanzi.spese.ui.theme.SpeseColors
import it.avanzi.spese.ui.theme.SpeseText
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.roundToInt

@Composable
fun Budget(state: LedgerState, model: SpeseViewModel, back: () -> Unit) {
    val scope = rememberCoroutineScope()
    val preferences = state.preferences
    var editing by rememberSaveable { mutableStateOf(false) }
    var input by rememberSaveable { mutableStateOf("") }
    val today = LocalDate.now()
    val month = YearMonth.from(today)
    val recent = state.monthLines(month).confirmedLines()
    val sums = recent.groupBy { it.expense.categoryId }.mapValues { it.value.sumCents() }
    val days = month.lengthOfMonth() - today.dayOfMonth + 1

    PageHeader(stringResource(R.string.budget), back)
    Card {
        SwitchRow(stringResource(R.string.budget_overall), preferences.overallBudgetEnabled, { checked ->
            when {
                !checked -> scope.launch { model.preferences.setBudget(false, preferences.overallBudgetCents) }
                preferences.overallBudgetCents > 0 -> scope.launch { model.preferences.setBudget(true, preferences.overallBudgetCents) }
                else -> { input = ""; editing = true }
            }
        })
        if (preferences.overallBudgetEnabled) {
            val remaining = preferences.overallBudgetCents - recent.sumCents()
            Text(ItalianFormats.money(remaining), style = SpeseText.Total, color = if (remaining < 0) SpeseColors.Danger else SpeseColors.Text)
            Text(
                stringResource(R.string.budget_remaining, ItalianFormats.money(preferences.overallBudgetCents), days, ItalianFormats.money(remaining.coerceAtLeast(0) / days)),
                style = SpeseText.Meta, color = SpeseColors.TextMuted,
            )
            Action(stringResource(R.string.edit), {
                input = asInput(preferences.overallBudgetCents)
                editing = true
            })
        } else Text(stringResource(R.string.budget_disabled), style = SpeseText.Meta, color = SpeseColors.TextMuted)
    }
    val (limited, unlimited) = state.categories.partition { it.monthlyLimitCents != null }
    if (limited.isNotEmpty()) SectionHeader(stringResource(R.string.with_limit))
    limited.forEachIndexed { index, category ->
        val limit = category.monthlyLimitCents ?: return@forEachIndexed
        val spent = sums[category.id] ?: 0
        val ratio = spent.toDouble() / limit
        val color = when {
            ratio > 1 -> SpeseColors.Danger
            ratio >= .85 -> SpeseColors.Warning
            else -> SpeseColors.Accent
        }
        Card {
            BudgetRow(category) {
                Text(stringResource(R.string.category_usage, ItalianFormats.money(spent), ItalianFormats.money(limit)), style = SpeseText.Meta, color = SpeseColors.TextMuted)
                Text(
                    when {
                        ratio > 1 -> stringResource(R.string.over_limit)
                        ratio >= .85 -> stringResource(R.string.almost_limit)
                        else -> stringResource(R.string.percentage, (ratio * 100).roundToInt())
                    }, color = color, style = SpeseText.MetaMedium,
                )
            }
            ProgressBar(ratio.toFloat(), color = color, cascadeIndex = index)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    if (spent > limit) stringResource(R.string.exceeded_by, ItalianFormats.money(spent - limit)) else
                        stringResource(R.string.remaining, ItalianFormats.money(limit - spent)),
                    style = SpeseText.Meta, color = SpeseColors.TextMuted,
                )
                IconAction("close", stringResource(R.string.remove_limit), {
                    scope.launch { model.repository.saveCategory(category.copy(monthlyLimitCents = null)) }
                })
            }
        }
    }
    if (unlimited.isNotEmpty()) SectionHeader(stringResource(R.string.without_limit))
    unlimited.forEach { category ->
        val spent = sums[category.id] ?: 0
        Card {
            BudgetRow(category, trailing = {
                Action(stringResource(R.string.add_limit), {
                    val proposed = (ceil(spent * 1.2 / 5_000).toLong().coerceAtLeast(1) * 5_000)
                    scope.launch { model.repository.saveCategory(category.copy(monthlyLimitCents = proposed)) }
                }, icon = "plus")
            }) { Text(ItalianFormats.money(spent), style = SpeseText.Meta, color = SpeseColors.TextMuted) }
        }
    }
    Text(stringResource(R.string.budget_note), style = SpeseText.Meta, color = SpeseColors.TextFaint)
    AppBottomSheet(editing, stringResource(R.string.budget_amount_title), { editing = false }) {
        Field(input, { input = it }, stringResource(R.string.amount), Modifier.fillMaxWidth(), decimal = true)
        val cents = parseCents(input)
        Action(stringResource(R.string.save), {
            if (cents != null) scope.launch { model.preferences.setBudget(true, cents) }
            editing = false
        }, Modifier.fillMaxWidth(), primary = true, enabled = cents != null)
    }
}

@Composable
private fun BudgetRow(
    category: CategoryEntity,
    trailing: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CategoryTile(category.iconKey, Color(category.colorArgb))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(category.name, style = SpeseText.BodyMedium)
            content()
        }
        trailing()
    }
}
