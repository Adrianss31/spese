package it.avanzi.spese.domain

import it.avanzi.spese.data.CategoryEntity
import it.avanzi.spese.data.ExpenseEntity
import it.avanzi.spese.data.RecurringEntity
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.math.roundToLong

enum class ExportFormat(val extension: String, val mime: String) {
    CSV("csv", "text/csv"), JSON("json", "application/json"), MARKDOWN("md", "text/markdown"),
}

enum class ExportPeriod { CURRENT, PREVIOUS, LAST_THREE, YEAR, ALL, CUSTOM }

data class ExportRange(val from: LocalDate?, val through: LocalDate?, val fileBase: String) {
    val valid get() = from == null || through == null || from <= through

    operator fun contains(date: LocalDate) = (from == null || date >= from) && (through == null || date <= through)
}

fun ExportPeriod.range(today: LocalDate, customFrom: LocalDate, customThrough: LocalDate): ExportRange {
    val month = YearMonth.from(today)
    return when (this) {
        ExportPeriod.CURRENT -> ExportRange(month.atDay(1), month.atEndOfMonth(), "spese_$month")
        ExportPeriod.PREVIOUS -> month.minusMonths(1).let { ExportRange(it.atDay(1), it.atEndOfMonth(), "spese_$it") }
        ExportPeriod.LAST_THREE -> month.minusMonths(2).let {
            ExportRange(it.atDay(1), month.atEndOfMonth(), "spese_${it}_$month")
        }
        ExportPeriod.YEAR -> ExportRange(LocalDate.of(today.year, 1, 1), LocalDate.of(today.year, 12, 31), "spese_${today.year}")
        ExportPeriod.ALL -> ExportRange(null, null, "spese_tutto")
        ExportPeriod.CUSTOM -> ExportRange(customFrom, customThrough, "spese_${customFrom}_$customThrough")
    }
}

data class ExportOptions(val descriptions: Boolean, val recurring: Boolean, val categories: Boolean)
data class ExportData(
    val range: ExportRange,
    val expenses: List<ExpenseEntity>,
    val categories: List<CategoryEntity>,
    val recurrings: List<RecurringEntity>,
    val options: ExportOptions,
) {
    val rows = expenses.filter { it.status == ExpenseStatus.CONFIRMED && it.date in range }
        .sortedWith(compareBy(ExpenseEntity::date, ExpenseEntity::id))
    private val names = categories.associate { it.id to it.name }
    fun category(id: Long) = names[id].orEmpty()
}

object ExportWriter {
    fun write(format: ExportFormat, data: ExportData): String {
        require(data.range.valid)
        return when (format) {
            ExportFormat.CSV -> csv(data)
            ExportFormat.JSON -> json(data)
            ExportFormat.MARKDOWN -> markdown(data)
        }
    }

    private fun csv(data: ExportData) = buildString {
        val options = data.options
        appendLine(buildList {
            add("data")
            if (options.descriptions) add("descrizione")
            add("categoria")
            add("importo")
            if (options.recurring) add("ricorrente")
        }.joinToString(";"))
        data.rows.forEach { row ->
            appendLine(buildList {
                add(row.date.toString())
                if (options.descriptions) add(csvCell(row.description.orEmpty(), true))
                add(csvCell(data.category(row.categoryId), true))
                add(decimal(row.amountCents).toPlainString().replace('.', ','))
                if (options.recurring) add(if (row.recurringId == null) "no" else "sì")
            }.joinToString(";"))
        }
    }

