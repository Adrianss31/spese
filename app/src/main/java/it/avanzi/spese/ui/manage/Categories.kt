package it.avanzi.spese.ui.manage

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import it.avanzi.spese.R
import it.avanzi.spese.data.CategoryEntity
import it.avanzi.spese.domain.ItalianFormats
import it.avanzi.spese.ui.components.AppBottomSheet
import it.avanzi.spese.ui.components.CategoryTile
import it.avanzi.spese.ui.components.SectionHeader
import it.avanzi.spese.ui.components.SwitchRow
import it.avanzi.spese.ui.home.Action
import it.avanzi.spese.ui.home.Card
import it.avanzi.spese.ui.home.ExpandOptions
import it.avanzi.spese.ui.home.Field
import it.avanzi.spese.ui.home.LedgerState
import it.avanzi.spese.ui.home.PageHeader
import it.avanzi.spese.ui.home.SpeseViewModel
import it.avanzi.spese.ui.home.confirmed
import it.avanzi.spese.ui.home.monthExpenses
import it.avanzi.spese.ui.home.parseCents
import it.avanzi.spese.ui.home.total
import it.avanzi.spese.ui.icons.SpeseIcons
import it.avanzi.spese.ui.theme.LocalReducedMotion
import it.avanzi.spese.ui.theme.SpeseColors
import it.avanzi.spese.ui.theme.SpeseMotion
import it.avanzi.spese.ui.theme.SpeseShapes
import it.avanzi.spese.ui.theme.SpeseText
import it.avanzi.spese.ui.theme.pressScale
import java.math.BigDecimal
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun Categories(state: LedgerState, model: SpeseViewModel, back: () -> Unit) {
    var editing by rememberSaveable { mutableLongStateOf(-1) }
    var dragId by remember { mutableLongStateOf(0) }
    var remainder by remember { mutableFloatStateOf(0f) }
    var ordered by remember { mutableStateOf<List<Long>?>(null) }
    val scope = rememberCoroutineScope()
    val base = state.categories.filterNot { it.isSystem }.map { it.id }
    LaunchedEffect(base) { if (ordered == base) ordered = null }
    val order = ordered ?: base
    val move = { id: Long, amount: Int ->
        val list = (ordered ?: base).toMutableList()
        val from = list.indexOf(id)
        val to = from + amount
        if (from >= 0 && to in list.indices) {
            list.add(to, list.removeAt(from))
            ordered = list
        }
    }
    val finish = {
        dragId = 0
        remainder = 0f
        ordered?.let { ids -> scope.launch { model.repository.reorderCategories(ids) } }
        Unit
    }
    val latestMove by rememberUpdatedState(move)
    val latestFinish by rememberUpdatedState(finish)
    val month = state.month.month.getDisplayName(TextStyle.FULL, Locale.ITALY)
    val spent = state.monthExpenses().confirmed().groupBy { it.categoryId }.mapValues { it.value.total() }

    PageHeader(stringResource(R.string.categories), back) {
        Action(stringResource(R.string.new_feminine), { editing = 0 }, icon = "plus")
    }
    Text(stringResource(R.string.category_guide), style = SpeseText.Meta, color = SpeseColors.TextFaint)
    val display = order.mapNotNull { id -> state.categories.firstOrNull { it.id == id } } +
        state.categories.filter { it.isSystem }
    display.forEachIndexed { index, category ->
        androidx.compose.runtime.key(category.id) {
            val interaction = remember { MutableInteractionSource() }
            val caption = stringResource(R.string.category_spent, ItalianFormats.money(spent[category.id] ?: 0), month)
            Row(
                Modifier.fillMaxWidth().heightIn(min = 66.dp).pressScale(interaction, .98f)
                    .clip(SpeseShapes.Row)
                    .background(if (dragId == category.id) SpeseColors.Surface3 else SpeseColors.Surface)
                    .clickable(interaction, indication = null) { editing = category.id }
                    .padding(end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                if (!category.isSystem) {
                    val label = stringResource(R.string.reorder, category.name)
                    val up = stringResource(R.string.move_up)
                    val down = stringResource(R.string.move_down)
                    Box(
                        Modifier.size(44.dp).semantics {
                            contentDescription = label
                            customActions = buildList {
                                if (index > 0) add(CustomAccessibilityAction(up) {
                                    latestMove(category.id, -1); latestFinish(); true
                                })
                                if (index < order.lastIndex) add(CustomAccessibilityAction(down) {
                                    latestMove(category.id, 1); latestFinish(); true
                                })
                            }
                        }.pointerInput(category.id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { dragId = category.id; remainder = 0f },
                                onDragEnd = latestFinish,
                                onDragCancel = latestFinish,
                            ) { change, delta ->
                                change.consume()
                                remainder += delta.y
                                val threshold = 48.dp.toPx()
                                if (kotlin.math.abs(remainder) >= threshold) {
                                    latestMove(category.id, if (remainder > 0) 1 else -1)
                                    remainder = 0f
                                }
                            }
                        },
                        contentAlignment = Alignment.Center,
                    ) { Icon(SpeseIcons["drag"], null, Modifier.size(18.dp), tint = SpeseColors.TextFaint) }
                } else Box(Modifier.size(44.dp))
                CategoryTile(category.iconKey, Color(category.colorArgb))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(category.name, style = SpeseText.BodyMedium)
                    Text(
                        category.monthlyLimitCents?.let {
                            stringResource(R.string.category_limit, caption, ItalianFormats.money(it))
                        } ?: caption,
                        style = SpeseText.Meta, color = SpeseColors.TextFaint,
                    )
                }
                Icon(SpeseIcons["chevronRight"], null, Modifier.size(18.dp), tint = SpeseColors.TextMuted)
            }
        }
    }
    val existing = state.categories.firstOrNull { it.id == editing }
    AppBottomSheet(editing >= 0, stringResource(if (editing == 0L) R.string.new_category else R.string.edit_category), { editing = -1 }) {
        androidx.compose.runtime.key(editing) {
            CategoryEditor(existing, onSave = { category ->
                scope.launch { model.repository.saveCategory(category) }
                editing = -1
            }, onDelete = {
                if (existing != null) scope.launch { model.repository.deleteCategory(existing.id) }
                editing = -1
            })
        }
    }
}

