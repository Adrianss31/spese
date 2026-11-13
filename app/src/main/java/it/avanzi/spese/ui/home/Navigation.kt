package it.avanzi.spese.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import it.avanzi.spese.R
import it.avanzi.spese.data.SavedExpense
import it.avanzi.spese.domain.ItalianFormats
import it.avanzi.spese.ui.components.AppBottomSheet
import it.avanzi.spese.ui.components.SectionHeader
import it.avanzi.spese.ui.components.ToastMessage
import it.avanzi.spese.ui.components.UndoToast
import it.avanzi.spese.ui.expense.ExpenseEditor
import it.avanzi.spese.ui.icons.SpeseIcons
import it.avanzi.spese.ui.theme.LocalReducedMotion
import it.avanzi.spese.ui.theme.SpeseColors
import it.avanzi.spese.ui.theme.SpeseMotion
import it.avanzi.spese.ui.theme.SpeseShapes
import it.avanzi.spese.ui.theme.SpeseText
import it.avanzi.spese.ui.theme.pressScale
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToLong

enum class Destination(val label: Int, val icon: String) {
    HOME(R.string.home, "tabHome"),
    STATS(R.string.stats, "tabStats"),
    RECURRING(R.string.recurrings, "tabRec"),
    MORE(R.string.more, "tabMore"),
    BUDGET(R.string.budget, "wallet"),
    CATEGORIES(R.string.categories, "tag"),
    EXPORT(R.string.export, "share"),
    UPDATES(R.string.updates, "download"),
    SEARCH(R.string.search, "search"),
}

private val tabs = Destination.entries.take(4)

@Composable
fun SpeseScreen(model: SpeseViewModel) {
    val ledger by model.state.collectAsState()
    var destination by rememberSaveable { mutableStateOf(Destination.HOME) }
    var editor by rememberSaveable { mutableStateOf(false) }
    var editorKey by rememberSaveable { mutableLongStateOf(0) }
    var selectedExpense by rememberSaveable { mutableLongStateOf(0) }
    var showMonths by rememberSaveable { mutableStateOf(false) }
    var searchImmediately by rememberSaveable { mutableStateOf(false) }
    var toast by remember { mutableStateOf<ToastMessage?>(null) }
    var undo by remember { mutableStateOf<SavedExpense?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val reduced = LocalReducedMotion.current
    val expense = ledger.expenses.firstOrNull { it.id == selectedExpense }
    val openExpense = {
        editorKey++
        selectedExpense = 0
        editor = true
    }
    val back = {
        destination = if (destination == Destination.SEARCH || destination in tabs) {
            Destination.HOME
        } else Destination.MORE
    }
    BackHandler(destination != Destination.HOME && !editor && !showMonths, onBack = back)

    Surface(color = SpeseColors.Bg) {
        Box(Modifier.fillMaxSize().safeDrawingPadding()) {
            Column(Modifier.fillMaxSize()) {
                AnimatedContent(
                    destination,
                    Modifier.weight(1f).fillMaxWidth(),
                    transitionSpec = {
                        val duration = if (reduced) 120 else 340
                        val enter = fadeIn(tween(duration, easing = SpeseMotion.Standard))
                        (if (reduced) enter else enter + slideInHorizontally(
                            tween(duration, easing = SpeseMotion.Standard),
                        ) { it * 12 / 390 }) togetherWith fadeOut(tween(duration))
                    },
                    label = "Destination",
                ) { screen ->
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                            .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        when (screen) {
                            Destination.HOME -> HomeContent(
                                ledger, onMonth = { showMonths = true },
                                onSearch = { searchImmediately = true; destination = Destination.SEARCH },
                                onAll = { searchImmediately = false; destination = Destination.SEARCH },
                                onRecurring = { destination = Destination.RECURRING },
                                highlightedId = undo?.expenseId,
                                onExpense = { row -> editorKey++; selectedExpense = row.id; editor = true },
                            )
                            Destination.SEARCH -> ExpenseSearch(ledger, searchImmediately, back) { row ->
                                editorKey++
                                selectedExpense = row.id
                                editor = true
                            }
                            Destination.MORE -> MoreContent(ledger) { destination = it }
                            else -> PageHeader(stringResource(screen.label), if (screen !in tabs) back else null)
                        }
                    }
                }
                NavigationBar(
                    selected = if (destination in tabs) destination else if (destination == Destination.SEARCH) Destination.HOME else Destination.MORE,
                    onSelect = { destination = it },
                )
            }

            AddButton(
                openExpense,
                Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 104.dp),
            )
            UndoToast(
                toast,
                onDismiss = { id ->
                    if (toast?.id == id) {
                        toast = null
                        undo = null
                    }
                },
                onUndo = { id ->
                    val saved = undo?.takeIf { it.expenseId == id }
                    if (saved != null) scope.launch { model.repository.undo(saved) }
                },
                modifier = Modifier.align(Alignment.TopCenter).padding(14.dp),
            )
        }
    }

    MonthPicker(showMonths, ledger.month, onDismiss = { showMonths = false }) {
        model.selectMonth(it)
        showMonths = false
    }

    AppBottomSheet(
        editor, stringResource(if (selectedExpense == 0L) R.string.new_expense else R.string.pending),
        onDismiss = { if (!saving) editor = false },
    ) {
        key(editorKey) {
            ExpenseEditor(
                ledger.categories,
                saving,
                onSave = { draft ->
                    if (!saving) {
                        saving = true
                        scope.launch {
                            try {
                                if (expense != null) {
                                    model.repository.confirmExpense(expense.id, draft.cents)
                                } else {
                                    val saved = model.save(
                                        draft.cents, draft.category, draft.description, draft.date, draft.frequency,
                                    )
                                    model.selectMonth(YearMonth.from(draft.date))
                                    destination = Destination.HOME
                                    undo = saved
                                    toast = ToastMessage(
                                        saved.expenseId,
                                        context.getString(R.string.expense_saved, ItalianFormats.money(draft.cents)),
                                    )
                                }
                                editor = false
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                undo = null
                                toast = ToastMessage(-1, context.getString(R.string.operation_failed), false)
                                editor = false
                            } finally {
                                saving = false
                            }
                        }
                    }
                },
                initialCents = expense?.amountCents,
                initialCategory = expense?.categoryId,
                initialDescription = expense?.description.orEmpty(),
                initialDate = expense?.date ?: LocalDate.now(),
                confirmation = selectedExpense != 0L,
            )
        }
    }
}