    private fun csvCell(value: String, protectFormula: Boolean): String {
        val first = value.trimStart().firstOrNull()
        val safe = if (protectFormula && (first in listOf('=', '+', '-', '@') || value.firstOrNull() in listOf('\t', '\r', '\n'))) "'$value" else value
        return if (safe.any { it in ";\r\n\"" }) "\"${safe.replace("\"", "\"\"")}\"" else safe
    }

    private fun json(data: ExportData): String {
        val options = data.options
        val root = buildJsonObject {
            put("periodo", buildJsonObject {
                put("dal", data.range.from?.toString())
                put("al", data.range.through?.toString())
            })
            put("valuta", "EUR")
            if (options.categories) put("categorie", buildJsonArray {
                data.categories.forEach { category ->
                    add(buildJsonObject {
                        put("nome", category.name)
                        put("colore", "#%06X".format(java.util.Locale.ROOT, category.colorArgb and 0xFFFFFF))
                        put("limite", category.monthlyLimitCents?.let { JsonPrimitive(decimal(it)) } ?: JsonNull)
                    })
                }
            })
            put("spese", buildJsonArray {
                data.rows.forEach { expense ->
                    add(buildJsonObject {
                        put("data", expense.date.toString())
                        if (options.descriptions) put("descrizione", expense.description)
                        put("categoria", data.category(expense.categoryId))
                        put("importo", JsonPrimitive(decimal(expense.amountCents)))
                        if (options.recurring) put("ricorrente", expense.recurringId != null)
                    })
                }
            })
            if (options.recurring) put("ricorrenti", buildJsonArray {
                data.recurrings.filter { it.active }.forEach { recurring ->
                    add(buildJsonObject {
                        put("nome", recurring.name)
                        put("categoria", data.category(recurring.categoryId))
                        put("importo", JsonPrimitive(decimal(recurring.amountCents)))
                        put("frequenza", frequency(recurring.frequency))
                        put("variabile", recurring.isVariable)
                        put("automatico", recurring.autoRecord)
                    })
                }
            })
        }
        return Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), root) + "\n"
    }

    private fun markdown(data: ExportData) = buildString {
        val options = data.options
        val total = data.rows.sumOf { it.amountCents }
        val from = data.range.from?.toString()
        val through = data.range.through?.toString()
        appendLine("# Spese personali · ${if (from != null && through != null) "$from – $through" else "Tutto"}")
        appendLine()
        appendLine("Questi sono i dati di spesa personali di un utente in Italia, in EUR. Gli importi indicano uscite confermate e le date seguono il formato ISO 8601.")
        appendLine()
        appendLine("**Totale:** ${ItalianFormats.money(total)} · **Spese:** ${data.rows.size}")
        if (options.categories) {
            appendLine()
            appendLine("## Categorie")
            appendLine("| Categoria | Totale | Limite mensile | Quota |")
            appendLine("|---|---:|---:|---:|")
            val categoryTotals = data.rows.groupBy { it.categoryId }.mapValues { (_, rows) -> rows.sumOf { it.amountCents } }
            data.categories.forEach { category ->
                val subtotal = categoryTotals[category.id] ?: 0
                val pct = if (total > 0) (100.0 * subtotal / total).roundToLong() else 0
                appendLine("| ${cell(category.name)} | ${ItalianFormats.money(subtotal)} | ${category.monthlyLimitCents?.let(ItalianFormats::money) ?: "—"} | $pct% |")
            }
        }
        val months = data.rows.groupBy { YearMonth.from(it.date) }
        val coversMultiple = if (data.range.from != null && data.range.through != null) {
            YearMonth.from(data.range.from) != YearMonth.from(data.range.through)
        } else months.size > 1
        if (coversMultiple) {
            appendLine()
            appendLine("## Confronto per mese")
            appendLine("| Mese | Totale |")
            appendLine("|---|---:|")
            months.forEach { (month, rows) ->
                appendLine("| $month | ${ItalianFormats.money(rows.sumOf { it.amountCents })} |")
            }
        }
        if (options.recurring) {
            appendLine()
            appendLine("## Ricorrenti attivi")
            data.recurrings.filter { it.active }.forEach {
                appendLine("- ${cell(it.name)} · ${frequency(it.frequency)} · circa ${ItalianFormats.money((it.amountCents * it.frequency.monthlyFactor).roundToLong())} al mese")
            }
        }
        appendLine()
        appendLine("## Spese")
        val headers = listOf("Data") + (if (options.descriptions) listOf("Descrizione") else emptyList()) +
            listOf("Categoria", "Importo") + (if (options.recurring) listOf("Ricorrente") else emptyList())
        appendLine(headers.joinToString(" | ", "| ", " |"))
        appendLine(headers.joinToString("|", "|", "|") { "---" })
        data.rows.forEach { row ->
            appendLine(buildList {
                add(row.date.toString())
                if (options.descriptions) add(cell(row.description.orEmpty()))
                add(cell(data.category(row.categoryId)))
                add(ItalianFormats.money(row.amountCents))
                if (options.recurring) add(if (row.recurringId != null) "Sì" else "No")
            }.joinToString(" | ", "| ", " |"))
        }
    }

    private fun decimal(cents: Long) = BigDecimal.valueOf(cents, 2)
    private fun cell(value: String) = value.replace("\\", "\\\\").replace("|", "\\|")
        .replace("<", "&lt;").replace(">", "&gt;").replace(Regex("[\\r\\n]+"), " ")
    private fun frequency(value: Frequency) = when (value) {
        Frequency.WEEKLY -> "settimanale"
        Frequency.MONTHLY -> "mensile"
        Frequency.BIMONTHLY -> "bimestrale"
        Frequency.YEARLY -> "annuale"
    }
}