@Composable
private fun CategoryEditor(category: CategoryEntity?, onSave: (CategoryEntity) -> Unit, onDelete: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(category?.name.orEmpty()) }
    var color by rememberSaveable { mutableStateOf(category?.colorArgb ?: SpeseColors.CategoryPalette[5].toArgb()) }
    var icon by rememberSaveable { mutableStateOf(category?.iconKey ?: "paw") }
    var hasLimit by rememberSaveable { mutableStateOf(category?.monthlyLimitCents != null) }
    var amount by rememberSaveable { mutableStateOf(category?.monthlyLimitCents?.let(::asInput).orEmpty()) }
    val reduced = LocalReducedMotion.current
    val preview by animateColorAsState(Color(color), tween(if (reduced) 120 else 250), label = "Category color")
    val bump = remember { Animatable(1f) }
    LaunchedEffect(color, icon, reduced) {
        bump.snapTo(if (reduced) 1f else 1.055f)
        if (!reduced) bump.animateTo(1f, SpeseMotion.springBack())
    }
    Card(Modifier.graphicsLayer { scaleX = bump.value; scaleY = bump.value }) {
        Row(
            Modifier.align(Alignment.CenterHorizontally).background(preview, SpeseShapes.Pill).padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CategoryTile(icon, Color.Transparent, size = 36.dp)
            Text(name.ifBlank { stringResource(R.string.new_category) }, color = SpeseColors.Bg, style = SpeseText.CardTitle)
        }
    }
    Field(name, { name = it }, stringResource(R.string.name), Modifier.fillMaxWidth())
    SectionHeader(stringResource(R.string.color))
    val paletteNames = stringArrayResource(R.array.palette_names)
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SpeseColors.CategoryPalette.chunked(5).forEachIndexed { row, choices ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                choices.forEachIndexed { index, choice ->
                    val current = choice.toArgb() == color
                    val label = paletteNames[row * 5 + index]
                    Box(
                        Modifier.size(44.dp).clip(SpeseShapes.Pill)
                            .selectable(current, role = Role.RadioButton) { color = choice.toArgb() }
                            .semantics { contentDescription = label }.padding(3.dp)
                            .then(if (current) Modifier.border(2.dp, SpeseColors.Text, SpeseShapes.Pill) else Modifier)
                            .padding(4.dp).background(choice, SpeseShapes.Pill),
                    )
                }
            }
        }
    }
    SectionHeader(stringResource(R.string.icon))
    val iconNames = stringArrayResource(R.array.category_icon_names)
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SpeseIcons.categoryKeys.chunked(6).forEachIndexed { row, choices ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                choices.forEachIndexed { index, choice ->
                    val current = icon == choice
                    val label = iconNames[row * 6 + index]
                    Box(
                        Modifier.weight(1f).heightIn(min = 44.dp).clip(SpeseShapes.SmallTile)
                            .background(if (current) Color(color) else SpeseColors.Surface2)
                            .selectable(current, role = Role.RadioButton) { icon = choice }.semantics { contentDescription = label },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(SpeseIcons[choice], null, Modifier.size(20.dp), tint = if (current) SpeseColors.Bg else SpeseColors.TextSoft)
                    }
                }
            }
        }
    }
    SwitchRow(stringResource(R.string.monthly_limit), hasLimit, { hasLimit = it }, subtitle = stringResource(R.string.limit_hint))
    ExpandOptions(hasLimit) { Field(amount, { amount = it }, stringResource(R.string.amount), Modifier.fillMaxWidth(), decimal = true) }
    if (category != null && !category.isSystem) {
        Text(stringResource(R.string.delete_category_note), color = SpeseColors.TextFaint, style = SpeseText.Meta)
    }
    val cents = parseCents(amount)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (category != null && !category.isSystem) Action(stringResource(R.string.delete), onDelete, danger = true)
        Action(
            stringResource(R.string.save),
            { onSave(CategoryEntity(category?.id ?: 0, name.trim(), color, icon, cents.takeIf { hasLimit }, 0)) },
            Modifier.weight(1f), primary = true, enabled = name.isNotBlank() && (!hasLimit || cents != null),
        )
    }
}

fun asInput(cents: Long): String = BigDecimal.valueOf(cents, 2).toPlainString().replace('.', ',')
