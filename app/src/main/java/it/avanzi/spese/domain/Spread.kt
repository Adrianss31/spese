package it.avanzi.spese.domain

import it.avanzi.spese.data.ExpenseEntity
import java.time.LocalDate
import java.time.YearMonth

/**
 * Una riga del registro così come va mostrata e sommata. Con lo spalmamento attivo un addebito che copre
 * più mesi diventa una quota per ciascun mese, alla stessa data del mese (limitata alla fine del mese).
 */
data class ExpenseLine(
    val expense: ExpenseEntity,
    val date: LocalDate,
    val cents: Long,
    val part: Int = 1,
    val parts: Int = 1,
) {
    val isQuota get() = parts > 1
    val month: YearMonth get() = YearMonth.from(date)
}

fun ExpenseEntity.lines(spread: Boolean): List<ExpenseLine> {
    val parts = spreadMonths?.takeIf { spread && it > 1 && status == ExpenseStatus.CONFIRMED }
        ?: return listOf(ExpenseLine(this, date, amountCents))
    val base = Math.floorDiv(amountCents, parts.toLong())
    val rest = Math.floorMod(amountCents, parts.toLong())
    val start = YearMonth.from(date)
    return (0 until parts).map { index ->
        val month = start.plusMonths(index.toLong())
        ExpenseLine(
            this, month.atDay(date.dayOfMonth.coerceAtMost(month.lengthOfMonth())),
            base + if (index < rest) 1 else 0, index + 1, parts,
        )
    }
}

/** Righe che cadono nei mesi da [from] a [to] inclusi, dalla più recente. */
fun List<ExpenseEntity>.linesIn(from: YearMonth, to: YearMonth, spread: Boolean): List<ExpenseLine> =
    asSequence().filter { expense ->
        val start = YearMonth.from(expense.date)
        val span = if (spread && expense.status == ExpenseStatus.CONFIRMED) (expense.spreadMonths ?: 1).coerceAtLeast(1) else 1
        start <= to && start.plusMonths(span - 1L) >= from
    }.flatMap { it.lines(spread) }
        .filter { it.month in from..to }
        .sortedWith(compareByDescending<ExpenseLine> { it.date }.thenByDescending { it.expense.id })
        .toList()

fun List<ExpenseLine>.confirmedLines() = filter { it.expense.status == ExpenseStatus.CONFIRMED }
fun List<ExpenseLine>.sumCents() = sumOf(ExpenseLine::cents)
