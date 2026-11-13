package it.avanzi.spese.domain

import it.avanzi.spese.data.CategoryEntity
import it.avanzi.spese.data.ExpenseEntity
import java.time.Instant
import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportTest {
    private val today = LocalDate.parse("2026-11-13")
    private val expense = ExpenseEntity(
        amountCents = 128450, categoryId = 3, description = "Caffè; \"doppio\"",
        date = today, recurringId = 10, createdAt = Instant.EPOCH,
    )
    private val data = ExportData(
        ExportPeriod.CURRENT.range(today, today, today),
        listOf(expense, expense.copy(status = ExpenseStatus.PENDING), expense.copy(date = today.minusMonths(1))),
        listOf(CategoryEntity(3, "Spesa", 0xFF7CC4FF.toInt(), "cart", 145000, 0)),
        emptyList(), ExportOptions(true, true, true),
    )

    @Test fun csvUsesDecimalCommaQuotesAndOnlyConfirmedExpensesInRange() {
        assertEquals(
            "data;descrizione;categoria;importo;ricorrente\n2026-11-13;\"Caffè; \"\"doppio\"\"\";Spesa;1284,50;sì\n",
            ExportWriter.write(ExportFormat.CSV, data),
        )
    }

    @Test fun jsonAmountIsNumericAndOptionsRemoveKeys() {
        val minimal = data.copy(options = ExportOptions(false, false, false))
        val objectValue = Json.parseToJsonElement(ExportWriter.write(ExportFormat.JSON, minimal)).jsonObject
        val row = objectValue.getValue("spese").jsonArray.single().jsonObject
        assertEquals(0, java.math.BigDecimal("1284.50").compareTo(row.getValue("importo").jsonPrimitive.content.toBigDecimal()))
        assertFalse(row.getValue("importo").jsonPrimitive.isString)
        assertFalse("descrizione" in row)
        assertFalse("ricorrente" in row)
        assertFalse("categorie" in objectValue)
    }

    @Test fun markdownContainsItalianTotalsAndEscapesTableSeparators() {
        val text = ExportWriter.write(ExportFormat.MARKDOWN, data.copy(expenses = listOf(expense.copy(description = "A|B"))))
        assertTrue("1.284,50 €" in text)
        assertTrue("1.450,00 €" in text)
        assertTrue("A\\|B" in text)
    }

    @Test fun periodNamesIncludeYearAcrossBoundary() {
        val january = LocalDate.parse("2027-01-04")
        val range = ExportPeriod.LAST_THREE.range(january, january, january)
        assertEquals("spese_2026-11_2027-01", range.fileBase)
        assertEquals(LocalDate.parse("2026-11-01"), range.from)
        assertEquals(LocalDate.parse("2027-01-31"), range.through)
    }
}