@Composable
fun PageHeader(title: String, onBack: (() -> Unit)? = null, action: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (onBack != null) IconAction("back", stringResource(R.string.back), onBack)
        Text(title, Modifier.weight(1f), style = SpeseText.ScreenTitle)
        action()
    }
}

@Composable
private fun NavigationBar(selected: Destination, onSelect: (Destination) -> Unit) {
    Column(Modifier.background(Color(0xF5121419))) {
        HorizontalDivider(color = SpeseColors.Divider)
        Row(
            Modifier.fillMaxWidth().heightIn(min = 83.dp).padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 18.dp)
                .selectableGroup(),
        ) {
            tabs.forEach { tab ->
                val source = remember { MutableInteractionSource() }
                val active = tab == selected
                val color = if (active) SpeseColors.Accent else SpeseColors.TextFaint
                Column(
                    Modifier.weight(1f).heightIn(min = 44.dp).pressScale(source)
                        .selectable(active, source, indication = null, role = Role.Tab) { onSelect(tab) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
                ) {
                    Box(
                        Modifier.size(56.dp, 32.dp)
                            .background(if (active) Color(0xFF23262E) else Color.Transparent, SpeseShapes.Pill),
                        contentAlignment = Alignment.Center,
                    ) { Icon(SpeseIcons[tab.icon], null, Modifier.size(22.dp), tint = color) }
                    Text(stringResource(tab.label), color = color, style = SpeseText.MetaMedium)
                }
            }
        }
    }
}

@Composable
private fun AddButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val source = remember { MutableInteractionSource() }
    val reduced = LocalReducedMotion.current
    val animation = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(reduced) {
        if (reduced) animation.snapTo(1f)
        else {
            delay(350)
            animation.animateTo(1f, SpeseMotion.springBack())
        }
    }
    Box(
        modifier.size(64.dp).graphicsLayer {
            scaleX = animation.value
            scaleY = animation.value
            rotationZ = -90f + 90f * animation.value
        }.pressScale(source).clip(SpeseShapes.FloatingButton).background(SpeseColors.Accent)
            .clickable(source, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(SpeseIcons["plus"], stringResource(R.string.new_expense), Modifier.size(28.dp), tint = SpeseColors.Bg)
    }
}

@Composable
private fun MoreContent(state: LedgerState, navigate: (Destination) -> Unit) {
    PageHeader(stringResource(R.string.more))
    val active = state.recurrings.filter { it.active }
    val limits = state.categories.count { it.monthlyLimitCents != null }
    val budget = state.preferences
    val groups = listOf(
        R.string.section_management to listOf(
            Destination.BUDGET to if (budget.overallBudgetEnabled) stringResource(
                R.string.budget_summary, ItalianFormats.money(budget.overallBudgetCents), limits,
            ) else stringResource(R.string.budget_off_summary, limits),
            Destination.CATEGORIES to stringResource(R.string.category_count, state.categories.size),
            Destination.RECURRING to stringResource(
                R.string.recurring_count, active.size,
                ItalianFormats.money(active.sumOf { it.amountCents * it.frequency.monthlyFactor }.roundToLong()),
            ),
        ),
        R.string.section_data to listOf(Destination.EXPORT to null),
        R.string.section_app to listOf(Destination.UPDATES to null),
    )
    groups.forEach { (group, options) ->
        SectionHeader(stringResource(group))
        Column(Modifier.clip(SpeseShapes.Group).background(SpeseColors.Surface)) {
            options.forEachIndexed { index, (target, detail) ->
                if (index > 0) HorizontalDivider(color = SpeseColors.Divider)
                val interaction = remember { MutableInteractionSource() }
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 64.dp).pressScale(interaction, .98f)
                        .clickable(interaction, indication = null, onClick = { navigate(target) })
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier.size(42.dp).background(SpeseColors.Surface2, SpeseShapes.Tile), contentAlignment = Alignment.Center) {
                        Icon(SpeseIcons[target.icon], null, Modifier.size(20.dp), tint = SpeseColors.TextSoft)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(stringResource(if (target == Destination.RECURRING) R.string.recurring_payments else target.label), style = SpeseText.BodyMedium)
                        detail?.let { Text(it, style = SpeseText.Meta, color = SpeseColors.TextFaint) }
                    }
                    Icon(SpeseIcons["chevronRight"], null, Modifier.size(18.dp), tint = SpeseColors.TextFaint)
                }
            }
        }
    }
    val context = LocalContext.current
    val version = remember { context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty() }
    Text(stringResource(R.string.version, version), color = SpeseColors.TextFaint, style = SpeseText.Meta)
}
