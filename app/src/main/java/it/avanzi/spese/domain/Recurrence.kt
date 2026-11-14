package it.avanzi.spese.domain

import it.avanzi.spese.data.RecurringEntity
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

fun RecurringEntity.nextOnOrAfter(date: LocalDate): LocalDate {
    val lowerBound = maxOf(date, anchorDate)
    val step = every.coerceAtLeast(1).toLong()
    return when (frequency) {
        Frequency.DAILY -> anchorDate.plusDays(ceilDiv(ChronoUnit.DAYS.between(anchorDate, lowerBound), step) * step)
        Frequency.WEEKLY -> {
            val first = anchorDate.with(TemporalAdjusters.nextOrSame(dayOfWeek ?: anchorDate.dayOfWeek))
            if (lowerBound <= first) first else {
                val period = 7 * step
                first.plusDays(ceilDiv(ChronoUnit.DAYS.between(first, lowerBound), period) * period)
            }
        }
        Frequency.MONTHLY -> {
            val day = dayOfMonth ?: anchorDate.dayOfMonth
            val start = YearMonth.from(anchorDate)
            val offset = ChronoUnit.MONTHS.between(start, YearMonth.from(lowerBound))
            val candidate = start.plusMonths(ceilDiv(offset, step) * step)
            candidate.onDay(day).takeIf { it >= lowerBound } ?: candidate.plusMonths(step).onDay(day)
        }
        Frequency.YEARLY -> {
            val day = dayOfMonth ?: anchorDate.dayOfMonth
            val start = YearMonth.of(anchorDate.year, monthOfYear ?: anchorDate.monthValue)
            val candidate = start.plusYears(ceilDiv((lowerBound.year - anchorDate.year).toLong(), step) * step)
            candidate.onDay(day).takeIf { it >= lowerBound } ?: candidate.plusYears(step).onDay(day)
        }
    }
}

fun RecurringEntity.occurrences(from: LocalDate, through: LocalDate): Sequence<LocalDate> {
    if (through < from || through < anchorDate) return emptySequence()
    return generateSequence(nextOnOrAfter(from)) { current ->
        if (current >= through) null else nextOnOrAfter(current.plusDays(1))
    }.takeWhile { it <= through }
}

/** Quante volte al mese, in media, arriva l'addebito. */
fun RecurringEntity.monthlyFactor(): Double = when (frequency) {
    Frequency.DAILY -> 365.25 / 12
    Frequency.WEEKLY -> 52.0 / 12
    Frequency.MONTHLY -> 1.0
    Frequency.YEARLY -> 1.0 / 12
} / every.coerceAtLeast(1)

fun RecurringEntity.monthlyCents(cents: Long = amountCents): Long = Math.round(cents * monthlyFactor())

/** Mesi coperti da un addebito, se più di uno: è su questi che l'addebito viene spalmato. */
fun spreadMonths(frequency: Frequency, every: Int): Int? = when (frequency) {
    Frequency.MONTHLY -> every
    Frequency.YEARLY -> 12 * every
    else -> 1
}.takeIf { it > 1 }

fun RecurringEntity.spreadMonths(): Int? = spreadMonths(frequency, every)

fun periodName(frequency: Frequency, every: Int): String = when (frequency) {
    Frequency.DAILY -> if (every == 1) "Ogni giorno" else "Ogni $every giorni"
    Frequency.WEEKLY -> if (every == 1) "Settimanale" else "Ogni $every settimane"
    Frequency.MONTHLY -> when (every) {
        1 -> "Mensile"
        2 -> "Bimestrale"
        3 -> "Trimestrale"
        4 -> "Quadrimestrale"
        6 -> "Semestrale"
        else -> "Ogni $every mesi"
    }
    Frequency.YEARLY -> when (every) {
        1 -> "Annuale"
        2 -> "Biennale"
        else -> "Ogni $every anni"
    }
}

fun RecurringEntity.scheduleLabel(): String {
    val day = dayOfMonth ?: anchorDate.dayOfMonth
    val detail = when (frequency) {
        Frequency.DAILY -> null
        Frequency.WEEKLY -> (dayOfWeek ?: anchorDate.dayOfWeek).getDisplayName(TextStyle.FULL, Locale.ITALY)
        Frequency.MONTHLY -> "il $day"
        Frequency.YEARLY -> "$day " + java.time.Month.of(monthOfYear ?: anchorDate.monthValue)
            .getDisplayName(TextStyle.FULL, Locale.ITALY)
    }
    return listOfNotNull(periodName(frequency, every), detail).joinToString(" · ")
}

private fun ceilDiv(value: Long, divisor: Long) = Math.floorDiv(value + divisor - 1, divisor)

private fun YearMonth.onDay(day: Int) = atDay(day.coerceIn(1, lengthOfMonth()))
