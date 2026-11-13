package it.avanzi.spese

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import it.avanzi.spese.domain.AmountInput
import it.avanzi.spese.domain.ItalianFormats
import it.avanzi.spese.ui.components.AmountDisplay
import it.avanzi.spese.ui.components.AppBottomSheet
import it.avanzi.spese.ui.components.CategoryChip
import it.avanzi.spese.ui.components.Keypad
import it.avanzi.spese.ui.components.ProgressBar
import it.avanzi.spese.ui.components.SectionHeader
import it.avanzi.spese.ui.components.SegmentedControl
import it.avanzi.spese.ui.components.SwitchRow
import it.avanzi.spese.ui.components.ToastMessage
import it.avanzi.spese.ui.components.UndoToast
import it.avanzi.spese.ui.theme.LocalReducedMotion
import it.avanzi.spese.ui.theme.SpeseColors
import it.avanzi.spese.ui.theme.SpeseMotion
import it.avanzi.spese.ui.theme.SpeseShapes
import it.avanzi.spese.ui.theme.SpeseText
import it.avanzi.spese.ui.theme.SpeseTheme

class FoundationPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { FoundationPreview() }
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun FoundationPreview() = SpeseTheme {
    var sheet by rememberSaveable { mutableStateOf(false) }
    var amount by rememberSaveable { mutableStateOf("") }
    var bump by remember { mutableIntStateOf(0) }
    var shake by remember { mutableIntStateOf(0) }
    var category by rememberSaveable { mutableIntStateOf(0) }
    var repeat by rememberSaveable { mutableStateOf(false) }
    var frequency by rememberSaveable { mutableIntStateOf(R.string.month) }
    var saved by rememberSaveable { mutableLongStateOf(0) }
    var pendingUndo by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    var nextToast by rememberSaveable { mutableLongStateOf(0) }
    val input = remember(amount) { AmountInput.restore(amount) }
    val reduced = LocalReducedMotion.current

    Surface(color = SpeseColors.Bg) {
        Box(Modifier.fillMaxSize().safeDrawingPadding()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.app_name), style = SpeseText.ScreenTitle)
                SectionHeader(stringResource(R.string.preview_latest))
                Text(ItalianFormats.money(saved), style = SpeseText.Total)
                ProgressBar((saved / 100_000f).coerceAtMost(1f))
                Button(
                    onClick = { sheet = true },
                    modifier = Modifier.heightIn(min = 44.dp),
                    shape = SpeseShapes.Row,
                ) { Text(stringResource(R.string.new_expense)) }
            }

            val message = pendingUndo?.let { (id, cents) ->
                ToastMessage(id, stringResource(R.string.expense_saved, ItalianFormats.money(cents)))
            }
            UndoToast(
                message,
                onDismiss = { id -> if (pendingUndo?.first == id) pendingUndo = null },
                onUndo = { id ->
                    pendingUndo?.takeIf { it.first == id }?.let { (_, cents) -> saved -= cents }
                },
                modifier = Modifier.align(Alignment.TopCenter).padding(14.dp),
            )

            AppBottomSheet(sheet, stringResource(R.string.new_expense), { sheet = false }) {
                AmountDisplay(input, bump, shake, Modifier.fillMaxWidth())
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(R.string.category_groceries to "cart", R.string.category_home to "home")
                        .forEachIndexed { index, (label, icon) ->
                            CategoryChip(
                                stringResource(label), icon, SpeseColors.CategoryPalette[index],
                                category == index, { category = index },
                            )
                        }
                }
                Column(
                    Modifier.fillMaxWidth().background(SpeseColors.Bg, SpeseShapes.Key)
                        .padding(horizontal = 14.dp),
                ) {
                    SwitchRow(stringResource(R.string.repeat), repeat, { repeat = it }, iconKey = "tabRec")
                    AnimatedVisibility(
                        repeat,
                        enter = fadeIn(tween(if (reduced) 120 else 320)) +
                            if (reduced) androidx.compose.animation.EnterTransition.None
                            else expandVertically(tween(320, easing = SpeseMotion.Standard)),
                        exit = fadeOut(tween(if (reduced) 120 else 320)) +
                            if (reduced) androidx.compose.animation.ExitTransition.None
                            else shrinkVertically(tween(320, easing = SpeseMotion.Standard)),
                    ) {
                        SegmentedControl(
                            listOf(R.string.week, R.string.month, R.string.year),
                            frequency, { stringResource(it) }, { frequency = it },
                            Modifier.padding(bottom = 12.dp),
                        )
                    }
                }
                Keypad(onKey = { key ->
                    val updated = input.press(key)
                    if (updated == null) shake++
                    else {
                        amount = updated.save()
                        bump++
                    }
                })
                Button(
                    onClick = {
                        if (input.cents == 0L) shake++
                        else {
                            saved += input.cents
                            pendingUndo = ++nextToast to input.cents
                            amount = ""
                            sheet = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    shape = SpeseShapes.Row,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SpeseColors.Accent.copy(alpha = if (input.cents == 0L) .45f else 1f),
                    ),
                ) { Text(stringResource(R.string.save_expense), style = SpeseText.BodyMedium) }
            }
        }
    }
}
