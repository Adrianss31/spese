package it.avanzi.spese.domain

import it.avanzi.spese.data.RecurringEntity
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

fun RecurringEntity.nextOnOrAfter(date: LocalDate): LocalDate {
    val lowerBound = maxOf(date, anchorDate)
    if (frequency == Frequency.WEEKLY) {
        return lowerBound.with(TemporalAdjusters.nextOrSame(dayOfWeek ?: anchorDate.dayOfWeek))
    }
    val day = dayOfMonth ?: anchorDate.dayOfMonth
    if (frequency == Frequency.YEARLY) {
        val month = monthOfYear ?: anchorDate.monthValue
        val candidate = YearMonth.of(lowerBound.year, month)
        return candidate.onDay(day).takeIf { it >= lowerBound } ?: candidate.plusYears(1).onDay(day)
    }
    val interval = if (frequency == Frequency.BIMONTHLY) 2L else 1L
    val startMonth = YearMonth.from(anchorDate)
    val reference = YearMonth.from(lowerBound)
    val offset = ChronoUnit.MONTHS.between(startMonth, reference)
    val candidate = startMonth.plusMonths(((offset + interval - 1) / interval) * interval)
    return candidate.onDay(day).takeIf { it >= lowerBound } ?: candidate.plusMonths(interval).onDay(day)
}

fun RecurringEntity.occurrences(from: LocalDate, through: LocalDate): Sequence<LocalDate> {
    if (through < from || through < anchorDate) return emptySequence()
    return generateSequence(nextOnOrAfter(from)) { current ->
        if (current >= through) null else nextOnOrAfter(current.plusDays(1))
    }.takeWhile { it <= through }
}

private fun YearMonth.onDay(day: Int) = atDay(day.coerceIn(1, lengthOfMonth()))
