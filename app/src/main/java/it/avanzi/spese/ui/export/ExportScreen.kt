package it.avanzi.spese.ui.export

import android.content.ClipData
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import it.avanzi.spese.R
import it.avanzi.spese.domain.ExportData
import it.avanzi.spese.domain.ExportFormat
import it.avanzi.spese.domain.ExportOptions
import it.avanzi.spese.domain.ExportPeriod
import it.avanzi.spese.domain.ExportWriter
import it.avanzi.spese.domain.ItalianFormats
import it.avanzi.spese.domain.range
import it.avanzi.spese.ui.components.ProgressBar
import it.avanzi.spese.ui.components.SectionHeader
import it.avanzi.spese.ui.components.SwitchRow
import it.avanzi.spese.ui.components.ToastMessage
import it.avanzi.spese.ui.expense.DateAction
import it.avanzi.spese.ui.home.Action
import it.avanzi.spese.ui.home.Card
import it.avanzi.spese.ui.home.ExpandOptions
import it.avanzi.spese.ui.home.LedgerState
import it.avanzi.spese.ui.home.PageHeader
import it.avanzi.spese.ui.home.total
import it.avanzi.spese.ui.theme.SpeseColors
import it.avanzi.spese.ui.theme.SpeseShapes
import it.avanzi.spese.ui.theme.SpeseText
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ExportScreen(state: LedgerState, back: () -> Unit, onToast: (ToastMessage) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val today = LocalDate.now()
    var period by rememberSaveable { mutableStateOf(ExportPeriod.CURRENT) }
    var format by rememberSaveable { mutableStateOf(ExportFormat.CSV) }
    var from by rememberSaveable { mutableLongStateOf(today.withDayOfMonth(1).toEpochDay()) }
    var through by rememberSaveable { mutableLongStateOf(today.toEpochDay()) }
    var descriptions by rememberSaveable { mutableStateOf(true) }
    var recurring by rememberSaveable { mutableStateOf(true) }
    var categories by rememberSaveable { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    val range = period.range(today, LocalDate.ofEpochDay(from), LocalDate.ofEpochDay(through))
    val fileName = "${range.fileBase}.${format.extension}"
    val data = remember(range, state.expenses, state.categories, state.recurrings, descriptions, recurring, categories) {
        ExportData(range, state.expenses, state.categories, state.recurrings, ExportOptions(descriptions, recurring, categories))
    }
    val previewData = remember(data) { data.copy(expenses = data.rows.take(6)) }
    val preview = remember(previewData, format) {
        if (range.valid) ExportWriter.write(format, previewData).lineSequence().take(9).joinToString("\n") else ""
    }

    PageHeader(stringResource(R.string.export), back)
    SectionHeader(stringResource(R.string.period))
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ExportPeriod.entries.forEach { entry ->
            val selected = entry == period
            Box(
                Modifier.heightIn(min = 44.dp).clip(SpeseShapes.Pill)
                    .background(if (selected) SpeseColors.Accent else SpeseColors.Surface2)
                    .selectable(selected, role = Role.RadioButton) { period = entry }.padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) { Text(stringResource(entry.label()), style = SpeseText.MetaMedium, color = if (selected) SpeseColors.Bg else SpeseColors.Text) }
        }
    }
    ExpandOptions(period == ExportPeriod.CUSTOM) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DateAction(LocalDate.ofEpochDay(from), stringResource(R.string.date_from)) { from = it.toEpochDay() }
                DateAction(LocalDate.ofEpochDay(through), stringResource(R.string.date_through)) { through = it.toEpochDay() }
            }
            if (!range.valid) Text(stringResource(R.string.bad_date_range), style = SpeseText.Meta, color = SpeseColors.Danger)
        }
    }
    SectionHeader(stringResource(R.string.format))
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ExportFormat.entries.forEach { item ->
            val selected = item == format
            val color = if (selected) SpeseColors.Accent else SpeseColors.TextMuted
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(SpeseShapes.Key).background(SpeseColors.Surface)
                    .border(1.dp, if (selected) color else SpeseColors.Border, SpeseShapes.Key)
                    .selectable(selected, role = Role.RadioButton) { format = item }.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.size(22.dp).border(2.dp, color, SpeseShapes.Pill), contentAlignment = Alignment.Center) {
                    if (selected) Box(Modifier.size(10.dp).background(color, SpeseShapes.Pill))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(stringResource(item.label()), style = SpeseText.BodyMedium)
                    Text(stringResource(item.hint()), color = SpeseColors.TextFaint, style = SpeseText.Meta)
                }
                Text(".${item.extension}", style = SpeseText.Export, color = SpeseColors.TextMuted)
            }
        }
    }
    SectionHeader(stringResource(R.string.include))
    Card {
        SwitchRow(stringResource(R.string.include_descriptions), descriptions, { descriptions = it })
        SwitchRow(stringResource(R.string.include_recurring), recurring, { recurring = it })
        SwitchRow(stringResource(R.string.include_categories), categories, { categories = it })
    }
    SectionHeader(stringResource(R.string.preview))
    Text(
        stringResource(R.string.preview_total, stringResource(period.label()), data.rows.size, ItalianFormats.money(data.rows.total())),
        style = SpeseText.Meta, color = SpeseColors.TextSoft,
    )
    Column(
        Modifier.fillMaxWidth().clip(SpeseShapes.Key).background(SpeseColors.Bg)
            .border(1.dp, SpeseColors.Border, SpeseShapes.Key).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(fileName, color = SpeseColors.Accent, style = SpeseText.Export)
        Text(preview, Modifier.horizontalScroll(rememberScrollState()), style = SpeseText.Export, color = SpeseColors.TextSoft)
    }
    Action(
        stringResource(if (busy) R.string.export_preparing else R.string.export_share),
        onClick = {
            if (!busy && range.valid) {
                busy = true
                scope.launch {
                    try {
                        val file = withContext(Dispatchers.IO) {
                            val directory = File(context.cacheDir, "exports").apply { mkdirs() }
                            File(directory, fileName).also { it.writeText(ExportWriter.write(format, data), Charsets.UTF_8) }
                        }
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                        val share = Intent(Intent.ACTION_SEND).apply {
                            type = format.mime
                            putExtra(Intent.EXTRA_STREAM, uri)
                            clipData = ClipData.newUri(context.contentResolver, fileName, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(share, context.getString(R.string.export_share)))
                        onToast(ToastMessage(System.nanoTime(), context.getString(R.string.export_ready, fileName), false))
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        onToast(ToastMessage(System.nanoTime(), context.getString(R.string.operation_failed), false))
                    } finally {
                        busy = false
                    }
                }
            }
        },
        Modifier.fillMaxWidth(), primary = true, enabled = !busy && range.valid, icon = "share",
    )
}

private fun ExportPeriod.label() = when (this) {
    ExportPeriod.CURRENT -> R.string.period_current
    ExportPeriod.PREVIOUS -> R.string.period_previous
    ExportPeriod.LAST_THREE -> R.string.period_last_three
    ExportPeriod.YEAR -> R.string.period_year
    ExportPeriod.ALL -> R.string.period_all
    ExportPeriod.CUSTOM -> R.string.period_custom
}

private fun ExportFormat.label() = when (this) {
    ExportFormat.CSV -> R.string.csv
    ExportFormat.JSON -> R.string.json
    ExportFormat.MARKDOWN -> R.string.markdown
}

private fun ExportFormat.hint() = when (this) {
    ExportFormat.CSV -> R.string.csv_hint
    ExportFormat.JSON -> R.string.json_hint
    ExportFormat.MARKDOWN -> R.string.markdown_hint
}
