package it.avanzi.spese.domain

import it.avanzi.spese.data.ExpenseEntity
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Test

class SpreadTest {
    private val yearly = ExpenseEntity(
        id = 1, amountCents = 60000, categoryId = 1, date = LocalDate.parse("2026-03-31"),
        recurringId = 7, createdAt = Instant.EPOCH, spreadMonths = 12,
    )
    private val coffee = ExpenseEntity(id = 2, amountCents = 150, categoryId = 2, date = LocalDate.parse("2026-11-03"), createdAt = Instant.EPOCH)

    @Test fun spreadsEvenlyFromTheChargeMonthClampingTheDay() {
        val lines = yearly.lines(spread = true)
        assertEquals(12, lines.size)
        assertEquals(List(12) { 5000L }, lines.map { it.cents })
        assertEquals(LocalDate.parse("2026-04-30"), lines[1].date)
        assertEquals(LocalDate.parse("2027-02-28"), lines.last().date)
        assertEquals(12, lines.last().part)
    }

    @Test fun remainderGoesToTheFirstMonthsSoTheSumMatches() {
        val odd = yearly.copy(amountCents = 1000, spreadMonths = 3)
        assertEquals(listOf(334L, 333L, 333L), odd.lines(spread = true).map { it.cents })
    }

    @Test fun monthTotalsWithAndWithoutSpread() {
        val all = listOf(coffee, yearly)
        val november = YearMonth.of(2026, 11)
        assertEquals(5150L, all.linesIn(november, november, spread = true).sumCents())
        assertEquals(150L, all.linesIn(november, november, spread = false).sumCents())
        val march = YearMonth.of(2026, 3)
        assertEquals(60000L, all.linesIn(march, march, spread = false).sumCents())
        assertEquals(60150L, all.linesIn(YearMonth.of(2026, 1), YearMonth.of(2027, 12), spread = true).sumCents())
    }

    @Test fun pendingChargesAreNotSpread() {
        val pending = yearly.copy(status = ExpenseStatus.PENDING)
        assertEquals(1, pending.lines(spread = true).size)
        assertEquals(emptyList<ExpenseLine>(), listOf(pending).linesIn(YearMonth.of(2026, 4), YearMonth.of(2026, 4), true))
    }
}
